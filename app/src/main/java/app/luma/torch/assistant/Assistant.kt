package app.luma.torch.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import app.luma.torch.Torch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// עזר — העוזר הקולי של עזרים. מנוע ההבנה של "עוזר אופליין" (OfflineNlu) בוחר פקודה, IntentEngine מוציא פרטים,
// וכאן מבצעים ומחזירים תשובה קצרה בעברית. אין כאן שיחות, הודעות, וייפיי או אינטרנט.
object Assistant {
    const val NAME = "עזר"

    @Volatile private var nlu: OfflineNlu? = null
    private val slots = IntentEngine()
    private var jokes: List<String> = emptyList()

    fun engine(c: Context): OfflineNlu = nlu ?: synchronized(this) {
        nlu ?: OfflineNlu(c.assets.open("assistant/nlu_model.bin").use { it.readBytes() },
            c.assets.open("assistant/nlu_labels.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }).also {
            jokes = try { c.assets.open("assistant/jokes.txt").bufferedReader().readLines().filter { l -> '|' in l } } catch (_: Exception) { emptyList() }
            nlu = it
        }
    }

    fun prefs(c: Context) = c.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    fun isDefault(c: Context): Boolean = try { c.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) } catch (_: Exception) { false }

    // מגדיר את עזרים כעוזר המערכת (RoleManager, API מוסתר; MANAGE_ROLE_HOLDERS ניתנת בזכות חתימת המערכת)
    fun makeDefault(c: Context, done: (Boolean) -> Unit = {}) {
        try {
            val rm = c.getSystemService(android.app.role.RoleManager::class.java)
            rm.javaClass.getMethod("addRoleHolderAsUser", String::class.java, String::class.java, Int::class.javaPrimitiveType,
                android.os.UserHandle::class.java, java.util.concurrent.Executor::class.java, java.util.function.Consumer::class.java)
                .invoke(rm, android.app.role.RoleManager.ROLE_ASSISTANT, c.packageName, 0, android.os.Process.myUserHandle(), c.mainExecutor,
                    java.util.function.Consumer<Boolean> { done(it) })
        } catch (_: Throwable) { done(false) }
    }

    // פעם אחת: עזרים הופכת לעוזר המערכת. אם המשתמש בחר אחר כך עוזר אחר — לא נוגעים
    fun ensureDefault(c: Context) {
        val p = prefs(c)
        if (p.getBoolean("auto_default_done", false) || !app.luma.torch.dictation.ModelLocator.isInstalled()) return
        if (isDefault(c)) { p.edit().putBoolean("auto_default_done", true).apply(); return }
        makeDefault(c) { ok -> if (ok) p.edit().putBoolean("auto_default_done", true).apply() }
    }

    // ---------- תשובות (כאן כל הנוסחים הקבועים) ----------
    object Say {
        const val NOT_UNDERSTOOD = "לא הבנתי. אפשר לנסח אחרת?"
        const val NOT_YET = "את זה עוד לא למדתי לעשות"
        const val NOT_SUPPORTED = "את זה עזר לא עושה"
        const val ERROR = "משהו השתבש"
        const val CANCELLED = "בסדר, ביטלתי"
        const val GREETING = "שלום! במה אפשר לעזור?"
        const val THANKS = "בשמחה!"
        const val NEED_A11Y = "צריך להפעיל את שירות הנגישות \"עזרים - קיצורים\""
        const val NO_PERMISSION = "אין לעזרים הרשאה לשנות את זה"
        const val NEED_MUSIC = "צריך לאשר לעזרים גישה לקבצי המוזיקה (בהגדרות עזר)"
        const val HELP = "אפשר לבקש: לפתוח אפליקציה, פנס, תזכורת, טיימר, שעון מעורר, סטופר, מה השעה, שעה בעולם, תאריך עברי, זמני היום, " +
            "פרשת השבוע, מתי חג, ראש חודש, ספירת העומר, מולד, סוללה, שקט או רטט, ווליום ובהירות, לנגן שיר, מה מתנגן, טיימר שינה, " +
            "פתקים, משימות, רשימת קניות, הוצאות, שתיית מים, מע״מ, ניווט, צילום מסך ומסך הבית."
    }

    // then: פעולה שמתבצעת מיד אחרי שהחלונית נסגרת (פתיחת אפליקציה, מסך הבית…)
    class Reply(val text: String, val delayed: (() -> Unit)? = null, val then: (() -> Unit)? = null)

