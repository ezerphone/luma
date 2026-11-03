package app.luma.torch.assistant

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import app.luma.torch.BaseActivity
import app.luma.torch.R

// הגדרות עזר: כרטיסיות — היי עזר, הקראת תשובות, זמני היום, הרשאות, עוזר המערכת
class AssistantActivity : BaseActivity() {
    private lateinit var hotSub: TextView
    private lateinit var speakSub: TextView
    private lateinit var citySub: TextView
    private lateinit var permSub: TextView
    private lateinit var roleSub: TextView
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Voice.init(this)
        val hot = navCard(R.drawable.ic_mic, "היי ${Assistant.NAME}") { startActivity(Intent(this, HotwordActivity::class.java)) }
        val speak = navCard(R.drawable.ic_speaker, "הקראת תשובות") { startActivity(Intent(this, SpeakActivity::class.java)) }
        val city = navCard(R.drawable.ic_sun, "זמני היום") { pickCity() }
        val perm = navCard(R.drawable.ic_lock, "הרשאות") { fixPermissions() }
        val role = navCard(R.drawable.ic_assist, "עוזר המערכת") {
            if (!Assistant.isDefault(this)) Assistant.makeDefault(this) { runOnUiThread { refresh() } }
        }
        hotSub = hot.second; speakSub = speak.second; citySub = city.second; permSub = perm.second; roleSub = role.second
        page(header("הגדרות ${Assistant.NAME}"), hot.first, speak.first, city.first, perm.first, role.first)
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
        val miss = Perms.missing(this)
        permSub.text = when (miss) { 0 -> "כל ההרשאות מאושרות"; 1 -> "חסרה הרשאה אחת"; else -> "חסרות $miss הרשאות" }
        roleSub.text = if (Assistant.isDefault(this)) "${Assistant.NAME} מוגדר כעוזר המערכת" else "${Assistant.NAME} לא מוגדר כעוזר המערכת · לחיצה להגדרה"
    }

    // בדיקת הרשאות: מפעיל לבד את מה שאפשר, מבקש את השאר, ומציג סיכום
    private fun fixPermissions() {
        Perms.fixSpecial(this)
        val ask = Perms.runtime().map { it.first }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (ask.isNotEmpty()) requestPermissions(ask.toTypedArray(), 7) else h.postDelayed({ summary() }, 700)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(code, p, g)
        h.postDelayed({ summary() }, 400)
    }

    private fun summary() {
        refresh()
        val lines = Perms.status(this).joinToString("\n") { (name, ok) -> (if (ok) "✓  " else "✗  ") + name }
        dlg().setTitle("הרשאות").setMessage(lines).setPositiveButton("סגור", null).show()
    }

    private fun pickCity() {
        val names = Zmanim.CITIES.map { it.name }.toTypedArray()
        dlg().setTitle("עיר לזמני היום").setSingleChoiceItems(names, Assistant.prefs(this).getInt("city", 0)) { d, i ->
            Assistant.prefs(this).edit().putInt("city", i).apply(); d.dismiss(); refresh()
        }.show()
    }
}
