package app.luma.torch.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.app.Notification
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import app.luma.torch.CameraButtonService
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.round

// פקודות נוספות של עזר: מחשבון, המרת יחידות, לוח שנה, התראות, לוח (העתק), פעולות במסך,
// אפליקציות, חיפוש, חזרה על התשובה, מספר אקראי, תמונות וחנות.
object Extras {
    private val main = Handler(Looper.getMainLooper())
    private fun n(s: String) = IntentEngine.n(s)
    private fun set(vararg w: String) = w.map { n(it) }.toHashSet()
    private fun toast(c: Context, t: String) = main.post { Toast.makeText(c.applicationContext, t, Toast.LENGTH_SHORT).show() }
    private fun fmt(v: Double): String {
        if (abs(v - round(v)) < 1e-9) return "%.0f".format(v)
        val s = "%.2f".format(v).trimEnd('0').trimEnd('.')
        return s
    }

    // ---------- מחשבון ----------
    private val PLUS = set("ועוד", "פלוס", "+")
    private val MINUS = set("פחות", "מינוס", "-")
    private val TIMES = set("כפול", "כפל", "x", "×", "*")
    private val DIV = set("חלקי", "חלק", "לחלק", "מחולק", "/", "÷")
    private val PERCENT_OF = set("אחוז", "אחוזים", "%")

    // סימנים מוקלדים (5+3, 10/2, 4*3, 7-2) → מילים, כי הניקוי של הטקסט מוחק סימנים
    private fun symbols(s: String) = s
        .replace(Regex("(\\d)\\s*-\\s*(?=\\d)"), "$1 פחות ")
        .replace(Regex("(\\d)\\s*[xX]\\s*(?=\\d)"), "$1 כפול ")
        .replace("+", " ועוד ").replace("*", " כפול ").replace("×", " כפול ")
        .replace("/", " חלקי ").replace("÷", " חלקי ").replace("%", " % ")

    // חשבון רק כשהמשפט נשמע כמו חשבון: מתחיל ב"כמה" / "תחשב" / "מה זה", או שכולו מספרים ופעולות.
    // ככה "תוסיף לקניות 2 חלב ועוד 3 לחם" לא נענה ב"התוצאה: 5"
    private val MATH_START = listOf("כמה", "תחשב", "תחשבי", "חשב", "לחשב", "מה זה", "מה התוצאה", "מה יוצא").map { n(it) }
    fun isMath(said: String): Boolean {
        val t = IntentEngine.Tok(symbols(said))
        val words = t.norm.filter { it.isNotBlank() && it !in IntentEngine.FILLER }
        if (words.isEmpty()) return false
        val joined = words.joinToString(" ")
        if (MATH_START.any { joined == it || joined.startsWith("$it ") }) return true
        var i = 0
        while (i < t.norm.size) {
            val w = t.norm[i]
            when {
                w.isBlank() || w in IntentEngine.FILLER -> i++
                w.trimStart('ב', 'ל', 'ו').toDoubleOrNull() != null -> i++
                t.number(i) != null -> i += t.number(i)!!.second
                w in PLUS || w in MINUS || w in TIMES || w in DIV || w in PERCENT_OF -> i++
                else -> return false
            }
        }
        return true
    }

