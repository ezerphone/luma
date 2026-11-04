package app.luma.torch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Torch.init(c)
        if (Torch.shake) c.startForegroundService(Intent(c, FlashService::class.java))
        // המקלדת הקולית מופעלת אוטומטית (גם אחרי עדכון של עזרים), אלא אם כובתה ידנית
        try { app.luma.torch.dictation.VoiceIme.ensureDefault(c) } catch (_: Exception) {}
        try { app.luma.torch.assistant.Assistant.rearm(c) } catch (_: Exception) {}
        try { app.luma.torch.assistant.Assistant.ensureDefault(c) } catch (_: Exception) {}
        try { app.luma.torch.assistant.Hotword.sync(c) } catch (_: Exception) {}
        // אחרי החלפת מצלמה: מתן הרשאות האחסון למצלמה (פעם אחת, ברקע)
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            val done = goAsync()
            Thread { try { CustomizationActivity.onBoot(c) } catch (_: Exception) {} finally { done.finish() } }.start()
        }
    }
}
