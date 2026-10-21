package app.luma.torch

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Random

class BtAddressActivity : BaseActivity() {
    private val macRegex = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private lateinit var et: EditText
    private lateinit var status: TextView
    private lateinit var applyBtn: TextView
    private var working = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        et = EditText(this).apply {
            hint = "AA:BB:CC:DD:EE:FF"; setHintTextColor(dim); setTextColor(fg)
            typeface = Typeface.MONOSPACE; textSize = 20f; gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(17))
            background = shape(chip, 16); setPadding(dp(14), dp(16), dp(14), dp(16))
            setText(Torch.btMac)
            // הוספת נקודתיים אוטומטית והגבלה לתווי הקסדצימל
            var fmt = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (fmt || s == null) return
                    fmt = true
                    val raw = s.toString().uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(12)
                    val out = raw.chunked(2).joinToString(":")
                    if (out != s.toString()) s.replace(0, s.length, out)
                    fmt = false
                }
            })
        }
        val random = tv("כתובת אקראית") {
            val bytes = ByteArray(6).also { Random().nextBytes(it) }
            bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte() // unicast + מקומית
            et.setText(bytes.joinToString(":") { "%02X".format(it) })
        }
        style(random, false)
        random.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }

        applyBtn = tv("החל שינוי", 16f) { applyMac() }
        style(applyBtn, true)
        applyBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

        status = TextView(this).apply { textSize = 14f; setTextColor(dim); gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), dp(16)); visibility = View.GONE }

        val info = TextView(this).apply {
            text = "השינוי דורש הרשאת root, ומתבצע דרך הגדרות המערכת. הבלוטוס יכבה וידלק מחדש."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f)
        }
        page(
            header("כתובת בלוטוס", null, "שינוי כתובת ה-MAC של הבלוטוס"),
            section("כתובת חדשה", et, random),
            applyBtn, status,
            section("מידע", info)
        )
    }

    private fun show(msg: String, color: Int) {
        status.text = msg; status.setTextColor(color); status.visibility = View.VISIBLE
    }

    private fun applyMac() {
        if (working) return
        val mac = et.text.toString().trim().uppercase()
        if (!macRegex.matches(mac)) { show("הכתובת לא תקינה. הפורמט הנדרש: AA:BB:CC:DD:EE:FF", warnC); return }
        if ((mac.substring(0, 2).toInt(16) and 1) == 1) {
            show("הבייט הראשון חייב להיות זוגי (למשל 02, 04, 0A), אחרת הכתובת לא תקפה.", warnC); return
        }
        working = true; applyBtn.alpha = 0.5f
        show("מבצע שינוי…", dim)
        Torch.btMac = mac
        // המאק עבר אימות מלא (הקסדצימל ונקודתיים בלבד), לכן בטוח להכניס אותו לפקודה
        val cmd = "setprop persist.service.bdroid.bdaddr \"$mac\"; " +
            "(resetprop ro.boot.btmacaddr \"$mac\" 2>/dev/null || setprop ro.boot.btmacaddr \"$mac\"); " +
            "svc bluetooth disable; sleep 2; svc bluetooth enable"
        Thread {
            val (code, out) = runAsRoot(cmd)
            runOnUiThread {
                working = false; applyBtn.alpha = 1f
                when {
                    code == 0 -> show("הכתובת הוגדרה ל-$mac והבלוטוס הופעל מחדש. אם הכתובת לא השתנתה, הפעל מחדש את המכשיר.", acc)
                    out.contains("not found", true) || out.contains("denied", true) || code == -1 ->
                        show("לא נמצאה גישת root או שהיא נדחתה. אשר הרשאת root לאפליקציה ונסה שוב.", warnC)
                    else -> show("הפקודה נכשלה (קוד $code). ייתכן שהמכשיר לא תומך בשינוי כזה.", warnC)
                }
            }
        }.start()
    }
}
