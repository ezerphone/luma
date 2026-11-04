package app.luma.torch

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

// התאמה אישית: הגדרה חד-פעמית של רדיו, מציג HTML וחנות כשרה (בלתי הפיכה), ובחירת מצלמה.
// אחרי ההגדרה נשאר במסך רק החלק של המצלמה. הסימון שההגדרה בוצעה נשמר במחיצת המערכת, כך שהוא שורד איפוס.
class CustomizationActivity : BaseActivity() {
    companion object {
        const val RADIO_DIR = "/system/system_ext/priv-app/FMRadio"
        const val HTML_DIR = "/system/app/HTMLViewer"
        const val STORE_DIR = "/system/priv-app/MyStore"
        const val CAMERA_DIR = "/system_ext/app/Camera"
        const val CAMERA_APK = "$CAMERA_DIR/Camera.apk"
        private val DONE = listOf("/system/etc/ezrim/setup_done", "/system_ext/etc/ezrim_setup_done")
        fun setupDone() = DONE.any { File(it).exists() }

        private fun sha(input: InputStream): String = input.use {
            val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(65536)
            while (true) { val n = it.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        }
        private fun fileSha(path: String) = try { sha(File(path).inputStream()) } catch (_: Exception) { null }
        private fun assetSha(c: Context, name: String) = try { sha(c.assets.open(name)) } catch (_: Exception) { null }

        // איזו מצלמה מותקנת: 0 מקורית, 1 משודרגת, null — אחרת או לא נמצאה (לפי טביעת אצבע של הקובץ)
        fun cameraNow(c: Context): Int? {
            val cur = fileSha(CAMERA_APK) ?: return null
            return when (cur) { assetSha(c, "camera_stock.apk") -> 0; assetSha(c, "camera_upgraded.apk") -> 1; else -> null }
        }
        val CAMERAS = listOf("מקורית", "משודרגת")
        // אחרי החלפת מצלמה: בעלייה הבאה נותנים לה את הרשאות האחסון (בלי זה המקורית נסגרת מיד — ההיתר להרשאות המוגבלות אובד בהחלפה)
        private const val GRANT_KEY = "camera_grant_pending"
        private const val GRANT_CMD = "pm grant com.mediatek.camera android.permission.READ_EXTERNAL_STORAGE 2>/dev/null; " +
            "pm grant com.mediatek.camera android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null; echo G_OK"
        private fun prefs(c: Context) = c.getSharedPreferences("customization", Context.MODE_PRIVATE)
        fun onBoot(c: Context) {
            if (!prefs(c).getBoolean(GRANT_KEY, false)) return
            if (RootShell.run(GRANT_CMD).second.contains("G_OK")) prefs(c).edit().remove(GRANT_KEY).apply()
        }
        private val CAMERA_ASSET = listOf("camera_stock.apk", "camera_upgraded.apk")
    }

    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()

    // המצב הנוכחי במכשיר
    private var radioNow = 0          // 0 אין, 1 כשר, 2 רגיל
    private var htmlNow = false
    private var storeNow = false
    private var camNow: Int? = null
    // הבחירות במסך
    private var radio = 0; private var html = 0; private var store = 0; private var cam = -1

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        page(header("התאמה אישית"), TextView(this).apply { text = "בודק…"; setTextColor(dim); textSize = 14f })
        Thread {
            val done = setupDone()
            if (!done) {
                val radioApk = File(RADIO_DIR).listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                radioNow = when {
                    radioApk == null -> 0
                    fileSha(radioApk.path) == assetSha(this, "radio_kosher.apk") -> 1
                    else -> 2
                }
                htmlNow = File(HTML_DIR).exists(); storeNow = File(STORE_DIR).exists()
            }
            camNow = cameraNow(this)
            runOnUiThread { if (!isFinishing) { if (done) buildCameraOnly() else buildSetup() } }
        }.start()
    }

    // בחירה בין כמה אפשרויות; אפשרות לא זמינה — אפורה ולא לחיצה
    private fun choice(names: List<String>, enabled: List<Boolean>, sel: () -> Int, pick: (Int) -> Unit): LinearLayout {
        val cs = mutableListOf<TextView>()
        fun paint() = cs.forEachIndexed { i, c -> style(c, sel() == i); c.alpha = if (enabled[i]) 1f else 0.35f }
        names.forEachIndexed { i, n -> cs += tv(n, 14f) { if (enabled[i]) { pick(i); paint() } } }
        paint()
        return row(cs)
    }

