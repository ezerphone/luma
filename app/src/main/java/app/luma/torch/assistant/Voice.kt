package app.luma.torch.assistant

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale

// הקראת התשובות: מנוע ההקראה של גוגל ישירות (וגם מוגדר כברירת המחדל של המערכת אם לא הוגדר כלום), בקול עברי.
object Voice {
    private const val GOOGLE = "com.google.android.tts"
    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set
    @Volatile var hebrew = false; private set
    private val main = Handler(Looper.getMainLooper())
    private var onDone: (() -> Unit)? = null

    fun enabled(c: Context) = Assistant.prefs(c).getBoolean("speak", true)

    fun init(c: Context) {
        if (tts != null) return
        val app = c.applicationContext
        try {
            if (Settings.Secure.getString(app.contentResolver, "tts_default_synth").isNullOrBlank() && installed(app))
                Settings.Secure.putString(app.contentResolver, "tts_default_synth", GOOGLE)
        } catch (_: Exception) {}
        val listener = TextToSpeech.OnInitListener { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) pickHebrew()
        }
        tts = if (installed(app)) TextToSpeech(app, listener, GOOGLE) else TextToSpeech(app, listener)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { onDone?.invoke(); onDone = null } }
            @Deprecated("") override fun onError(id: String?) { main.post { onDone?.invoke(); onDone = null } }
        })
    }

    var status = ""; private set   // שורת אבחון למסך ההגדרות

    // מחפש קול עברי: קודם ברשימת הקולות (מותקן, לא דורש רשת), ואם אין — לפי שפה (he / iw)
    private fun pickHebrew() {
        val t = tts ?: return
        try {
            val v = t.voices?.filter { (it.locale.language == "he" || it.locale.language == "iw") }
                ?.sortedBy { (if (it.isNetworkConnectionRequired) 2 else 0) + (if ("notInstalled" in it.features) 4 else 0) }
                ?.firstOrNull { "notInstalled" !in it.features }
            if (v != null && t.setVoice(v) == TextToSpeech.SUCCESS) { hebrew = true; status = "קול: ${v.name}"; return }
        } catch (_: Exception) {}
        for (l in listOf(Locale("he", "IL"), Locale("iw", "IL"), Locale("he"), Locale("iw"))) {
            val r = try { t.isLanguageAvailable(l) } catch (_: Exception) { -2 }
            if (r >= TextToSpeech.LANG_AVAILABLE) { t.language = l; hebrew = true; status = "שפה: $l"; return }
        }
        hebrew = false
        status = "מנוע: ${t.defaultEngine ?: "?"} · קולות: ${try { t.voices?.size ?: 0 } catch (_: Exception) { 0 }}"
    }

    // בדיקה מחדש (למשל אחרי שהותקן קול) — משחרר ומאתחל את המנוע
    fun recheck(c: Context) { try { tts?.shutdown() } catch (_: Exception) {}; tts = null; ready = false; hebrew = false; init(c) }

    private fun installed(c: Context) = try { c.packageManager.getPackageInfo(GOOGLE, 0); true } catch (_: Exception) { false }

    // מקריא; done נקרא בסוף ההקראה — או מיד אם אין הקראה
    fun speak(c: Context, text: String, done: (() -> Unit)? = null) {
        if (!enabled(c) || !hebrew || text.isBlank()) { done?.let { main.post(it) }; return }
        onDone = done
        tts?.speak(text.replace("\n", ". "), TextToSpeech.QUEUE_FLUSH, null, "r")
    }

    fun stop() { onDone = null; tts?.stop() }
}
