package app.luma.torch

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

// מתן/ביטול הרשאת מיקום מדומה (android:mock_location) לאפליקציה נבחרת.
// ההרשאה המתאימה (MANAGE_APP_OPS_MODES) היא ברמת "signature" בלבד — לא "signature|privileged" —
// ולכן אין דרך לאשר אותה לעזרים דרך קובץ הרשאות priv-app, גם אם עזרים מותקנת כאפליקציית מערכת.
// הפעולה היחידה האפשרית היא דרך root.
class MockLocationActivity : BaseActivity() {
    private class AppRow(val label: String, val pkg: String, val info: ResolveInfo)
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pickBtn = tv("בחירת אפליקציה למתן הרשאה") { choose { grant(it) } }
        style(pickBtn, true)
        pickBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

        status = TextView(this).apply {
            textSize = 13f; setTextColor(if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt())
            visibility = View.GONE; setPadding(0, 0, 0, dp(10))
        }

        val info = TextView(this).apply {
            text = "אפליקציות עם ההרשאה הזו יכולות לדווח מיקום GPS מזויף במקום המיקום האמיתי. השינוי דורש הרשאת root."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f)
        }

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        refreshList()

        page(
            header("מיקום מדומה", null, "מתן הרשאת מיקום מזויף לאפליקציה"),
            pickBtn, status,
            section("אפליקציות עם הרשאה", listBox),
            section("מידע", info)
        )
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val apps = Torch.mockLocationApps.sorted()
        if (apps.isEmpty()) {
            listBox.addView(TextView(this).apply { text = "אין כרגע אפליקציות עם הרשאה"; textSize = 13f; setTextColor(dim) })
            return
        }
        apps.forEach { pkg ->
            val label = try { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() } catch (_: Exception) { pkg }
            val iconView = ImageView(this).apply {
                try { setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (_: Exception) { setImageDrawable(icon(R.drawable.ic_camera, dim)) }
            }
            val revoke = tv("ביטול", 13f) { revoke(pkg) }
            style(revoke, false)
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8))
                addView(iconView, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
                addView(TextView(this@MockLocationActivity).apply { text = label; textSize = 15f; setTextColor(fg) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(revoke)
            }
            listBox.addView(row)
        }
    }

    private fun showErr(msg: String) { status.text = msg; status.visibility = View.VISIBLE }
    private fun clearErr() { status.visibility = View.GONE }

    private fun grant(pkg: String) {
        Thread {
            val (code, out) = runAsRoot("appops set ${q(pkg)} android:mock_location allow")
            runOnUiThread {
                if (code == 0) {
                    clearErr()
                    Torch.mockLocationApps = Torch.mockLocationApps + pkg
                    refreshList()
                } else {
                    showErr(out.ifBlank { "הפעולה נכשלה (קוד $code). ודא שיש הרשאת root." })
                }
            }
        }.start()
    }

    private fun revoke(pkg: String) {
        Thread {
            val (code, out) = runAsRoot("appops set ${q(pkg)} android:mock_location deny")
            runOnUiThread {
                if (code == 0) {
                    clearErr()
                    Torch.mockLocationApps = Torch.mockLocationApps - pkg
                    refreshList()
                } else {
                    showErr(out.ifBlank { "הביטול נכשל (קוד $code)." })
                }
            }
        }.start()
    }

    // בחירת אפליקציה מהרשימה, עם שם ואייקון — זהה בעיצובו לבוחר שבכפתור המצלמה
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
                val r = (cv as? LinearLayout) ?: LinearLayout(this@MockLocationActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(9), dp(6), dp(9))
                    addView(ImageView(this@MockLocationActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                    addView(TextView(this@MockLocationActivity).apply { textSize = 16f; setTextColor(fg) }, LinearLayout.LayoutParams(0, -2, 1f))
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
                addView(TextView(this@MockLocationActivity).apply {
                    text = "בחירת אפליקציה"; textSize = 20f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(10))
                })
                addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
            }
        }.show()
    }
}
