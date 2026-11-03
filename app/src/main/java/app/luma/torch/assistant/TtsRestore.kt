package app.luma.torch.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.luma.torch.RootShell
import java.io.File

// קול עברי אופליין ל-Google TTS: הנתונים צרובים בקושחה (/system/etc/ezrim/tts), ועזרים מעתיק אותם (עם root)
// לתיקייה הפרטית של Google TTS — תיקיית הקולות (files/superpacks) וקובץ הרישום (databases/superpacks.db).
object TtsRestore {
    private const val PKG = "com.google.android.tts"
    const val SRC = "/system/etc/ezrim/tts"
    private const val MARK = "he-il/he-il-x-multi-r8"
    enum class Result { OK, NO_ROOT, FAIL }
    @Volatile private var running = false

    private fun googleInstalled(c: Context) = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Exception) { false }

    // יש מה להתקין: הנתונים בקושחה ו-Google TTS מותקן
    fun available(c: Context) = File("$SRC/superpacks/$MARK").isDirectory && googleInstalled(c)

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
        val (code, _) = RootShell.run("""
            [ -d $d ] || exit 3
            am force-stop $PKG
            U=${'$'}(stat -c %u $d); CTX=${'$'}(ls -Zd $d | awk '{print ${'$'}1}')
            mkdir -p $d/files $d/databases
            rm -rf $d/files/superpacks && cp -r $SRC/superpacks $d/files/ &&
            rm -f $d/databases/superpacks.db* && cp $SRC/superpacks.db $d/databases/superpacks.db &&
            chown -R ${'$'}U:${'$'}U $d/files/superpacks $d/databases &&
            chmod -R u+rwX,g+rwX,o-rwx $d/files/superpacks $d/databases &&
            chcon -R "${'$'}CTX" $d/files/superpacks $d/databases
        """.trimIndent())
        if (code != 0) return Result.FAIL
        Handler(Looper.getMainLooper()).post { Voice.recheck(c) }
        return Result.OK
    }
}
