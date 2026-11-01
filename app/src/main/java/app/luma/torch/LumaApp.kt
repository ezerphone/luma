package app.luma.torch

import android.app.Application
import android.os.Process
import android.util.Log
import java.io.File

// נקודת הבדיקה היחידה של הקושחה. Application נוצר לפני כל Activity, Service ו-Receiver,
// כך שבקושחה לא מקורית שום חלק באפליקציה לא מגיע לרוץ.
class LumaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!FirmwareGuard.isGenuine()) {
            Log.e("Luma", "קובץ ההרשאות של הקושחה לא נמצא: האפליקציה נסגרת")
            Process.killProcess(Process.myPid())
            return
        }
        // שומר את פירוט הקריסה האחרונה בקובץ; המסך הראשי מציג אותה בפתיחה הבאה, כדי שאפשר יהיה לדווח עליה בלי adb
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "last_crash.txt").writeText(Log.getStackTraceString(e)) } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
        // מנוע הדיבור ומנוע ההבנה נטענים פעם אחת כשהתהליך עולה (פתיחת עזרים, אתחול, שירות הנגישות),
        // ונשארים טעונים כל עוד התהליך חי — כך העוזר וההקלדה הקולית מוכנים מיד
        try { if (app.luma.torch.dictation.ModelLocator.isInstalled()) app.luma.torch.dictation.SpeechEngine.get(this).warm() } catch (_: Throwable) {}
        Thread { try { app.luma.torch.assistant.Assistant.engine(this) } catch (_: Throwable) {} }.start()
    }
}
