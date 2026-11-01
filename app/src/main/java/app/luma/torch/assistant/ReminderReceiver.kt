package app.luma.torch.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.luma.torch.R

// תזכורת שהגיע זמנה: התראה עם צליל. טיימר שינה: עצירת המוזיקה.
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        Assistant.done(c, id)
        if (i.getBooleanExtra("pause", false)) { Assistant.pauseMedia(c); return }
        val text = i.getStringExtra("text") ?: "תזכורת"
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "תזכורות של עזר", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
            })
        }
        nm.notify(id, Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_mic).setContentTitle(Assistant.NAME).setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).build())
    }

    companion object { const val CH = "assistant_reminders" }
}
