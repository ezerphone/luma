package app.luma.torch

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

// מסך בחירת הפעולה ללחיצה אחת: שתי לשוניות, פקודות מצד אחד ורשימת אפליקציות מהצד השני.
class CameraActionActivity : BaseActivity() {
    private class Cmd(val code: Int, val label: String, val icon: Int)
    private class AppRow(val label: String, val pkg: String, val info: ResolveInfo)

    private val cmds = listOf(
        Cmd(0, "אף אחד", R.drawable.ic_none),
        Cmd(1, "פנס", R.drawable.ic_flash),
        Cmd(5, "חזרה למסך הבית", R.drawable.ic_home),
        Cmd(4, "חזרה אחורה", R.drawable.ic_back),
        Cmd(3, "צילום מסך", R.drawable.ic_screenshot),
        Cmd(7, "התחלת הקלטת מסך", R.drawable.ic_record),
        Cmd(6, "פתיחת עוזר אישי", R.drawable.ic_assist)
    )
    private var apps = listOf<AppRow>()
    private var shown = listOf<Any>()
    private var tab = 0
    private var slot = 0
    private val icons = HashMap<String, Drawable>()
    private lateinit var adapter: BaseAdapter
    private lateinit var tabCmd: TextView
    private lateinit var tabApp: TextView
    private lateinit var search: EditText

    private var act: Int
        get() = when (slot) { 0 -> Torch.shortAct; 1 -> Torch.longAct; else -> Torch.dblAct }
        set(v) { when (slot) { 0 -> Torch.shortAct = v; 1 -> Torch.longAct = v; else -> Torch.dblAct = v } }
    private var pkg: String
        get() = when (slot) { 0 -> Torch.shortPkg; 1 -> Torch.longPkg; else -> Torch.dblPkg }
        set(v) { when (slot) { 0 -> Torch.shortPkg = v; 1 -> Torch.longPkg = v; else -> Torch.dblPkg = v } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        slot = intent.getIntExtra("slot", 0).coerceIn(0, 2)
        val title = listOf("לחיצה רגילה", "לחיצה ארוכה", "לחיצה כפולה")[slot]
        apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { AppRow(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }

        tabCmd = tv("פקודות", 15f) { select(0) }
        tabApp = tv("אפליקציות", 15f) { select(1) }
        val tabs = row(listOf(tabCmd, tabApp)).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) } }

        search = EditText(this).apply {
            hint = "חיפוש אפליקציה"; setHintTextColor(dim); setTextColor(fg)
            background = shape(chip, 16); setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (tab == 1) fill() }
            })
        }

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val r = (cv as? LinearLayout) ?: LinearLayout(this@CameraActionActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(14), dp(8))
                    addView(ImageView(this@CameraActionActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
                    addView(TextView(this@CameraActionActivity).apply { textSize = 16f; setTextColor(fg) }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(ImageView(this@CameraActionActivity).apply { setImageDrawable(icon(R.drawable.ic_check, acc)) }, LinearLayout.LayoutParams(dp(22), dp(22)))
                }
                val iv = r.getChildAt(0) as ImageView
                val label = r.getChildAt(1) as TextView
                val mark = r.getChildAt(2)
                val item = shown[p]
                val selected: Boolean
                if (item is Cmd) {
                    iv.setImageDrawable(icon(item.icon, acc)); iv.setPadding(dp(10), dp(10), dp(10), dp(10)); iv.background = shape(accSoft, 14)
                    label.text = item.label
                    selected = act == item.code
                } else {
                    item as AppRow
                    iv.setPadding(0, 0, 0, 0); iv.background = null
                    iv.setImageDrawable(icons.getOrPut(item.pkg) { item.info.loadIcon(packageManager) })
                    label.text = item.label
                    selected = act == 2 && pkg == item.pkg
                }
                mark.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                r.background = if (selected) shape(accSoft, 16) else null
                return r
            }
        }
        val list = ListView(this).apply {
            adapter = this@CameraActionActivity.adapter; divider = null; setSelector(ColorDrawable(Color.TRANSPARENT))
            clipToPadding = false; setPadding(0, 0, 0, dp(24))
            setOnItemClickListener { _, _, pos, _ ->
                val item = shown[pos]
                if (item is Cmd) act = item.code
                else if (item is AppRow) { act = 2; pkg = item.pkg }
                this@CameraActionActivity.adapter.notifyDataSetChanged()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(20), dp(48), dp(20), 0)
            addView(header(title, null, "בחר מה הלחיצה תפעיל"))
            addView(tabs)
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        select(if (act == 2) 1 else 0)
    }

    private fun select(t: Int) {
        tab = t
        style(tabCmd, t == 0); style(tabApp, t == 1)
        search.visibility = if (t == 1) View.VISIBLE else View.GONE
        fill()
    }

    private fun fill() {
        shown = if (tab == 0) cmds else {
            val q = search.text.toString()
            if (q.isBlank()) apps else apps.filter { it.label.contains(q, true) }
        }
        adapter.notifyDataSetChanged()
    }
}
