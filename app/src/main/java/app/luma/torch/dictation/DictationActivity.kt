package app.luma.torch.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import app.luma.torch.BaseActivity

// מסך ההגדרות של המקלדת הקולית.
class DictationActivity : BaseActivity(), SpeechEngine.Observer {
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private val vp get() = DictationPrefs.prefs(this)
    private val main = Handler(Looper.getMainLooper())
    private val refresh = Runnable { render() }

    private lateinit var detail: TextView
    private lateinit var errSection: View
    private lateinit var imeSw: Switch
    private var painting = false
    private lateinit var micInfo: TextView
    private lateinit var micBtn: TextView
    private lateinit var micSection: View
    private lateinit var dictBtn: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        VoiceIme.ensureDefault(this)
        // שגיאה מוצגת רק כשיש תקלה (מודל חסר או כשל בטעינה); במצב תקין אין שורת מצב
        detail = TextView(this).apply {
            textSize = 12f; setTextColor(warnC); setLineSpacing(0f, 1.2f)
            typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true)
        }

        // הפעלת המקלדת ברשימת המקלדות של המערכת
        imeSw = Switch(this).apply { text = "המקלדת הקולית מופעלת"; setTextColor(fg) }
        tint(imeSw)
        imeSw.setOnCheckedChangeListener { _, c ->
            if (painting) return@setOnCheckedChangeListener
            if (!VoiceIme.set(this, c)) main.postDelayed({ render() }, 1500) // דרך root: בודקים שוב אחרי רגע
        }

        // מיקרופון
        micInfo = info()
        micBtn = tv("אישור הרשאת מיקרופון", 15f) { askMic() }
        style(micBtn, true)
        micBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }

        // מילון אישי
        dictBtn = tv("מילון אישי", 15f) { startActivity(Intent(this, DictionaryActivity::class.java)) }
        style(dictBtn, false)

        // פיסוק
        val autoPunct = Switch(this).apply {
            text = "פיסוק אוטומטי של המודל"; setTextColor(fg)
            isChecked = vp.getBoolean("auto_punct", true)
            setOnCheckedChangeListener { _, c -> vp.edit().putBoolean("auto_punct", c).apply() }
        }
        tint(autoPunct)
        val commands = Switch(this).apply {
            text = "פקודות פיסוק קוליות"; setTextColor(fg)
            isChecked = vp.getBoolean("voice_commands", true)
            setOnCheckedChangeListener { _, c -> vp.edit().putBoolean("voice_commands", c).apply() }
        }
        tint(commands)
        commands.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        val commandsHint = info("הופך שמות תווים לתווים עצמם.")

        // מראה הסרגל
        val barGroup = group(listOf("אוטומטי", "כהה", "בהיר"), { vp.getInt("bar_theme", 0) }) { vp.edit().putInt("bar_theme", it).apply() }
        barGroup.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }

        // אודות: שורה קטנה אחת בתחתית (הקרדיט המלא והרישיונות במסך הנפרד)
        val about = TextView(this).apply {
            text = "מודל הדיבור: יעקב קולני · ivrit.ai (CC BY 4.0) · פרטיות ורישיונות ›"
            textSize = 11f; setTextColor(dim); gravity = android.view.Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(8))
            setOnClickListener { startActivity(Intent(this@DictationActivity, DictationLegalActivity::class.java)) }
        }

        micSection = section("מיקרופון", micInfo, micBtn)
        val punctSection = section("פיסוק", autoPunct, commands, commandsHint)
        val lookSection = section("מראה הסרגל", barGroup)
        val dictSection = section("מילון", dictBtn)

        errSection = section("תקלה", detail)
        page(
            header("הקלדה קולית"),
            errSection,
            section("מקלדת", imeSw),
            micSection, punctSection, lookSection, dictSection,
            about
        )
    }

    private fun info(t: String = "") = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f); setPadding(0, dp(2), 0, 0)
    }

    // ---------- מיקרופון ----------
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askMic() {
        if (vp.getBoolean("askedMic", false) && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            dlg().setTitle("הרשאת מיקרופון")
                .setMessage("כדי להכתיב, אפשר את המיקרופון בהגדרות האפליקציה וחזור לכאן.")
                .setPositiveButton("פתיחת הגדרות") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("ביטול", null).show()
            return
        }
        vp.edit().putBoolean("askedMic", true).apply()
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        render()
    }

    // ---------- תצוגה ----------
    private fun render() {
        if (!::detail.isInitialized) return
        val installed = ModelLocator.isInstalled()
        val state = SpeechEngine.get(this).state()
        val mic = hasMic()

        val err = SpeechEngine.get(this).lastError()
        detail.text = when {
            !installed -> "המודל חסר: ${ModelLocator.missing().joinToString(", ")}"
            state == "error" -> "שגיאה בטעינת המודל" + if (err.isNotEmpty()) "\n$err" else ""
            else -> ""
        }
        errSection.visibility = if (detail.text.isEmpty()) View.GONE else View.VISIBLE

        painting = true; imeSw.isChecked = VoiceIme.isEnabled(this); painting = false

        micInfo.text = "נדרשת כדי להקליט."
        micSection.visibility = if (mic) View.GONE else View.VISIBLE // כשההרשאה ניתנה אין מה להציג

        val count = RecognitionHints.snapshot(this).count
        dictBtn.text = "מילון אישי · " + when (count) { 0 -> "שמות ומונחים שיזוהו נכון"; 1 -> "מילה אחת"; else -> "$count מילים וביטויים" }
    }

    override fun onResume() {
        super.onResume()
        SpeechEngine.get(this).observe(this)
        render()
    }

    override fun onPause() {
        SpeechEngine.get(this).remove(this)
        main.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onState(state: String) { render() }
}
