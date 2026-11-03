package app.luma.torch.assistant

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper

// הקראת התשובות: מנוע ההקראה של גוגל ישירות (וגם מוגדר כברירת המחדל של המערכת אם לא הוגדר כלום), בקול עברי מקומי בלבד —
// קול שצריך אינטרנט לא נבחר (בלי אינטרנט הוא פשוט שותק).
object Voice {
    private const val GOOGLE = "com.google.android.tts"
    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set    // המנוע סיים להיטען (הצליח או לא)
    @Volatile var hebrew = false; private set
    private val main = Handler(Looper.getMainLooper())
    private var onDone: (() -> Unit)? = null
    private var pending: Pair<String, (() -> Unit)?>? = null   // הקראה שהתבקשה לפני שהמנוע סיים להיטען
    private val waiters = mutableListOf<() -> Unit>()

    fun enabled(c: Context) = Assistant.prefs(c).getBoolean("speak", true)

    fun init(c: Context) {
        if (tts != null) return
        val app = c.applicationContext
        try {
            if (Settings.Secure.getString(app.contentResolver, "tts_default_synth").isNullOrBlank() && installed(app))
                Settings.Secure.putString(app.contentResolver, "tts_default_synth", GOOGLE)
        } catch (_: Exception) {}
        val listener = TextToSpeech.OnInitListener { st ->
            main.post {
                hebrew = st == TextToSpeech.SUCCESS && pickHebrew()
                ready = true
                val p = pending; pending = null
                p?.let { (text, done) -> speak(app, text, done) }
                val w = waiters.toList(); waiters.clear(); w.forEach { it() }
            }
        }
        tts = if (installed(app)) TextToSpeech(app, listener, GOOGLE) else TextToSpeech(app, listener)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { onDone?.invoke(); onDone = null } }
            @Deprecated("") override fun onError(id: String?) { main.post { onDone?.invoke(); onDone = null } }
        })
    }

    // נקרא כשהמנוע מוכן (מיד, אם כבר מוכן) — להצגת המצב במסך ההגדרות
    fun whenReady(c: Context, cb: () -> Unit) {
        init(c)
        if (ready) main.post(cb) else waiters += cb
    }

    // קול עברי מותקן במכשיר, שלא צריך אינטרנט
    private fun pickHebrew(): Boolean {
        val t = tts ?: return false
        return try {
            val v = t.voices?.firstOrNull {
                (it.locale.language == "he" || it.locale.language == "iw") && !it.isNetworkConnectionRequired && "notInstalled" !in it.features
            } ?: return false
            t.setVoice(v) == TextToSpeech.SUCCESS
        } catch (_: Exception) { false }
    }

    // בדיקה מחדש (למשל אחרי שהותקן קול) — משחרר ומאתחל את המנוע; cb כשהבדיקה הסתיימה
    fun recheck(c: Context, cb: (() -> Unit)? = null) {
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null; ready = false; hebrew = false
        cb?.let { waiters += it }
        init(c)
    }

    private fun installed(c: Context) = try { c.packageManager.getPackageInfo(GOOGLE, 0); true } catch (_: Exception) { false }

    // מקריא; done נקרא בסוף ההקראה — או מיד אם אין הקראה. אם המנוע עוד נטען — ההקראה מחכה לו
    fun speak(c: Context, text: String, done: (() -> Unit)? = null) {
        if (!enabled(c) || text.isBlank()) { done?.let { main.post(it) }; return }
        if (!ready) { init(c); pending = text to done; return }
        if (!hebrew) { done?.let { main.post(it) }; return }
        onDone = done
        tts?.speak(text.replace("\n", ". "), TextToSpeech.QUEUE_FLUSH, null, "r")
    }

    fun stop() { onDone = null; pending = null; tts?.stop() }
}
