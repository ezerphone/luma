package app.luma.torch

import android.os.Bundle
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class CustomizationActivity : BaseActivity() {
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    // נתיבי המערכת שסופקו עבור אפליקציות המערכת
    private val radioDir = "/system/system_ext/priv-app/FMRadio"
    private val htmlDir = "/system/app/HTMLViewer"
    private val cameraApk = "/system_ext/app/Camera/Camera.apk"
    private lateinit var radioStatus: TextView
    private lateinit var htmlStatus: TextView
    private lateinit var camStatus: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        radioStatus = status()
        val delRadio = warnBtn("מחיקת אפליקציית הרדיו") {
            confirm("מחיקת רדיו", "פעולה זו תמחק לצמיתות את אפליקציית הרדיו מהמכשיר. לא ניתן לשחזר אותה בחזרה (אלא בצריבה מחדש של הקושחה). להמשיך?") {
                deleteDir(radioDir, radioStatus)
            }
        }

        htmlStatus = status()
        val delHtml = warnBtn("מחיקת מציג ה-HTML") {
            confirm("מחיקת מציג HTML", "פעולה זו תמחק לצמיתות את מציג ה-HTML מהמכשיר. לא ניתן לשחזר אותה בחזרה (אלא בצריבה מחדש של הקושחה). להמשיך?") {
                deleteDir(htmlDir, htmlStatus)
            }
        }

        camStatus = status()
        val revertCam = warnBtn("חזרה למצלמה המקורית") {
            confirm("חזרה למצלמה המקורית", "ייתכן שלאחר ההחלפה יידרש איפוס להגדרות יצרן כדי שהמצלמה תעבוד כראוי. פעולה זו תחליף את אפליקציית המצלמה הנוכחית במכשיר בחזרה למצלמה המקורית. להמשיך?") {
                revertCamera()
            }
        }

        page(
            header("התאמה אישית", null, "פעולות מערכת למכשיר — דורש root"),
            section("אפליקציית רדיו", delRadio, radioStatus),
            section("מציג HTML", delHtml, htmlStatus),
            section("מצלמה", revertCam, camStatus)
        )
    }

    private fun status() = TextView(this).apply {
        textSize = 13f; setTextColor(dim); setPadding(0, dp(10), 0, 0); visibility = android.view.View.GONE
    }

    private fun show(t: TextView, msg: String, color: Int) {
        t.text = msg; t.setTextColor(color); t.visibility = android.view.View.VISIBLE
    }

    // כפתור בצבע אזהרה, לפעולות הרסניות
    private fun warnBtn(t: String, click: () -> Unit) = tv(t, 15f, click).apply {
        setTextColor(warnC); background = shape(chip, 14, warnC)
    }

    private fun confirm(title: String, msg: String, go: () -> Unit) {
        dlg().setTitle(title).setMessage(msg)
            .setPositiveButton("אישור") { _, _ -> go() }
            .setNegativeButton("ביטול", null).show()
    }

    // שואל אם להפעיל מחדש את המכשיר כעת, כדי שהשינוי ייכנס לתוקף
    private fun askReboot() {
        dlg().setTitle("הפעלה מחדש")
            .setMessage("הפעולה הושלמה. להפעיל מחדש את המכשיר כעת כדי שהשינוי ייכנס לתוקף?")
            .setPositiveButton("הפעלה מחדש") { _, _ -> Thread { runAsRoot("reboot") }.start() }
            .setNegativeButton("לא עכשיו", null).show()
    }

    private fun deleteDir(dir: String, statusView: TextView) {
        show(statusView, "מוחק…", dim)
        Thread {
            // במכשירי system-as-root אין נקודת עגינה נפרדת ל-/system, לכן מנסים גם remount על השורש עצמו
            val cmd = "mount -o remount,rw / 2>&1; mount -o remount,rw /system 2>&1; mount -o remount,rw /system_ext 2>&1; " +
                "echo ---; awk '\$2==\"/\"||\$2==\"/system\"||\$2==\"/system_ext\"{print}' /proc/mounts; echo ---; " +
                "rm -rf ${q(dir)} 2>&1; [ -d ${q(dir)} ] && echo STILL_EXISTS || echo REMOVED"
            val (code, out) = runAsRoot(cmd)
            runOnUiThread {
                if (out.contains("REMOVED")) { show(statusView, "נמחק בהצלחה.", acc); askReboot() }
                else {
                    setTextIsSelectable(statusView)
                    show(statusView, "נכשל (קוד $code):\n${out.ifBlank { "(ריק)" }}", warnC)
                }
            }
        }.start()
    }

    private fun setTextIsSelectable(t: TextView) { t.setTextIsSelectable(true); t.typeface = android.graphics.Typeface.MONOSPACE; t.textSize = 11f }

    // מעתיק את קובץ המצלמה המקורית מתוך נכסי עזרים עצמה (קובץ קטן), לנתיב המערכת, עם הרשאות 644
    private fun revertCamera() {
        show(camStatus, "מעתיק…", dim)
        Thread {
            try {
                val tmp = File(cacheDir, "camera_stock.apk")
                assets.open("camera_stock.apk").use { input -> FileOutputStream(tmp).use { out -> input.copyTo(out) } }
                val cmd = "mount -o remount,rw / 2>&1; mount -o remount,rw /system_ext 2>&1; mount -o remount,rw /system 2>&1; " +
                    "echo ---; awk '\$2==\"/\"||\$2==\"/system\"||\$2==\"/system_ext\"{print}' /proc/mounts; echo ---; " +
                    "cp ${q(tmp.absolutePath)} ${q(cameraApk)} 2>&1 && chmod 644 ${q(cameraApk)} 2>&1 && echo OK"
                val (code, out) = runAsRoot(cmd)
                runOnUiThread {
                    if (out.contains("OK")) { show(camStatus, "הוחלפה בהצלחה.", acc); askReboot() }
                    else { setTextIsSelectable(camStatus); show(camStatus, "נכשל (קוד $code):\n${out.ifBlank { "(ריק)" }}", warnC) }
                }
            } catch (e: Exception) {
                runOnUiThread { show(camStatus, "קובץ המצלמה המקורית חסר באפליקציה (camera_stock.apk).", warnC) }
            }
        }.start()
    }
}