    // מחזיר תשובה אם במשפט יש חשבון (שני מספרים לפחות ופעולה), אחרת null
    fun calc(said: String): Assistant.Reply? {
        val t = IntentEngine.Tok(symbols(said))
        val nums = mutableListOf<Double>(); val ops = mutableListOf<Char>()
        var i = 0; var pendingPercent = false
        while (i < t.norm.size) {
            val w = t.norm[i]
            val dec = w.trimStart('ב', 'ל', 'ו').toDoubleOrNull()
            val num = dec?.let { it to 1 } ?: t.number(i)?.let { it.first.toDouble() to it.second }
            when {
                num != null -> {
                    if (nums.size > ops.size) { i++; continue } // שני מספרים ברצף — מתעלמים מהשני
                    nums += num.first; i += num.second; continue
                }
                w in PLUS && nums.size > ops.size -> ops += '+'
                w in MINUS && nums.size > ops.size -> ops += '-'
                w in TIMES && nums.size > ops.size -> ops += '*'
                w in DIV && nums.size > ops.size -> ops += '/'
                w in PERCENT_OF && nums.size > ops.size -> { ops += '%'; pendingPercent = true }
            }
            i++
        }
        if (nums.size < 2 || ops.isEmpty()) return null
        while (ops.size >= nums.size) ops.removeAt(ops.size - 1)
        // קודם כפל, חילוק ואחוזים, אחר כך חיבור וחיסור
        val v = nums.toMutableList(); val o = ops.toMutableList()
        var k = 0
        while (k < o.size) {
            val op = o[k]
            if (op == '*' || op == '/' || op == '%') {
                if (op == '/' && v[k + 1] == 0.0) return Assistant.Reply("אי אפשר לחלק באפס")
                v[k] = when (op) { '*' -> v[k] * v[k + 1]; '/' -> v[k] / v[k + 1]; else -> v[k] / 100.0 * v[k + 1] }
                v.removeAt(k + 1); o.removeAt(k)
            } else k++
        }
        var res = v[0]
        for (j in o.indices) res = if (o[j] == '+') res + v[j + 1] else res - v[j + 1]
        return Assistant.Reply("התוצאה: ${fmt(res)}")
    }

    // ---------- המרת יחידות ----------
    private data class Unt(val name: String, val kind: String, val f: Double)
    private val UNITS: Map<String, Unt> by lazy {
        val m = HashMap<String, Unt>()
        fun u(kind: String, f: Double, name: String, vararg words: String) { for (w in listOf(*words, name)) m[n(w)] = Unt(name, kind, f) }
        u("len", 0.001, "מילימטר", "מילימטרים", "ממ", "מ״מ")
        u("len", 0.01, "סנטימטר", "סנטימטרים", "סמ", "ס״מ", "סנטי")
        u("len", 1.0, "מטר", "מטרים")
        u("len", 1000.0, "קילומטר", "קילומטרים", "קמ", "ק״מ")
        u("len", 0.0254, "אינץ׳", "אינץ", "אינצ׳ים", "אינצים")
        u("len", 0.3048, "רגל", "רגליים", "פיט")
        u("len", 0.9144, "יארד", "יארדים")
        u("len", 1609.344, "מייל", "מיילים", "מיל")
        u("mass", 0.001, "גרם", "גרמים")
        u("mass", 1.0, "קילוגרם", "קילוגרמים", "קילו", "קג", "ק״ג")
        u("mass", 1000.0, "טון", "טונות")
        u("mass", 0.45359237, "ליברה", "ליברות", "פאונד", "פאונדים")
        u("mass", 0.0283495, "אונקיה", "אונקיות")
        u("vol", 0.001, "מיליליטר", "מיליליטרים", "מל", "מ״ל")
        u("vol", 1.0, "ליטר", "ליטרים")
        u("vol", 0.24, "כוס", "כוסות")
        u("vol", 0.015, "כף", "כפות")
        u("vol", 0.005, "כפית", "כפיות")
        u("vol", 3.785411784, "גלון", "גלונים")
        u("time", 1.0, "שנייה", "שניות")
        u("time", 60.0, "דקה", "דקות")
        u("time", 3600.0, "שעה", "שעות")
        u("time", 86400.0, "יום", "ימים")
        u("time", 604800.0, "שבוע", "שבועות")
        u("temp", 0.0, "צלזיוס", "מעלות צלזיוס")
        u("temp", 1.0, "פרנהייט", "פרנהיט")
        u("temp", 2.0, "קלווין", "קלוין")
        m
    }
    private fun unitOf(w: String): Unt? = UNITS[w] ?: UNITS[w.drop(1)].takeIf { w.length > 2 && w[0] in "בלוה" }

