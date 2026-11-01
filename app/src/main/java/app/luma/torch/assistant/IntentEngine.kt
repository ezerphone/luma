package app.luma.torch.assistant

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

// חילוץ פרטים מהמשפט, אחרי שמנוע ההבנה (OfflineNlu) בחר פקודה —
// משך זמן, שעה, טקסט תזכורת, שם איש קשר, שם שיר, איזה זמן מזמני היום.
class IntentEngine {

    data class Understanding(
        val intent: String,             // הפקודה (שמות התוויות של מנוע ההבנה), או "other"
        val confidence: Float,
        val duration: Duration? = null, // טיימר, "בעוד…"
        val time: LocalDateTime? = null, // שעון מעורר / תזכורת בשעה מסוימת
        val text: String? = null,       // טקסט התזכורת / שם איש הקשר / שם השיר / שם האפליקציה
        val artist: String? = null,     // "של X" בבקשת שיר
        val zman: String? = null,       // sunset, sunrise, candles, shabbat_end, chatzot, dawn, shema, tefila, mincha, plag, fast_start, fast_end, all
        val dayOffset: Int = 0,         // 0 היום, 1 מחר, 2 מחרתיים
        val hebrew: Boolean = false,    // "תאריך עברי"
        val number: Double? = null      // אחוזים, סכום, כוסות, שנה…
    )

    // מוציא פרטים מהמשפט, לפי הפקודה שמנוע ההבנה בחר
    fun parse(intent: String, conf: Float, text: String, now: LocalDateTime = LocalDateTime.now()): Understanding {
        val t = Tok(text)
        val base = Understanding(intent, conf, dayOffset = t.dayOffset())
        return when (intent) {
            "timer" -> base.copy(duration = t.duration())
            "alarm" -> {
                val d = if (t.has("בעוד")) t.duration() else null
                base.copy(time = d?.let { now.plus(it).withSecond(0).withNano(0) } ?: t.clockTime(now))
            }
            "reminder" -> {
                val d = if (t.has("בעוד") || t.has("עוד")) t.duration() else null
                val time = d?.let { now.plus(it).withSecond(0).withNano(0) } ?: t.clockTime(now)
                base.copy(duration = d, time = time, text = t.remainder(REMINDER_WORDS).ifBlank { null })
            }
            "call" -> base.copy(text = t.contactName())
            "play_song" -> { val (q, a) = t.song(); base.copy(text = q, artist = a) }
            "open_app" -> base.copy(text = t.remainder(APP_WORDS).ifBlank { null }?.let { stripL(it) })
            "zmanim" -> base.copy(zman = t.zman())
            "volume_set", "brightness_set" -> base.copy(number = t.percent())
            "vat" -> base.copy(number = t.amount(), text = if (t.has("בלי") || t.has("לפני") || t.has("ללא") || t.has("נטו")) "minus" else "plus")
            "expense_add" -> base.copy(number = t.amount(), text = t.remainder(LIST_WORDS).let { r -> r.split(' ').filter { w -> w.toDoubleOrNull() == null && w !in setOf("שקל", "שקלים", "ש״ח", "₪") }.joinToString(" ") }.ifBlank { null })
            "water_add" -> base.copy(number = t.amount() ?: 1.0)
            "years_since" -> base.copy(number = t.amount())
            "sleep_timer" -> base.copy(duration = t.duration())
            "navigate" -> base.copy(text = t.remainder(NAV_WORDS).ifBlank { null }?.let { stripL(it) })
            "world_time" -> base.copy(text = t.remainder(WT_WORDS).ifBlank { null }?.let { stripPrefixB(it) })
            "holiday_when", "date_calc", "hebrew_date_when" -> base.copy(text = t.remainder(WHEN_WORDS).ifBlank { null })
            "note_add", "note_delete", "todo_add", "todo_done", "shopping_add", "shopping_remove", "reminder_cancel" ->
                base.copy(text = t.remainder(LIST_WORDS).ifBlank { null })
            "date_query" -> base.copy(hebrew = t.has("עברי") || t.has("העברי") || t.has("בעברית"))
            else -> base
        }
    }

