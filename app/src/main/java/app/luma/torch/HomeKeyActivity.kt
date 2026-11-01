package app.luma.torch

import android.os.Bundle
import android.view.View
import android.widget.*

// מקש הבית הפיזי שמתחת למסך: מה יקרה בלחיצה אחת ובלחיצה כפולה.
// מבוצע על ידי שירות הנגישות "עזרים - קיצורים" (CameraButtonService).
class HomeKeyActivity : BaseActivity() {
    private val names = listOf("כלום", "אחורה", "מסך הבית")
    private val codes = listOf(0, 4, 5)
    private lateinit var banner: View
    private lateinit var body: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val goA11y = tv("הפעלת השירות") { enableA11y(); refreshState() }
        style(goA11y, true)
        val bannerText = TextView(this).apply {
            text = "כדי לשנות את פעולת מקש הבית צריך קודם להפעיל את שירות הנגישות \"עזרים - קיצורים\"."
            setTextColor(fg); textSize = 14f; setPadding(0, 0, 0, dp(12))
        }
        banner = section("נדרשת הפעלת שירות", bannerText, goA11y)

        val single = oneLine(group(names, { codes.indexOf(Torch.homeAct).coerceAtLeast(0) }) { Torch.homeAct = codes[it] })
        val double = oneLine(group(names, { codes.indexOf(Torch.homeDblAct).coerceAtLeast(0) }) { Torch.homeDblAct = codes[it] })
        val note = TextView(this).apply {
            text = "כשלחיצה כפולה מוגדרת, הלחיצה האחת מתבצעת בהשהיה קצרה (כדי לזהות אם תבוא לחיצה שנייה)."
            textSize = 12f; setTextColor(dim); setPadding(dp(2), dp(10), 0, 0)
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(section("לחיצה אחת", single))
            addView(section("לחיצה כפולה", double, note))
        }
        page(header("מקש הבית"), banner, body)
        refreshState()
    }

    // כל הכפתורים בשורה אחת ובאותו גובה, גם אם הטקסט ארוך
    private fun oneLine(g: LinearLayout) = g.apply {
        for (i in 0 until childCount) (getChildAt(i) as TextView).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    private fun refreshState() {
        val on = a11yOn()
        banner.visibility = if (on) View.GONE else View.VISIBLE
        body.alpha = if (on) 1f else 0.4f
        setEnabledDeep(body, on)
    }

    private fun setEnabledDeep(v: View, on: Boolean) {
        v.isEnabled = on
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) setEnabledDeep(v.getChildAt(i), on)
    }

    override fun onResume() { super.onResume(); refreshState() }
}
