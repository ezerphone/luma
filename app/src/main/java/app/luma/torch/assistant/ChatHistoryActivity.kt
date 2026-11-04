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
import app.luma.torch.R
import java.text.SimpleDateFormat
import java.util.*

// היסטוריית השיחות עם עזר. לחיצה ארוכה על שיחה: שינוי שם, ייצוא, ארכיון, סימון כמה שיחות, מחיקה.
// עם archive=true — מסך הארכיון (אותן פעולות, עם "הוצאה מהארכיון")
class ChatHistoryActivity : BaseActivity() {
    private val picked = LinkedHashSet<String>()
    private var selecting = false
    private val red = 0xFFC62828.toInt()
    private val archive get() = intent.getBooleanExtra("archive", false)

    // מסך הכניסה לעזר: השיחות האחרונות (מתעדכן בכל חזרה למסך)
    override fun onResume() { super.onResume(); build() }

    @Deprecated("") override fun onBackPressed() { if (selecting) endSelect() else super.onBackPressed() }

    private fun endSelect() { selecting = false; picked.clear(); build() }

    private fun build() {
        val chats = ChatStore.list(this, archive)
        picked.retainAll(chats.map { it.id }.toSet())
        if (selecting && chats.isEmpty()) selecting = false
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
        val views = mutableListOf<View>()
        if (selecting) {
            // מצב סימון: חזרה · "N נבחרו" · הכול; ולמטה ארכיון · ייצוא · מחיקה
            val allPicked = picked.size == chats.size
            val bar = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
                addView(ImageView(this@ChatHistoryActivity).apply {
                    setImageDrawable(icon(R.drawable.ic_back, fg)); scaleX = -1f   // חץ חזרה (ימינה, בעברית)
                    setPadding(dp(9), dp(9), dp(9), dp(9)); background = shape(chip, 14); contentDescription = "חזרה"
                    setOnClickListener { endSelect() }
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
                addView(TextView(this@ChatHistoryActivity).apply { text = "${picked.size} נבחרו"; textSize = 22f; setTextColor(fg); typeface = android.graphics.Typeface.DEFAULT_BOLD },
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(pill(R.drawable.ic_select_all, "הכול", if (allPicked) acc else fg) {
                    if (allPicked) picked.clear() else picked += chats.map { it.id }
                    build()
                })
            }
            views += bar; views += items
            val acts = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
                val sel = { chats.filter { it.id in picked } }
                if (archive) addView(pill(R.drawable.ic_archive, "הוצאה", fg) { moveArchive(picked.toList(), false) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                else addView(pill(R.drawable.ic_archive, "ארכיון", fg) { moveArchive(picked.toList(), true) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                addView(pill(R.drawable.ic_share, "ייצוא", fg) { export(sel()) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                addView(pill(R.drawable.ic_delete, "מחיקה", red) { confirmDelete(picked.toList()) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            views += acts
        } else if (archive) {
            views += header("ארכיון", null, if (items.isEmpty()) null else "${items.size} שיחות")
            views += items
            if (items.isEmpty()) views += TextView(this).apply { text = "אין שיחות בארכיון"; textSize = 13f; setTextColor(dim); gravity = Gravity.CENTER }
            else views += TextView(this).apply { text = "לחיצה ארוכה על שיחה — אפשרויות"; textSize = 12f; setTextColor(dim); gravity = Gravity.CENTER }
        } else {
            val newChat = tv("+ שיחה חדשה") { startActivity(Intent(this, ChatActivity::class.java).putExtra("new", true)) }
            style(newChat, true)
            newChat.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            val gear = ImageView(this).apply {
                setImageDrawable(icon(R.drawable.ic_settings, dim)); setPadding(dp(9), dp(9), dp(9), dp(9)); background = shape(card, 14, line)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { startActivity(Intent(this@ChatHistoryActivity, AssistantActivity::class.java)) }
            }
            views += header(Assistant.NAME, gear, "שיחות אחרונות"); views += newChat; views += items
            // שורת "ארכיון" בתחתית — רק כשיש בו שיחות
            val archived = ChatStore.list(this, true).size
            if (archived > 0) views += archiveRow(archived)
            views += TextView(this).apply {
                text = if (items.isEmpty()) "אין עדיין שיחות" else "לחיצה ארוכה על שיחה — אפשרויות"
                textSize = 12f; setTextColor(dim); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
            }
        }
        page(*views.toTypedArray())
    }

    private fun pill(res: Int, t: String, color: Int, click: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER; background = shape(chip, 14); setPadding(dp(12), dp(9), dp(12), dp(9))
        addView(ImageView(this@ChatHistoryActivity).apply { setImageDrawable(icon(res, color)) }, LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(6) })
        addView(TextView(this@ChatHistoryActivity).apply { text = t; textSize = 14f; setTextColor(color); typeface = medium })
        setOnClickListener { click() }
    }

    private fun archiveRow(n: Int) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = shape(card, 18, line); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(ImageView(this@ChatHistoryActivity).apply {
            setImageDrawable(icon(R.drawable.ic_archive, acc)); setPadding(dp(8), dp(8), dp(8), dp(8)); background = shape(accSoft, 12)
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@ChatHistoryActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ChatHistoryActivity).apply { text = "ארכיון"; textSize = 15f; setTextColor(fg); typeface = medium })
            addView(TextView(this@ChatHistoryActivity).apply { text = if (n == 1) "שיחה אחת" else "$n שיחות"; textSize = 12f; setTextColor(dim) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(this@ChatHistoryActivity).apply { setImageDrawable(icon(R.drawable.ic_chevron, dim)) }, LinearLayout.LayoutParams(dp(18), dp(18)))
        setOnClickListener { startActivity(Intent(this@ChatHistoryActivity, ChatHistoryActivity::class.java).putExtra("archive", true)) }
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
            if (archive) {
                addView(row("הוצאה מהארכיון") { moveArchive(listOf(ch.id), false) })
                addView(row("ייצוא שיחה") { export(listOf(ch)) })
                addView(row("סימון כמה שיחות") { selecting = true; picked.clear(); picked += ch.id; build() })
            } else {
                addView(row("שינוי שם") { rename(ch) })
                addView(row("ייצוא שיחה") { export(listOf(ch)) })
                addView(row("העברה לארכיון") { moveArchive(listOf(ch.id), true) })
                addView(row("סימון כמה שיחות") { selecting = true; picked.clear(); picked += ch.id; build() })
            }
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

    private fun moveArchive(ids: List<String>, toArchive: Boolean) {
        if (ids.isEmpty()) return
        ids.forEach { ChatStore.setArchived(this, it, toArchive) }
        Toast.makeText(this, when {
            toArchive && ids.size == 1 -> "השיחה הועברה לארכיון"
            toArchive -> "${ids.size} שיחות הועברו לארכיון"
            ids.size == 1 -> "השיחה הוצאה מהארכיון"
            else -> "${ids.size} שיחות הוצאו מהארכיון"
        }, Toast.LENGTH_SHORT).show()
        selecting = false; picked.clear(); build()
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
