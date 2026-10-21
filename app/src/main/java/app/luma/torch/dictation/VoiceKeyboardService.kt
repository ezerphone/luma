package app.luma.torch.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

// מקלדת קולית: סרגל קטן בסגנון ההקלדה הקולית של גוגל. מיקרופון, מחיקה, סטטוס, חזרה למקלדת הרגילה ואות השפה.
// הטקסט נכנס לשדה דרך החיבור הרגיל של המקלדת (InputConnection), בלי שירות נגישות ובלי שירות חזית.
class VoiceKeyboardService : InputMethodService(), SpeechEngine.Observer {
    private enum class Kind { MIC, BACKSPACE, KEYBOARD, SETTINGS }

    private lateinit var engine: SpeechEngine
    private val main = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var pill: LinearLayout? = null
    private var statusView: TextView? = null
    private var micIcon: Icon? = null
    private var delIcon: Icon? = null
    private var kbIcon: Icon? = null
    private var cogIcon: Icon? = null
    private var password = false
    private var note: String? = null
    private var prefix = ""          // רווח לפני המילים החדשות, אם התו שלפני הסמן אינו רווח
    private var freshSegment = false // אחרי מחיקה בזמן הקלטה: מחשבים את הרווח מחדש בטקסט הבא
    private var autoStart = false    // פתיחת המקלדת מתחילה להקשיב מיד; אם המודל עוד נטען, ממתינים לו

    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (!canDelete()) return
            deleteOnce()
            main.postDelayed(this, 60)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun systemNight() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    // מראה הסרגל לפי ההגדרה: 0 אוטומטי (לפי המערכת), 1 כהה, 2 בהיר
    private fun dark() = when (DictationPrefs.prefs(this).getInt("bar_theme", 0)) { 1 -> true; 2 -> false; else -> systemNight() }
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    // מחיקה עובדת גם תוך כדי הקלטה. רק בזמן סיום ההכתבה (המתנה לטקסט האחרון) היא חסומה
    private fun canDelete() = !password && engine.state() != "finishing"

