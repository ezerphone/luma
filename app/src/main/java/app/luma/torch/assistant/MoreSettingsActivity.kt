package app.luma.torch.assistant

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.luma.torch.BaseActivity
import app.luma.torch.R

// הגדרות נוספות: הרשאות, עוזר המערכת, ומראה בועת הצ'אט (צבע ומצב בהיר / כהה / אוטומטי)
class MoreSettingsActivity : BaseActivity() {
    private lateinit var permSub: TextView
    private lateinit var roleSub: TextView
    private val dots = mutableListOf<View>()
    private val segs = mutableListOf<TextView>()
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val perm = navCard(R.drawable.ic_lock, "הרשאות", chevron = false) { fixPermissions() }
        val role = navCard(R.drawable.ic_assist, "עוזר המערכת", chevron = false) {
            if (!Assistant.isDefault(this)) Assistant.makeDefault(this) { runOnUiThread { refresh() } }
        }
        permSub = perm.second; roleSub = role.second
        page(header("הגדרות נוספות"), perm.first, role.first, bubbleCard())
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val miss = Perms.missing(this)
        permSub.text = when (miss) { 0 -> "כל ההרשאות מאושרות"; 1 -> "חסרה הרשאה אחת"; else -> "חסרות $miss הרשאות" }
        roleSub.text = if (Assistant.isDefault(this)) "${Assistant.NAME} מוגדר כעוזר המערכת" else "${Assistant.NAME} לא מוגדר כעוזר המערכת · לחיצה להגדרה"
        paintBubble()
    }

    // בועת הצ'אט: שישה צבעים, ומצב תצוגה
    private fun bubbleCard(): View {
        fun label(t: String, top: Int) = TextView(this).apply {
            text = t; textSize = 13f; setTextColor(dim); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(2), dp(top), dp(2), dp(8))
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        Bubble.TONES.forEachIndexed { i, t ->
            val d = View(this).apply {
                contentDescription = listOf("כחול", "סגול", "טורקיז", "ירוק", "ורוד", "כתום")[i]
                setOnClickListener { Bubble.setColor(this@MoreSettingsActivity, i); paintBubble() }
            }
            d.tag = t; dots += d
            row.addView(d, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        }
        val seg = LinearLayout(this).apply { background = shape(chip, 14); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        Bubble.MODES.forEachIndexed { i, m ->
            val t = TextView(this).apply {
                text = m; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(9), 0, dp(9))
                setOnClickListener { Bubble.setMode(this@MoreSettingsActivity, i); paintBubble() }
            }
            segs += t; seg.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = shape(card, 20, line); setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            addView(TextView(this@MoreSettingsActivity).apply { text = "בועת הצ׳אט"; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD })
            addView(label("צבע", 10)); addView(row)
            addView(label("מצב תצוגה", 16)); addView(seg)
        }
    }

    private fun paintBubble() {
        val sel = Bubble.color(this)
        dots.forEachIndexed { i, d ->
            val t = d.tag as Bubble.Tone
            d.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.deep, t.main)).apply {
                shape = GradientDrawable.OVAL
                if (i == sel) setStroke(dp(3), fg)
            }
            d.scaleX = if (i == sel) 1.08f else 1f; d.scaleY = d.scaleX
        }
        val m = Bubble.mode(this)
        segs.forEachIndexed { i, t ->
            val on = i == m
            t.background = if (on) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { cornerRadius = dp(11).toFloat() } else null
            t.setTextColor(if (on) Color.WHITE else fg); t.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    // בדיקת הרשאות: מפעיל לבד את מה שאפשר, מבקש את השאר, ומציג סיכום
    private fun fixPermissions() {
        Perms.fixSpecial(this)
        val ask = Perms.runtime().map { it.first }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (ask.isNotEmpty()) requestPermissions(ask.toTypedArray(), 7) else h.postDelayed({ summary() }, 700)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(code, p, g)
        h.postDelayed({ summary() }, 400)
    }

    private fun summary() {
        refresh()
        val lines = Perms.status(this).joinToString("\n") { (name, ok) -> (if (ok) "✓  " else "✗  ") + name }
        dlg().setTitle("הרשאות").setMessage(lines).setPositiveButton("סגור", null).show()
    }
}
