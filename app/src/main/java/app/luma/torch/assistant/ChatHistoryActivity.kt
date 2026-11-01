package app.luma.torch.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.*
import app.luma.torch.BaseActivity
import java.text.SimpleDateFormat
import java.util.*

// היסטוריית השיחות עם עזר
class ChatHistoryActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); build() }

    private fun build() {
        val newChat = tv("+ שיחה חדשה") { startActivity(Intent(this, ChatActivity::class.java).putExtra("new", true)); finish() }
        style(newChat, true)
        newChat.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        val fmt = SimpleDateFormat("EEEE · HH:mm", Locale("he"))
        val items = ChatStore.list(this).map { ch ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = shape(card, 18, line); setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                addView(TextView(this@ChatHistoryActivity).apply { text = ch.title; textSize = 15f; setTextColor(fg); typeface = medium; maxLines = 1 })
                addView(TextView(this@ChatHistoryActivity).apply { text = fmt.format(Date(ch.updated)); textSize = 12f; setTextColor(dim) })
                setOnClickListener { startActivity(Intent(this@ChatHistoryActivity, ChatActivity::class.java).putExtra("id", ch.id)); finish() }
                setOnLongClickListener {
                    dlg().setMessage("למחוק את השיחה?").setPositiveButton("מחיקה") { _, _ -> ChatStore.delete(this@ChatHistoryActivity, ch.id); build() }
                        .setNegativeButton("ביטול", null).show(); true
                }
            }
        }
        val note = TextView(this).apply { text = if (items.isEmpty()) "אין עדיין שיחות" else "לחיצה ארוכה על שיחה — מחיקה"; textSize = 12f; setTextColor(dim); gravity = android.view.Gravity.CENTER }
        page(header("שיחות"), newChat, *items.toTypedArray(), note)
    }
}