    private fun stripPrefixB(s: String): String {
        val w = s.split(' ').toMutableList()
        if (w[0].length > 3 && w[0][0] == 'ב') w[0] = w[0].substring(1)
        return w.joinToString(" ")
    }

    private fun stripL(s: String): String {
        val w = s.split(' ').toMutableList()
        if (w[0].length > 3 && w[0][0] == 'ל') w[0] = w[0].substring(1)
        return w.joinToString(" ")
    }

    companion object {
        val APP_WORDS by lazy { set("תפתח", "תפתחי", "פתח", "פתחי", "לפתוח", "תפעיל", "תפעילי", "הפעל", "להפעיל", "תיכנס", "תכנס", "כנס", "היכנס",
            "להיכנס", "תעלה", "תריץ", "תקפיץ", "תביא", "את", "האפליקציה", "אפליקציה", "אפליקציית", "לאפליקציה", "לאפליקציית", "היישום", "יישום", "ל", "אל") }

        fun n(s: String) = Features.normalize(s) // לנרמול מילים לחיפוש

        private fun set(vararg w: String) = w.map { n(it) }.toHashSet()

        val NAV_WORDS by lazy { set("תנווט", "נווט", "לנווט", "תנווטי", "תחפש", "חפש", "תמצא", "מצא", "באפליקציית", "באפליקציה", "בוויז", "בווייז",
            "במפות", "במפה", "הניווט", "ניווט", "לי", "את", "אל", "איך", "מגיעים", "מגיע", "להגיע", "דרך", "מסלול", "הדרך") }
        val WT_WORDS by lazy { set("מה", "השעה", "שעה", "כמה", "עכשיו", "איזו", "יש", "באיזו", "אצל", "תגיד", "לי") }
        val WHEN_WORDS by lazy { set("מתי", "כמה", "ימים", "יום", "נשארו", "נשאר", "עד", "עוד", "יוצא", "חל", "חלה", "יהיה", "באיזה", "תאריך", "השנה",
            "זה", "יוצאת", "מתחיל", "מתחילה", "לי", "תגיד") }
        val LIST_WORDS by lazy { set("תרשום", "רשום", "תרשמי", "לרשום", "תוסיף", "הוסף", "תוסיפי", "להוסיף", "תכתוב", "כתוב", "תכתבי", "תשמור", "שמור",
            "תמחק", "מחק", "תמחקי", "תוריד", "הורד", "תסיר", "הסר", "תסמן", "סמן", "תבטל", "בטל", "פתק", "הפתק", "בפתק", "לפתקים", "פתקים",
            "משימה", "המשימה", "משימות", "למשימות", "לרשימת", "ברשימת", "מרשימת", "רשימת", "הקניות", "קניות", "לקניות", "הוצאה", "הוצאתי", "שילמתי",
            "קניתי", "על", "את", "ש", "שצריך", "לקנות", "גם", "בוצע", "בוצעה", "סיימתי", "עשיתי", "התזכורת", "תזכורת", "של", "חדש", "חדשה", "לי") }
        val FILLER: Set<String> = set("בבקשה", "היי", "עזרים", "נו", "יאללה", "רגע", "שמע", "תקשיב", "אממ", "אה", "עכשיו", "מהר", "תודה", "כבר", "אם", "אפשר",
            "תוכל", "תוכלי", "אני", "רוצה", "צריך", "צריכה", "לי", "בא", "מבקש", "מבקשת", "תעשה", "טובה", "הייתי", "יכול", "יכולה", "אתה", "פה", "כאן")
        val REMINDER_WORDS = set("תזכיר", "תזכירי", "תזכירו", "הזכר", "להזכיר", "תזכורת", "תרשום", "תקבע", "קבע", "תוסיף", "תיצור", "שים", "תשים",
            "תכניס", "תעדכן", "תודיע", "אותי", "אל", "תתן", "לשכוח", "שלא", "אשכח", "תדאג", "חייב", "לזכור", "תזכור", "לי")
        val CALL_WORDS = set("תתקשר", "תתקשרי", "תתקשרו", "התקשר", "התקשרי", "להתקשר", "תחייג", "תחייגי", "תחייגו", "חייג", "חייגי", "לחייג", "תצלצל",
            "תצלצלי", "צלצל", "צלצלי", "לצלצל", "תטלפן", "טלפן", "טלפני", "לטלפן", "תוציא", "שיחה", "שיחת", "טלפון", "תרים", "תעשה", "תפתח", "תחבר",
            "אותי", "לדבר", "עם", "אל", "תשיג", "תביא", "תשים", "תעלה", "על", "הקו", "בטלפון", "בנייד", "לנייד", "לפלאפון", "לטלפון")
        val SONG_WORDS = set("תנגן", "תנגני", "נגן", "תשמיע", "השמע", "תשים", "שים", "תפעיל", "הפעל", "לשמוע", "לנגן", "תוכל", "את", "השיר", "שיר",
            "משהו", "שירים", "השירים", "מוזיקה", "קצת", "האלבום")

        private val UNITS = mapOf("אחת" to 1, "אחד" to 1, "שתיים" to 2, "שתים" to 2, "שניים" to 2, "שנים" to 2, "שתי" to 2, "שני" to 2, "שלוש" to 3, "שלושה" to 3,
            "שלושת" to 3, "ארבע" to 4, "ארבעה" to 4, "ארבעת" to 4, "חמש" to 5, "חמישה" to 5, "חמשת" to 5, "שש" to 6, "שישה" to 6, "ששת" to 6, "שבע" to 7,
            "שבעה" to 7, "שבעת" to 7, "שמונה" to 8, "שמונת" to 8, "תשע" to 9, "תשעה" to 9, "תשעת" to 9, "עשר" to 10, "עשרה" to 10, "עשרת" to 10)
            .mapKeys { n(it.key) }
        private val TENS = mapOf("עשרים" to 20, "שלושים" to 30, "ארבעים" to 40, "חמישים" to 50, "שישים" to 60, "שבעים" to 70, "שמונים" to 80, "תשעים" to 90)
            .mapKeys { n(it.key) }
        private val HOURS_WORD = mapOf("שעה" to 1, "שעתיים" to 2).mapKeys { n(it.key) }
        private val MIN_UNIT = set("דקה", "דקות", "דק", "דקת")
        private val HOUR_UNIT = set("שעה", "שעות")
        private val SEC_UNIT = set("שניה", "שנייה", "שניות")
        private val WEEKDAYS = mapOf("ראשון" to DayOfWeek.SUNDAY, "שני" to DayOfWeek.MONDAY, "שלישי" to DayOfWeek.TUESDAY, "רביעי" to DayOfWeek.WEDNESDAY,
            "חמישי" to DayOfWeek.THURSDAY, "שישי" to DayOfWeek.FRIDAY, "שבת" to DayOfWeek.SATURDAY).mapKeys { n(it.key) }

        // זמני היום: מילת מפתח → מזהה (הראשונה שמתאימה)
        private val ZMANIM = listOf(
            "הדלקת" to "candles", "נרות" to "candles", "כניסת" to "candles", "נכנסת" to "candles", "מתחילה" to "candles",
            "יציאת" to "shabbat_end", "יוצאת" to "shabbat_end", "צאת" to "shabbat_end", "מוצאי" to "shabbat_end", "נגמרת" to "shabbat_end",
            "הצום" to "fast", "צום" to "fast", "שקיעה" to "sunset", "השקיעה" to "sunset", "שקיעת" to "sunset", "שוקעת" to "sunset",
            "זריחה" to "sunrise", "הזריחה" to "sunrise", "הנץ" to "sunrise", "זורחת" to "sunrise", "חצות" to "chatzot", "עלות" to "dawn",
            "שמע" to "shema", "תפילה" to "tefila", "שחרית" to "tefila", "פלג" to "plag", "מנחה" to "mincha", "זמני" to "all", "זמנים" to "all", "הזמנים" to "all"
        ).map { n(it.first) to it.second }
    }

