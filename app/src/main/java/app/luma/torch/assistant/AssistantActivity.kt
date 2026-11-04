package app.luma.torch.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import app.luma.torch.BaseActivity
import app.luma.torch.R

// הגדרות עזר: כרטיסיות — היי עזר, הקראת תשובות, זמני היום, הגדרות נוספות
class AssistantActivity : BaseActivity() {
    private lateinit var hotSub: TextView
    private lateinit var speakSub: TextView
    private lateinit var citySub: TextView
    private lateinit var remSub: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Voice.init(this)
        val hot = navCard(R.drawable.ic_mic, "היי ${Assistant.NAME}") { startActivity(Intent(this, HotwordActivity::class.java)) }
        val speak = navCard(R.drawable.ic_speaker, "הקראת תשובות") { startActivity(Intent(this, SpeakActivity::class.java)) }
        val rem = navCard(R.drawable.ic_alarm, "תזכורות") { startActivity(Intent(this, RemindersActivity::class.java)) }
        val city = navCard(R.drawable.ic_sun, "זמני היום") { pickCity() }
        val more = navCard(R.drawable.ic_settings, "הגדרות נוספות") { startActivity(Intent(this, MoreSettingsActivity::class.java)) }
        more.second.text = "הרשאות, עוזר המערכת, בועת הצ׳אט"
        hotSub = hot.second; speakSub = speak.second; citySub = city.second; remSub = rem.second
        page(header("הגדרות ${Assistant.NAME}"), hot.first, speak.first, rem.first, city.first, more.first)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val p = Assistant.prefs(this)
        hotSub.text = if (Hotword.enabled(this)) "פועל" + (if (Hotword.chargingOnly(this)) " · רק בזמן טעינה" else "") else "כבוי"
        speakSub.text = "בודק…"
        Voice.whenReady(this) {
            speakSub.text = when {
                !Voice.hebrew -> "אין קול עברי זמין"
                p.getBoolean("speak", true) -> "פועל · קול עברי זמין"
                else -> "כבוי · קול עברי זמין"
            }
        }
        citySub.text = Zmanim.city(this).name
        remSub.text = Assistant.openReminders(this).size.let { n -> when (n) { 0 -> "אין תזכורות פתוחות"; 1 -> "תזכורת אחת פתוחה"; else -> "$n תזכורות פתוחות" } }
    }

    private fun pickCity() {
        val names = Zmanim.CITIES.map { it.name }.toTypedArray()
        dlg().setTitle("עיר לזמני היום").setSingleChoiceItems(names, Assistant.prefs(this).getInt("city", 0)) { d, i ->
            Assistant.prefs(this).edit().putInt("city", i).apply(); d.dismiss(); refresh()
        }.show()
    }
}
