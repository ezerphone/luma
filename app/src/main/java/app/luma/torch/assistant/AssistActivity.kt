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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity
import app.luma.torch.R
import app.luma.torch.dictation.ModelLocator
import kotlin.math.abs
import kotlin.math.min

// חלונית עזר — נפתחת כעוזר המערכת (החלקה / לחיצה כפולה / "היי עזר"): כרטיס קטן בתחתית המסך.
// פס בראש הכרטיס: גרירה למעלה פותחת את הצ'אט המלא · "עזר מקשיב…" + גל קול קטן · מה שנקלט / התשובה ·
// שדה כתיבה + מיקרופון + שליחה. מקשיב מיד; התשובה נשארת על המסך גם כשמקשיבים שוב.
class AssistActivity : BaseActivity(), Listen.Callback {
    private val blue = 0xFF1F6FEB.toInt()
    private val violet = 0xFF9B6BFF.toInt()
    private val cardC get() = if (isDark) 0xFF1F2227.toInt() else 0xFFF7F8FA.toInt()
    private val textC get() = if (isDark) 0xFFF1F3F4.toInt() else 0xFF111111.toInt()
    private val greyC get() = if (isDark) 0xFF9AA0A6.toInt() else 0xFF8A929B.toInt()
    private val softBlue get() = if (isDark) 0xFF26324A.toInt() else 0xFFE8EEFC.toInt()
    private val offC get() = if (isDark) 0xFF3A3F47.toInt() else 0xFFE3E6EA.toInt()
    private val fieldBg get() = if (isDark) 0xFF2A2E35.toInt() else Color.WHITE
    private val fieldLine get() = if (isDark) 0xFF3A3F47.toInt() else 0xFFDDE1E6.toInt()

