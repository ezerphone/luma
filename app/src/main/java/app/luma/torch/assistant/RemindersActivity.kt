package app.luma.torch.assistant

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.luma.torch.BaseActivity
import app.luma.torch.R

// התזכורות הפתוחות של עזר: מספר סידורי קבוע, הטקסט ומתי, ומחיקה (עם אישור)
class RemindersActivity : BaseActivity() {
    override fun onResume() { super.onResume(); build() }

    private fun build() {
        val items = Assistant.openReminders(this)
        val sub = when (items.size) { 0 -> "אין תזכורות פתוחות"; 1 -> "תזכורת אחת פתוחה"; else -> "${items.size} תזכורות פתוחות" }
        val rows = items.map { r ->
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; background = shape(card, 20, line); setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
                addView(TextView(this@RemindersActivity).apply {
                    text = r.num.toString(); textSize = 15f; setTextColor(onAcc); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { shape = GradientDrawable.OVAL }
                }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
                addView(LinearLayout(this@RemindersActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@RemindersActivity).apply { text = r.text; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD })
                    addView(TextView(this@RemindersActivity).apply { text = Assistant.whenText(r.at); textSize = 12f; setTextColor(dim); setPadding(0, dp(2), 0, 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageView(this@RemindersActivity).apply {
                    setImageDrawable(icon(R.drawable.ic_delete, dim)); setPadding(dp(8), dp(8), dp(8), dp(8)); background = shape(chip, 12)
                    contentDescription = "מחיקת תזכורת ${r.num}"
                    setOnClickListener { confirm(r) }
                }, LinearLayout.LayoutParams(dp(36), dp(36)))
            }
        }
        val hint = TextView(this).apply {
            text = if (items.isEmpty()) "אפשר לבקש מעזר, למשל: \"תזכיר לי מחר בתשע לקנות חלב\""
                else "המספר נשאר קבוע לכל תזכורת.\nלביטול בקול: \"תבטל תזכורת ${items.first().num}\""
            textSize = 12f; setTextColor(dim); gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(0, dp(6), 0, 0)
        }
        page(header("תזכורות", null, sub), *rows.toTypedArray(), hint)
    }

    private fun confirm(r: Assistant.Rem) {
        dlg().setMessage("למחוק את תזכורת ${r.num}?\n${r.text} · ${Assistant.whenText(r.at)}")
            .setPositiveButton("מחיקה") { _, _ -> Assistant.cancelReminder(this, r.id); build() }
            .setNegativeButton("ביטול", null).show()
    }
}
