package app.luma.torch.dictation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import app.luma.torch.RootShell

// הפעלה וכיבוי של המקלדת הקולית ברשימת המקלדות של המערכת.
// אנדרואיד מכבה כל מקלדת חדשה כברירת מחדל (מקלדת יכולה לראות את מה שמקלידים, ולכן המערכת דורשת אישור).
// לעזרים יש הרשאת WRITE_SECURE_SETTINGS כאפליקציית מערכת, ולכן היא מפעילה את המקלדת בעצמה —
// אלא אם המשתמש כיבה אותה במפורש במסך ההקלדה הקולית.
object VoiceIme {
    private const val USER_OFF = "ime_user_off"
    private const val AUTO_DONE = "ime_auto_enabled"

    fun id(c: Context): String = ComponentName(c, VoiceKeyboardService::class.java).flattenToShortString()

    private fun entries(c: Context): List<String> =
        (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: "")
            .split(':').filter { it.isNotBlank() }

    fun isEnabled(c: Context): Boolean { val me = id(c); return entries(c).any { it.substringBefore(';') == me } }

    private fun canWrite(c: Context) =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    // כתיבה ישירה להגדרות; בלי הרשאה — false
    private fun write(c: Context, on: Boolean): Boolean {
        if (!canWrite(c)) return false
        return try {
            val me = id(c)
            val rest = entries(c).filter { it.substringBefore(';') != me }
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS, (if (on) rest + me else rest).joinToString(":"))
        } catch (_: Exception) { false }
    }

    // נקרא בפתיחת האפליקציה, באתחול ואחרי עדכון: מפעיל את המקלדת פעם אחת (בהפעלה הראשונה שמצליחה).
    // אחר כך הבחירה בידי המשתמש — אם כיבה אותה (כאן או בהגדרות אנדרואיד) היא לא תופעל שוב מעצמה.
    fun ensureDefault(c: Context) {
        val p = DictationPrefs.prefs(c)
        if (p.getBoolean(AUTO_DONE, false) || p.getBoolean(USER_OFF, false)) return
        if (!ModelLocator.isInstalled()) return
        if (isEnabled(c) || write(c, true)) p.edit().putBoolean(AUTO_DONE, true).apply()
    }

    // הפעלה שקטה, בלי לשנות את הבחירה השמורה (להכתבה מכפתור המצלמה, שדורשת שהמקלדת תהיה ברשימה)
    fun enableNow(c: Context): Boolean = isEnabled(c) || write(c, true)

    // מהמתג במסך: זוכר את הבחירה. אם אין הרשאה, ניסיון דרך root (בחוט רקע). מחזיר false אם לא הצליח מיד.
    fun set(c: Context, on: Boolean): Boolean {
        DictationPrefs.prefs(c).edit().putBoolean(USER_OFF, !on).apply()
        if (write(c, on)) return true
        val me = id(c)
        Thread { RootShell.run("ime " + (if (on) "enable " else "disable ") + me) }.start()
        return false
    }
}