    // ---------- מילים במשפט ----------
    private class Tok(text: String) {
        val orig: List<String> = text.replace(Regex("([בלמ])-(\\d)"), "$1$2").replace(Regex("[^\\p{L}\\p{N}:.%]+"), " ").trim()
            .split(' ').map { it.trim('.') }.filter { it.isNotEmpty() }
        // כמו הנרמול של המסווג, אבל שומר נקודתיים (7:30) ומספרים עשרוניים
        val norm: List<String> = orig.map { w ->
            if (Regex("^[בלמ]?\\d+(\\.\\d+)?%?$").matches(w)) w.removeSuffix("%")
            else w.split(':').joinToString(":") { n(it).replace(" ", "") }
        }

        fun has(w: String) = norm.contains(n(w))

        // מספר ראשון במשפט: ספרות (כולל עשרוני / אחוזים) או מילים ("מאתיים וחמישים", "שלושים")
        fun amount(): Double? {
            for (w in orig) Regex("^[בלמ]?-?(\\d+(?:\\.\\d+)?)%?$").find(w)?.let { return it.groupValues[1].toDouble() }
            for (i in norm.indices) wordNumber(i)?.let { return it.toDouble() }
            return null
        }

        fun percent(): Double? = when {
            has("מקסימום") || has("למקסימום") || has("מלא") || has("הכי חזק") -> 100.0
            has("מינימום") || has("למינימום") || has("הכי חלש") -> 0.0
            has("חצי") || has("לחצי") -> 50.0
            else -> amount()
        }

