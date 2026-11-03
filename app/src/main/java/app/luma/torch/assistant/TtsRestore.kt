package app.luma.torch.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.luma.torch.RootShell
import java.io.File

// קול עברי אופליין ל-Google TTS: הנתונים צרובים בקושחה — כתיקייה (/system/etc/ezrim/tts) או כקובץ ZIP אחד
// (/system/etc/ezrim/tts_he.zip, חוסך "מקומות" של קבצים במחיצת המערכת) — ועזרים מעתיק אותם (עם root)
// לתיקייה הפרטית של Google TTS — תיקיית הקולות (files/superpacks) וקובץ הרישום (databases/superpacks.db).
object TtsRestore {
    private const val PKG = "com.google.android.tts"
    const val DIR = "/system/etc/ezrim/tts"
    const val ZIP = "/system/etc/ezrim/tts_he.zip"
    private const val MARK = "he-il/he-il-x-multi-r8"
    enum class Result { OK, NO_ROOT, FAIL }
    @Volatile private var running = false

    private fun googleInstalled(c: Context) = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Exception) { false }

    // יש מה להתקין: הנתונים בקושחה ו-Google TTS מותקן
    fun available(c: Context) = (File("$DIR/superpacks/$MARK").isDirectory || File(ZIP).isFile) && googleInstalled(c)

    // מאיפה מעתיקים: התיקייה בקושחה, או ה-ZIP אחרי פריסה לתיקייה זמנית של עזרים
    private fun source(c: Context): String? {
        if (File("$DIR/superpacks/$MARK").isDirectory) return DIR
        if (!File(ZIP).isFile) return null
        val stage = File(c.filesDir, "tts_stage")
        stage.deleteRecursively(); stage.mkdirs()
        java.util.zip.ZipInputStream(File(ZIP).inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                // ב-ZIP הקבצים יושבים תחת system/etc/ezrim/tts/ — מורידים את הקידומת
                val name = e.name.substringAfter("ezrim/tts/", e.name).trimStart('/')
                if (name.isEmpty()) continue
                val f = File(stage, name)
                if (!f.canonicalPath.startsWith(stage.canonicalPath)) continue
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
            }
        }
        return if (File(stage, "superpacks/$MARK").isDirectory) stage.absolutePath else null
    }

    // אוטומטי, בכניסה לעזרים: רק אם הקול חסר
    fun ensure(c: Context) {
        if (!available(c) || running) return
        val app = c.applicationContext
        running = true
        Thread {
            try {
                val (code, out) = RootShell.run("[ -d /data/data/$PKG/files/superpacks/$MARK ] && echo HAVE || echo MISSING")
                if (code == 0 && out.contains("MISSING")) copy(app)
            } catch (_: Exception) {} finally { running = false }
        }.start()
    }

    // ידני, מהכפתור בהגדרות עזר
    fun install(c: Context, done: (Result) -> Unit) {
        val app = c.applicationContext
        Thread {
            val r = try { copy(app) } catch (_: Exception) { Result.FAIL }
            Handler(Looper.getMainLooper()).post { done(r) }
        }.start()
    }

    private fun copy(c: Context): Result {
        val (id, idOut) = RootShell.run("id")
        if (id != 0 || !idOut.contains("uid=0")) return Result.NO_ROOT
        val d = "/data/data/$PKG"
        val src = source(c) ?: return Result.FAIL
        val (code, _) = RootShell.run("""
            [ -d $d ] || exit 3
            am force-stop $PKG
            U=${'$'}(stat -c %u $d); CTX=${'$'}(ls -Zd $d | awk '{print ${'$'}1}')
            mkdir -p $d/files $d/databases
            rm -rf $d/files/superpacks && cp -r $src/superpacks $d/files/ &&
            rm -f $d/databases/superpacks.db* && cp $src/superpacks.db $d/databases/superpacks.db &&
            chown -R ${'$'}U:${'$'}U $d/files/superpacks $d/databases &&
            chmod -R u+rwX,g+rwX,o-rwx $d/files/superpacks $d/databases &&
            chcon -R "${'$'}CTX" $d/files/superpacks $d/databases
        """.trimIndent())
        File(c.filesDir, "tts_stage").deleteRecursively()
        if (code != 0) return Result.FAIL
        Handler(Looper.getMainLooper()).post { Voice.recheck(c) }
        return Result.OK
    }
}
