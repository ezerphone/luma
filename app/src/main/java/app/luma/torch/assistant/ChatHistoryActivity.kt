package app.luma.torch.assistant

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import app.luma.torch.BaseActivity
import java.text.SimpleDateFormat
import java.util.*

// היסטוריית השיחות עם עזר. לחיצה ארוכה על שיחה: שינוי שם, ייצוא, סימון כמה שיחות, מחיקה
class ChatHistoryActivity : BaseActivity() {
    private val picked = LinkedHashSet<String>()
    private var selecting = false
    private val red = 0xFFC62828.toInt()

    // מסך הכניסה לעזר: השיחות האחרונות (מתעדכן בכל חזרה למסך)
    override fun onResume() { super.onResume(); build() }

    @Deprecated("") override fun onBackPressed() { if (selecting) { selecting = false; picked.clear(); build() } else super.onBackPressed() }

    private fun build() {
        val chats = ChatStore.list(this)
        picked.retainAll(chats.map { it.id }.toSet())
        val fmt = SimpleDateFormat("EEEE · HH:mm", Locale("he"))
        val items = chats.map { ch ->
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ChatHistoryActivity).apply { text = ch.title; textSize = 15f; setTextColor(fg); typeface = medium; maxLines = 1 })
                addView(TextView(this@ChatHistoryActivity).apply { text = fmt.format(Date(ch.updated)); textSize = 12f; setTextColor(dim) })
            }
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; background = shape(card, 18, line); setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                if (selecting) addView(CheckBox(this@ChatHistoryActivity).apply {
                    isChecked = ch.id in picked; isClickable = false; buttonTintList = android.content.res.ColorStateList.valueOf(acc)
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                setOnClickListener {
                    if (selecting) { if (!picked.remove(ch.id)) picked += ch.id; if (picked.isEmpty()) selecting = false; build() }
                    else startActivity(Intent(this@ChatHistoryActivity, ChatActivity::class.java).putExtra("id", ch.id))
                }
                setOnLongClickListener { if (!selecting) actions(ch); true }
            }
        }
        val note = TextView(this).apply {
            text = if (items.isEmpty()) "אין עדיין שיחות" else "לחיצה ארוכה על שיחה — אפשרויות"
            textSize = 12f; setTextColor(dim); gravity = Gravity.CENTER
        }
        if (selecting) {
            // מצב סימון: ✕ · "N נבחרו" · ייצוא · מחיקה
            val bar = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
                addView(TextView(this@ChatHistoryActivity).apply {
                    text = "✕"; textSize = 20f; setTextColor(dim); setPadding(dp(4), dp(4), dp(12), dp(4))
                    setOnClickListener { selecting = false; picked.clear(); build() }
                })
                addView(TextView(this@ChatHistoryActivity).apply { text = "${picked.size} נבחרו"; textSize = 22f; setTextColor(fg); typeface = android.graphics.Typeface.DEFAULT_BOLD },
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv("ייצוא", 14f) { export(chats.filter { it.id in picked }) }.apply { style(this, false); setPadding(dp(14), dp(8), dp(14), dp(8)) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
                addView(tv("מחיקה", 14f) { confirmDelete(picked.toList()) }.apply { style(this, false); setTextColor(red); setPadding(dp(14), dp(8), dp(14), dp(8)) })
            }
            page(bar, *items.toTypedArray())
        } else {
            val newChat = tv("+ שיחה חדשה") { startActivity(Intent(this, ChatActivity::class.java).putExtra("new", true)) }
            style(newChat, true)
            newChat.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            val gear = ImageView(this).apply {
                setImageDrawable(icon(app.luma.torch.R.drawable.ic_settings, dim)); setPadding(dp(9), dp(9), dp(9), dp(9)); background = shape(card, 14, line)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { startActivity(Intent(this@ChatHistoryActivity, AssistantActivity::class.java)) }
            }
            page(header(Assistant.NAME, gear, "שיחות אחרונות"), newChat, *items.toTypedArray(), note)
        }
    }

    // חלונית מלמטה עם האפשרויות לשיחה
    private fun actions(ch: ChatStore.Chat) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        fun row(t: String, color: Int = fg, go: () -> Unit) = TextView(this).apply {
            text = t; textSize = 16f; setTextColor(color); setPadding(dp(8), dp(14), dp(8), dp(14))
            setOnClickListener { d.dismiss(); go() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(22))
            background = GradientDrawable().apply { setColor(card); cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f) }
            addView(View(this@ChatHistoryActivity).apply { background = shape(line, 2) }, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })
            addView(TextView(this@ChatHistoryActivity).apply { text = ch.title; textSize = 13f; setTextColor(dim); maxLines = 1; setPadding(dp(8), 0, dp(8), dp(4)) })
            addView(row("שינוי שם") { rename(ch) })
            addView(row("ייצוא שיחה") { export(listOf(ch)) })
            addView(row("סימון כמה שיחות") { selecting = true; picked.clear(); picked += ch.id; build() })
            addView(row("מחיקה", red) { confirmDelete(listOf(ch.id)) })
        }
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM); decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL; setDimAmount(0.5f)
        }
        d.show()
    }

    private fun rename(ch: ChatStore.Chat) {
        val et = EditText(this).apply { setText(ch.title); setSelection(text.length); setTextColor(fg) }
        val wrap = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) }
        dlg().setTitle("שינוי שם").setView(wrap)
            .setPositiveButton("שמירה") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let { ChatStore.rename(this, ch.id, it.take(60)); build() } }
            .setNegativeButton("ביטול", null).show()
    }

    // ייצוא: השיחות כטקסט, דרך חלון השיתוף של אנדרואיד
    private fun export(list: List<ChatStore.Chat>) {
        if (list.isEmpty()) return
        val text = list.joinToString("\n\n—————\n\n") { ChatStore.asText(it, Assistant.NAME) }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, if (list.size == 1) list[0].title else "שיחות עם ${Assistant.NAME}")
            .putExtra(Intent.EXTRA_TEXT, text)
        try { startActivity(Intent.createChooser(i, "ייצוא שיחה")) } catch (_: Exception) { Toast.makeText(this, "אין אפליקציה לשיתוף", Toast.LENGTH_SHORT).show() }
    }

    private fun confirmDelete(ids: List<String>) {
        if (ids.isEmpty()) return
        dlg().setMessage(if (ids.size == 1) "למחוק את השיחה?" else "למחוק ${ids.size} שיחות?")
            .setPositiveButton("מחיקה") { _, _ -> ids.forEach { ChatStore.delete(this, it) }; selecting = false; picked.clear(); build() }
            .setNegativeButton("ביטול", null).show()
    }
}
