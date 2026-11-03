package app.luma.torch.assistant

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity
import app.luma.torch.R
import app.luma.torch.dictation.ModelLocator
import kotlin.math.abs
import kotlin.math.min

// חלונית עזר — נפתחת כעוזר המערכת (החלקה / לחיצה כפולה / "היי עזר"): כרטיס בתחתית המסך.
// פס בראש הכרטיס: גרירה למעלה (או לחיצה) פותחת את הצ'אט המלא · "עזר מקשיב…" + גל קול · מה שנקלט בגדול ·
// כפתורי קיצור · שדה כתיבה + מיקרופון/שליחה. מקשיב מיד, עונה בכרטיס, ומקשיב שוב אחרי כל תשובה.
class AssistActivity : BaseActivity(), Listen.Callback {
    private val blue = 0xFF1F6FEB.toInt()
    private val violet = 0xFF9B6BFF.toInt()
    private val cardC get() = if (isDark) 0xFF1F2227.toInt() else 0xFFF7F8FA.toInt()
    private val textC get() = if (isDark) 0xFFF1F3F4.toInt() else 0xFF111111.toInt()
    private val greyC get() = if (isDark) 0xFF9AA0A6.toInt() else 0xFF8A929B.toInt()
    private val chipBg get() = if (isDark) 0xFF26324A.toInt() else 0xFFE8EEFC.toInt()
    private val chipFg get() = if (isDark) 0xFFAECBFA.toInt() else 0xFF1F4FBF.toInt()
    private val fieldBg get() = if (isDark) 0xFF2A2E35.toInt() else Color.WHITE
    private val fieldLine get() = if (isDark) 0xFF3A3F47.toInt() else 0xFFDDE1E6.toInt()

    private val h = Handler(Looper.getMainLooper())
    private lateinit var sheet: LinearLayout
    private lateinit var statusIcon: ImageView
    private lateinit var status: TextView
    private lateinit var wave: Wave
    private lateinit var question: TextView
    private lateinit var big: TextView
    private lateinit var speaking: TextView
    private lateinit var chips: View
    private lateinit var input: EditText
    private lateinit var action: ImageView
    private var listen: Listen? = null
    private var asked = false   // נאמר/נשאל משהו בפתיחה הזו (אז כבר לא מציגים כפתורי קיצור)
    private val close = Runnable { finish() }
    private var chat = ChatStore.create()   // כל פתיחה של החלונית / "היי עזר" = שיחה חדשה בהיסטוריה