    private val h = Handler(Looper.getMainLooper())
    private lateinit var sheet: LinearLayout
    private lateinit var topRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var wave: Wave
    private lateinit var question: TextView
    private lateinit var big: TextView
    private lateinit var input: EditText
    private lateinit var micBtn: ImageView
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
        // החלון שקוף ונמתח מתחת לשורת המצב ולסרגל הניווט — בלי פסים שחורים
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (android.os.Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        // גם כשהמסך כבוי או נעול ("היי עזר" מהכיס): מדליקים את המסך ומופיעים מעל מסך הנעילה
        setShowWhenLocked(true); setTurnScreenOn(true)
        Voice.init(this)

        // פס הגרירה בראש הכרטיס
        val handle = FrameLayout(this).apply {
            addView(View(this@AssistActivity).apply { background = round(if (isDark) 0xFF4A4F57.toInt() else 0xFFC5CAD1.toInt(), 2) },
                FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER))
        }
        status = TextView(this).apply { textSize = 13f; setTextColor(blue); typeface = Typeface.DEFAULT_BOLD }
        wave = Wave(this)
        topRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@AssistActivity).apply { setImageDrawable(icon(R.drawable.ic_mic, blue)) }, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(5) })
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            addView(wave, LinearLayout.LayoutParams(dp(46), dp(20)))
        }
        question = TextView(this).apply { textSize = 12f; setTextColor(greyC); visibility = View.GONE; maxLines = 2 }
        big = TextView(this).apply { textSize = 16f; setTextColor(textC); setLineSpacing(0f, 1.2f); maxLines = 6 }

        input = EditText(this).apply {
            hint = "אפשר גם לכתוב…"; setHintTextColor(greyC); setTextColor(textC); textSize = 14f
            background = round(fieldBg, 20, fieldLine); setPadding(dp(14), 0, dp(14), 0)
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendTyped(); true }
            setOnFocusChangeListener { _, f -> if (f) { h.removeCallbacks(close); listen?.cancel(); Voice.stop(); paint() } }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { paint() }
            })
        }
        micBtn = ImageView(this).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { if (listen?.isListening == true) listen?.stop() else startListening() }
        }
        sendBtn = ImageView(this).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { sendTyped() }
        }
        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
            addView(micBtn, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(6) })
            addView(sendBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(cardC, 22); isClickable = true; elevation = dp(10).toFloat()
            setPadding(dp(12), 0, dp(12), dp(10))
            addView(handle, LinearLayout.LayoutParams(-1, dp(16)))
            addView(topRow, LinearLayout.LayoutParams(-1, -2))
            addView(question, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            addView(big, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) })
            addView(bottom, LinearLayout.LayoutParams(-1, -2))
        }
        // רק גרירה של הפס (או של שורת הסטטוס) כלפי מעלה פותחת את הצ'אט — נגיעה רגילה לא
        val drag = object : View.OnTouchListener {
            var y0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { y0 = e.rawY; h.removeCallbacks(close) }
                    MotionEvent.ACTION_MOVE -> sheet.translationY = min(0f, e.rawY - y0) * 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (e.rawY - y0 < -dp(50)) openChat()
                        else sheet.animate().translationY(0f).setDuration(180).start()
                    }
                }
                return true
            }
        }
        handle.setOnTouchListener(drag); topRow.setOnTouchListener(drag)

        val rootView = FrameLayout(this).apply {
            setOnClickListener { finish() }
            addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(10)) })
            // מרווח מעל סרגל הניווט (כי החלון נמתח מתחתיו)
            if (android.os.Build.VERSION.SDK_INT >= 30) setOnApplyWindowInsetsListener { _, ins ->
                val nav = ins.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()).bottom
                (sheet.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(10) + nav
                sheet.requestLayout(); ins
            }
        }
        setContentView(rootView)
        showListeningIdle()
        paint()
        val q = intent.getStringExtra("q")
        if (!q.isNullOrBlank()) process(q, voice = true)   // "היי עזר, תדליק פנס" — ישר לביצוע
        else startListening()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i)
        h.removeCallbacks(close)
        if (i.getBooleanExtra("hotword", false)) chat = ChatStore.create()   // "היי עזר" חדש = שיחה חדשה
        val q = i.getStringExtra("q")
        if (!q.isNullOrBlank()) { listen?.cancel(); Voice.stop(); process(q, voice = true) } else startListening()
    }

    private fun openChat() {
        val i = Intent(this, ChatActivity::class.java)
        if (chat.msgs.isNotEmpty()) i.putExtra("id", chat.id)   // ממשיכים בצ'אט את מה שנאמר בחלונית
        startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); finish()
    }

    // הכפתורים: מיקרופון (כחול מלא כשמקשיב) ושליחה (נדלקת כשיש טקסט)
    private fun paint() {
        val on = listen?.isListening == true
        micBtn.setImageDrawable(icon(R.drawable.ic_mic, if (on) Color.WHITE else blue))
        micBtn.background = circle(if (on) blue else softBlue)
        val typed = input.text.isNotBlank()
        sendBtn.setImageDrawable(icon(R.drawable.ic_send, if (typed) Color.WHITE else greyC))
        sendBtn.background = circle(if (typed) blue else offC)
        sendBtn.isEnabled = typed
    }

    private fun canListen() = ModelLocator.isInstalled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!canListen()) return
        h.removeCallbacks(close); Voice.stop()
        (listen ?: Listen(this, this).also { listen = it }).start()
    }

    // לפני שנאמר משהו: "דבר עכשיו"
    private fun showListeningIdle() {
        if (answered) return
        question.visibility = View.GONE
        big.text = "דבר עכשיו"; big.setTextColor(greyC)
    }

    override fun onListening() {
        topRow.visibility = View.VISIBLE; status.text = "${Assistant.NAME} מקשיב…"; wave.active = true
        showListeningIdle()   // אם יש תשובה על המסך — היא נשארת
        paint()
    }
    override fun onPartial(text: String) {
        answered = false
        question.visibility = View.GONE
        big.text = text; big.setTextColor(textC)
    }
    override fun onFinal(text: String) { wave.active = false; paint(); process(text, voice = true) }
    override fun onNothing() {
        wave.active = false; topRow.visibility = if (answered) View.GONE else View.VISIBLE
        status.text = Assistant.NAME; paint()
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
        answered = false
        topRow.visibility = View.VISIBLE; status.text = "חושב…"; wave.active = false
        question.visibility = View.GONE
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
        // תשובה: השאלה בקטן והתשובה מתחתיה — נשארת על המסך
        answered = true
        topRow.visibility = View.GONE
        question.text = said; question.visibility = View.VISIBLE
        big.text = r.text; big.setTextColor(textC)
        // אחרי התשובה (ואחרי ההקראה, אם יש) — מקשיבים שוב; התשובה נשארת עד שאומרים משהו חדש
        Voice.speak(this, r.text) {
            if (!isFinishing) { if (voice) startListening() else h.postDelayed(close, 10000) }
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null); wave.active = false
        listen?.cancel(); Voice.stop()
        super.onDestroy()
    }

    // גל קול קטן: עמודות בגרדיאנט כחול-סגול שזזות בזמן ההקשבה
    private inner class Wave(c: Context) : View(c) {
        private val n = 8
        private val cur = FloatArray(n) { 0.2f }
        private val target = FloatArray(n) { 0.2f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rnd = java.util.Random()
        private var frame = 0
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (++frame % 6 == 0 && active) for (i in 0 until n) target[i] = 0.3f + rnd.nextFloat() * 0.7f
                var moving = false
                for (i in 0 until n) { cur[i] += (target[i] - cur[i]) * 0.25f; if (abs(target[i] - cur[i]) > 0.01f) moving = true }
                if (!active && !moving) cancel()
                invalidate()
            }
        }
        var active = false
            set(v) { field = v; if (v && !anim.isStarted) anim.start(); if (!v) target.fill(0.2f) }

        override fun onDraw(cv: Canvas) {
            val bw = dp(3).toFloat(); val gap = (width - n * bw) / (n - 1)
            p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), blue, violet, Shader.TileMode.CLAMP)
            var x = 0f
            for (i in 0 until n) {
                val bh = maxOf(bw, cur[i] * height); val t = (height - bh) / 2
                cv.drawRoundRect(x, t, x + bw, t + bh, bw / 2, bw / 2, p)
                x += bw + gap
            }
        }
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    }
}
