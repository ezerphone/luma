package app.luma.torch.dictation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager

// מיקרופון אדום שפועם בקצה המסך, ליד כפתור המצלמה, בזמן הכתבה מהכפתור.
// מוצג כשכבת נגישות (בלי הרשאה נוספת) מתוך שירות הנגישות של עזרים.
class EdgeMic(private val c: Context) {
    private var view: MicView? = null
    private val wm get() = c.getSystemService(WindowManager::class.java)
    private fun dp(v: Float) = v * c.resources.displayMetrics.density

    fun show() {
        if (view != null) return
        val v = MicView(c)
        val size = dp(96f).toInt()
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL   // כפתור המצלמה: צד ימין, באמצע הגובה
        }
        try { wm.addView(v, lp); view = v; v.start() } catch (_: Exception) {}
    }

    fun hide() {
        val v = view ?: return
        view = null
        v.stop()
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private inner class MicView(c: Context) : View(c) {
        private val red = 0xFFE53935.toInt()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var t = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }
        fun start() = anim.start()
        fun stop() = anim.cancel()

        override fun onDraw(cv: Canvas) {
            val r = dp(26f)
            val cx = width - dp(30f); val cy = height / 2f   // צמוד לקצה המסך
            // שתי טבעות שמתרחבות ונעלמות, כמו הקלטה
            for (k in 0..1) {
                val p = (t + k * 0.5f) % 1f
                fill.color = red; fill.alpha = ((1f - p) * 90).toInt()
                cv.drawCircle(cx, cy, r + p * dp(20f), fill)
            }
            fill.color = red; fill.alpha = 255
            cv.drawCircle(cx, cy, r, fill)
            // אייקון מיקרופון
            val s = r / 12f
            cv.drawRoundRect(RectF(cx - 3 * s, cy - 8 * s, cx + 3 * s, cy + 3 * s), 3 * s, 3 * s, white)
            stroke.strokeWidth = 2 * s
            cv.drawArc(RectF(cx - 6.5f * s, cy - 6 * s, cx + 6.5f * s, cy + 6 * s), 0f, 180f, false, stroke)
            cv.drawLine(cx, cy + 6 * s, cx, cy + 9.5f * s, stroke)
        }
    }
}