    fun convert(said: String): Assistant.Reply {
        val t = IntentEngine.Tok(said)
        var value: Double? = null
        val units = mutableListOf<Unt>()
        var i = 0
        while (i < t.norm.size) {
            val w = t.norm[i]
            if (value == null) {
                val d = w.toDoubleOrNull() ?: w.trimStart('ב', 'ל').toDoubleOrNull()
                if (d != null) { value = d; i++; continue }
                val x = t.number(i)
                if (x != null) { value = x.first.toDouble(); i += x.second; continue }
            }
            unitOf(w)?.let { if (units.none { u -> u.name == it.name }) units += it }
            i++
        }
        val v = value ?: 1.0
        if (units.size < 2) return Assistant.Reply("ממה למה להמיר? למשל: כמה זה 5 מייל בקילומטרים")
        val (a, b) = units[0] to units[1]
        if (a.kind != b.kind) return Assistant.Reply("אי אפשר להמיר ${a.name} ל${b.name}")
        val r = if (a.kind == "temp") {
            val c = when (a.f) { 0.0 -> v; 1.0 -> (v - 32) * 5 / 9; else -> v - 273.15 }
            when (b.f) { 0.0 -> c; 1.0 -> c * 9 / 5 + 32; else -> c + 273.15 }
        } else v * a.f / b.f
        return Assistant.Reply("${fmt(v)} ${a.name} = ${fmt(r)} ${b.name}")
    }