        private val HUNDREDS = mapOf("מאה" to 100, "מאתיים" to 200, "אלף" to 1000, "אלפיים" to 2000).mapKeys { n(it.key) }

        // מספר במילים שמתחיל במילה i: מאות + עשרות + יחידות ("שלוש מאות ועשרים וחמש")
        fun wordNumber(i0: Int): Int? {
            var i = i0; var total = 0; var any = false
            fun word(k: Int) = norm.getOrNull(k)?.let { w -> if (k > i0 && w.startsWith("ו") && w.length > 2) w.substring(1) else w.removePrefix("ל").takeIf { k == i0 && it != w && (UNITS.containsKey(it) || TENS.containsKey(it) || HUNDREDS.containsKey(it)) } ?: w }
            val w0 = word(i)
            if (w0 != null && UNITS.containsKey(w0) && norm.getOrNull(i + 1) == n("מאות")) { total += UNITS[w0]!! * 100; i += 2; any = true }
            else if (w0 != null && HUNDREDS.containsKey(w0)) { total += HUNDREDS[w0]!!; i++; any = true }
            word(i)?.let { w -> TENS[w]?.let { total += it; i++; any = true } }
            word(i)?.let { w -> UNITS[w]?.let { u ->
                if (norm.getOrNull(i + 1) == n("עשרה") || norm.getOrNull(i + 1) == n("עשר")) { total += 10 + u; i += 2 } else { total += u; i++ }
                any = true } }
            return if (any) total else null
        }

        fun dayOffset(): Int = when {
            has("מחרתיים") -> 2
            has("מחר") -> 1
            else -> 0
        }

        // מספר שמתחיל במילה i (במילים או בספרות). מחזיר ערך ומספר המילים שנצרכו
        fun number(i: Int): Pair<Int, Int>? {
            if (i >= norm.size) return null
            val w = stripPrefix(norm[i])
            w.toIntOrNull()?.let { return it to 1 }
            TENS[w]?.let { t ->
                if (i + 1 < norm.size && norm[i + 1].startsWith("ו")) UNITS[norm[i + 1].substring(1)]?.let { return t + it to 2 }
                return t to 1
            }
            UNITS[w]?.let { u ->
                if (u < 10 && i + 1 < norm.size && (norm[i + 1] == n("עשרה") || norm[i + 1] == n("עשר"))) return 10 + u to 2
                return u to 1
            }
            return null
        }

