package app.luma.torch.assistant

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import app.luma.torch.BaseActivity

// הקראת תשובות: המתג זמין רק כשיש קול עברי. בלי קול — "אין קול עברי זמין" וכפתור התקנה;
// עם קול — כפתור "השמעת דוגמה"
class SpeakActivity : BaseActivity() {
    private lateinit var sw: Switch
    private lateinit var msg: TextView
    private lateinit var install: TextView
    private lateinit var sample: TextView
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private var busy = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Assistant.prefs(this)
        sw = settingSwitch("הקראת תשובות").apply {
            isChecked = p.getBoolean("speak", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("speak", c).apply() }
        }
        msg = TextView(this).apply { textSize = 13f; setPadding(0, dp(10), 0, 0) }
        install = tv("התקנת קול עברי") { doInstall() }.apply { style(this, true) }
        sample = tv("השמעת דוגמה") { Voice.speak(this, "שלום, אני ${Assistant.NAME}") }.apply { style(this, false) }
        val gap = { View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(10)) } }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10))
            addView(msg); addView(gap()); addView(install); addView(sample)
        }
        page(header("הקראת תשובות"), plainCard(sw, View(this).apply { setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }, body))
        Voice.init(this)
        paint()
    }

    override fun onResume() { super.onResume(); paint() }

    private fun paint() {
        if (busy) return
        if (!Voice.ready) {
            grey(sw, true); msg.text = "בודק…"; msg.setTextColor(dim)
            install.visibility = View.GONE; sample.visibility = View.GONE
            Voice.whenReady(this) { paint() }
            return
        }
        val he = Voice.hebrew
        grey(sw, !he)
        msg.visibility = if (he) View.GONE else View.VISIBLE
        msg.text = "אין קול עברי זמין"; msg.setTextColor(warnC)
        install.visibility = if (!he && TtsRestore.available(this)) View.VISIBLE else View.GONE
        sample.visibility = if (he) View.VISIBLE else View.GONE
    }

    private fun doInstall() {
        if (busy) return
        busy = true; install.isEnabled = false; install.text = "מתקין…"
        TtsRestore.install(this) { r ->
            busy = false; install.isEnabled = true; install.text = "התקנת קול עברי"
            paint()   // אחרי התקנה מוצלחת עזר כבר בדק מחדש: אם יש קול — הכפתור נעלם והמתג נדלק
            if (r == TtsRestore.Result.NO_ROOT) { msg.text = "אין הרשאת root להתקנת הקול"; msg.visibility = View.VISIBLE }
            else if (r == TtsRestore.Result.FAIL || !Voice.hebrew) { msg.text = "התקנת הקול נכשלה"; msg.visibility = View.VISIBLE }
        }
    }
}
