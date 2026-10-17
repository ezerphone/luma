package app.luma.torch

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class HideIconActivity : BaseActivity() {
    companion object { const val BACKUP_PATH = "/sdcard/Ezrim_restore.sh" }
    private data class Row(val pkg: String, val comp: String?, val label: String, var hidden: Boolean)
    private val all = mutableListOf<Row>()
    private val shown = mutableListOf<Row>()
    private val icons = HashMap<String, Drawable>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        loadApps()

        // סריקה אמיתית של המערכת, לא תלויה בזיכרון של עזרים — מתאים גם אחרי הסרה והתקנה מחדש
        val scanBtn = tv("סריקת המכשיר ושחזור (אחרי התקנה מחדש של עזרים)", 13f) { scanAndRestore() }
        style(scanBtn, false)
        scanBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

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
            addView(scanBtn)
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        removeOldBackupFile()
    }

    // טוען אפליקציות עם אייקון בלאנצ'ר, ומוסיף גם אפליקציות שכבר הוסתרו קודם (ולכן לא מופיעות יותר בחיפוש)
    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = HashMap<String, String>()
        pm.queryIntentActivities(intent, 0).forEach {
            val pkg = it.activityInfo.packageName
            if (pkg == packageName) return@forEach
            val comp = ComponentName(pkg, it.activityInfo.name).flattenToString()
            found[pkg] = comp
            Torch.setLauncherComp(pkg, comp)
        }
        val hidden = Torch.hiddenApps
        val pkgs = (found.keys + hidden).toSortedSet()
        pkgs.forEach { pkg ->
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
            // רק אפליקציות מערכת — הסתרת אפליקציות משתמש לא עובדת על המכשיר הזה (האייקון לא ממש נעלם)
            if (info == null || (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) return@forEach
            val comp = found[pkg] ?: Torch.getLauncherComp(pkg)
            val label = info.loadLabel(pm).toString()
            all += Row(pkg, comp, label, pkg in hidden)
        }
        all.sortBy { it.label.lowercase() }
        shown.clear(); shown += all
    }

    private fun filter(q: String) {
        shown.clear()
        shown += if (q.isBlank()) all else all.filter { it.label.contains(q, true) }
        adapter.notifyDataSetChanged()
    }

    // מוחק קובץ גיבוי שגרסה קודמת של עזרים יצרה באחסון המשותף (כבר לא בשימוש, ולא נשאר בלי צורך)
    private fun removeOldBackupFile() { Thread { runAsRoot("rm -f $BACKUP_PATH") }.start() }

    // סורק את כל המערכת (לא את הזיכרון של עזרים) ומוצא רכיבי לאנצ'ר שמושבתים כרגע, ללא תלות
    // במה שהאפליקציה "זוכרת" — חשוב אחרי שעזרים הוסרה והותקנה מחדש
    private fun scanAndRestore() {
        Toast.makeText(this, "סורק את המכשיר…", Toast.LENGTH_SHORT).show()
        Thread {
            val (code, out) = runAsRoot("dumpsys package")
            if (code != 0) {
                runOnUiThread { Toast.makeText(this, "הסריקה נכשלה. ודא שיש הרשאת root.", Toast.LENGTH_LONG).show() }
                return@Thread
            }
            val found = LinkedHashMap<String, MutableList<String>>() // pkg -> component class names
            var curPkg: String? = null
            var collecting = false
            var pkgCount = 0
            val pkgRe = Regex("""^Package \[([^\]]+)\]""")
            out.lineSequence().forEach { raw ->
                val t = raw.trim() // דילוג על ההזחה שבה דאמפסיס כותב כל שורה
                val m = pkgRe.find(t)
                if (m != null) { curPkg = m.groupValues[1]; collecting = false; pkgCount++; return@forEach }
                if (t == "disabledComponents:") { collecting = true; return@forEach }
                if (collecting) {
                    if (t.isEmpty() || t.endsWith(":")) { collecting = false }
                    else if (curPkg != null && curPkg != packageName) {
                        found.getOrPut(curPkg!!) { mutableListOf() }.add(t)
                    }
                }
            }
            val pairs = found.flatMap { (pkg, comps) -> comps.map { pkg to "$pkg/$it" } }
            runOnUiThread {
                if (pairs.isEmpty()) {
                    Toast.makeText(this, "לא נמצאו אייקונים מוסתרים (נסרקו $pkgCount חבילות)", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val names = pairs.map { (pkg, _) -> try { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() } catch (_: Exception) { pkg } }
                val msg = "נמצאו ${pairs.size} אייקונים מוסתרים במכשיר:\n" + names.distinct().joinToString("\n") + "\n\nלשחזר את כולם?"
                dlg().setTitle("סריקת המכשיר").setMessage(msg)
                    .setPositiveButton("שחזור הכל") { _, _ -> applyScan(pairs) }
                    .setNegativeButton("ביטול", null).show()
            }
        }.start()
    }

    private fun applyScan(pairs: List<Pair<String, String>>) {
        Toast.makeText(this, "משחזר…", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = 0
            pairs.forEach { (_, comp) -> val (code, _) = runAsRoot("pm enable ${q(comp)}"); if (code == 0) ok++ }
            runOnUiThread {
                Torch.hiddenApps = emptySet()
                all.forEach { it.hidden = false }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "שוחזרו $ok אייקונים", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // פותח אפליקציה מוסתרת מבלי שהרכיב שלה יוצג כ"מופעל" בשום שלב, אם אפשר:
    // ניסיון ראשון מפעיל דרך root ישירות, בלי לגעת כלל במצב ההפעלה של הרכיב.
    // רק אם זה נכשל (כי המערכת חוסמת הפעלה של רכיב מושבת גם ל-root), יש גיבוי שמפעיל-פותח-ומחזיר להסתרה
    // ברצף מיידי, בלי המתנה מכוונת, כדי לצמצם את החלון שבו הרכיב נחשב "מופעל" ככל האפשר.
    private fun openHidden(row: Row) {
        val comp = row.comp ?: return
        Thread {
            val (directCode, directOut) = runAsRoot("am start -n ${q(comp)}")
            val directOk = directCode == 0 && !directOut.contains("Error", true) && !directOut.contains("Exception", true)
            if (!directOk) {
                runAsRoot("pm enable ${q(comp)}")
                runAsRoot("am start -n ${q(comp)}")
                runAsRoot("pm disable ${q(comp)}")
            }
        }.start()
    }

    private fun toggle(row: Row, hide: Boolean) {
        val comp = row.comp
        if (comp == null) { Toast.makeText(this, "לא נמצא רכיב להסתרה עבור אפליקציה זו", Toast.LENGTH_SHORT).show(); adapter.notifyDataSetChanged(); return }
        Thread {
            val cmd = if (hide) "pm disable ${q(comp)}" else "pm enable ${q(comp)}"
            val (code, out) = runAsRoot(cmd)
            val failed = code != 0 || out.contains("Exception", true) || out.contains("Error", true) || out.contains("Unknown component", true)
            runOnUiThread {
                if (!failed) {
                    row.hidden = hide
                    val set = Torch.hiddenApps.toMutableSet()
                    if (hide) set += row.pkg else set -= row.pkg
                    Torch.hiddenApps = set
                } else {
                    row.hidden = !hide // מחזיר את המתג למצב הקודם
                    Toast.makeText(this, out.ifBlank { "לא נמצאה גישת root או שהיא נדחתה (קוד $code)" }, Toast.LENGTH_LONG).show()
                }
                adapter.notifyDataSetChanged()
            }
        }.start()
    }
}
