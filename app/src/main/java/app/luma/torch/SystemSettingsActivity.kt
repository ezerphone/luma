package app.luma.torch

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

// הגדרות מערכת: כפתור הסיבוב בסרגל הניווט, עיצוב הסטטוס בר וקיצורים לסרגל ההגדרות המהירות.
// כפתור הסיבוב דורש WRITE_SECURE_SETTINGS (עזרים כ-priv-app); בלי ההרשאה המתג פשוט מנוטרל.
class SystemSettingsActivity : BaseActivity() {
    private val KEY = "qsrow_mode"
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private lateinit var rotSw: Switch
    private lateinit var rotStatus: TextView
    private var painting = false

    private fun get(key: String) = try { Settings.System.getInt(contentResolver, key, 0) } catch (_: Exception) { 0 }
    private fun mode() = get(KEY)

    private fun put(key: String, v: Int) {
        val ok = try { Settings.System.putInt(contentResolver, key, v) } catch (_: Exception) { false }
        if (!ok) Thread { runAsRoot("settings put system $key $v") }.start()
    }
    private fun setMode(v: Int) = put(KEY, v)

    // גרסה צפופה של section/group — כדי שכל המסך ייכנס בלי גלילה
    private fun box(title: String, vararg v: View) = section(title, *v).apply {
        setPadding(dp(14), dp(10), dp(14), dp(12))
        (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        (getChildAt(0) as TextView).setPadding(dp(2), 0, 0, dp(6))
    }
    private fun small(row: LinearLayout) = row.apply {
        for (i in 0 until childCount) (getChildAt(i) as TextView).apply { textSize = 13f; setPadding(dp(2), dp(9), dp(2), dp(9)) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // כפתור סיבוב אוטומטי
        rotSw = Switch(this).apply { text = "כפתור סיבוב אוטומטי בסרגל הניווט"; setTextColor(fg) }
        tint(rotSw); rotSw.textSize = 14f; rotSw.minHeight = 0; rotSw.setPadding(0, dp(2), 0, dp(2))
        rotStatus = TextView(this).apply { textSize = 13f; setTextColor(warnC); setPadding(0, dp(10), 0, 0); visibility = View.GONE }
        rotSw.setOnCheckedChangeListener { _, c -> if (!painting) changeRotation(c) }

        // עיצוב הסטטוס בר
        val design = small(group(listOf("ברירת מחדל", "שורת עיגולים"), { if (mode() == 0) 0 else 1 }) { setMode(if (it == 0) 0 else 1) })

        // התאריך במסך הנעילה (lock_date_mode: 0 לועזי, 1 עברי, 2 שניהם)
        val date = small(group(listOf("לועזי", "עברי", "לועזי + עברי"), { get("lock_date_mode").coerceIn(0, 2) }) { put("lock_date_mode", it) })

        // קיצורים לסרגל ההגדרות המהירות
        val addFlash = tv("+ פנס") { addTile(FlashTile::class.java, "פנס", R.drawable.ic_flash) }
        style(addFlash, false)
        val addEject = tv("+ הוצא כרטיס") { addTile(EjectTile::class.java, "הוצא", R.drawable.ic_eject) }
        style(addEject, false)
        val tiles = small(row(listOf(addFlash, addEject)))

        page(
            header("הגדרות מערכת").apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10) },
            box("סיבוב מסך", rotSw, rotStatus),
            box("עיצוב הסטטוס בר", design),
            box("תאריך במסך הנעילה", date),
            box("קיצורים בסרגל", tiles)
        )
        // פחות ריווח עליון/תחתון מהרגיל
        try { ((findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0)).setPadding(dp(20), dp(30), dp(20), dp(12)) } catch (_: Exception) {}
        loadRotation()
    }

    private fun setChecked(v: Boolean) { painting = true; rotSw.isChecked = v; painting = false }

    private fun noPermission() {
        rotSw.isEnabled = false; rotSw.alpha = 0.4f
        rotStatus.text = "אין לעזרים הרשאת WRITE_SECURE_SETTINGS (נדרשת התקנה כאפליקציית מערכת)."
        rotStatus.visibility = View.VISIBLE
    }

    // כשההגדרה לא קיימת, ברירת המחדל של המערכת היא "מוצג"
    private fun loadRotation() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            noPermission()
        }
        val v = try { Settings.Secure.getString(contentResolver, "show_rotation_suggestions") } catch (_: Exception) { null }
        setChecked(v != "0")
    }

    private fun changeRotation(on: Boolean) {
        try {
            Settings.Secure.putString(contentResolver, "show_rotation_suggestions", if (on) "1" else "0")
        } catch (e: SecurityException) {
            setChecked(!on); noPermission()
        }
    }

    private fun addTile(cls: Class<*>, label: String, res: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, cls), label, Icon.createWithResource(this, res), mainExecutor) { }
        } else {
            dlg().setMessage("החלק מטה מראש המסך פעמיים, לחץ על סמל העריכה (עיפרון) וגרור את האריח \"$label\" לסרגל.").setPositiveButton("הבנתי", null).show()
        }
    }
}
