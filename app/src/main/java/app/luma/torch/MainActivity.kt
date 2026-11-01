package app.luma.torch

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.*
import app.luma.torch.dictation.DictationActivity
import app.luma.torch.dictation.ModelLocator

class MainActivity : BaseActivity() {
    private fun hero() = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "עזרים"; textSize = 34f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply { text = "הגדרות נוספות לנגן"; textSize = 14f; setTextColor(dim) })
        }, FrameLayout.LayoutParams(-1, -2))
        // גלגל שיניים להגדרות
        addView(ImageView(this@MainActivity).apply {
            setImageDrawable(icon(R.drawable.ic_settings, dim)); setPadding(dp(9), dp(9), dp(9), dp(9))
            background = shape(card, 14, line)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END))
    }

    private fun footer(): TextView {
        val email = "ezerphone0@gmail.com"
        val phone = "043136429"
        val t = "גרסה כשרה מאת $email\nלהכשרת נגנים תיקון בריקים צריבת רוט ועוד $phone"
        val sp = SpannableString(t)
        fun link(part: String, url: String) {
            val i = t.indexOf(part)
            if (i >= 0) sp.setSpan(URLSpan(url), i, i + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        link(email, "mailto:$email"); link(phone, "tel:$phone")
        return TextView(this).apply {
            text = sp; textSize = 12f; setTextColor(dim); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f); movementMethod = LinkMovementMethod.getInstance(); setLinkTextColor(acc)
            setPadding(dp(24), dp(12), dp(24), dp(22))
        }
    }

    // אם האפליקציה קרסה בפעם הקודמת, מציגים את פירוט הקריסה (אפשר לסמן ולהעתיק)
    override fun onResume() {
        super.onResume()
        val f = java.io.File(filesDir, "last_crash.txt")
        if (!f.exists()) return
        val trace = try { f.readText() } catch (_: Exception) { "" }
        f.delete()
        if (trace.isBlank()) return
        val body = TextView(this).apply {
            text = trace.take(3000); textSize = 11f; setTextColor(fg); typeface = Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        dlg().setTitle("האפליקציה קרסה בפעם הקודמת").setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("סגור", null).show()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        RootShell.warmUp() // בקשת הרשאת root פעם אחת, בפתיחת האפליקציה
        if (Torch.shake) startForegroundService(Intent(this, FlashService::class.java))
        try { app.luma.torch.dictation.VoiceIme.ensureDefault(this) } catch (_: Exception) {}
        try { app.luma.torch.assistant.Assistant.ensureDefault(this) } catch (_: Exception) {}
        try { app.luma.torch.assistant.Hotword.sync(this) } catch (_: Exception) {}
        val btCard = big(R.drawable.ic_bluetooth, "כתובת בלוטוס", "שינוי כתובת MAC (דורש root)", BtAddressActivity::class.java)
        val sysCard = big(R.drawable.ic_statusbar, "הגדרות מערכת", "כפתור סיבוב, עיצוב הסטטוס בר וקיצורים", SystemSettingsActivity::class.java)
        val hideCard = big(R.drawable.ic_hide, "הסתרת אייקונים", "הסתרת אייקון של אפליקציה ממסך הבית", HideIconActivity::class.java)
        val custCard = big(R.drawable.ic_tune, "התאמה אישית", "מחיקת רדיו/מציג HTML והחזרת המצלמה המקורית (דורש root)", CustomizationActivity::class.java)
        val mockCard = big(R.drawable.ic_location, "מיקום מדומה", "מתן הרשאת מיקום מזויף לאפליקציה", MockLocationActivity::class.java)
        pageWithFooter(
            footer(),
            hero(),
            big(R.drawable.ic_flash, "פנס", "הדלקה, הבהוב, כיבוי אוטומטי וניעור", FlashActivity::class.java),
            big(R.drawable.ic_shortcuts, "קיצורים", "כפתור המצלמה ומקש הבית", ShortcutsActivity::class.java),
            big(R.drawable.ic_assist, "עזר", "העוזר הקולי: אפליקציות, תזכורות, זמני היום ועוד", app.luma.torch.assistant.ChatActivity::class.java,
                "מודל זיהוי הדיבור לא נמצא בקושחה") { ModelLocator.isInstalled() },
            big(R.drawable.ic_mic, "הקלדה קולית", "הכתבה בעברית בלי אינטרנט, בכל שדה טקסט", DictationActivity::class.java,
                "מודל זיהוי הדיבור לא נמצא בקושחה") { ModelLocator.isInstalled() },
            sysCard, btCard, hideCard, custCard, mockCard
        )
        // כתובת בלוטוס והתאמה אישית דורשות root; הסתרת אייקונים דורשת הרשאת מערכת (priv-app),
        // ונבדקת ישירות ללא צורך ב-root. בהגדרות מערכת כל פריט מנוטרל בנפרד אם חסרה לו הרשאה
        checkRoot(listOf(btCard, custCard))
        checkPerm(hideCard, "android.permission.CHANGE_COMPONENT_ENABLED_STATE")
    }

    // מאפיר ומנטרל כרטיסים שדורשים root, כשאין במכשיר הרשאת root
    private fun checkRoot(rootCards: List<View>) {
        Thread {
            val (code, out) = runAsRoot("id")
            val ok = code == 0 && out.contains("uid=0")
            runOnUiThread {
                rootCards.forEach { it.alpha = if (ok) 1f else 0.4f; it.isEnabled = ok }
            }
        }.start()
    }

    // מאפיר ומנטרל כרטיס שדורש הרשאת מערכת privileged, אם היא לא הוענקה לעזרים
    private fun checkPerm(card: View, permission: String) {
        val ok = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        card.alpha = if (ok) 1f else 0.4f; card.isEnabled = ok
    }
}