    private fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(dim); setPadding(dp(2), dp(10), 0, 0) }

    // ---------- לפני ההגדרה ----------
    private fun buildSetup() {
        radio = radioNow; html = if (htmlNow) 0 else -1; store = if (storeNow) 0 else -1
        val radioOk = listOf(true, radioNow != 0, radioNow == 2)
        val radioSec = section("רדיו", choice(listOf("ללא רדיו", "רדיו כשר", "רדיו רגיל"), radioOk, { radio }) { radio = it })
        if (radioNow == 0) radioSec.addView(note("לא קיים במכשיר"))
        val htmlSec = section("מציג HTML", choice(listOf("להשאיר", "למחוק"), listOf(htmlNow, htmlNow), { html }) { html = it })
        if (!htmlNow) htmlSec.addView(note("לא קיים במכשיר"))
        val storeSec = section("חנות כשרה", choice(listOf("להשאיר", "למחוק"), listOf(storeNow, storeNow), { store }) { store = it })
        if (!storeNow) storeSec.addView(note("לא קיים במכשיר"))
        val ok = tv("אישור (בלתי הפיך)", 16f) { confirmSetup() }.apply { style(this, true) }
        ok.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) }
        // המצלמה נפרדת מההגדרה החד-פעמית: החלפה שלה לא מסמנת את ההגדרה כ"בוצעה"
        val (camSec, swap) = cameraPart()
        page(header("התאמה אישית", null, "הגדרה חד-פעמית: רדיו, מציג HTML וחנות כשרה"), radioSec, htmlSec, storeSec, ok, camSec, swap)
    }

    private fun confirmSetup() {
        val lines = listOf(
            "רדיו: " + when { radio == radioNow -> "נשאר כמו שהוא"; radio == 0 -> "יימחק"; else -> "יוחלף ברדיו כשר" },
            "מציג HTML: " + if (!htmlNow) "לא קיים" else if (html == 1) "יימחק" else "נשאר",
            "חנות כשרה: " + if (!storeNow) "לא קיימת" else if (store == 1) "תימחק" else "נשארת")
        dlg().setTitle("לבצע את השינויים?")
            .setMessage("שים לב: הפעולה בלתי הפיכה.\n\n" + lines.joinToString("\n") + "\n\nבסיום המכשיר יופעל מחדש.")
            .setPositiveButton("ביצוע") { _, _ -> runSetup() }
            .setNegativeButton("ביטול", null).show()
    }

    private fun progress(): AlertDialog = dlg().setTitle("מבצע…")
        .setMessage("אל תכבה את המכשיר. הוא יופעל מחדש בסיום.").setCancelable(false).show()

    private fun asset(name: String): File {
        val f = File(cacheDir, name)
        assets.open(name).use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } }
        return f
    }

    private val REMOUNT = "mount -o remount,rw / 2>/dev/null; mount -o remount,rw /system 2>/dev/null; mount -o remount,rw /system_ext 2>/dev/null; "

    private fun cameraCmd(which: Int): String {
        prefs(this).edit().putBoolean(GRANT_KEY, true).commit()
        val f = asset(CAMERA_ASSET[which])
        return "cp ${q(f.path)} $CAMERA_APK && chmod 644 $CAMERA_APK && rm -rf $CAMERA_DIR/oat && " +
            "{ pm clear com.mediatek.camera >/dev/null 2>&1; echo C_OK; }; "
    }

    private fun runSetup() {
        val p = progress()
        Thread {
            val steps = mutableListOf<Pair<String, String>>()   // (סימון הצלחה, שם לתצוגה)
            val cmd = StringBuilder(REMOUNT)
            try {
                if (radio != radioNow) {
                    if (radio == 0) cmd.append("rm -rf $RADIO_DIR; [ -d $RADIO_DIR ] || echo R_OK; ")
                    else {
                        val f = asset("radio_kosher.apk")
                        cmd.append("A=\$(ls $RADIO_DIR/*.apk 2>/dev/null | head -1); [ -n \"\$A\" ] || A=$RADIO_DIR/FMRadio.apk; " +
                            "cp ${q(f.path)} \"\$A\" && chmod 644 \"\$A\" && rm -rf $RADIO_DIR/oat && { pm clear com.android.fmradio >/dev/null 2>&1; echo R_OK; }; ")
                    }
                    steps += "R_OK" to "רדיו"
                }
                if (htmlNow && html == 1) { cmd.append("rm -rf $HTML_DIR; [ -d $HTML_DIR ] || echo H_OK; "); steps += "H_OK" to "מציג HTML" }
                if (storeNow && store == 1) { cmd.append("rm -rf $STORE_DIR; [ -d $STORE_DIR ] || echo S_OK; "); steps += "S_OK" to "חנות כשרה" }
            } catch (_: Exception) { steps += "X_FAIL" to "הכנת הקבצים" }
            val out = if (steps.any { it.first == "X_FAIL" }) "" else runAsRoot(cmd.toString()).second
            val failed = steps.filter { !out.contains(it.first) }
            // הכול הצליח: מסמנים שההגדרה בוצעה (במחיצת המערכת — שורד איפוס) ומפעילים מחדש
            val marked = failed.isEmpty() && runAsRoot(REMOUNT +
                "{ mkdir -p /system/etc/ezrim && touch /system/etc/ezrim/setup_done; } 2>/dev/null || touch /system_ext/etc/ezrim_setup_done; " +
                "[ -e /system/etc/ezrim/setup_done ] || [ -e /system_ext/etc/ezrim_setup_done ] && echo D_OK").second.contains("D_OK")
            runOnUiThread {
                // שום דבר לא השתנה במכשיר — רק סימון, בלי הפעלה מחדש
                if (marked && steps.isEmpty()) { p.dismiss(); recreate(); return@runOnUiThread }
                if (marked) { Thread { runAsRoot("sync; reboot") }.start(); return@runOnUiThread }
                p.dismiss()
                val report = steps.joinToString("\n") { (k, name) -> (if (k in failed.map { it.first }) "✗  " else "✓  ") + name }
                dlg().setTitle("חלק מהשינויים לא בוצעו")
                    .setMessage(if (report.isBlank()) "הפעולה נכשלה" else report)
                    .setPositiveButton("סגור") { _, _ -> recreate() }.setCancelable(false).show()
            }
        }.start()
    }

    // ---------- אחרי ההגדרה: רק מצלמה ----------
    // חלק המצלמה (גם לפני וגם אחרי ההגדרה): בחירה + כפתור "החלפת מצלמה" משלו
    private fun cameraPart(): Pair<View, View> {
        cam = camNow ?: -1
        val now = TextView(this).apply { textSize = 13f; setTextColor(dim); setPadding(dp(2), dp(10), 0, 0) }
        lateinit var swap: TextView
        fun paint() {
            now.text = "בשימוש עכשיו: " + (camNow?.let { CAMERAS[it] } ?: "מצלמה אחרת")
            val can = cam >= 0 && cam != camNow
            swap.isEnabled = can; swap.alpha = if (can) 1f else 0.45f
        }
        swap = tv("החלפת מצלמה", 16f) { confirmCamera() }.apply { style(this, true) }
        val sec = section("מצלמה", choice(CAMERAS, listOf(true, true), { cam }) { cam = it; paint() }, now)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(swap)
            addView(TextView(this@CustomizationActivity).apply {
                text = "בהחלפה הגדרות המצלמה מתאפסות והמכשיר מופעל מחדש."
                textSize = 13f; setTextColor(dim); gravity = Gravity.START; setPadding(dp(4), dp(12), dp(4), 0)
            })
        }
        paint()
        return sec to box
    }

    private fun buildCameraOnly() {
        val (sec, box) = cameraPart()
        page(header("התאמה אישית"), sec, box)
    }

    private fun confirmCamera() {
        dlg().setTitle("להחליף ל${"מצלמה " + CAMERAS[cam]}?")
            .setMessage("הגדרות המצלמה יתאפסו והמכשיר יופעל מחדש.")
            .setPositiveButton("החלפה") { _, _ ->
                val p = progress()
                Thread {
                    val ok = try { runAsRoot(REMOUNT + cameraCmd(cam)).second.contains("C_OK") } catch (_: Exception) { false }
                    runOnUiThread {
                        if (ok) Thread { runAsRoot("sync; reboot") }.start()
                        else { p.dismiss(); dlg().setMessage("ההחלפה נכשלה").setPositiveButton("סגור", null).show() }
                    }
                }.start()
            }
            .setNegativeButton("ביטול", null).show()
    }
}
