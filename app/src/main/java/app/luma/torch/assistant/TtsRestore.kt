package app.luma.torch.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.luma.torch.RootShell
import java.io.File

// קול עברי אופליין ל-Google TTS (מותקן רק בלחיצה על הכפתור בהגדרות עזר): הנתונים צרובים בקושחה — כתיקייה (/system/etc/ezrim/tts) או כקובץ ZIP אחד
// (/system/etc/ezrim/tts_he.zip, חוסך "מקומות" של קבצים במחיצת המערכת) — ועזרים מעתיק אותם (עם root)
// לתיקייה של Google TTS — תיקיית הקולות (files/superpacks) וקובץ הרישום (databases/superpacks.db).
// Google TTS שומרת את הקולות באחסון של המכשיר (/data/user_de), שזמין כבר לפני ביטול הנעילה — לא ב-/data/data.
object TtsRestore {
    private const val PKG = "com.google.android.tts"
    const val DIR = "/system/etc/ezrim/tts"
    const val ZIP = "/system/etc/ezrim/tts_he.zip"
    private const val MARK = "he-il/he-il-x-multi-r8"
    enum class Result { OK, NO_ROOT, FAIL }
    
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

    // ידני, מהכפתור בהגדרות עזר
    // done נקרא אחרי ההעתקה; ב-OK — גם אחרי שעזר בדק מחדש אם יש קול עברי
    fun install(c: Context, done: (Result) -> Unit) {
        val app = c.applicationContext
        Thread {
            val r = try { copy(app) } catch (_: Exception) { Result.FAIL }
            Handler(Looper.getMainLooper()).post {
                if (r == Result.OK) Voice.recheck(app) { done(r) } else done(r)
            }
        }.start()
    }

    private fun copy(c: Context): Result {
        if (!RootShell.hasRoot()) return Result.NO_ROOT
        val d = "/data/user_de/0/$PKG"
        val src = source(c) ?: return Result.FAIL
        val (code, _) = RootShell.run("""
            [ -d $d ] || exit 3   # רץ בתת-מעטפת: לא סוגר את חיבור ה-root
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
        return if (code == 0) Result.OK else Result.FAIL
    }
}
