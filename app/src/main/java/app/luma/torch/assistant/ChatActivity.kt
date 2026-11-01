package app.luma.torch.assistant

import android.Manifest
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity
import app.luma.torch.R
import app.luma.torch.dictation.ModelLocator
import java.text.SimpleDateFormat
import java.util.*

// עזר — מסך צ'אט (כניסה מתוך עזרים): היסטוריה, הקלדה, מיקרופון. אחרי תשובה לבקשה קולית — מקשיב שוב.
class ChatActivity : BaseActivity(), Listen.Callback {
    private lateinit var chat: ChatStore.Chat
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var mic: ImageView
    private var listen: Listen? = null
    private var voiceMode = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Voice.init(this)
        chat = intent.getStringExtra("id")?.let { ChatStore.load(this, it) }
            ?: if (intent.getBooleanExtra("new", false)) ChatStore.create()
            else ChatStore.current(this)

        fun iconBox(res: Int, click: () -> Unit) = ImageView(this).apply {
            setImageDrawable(icon(res, dim)); setPadding(dp(9), dp(9), dp(9), dp(9)); background = shape(card, 14, line); setOnClickListener { click() }
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(10))
            addView(iconBox(R.drawable.ic_menu) { startActivity(Intent(this@ChatActivity, ChatHistoryActivity::class.java)); finish() }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(this@ChatActivity).apply { text = Assistant.NAME; textSize = 24f; setTextColor(fg); typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconBox(R.drawable.ic_settings) { startActivity(Intent(this@ChatActivity, AssistantActivity::class.java)) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(12)) }
        scroll = ScrollView(this).apply { addView(list); isFillViewport = true }

        mic = ImageView(this).apply {
            setImageDrawable(icon(R.drawable.ic_mic, fg)); setPadding(dp(12), dp(12), dp(12), dp(12)); background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) }
            setOnClickListener { if (listen?.isListening == true) listen?.stop() else startListening() }
        }
        input = EditText(this).apply {
            hint = "כתוב ל${Assistant.NAME}…"; setHintTextColor(dim); setTextColor(fg); textSize = 16f; background = shape(chip, 24)
            setPadding(dp(16), dp(11), dp(16), dp(11)); maxLines = 4; imeOptions = EditorInfo.IME_ACTION_SEND; inputType = android.text.InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, _, _ -> sendTyped(); true }
            setOnFocusChangeListener { _, f -> if (f) { voiceMode = false; listen?.cancel(); Voice.stop(); idleMic() } }
        }
        val send = ImageView(this).apply {
            setImageDrawable(icon(R.drawable.ic_send, onAcc)); setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { shape = GradientDrawable.OVAL }
            setOnClickListener { sendTyped() }
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(14))
            addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
            addView(mic, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            addView(top); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); addView(bar)
        })
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        if (chat.msgs.isEmpty()) addBubble(false, "שלום! אני ${Assistant.NAME}. אפשר לכתוב או ללחוץ על המיקרופון ולבקש.", save = false)
        else { var day = ""; chat.msgs.forEach { m -> dayLabel(m.t).let { if (it != day) { day = it; addDay(it) } }; addBubble(m.me, m.text, save = false) } }
    }

    private fun dayLabel(t: Long): String {
        val d = Calendar.getInstance().apply { timeInMillis = t }; val now = Calendar.getInstance()
        return if (d.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) && d.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "היום"
        else SimpleDateFormat("d.M", Locale("he")).format(Date(t))
    }

    private fun addDay(s: String) = list.addView(TextView(this).apply { text = s; textSize = 11f; setTextColor(dim); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) },
        LinearLayout.LayoutParams(-1, -2))

    private fun addBubble(me: Boolean, text: String, save: Boolean = true): TextView {
        val tv = TextView(this).apply {
            this.text = text; textSize = 15f; setLineSpacing(0f, 1.25f); setPadding(dp(14), dp(10), dp(14), dp(10)); setTextIsSelectable(true)
            if (me) { setTextColor(onAcc); background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { cornerRadii = r(18, 18, 6, 18) } }
            else { setTextColor(fg); background = GradientDrawable().apply { setColor(card); setStroke(dp(1), line); cornerRadii = r(18, 18, 18, 6) } }
        }
        list.addView(tv, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (me) Gravity.END else Gravity.START; topMargin = dp(5); bottomMargin = dp(5)
            if (me) marginStart = dp(48) else marginEnd = dp(40)
        })
        if (save) ChatStore.add(this, chat, me, text)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    // פינות: עליון-ימין, עליון-שמאל, תחתון-שמאל, תחתון-ימין (RTL: הבועה שלי משמאל)
    private fun r(tr: Int, tl: Int, bl: Int, br: Int) = floatArrayOf(dp(tl).toFloat(), dp(tl).toFloat(), dp(tr).toFloat(), dp(tr).toFloat(),
        dp(br).toFloat(), dp(br).toFloat(), dp(bl).toFloat(), dp(bl).toFloat())

    private fun sendTyped() {
        val t = input.text.toString().trim(); if (t.isEmpty()) return
        input.setText(""); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        voiceMode = false; ask(t)
    }

    private fun ask(text: String) {
        addBubble(true, text)
        val pending = addBubble(false, "…", save = false)
        Thread {
            val r = try { Assistant.handle(this, text) } catch (_: Exception) { Assistant.Reply(Assistant.Say.ERROR) }
            runOnUiThread {
                val shown = r.text.ifBlank { "בוצע" }
                pending.text = shown
                ChatStore.add(this, chat, false, shown)
                r.then?.let { try { it() } catch (_: Exception) {} ; voiceMode = false; return@runOnUiThread }
                Voice.speak(this, r.text) { if (voiceMode && !isFinishing) startListening() }
            }
        }.start()
    }

    // ---------- מיקרופון ----------
    private fun canListen() = ModelLocator.isInstalled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!canListen()) { Toast.makeText(this, "צריך לאשר מיקרופון (בהגדרות עזר)", Toast.LENGTH_LONG).show(); return }
        Voice.stop(); voiceMode = true
        (listen ?: Listen(this, this).also { listen = it }).start()
        input.hint = "רגע…"
    }

    private fun idleMic() { mic.setColorFilter(null); mic.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) }; input.hint = "כתוב ל${Assistant.NAME}…" }

    override fun onListening() { input.hint = "מקשיב…"; mic.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { shape = GradientDrawable.OVAL } }
    override fun onPartial(text: String) { input.hint = text }
    override fun onFinal(text: String) { idleMic(); ask(text) }
    override fun onNothing() { idleMic(); voiceMode = false }

    override fun onPause() { listen?.cancel(); Voice.stop(); idleMic(); super.onPause() }

    // חזרה לצ'אט אחרי שיחה בחלונית / "היי עזר": אם נוספו הודעות — מציגים אותן
    override fun onRestart() {
        super.onRestart()
        val d = ChatStore.load(this, chat.id) ?: return
        if (d.msgs.size != chat.msgs.size) { intent.putExtra("id", chat.id); intent.removeExtra("new"); recreate() }
    }
}