    private fun round(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        // גם כשהמסך כבוי או נעול ("היי עזר" מהכיס): מדליקים את המסך ומופיעים מעל מסך הנעילה
        setShowWhenLocked(true); setTurnScreenOn(true)
        Voice.init(this)

        // פס הגרירה בראש הכרטיס
        val handle = FrameLayout(this).apply {
            addView(View(this@AssistActivity).apply { background = round(greyC and 0x66FFFFFF, 3) },
                FrameLayout.LayoutParams(dp(40), dp(5), Gravity.CENTER))
        }
        statusIcon = ImageView(this).apply { setImageDrawable(icon(R.drawable.ic_mic, blue)) }
        status = TextView(this).apply { textSize = 15f; setTextColor(blue); typeface = Typeface.DEFAULT_BOLD }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(statusIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(6) })
            addView(status)
        }
        wave = Wave(this)
        question = TextView(this).apply { textSize = 13f; setTextColor(greyC); visibility = View.GONE }
        big = TextView(this).apply { textSize = 19f; setTextColor(textC); gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f) }
        speaking = TextView(this).apply { text = "● מקריא…"; textSize = 13f; setTextColor(blue); visibility = View.GONE; setPadding(0, dp(8), 0, 0) }

        val chipRow = LinearLayout(this)
        listOf("מה השעה", "זמני היום", "תזכורת", "שיר הבא", "פרשת השבוע").forEach { t ->
            chipRow.addView(TextView(this).apply {
                text = t; textSize = 13f; setTextColor(chipFg); background = round(chipBg, 14); setPadding(dp(11), dp(6), dp(11), dp(6))
                setOnClickListener { listen?.cancel(); process(t, voice = true) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        chips = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chipRow) }

        input = EditText(this).apply {
            hint = "אפשר גם לכתוב…"; setHintTextColor(greyC); setTextColor(textC); textSize = 15f
            background = round(fieldBg, 22, fieldLine); setPadding(dp(16), 0, dp(16), 0)
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendTyped(); true }
            setOnFocusChangeListener { _, f -> if (f) { h.removeCallbacks(close); listen?.cancel(); Voice.stop(); idle() } }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { paintAction() }
            })
        }
        action = ImageView(this).apply {
            setPadding(dp(11), dp(11), dp(11), dp(11))
            setOnClickListener {
                if (input.text.isNotBlank()) sendTyped()
                else if (listen?.isListening == true) listen?.stop() else startListening()
            }
        }
        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
            addView(action, LinearLayout.LayoutParams(dp(46), dp(46)))
        }

        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(cardC, 26); isClickable = true; elevation = dp(10).toFloat()
            setPadding(dp(16), 0, dp(16), dp(14))
            addView(handle, LinearLayout.LayoutParams(-1, dp(22)))
            addView(top, LinearLayout.LayoutParams(-1, -2))
            addView(wave, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
            addView(question, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            addView(big, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            addView(speaking, LinearLayout.LayoutParams(-1, -2))
            addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        // גרירה של הפס (וגם של שורת הסטטוס) כלפי מעלה → הצ'אט המלא
        val drag = object : View.OnTouchListener {
            var y0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { y0 = e.rawY; h.removeCallbacks(close) }
                    MotionEvent.ACTION_MOVE -> sheet.translationY = min(0f, e.rawY - y0) * 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dy = e.rawY - y0
                        if (dy < -dp(50) || (e.actionMasked == MotionEvent.ACTION_UP && abs(dy) < dp(6))) openChat()
                        else sheet.animate().translationY(0f).setDuration(180).start()
                    }
                }
                return true
            }
        }
        handle.setOnTouchListener(drag); top.setOnTouchListener(drag)

        setContentView(FrameLayout(this).apply {
            setOnClickListener { finish() }
            addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        })
        paintAction(); idle()
        val q = intent.getStringExtra("q")
        if (!q.isNullOrBlank()) process(q, voice = true)   // "היי עזר, תדליק פנס" — ישר לביצוע
        else startListening()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i)
        h.removeCallbacks(close)
        if (i.getBooleanExtra("hotword", false)) { chat = ChatStore.create(); asked = false }   // "היי עזר" חדש = שיחה חדשה
        val q = i.getStringExtra("q")
        if (!q.isNullOrBlank()) { listen?.cancel(); Voice.stop(); process(q, voice = true) } else startListening()
    }

    private fun openChat() {
        val i = Intent(this, ChatActivity::class.java)
        if (chat.msgs.isNotEmpty()) i.putExtra("id", chat.id)   // ממשיכים בצ'אט את מה שנאמר בחלונית
        startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); finish()
    }

    // הכפתור שליד השדה: שליחה כשיש טקסט, אחרת מיקרופון
    private fun paintAction() {
        val typed = this::input.isInitialized && input.text.isNotBlank()
        action.setImageDrawable(icon(if (typed) R.drawable.ic_send else R.drawable.ic_mic, if (typed) Color.WHITE else blue))
        action.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (typed) blue else chipBg) }
    }

    private fun canListen() = ModelLocator.isInstalled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!canListen()) { idle(); return }
        h.removeCallbacks(close); Voice.stop()
        (listen ?: Listen(this, this).also { listen = it }).start()
        status.text = "רגע…"
    }

    // מצב מנוחה: לא מקשיבים (מקלידים, או אין הרשאה)
    private fun idle() {
        wave.active = false; status.text = Assistant.NAME; status.setTextColor(greyC); statusIcon.setColorFilter(greyC)
        if (!asked) { question.visibility = View.GONE; big.text = "במה אפשר לעזור?"; big.setTextColor(greyC) }
        chips.visibility = if (asked) View.GONE else View.VISIBLE
    }

    override fun onListening() {
        wave.active = true; status.text = "${Assistant.NAME} מקשיב…"; status.setTextColor(blue); statusIcon.setColorFilter(blue)
        question.visibility = View.GONE; speaking.visibility = View.GONE
        big.text = "דבר עכשיו"; big.setTextColor(greyC); big.textSize = 19f
        chips.visibility = if (asked) View.GONE else View.VISIBLE
    }
    override fun onPartial(text: String) { big.text = text; big.setTextColor(textC); chips.visibility = View.GONE }
    override fun onFinal(text: String) { process(text, voice = true) }
    override fun onNothing() { idle(); if (asked) h.postDelayed(close, 1500) }

    private fun sendTyped() {
        val t = input.text.toString().trim(); if (t.isEmpty()) return
        input.setText(""); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        process(t, voice = false)
    }

    private fun process(text: String, voice: Boolean) {
        asked = true; chips.visibility = View.GONE
        wave.active = false; status.text = "חושב…"; status.setTextColor(blue); statusIcon.setColorFilter(blue)
        question.visibility = View.GONE; speaking.visibility = View.GONE
        big.text = text; big.setTextColor(textC)
        ChatStore.add(this, chat, true, text)
        Thread {
            val r = try { Assistant.handle(this, text) } catch (_: Exception) { Assistant.Reply(Assistant.Say.ERROR) }
            runOnUiThread { show(text, r, voice) }
        }.start()
    }

    private fun show(said: String, r: Assistant.Reply, voice: Boolean) {
        ChatStore.add(this, chat, false, r.text.ifBlank { "בוצע" })
        r.then?.let { act ->
            // פעולה שצריכה את המסך (פתיחת אפליקציה, מסך הבית…): סוגרים את החלונית ומבצעים
            finish(); h.removeCallbacksAndMessages(null)
            Handler(Looper.getMainLooper()).postDelayed({ try { act() } catch (_: Exception) {} }, 250)
            return
        }
        // תשובה: השאלה בקטן, התשובה בגדול, ו"מקריא…" בזמן ההקראה
        status.text = Assistant.NAME; status.setTextColor(greyC); statusIcon.setColorFilter(greyC)
        question.text = said; question.visibility = View.VISIBLE
        big.text = r.text; big.setTextColor(textC)
        val willSpeak = Voice.enabled(this) && Voice.hebrew && r.text.isNotBlank()
        speaking.visibility = if (willSpeak) View.VISIBLE else View.GONE
        // אחרי התשובה (ואחרי ההקראה, אם יש) — מקשיבים שוב; אם לא אומרים כלום, החלונית נסגרת
        Voice.speak(this, r.text) {
            speaking.visibility = View.GONE
            if (!isFinishing) { if (voice) startListening() else h.postDelayed(close, 8000) }
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null); wave.active = false
        listen?.cancel(); Voice.stop()
        super.onDestroy()
    }

    // גל קול: עמודות בגרדיאנט כחול-סגול שזזות בזמן ההקשבה, ושטוחות כשלא מקשיבים
    private inner class Wave(c: Context) : View(c) {
        private val n = 15
        private val cur = FloatArray(n) { 0.15f }
        private val target = FloatArray(n) { 0.15f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rnd = java.util.Random()
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { step() }
        }
        private var frame = 0
        var active = false
            set(v) { field = v; if (v && !anim.isStarted) anim.start(); if (!v) { target.fill(0.15f); if (!anim.isStarted) invalidate() } }

        private fun step() {
            if (++frame % 6 == 0 && active) for (i in 0 until n) {
                val mid = 1f - abs(i - n / 2f) / (n / 2f) * 0.55f
                target[i] = (0.25f + rnd.nextFloat() * 0.75f) * mid
            }
            var moving = false
            for (i in 0 until n) { cur[i] += (target[i] - cur[i]) * 0.22f; if (abs(target[i] - cur[i]) > 0.01f) moving = true }
            if (!active && !moving) anim.cancel()
            invalidate()
        }

        override fun onDraw(cv: Canvas) {
            val bw = dp(5).toFloat(); val gap = dp(5).toFloat()
            val total = n * bw + (n - 1) * gap
            var x = (width - total) / 2
            p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), blue, violet, Shader.TileMode.CLAMP)
            p.alpha = if (active) 255 else 110
            for (i in 0 until n) {
                val bh = maxOf(bw, cur[i] * height)
                val t = (height - bh) / 2
                cv.drawRoundRect(x, t, x + bw, t + bh, bw / 2, bw / 2, p)
                x += bw + gap
            }
        }

        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    }
}