        private fun stripPrefix(w: String): String {
            if (w.toIntOrNull() != null) return w
            if (UNITS.containsKey(w) || TENS.containsKey(w)) return w
            for (p in listOf("ב", "ל", "ו", "של", "וב", "ול")) {
                if (w.startsWith(p)) {
                    val r = w.substring(p.length)
                    if (r.toIntOrNull() != null || UNITS.containsKey(r) || TENS.containsKey(r)) return r
                }
            }
            return w
        }

        // משך זמן: "עשר דקות", "חצי שעה", "שעה וחצי", "שעתיים", "רבע שעה", "שלושת רבעי שעה", "30 שניות", "דקה וחצי"
        fun duration(): Duration? {
            var total = Duration.ZERO
            var found = false
            var i = 0
            while (i < norm.size) {
                val w = stripUnitPrefix(norm[i])
                val next = norm.getOrNull(i + 1)
                when {
                    w == n("חצי") && next != null && stripUnitPrefix(next) in HOUR_UNIT -> { total += Duration.ofMinutes(30); found = true; i += 2; continue }
                    w == n("חצי") && next != null && stripUnitPrefix(next) in MIN_UNIT -> { total += Duration.ofSeconds(30); found = true; i += 2; continue }
                    w == n("רבע") && next != null && stripUnitPrefix(next) in HOUR_UNIT -> { total += Duration.ofMinutes(15); found = true; i += 2; continue }
                    w == n("שלושת") && next == n("רבעי") -> { total += Duration.ofMinutes(45); found = true; i += 3; continue }
                    w == n("דקותיים") -> { total += Duration.ofMinutes(2); found = true }
                    w in HOURS_WORD.keys && (i == 0 || number(i - 1) == null) -> { total += Duration.ofHours(HOURS_WORD[w]!!.toLong()); found = true }
                    w in MIN_UNIT && (i == 0 || number(i - 1) == null) -> { total += Duration.ofMinutes(1); found = true }
                    w == n("וחצי") && found -> {
                        // "שעה וחצי" / "דקה וחצי"
                        val prev = stripUnitPrefix(norm[i - 1])
                        total += if (prev in MIN_UNIT) Duration.ofSeconds(30) else Duration.ofMinutes(30)
                    }
                    w == n("ורבע") && found -> total += Duration.ofMinutes(15)
                    else -> {
                        val num = number(i)
                        if (num != null) {
                            val unit = norm.getOrNull(i + num.second)?.let { stripUnitPrefix(it) }
                            when (unit) {
                                in MIN_UNIT -> { total += Duration.ofMinutes(num.first.toLong()); found = true; i += num.second + 1; continue }
                                in HOUR_UNIT -> { total += Duration.ofHours(num.first.toLong()); found = true; i += num.second + 1; continue }
                                in SEC_UNIT -> { total += Duration.ofSeconds(num.first.toLong()); found = true; i += num.second + 1; continue }
                            }
                        }
                    }
                }
                i++
            }
            return if (found && !total.isZero) total else null
        }

        private fun stripUnitPrefix(w: String): String =
            if (w.length > 2 && (w[0] == 'ל' || w[0] == 'ב') && (w.substring(1) in MIN_UNIT || w.substring(1) in HOUR_UNIT || w.substring(1) in SEC_UNIT ||
                    w.substring(1) == n("חצי") || w.substring(1) == n("רבע") || w.substring(1) in HOURS_WORD.keys)) w.substring(1) else w

