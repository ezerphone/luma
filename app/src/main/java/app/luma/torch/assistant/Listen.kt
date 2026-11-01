package app.luma.torch.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.luma.torch.dictation.SpeechEngine

// האזנה אחת לבקשה: מתחילה, מעבירה טקסט חלקי, ומסיימת לבד אחרי שקט (1.3 שנ') או כשלא אמרו כלום (7 שנ').
// בזמן ההאזנה ההאזנה ל"היי עזר" מושהית.
class Listen(c: Context, private val cb: Callback) : SpeechEngine.Observer {
    interface Callback {
        fun onListening()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onNothing()
    }

    private val engine = SpeechEngine.get(c)
    private val h = Handler(Looper.getMainLooper())
    private var want = false
    private var last = ""
    private var active = false
    private var holding = false
    private fun releaseOnce() { if (holding) { holding = false; Hotword.release() } }
    private val silence = Runnable { engine.stop() }
    private val nothing = Runnable { if (last.isBlank()) { engine.cancel(); finish(); cb.onNothing() } }

    val isListening get() = active

    fun start() {
        if (!holding) { holding = true; Hotword.hold() }
        engine.warm(); engine.observe(this)
        want = true; last = ""
        tryStart()
    }

    fun stop() { if (active) engine.stop() }

    fun cancel() { want = false; h.removeCallbacksAndMessages(null); if (active) engine.cancel(); finish() }

    private fun finish() { active = false; want = false; engine.remove(this); releaseOnce() }

    private fun tryStart() {
        if (!want || engine.state() != "ready" || engine.isListening) return
        want = false
        val ok = engine.start(object : SpeechEngine.Session {
            override fun onText(text: String) {
                if (text == last) return
                last = text; cb.onPartial(text)
                h.removeCallbacks(silence); if (text.isNotBlank()) h.postDelayed(silence, 1300)
            }
            override fun onFinished() {
                h.removeCallbacks(silence); h.removeCallbacks(nothing)
                active = false; engine.remove(this@Listen); releaseOnce()
                if (last.isNotBlank()) cb.onFinal(last) else cb.onNothing()
            }
            override fun onError(message: String) { active = false; engine.remove(this@Listen); releaseOnce(); cb.onNothing() }
        })
        if (ok) { active = true; cb.onListening(); h.postDelayed(nothing, 7000) }
    }

    override fun onState(state: String) { tryStart() }
}
