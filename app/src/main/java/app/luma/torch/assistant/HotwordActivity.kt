package app.luma.torch.assistant

import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import app.luma.torch.BaseActivity

// הגדרות "היי עזר": מתג הפעלה ראשי; כל עוד הוא כבוי — שאר המתגים אפורים והשירות מושבת
class HotwordActivity : BaseActivity() {
    private lateinit var master: Switch
    private lateinit var subs: List<Switch>

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = Assistant.prefs(this)
        master = settingSwitch("הפעלת היי ${Assistant.NAME}")
        val charge = settingSwitch("רק בזמן טעינה")
        val locked = settingSwitch("להקשיב גם כשהמסך נעול")
        val off = settingSwitch("להקשיב גם כשהמסך כבוי")
        subs = listOf(charge, locked, off)
        val keys = listOf("hotword_charging", "hotword_locked", "hotword_off")
        master.isChecked = p.getBoolean("hotword", false)
        subs.forEachIndexed { i, s -> s.isChecked = p.getBoolean(keys[i], false) }
        master.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("hotword", c).apply(); paint(); Hotword.sync(this) }
        subs.forEachIndexed { i, s -> s.setOnCheckedChangeListener { _, c -> p.edit().putBoolean(keys[i], c).apply(); Hotword.sync(this) } }

        val note = TextView(this).apply {
            text = "שירות היי ${Assistant.NAME} מאפשר לומר את המילה היי ${Assistant.NAME} כשהתכונה דלוקה ולפתוח את העוזר האישי, " +
                "וכן אפשר לומר היי ${Assistant.NAME} ופקודה וזה יתבצע מייד.\nהשירות לוקח סוללה ולכן מומלץ להפעיל אותו רק כשצריך."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.3f); setPadding(dp(4), dp(4), dp(4), 0)
        }
        page(header("היי ${Assistant.NAME}"), plainCard(master), plainCard(charge, locked, off), note)
        paint()
    }

    private fun paint() = subs.forEach { grey(it, !master.isChecked) }
}
