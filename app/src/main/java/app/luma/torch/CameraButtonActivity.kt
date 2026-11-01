package app.luma.torch

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import app.luma.torch.dictation.ModelLocator
import app.luma.torch.dictation.VoiceIme

// מסך ראשון: בוחרים איזו לחיצה להגדיר (רגילה, ארוכה, כפולה). הבחירה עצמה במסך CameraActionActivity.
class CameraButtonActivity : BaseActivity() {
    private lateinit var banner: View
    private lateinit var shield: View
    private lateinit var rows: LinearLayout
    private lateinit var dictSec: View

    private fun appName(p: String) = try { packageManager.getApplicationInfo(p, 0).loadLabel(packageManager).toString() } catch (_: Exception) { "לא נבחרה" }

    private fun summary(act: Int, pkg: String) = when (act) {
        0 -> "אף אחד"
        1 -> "פנס"
        2 -> if (pkg.isEmpty()) "לא נבחרה אפליקציה" else "פתיחת " + appName(pkg)
        3 -> "צילום מסך"
        4 -> "חזרה אחורה"
        5 -> "חזרה למסך הבית"
        6 -> "עוזר אישי"
        8 -> "הפעלה / עצירה של מדיה"
        9 -> "אפליקציות אחרונות"
        else -> "אף אחד"
    }

    private fun slotRow(title: String, slot: Int, act: Int, pkg: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = shape(card, 20, line); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(LinearLayout(this@CameraButtonActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@CameraButtonActivity).apply { text = title; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@CameraButtonActivity).apply { text = summary(act, pkg); textSize = 13f; setTextColor(dim); setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(this@CameraButtonActivity).apply { setImageDrawable(icon(R.drawable.ic_chevron, dim)) }, LinearLayout.LayoutParams(dp(18), dp(18)))
        setOnClickListener {
            startActivity(Intent(this@CameraButtonActivity, CameraActionActivity::class.java).putExtra("slot", slot))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val goA11y = tv("הפעלת השירות") { enableA11y(); refreshState() }
        style(goA11y, true)
        val bannerText = TextView(this).apply {
            text = "כדי לבחור פעולות לכפתור המצלמה צריך קודם להפעיל את שירות הנגישות \"עזרים - קיצורים\"."
            setTextColor(fg); textSize = 14f; setPadding(0, 0, 0, dp(12))
        }
        banner = section("נדרשת הפעלת שירות", bannerText, goA11y)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dictSec = dictationSection()
        shield = View(this).apply { isClickable = true }
        val locked = FrameLayout(this).apply {
            addView(rows)
            addView(shield, FrameLayout.LayoutParams(-1, -1))
        }
        page(
            header("כפתור המצלמה", null, "בחר לחיצה ואז מה היא תפעיל"),
            banner,
            locked
        )
        refreshState()
    }

    // כל עוד שירות הנגישות כבוי, אי אפשר לבחור פעולות. הסיכומים מתעדכנים בכל חזרה למסך
    private fun refreshState() {
        rows.removeAllViews()
        rows.addView(slotRow("לחיצה רגילה", 0, Torch.shortAct, Torch.shortPkg))
        rows.addView(slotRow("לחיצה ארוכה", 1, Torch.longAct, Torch.longPkg))
        rows.addView(slotRow("לחיצה כפולה", 2, Torch.dblAct, Torch.dblPkg))
        rows.addView(dictSec)
        val on = a11yOn()
        banner.visibility = if (on) View.GONE else View.VISIBLE
        shield.visibility = if (on) View.GONE else View.VISIBLE
        rows.alpha = if (on) 1f else 0.4f
    }

    // הכתבה קולית כשיש שדה טקסט פתוח: במקום הפעולות שלמעלה, הכפתור מכתיב לתוך השדה
    private fun dictationSection(): View {
        val installed = ModelLocator.isInstalled()
        val sw = Switch(this).apply { text = "הכתבה קולית כשיש שדה טקסט פתוח"; setTextColor(fg); isChecked = Torch.dictInText && installed }
        tint(sw)
        var repaint: () -> Unit = {}
        val mode = group(listOf("החזקה", "הפעלה וכיבוי"), { if (Torch.dictHold) 0 else 1 }) { Torch.dictHold = it == 0; repaint() }
        mode.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        val hint = TextView(this).apply {
            textSize = 12f; setTextColor(dim); setLineSpacing(0f, 1.2f); setPadding(dp(2), dp(10), 0, 0)
        }
        fun paint() {
            val on = sw.isChecked
            mode.visibility = if (on) View.VISIBLE else View.GONE
            hint.text = when {
                !installed -> "מודל זיהוי הדיבור לא נמצא בקושחה."
                !on -> "כשהסמן בשדה טקסט, הכפתור יכתיב לתוכו במקום הפעולות שלמעלה."
                Torch.dictHold -> "מחזיקים את הכפתור, מדברים ועוזבים. הטקסט נכנס והמקלדת הרגילה חוזרת."
                else -> "לחיצה מתחילה להכתיב, לחיצה נוספת עוצרת. הטקסט נכנס והמקלדת הרגילה חוזרת."
            }
        }
        repaint = { paint() }
        sw.isEnabled = installed
        sw.setOnCheckedChangeListener { _, c ->
            Torch.dictInText = c
            if (c) VoiceIme.enableNow(this) // המקלדת הקולית חייבת להיות ברשימת המקלדות
            paint()
        }
        paint()
        return section("בשדה טקסט", sw, mode, hint)
    }

    override fun onResume() { super.onResume(); refreshState() }
}