    private fun deleteOnce() {
        if (engine.isListening) {
            // מקבעים את מה שהוכתב עד עכשיו, כדי שהמחיקה תפעל על טקסט אמיתי ולא על טקסט בהרכבה,
            // ומאפסים את התמלול כדי שההשערה הבאה לא תחזיר את מה שנמחק
            currentInputConnection?.finishComposingText()
            engine.resetTranscript()
            freshSegment = true
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onCreate() {
        super.onCreate()
        engine = SpeechEngine.get(this)
        // המודל נטען פעם אחת כשהמקלדת עולה, ונשאר טעון כל עוד התהליך חי (נטען שוב רק אחרי הפעלה מחדש)
        if (ModelLocator.isInstalled() && hasMic()) engine.warm()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val side = dp(12f).toInt()
        val r = LinearLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(side, dp(8f).toInt(), side, dp(10f).toInt())
        }
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
        }
        val box = dp(48f).toInt()

        val mic = Icon(Kind.MIC).apply { setOnClickListener { onMic() } }
        val del = Icon(Kind.BACKSPACE).apply {
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        if (canDelete()) { deleteOnce(); main.postDelayed(deleteRepeat, 400) }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false; main.removeCallbacks(deleteRepeat)
                    }
                }
                true
            }
        }
        val st = TextView(this).apply {
            textSize = 18f; gravity = Gravity.CENTER; maxLines = 1; textDirection = View.TEXT_DIRECTION_RTL
        }
        val kb = Icon(Kind.KEYBOARD).apply { setOnClickListener { backToKeyboard() } }
        val cog = Icon(Kind.SETTINGS).apply { setOnClickListener { openSetup() } } // הגדרות ההקלדה הקולית בעזרים

        p.addView(mic, LinearLayout.LayoutParams(box, box))
        p.addView(kb, LinearLayout.LayoutParams(box, box))
        p.addView(st, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        p.addView(del, LinearLayout.LayoutParams(box, box))
        p.addView(cog, LinearLayout.LayoutParams(box, box))
        r.addView(p, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f).toInt()))

        root = r; pill = p; statusView = st; micIcon = mic; delIcon = del; kbIcon = kb; cogIcon = cog
        applyTheme(); render()
        return r
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(); render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        password = isPassword(info)
        note = null
        applyTheme() // ייתכן שהמראה שונה בהגדרות
        // אם כבר טעון זו קריאה ריקה. היא נחוצה רק אחרי כשל טעינה או אחרי שניתנה הרשאת מיקרופון
        if (!password && ModelLocator.isInstalled() && hasMic()) engine.warm()
        autoStart = !restarting && !password && ModelLocator.isInstalled() && hasMic()
        engine.observe(this)
        tryAutoStart()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        autoStart = false
        main.removeCallbacks(deleteRepeat)
        engine.remove(this)
        if (engine.isListening) engine.cancel()
        currentInputConnection?.finishComposingText()
        // בכוונה לא משחררים את המודל מהזיכרון כאן
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        engine.remove(this)
        super.onDestroy()
    }

    override fun onState(state: String) { tryAutoStart(); render() }

    // מתחילים להקשיב ברגע שהמקלדת נפתחת והמודל מוכן (פעם אחת לכל פתיחה)
    private fun tryAutoStart() {
        if (!autoStart || engine.state() != "ready" || engine.isListening) return
        autoStart = false
        startListening()
    }

    // ---------- פעולות ----------
    private fun onMic() {
        if (password) return
        if (!ModelLocator.isInstalled()) { note = "המודל חסר"; render(); return }
        if (!hasMic()) { openSetup(); return }
        note = null
        when {
            engine.isListening -> engine.stop()
            engine.state() == "ready" -> startListening()
            else -> { engine.warm(); render() } // טוען או שגיאה: ניסיון חוזר
        }
    }

    private fun startListening() {
        val ic = currentInputConnection ?: return
        // אם התו שלפני הסמן אינו רווח, מוסיפים רווח לפני המילים החדשות
        prefix = spaceBeforeCursor(ic)
        freshSegment = false
        val ok = engine.start(object : SpeechEngine.Session {
            override fun onText(text: String) {
                val p = DictationPrefs.prefs(this@VoiceKeyboardService)
                val body = Punctuation.process(text, p.getBoolean("auto_punct", true), p.getBoolean("voice_commands", true))
                if (freshSegment && body.isNotEmpty()) {
                    currentInputConnection?.let { prefix = spaceBeforeCursor(it) }
                    freshSegment = false
                }
                val pre = if (prefix.isNotEmpty() && body.isNotEmpty() && !Punctuation.attachesLeft(body)) prefix else ""
                currentInputConnection?.setComposingText(if (body.isEmpty()) "" else pre + body, 1)
            }
            override fun onFinished() { currentInputConnection?.finishComposingText(); render() }
            override fun onError(message: String) {
                currentInputConnection?.finishComposingText(); note = "שגיאת מיקרופון"; render()
            }
        })
        if (!ok) { note = "המודל עדיין נטען"; render() }
    }

    // רווח לפני המילים החדשות, אם התו שלפני הסמן אינו רווח
    private fun spaceBeforeCursor(ic: android.view.inputmethod.InputConnection): String {
        val before = ic.getTextBeforeCursor(1, 0)
        return if (!before.isNullOrEmpty() && !before[0].isWhitespace()) " " else ""
    }

    private fun backToKeyboard() {
        if (!switchToPreviousInputMethod())
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun openSetup() {
        startActivity(Intent(this, DictationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isPassword(info: EditorInfo?): Boolean {
        val t = info?.inputType ?: return false
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    // ---------- תצוגה ----------
    private fun applyTheme() {
        val n = dark()
        root?.setBackgroundColor(if (n) 0xFF202124.toInt() else 0xFFF1F3F4.toInt())
        pill?.background = GradientDrawable().apply {
            cornerRadius = dp(28f); setColor(if (n) 0xFF3C4043.toInt() else 0xFFFFFFFF.toInt())
        }
        statusView?.setTextColor(if (n) 0xFFE8EAED.toInt() else 0xFF202124.toInt())
        for (i in listOf(micIcon, delIcon, kbIcon, cogIcon)) i?.invalidate()
    }

    private fun render() {
        val s = engine.state()
        val text = when {
            note != null -> note!!
            password -> "לא זמין בשדה סיסמה"
            !ModelLocator.isInstalled() -> "המודל חסר"
            !hasMic() -> "אין הרשאת מיקרופון"
            s == "listening" -> "מקשיב"
            s == "finishing" -> "מסיים"
            s == "error" -> "שגיאה בטעינת המודל"
            s == "loading" -> "מתבצע אתחול..."
            else -> "בהשהיה"
        }
        statusView?.text = text
        micIcon?.apply { alpha = if (password) 0.38f else 1f; contentDescription = if (engine.isListening) "סיום הכתבה" else "התחלת הכתבה"; invalidate() }
        delIcon?.apply { alpha = if (canDelete()) 1f else 0.38f; contentDescription = "מחיקה"; invalidate() }
        kbIcon?.apply { contentDescription = "חזרה למקלדת"; invalidate() }
        cogIcon?.apply { contentDescription = "הגדרות"; invalidate() }
    }

    // אייקון מצויר ידנית על רשת של 24 יחידות (מיקרופון, מחיקה, מקלדת), בתוך אזור לחיצה של 48dp
    private inner class Icon(private val kind: Kind) : View(this@VoiceKeyboardService) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            val s = dp(1f)
            val ox = (width - 24 * s) / 2f
            val oy = (height - 24 * s) / 2f
            fun x(v: Float) = ox + v * s
            fun y(v: Float) = oy + v * s
            val n = dark()
            var color = if (n) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            if (kind == Kind.MIC && engine.isListening) {
                fill.color = if (n) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
                canvas.drawCircle(width / 2f, height / 2f, 20 * s, fill)
                color = if (n) 0xFF202124.toInt() else 0xFFFFFFFF.toInt()
            }
            stroke.color = color; stroke.strokeWidth = 1.8f * s; fill.color = color
            when (kind) {
                Kind.MIC -> {
                    canvas.drawRoundRect(x(9f), y(3f), x(15f), y(14f), 3 * s, 3 * s, fill)
                    canvas.drawArc(RectF(x(6f), y(7f), x(18f), y(17f)), 0f, 180f, false, stroke)
                    canvas.drawLine(x(12f), y(17f), x(12f), y(21f), stroke)
                    canvas.drawLine(x(9f), y(21f), x(15f), y(21f), stroke)
                }
                Kind.BACKSPACE -> {
                    val path = Path().apply {
                        moveTo(x(9f), y(5f)); lineTo(x(21f), y(5f)); lineTo(x(21f), y(19f))
                        lineTo(x(9f), y(19f)); lineTo(x(3f), y(12f)); close()
                    }
                    canvas.drawPath(path, stroke)
                    canvas.drawLine(x(11.5f), y(9.5f), x(16.5f), y(14.5f), stroke)
                    canvas.drawLine(x(16.5f), y(9.5f), x(11.5f), y(14.5f), stroke)
                }
                Kind.KEYBOARD -> {
                    canvas.drawRoundRect(x(2.5f), y(6f), x(21.5f), y(18f), 2 * s, 2 * s, stroke)
                    for (cx in floatArrayOf(6f, 9.5f, 13f, 16.5f)) {
                        canvas.drawCircle(x(cx), y(9.5f), 0.9f * s, fill)
                        canvas.drawCircle(x(cx), y(12.5f), 0.9f * s, fill)
                    }
                    canvas.drawLine(x(8f), y(15.3f), x(16f), y(15.3f), stroke)
                }
                Kind.SETTINGS -> {
                    // גלגל שיניים: 8 שיניים סביב חור מרכזי
                    val cx = x(12f); val cy = y(12f)
                    fun pt(r: Float, deg: Double) = Pair(
                        cx + (r * Math.cos(Math.toRadians(deg))).toFloat(), cy + (r * Math.sin(Math.toRadians(deg))).toFloat())
                    val path = Path()
                    for (i in 0 until 8) {
                        val base = i * 45.0
                        val a = pt(7.2f * s, base - 14.0); val b = pt(9.6f * s, base - 9.0)
                        val c = pt(9.6f * s, base + 9.0); val d = pt(7.2f * s, base + 14.0)
                        if (i == 0) path.moveTo(a.first, a.second) else path.lineTo(a.first, a.second)
                        path.lineTo(b.first, b.second); path.lineTo(c.first, c.second); path.lineTo(d.first, d.second)
                    }
                    path.close()
                    canvas.drawPath(path, stroke)
                    canvas.drawCircle(cx, cy, 3f * s, stroke)
                }
            }
        }
    }
}
