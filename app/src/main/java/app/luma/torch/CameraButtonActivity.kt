package app.luma.torch

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class CameraButtonActivity : BaseActivity() {
    private class AppRow(val label: String, val pkg: String, val info: ResolveInfo)

    private fun appName(p: String) = try { packageManager.getApplicationInfo(p, 0).loadLabel(packageManager).toString() } catch (_: Exception) { "לא נבחרה" }

    // בחירת אפליקציה מהרשימה, עם שם ואייקון
    private fun choose(cb: (String) -> Unit) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val items = packageManager.queryIntentActivities(i, 0)
            .map { AppRow(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        val icons = HashMap<String, Drawable>()
        val ad = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val a = items[p]
                val r = (cv as? LinearLayout) ?: LinearLayout(this@CameraButtonActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(9), dp(6), dp(9))
                    addView(ImageView(this@CameraButtonActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                    addView(TextView(this@CameraButtonActivity).apply { textSize = 16f; setTextColor(fg) }, LinearLayout.LayoutParams(0, -2, 1f))
                }
                (r.getChildAt(0) as ImageView).setImageDrawable(icons.getOrPut(a.pkg) { a.info.loadIcon(packageManager) })
                (r.getChildAt(1) as TextView).text = a.label
                return r
            }
        }
        sheet { d ->
            val list = ListView(this).apply {
                adapter = ad; divider = null; setSelector(ColorDrawable(Color.TRANSPARENT))
                setOnItemClickListener { _, _, pos, _ -> d.dismiss(); cb(items[pos].pkg) }
            }
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = shape(card, 28, line)
                setPadding(dp(20), dp(22), dp(20), dp(12))
                addView(TextView(this@CameraButtonActivity).apply {
                    text = "בחירת אפליקציה"; textSize = 20f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(10))
                })
                addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
            }
        }.show()
    }

    private fun block(title: String, act: () -> Int, setAct: (Int) -> Unit, pkg: () -> String, setPkg: (String) -> Unit): View {
        val appIcon = ImageView(this)
        val name = TextView(this).apply { textSize = 15f; setTextColor(fg); typeface = medium }
        val change = TextView(this).apply { text = "שינוי"; textSize = 13f; setTextColor(acc) }
        val appRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = shape(chip, 16); setPadding(dp(12), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            addView(appIcon, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
            addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            addView(change)
        }
        fun upd() {
            appRow.visibility = if (act() == 2) View.VISIBLE else View.GONE
            val p = pkg()
            if (p.isEmpty()) {
                name.text = "לא נבחרה אפליקציה"; appIcon.setImageDrawable(icon(R.drawable.ic_camera, dim))
            } else {
                name.text = appName(p)
                try { appIcon.setImageDrawable(packageManager.getApplicationIcon(p)) }
                catch (_: Exception) { appIcon.setImageDrawable(icon(R.drawable.ic_camera, dim)) }
            }
        }
        appRow.setOnClickListener { choose { setPkg(it); upd() } }
        // חמש אפשרויות, בשתי שורות כדי שיהיה נוח לקריאה במסך טלפון
        val names = listOf("ללא", "פנס", "פתיחת אפליקציה", "צילום מסך", "חזרה אחורה")
        val chips = names.map { n -> tv(n, 14f) {} }
        fun paint() = chips.forEachIndexed { i, c -> style(c, act() == i) }
        chips.forEachIndexed { i, c ->
            c.setOnClickListener {
                setAct(i)
                if (i == 2) choose { setPkg(it); upd() }
                upd(); paint()
            }
        }
        paint()
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row(chips.subList(0, 3)))
            addView(row(chips.subList(3, 5)).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } })
        }
        upd()
        return section(title, grid, appRow)
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
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(block("לחיצה רגילה", { Torch.shortAct }, { Torch.shortAct = it }, { Torch.shortPkg }, { Torch.shortPkg = it }))
            addView(block("לחיצה ארוכה", { Torch.longAct }, { Torch.longAct = it }, { Torch.longPkg }, { Torch.longPkg = it }))
            addView(block("לחיצה כפולה", { Torch.dblAct }, { Torch.dblAct = it }, { Torch.dblPkg }, { Torch.dblPkg = it }))
        }
        shield = View(this).apply { isClickable = true }
        val locked = FrameLayout(this).apply {
            addView(controls)
            addView(shield, FrameLayout.LayoutParams(-1, -1))
        }
        page(
            header("כפתור המצלמה", null, "קבע מה יקרה בכל לחיצה"),
            banner,
            locked
        )
        refreshState()
    }

    private lateinit var banner: View
    private lateinit var controls: View
    private lateinit var shield: View

    private fun serviceOn(): Boolean {
        val cn = ComponentName(this, CameraButtonService::class.java)
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it) == cn }
    }

    // כל עוד שירות הנגישות כבוי, אי אפשר לבחור פעולות
    private fun refreshState() {
        val on = serviceOn()
        banner.visibility = if (on) View.GONE else View.VISIBLE
        shield.visibility = if (on) View.GONE else View.VISIBLE
        controls.alpha = if (on) 1f else 0.4f
    }

    override fun onResume() { super.onResume(); refreshState() }
}
