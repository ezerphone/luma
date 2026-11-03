package app.luma.torch.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import app.luma.torch.BaseActivity
import app.luma.torch.CameraButtonService
import app.luma.torch.R
import app.luma.torch.dictation.VoiceIme

// רכיבי המסכים של הגדרות עזר, בעיצוב של שאר עזרים

// כרטיסייה: אייקון, כותרת, שורת מצב, וחץ. מחזירה את הכרטיסייה ואת שורת המצב (לעדכון)
fun BaseActivity.navCard(res: Int, title: String, chevron: Boolean = true, click: () -> Unit): Pair<View, TextView> {
    val sub = TextView(this).apply { textSize = 12f; setTextColor(dim); setPadding(0, dp(2), 0, 0) }
    val v = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = shape(card, 20, line); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        addView(ImageView(this@navCard).apply {
            setImageDrawable(icon(res, acc)); setPadding(dp(10), dp(10), dp(10), dp(10)); background = shape(accSoft, 14)
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@navCard).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@navCard).apply { text = title; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD })
            addView(sub)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (chevron) addView(ImageView(this@navCard).apply { setImageDrawable(icon(R.drawable.ic_chevron, dim)) }, LinearLayout.LayoutParams(dp(18), dp(18)))
        setOnClickListener { click() }
    }
    return v to sub
}

// כרטיס לבן שמכיל כמה שורות, עם קו מפריד ביניהן
fun BaseActivity.plainCard(vararg rows: View) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; background = shape(card, 20, line); setPadding(dp(16), dp(6), dp(16), dp(6))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    rows.forEachIndexed { i, r ->
        if (i > 0 && r is Switch && rows[i - 1] is Switch) addView(View(this@plainCard).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
        addView(r)
    }
}

fun BaseActivity.settingSwitch(label: String) = Switch(this).apply {
    text = label; setTextColor(fg); tint(this); textSize = 15f
    setPadding(0, dp(12), 0, dp(12))
}

// מתג "אפור": לא לחיץ, והטקסט בהיר
fun BaseActivity.grey(s: Switch, off: Boolean) {
    s.isEnabled = !off; s.alpha = if (off) 0.45f else 1f
}

// ---------- הרשאות ----------
object Perms {
    fun runtime(): List<Pair<String, String>> = listOfNotNull(
        Manifest.permission.RECORD_AUDIO to "מיקרופון",
        (if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) to "קבצי מוזיקה",
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS to "התראות" else null,
        if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT to "בלוטוס" else null
    )

    private fun secureHas(c: Context, key: String, cn: ComponentName): Boolean {
        val s = try { Settings.Secure.getString(c.contentResolver, key) } catch (_: Exception) { null } ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it) == cn }
    }

    // כל ההרשאות של עזר, ולכל אחת — האם מאושרת
    fun status(c: Context): List<Pair<String, Boolean>> =
        runtime().map { (p, name) -> name to (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) } + listOf(
            "שירות הנגישות" to secureHas(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentName(c, CameraButtonService::class.java)),
            "גישה להתראות" to secureHas(c, "enabled_notification_listeners", ComponentName(c, MediaListener::class.java)),
            "הצגה מעל אפליקציות" to Settings.canDrawOverlays(c),
            "עוזר המערכת" to Assistant.isDefault(c),
            "מקלדת קולית" to VoiceIme.isEnabled(c)
        )

    fun missing(c: Context) = status(c).count { !it.second }

    // מפעיל לבד את כל מה שאפשר בלי לשאול (עזרים רשאי לשנות הגדרות מערכת)
    fun fixSpecial(c: Context) {
        try {
            val a11y = ComponentName(c, CameraButtonService::class.java).flattenToString()
            if (!secureHas(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentName(c, CameraButtonService::class.java))) {
                Assistant.appendSecure(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a11y)
                Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        } catch (_: Exception) {}
        Assistant.appendSecure(c, "enabled_notification_listeners", ComponentName(c, MediaListener::class.java).flattenToString())
        allowOverlay(c)
        if (!Assistant.isDefault(c)) Assistant.makeDefault(c)
        try { VoiceIme.enableNow(c) } catch (_: Exception) {}
    }
}