        // שעה: "בשש וחצי", "רבע לשבע", "בשבע ועשרים", "ב-7:30", "בשעה שמונה בערב", "מחר בחמש"
        fun clockTime(now: LocalDateTime): LocalDateTime? {
            var hour = -1; var minute = 0
            for (i in norm.indices) {
                val w = norm[i]
                // 7:30
                Regex("^(?:ב|ל|בשעה|לשעה)?(\\d{1,2}):(\\d{2})$").find(w)?.let {
                    hour = it.groupValues[1].toInt(); minute = it.groupValues[2].toInt()
                }
                if (hour >= 0) break
                // רבע ל-X / עשרים ל-X / עשרה ל-X / חמש ל-X
                val before = mapOf(n("רבע") to 15, n("עשרים") to 20, n("עשרה") to 10, n("חמש") to 5)
                val bw = if (w.length > 3 && (w[0] == 'ב' || w[0] == 'ל')) w.substring(1) else w
                if (bw in before && i + 1 < norm.size && norm[i + 1].startsWith("ל")) {
                    val h = hourWord(norm[i + 1].substring(1))
                    if (h != null) { hour = (h + 11) % 12; if (hour == 0) hour = 12; minute = 60 - before[bw]!!; break }
                }
                // ב/לשעה X
                val isAt = w.startsWith("ב") || w.startsWith("ל") || (i > 0 && (norm[i - 1] == n("בשעה") || norm[i - 1] == n("לשעה") || norm[i - 1] == n("השעה")))
                if (!isAt) continue
                val h = hourWord(if (i > 0 && (norm[i - 1] == n("בשעה") || norm[i - 1] == n("לשעה") || norm[i - 1] == n("השעה"))) w else w.substring(1)) ?: continue
                hour = h
                // דקות אחרי: וחצי / ורבע / ועשרים / וחמש / ו-X דקות / "שתים עשרה"
                var j = i + 1
                if (j < norm.size && (norm[j] == n("עשרה") || norm[j] == n("עשר")) && h in 1..2) { hour = 10 + h; j++ }
                val m = norm.getOrNull(j)
                minute = when {
                    m == null -> 0
                    m == n("וחצי") -> 30
                    m == n("ורבע") -> 15
                    m.startsWith("ו") -> {
                        val rest = m.substring(1)
                        val t = TENS[rest]
                        if (t != null) {
                            val u = norm.getOrNull(j + 1)?.takeIf { it.startsWith("ו") }?.substring(1)?.let { UNITS[it] }
                            t + (u ?: 0)
                        } else UNITS[rest] ?: 0
                    }
                    else -> 0
                }
                break
            }
            if (hour < 0) return null
            val part = when {
                has("בבוקר") -> "am"
                has("בערב") || has("אחהצ") || (has("אחרי") && has("הצהריים")) -> "pm"
                has("בלילה") -> if (hour in 7..11) "pm" else "am"
                has("בצהריים") -> if (hour in 1..5) "pm" else "am"
                else -> null
            }
            var h24 = hour % 24
            if (part == "pm" && h24 < 12) h24 += 12
            if (part == "am" && h24 == 12) h24 = 0
            var day = now.toLocalDate().plusDays(dayOffset().toLong())
            weekday()?.let { day = now.toLocalDate().with(TemporalAdjusters.next(it)) }
            var t = day.atTime(h24, minute.coerceIn(0, 59))
            if (part == null && hour <= 12 && dayOffset() == 0 && weekday() == null) {
                // בלי בוקר/ערב: השעה הקרובה הבאה (6 → 06:00 או 18:00)
                val am = day.atTime(hour % 12, minute)
                val pm = day.atTime(hour % 12 + 12, minute)
                t = listOf(am, pm, am.plusDays(1)).first { it.isAfter(now) }
            } else if (!t.isAfter(now) && dayOffset() == 0 && weekday() == null) t = t.plusDays(1)
            return t
        }

        private fun hourWord(w: String): Int? {
            w.toIntOrNull()?.let { return it.takeIf { h -> h in 0..23 } }
            return UNITS[w]?.takeIf { it in 1..10 } ?: if (w == n("אחת")) 1 else null
        }

        fun weekday(): DayOfWeek? {
            val i = norm.indexOf(n("ביום")).takeIf { it >= 0 } ?: norm.indexOf(n("יום")).takeIf { it >= 0 }
            if (i != null) WEEKDAYS[norm.getOrNull(i + 1)]?.let { return it }
            norm.forEach { w -> if (w.startsWith("ב")) WEEKDAYS[w.substring(1)]?.let { if (it != DayOfWeek.SATURDAY) return it } }
            return null
        }