    // ---------- לוח שנה ----------
    fun calendar(c: Context, said: String): Assistant.Reply {
        val u = IntentEngine().parse("reminder", 1f, said)
        val title = u.text?.split(' ')?.filter { n(it) !in set("ללוח", "לוח", "השנה", "יומן", "ביומן", "ליומן", "אירוע", "פגישה", "תקבע", "קבע", "תוסיף", "הוסף") }
            ?.joinToString(" ")?.ifBlank { null } ?: "אירוע"
        val at = u.time ?: LocalDateTime.now().plusHours(1).withMinute(0)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms + 3600_000)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(c.packageManager) == null) {
            Assistant.schedule(c, at, title)
            return Assistant.Reply("אין במכשיר לוח שנה, אז קבעתי תזכורת ב-${Assistant.hm(at)}: $title")
        }
        return Assistant.Reply("פותח את לוח השנה עם האירוע: $title", then = { c.startActivity(i) })
    }

    // ---------- התראות ----------
    fun notifications(c: Context): Assistant.Reply {
        val l = MediaListener.instance ?: return Assistant.Reply("צריך לאשר לעזרים גישה להתראות (בהגדרות עזר ← הגדרות נוספות ← הרשאות)")
        val pm = c.packageManager
        val items = try { l.activeNotifications.orEmpty() } catch (_: Exception) { emptyArray() }
            .filter { !it.isOngoing && it.packageName != c.packageName && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .mapNotNull { sb ->
                val ex = sb.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) null
                else {
                    val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sb.packageName, 0)).toString() } catch (_: Exception) { sb.packageName }
                    "מ$app: " + listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
                }
            }
        if (items.isEmpty()) return Assistant.Reply("אין התראות חדשות")
        val head = if (items.size == 1) "יש לך התראה אחת" else "יש לך ${items.size} התראות"
        return Assistant.Reply(head + ":\n" + items.take(5).joinToString("\n"))
    }

    // ---------- לוח (העתקה) ----------
    fun clipboard(c: Context): Assistant.Reply {
        val cm = c.getSystemService(ClipboardManager::class.java)
        val t = try { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString() } catch (_: Exception) { null }
        return if (t.isNullOrBlank()) Assistant.Reply("הלוח ריק") else Assistant.Reply("בלוח מועתק: $t")
    }

    // ---------- פעולות במסך (שירות הנגישות, אחרי שהחלונית נסגרת) ----------
    private fun svc(): CameraButtonService? = CameraButtonService.instance
    private fun screen(ok: String, act: (CameraButtonService) -> Unit): Assistant.Reply {
        val s = svc() ?: return Assistant.Reply(Assistant.Say.NEED_A11Y)
        return Assistant.Reply(ok, then = { main.postDelayed({ try { act(s) } catch (_: Exception) {} }, 350) })
    }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(); val q = ArrayDeque<AccessibilityNodeInfo>()
        root?.let { q.add(it) }
        while (q.isNotEmpty() && out.size < 2000) {
            val x = q.removeFirst(); out += x
            for (i in 0 until x.childCount) x.getChild(i)?.let { q.add(it) }
        }
        return out
    }
    private fun clickable(x: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var p = x; while (p != null && !p.isClickable) p = p.parent; return p
    }
    private fun findByName(s: CameraButtonService, name: String): AccessibilityNodeInfo? {
        val q = n(name)
        return nodes(s.rootInActiveWindow).firstOrNull { x ->
            val t = n(listOfNotNull(x.text, x.contentDescription).joinToString(" "))
            t.isNotBlank() && (t == q || t.contains(q))
        }
    }
    private fun focused(s: CameraButtonService) = s.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    private fun gesture(s: CameraButtonService, x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        s.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }
    private fun size(s: CameraButtonService) = s.resources.displayMetrics.let { it.widthPixels.toFloat() to it.heightPixels.toFloat() }

    private val SCREEN_WORDS = set("תלחץ", "לחץ", "תלחצי", "ללחוץ", "תקיש", "הקש", "על", "את", "הכפתור", "כפתור", "לחיצה", "ארוכה", "תעשה",
        "תכתוב", "כתוב", "תכתבי", "לכתוב", "תקליד", "הקלד", "להקליד", "בשדה", "שדה", "תחפש", "חפש", "לחפש", "חיפוש", "בתוך", "באפליקציה", "האפליקציה")
    private fun rest(said: String): String = said.split(' ').filter { it.isNotBlank() && n(it) !in SCREEN_WORDS && n(it) !in IntentEngine.FILLER }.joinToString(" ")

    fun click(c: Context, said: String): Assistant.Reply {
        val name = rest(said).ifBlank { return Assistant.Reply("על מה ללחוץ?") }
        return screen("לוחץ על $name") { s ->
            val node = clickable(findByName(s, name))
            if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) toast(c, "לא מצאתי במסך: $name")
        }
    }

    fun longPress(c: Context, said: String): Assistant.Reply {
        val name = rest(said)
        return screen(if (name.isBlank()) "לחיצה ארוכה" else "לחיצה ארוכה על $name") { s ->
            if (name.isNotBlank()) {
                var p = findByName(s, name); while (p != null && !p.isLongClickable) p = p.parent
                if (p == null || !p.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) toast(c, "לא מצאתי במסך: $name")
            } else { val (w, h) = size(s); gesture(s, w / 2, h / 2, w / 2, h / 2, 800) }
        }
    }

    fun type(c: Context, said: String): Assistant.Reply {
        val text = rest(said).ifBlank { return Assistant.Reply("מה לכתוב?") }
        return screen("כותב: $text") { s ->
            val f = focused(s)
            if (f == null) { toast(c, "אין שדה כתיבה פתוח"); return@screen }
            val cur = f.text?.toString()?.takeIf { !f.isShowingHintText }.orEmpty()
            val sep = if (cur.isNotEmpty() && !cur.endsWith(" ")) " " else ""
            f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur + sep + text) })
        }
    }

    fun scroll(down: Boolean): Assistant.Reply = screen(if (down) "גולל למטה" else "גולל למעלה") { s ->
        val sc = nodes(s.rootInActiveWindow).firstOrNull { it.isScrollable && it.isVisibleToUser }
        val ok = sc?.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
        if (!ok) { val (w, h) = size(s); if (down) gesture(s, w / 2, h * 0.75f, w / 2, h * 0.3f, 300) else gesture(s, w / 2, h * 0.3f, w / 2, h * 0.75f, 300) }
    }

    fun swipe(left: Boolean): Assistant.Reply = screen(if (left) "מחליק שמאלה" else "מחליק ימינה") { s ->
        val (w, h) = size(s)
        if (left) gesture(s, w * 0.85f, h / 2, w * 0.15f, h / 2, 250) else gesture(s, w * 0.15f, h / 2, w * 0.85f, h / 2, 250)
    }

    // העתקה / הדבקה / סימון / מחיקה בשדה הכתיבה הפעיל
    fun text(c: Context, op: String): Assistant.Reply {
        val ok = when (op) { "copy" -> "מעתיק את הטקסט"; "paste" -> "מדביק"; "select" -> "מסמן את כל הטקסט"; else -> "מוחק את הטקסט" }
        return screen(ok) { s ->
            val f = focused(s) ?: run { toast(c, "אין שדה כתיבה פתוח"); return@screen }
            val len = f.text?.length ?: 0
            fun selectAll() = f.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len) })
            when (op) {
                "copy" -> { selectAll(); f.performAction(AccessibilityNodeInfo.ACTION_COPY) }
                "paste" -> f.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                "select" -> selectAll()
                else -> f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") })
            }
        }
    }

    // הקראת המסך: הטקסטים שרואים במסך, אחרי שהחלונית נסגרת
    fun readScreen(c: Context): Assistant.Reply = screen("קורא את המסך") { s ->
        val seen = LinkedHashSet<String>()
        for (x in nodes(s.rootInActiveWindow)) {
            if (!x.isVisibleToUser) continue
            val t = (x.text ?: x.contentDescription)?.toString()?.trim().orEmpty()
            if (t.length > 1) seen += t
        }
        val all = seen.joinToString(". ").take(800)
        if (all.isBlank()) toast(c, "לא מצאתי טקסט במסך")
        else { Voice.init(c); Voice.speak(c, all); if (!Voice.hebrew) toast(c, all.take(300)) }
    }

    // ---------- אפליקציות ----------
    private val APP_EXTRA = set("פרטי", "פרטים", "מידע", "על", "הגדרות", "של", "תסיר", "הסר", "להסיר", "הסרת", "תמחק", "מחק", "למחוק", "מחיקת", "תסגור", "סגור", "לסגור")
    private fun appName(said: String) = said.split(' ').filter { it.isNotBlank() && n(it) !in APP_EXTRA && n(it) !in IntentEngine.APP_WORDS && n(it) !in IntentEngine.FILLER }.joinToString(" ")
    private fun pkgOf(c: Context, said: String): Pair<String, String>? {
        val (label, i) = Assistant.findApp(c, appName(said)) ?: return null
        val pkg = i.component?.packageName ?: i.`package` ?: return null
        return label to pkg
    }

    fun closeApp(c: Context): Assistant.Reply = screen("סוגר את האפליקציה") { s ->
        val pkg = s.rootInActiveWindow?.packageName?.toString()
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        if (pkg != null && pkg != c.packageName) main.postDelayed({
            try { c.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg) } catch (_: Exception) {}
        }, 600)
    }

    fun previousApp(): Assistant.Reply = screen("חוזר לאפליקציה הקודמת") { s ->
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        main.postDelayed({ s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }, 350)
    }

    fun clearRecents(c: Context): Assistant.Reply {
        val ok = try {
            val svc = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            svc!!.javaClass.getMethod("removeAllVisibleRecentTasks").invoke(svc); true
        } catch (_: Throwable) { false }
        return if (ok) Assistant.Reply("ניקיתי את האפליקציות האחרונות")
        else screen("פותח את האפליקציות האחרונות") { it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
    }

    fun appInfo(c: Context, said: String): Assistant.Reply {
        val (label, pkg) = pkgOf(c, said) ?: return Assistant.Reply(if (appName(said).isBlank()) "של איזו אפליקציה?" else "לא מצאתי אפליקציה בשם ${appName(said)}")
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Assistant.Reply("פותח את הפרטים של $label", then = { c.startActivity(i) })
    }

    fun uninstall(c: Context, said: String): Assistant.Reply {
        val (label, pkg) = pkgOf(c, said) ?: return Assistant.Reply(if (appName(said).isBlank()) "איזו אפליקציה להסיר?" else "לא מצאתי אפליקציה בשם ${appName(said)}")
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Assistant.Reply("פותח את חלון ההסרה של $label", then = { c.startActivity(i) })
    }

    // ---------- חיפוש ----------
    private val SEARCH_WORDS = set("תחפש", "חפש", "לחפש", "חיפוש", "תמצא", "מצא", "במכשיר", "בטלפון", "בנגן", "את", "לי", "איפה", "יש")
    fun searchDevice(c: Context, said: String): Assistant.Reply {
        val q = said.split(' ').filter { it.isNotBlank() && n(it) !in SEARCH_WORDS && n(it) !in IntentEngine.FILLER }.joinToString(" ")
        if (q.isBlank()) return Assistant.Reply("מה לחפש?")
        val nq = n(q)
        val found = mutableListOf<String>()
        val pm = c.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launch, 0).map { it.loadLabel(pm).toString() }.distinct()
            .filter { n(it).contains(nq) }.take(3).forEach { found += "אפליקציה: $it" }
        for ((key, title) in listOf("notes" to "פתק", "todo" to "משימה", "shop" to "קניות")) {
            val a = org.json.JSONArray(Assistant.prefs(c).getString("list_$key", "[]"))
            (0 until a.length()).map { a.getString(it) }.filter { n(it).contains(nq) }.take(2).forEach { found += "$title: $it" }
        }
        try {
            c.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media.TITLE), null, null, null)?.use { cur ->
                var k = 0
                while (cur.moveToNext() && k < 3) { val t = cur.getString(0) ?: continue; if (n(t).contains(nq)) { found += "שיר: $t"; k++ } }
            }
        } catch (_: Exception) {}
        return if (found.isEmpty()) Assistant.Reply("לא מצאתי במכשיר את \"$q\"") else Assistant.Reply("מצאתי:\n" + found.joinToString("\n"))
    }

    fun searchInApp(c: Context, said: String): Assistant.Reply {
        val q = rest(said).ifBlank { return Assistant.Reply("מה לחפש?") }
        return screen("מחפש: $q") { s ->
            fun put(f: AccessibilityNodeInfo) = f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, q) })
            val all = nodes(s.rootInActiveWindow)
            val field = focused(s) ?: all.firstOrNull { it.isEditable && it.isVisibleToUser }
            if (field != null) { put(field); return@screen }
            // אין שדה: לוחצים על כפתור החיפוש של האפליקציה, ואז כותבים
            val btn = all.firstOrNull { x -> val d = n(listOfNotNull(x.contentDescription, x.text).joinToString(" ")); d.contains(n("חיפוש")) || d.contains("search") }
            if (clickable(btn)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                main.postDelayed({ (focused(s) ?: nodes(s.rootInActiveWindow).firstOrNull { it.isEditable })?.let { put(it) } ?: toast(c, "לא מצאתי שדה חיפוש") }, 600)
            else toast(c, "לא מצאתי שדה חיפוש")
        }
    }

    // ---------- שונות ----------
    fun random(said: String): Assistant.Reply {
        val t = IntentEngine.Tok(said)
        val nums = mutableListOf<Int>(); var i = 0
        while (i < t.norm.size) { val x = t.number(i); if (x != null) { nums += x.first; i += x.second } else i++ }
        val (lo, hi) = when { nums.size >= 2 -> minOf(nums[0], nums[1]) to maxOf(nums[0], nums[1]); nums.size == 1 -> 1 to nums[0]; else -> 1 to 100 }
        return Assistant.Reply("המספר: ${(lo..hi).random()}")
    }

    fun photos(c: Context): Assistant.Reply {
        val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(c.packageManager) == null) return Assistant.Reply("לא נמצאה אפליקציית תמונות")
        return Assistant.Reply("פותח את התמונות", then = { c.startActivity(i) })
    }

    const val STORE_PKG = "com.example.mystore"
    fun store(c: Context): Assistant.Reply {
        val i = c.packageManager.getLaunchIntentForPackage(STORE_PKG)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return Assistant.Reply("לא מצאתי במכשיר את החנות הכשרה")
        return Assistant.Reply("פותח את החנות הכשרה", then = { c.startActivity(i) })
    }
}
