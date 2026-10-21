package app.luma.torch

import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

// דורש שעזרים תותקן כאפליקציית מערכת (priv-app) עם הרשאת
// android.permission.WRITE_SECURE_SETTINGS מאושרת בקובץ ההרשאות של הקושחה.
// אין כאן שום נפילה ל-root: בלי ההרשאה הזו המסך פשוט לא יעבוד.
class RotationActivity : BaseActivity() {
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private lateinit var sw: Switch
    private lateinit var status: TextView
    private var painting = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sw = Switch(this).apply { text = "הצג כפתור סיבוב מסך"; setTextColor(fg) }
        tint(sw)
        status = TextView(this).apply { textSize = 13f; setTextColor(dim); setPadding(0, dp(12), 0, 0); visibility = View.GONE }
        sw.setOnCheckedChangeListener { _, c -> if (!painting) change(c) }
        page(
            header("כפתור סיבוב מסך", null, "הצגה או הסתרה של הכפתור בסרגל הניווט"),
            section("הכפתור בסרגל הניווט", sw, status)
        )
        load()
    }

    private fun setChecked(v: Boolean) { painting = true; sw.isChecked = v; painting = false }
    private fun show(msg: String, color: Int) { status.text = msg; status.setTextColor(color); status.visibility = View.VISIBLE }

    // קריאת המצב הנוכחי; כשההגדרה לא קיימת ברירת המחדל של המערכת היא "מוצג"
    private fun load() {
        try {
            val v = Settings.Secure.getString(contentResolver, "show_rotation_suggestions")
            setChecked(v != "0")
        } catch (e: SecurityException) {
            sw.isEnabled = false
            show("אין לעזרים הרשאת WRITE_SECURE_SETTINGS. יש להתקין אותה כאפליקציית מערכת עם קובץ ההרשאות המתאים.", warnC)
        }
    }

    private fun change(on: Boolean) {
        try {
            Settings.Secure.putString(contentResolver, "show_rotation_suggestions", if (on) "1" else "0")
            show(if (on) "הכפתור יוצג" else "הכפתור הוסתר", acc)
        } catch (e: SecurityException) {
            setChecked(!on)
            show("אין לעזרים הרשאת WRITE_SECURE_SETTINGS. יש להתקין אותה כאפליקציית מערכת עם קובץ ההרשאות המתאים.", warnC)
        }
    }
}
