package app.luma.torch

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

// מעטפת root אחת שנשארת פתוחה, כך שהרשאת ה-root מתבקשת פעם אחת בלבד בכל הפעלה של האפליקציה
object RootShell {
    private var proc: Process? = null
    private var w: BufferedWriter? = null
    private var r: BufferedReader? = null
    @Volatile private var asked = false

    // מבקש את הרשאת ה-root מיד בפתיחת האפליקציה (פעם אחת)
    fun warmUp() {
        if (asked) return
        asked = true
        Thread { run("id") }.start()
    }

    private fun alive(): Boolean {
        val p = proc ?: return false
        return try { p.exitValue(); false } catch (_: IllegalThreadStateException) { true }
    }

    private fun close() {
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null; w = null; r = null
    }

    @Synchronized
    fun run(cmd: String): Pair<Int, String> {
        if (!alive()) {
            close()
            try {
                val p = ProcessBuilder("su").redirectErrorStream(true).start()
                proc = p; w = p.outputStream.bufferedWriter(); r = p.inputStream.bufferedReader()
            } catch (e: Exception) { return -1 to (e.message ?: "su not found") }
        }
        val marker = "__EZRIM_END_${System.nanoTime()}__"
        return try {
            w!!.write("{ $cmd\n} < /dev/null\nrc=\$?\necho\necho \"$marker\$rc\"\n")
            w!!.flush()
            val sb = StringBuilder()
            var code: Int
            while (true) {
                val line = r!!.readLine() ?: throw IOException("denied")
                if (line.startsWith(marker)) { code = line.removePrefix(marker).trim().toIntOrNull() ?: -1; break }
                sb.append(line).append('\n')
            }
            code to sb.toString().trim()
        } catch (e: Exception) { close(); -1 to (e.message ?: "denied") }
    }
}
