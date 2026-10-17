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

class MainActivity : BaseActivity() {
    // בדיקה אם במכשיר קיים כפתור מצלמה פיזי
    private fun hasCameraButton(): Boolean = InputDevice.getDeviceIds().any { id ->
        val d = InputDevice.getDevice(id) ?: return@any false
        !d.isVirtual && d.hasKeys(KeyEvent.KEYCODE_CAMERA)[0]
    }

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

    private fun big(res: Int, t: String, s: String, go: Class<*>, check: (() -> Boolean)? = null) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = shape(card, 20, line); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        if (check != null && !check()) alpha = 0.6f
        addView(ImageView(this@MainActivity).apply {
            setImageDrawable(icon(res, acc)); setPadding(dp(10), dp(10), dp(10), dp(10)); background = shape(accSoft, 14)
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = t; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@MainActivity).apply { text = s; textSize = 12f; setTextColor(dim); setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageView(this@MainActivity).apply { setImageDrawable(icon(R.drawable.ic_chevron, dim)) }, LinearLayout.LayoutParams(dp(18), dp(18)))
        setOnClickListener {
            if (check != null && !check()) Toast.makeText(this@MainActivity, "לא נמצא כפתור מצלמה במכשיר", Toast.LENGTH_LONG).show()
            else startActivity(Intent(this@MainActivity, go))
        }
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        RootShell.warmUp() // בקשת הרשאת root פעם אחת, בפתיחת האפליקציה
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        if (Torch.shake) startForegroundService(Intent(this, FlashService::class.java))
        val btCard = big(R.drawable.ic_bluetooth, "כתובת בלוטוס", "שינוי כתובת MAC (דורש root)", BtAddressActivity::class.java)
        val rotCard = big(R.drawable.ic_rotate, "כפתור סיבוב מסך", "הצגה או הסתרה של הכפתור בסרגל הניווט (דורש root)", RotationActivity::class.java)
        val hideCard = big(R.drawable.ic_hide, "הסתרת אייקונים", "הסתרת אייקון של אפליקציה ממסך הבית (דורש root)", HideIconActivity::class.java)
        val custCard = big(R.drawable.ic_tune, "התאמה אישית", "מחיקת רדיו/מציג HTML והחזרת המצלמה המקורית (דורש root)", CustomizationActivity::class.java)
        pageWithFooter(
            footer(),
            hero(),
            big(R.drawable.ic_flash, "פנס", "הדלקה, הבהוב, כיבוי אוטומטי וניעור", FlashActivity::class.java),
            big(R.drawable.ic_camera, "כפתור המצלמה", "מה יקרה בלחיצה רגילה, כפולה או ארוכה", CameraButtonActivity::class.java) { hasCameraButton() },
            btCard, rotCard, hideCard, custCard
        )
        checkRoot(listOf(btCard, rotCard, hideCard, custCard))
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
}
