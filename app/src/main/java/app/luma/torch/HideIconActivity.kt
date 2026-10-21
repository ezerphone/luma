package app.luma.torch

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

// דורש שעזרים תותקן כאפליקציית מערכת (priv-app) עם הרשאת
// android.permission.CHANGE_COMPONENT_ENABLED_STATE מאושרת בקובץ ההרשאות של הקושחה.
// אין כאן שום נפילה ל-root: בלי ההרשאה הזו הפעולות פשוט ייכשלו עם הודעה ברורה.
// המצב בכל שורה נקרא ישירות מהמערכת בכל פתיחה, ולכן תמיד משקף את המציאות —
// גם אם עזרים הוסרה והותקנה מחדש בינתיים.
class HideIconActivity : BaseActivity() {
    private data class Row(val pkg: String, val comp: ComponentName, val label: String, var hidden: Boolean)
    private val all = mutableListOf<Row>()
    private val shown = mutableListOf<Row>()
    private val icons = HashMap<String, Drawable>()
    private lateinit var adapter: BaseAdapter
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        loadApps()

        val restoreAll = tv("שחזור כל האייקונים") { restoreAll() }
        style(restoreAll, false)
        restoreAll.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }

        status = TextView(this).apply {
            textSize = 12f; setTextColor(if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt())
            visibility = View.GONE; setPadding(0, 0, 0, dp(10))
        }

        val search = EditText(this).apply {
            hint = "חיפוש אפליקציה"; setHintTextColor(dim); setTextColor(fg)
            background = shape(chip, 16); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
        })

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val row = shown[p]
                val r = (cv as? LinearLayout) ?: LinearLayout(this@HideIconActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(11), dp(4), dp(11))
                    addView(ImageView(this@HideIconActivity), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
                    addView(TextView(this@HideIconActivity).apply { textSize = 15f; setTextColor(fg) }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(Switch(this@HideIconActivity))
                }
                (r.getChildAt(0) as ImageView).setImageDrawable(icons.getOrPut(row.pkg) {
                    try { packageManager.getApplicationIcon(row.pkg) } catch (_: Exception) { icon(R.drawable.ic_camera, dim) }
                })
                (r.getChildAt(1) as TextView).text = row.label
                val sw = r.getChildAt(2) as Switch
                tint(sw)
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = !row.hidden // המתג דולק = האייקון מוצג
                sw.setOnCheckedChangeListener { _, checked -> toggle(row, hide = !checked) }
                // לחיצה על השורה (לא על המתג) של אפליקציה מוסתרת פותחת אותה, בלי לבטל את ההסתרה
                r.setOnClickListener { if (row.hidden) openHidden(row) }
                return r
            }
        }
        val list = ListView(this).apply {
            adapter = this@HideIconActivity.adapter; divider = shape(line, 0); dividerHeight = 1
            clipToPadding = false; setPadding(0, 0, 0, dp(24))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(20), dp(48), dp(20), 0)
            addView(header("הסתרת אייקונים", null, "הסתרת אייקון של אפליקציית מערכת ממסך הבית"))
            addView(restoreAll)
            addView(status)
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    private fun isHidden(cn: ComponentName): Boolean = when (packageManager.getComponentEnabledSetting(cn)) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
        else -> false
    }

    // טוען את כל אפליקציות המערכת עם רכיב לאנצ'ר, כולל כאלה שמושבתות כרגע — המצב נקרא
    // ישירות מהמערכת (MATCH_DISABLED_COMPONENTS), לא מזיכרון שמור, ולכן תמיד נכון בפועל
    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS).forEach {
            val pkg = it.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
            // רק אפליקציות מערכת — הסתרת אפליקציות משתמש לא עובדת על המכשיר הזה (האייקון לא ממש נעלם)
            if (info == null || (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0) return@forEach
            val cn = ComponentName(pkg, it.activityInfo.name)
            all += Row(pkg, cn, info.loadLabel(pm).toString(), isHidden(cn))
        }
        all.sortBy { it.label.lowercase() }
        shown.clear(); shown += all
    }

    private fun filter(q: String) {
        shown.clear()
        shown += if (q.isBlank()) all else all.filter { it.label.contains(q, true) }
        adapter.notifyDataSetChanged()
    }

    private fun showErr(msg: String) { status.text = msg; status.visibility = View.VISIBLE }

    private fun setComponent(cn: ComponentName, hide: Boolean): Boolean = try {
        packageManager.setComponentEnabledSetting(
            cn,
            if (hide) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        true
    } catch (e: SecurityException) {
        showErr("אין לעזרים הרשאת CHANGE_COMPONENT_ENABLED_STATE. יש להתקין אותה כאפליקציית מערכת עם קובץ ההרשאות המתאים.")
        false
    }

    // מחזיר אייקון לכל האפליקציות שמוסתרות כרגע ברשימה הנוכחית
    private fun restoreAll() {
        val toRestore = all.filter { it.hidden }
        if (toRestore.isEmpty()) { Toast.makeText(this, "אין אייקונים מוסתרים", Toast.LENGTH_SHORT).show(); return }
        var ok = 0
        toRestore.forEach { if (setComponent(it.comp, hide = false)) { it.hidden = false; ok++ } }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "שוחזרו $ok אייקונים", Toast.LENGTH_LONG).show()
    }

    // פותח אפליקציה מוסתרת: מפעיל את הרכיב זמנית, פותח אותה, ומיד מחזיר אותו להסתרה —
    // כך שברגע שחוזרים למסך הבית האייקון עדיין נראה מוסתר כרגיל
    private fun openHidden(row: Row) {
        if (!setComponent(row.comp, hide = false)) return
        try {
            startActivity(Intent().setComponent(row.comp).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "לא ניתן לפתוח את האפליקציה", Toast.LENGTH_SHORT).show()
        }
        setComponent(row.comp, hide = true)
    }

    private fun toggle(row: Row, hide: Boolean) {
        if (setComponent(row.comp, hide)) {
            row.hidden = hide
        } else {
            adapter.notifyDataSetChanged() // מחזיר את המתג למצב האמיתי
        }
    }
}
