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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity
import app.luma.torch.R
import app.luma.torch.dictation.ModelLocator
import kotlin.math.min
import kotlin.math.sin

// חלונית עזר — נפתחת כעוזר המערכת (החלקה / לחיצה כפולה / "היי עזר"): חלונית בתחתית המסך, ברקע שעובר משחור לכחול,
// עם כדור זוהר, שורה קטנה + טקסט גדול, ושדה זכוכית עם מיקרופון ושליחה. גרירה של החלק העליון למעלה פותחת את הצ'אט.
class AssistActivity : BaseActivity(), Listen.Callback {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var panel: LinearLayout
    private lateinit var orb: Orb
    private lateinit var small: TextView
    private lateinit var big: TextView
    private lateinit var input: EditText
    private lateinit var micBtn: FrameLayout
    private lateinit var micIcon: ImageView
    private lateinit var micWave: Bars
    private lateinit var sendBtn: ImageView
    private var listen: Listen? = null
    private var answered = false   // יש תשובה על המסך (נשארת עד שאומרים משהו חדש)
    private val close = Runnable { finish() }
    private var chat = ChatStore.create()   // כל פתיחה של החלונית / "היי עזר" = שיחה חדשה בהיסטוריה

    private fun round(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun circle(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // שורת המצב וסרגל הניווט שקופים: החלון מצייר את הרקע שלהם בעצמו (אחרת המערכת מציירת פס שחור)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        overridePendingTransition(android.R.anim.fade_in, 0)
        // גם כשהמסך כבוי או נעול ("היי עזר" מהכיס): מדליקים את המסך ומופיעים מעל מסך הנעילה
        setShowWhenLocked(true); setTurnScreenOn(true)
        Voice.init(this)

        orb = Orb(this)
        small = TextView(this).apply { textSize = 13f; setTextColor(0xFFAAB4C3.toInt()); gravity = Gravity.CENTER; maxLines = 2 }
        big = TextView(this).apply {
            textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f); maxLines = 6
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(18), dp(18), dp(18), dp(14))
            addView(orb, LinearLayout.LayoutParams(dp(96), dp(96)).apply { bottomMargin = dp(6) })
            addView(small, LinearLayout.LayoutParams(-1, -2))
            addView(big, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }

        input = EditText(this).apply {
            hint = "יש לך שאלה..."; setHintTextColor(0xAAFFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 15f
            background = null; setPadding(dp(16), 0, dp(8), 0)
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendTyped(); true }
            setOnFocusChangeListener { _, f -> if (f) { h.removeCallbacks(close); listen?.cancel(); Voice.stop(); paint() } }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { paint() }
            })
        }
        micIcon = ImageView(this).apply { setImageDrawable(icon(R.drawable.ic_mic, Color.WHITE)); setPadding(dp(10), dp(10), dp(10), dp(10)) }
        micWave = Bars(this)
        micBtn = FrameLayout(this).apply {
            addView(micIcon, FrameLayout.LayoutParams(-1, -1))
            addView(micWave, FrameLayout.LayoutParams(dp(22), dp(18), Gravity.CENTER))
            setOnClickListener { if (listen?.isListening == true) listen?.stop() else startListening() }
        }
        sendBtn = ImageView(this).apply { setPadding(dp(11), dp(11), dp(11), dp(11)); setOnClickListener { sendTyped() } }
        val glass = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(6), 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33FFFFFF, 0x14FFFFFF)).apply {
                cornerRadius = dp(26).toFloat(); setStroke(dp(1), 0x55FFFFFF)
            }
            addView(input, LinearLayout.LayoutParams(0, -1, 1f))
            addView(micBtn, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(6) })
            addView(sendBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true; elevation = dp(12).toFloat(); clipToOutline = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF020611.toInt(), 0xFF030B22.toInt(), 0xFF0A2A66.toInt(), 0xFF2A6FD6.toInt(), 0xFF6FAEF8.toInt())).apply {
                cornerRadius = dp(30).toFloat(); setStroke(dp(1), 0x22FFFFFF)
            }
            addView(top, LinearLayout.LayoutParams(-1, -2))
            addView(glass, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(12), 0, dp(12), dp(14)) })
        }
        // גרירה של החלק העליון כלפי מעלה → הצ'אט המלא (נגיעה רגילה לא)
        top.setOnTouchListener(object : View.OnTouchListener {
            var y0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { y0 = e.rawY; h.removeCallbacks(close) }
                    MotionEvent.ACTION_MOVE -> panel.translationY = min(0f, e.rawY - y0) * 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (e.rawY - y0 < -dp(50)) openChat() else panel.animate().translationY(0f).setDuration(180).start()
                    }
                }
                return true
            }
        })

        val rootView = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { finish() }
            addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(10)) })
            // מרווח מעל סרגל הניווט והמקלדת (החלון נמתח מתחתיהם)
            setOnApplyWindowInsetsListener { _, ins ->
                val bottom = if (android.os.Build.VERSION.SDK_INT >= 30)
                    ins.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
                else @Suppress("DEPRECATION") ins.systemWindowInsetBottom
                (panel.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(10) + bottom
                panel.requestLayout(); ins
            }
        }
        setContentView(rootView)
        greet(); paint()
        val q = intent.getStringExtra("q")
        if (!q.isNullOrBlank()) process(q, voice = true)   // "היי עזר, תדליק פנס" — ישר לביצוע
        else startListening()
    }

    // נסגרת בהיעלמות מהירה, בלי ירידה
    override fun finish() { super.finish(); overridePendingTransition(0, android.R.anim.fade_out) }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i)
        h.removeCallbacks(close)
        if (i.getBooleanExtra("hotword", false)) { chat = ChatStore.create(); answered = false; greet() }   // "היי עזר" חדש = שיחה חדשה
        val q = i.getStringExtra("q")
        if (!q.isNullOrBlank()) { listen?.cancel(); Voice.stop(); process(q, voice = true) } else startListening()
    }

    private fun openChat() {
        val i = Intent(this, ChatActivity::class.java)
        if (chat.msgs.isNotEmpty()) i.putExtra("id", chat.id)   // ממשיכים בצ'אט את מה שנאמר בחלונית
        startActivity(i); finish()
    }

    private fun greet() {
        orb.big = true; small.text = "שלום"; big.text = "איך אפשר לעזור?"; big.textSize = 22f
    }

    // כפתורים: מיקרופון (גל קול כשמקשיב) ושליחה (נדלקת כשיש טקסט)
    private fun paint() {
        val on = listen?.isListening == true
        micBtn.background = if (on) circle(0x40FFFFFF) else null
        micIcon.visibility = if (on) View.GONE else View.VISIBLE
        micWave.visibility = if (on) View.VISIBLE else View.GONE
        micWave.active = on; orb.listening = on
        val typed = input.text.isNotBlank()
        sendBtn.setImageDrawable(icon(R.drawable.ic_send, if (typed) 0xFF0A2A66.toInt() else 0x88FFFFFF.toInt()))
        sendBtn.background = circle(if (typed) Color.WHITE else 0x1FFFFFFF)
        sendBtn.isEnabled = typed
        input.hint = if (on) "מקשיב…" else "יש לך שאלה..."
    }

    private fun canListen() = ModelLocator.isInstalled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!canListen()) return
        h.removeCallbacks(close); Voice.stop()
        (listen ?: Listen(this, this).also { listen = it }).start()
    }

    override fun onListening() { paint(); if (!answered) { small.text = "${Assistant.NAME} מקשיב"; } }
    override fun onPartial(text: String) {
        answered = false; orb.big = true
        small.text = "${Assistant.NAME} מקשיב"; big.text = text; big.textSize = 22f
    }
    override fun onFinal(text: String) { paint(); process(text, voice = true) }
    override fun onNothing() {
        paint()
        // לא נאמר כלום: אם יש תשובה — משאירים אותה לקריאה עוד קצת, ואז החלונית נסגרת
        h.postDelayed(close, if (answered) 6000 else 1500)
    }

    private fun sendTyped() {
        val t = input.text.toString().trim(); if (t.isEmpty()) return
        input.setText(""); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        process(t, voice = false)
    }

    private fun process(text: String, voice: Boolean) {
        h.removeCallbacks(close)
        answered = false; orb.big = true
        small.text = "חושב…"; big.text = text; big.textSize = 22f
        ChatStore.add(this, chat, true, text)
        Thread {
            val r = try { Assistant.handle(this, text) } catch (_: Exception) { Assistant.Reply(Assistant.Say.ERROR) }
            runOnUiThread { show(text, r, voice) }
        }.start()
    }

    private fun show(said: String, r: Assistant.Reply, voice: Boolean) {
        ChatStore.add(this, chat, false, r.text.ifBlank { "בוצע" })
        r.then?.let { act ->
            // פעולה שצריכה את המסך (פתיחת אפליקציה, פעולה במסך…): סוגרים את החלונית ומבצעים
            finish(); h.removeCallbacksAndMessages(null)
            Handler(Looper.getMainLooper()).postDelayed({ try { act() } catch (_: Exception) {} }, 250)
            return
        }
        // תשובה: השאלה בקטן, התשובה בגדול; הכדור מתכווץ
        answered = true; orb.big = false
        small.text = said; big.text = r.text; big.textSize = 19f
        // אחרי התשובה (ואחרי ההקראה, אם יש) — מקשיבים שוב; התשובה נשארת עד שאומרים משהו חדש
        Voice.speak(this, r.text) {
            if (!isFinishing) { if (voice) startListening() else h.postDelayed(close, 10000) }
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null); orb.stop(); micWave.active = false
        listen?.cancel(); Voice.stop()
        super.onDestroy()
    }

    // הכדור: כדור כחול זוהר עם טבעת שמסתובבת לאט; בזמן הקשבה הוא פועם והזוהר מתחזק
    private inner class Orb(c: Context) : View(c) {
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        private var t = 0f
        private var scale = 1f
        var big = true
        var listening = false
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; scale += ((if (big) 1f else 0.68f) - scale) * 0.12f; invalidate() }
        }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null); anim.start() }
        fun stop() = anim.cancel()

        override fun onDraw(cv: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val pulse = if (listening) 1f + 0.06f * sin(t * 2 * Math.PI.toFloat() * 8) else 1f
            val r = min(width, height) / 2f * 0.62f * scale * pulse
            glow.color = 0xFF2F7BFF.toInt(); glow.alpha = if (listening) 150 else 95
            glow.maskFilter = BlurMaskFilter(r * (if (listening) 0.75f else 0.5f), BlurMaskFilter.Blur.NORMAL)
            cv.drawCircle(cx, cy, r, glow)
            body.shader = RadialGradient(cx + r * 0.2f, cy + r * 0.25f, r * 1.1f,
                intArrayOf(0xFF2A7BFF.toInt(), 0xFF0A1F5C.toInt(), 0xFF020611.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, body)
            shine.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 0.5f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, shine)
            cv.save(); cv.rotate(t * 360f, cx, cy)
            ring.strokeWidth = r * 0.07f
            ring.shader = SweepGradient(cx, cy, intArrayOf(0x007CC4FF, 0xFF7CC4FF.toInt(), 0xFFB39DFF.toInt(), 0x007CC4FF), null)
            cv.drawArc(RectF(cx - r * 1.04f, cy - r * 1.04f, cx + r * 1.04f, cy + r * 1.04f), 0f, 300f, false, ring)
            cv.restore()
        }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    }

    // גל קול קטן בלבן, בתוך כפתור המיקרופון בזמן הקשבה
    private inner class Bars(c: Context) : View(c) {
        private val n = 5
        private val cur = FloatArray(n) { 0.3f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val rnd = java.util.Random()
        private var frame = 0
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (++frame % 5 == 0) for (i in 0 until n) cur[i] = 0.3f + rnd.nextFloat() * 0.7f; invalidate() }
        }
        var active = false
            set(v) { field = v; if (v && !anim.isStarted) anim.start(); if (!v) anim.cancel() }

        override fun onDraw(cv: Canvas) {
            val bw = width / (n * 2f - 1)
            for (i in 0 until n) {
                val bh = maxOf(bw, cur[i] * height); val x = i * bw * 2; val y = (height - bh) / 2
                cv.drawRoundRect(x, y, x + bw, y + bh, bw / 2, bw / 2, p)
            }
        }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    }
}
