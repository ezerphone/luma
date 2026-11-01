package app.luma.torch.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity
import app.luma.torch.R
import app.luma.torch.dictation.ModelLocator

// חלונית עזר — נפתחת כעוזר המערכת (החלקה / לחיצה כפולה / "היי עזר"): קפסולה כהה בתחתית המסך.
// ➕ פותח את הצ'אט המלא · שדה "יש לך שאלה" · 🎤 · שליחה. מקשיב מיד, עונה בבועה, ומקשיב שוב אחרי כל תשובה.
class AssistActivity : BaseActivity(), Listen.Callback {
    private val pill = 0xFF1E1F20.toInt()
    private val white = 0xFFE3E3E3.toInt()
    private val grey = 0xFF9AA0A6.toInt()
    private val blue = 0xFF8AB4F8.toInt()
    private val HINT = "יש לך שאלה ..."

    private val h = Handler(Looper.getMainLooper())
    private lateinit var bubble: LinearLayout
    private lateinit var heard: TextView
    private lateinit var reply: TextView
    private lateinit var input: EditText
    private lateinit var mic: ImageView
    private var pulse: ObjectAnimator? = null
    private var listen: Listen? = null
    private val close = Runnable { finish() }
    private val chat by lazy { ChatStore.current(this) }   // גם שיחות מהחלונית נשמרות בהיסטוריה

    private fun round(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun iconBtn(res: Int, click: () -> Unit) = ImageView(this).apply {
        setImageDrawable(icon(res, white)); setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { click() }
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, round(Color.WHITE, 24))
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        // גם כשהמסך כבוי או נעול ("היי עזר" מהכיס): מדליקים את המסך ומופיעים מעל מסך הנעילה
        setShowWhenLocked(true); setTurnScreenOn(true)
        Voice.init(this)

        heard = TextView(this).apply { textSize = 15f; setTextColor(grey) }
        reply = TextView(this).apply { textSize = 19f; setTextColor(white); setPadding(0, dp(6), 0, 0); setLineSpacing(0f, 1.2f) }
        bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(pill, 24); setPadding(dp(20), dp(16), dp(20), dp(16)); visibility = View.GONE; isClickable = true
            addView(heard); addView(reply)
        }

        val plus = iconBtn(R.drawable.ic_plus) { startActivity(Intent(this, ChatActivity::class.java)); finish() }
        input = EditText(this).apply {
            hint = HINT; setHintTextColor(grey); setTextColor(white); textSize = 20f; background = null
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND; setPadding(dp(4), 0, dp(4), 0)
            setOnEditorActionListener { _, _, _ -> sendTyped(); true }
            setOnFocusChangeListener { _, f -> if (f) { h.removeCallbacks(close); listen?.cancel(); Voice.stop(); idle() } }
        }
        mic = iconBtn(R.drawable.ic_mic) { if (listen?.isListening == true) listen?.stop() else startListening() }
        val send = iconBtn(R.drawable.ic_send) { sendTyped() }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0)
            addView(plus, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(mic, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        val capsule = FrameLayout(this).apply {
            background = round(pill, 40); isClickable = true; elevation = dp(8).toFloat()
            addView(row, FrameLayout.LayoutParams(-1, dp(68)))
        }
        setContentView(FrameLayout(this).apply {
            setOnClickListener { finish() }
            addView(LinearLayout(this@AssistActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(bubble, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                addView(capsule, LinearLayout.LayoutParams(-1, -2))
            }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(16)) })
        })
        val q = intent.getStringExtra("q")
        if (!q.isNullOrBlank()) process(q, voice = true)   // "היי עזר, תדליק פנס" — ישר לביצוע
        else startListening()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i)
        h.removeCallbacks(close)
        val q = i.getStringExtra("q")
        if (!q.isNullOrBlank()) { listen?.cancel(); Voice.stop(); process(q, voice = true) } else startListening()
    }

    private fun canListen() = ModelLocator.isInstalled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!canListen()) return
        h.removeCallbacks(close); Voice.stop()
        (listen ?: Listen(this, this).also { listen = it }).start()
        input.hint = "רגע…"
    }

    private fun idle() { pulse?.cancel(); mic.clearColorFilter(); mic.alpha = 1f; input.hint = HINT }

    override fun onListening() {
        input.hint = "מקשיב…"; mic.setColorFilter(blue)
        pulse = ObjectAnimator.ofFloat(mic, "alpha", 1f, 0.4f).apply { duration = 600; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start() }
    }
    override fun onPartial(text: String) { input.hint = text }
    override fun onFinal(text: String) { idle(); process(text, voice = true) }
    override fun onNothing() { idle(); if (bubble.visibility == View.VISIBLE) h.postDelayed(close, 1500) }

    private fun sendTyped() {
        val t = input.text.toString().trim(); if (t.isEmpty()) return
        input.setText(""); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        process(t, voice = false)
    }

    private fun process(text: String, voice: Boolean) {
        bubble.visibility = View.VISIBLE; heard.text = text; reply.text = "…"
        ChatStore.add(this, chat, true, text)
        Thread {
            val r = try { Assistant.handle(this, text) } catch (_: Exception) { Assistant.Reply(Assistant.Say.ERROR) }
            runOnUiThread { show(r, voice) }
        }.start()
    }

    private fun show(r: Assistant.Reply, voice: Boolean) {
        ChatStore.add(this, chat, false, r.text.ifBlank { "בוצע" })
        r.then?.let { act ->
            // פעולה שצריכה את המסך (פתיחת אפליקציה, מסך הבית…): סוגרים את החלונית ומבצעים
            finish(); h.removeCallbacksAndMessages(null)
            Handler(Looper.getMainLooper()).postDelayed({ try { act() } catch (_: Exception) {} }, 250)
            return
        }
        reply.text = r.text
        // אחרי התשובה (ואחרי ההקראה, אם יש) — מקשיבים שוב; אם לא אומרים כלום, החלונית נסגרת
        Voice.speak(this, r.text) { if (!isFinishing) { if (voice) startListening() else h.postDelayed(close, 8000) } }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null); pulse?.cancel()
        listen?.cancel(); Voice.stop()
        super.onDestroy()
    }
}
