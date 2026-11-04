package app.luma.torch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import java.io.File

class SettingsActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val export = tv("ייצוא דוח שגיאות") { exportReport() }.apply { style(this, false) }
        page(
            header("הגדרות", null, "מראה האפליקציה"),
            section("תצוגה", group(listOf("אוטומטי", "בהיר", "כהה"), { Torch.theme }) { Torch.theme = it; recreate() }),
            section("תמיכה", export)
        )
    }

    // דוח שגיאות: פרטי המכשיר + פירוט הקריסה האחרונה (אם הייתה), דרך חלון השיתוף
    private fun exportReport() {
        val trace = try { File(filesDir, "last_crash.txt").takeIf { it.exists() }?.readText().orEmpty() } catch (_: Exception) { "" }
        if (trace.isBlank()) { Toast.makeText(this, "אין שגיאות לדווח", Toast.LENGTH_SHORT).show(); return }
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        val text = "דוח שגיאות — עזרים $ver\n${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n\n$trace"
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "דוח שגיאות — עזרים").putExtra(Intent.EXTRA_TEXT, text)
        try { startActivity(Intent.createChooser(i, "ייצוא דוח שגיאות")) }
        catch (_: Exception) { Toast.makeText(this, "אין אפליקציה לשיתוף", Toast.LENGTH_SHORT).show() }
    }
}
