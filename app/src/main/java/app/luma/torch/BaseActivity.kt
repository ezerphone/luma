package app.luma.torch

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*

open class BaseActivity : Activity() {
    val isDark: Boolean get() = when (Torch.theme) {
        1 -> false
        2 -> true
        else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    val bg get() = if (isDark) 0xFF0A1013.toInt() else 0xFFF1F5F6.toInt()
    val card get() = if (isDark) 0xFF121A1F.toInt() else Color.WHITE
    val chip get() = if (isDark) 0xFF1B262C.toInt() else 0xFFE9EFF1.toInt()
    val line get() = if (isDark) 0xFF1F2C33.toInt() else 0xFFDDE5E8.toInt()
    val fg get() = if (isDark) 0xFFF2F6F7.toInt() else 0xFF0E1A1F.toInt()
    val dim get() = if (isDark) 0xFF8CA0A8.toInt() else 0xFF5E727A.toInt()
    val acc get() = if (isDark) 0xFF3DDCB4.toInt() else 0xFF0C9A82.toInt()
    val acc2 get() = if (isDark) 0xFF2BB8DB.toInt() else 0xFF0A80A6.toInt()
    val onAcc get() = if (isDark) 0xFF04201A.toInt() else Color.WHITE
    val accSoft get() = (acc and 0x00FFFFFF) or 0x26000000
    private var built = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Torch.init(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = if (isDark) 0 else 0x2010
        built = isDark
    }

    override fun onStart() {
        super.onStart()
        if (isDark != built) recreate()
    }
    fun dlg() = AlertDialog.Builder(this, if (isDark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    fun shape(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(c)
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    fun gradient(r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(acc, acc2)).apply { cornerRadius = dp(r).toFloat() }
    fun icon(res: Int, c: Int): Drawable = getDrawable(res)!!.mutate().apply { setTint(c) }
    val medium get() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun tv(t: String, size: Float = 15f, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = size; gravity = Gravity.CENTER; setPadding(dp(2), dp(13), dp(2), dp(13))
        typeface = medium
        setTextColor(fg); setOnClickListener { click() }
    }
    fun style(v: TextView, sel: Boolean) {
        v.setTextColor(if (sel) onAcc else fg)
        v.background = if (sel) gradient(14) else shape(chip, 14)
    }
    fun row(items: List<TextView>) = LinearLayout(this).apply {
        items.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
    }
    fun group(names: List<String>, sel: () -> Int, pick: (Int) -> Unit): LinearLayout {
        val cs = mutableListOf<TextView>()
        fun paint() = cs.forEachIndexed { i, c -> style(c, sel() == i) }
        names.forEachIndexed { i, n -> cs += tv(n, 14f) { pick(i); paint() } }
        paint()
        return row(cs)
    }
    fun tint(s: Switch) {
        val on = intArrayOf(android.R.attr.state_checked)
        s.thumbTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(acc, dim))
        s.trackTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf((acc and 0x00FFFFFF) or 0x66000000, chip))
        s.textSize = 15f
    }
    // הרצת פקודה עם הרשאת root (מחזיר קוד יציאה ופלט), דרך מעטפת root אחת משותפת
    fun runAsRoot(cmd: String): Pair<Int, String> = RootShell.run(cmd)
    // ציטוט בטוח לשורת shell, כדי שנתיב או שם עם תווים מיוחדים לא ישברו את הפקודה
    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun fmtTime(s: Int): String {
        fun mins(n: Int) = if (n == 1) "דקה" else "$n דקות"
        fun secs(n: Int) = if (n == 1) "שנייה" else "$n שניות"
        return when {
            s < 60 -> secs(s)
            s % 60 == 0 -> mins(s / 60)
            else -> mins(s / 60) + " ו-" + secs(s % 60)
        }
    }
    // חלון קופץ מעוצב (כרטיס מעוגל על רקע מעומעם)
    fun sheet(build: (Dialog) -> View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(build(d))
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(resources.displayMetrics.widthPixels - dp(40), WindowManager.LayoutParams.WRAP_CONTENT)
            decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
            setDimAmount(0.65f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }
        return d
    }
    fun section(title: String, vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(card, 24, line)
        setPadding(dp(18), dp(16), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(this@BaseActivity).apply {
            text = title; setTextColor(dim); textSize = 13f; typeface = medium; letterSpacing = 0.03f
            setPadding(dp(2), 0, 0, dp(12))
        })
        v.forEach { addView(it) }
    }
    fun header(title: String, end: View? = null, sub: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
        val top = LinearLayout(this@BaseActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@BaseActivity).apply { text = title; textSize = 30f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
            end?.let { addView(it) }
        }
        addView(top)
        sub?.let { addView(TextView(this@BaseActivity).apply { text = it; textSize = 14f; setTextColor(dim); setPadding(dp(2), dp(2), 0, 0) }) }
    }
    private fun scroller(v: Array<out View>) = ScrollView(this).apply {
        setBackgroundColor(bg); overScrollMode = View.OVER_SCROLL_NEVER
        addView(LinearLayout(this@BaseActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(48), dp(20), dp(24)); v.forEach { addView(it) }
        })
    }
    fun page(vararg v: View) = setContentView(scroller(v))
    // כמו page, עם שורת תחתית קבועה בתחתית המסך
    fun pageWithFooter(footer: View, vararg v: View) {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            addView(scroller(v), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
        })
    }
}
