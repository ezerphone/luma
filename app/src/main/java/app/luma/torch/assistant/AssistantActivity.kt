package app.luma.torch.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import app.luma.torch.BaseActivity

// הגדרות עזר: "היי עזר", הקראה, עיר לזמני היום, עוזר המערכת, והרשאות (רק כשחסרות)
class AssistantActivity : BaseActivity() {
    private lateinit var voiceText: TextView
    private lateinit var voiceBtn: TextView
    private lateinit var testBtn: TextView
    private lateinit var roleText: TextView
    private lateinit var permSection: View
    private lateinit var cityBtn: TextView

    private val perms: Array<String> get() = listOfNotNull(
        Manifest.permission.RECORD_AUDIO,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
        if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
    ).toTypedArray()

    private fun sw(label: String, key: String, def: Boolean, after: () -> Unit = {}) = Switch(this).apply {
        text = label; setTextColor(fg); isChecked = Assistant.prefs(this@AssistantActivity).getBoolean(key, def); tint(this)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) }
        setOnCheckedChangeListener { _, c -> Assistant.prefs(this@AssistantActivity).edit().putBoolean(key, c).apply(); after() }
    }

    private fun info(t: String = "") = TextView(this).apply { text = t; textSize = 12f; setTextColor(dim); setLineSpacing(0f, 1.25f); setPadding(0, dp(4), 0, 0) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Voice.init(this)
        val hot = sw("הפעלת ״היי עזר״", "hotword", false) { Hotword.sync(this) }
        val hotCharge = sw("רק בזמן טעינה", "hotword_charging", false) { Hotword.sync(this) }
        val hotLocked = sw("להקשיב גם כשהמסך נעול", "hotword_locked", false) { Hotword.sync(this) }
        val hotOff = sw("להקשיב גם כשהמסך כבוי", "hotword_off", false) { Hotword.sync(this) }
        val hotNote = info("המיקרופון פתוח ברקע (נקודה ירוקה בפינה) וצורך סוללה. אחרי הפעלה מחדש של המכשיר — לפתוח את עזרים פעם אחת.")

        val speak = sw("הקראת תשובות", "speak", true)
        voiceText = info()
        voiceBtn = tv("התקנת קול עברי") {
            try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage("com.google.android.tts")) }
            catch (_: Exception) { try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (_: Exception) {} }
        }
        style(voiceBtn, false)
        testBtn = tv("השמעת דוגמה") { Voice.recheck(this); Handler(Looper.getMainLooper()).postDelayed({ refresh(); Voice.speak(this, "שלום, אני עזר") }, 1500) }
        style(testBtn, false)

        cityBtn = tv("") { pickCity() }
        style(cityBtn, false)
        roleText = info()

        val permBtn = tv("אישור הרשאות") { requestPermissions(perms, 7) }
        style(permBtn, true)
        permSection = section("הרשאות", info("כדי להקשיב, לנגן שירים ולהציג תזכורות צריך לאשר גישה."), permBtn)

        page(
            header("הגדרות ${Assistant.NAME}"),
            permSection,
            section("היי ${Assistant.NAME}", hot, hotCharge, hotLocked, hotOff, hotNote),
            section("קול", speak, voiceText, voiceBtn, testBtn),
            section("זמני היום", cityBtn),
            section("עוזר המערכת", roleText)
        )
    }

    override fun onResume() { super.onResume(); refresh(); Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 1200) }
    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(code, p, g); refresh() }

    private fun refresh() {
        voiceText.text = if (Voice.hebrew) "קול עברי זמין ✓" else "לא נמצא קול עברי להקראה"
        voiceText.setTextColor(if (Voice.hebrew) acc else dim)
        voiceBtn.visibility = View.GONE
        testBtn.visibility = if (Voice.hebrew) View.VISIBLE else View.GONE
        val def = Assistant.isDefault(this)
        roleText.text = if (def) "${Assistant.NAME} מוגדר כעוזר המערכת ✓" else "${Assistant.NAME} יוגדר כעוזר המערכת אוטומטית באתחול הבא"
        roleText.setTextColor(if (def) acc else dim)
        permSection.visibility = if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) View.GONE else View.VISIBLE
        cityBtn.text = Zmanim.city(this).name + " — שינוי"
    }

    private fun pickCity() {
        val names = Zmanim.CITIES.map { it.name }.toTypedArray()
        dlg().setTitle("עיר לזמני היום").setSingleChoiceItems(names, Assistant.prefs(this).getInt("city", 0)) { d, i ->
            Assistant.prefs(this).edit().putInt("city", i).apply(); d.dismiss(); refresh()
        }.show()
    }
}
