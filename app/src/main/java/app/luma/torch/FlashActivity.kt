package app.luma.torch

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class FlashActivity : BaseActivity() {
    private lateinit var dial: ImageView
    private lateinit var halo: View
    private lateinit var blinkBtn: TextView
    private lateinit var status: TextView
    private lateinit var tip: TextView
    private val refresh = { ui() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        dial = ImageView(this).apply {
            setImageResource(R.drawable.ic_power); setPadding(dp(31), dp(31), dp(31), dp(31))
            setOnClickListener { Torch.set(!Torch.on) }
        }
        halo = View(this)
        val hero = FrameLayout(this).apply {
            addView(halo, FrameLayout.LayoutParams(dp(130), dp(130), Gravity.CENTER))
            addView(dial, FrameLayout.LayoutParams(dp(98), dp(98), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(-1, dp(140))
        }
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 22f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, 0) }
        tip = TextView(this).apply { gravity = Gravity.CENTER; textSize = 13f; setTextColor(dim); setPadding(0, dp(4), 0, dp(24)) }
        blinkBtn = tv("הבהוב פנס", 16f) { Torch.blink(!Torch.blink) }
        blinkBtn.compoundDrawablePadding = dp(10)
        blinkBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        val sw = Switch(this).apply {
            text = "הדלקה וכיבוי בניעור"; setTextColor(fg); isChecked = Torch.shake
            setOnCheckedChangeListener { _, c ->
                Torch.shake = c
                // ההתראה היחידה באפליקציה היא של שירות הניעור, לכן רק כאן מבקשים הרשאת התראות
                if (c && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                val i = Intent(this@FlashActivity, FlashService::class.java)
                if (c) startForegroundService(i) else stopService(i)
            }
        }
        val snd = Switch(this).apply {
            text = "צליל קצר בהדלקה בניעור"; setTextColor(fg); isChecked = Torch.sound; setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, c -> Torch.sound = c }
        }
        tint(sw); tint(snd)
        val sensCap = TextView(this).apply { text = "רגישות ניעור"; textSize = 13f; setTextColor(dim); setPadding(dp(2), dp(18), 0, dp(10)) }
        val sensPick = group(listOf("נמוכה", "בינונית", "גבוהה"), { Torch.sens }) { Torch.sens = it }
        val addTile = tv("הוספת כפתור פנס לסרגל ההגדרות המהירות") { addTile() }
        style(addTile, false)
        page(
            header("פנס", null, "הדלקה, הבהוב וכיבוי אוטומטי"), hero, status, tip,
            timerSection(),
            blinkBtn,
            section("תנועה", sw, snd, sensCap, sensPick),
            section("סרגל ההגדרות המהירות", addTile)
        )
        ui()
    }

    // ---------- כיבוי אוטומטי ----------
    private fun timerSection(): View {
        val sw = Switch(this).apply { text = "כיבוי אוטומטי"; setTextColor(fg) }
        tint(sw)
        val cap = TextView(this).apply { text = "זמן עד כיבוי"; textSize = 12f; setTextColor(dim) }
        val valueTv = TextView(this).apply { textSize = 17f; setTextColor(fg); typeface = medium }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(cap); addView(valueTv) }
        val pick = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = shape(chip, 16); setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
            addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageView(this@FlashActivity).apply { setImageDrawable(icon(R.drawable.ic_chevron, dim)) }, LinearLayout.LayoutParams(dp(20), dp(20)))
        }
        var painting = false
        fun paint() {
            painting = true; sw.isChecked = Torch.timer > 0; painting = false
            valueTv.text = fmtTime(if (Torch.timer > 0) Torch.timer else Torch.timerLast)
            pick.alpha = if (Torch.timer > 0) 1f else 0.5f
        }
        sw.setOnCheckedChangeListener { _, c ->
            if (!painting) { Torch.timer = if (c) Torch.timerLast else 0; paint() }
        }
        pick.setOnClickListener { timerDialog { paint() } }
        paint()
        return section("כיבוי אוטומטי", sw, pick)
    }

    private fun timerDialog(done: () -> Unit) {
        val cur = if (Torch.timer > 0) Torch.timer else Torch.timerLast
        var minutes = cur % 60 == 0
        var num = if (minutes) cur / 60 else cur
        sheet { d ->
            fun max() = if (minutes) 1440 else 86400
            fun total() = if (minutes) num * 60 else num
            val title = TextView(this).apply { text = "כיבוי אוטומטי"; textSize = 22f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD }
            val sub = TextView(this).apply { text = "אחרי כמה זמן הפנס יכבה מעצמו"; textSize = 14f; setTextColor(dim); setPadding(0, dp(4), 0, dp(18)) }
            fun seg(t: String) = TextView(this).apply { text = t; textSize = 15f; gravity = Gravity.CENTER; typeface = medium; setPadding(0, dp(10), 0, dp(10)) }
            val bMin = seg("דקות"); val bSec = seg("שניות")
            val segBox = LinearLayout(this).apply {
                background = shape(chip, 16); setPadding(dp(4), dp(4), dp(4), dp(4))
                addView(bMin, LinearLayout.LayoutParams(0, -2, 1f)); addView(bSec, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER; textSize = 52f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(fg); background = null; filters = arrayOf(InputFilter.LengthFilter(5)); setSelectAllOnFocus(true)
                setText(num.toString())
            }
            val unitLbl = TextView(this).apply { gravity = Gravity.CENTER; textSize = 15f; setTextColor(dim) }
            val preview = TextView(this).apply { gravity = Gravity.CENTER; textSize = 13f; setTextColor(acc); setPadding(0, dp(10), 0, 0) }
            fun paint() {
                fun segStyle(v: TextView, sel: Boolean) { v.setTextColor(if (sel) onAcc else dim); v.background = if (sel) gradient(12) else null }
                segStyle(bMin, minutes); segStyle(bSec, !minutes)
                unitLbl.text = if (minutes) (if (num == 1) "דקה" else "דקות") else (if (num == 1) "שנייה" else "שניות")
                preview.text = "הפנס יכבה אחרי " + fmtTime(total())
            }
            fun setNum(n: Int) {
                num = n.coerceIn(1, max())
                val s = num.toString()
                if (et.text.toString() != s) { et.setText(s); et.setSelection(s.length) }
                paint()
            }
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val n = s.toString().toIntOrNull()
                    if (n != null && n >= 1) { num = n.coerceIn(1, max()); paint() }
                }
            })
            bMin.setOnClickListener { minutes = true; setNum(num) }
            bSec.setOnClickListener { minutes = false; setNum(num) }
            fun step(t: String, delta: Int) = TextView(this).apply {
                text = t; textSize = 28f; gravity = Gravity.CENTER; setTextColor(fg)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) }
                setOnClickListener { setNum(num + delta * (if (minutes) 1 else 5)) }
            }
            val numRow = LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }
                addView(step("−", -1), LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(et, LinearLayout.LayoutParams(0, -2, 1f))
                addView(step("+", 1), LinearLayout.LayoutParams(dp(56), dp(56)))
            }
            val ok = tv("אישור", 16f) {
                val n = et.text.toString().toIntOrNull()
                if (n == null || n < 1) Toast.makeText(this, "יש להזין מספר גדול מאפס", Toast.LENGTH_SHORT).show()
                else {
                    num = n.coerceIn(1, max())
                    val secs = total(); Torch.timer = secs; Torch.timerLast = secs
                    d.dismiss(); done()
                }
            }
            val cancel = tv("ביטול", 16f) { d.dismiss() }
            style(ok, true); style(cancel, false)
            val btns = row(listOf(ok, cancel)).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) } }
            paint()
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = shape(card, 28, line)
                setPadding(dp(24), dp(24), dp(24), dp(20))
                addView(title); addView(sub); addView(segBox); addView(numRow); addView(unitLbl); addView(preview); addView(btns)
            }
        }.show()
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, FlashTile::class.java), "פנס",
                Icon.createWithResource(this, R.drawable.ic_flash), mainExecutor) { }
        } else {
            dlg().setMessage("החלק מטה מראש המסך פעמיים, לחץ על סמל העריכה (עיפרון) וגרור את האריח \"פנס\" לסרגל.").setPositiveButton("הבנתי", null).show()
        }
    }

    override fun onStart() { super.onStart(); Torch.listeners += refresh; ui() }
    override fun onStop() { super.onStop(); Torch.listeners -= refresh }

    private fun ui() {
        val on = Torch.on
        dial.setColorFilter(if (on) onAcc else acc)
        dial.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, if (on) intArrayOf(acc, acc2) else intArrayOf(card, card)).apply {
            shape = GradientDrawable.OVAL; setStroke(dp(2), if (on) Color.TRANSPARENT else line)
        }
        halo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (on) {
                gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = dp(65).toFloat()
                colors = intArrayOf((acc and 0x00FFFFFF) or 0x66000000, (acc and 0x00FFFFFF))
            } else setColor(Color.TRANSPARENT)
        }
        style(blinkBtn, Torch.blink)
        blinkBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_blink, if (Torch.blink) onAcc else fg), null, null, null)
        status.text = when {
            Torch.busy -> "המצלמה בשימוש באפליקציה אחרת"
            Torch.blink -> "הפנס מהבהב"
            on -> "הפנס דלוק"
            else -> "הפנס כבוי"
        }
        tip.text = when {
            Torch.busy -> "סגור את האפליקציה האחרת כדי להמשיך"
            Torch.blink -> "לחץ על כפתור ההבהוב כדי לעצור"
            on -> if (Torch.timer > 0) "יכבה אוטומטית אחרי ${fmtTime(Torch.timer)}" else "הקש על הכפתור לכיבוי"
            else -> "הקש על הכפתור להדלקה"
        }
    }
}
