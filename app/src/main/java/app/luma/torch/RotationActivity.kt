package app.luma.torch

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class RotationActivity : BaseActivity() {
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private lateinit var sw: Switch
    private lateinit var status: TextView
    private var painting = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        sw = Switch(this).apply { text = "הצג כפתור סיבוב מסך"; setTextColor(fg); isEnabled = false }
        tint(sw)
        status = TextView(this).apply { textSize = 13f; setTextColor(dim); setPadding(0, dp(12), 0, 0); visibility = View.GONE }
        sw.setOnCheckedChangeListener { _, c -> if (!painting) change(c) }
        val info = TextView(this).apply {
            text = "הכפתור הקטן שקופץ בסרגל הניווט כשמסובבים את המכשיר. השינוי דורש הרשאת root."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f)
        }
        page(
            header("כפתור סיבוב מסך", null, "הצגה או הסתרה של הכפתור בסרגל הניווט"),
            section("הכפתור בסרגל הניווט", sw, status),
            section("מידע", info)
        )
        load()
    }

    private fun setChecked(v: Boolean) { painting = true; sw.isChecked = v; painting = false }

    private fun show(msg: String, color: Int) { status.text = msg; status.setTextColor(color); status.visibility = View.VISIBLE }

    private fun err(code: Int, out: String) =
        if (code == -1 || out.contains("not found", true) || out.contains("denied", true))
            "לא נמצאה גישת root או שהיא נדחתה. אשר הרשאת root לאפליקציה ונסה שוב."
        else "הפקודה נכשלה (קוד $code)."

    // קריאת המצב הנוכחי; כשההגדרה לא קיימת ברירת המחדל של המערכת היא "מוצג"
    private fun load() {
        show("בודק מצב נוכחי…", dim)
        Thread {
            val (code, out) = runAsRoot("settings get secure show_rotation_suggestions")
            runOnUiThread {
                sw.isEnabled = true
                if (code == 0) { setChecked(out.trim() != "0"); status.visibility = View.GONE }
                else show(err(code, out), warnC)
            }
        }.start()
    }

    private fun change(on: Boolean) {
        sw.isEnabled = false
        Thread {
            val (code, out) = runAsRoot("settings put secure show_rotation_suggestions ${if (on) 1 else 0}")
            runOnUiThread {
                sw.isEnabled = true
                if (code == 0) show(if (on) "הכפתור יוצג" else "הכפתור הוסתר", acc)
                else { setChecked(!on); show(err(code, out), warnC) }
            }
        }.start()
    }
}
