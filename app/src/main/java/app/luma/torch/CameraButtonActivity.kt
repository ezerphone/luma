package app.luma.torch

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

// מסך ראשון: בוחרים איזו לחיצה להגדיר (רגילה, ארוכה, כפולה). הבחירה עצמה במסך CameraActionActivity.
class CameraButtonActivity : BaseActivity() {
    private lateinit var banner: View
    private lateinit var shield: View
    private lateinit var rows: LinearLayout

    private fun appName(p: String) = try { packageManager.getApplicationInfo(p, 0).loadLabel(packageManager).toString() } catch (_: Exception) { "לא נבחרה" }

    private fun summary(act: Int, pkg: String) = when (act) {
        0 -> "אף אחד"
        1 -> "פנס"
        2 -> if (pkg.isEmpty()) "לא נבחרה אפליקציה" else "פתיחת " + appName(pkg)
        3 -> "צילום מסך"
        4 -> "חזרה אחורה"
        5 -> "חזרה למסך הבית"
        6 -> "עוזר אישי"
        7 -> "התחלת הקלטת מסך"
        else -> ""
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
        val goA11y = tv("הפעלת השירות") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        style(goA11y, true)
        val bannerText = TextView(this).apply {
            text = "כדי לבחור פעולות לכפתור המצלמה צריך קודם להפעיל את שירות הנגישות."
            setTextColor(fg); textSize = 14f; setPadding(0, 0, 0, dp(12))
        }
        banner = section("נדרשת הפעלת שירות", bannerText, goA11y)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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

    private fun serviceOn(): Boolean {
        val cn = ComponentName(this, CameraButtonService::class.java)
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it) == cn }
    }

    // כל עוד שירות הנגישות כבוי, אי אפשר לבחור פעולות. הסיכומים מתעדכנים בכל חזרה למסך
    private fun refreshState() {
        rows.removeAllViews()
        rows.addView(slotRow("לחיצה רגילה", 0, Torch.shortAct, Torch.shortPkg))
        rows.addView(slotRow("לחיצה ארוכה", 1, Torch.longAct, Torch.longPkg))
        rows.addView(slotRow("לחיצה כפולה", 2, Torch.dblAct, Torch.dblPkg))
        val on = serviceOn()
        banner.visibility = if (on) View.GONE else View.VISIBLE
        shield.visibility = if (on) View.GONE else View.VISIBLE
        rows.alpha = if (on) 1f else 0.4f
    }

    override fun onResume() { super.onResume(); refreshState() }
}