    private val INTERNAL = mapOf("reminder_add" to "reminder", "timer_set" to "timer", "alarm_set" to "alarm", "zmanim_all" to "zmanim",
        "battery_query" to "battery", "dnd_on" to "ringer_silent", "vibrate_mode" to "ringer_vibrate", "dnd_off" to "ringer_normal",
        "music_pause" to "media_pause", "music_resume" to "media_play", "music_next" to "media_next", "music_prev" to "media_prev")
    // הוסרו בכוונה: שיחות, הודעות, וייפיי ואינטרנט
    private val REMOVED = setOf("call_contact", "call_back", "recent_calls", "contact_number", "message_send", "notif_read", "driving_on", "driving_off",
        "wifi_on", "wifi_off", "transit_route", "bus_line", "app_store")
    private val OPEN_VERBS = listOf("תפתח", "תפתחי", "פתח", "פתחי", "לפתוח", "תיכנס", "תכנס", "כנס", "היכנס", "להיכנס", "תפעיל", "הפעל", "תריץ", "תקפיץ")
        .map { IntentEngine.n(it) }.toSet()

    fun handle(c: Context, said: String): Reply {
        val r = engine(c).classify(said)
        if (r.label in REMOVED && r.prob >= 0.35f) return Reply(Say.NOT_SUPPORTED)
        val first = IntentEngine.n(said).split(' ').firstOrNull { it !in IntentEngine.FILLER }
        if (r.label == "open_app" || (first in OPEN_VERBS && r.prob < 0.35f)) {
            val u = slots.parse("open_app", r.prob, said)
            findApp(c, u.text)?.let { (label, i) -> return Reply("פותח את $label", then = { c.startActivity(i) }) }
            if (r.label == "open_app" && r.prob >= 0.35f) return Reply("לא מצאתי אפליקציה בשם ${u.text ?: ""}")
        }
        if (r.label == "other" || r.prob < 0.35f) return Reply(Say.NOT_UNDERSTOOD)
        val u = slots.parse(INTERNAL[r.label] ?: r.label, r.prob, said)
        Torch.init(c)
        val today = LocalDate.now()
        return when (u.intent) {
            // מכשיר
            "flashlight_on" -> { Torch.set(true); Reply(if (Torch.busy) "הפנס תפוס כרגע על ידי המצלמה" else "הפנס דלוק") }
            "flashlight_off" -> { Torch.set(false); Reply("כיביתי את הפנס") }
            "battery" -> battery(c)
            "storage_query" -> storage()
            "ringer_silent" -> ringer(c, AudioManager.RINGER_MODE_SILENT, "עברתי למצב שקט")
            "ringer_vibrate" -> ringer(c, AudioManager.RINGER_MODE_VIBRATE, "עברתי לרטט")
            "ringer_normal" -> ringer(c, AudioManager.RINGER_MODE_NORMAL, "הצלצול חזר")
            "volume_up" -> volume(c, AudioManager.ADJUST_RAISE, "הגברתי")
            "volume_down" -> volume(c, AudioManager.ADJUST_LOWER, "הנמכתי")
            "volume_mute" -> { c.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0); Reply("השתקתי") }
            "volume_set" -> volumeSet(c, u.number)
            "brightness_up" -> brightness(c, +60); "brightness_down" -> brightness(c, -60)
            "brightness_set" -> brightnessSet(c, u.number)
            "brightness_auto" -> sysSet(c, Settings.System.SCREEN_BRIGHTNESS_MODE, 1, "הפעלתי בהירות אוטומטית")
            "rotation_on" -> sysSet(c, Settings.System.ACCELEROMETER_ROTATION, 1, "הפעלתי סיבוב מסך")
            "rotation_off" -> sysSet(c, Settings.System.ACCELEROMETER_ROTATION, 0, "נעלתי את סיבוב המסך")
            "bluetooth_on", "bluetooth_off" -> bluetooth(c, u.intent == "bluetooth_on")
            "open_settings" -> start(c, Intent(Settings.ACTION_SETTINGS), "פותח את ההגדרות")
            "camera" -> start(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "פותח את המצלמה")
            "open_app" -> Reply("איזו אפליקציה לפתוח?")
            "navigate" -> navigate(c, u.text)
            // מסך
            "nav_home" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "nav_back" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "nav_recents" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            "nav_notifications" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "nav_quick_settings" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "screenshot" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            "lock_screen" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            "power_menu" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            "split_screen" -> global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            // מדיה
            "media_pause" -> media(c, KeyEvent.KEYCODE_MEDIA_PAUSE, "עצרתי")
            "media_play" -> media(c, KeyEvent.KEYCODE_MEDIA_PLAY, "ממשיך")
            "media_next" -> media(c, KeyEvent.KEYCODE_MEDIA_NEXT, "הבא")
            "media_prev" -> media(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "הקודם")
            "play_song" -> song(c, u.text, u.artist)
            "now_playing" -> nowPlaying(c)
            "sleep_timer" -> { val d = u.duration ?: Duration.ofMinutes(30); schedule(c, LocalDateTime.now().plus(d), "", pause = true); Reply("המוזיקה תיעצר בעוד ${say(d)}") }
            // זמן
            "time_query" -> Reply("השעה " + hm(LocalDateTime.now()))
            "date_query" -> date(u)
            "world_time" -> worldTime(u.text)
            "timer" -> timer(c, u.duration)
            "alarm" -> alarm(c, u.time)
            "stopwatch_start" -> { prefs(c).edit().putLong("stopwatch", System.currentTimeMillis()).apply(); Reply("הסטופר התחיל") }
            "stopwatch_stop" -> stopwatch(c)
            "date_calc", "holiday_when", "hebrew_date_when" -> whenIs(u, today, daysOnly = u.intent == "date_calc")
            "years_since" -> u.number?.toInt()?.let { y -> Reply("עברו ${today.year - y} שנים מאז $y") } ?: Reply("מאיזו שנה?")
            // תזכורות
            "reminder" -> reminder(c, u)
            "reminder_list" -> reminderList(c)
            "reminder_cancel" -> reminderCancel(c, u.text)
            "alarm_list" -> start(c, Intent(AlarmClock.ACTION_SHOW_ALARMS), "פותח את רשימת השעונים")
            // רשימות
            "note_add" -> listAdd(c, "notes", u.text, "רשמתי פתק: ")
            "note_list" -> listShow(c, "notes", "הפתקים שלך", "אין פתקים")
            "note_delete" -> listRemove(c, "notes", u.text, "מחקתי את הפתק")
            "todo_add" -> listAdd(c, "todo", u.text, "הוספתי משימה: ")
            "todo_list" -> listShow(c, "todo", "המשימות שלך", "אין משימות פתוחות")
            "todo_done" -> listRemove(c, "todo", u.text, "סימנתי כבוצע")
            "todo_clear" -> { prefs(c).edit().remove("list_todo").apply(); Reply("ניקיתי את רשימת המשימות") }
            "shopping_add" -> listAdd(c, "shop", u.text, "הוספתי לרשימת הקניות: ")
            "shopping_list" -> listShow(c, "shop", "ברשימת הקניות", "רשימת הקניות ריקה")
            "shopping_remove" -> listRemove(c, "shop", u.text, "הורדתי מהרשימה")
            "shopping_clear" -> { prefs(c).edit().remove("list_shop").apply(); Reply("ניקיתי את רשימת הקניות") }
            "expense_add" -> expenseAdd(c, u.number, u.text)
            "expense_query" -> expenseQuery(c)
            "water_add" -> water(c, (u.number ?: 1.0).toInt())
            "water_query" -> water(c, 0)
            // יהדות
            "zmanim" -> Reply(Zmanim.answer(c, u.zman ?: "all", u.dayOffset))
            "parasha" -> parasha(today)
            "rosh_chodesh" -> roshChodesh(today)
            "omer" -> omer(today)
            "molad" -> molad(today)
            // כלים
            "vat" -> vat(u.number, u.text == "minus")
            // שיחה
            "cancel" -> Reply(Say.CANCELLED)
            "greeting" -> Reply(Say.GREETING)
            "thanks" -> Reply(Say.THANKS)
            "help" -> Reply(Say.HELP)
            "joke" -> jokes.randomOrNull()?.split('|')?.let { Reply(it[0] + "\n" + it.getOrElse(1) { "" }) } ?: Reply("אין לי בדיחה כרגע")
            "how_are_you" -> Reply("ברוך השם, מצוין! ומה איתך?")
            "goodbye" -> Reply("להתראות!", then = {})
            "name_ask_assistant" -> Reply("קוראים לי $NAME")
            "compliment" -> Reply("תודה, אתה נחמד!")
            "feeling", "cheer_up" -> Reply("אני כאן. מקווה שיהיה לך יום טוב יותר")
            "complaint" -> Reply("מצטער, אני עוד לומד. אפשר לנסח אחרת?")
            else -> Reply(Say.NOT_YET)
        }
    }

    // ---------- זמן ----------
    fun hm(t: LocalDateTime) = "%d:%02d".format(t.hour, t.minute)
    private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("he"))

    fun say(d: Duration): String {
        val m = d.toMinutes(); val s = d.seconds % 60
        val parts = mutableListOf<String>()
        if (m >= 60) parts += if (m / 60 == 1L) "שעה" else "${m / 60} שעות"
        if (m % 60 > 0) parts += if (m % 60 == 1L) "דקה" else "${m % 60} דקות"
        if (s > 0) parts += "$s שניות"
        return parts.joinToString(" ו")
    }

    private fun date(u: IntentEngine.Understanding): Reply {
        val d = LocalDate.now().plusDays(u.dayOffset.toLong())
        val which = if (u.dayOffset == 1) "מחר" else "היום"
        val greg = "%d.%d.%d".format(d.dayOfMonth, d.monthValue, d.year)
        return Reply("$which ${dayName(d)}, ${HebCal.format(d)}" + if (u.hebrew) "" else ", $greg")
    }

    private val ZONES = mapOf("ניו יורק" to "America/New_York", "ברוקלין" to "America/New_York", "מונסי" to "America/New_York", "לייקווד" to "America/New_York",
        "אמריקה" to "America/New_York", "מיאמי" to "America/New_York", "שיקגו" to "America/Chicago", "לוס אנגלס" to "America/Los_Angeles",
        "טורונטו" to "America/Toronto", "מונטריאול" to "America/Toronto", "לונדון" to "Europe/London", "מנצסטר" to "Europe/London", "אנגליה" to "Europe/London",
        "פריז" to "Europe/Paris", "צרפת" to "Europe/Paris", "אנטוורפן" to "Europe/Brussels", "בלגיה" to "Europe/Brussels", "ברלין" to "Europe/Berlin",
        "גרמניה" to "Europe/Berlin", "ציריך" to "Europe/Zurich", "שוויץ" to "Europe/Zurich", "וינה" to "Europe/Vienna", "רומא" to "Europe/Rome",
        "איטליה" to "Europe/Rome", "מוסקבה" to "Europe/Moscow", "רוסיה" to "Europe/Moscow", "אומן" to "Europe/Kiev", "אוקראינה" to "Europe/Kiev",
        "קייב" to "Europe/Kiev", "דובאי" to "Asia/Dubai", "הודו" to "Asia/Kolkata", "סין" to "Asia/Shanghai", "יפן" to "Asia/Tokyo", "טוקיו" to "Asia/Tokyo",
        "סידני" to "Australia/Sydney", "אוסטרליה" to "Australia/Sydney", "יוהנסבורג" to "Africa/Johannesburg", "בואנוס איירס" to "America/Argentina/Buenos_Aires",
        "ארגנטינה" to "America/Argentina/Buenos_Aires", "מקסיקו" to "America/Mexico_City", "ברזיל" to "America/Sao_Paulo").mapKeys { IntentEngine.n(it.key) }

    private fun worldTime(place: String?): Reply {
        if (place == null) return Reply("השעה " + hm(LocalDateTime.now()))
        val q = IntentEngine.n(place)
        val z = ZONES[q] ?: ZONES.entries.firstOrNull { q.contains(it.key) || it.key.contains(q) }?.value ?: return Reply("לא מכיר את המקום $place")
        return Reply("ב$place השעה " + hm(LocalDateTime.now(ZoneId.of(z))))
    }

    private fun stopwatch(c: Context): Reply {
        val st = prefs(c).getLong("stopwatch", 0)
        if (st == 0L) return Reply("הסטופר לא פועל")
        prefs(c).edit().remove("stopwatch").apply()
        return Reply("עצרתי: " + say(Duration.ofMillis(System.currentTimeMillis() - st)).ifBlank { "פחות משנייה" })
    }

    // "מתי פסח", "כמה ימים עד חנוכה", "כמה ימים עד שבת"
    private fun whenIs(u: IntentEngine.Understanding, today: LocalDate, daysOnly: Boolean): Reply {
        val q = IntentEngine.n(u.text.orEmpty())
        val key = HebCal.HOLIDAYS.firstOrNull { q.contains(IntentEngine.n(it.first)) }?.second
        val target: LocalDate; val name: String
        when {
            key != null -> { target = HebCal.nextHoliday(key, today) ?: return Reply(Say.NOT_YET); name = HebCal.HOLIDAY_NAMES[key]!! }
            q.contains(IntentEngine.n("שבת")) -> { target = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)); name = "שבת" }
            q.contains(IntentEngine.n("ראש חודש")) -> return roshChodesh(today)
            else -> return Reply("לא זיהיתי את התאריך")
        }
        val days = ChronoUnit.DAYS.between(today, target)
        val left = when (days) { 0L -> "היום"; 1L -> "מחר"; else -> "בעוד $days ימים" }
        return if (daysOnly) Reply("$name $left") else Reply("$name $left — ${dayName(target)}, ${HebCal.format(target)} (${target.dayOfMonth}.${target.monthValue})")
    }

    // ---------- יהדות ----------
    private fun parasha(today: LocalDate): Reply {
        val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        return Reply("פרשת השבוע: ${HebCal.parasha(sat)}")
    }

    private fun roshChodesh(today: LocalDate): Reply {
        val (h, days) = HebCal.nextRoshChodesh(today)
        val m = HebCal.monthName(h.m, h.y)
        return Reply("ראש חודש $m: " + days.joinToString(" ו") { "${dayName(it)} ${it.dayOfMonth}.${it.monthValue}" })
    }

    private fun omer(today: LocalDate): Reply {
        val n = HebCal.omerDay(today) ?: return Reply("עכשיו לא ימי ספירת העומר")
        val w = n / 7; val d = n % 7
        val weeks = if (w == 0) "" else " שהם ${if (w == 1) "שבוע אחד" else "$w שבועות"}" + if (d > 0) " ו${if (d == 1) "יום אחד" else "$d ימים"}" else ""
        return Reply("היום $n יום לעומר$weeks. הלילה סופרים ${n + 1}.")
    }

    private fun molad(today: LocalDate): Reply {
        val (h, d, t) = HebCal.nextMolad(today)
        return Reply("מולד חודש ${HebCal.monthName(h.m, h.y)}: ${dayName(d)} ${d.dayOfMonth}.${d.monthValue}, בשעה ${t.first}:%02d ו-${t.third} חלקים".format(t.second))
    }

    // ---------- כלים ----------
    private fun vat(x: Double?, minus: Boolean): Reply {
        if (x == null) return Reply("על איזה סכום?")
        fun f(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)
        return if (minus) Reply("${f(x)} ₪ כולל מע״מ = ${f(x / 1.18)} ₪ לפני מע״מ (המע״מ: ${f(x - x / 1.18)} ₪)")
        else Reply("${f(x)} ₪ + 18% מע״מ = ${f(x * 1.18)} ₪ (המע״מ: ${f(x * 0.18)} ₪)")
    }

    // ניווט: כל אפליקציית ניווט שמקבלת חיפוש (geo:) — אם יש כמה, המערכת שואלת באיזו
    private fun navigate(c: Context, place: String?): Reply {
        if (place.isNullOrBlank()) return Reply("לאן לנווט?")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(c.packageManager) == null) return Reply("לא נמצאה אפליקציית ניווט במכשיר")
        return Reply("מחפש: $place", then = { c.startActivity(i) })
    }

    // ---------- מכשיר ----------
    private fun battery(c: Context): Reply {
        val bm = c.getSystemService(BatteryManager::class.java)
        return Reply("נשארו ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} אחוז" + if (bm.isCharging) ", והטלפון בטעינה" else "")
    }

    private fun storage(): Reply {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        return Reply("פנויים %.1f GB מתוך %.0f GB".format(st.availableBytes / 1e9, st.totalBytes / 1e9))
    }

    private fun ringer(c: Context, mode: Int, ok: String): Reply {
        val am = c.getSystemService(AudioManager::class.java)
        if (mode != AudioManager.RINGER_MODE_NORMAL || am.ringerMode == AudioManager.RINGER_MODE_SILENT) grantDnd(c)
        return try { am.ringerMode = mode; Reply(ok) } catch (_: SecurityException) { Reply(Say.NO_PERMISSION) }
    }

    private fun grantDnd(c: Context) {
        if (c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) return
        appendSecure(c, "enabled_notification_policy_access_packages", c.packageName)
    }

    fun appendSecure(c: Context, key: String, value: String) {
        try {
            val cur = Settings.Secure.getString(c.contentResolver, key).orEmpty().split(':').filter { it.isNotBlank() }
            if (value !in cur) Settings.Secure.putString(c.contentResolver, key, (cur + value).joinToString(":"))
        } catch (_: Exception) {}
    }

    private fun volume(c: Context, dir: Int, ok: String): Reply {
        val am = c.getSystemService(AudioManager::class.java)
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, if (it == 1) AudioManager.FLAG_SHOW_UI else 0) }
        return Reply(ok)
    }

    private fun volumeSet(c: Context, pct: Double?): Reply {
        if (pct == null) return Reply("לכמה אחוז?")
        val am = c.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct.coerceIn(0.0, 100.0) / 100).toInt(), AudioManager.FLAG_SHOW_UI)
        return Reply("הווליום על ${pct.toInt()}%")
    }

    private fun sysSet(c: Context, key: String, v: Int, ok: String): Reply =
        try { Settings.System.putInt(c.contentResolver, key, v); Reply(ok) } catch (_: Exception) { Reply(Say.NO_PERMISSION) }

    private fun brightness(c: Context, delta: Int): Reply = try {
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        val cur = Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (cur + delta).coerceIn(5, 255))
        Reply(if (delta > 0) "הגברתי את הבהירות" else "הנמכתי את הבהירות")
    } catch (_: Exception) { Reply(Say.NO_PERMISSION) }

    private fun brightnessSet(c: Context, pct: Double?): Reply {
        if (pct == null) return Reply("לכמה אחוז?")
        return try {
            Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (255 * pct.coerceIn(1.0, 100.0) / 100).toInt())
            Reply("הבהירות על ${pct.toInt()}%")
        } catch (_: Exception) { Reply(Say.NO_PERMISSION) }
    }

    private fun bluetooth(c: Context, on: Boolean): Reply {
        @Suppress("DEPRECATION", "MissingPermission")
        val ok = try { val a = android.bluetooth.BluetoothAdapter.getDefaultAdapter(); if (on) a.enable() else a.disable() } catch (_: Exception) { false }
        return if (ok) Reply(if (on) "הדלקתי בלוטוס" else "כיביתי בלוטוס") else start(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "פותח את הגדרות הבלוטוס")
    }

    private fun start(c: Context, i: Intent, ok: String): Reply = Reply(ok, then = {
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    })

    private fun global(action: Int): Reply {
        val svc = app.luma.torch.CameraButtonService.instance ?: return Reply(Say.NEED_A11Y)
        return Reply("", then = { svc.performGlobalAction(action) })
    }

    // ---------- מדיה ----------
    private fun media(c: Context, key: Int, ok: String): Reply {
        val am = c.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return Reply(ok)
    }

    fun pauseMedia(c: Context) { media(c, KeyEvent.KEYCODE_MEDIA_PAUSE, "") }

    // מה מתנגן: דרך "גישה להתראות" (עזרים מפעילה אותה לעצמה) — רק כדי לקרוא את פרטי השיר, לא הודעות
    private fun nowPlaying(c: Context): Reply {
        val cn = ComponentName(c, MediaListener::class.java)
        appendSecure(c, "enabled_notification_listeners", cn.flattenToString())
        return try {
            val sessions = c.getSystemService(android.media.session.MediaSessionManager::class.java).getActiveSessions(cn)
            val playing = sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
            val md = playing?.metadata ?: return Reply("כרגע לא מתנגן כלום")
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            Reply("מתנגן: $title" + if (artist.isNotBlank()) " — $artist" else "")
        } catch (_: SecurityException) { Reply("צריך לאשר גישה להתראות לעזרים") }
    }

    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun song(c: Context, title: String?, artist: String?): Reply {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (!granted(c, perm)) return Reply(Say.NEED_MUSIC)
        val qt = IntentEngine.n(title.orEmpty()).split(' ').filter { it.length > 1 }
        val at = IntentEngine.n(artist.orEmpty()).split(' ').filter { it.length > 1 }
        if (qt.isEmpty() && at.isEmpty()) return Reply("איזה שיר לנגן?")
        var bestId = -1L; var bestScore = 0; var bestName = ""
        c.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DISPLAY_NAME),
            null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val ttl = IntentEngine.n((cur.getString(1) ?: "") + " " + (cur.getString(4) ?: ""))
                val art = IntentEngine.n((cur.getString(2) ?: "") + " " + (cur.getString(3) ?: ""))
                var s = qt.count { ttl.contains(it) } * 10 + at.count { art.contains(it) } * 6
                if (qt.isNotEmpty() && qt.all { ttl.contains(it) }) s += 20
                if (at.isNotEmpty() && at.all { "$ttl $art".contains(it) }) s += 12
                if (s > bestScore) { bestScore = s; bestId = cur.getLong(0); bestName = cur.getString(1) ?: "" }
            }
        }
        if (bestId < 0 || bestScore < (if (qt.isNotEmpty()) 20 else 12)) return Reply("לא מצאתי במכשיר את " + listOfNotNull(title, artist?.let { "של $it" }).joinToString(" "))
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, bestId)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (i.resolveActivity(c.packageManager) == null) return Reply("לא נמצאה אפליקציה שמנגנת מוזיקה")
        return Reply("מנגן: $bestName", then = { c.startActivity(i) })
    }

    // ---------- פתיחת אפליקציות ----------
    private val ALIASES = mapOf("וואטסאפ" to "whatsapp", "ווטסאפ" to "whatsapp", "וטסאפ" to "whatsapp", "ווייז" to "waze", "וייז" to "waze",
        "כרום" to "chrome", "יוטיוב" to "youtube", "מצלמה" to "camera", "גלריה" to "gallery", "תמונות" to "photos", "הגדרות" to "settings",
        "מחשבון" to "calculator", "שעון" to "clock", "יומן" to "calendar", "אנשי קשר" to "contacts", "טלפון" to "phone", "חייגן" to "dialer",
        "הודעות" to "messages", "מוזיקה" to "music", "קבצים" to "files", "מנהל קבצים" to "file manager", "מפות" to "maps", "גוגל" to "google",
        "חנות" to "store", "פליי" to "play", "גימייל" to "gmail", "מייל" to "mail", "רדיו" to "radio", "מקליט" to "recorder", "הקלטות" to "recorder",
        "מוביט" to "moovit", "גט" to "gett", "אוסמאנד" to "osmand", "אורגניק" to "organic").mapKeys { IntentEngine.n(it.key) }

    fun findApp(c: Context, name: String?): Pair<String, Intent>? {
        if (name.isNullOrBlank()) return null
        val pm = c.packageManager
        var q = IntentEngine.n(name)
        if (q.length > 3 && q[0] == 'ה' && ALIASES[q] == null) q = q.substring(1)
        val alias = ALIASES[q] ?: ALIASES.entries.firstOrNull { q.contains(it.key) }?.value
        var best: android.content.pm.ResolveInfo? = null; var bestScore = 0
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            val label = IntentEngine.n(ri.loadLabel(pm).toString())
            val pkg = ri.activityInfo.packageName.lowercase()
            val score = when {
                label == q -> 100
                alias != null && (label.contains(alias) || pkg.contains(alias.replace(" ", ""))) -> 90
                label.startsWith(q) || q.startsWith(label) && label.length >= 3 -> 75
                label.contains(q) -> 65
                q.split(' ').all { t -> label.contains(t) } -> 55
                similar(label, q) -> 45
                else -> 0
            }
            if (score > bestScore) { bestScore = score; best = ri }
        }
        val ri = best ?: return null
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return ri.loadLabel(pm).toString() to i
    }

    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length] <= maxOf(1, minOf(a.length, b.length) / 4)
    }

    // ---------- טיימר, שעון מעורר, תזכורות ----------
    private fun timer(c: Context, d: Duration?): Reply {
        if (d == null) return Reply("לכמה זמן לכוון טיימר?")
        return try {
            c.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, d.seconds.toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .putExtra(AlarmClock.EXTRA_MESSAGE, NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Reply("טיימר ל${say(d)}")
        } catch (_: Exception) { schedule(c, LocalDateTime.now().plus(d), "הטיימר הסתיים"); Reply("טיימר ל${say(d)}") }
    }

    private fun alarm(c: Context, t: LocalDateTime?): Reply {
        if (t == null) return Reply("לאיזו שעה לכוון?")
        return try {
            c.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, t.hour).putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Reply("כיוונתי שעון מעורר ל-${hm(t)}")
        } catch (_: Exception) { schedule(c, t, "שעון מעורר"); Reply("אין אפליקציית שעון; אזכיר לך ב-${hm(t)}") }
    }

    private fun reminder(c: Context, u: IntentEngine.Understanding): Reply {
        val at = u.time ?: if (u.dayOffset > 0) LocalDate.now().plusDays(u.dayOffset.toLong()).atTime(9, 0) else null
        val what = u.text ?: "תזכורת"
        if (at == null) return Reply("מתי להזכיר לך $what?")
        schedule(c, at, what)
        return Reply("אזכיר לך ${whenText(at)}: $what")
    }

    private fun whenText(at: LocalDateTime): String {
        val day = when (at.toLocalDate()) { LocalDate.now() -> ""; LocalDate.now().plusDays(1) -> "מחר "; else -> "ב${dayName(at.toLocalDate())} " }
        return "${day}ב-${hm(at)}"
    }

    private fun reminders(c: Context) = JSONArray(prefs(c).getString("reminders", "[]"))

    private fun reminderList(c: Context): Reply {
        val l = reminders(c); val now = System.currentTimeMillis()
        val items = (0 until l.length()).map { l.getJSONObject(it) }.filter { it.getLong("at") > now && !it.optBoolean("pause") }.sortedBy { it.getLong("at") }
        if (items.isEmpty()) return Reply("אין תזכורות")
        return Reply("התזכורות שלך:\n" + items.joinToString("\n") {
            "• " + whenText(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.getLong("at")), ZoneId.systemDefault())) + ": " + it.getString("text") })
    }

    private fun reminderCancel(c: Context, text: String?): Reply {
        val l = reminders(c); val now = System.currentTimeMillis()
        val items = (0 until l.length()).map { l.getJSONObject(it) }.filter { it.getLong("at") > now && !it.optBoolean("pause") }.sortedBy { it.getLong("at") }
        if (items.isEmpty()) return Reply("אין תזכורות לבטל")
        val q = IntentEngine.n(text.orEmpty())
        val hit = items.firstOrNull { q.isNotBlank() && match(IntentEngine.n(it.getString("text")), q) } ?: items.first()
        cancelAlarm(c, hit.getInt("id")); done(c, hit.getInt("id"))
        return Reply("ביטלתי את התזכורת: ${hit.getString("text")}")
    }

    fun schedule(c: Context, at: LocalDateTime, text: String, pause: Boolean = false) {
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val list = reminders(c)
        list.put(JSONObject().put("id", id).put("at", ms).put("text", text).put("pause", pause))
        prefs(c).edit().putString("reminders", list.toString()).apply()
        arm(c, id, ms, text, pause)
    }

    private fun pi(c: Context, id: Int, text: String, pause: Boolean) = PendingIntent.getBroadcast(c, id,
        Intent(c, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text).putExtra("pause", pause),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(c: Context, id: Int, ms: Long, text: String, pause: Boolean) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, id, text, pause)
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        } catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p) }
    }

    private fun cancelAlarm(c: Context, id: Int) { c.getSystemService(AlarmManager::class.java).cancel(pi(c, id, "", false)) }

    fun rearm(c: Context) {
        val list = reminders(c); val keep = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getLong("at") > System.currentTimeMillis() - 60_000) {
                keep.put(o); arm(c, o.getInt("id"), maxOf(o.getLong("at"), System.currentTimeMillis() + 5000), o.getString("text"), o.optBoolean("pause"))
            }
        }
        prefs(c).edit().putString("reminders", keep.toString()).apply()
    }

    fun done(c: Context, id: Int) {
        val list = reminders(c); val keep = JSONArray()
        for (i in 0 until list.length()) list.getJSONObject(i).let { if (it.getInt("id") != id) keep.put(it) }
        prefs(c).edit().putString("reminders", keep.toString()).apply()
    }

    // התאמה סלחנית: כל מילה בבקשה (גם בלי ל/ה/ב בהתחלה) מופיעה בפריט
    private fun match(item: String, q: String): Boolean {
        if (item.contains(q) || q.contains(item)) return true
        val words = q.split(' ').filter { it.length > 1 }.map { w -> if (w.length > 2 && w[0] in "לבהמש") w.substring(1) else w }
        return words.isNotEmpty() && words.all { item.contains(it) }
    }

    // ---------- רשימות: פתקים, משימות, קניות ----------
    private fun list(c: Context, name: String) = JSONArray(prefs(c).getString("list_$name", "[]"))
    private fun save(c: Context, name: String, a: JSONArray) = prefs(c).edit().putString("list_$name", a.toString()).apply()

    private fun listAdd(c: Context, name: String, text: String?, ok: String): Reply {
        if (text.isNullOrBlank()) return Reply("מה לרשום?")
        val a = list(c, name)
        // ברשימת קניות: "חלב ולחם" → שני פריטים
        val items = if (name == "shop") text.split(Regex(" ו(?=\\S)|,| וגם ")).map { it.trim() }.filter { it.isNotEmpty() } else listOf(text)
        items.forEach { a.put(it) }
        save(c, name, a)
        return Reply(ok + items.joinToString(", "))
    }

    private fun listShow(c: Context, name: String, title: String, empty: String): Reply {
        val a = list(c, name)
        if (a.length() == 0) return Reply(empty)
        return Reply("$title:\n" + (0 until a.length()).joinToString("\n") { "• " + a.getString(it) })
    }

    private fun listRemove(c: Context, name: String, text: String?, ok: String): Reply {
        val a = list(c, name)
        if (a.length() == 0) return Reply("הרשימה ריקה")
        val q = IntentEngine.n(text.orEmpty())
        val idx = (0 until a.length()).firstOrNull { q.isNotBlank() && match(IntentEngine.n(a.getString(it)), q) }
            ?: if (name == "notes" && q.isBlank()) a.length() - 1 else return Reply("לא מצאתי את זה ברשימה")
        val removed = a.getString(idx)
        val b = JSONArray(); for (i in 0 until a.length()) if (i != idx) b.put(a.get(i))
        save(c, name, b)
        return Reply("$ok: $removed")
    }

    private fun expenseAdd(c: Context, amount: Double?, what: String?): Reply {
        if (amount == null) return Reply("כמה הוצאת?")
        val a = list(c, "expense")
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("a", amount).put("w", what ?: ""))
        save(c, "expense", a)
        return Reply("רשמתי הוצאה של ${"%.0f".format(amount)} ₪" + (what?.let { " על $it" } ?: ""))
    }

    private fun expenseQuery(c: Context): Reply {
        val a = list(c, "expense"); val now = LocalDate.now()
        var month = 0.0; var today = 0.0
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val d = java.time.Instant.ofEpochMilli(o.getLong("t")).atZone(ZoneId.systemDefault()).toLocalDate()
            if (d.year == now.year && d.month == now.month) month += o.getDouble("a")
            if (d == now) today += o.getDouble("a")
        }
        return Reply("היום הוצאת ${"%.0f".format(today)} ₪, ובחודש הזה ${"%.0f".format(month)} ₪")
    }

    private fun water(c: Context, add: Int): Reply {
        val key = "water_" + LocalDate.now()
        val n = prefs(c).getInt(key, 0) + add
        if (add > 0) prefs(c).edit().putInt(key, n).apply()
        return Reply(if (add > 0) "רשמתי. היום שתית $n כוסות" else "היום שתית $n כוסות מים")
    }
}