        // מה שנשאר אחרי שמורידים מילות פקודה, מילות מילוי, ביטויי זמן — בכתיב המקורי
        fun remainder(cmd: Set<String>): String {
            val drop = BooleanArray(orig.size)
            for (i in norm.indices) {
                val w = norm[i]
                if (w in cmd || w in FILLER) drop[i] = true
            }
            // ביטויי זמן
            for (i in norm.indices) {
                val w = norm[i]
                if (w == n("בעוד") || w == n("עוד") || w == n("מחר") || w == n("מחרתיים") || w == n("היום") || w == n("הערב") || w == n("בבוקר") || w == n("בערב") ||
                    w == n("בלילה") || w == n("בצהריים") || w == n("בשעה") || w == n("לשעה") || w == n("ביום")) drop[i] = true
                if (w == n("ביום") && i + 1 < norm.size) drop[i + 1] = true
                if (number(i) != null && (w.startsWith("ב") || w.startsWith("ל") || i > 0 && drop[i - 1])) {
                    val (_, k) = number(i)!!
                    for (j in i until minOf(norm.size, i + k)) drop[j] = true
                    val u = norm.getOrNull(i + k)
                    if (u != null && (u in MIN_UNIT || u in HOUR_UNIT || u in SEC_UNIT || u == n("וחצי") || u == n("ורבע") || u.startsWith("ו") && (TENS.containsKey(u.substring(1)) || UNITS.containsKey(u.substring(1))))) drop[i + k] = true
                }
                if (w in MIN_UNIT || w in HOUR_UNIT || w in HOURS_WORD.keys || w == n("חצי") || w == n("רבע") || w == n("וחצי") || w == n("ורבע") || w == n("דקותיים")) {
                    if (i > 0 && (drop[i - 1] || norm[i - 1] == n("חצי") || norm[i - 1] == n("רבע"))) drop[i] = true
                    if (w == n("חצי") || w == n("רבע")) drop[i] = drop[i] || (i > 0 && drop[i - 1])
                }
                if ((w == n("ברבע") || w == n("רבע") || w == n("בעשרים") || w == n("בעשרה")) && norm.getOrNull(i + 1)?.startsWith("ל") == true && hourWord(norm[i + 1].substring(1)) != null) {
                    drop[i] = true; drop[i + 1] = true
                }
            }
            return orig.filterIndexed { i, _ -> !drop[i] }.joinToString(" ").trim()
        }

        // שם איש הקשר: מה שנשאר אחרי מילות החיוג, בלי ה-ל בהתחלה ("לאמא" → "אמא")
        fun contactName(): String? {
            val r = remainder(CALL_WORDS).split(' ').filter { it.isNotEmpty() }.toMutableList()
            if (r.isEmpty()) return null
            if (r.size > 1 && n(r.last()) in setOf(n("בבית"), n("לבית"))) r.removeAt(r.size - 1)
            if (r.size > 1 && r[0] == "את") r.removeAt(0)
            r[0] = r[0].let { if (it.startsWith("ל") && it.length > 2) it.substring(1) else it }
            return r.joinToString(" ").ifBlank { null }
        }

        // שיר: "תנגן את ידיד נפש של אברהם פריד" → ("ידיד נפש", "אברהם פריד"); "משהו של X" → (null, "X")
        fun song(): Pair<String?, String?> {
            val shel = norm.indexOf(n("של"))
            val artist = if (shel >= 0) orig.drop(shel + 1).filter { n(it) !in FILLER }.joinToString(" ").ifBlank { null } else null
            val head = if (shel >= 0) Tok(orig.take(shel).joinToString(" ")) else this
            val q = head.remainder(SONG_WORDS).ifBlank { null }
            return q to artist
        }

        fun zman(): String {
            for ((k, v) in ZMANIM) if (norm.any { it == k || (it.length > 3 && it.substring(1) == k) }) {
                if (v == "fast") return if (has("סוף") || has("נגמר") || has("צאת") || has("יציאת")) "fast_end" else "fast_start"
                if (v == "mincha" && has("גדולה")) return "mincha"
                if (v == "shema" || v == "tefila") return v
                if (v == "chatzot" && has("הלילה")) return "chatzot_night"
                return v
            }
            if (has("שבת") || has("השבת")) return if (has("עד")) "candles" else "candles"
            return "all"
        }
    }
}
