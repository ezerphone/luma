package app.luma.torch

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import kotlin.math.sqrt

class FlashService : Service(), SensorEventListener {
    private var last = 0L
    private var peak = 0L
    private var lastPeak = 0L
    private var tone: ToneGenerator? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        Torch.init(this)
        tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("luma", "עזרים", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "luma").setSmallIcon(R.drawable.ic_flash)
            .setContentTitle("עזרים").setContentText("הדלקה בניעור פעילה").setContentIntent(pi).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "luma:shake").also { it.acquire() }
        val sm = getSystemService(SensorManager::class.java)
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }

    // צריך שתי תנודות חדות בתוך פחות משנייה; הסף נקבע לפי הרגישות שנבחרה
    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
        val t = SystemClock.elapsedRealtime()
        val th = when (Torch.sens) { 2 -> 2.2f; 1 -> 2.8f; else -> 3.5f }
        if (g > th && t - lastPeak > 150) {
            lastPeak = t
            if (peak != 0L && t - peak < 900) {
                peak = 0L
                if (t - last > 1500) {
                    last = t; Torch.set(!Torch.on)
                    if (Torch.sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                }
            } else peak = t
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        getSystemService(SensorManager::class.java).unregisterListener(this)
        wl?.release()
        tone?.release()
    }
}
