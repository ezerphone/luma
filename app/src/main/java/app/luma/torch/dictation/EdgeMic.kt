package app.luma.torch.dictation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.view.WindowManager

// סימון "מקשיב" בזמן הכתבה מכפתור המצלמה: קו זוהר בקצה המסך (ליד הכפתור) ותווית קטנה "מקשיב".
// מוצג כשכבת נגישות (בלי הרשאה נוספת) מתוך שירות הנגישות של עזרים, ולא נוגע בתוכן המסך.
class EdgeMic(private val c: Context) {
    private var view: GlowView? = null
    private val wm get() = c.getSystemService(WindowManager::class.java)
    private fun dp(v: Float) = v * c.resources.displayMetrics.density

    fun show() {
        if (view != null) return
        val v = GlowView(c)
        val lp = WindowManager.LayoutParams(dp(130f).toInt(), dp(190f).toInt(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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

    private inner class GlowView(c: Context) : View(c) {
        private val teal = 0xFF0C9A82.toInt()
        private val blue = 0xFF2BB8DB.toInt()
        private val line = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = dp(13f); typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER
        }
        private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var t = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // נחוץ לזוהר ולצל
        fun start() = anim.start()
        fun stop() = anim.cancel()

        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val lw = dp(5f); val lh = dp(150f)
            val top = (h - lh) / 2
            // הזוהר: נושם לאט
            glow.shader = null
            glow.color = teal; glow.alpha = (70 + 70 * t).toInt()
            glow.maskFilter = BlurMaskFilter(dp(10f + 6f * t), BlurMaskFilter.Blur.NORMAL)
            cv.drawRoundRect(RectF(w - lw - dp(4f), top, w + dp(4f), top + lh), lw, lw, glow)
            // הקו עצמו, צמוד לקצה
            line.shader = LinearGradient(0f, top, 0f, top + lh, intArrayOf(blue, teal, blue), null, Shader.TileMode.CLAMP)
            cv.drawRoundRect(RectF(w - lw, top, w + lw, top + lh), lw, lw, line)
            // התווית "מקשיב" עם אייקון מיקרופון
            val label = "מקשיב"
            val tw = text.measureText(label)
            val iw = dp(12f); val padH = dp(12f); val gap = dp(6f)
            val pw = padH * 2 + tw + gap + iw; val ph = dp(30f)
            val right = w - dp(16f); val left = right - pw
            val cy = h / 2
            pill.setShadowLayer(dp(4f), 0f, dp(2f), 0x33000000)
            cv.drawRoundRect(RectF(left, cy - ph / 2, right, cy + ph / 2), ph / 2, ph / 2, pill)
            // אייקון (בצד ימין של התווית, לפני הטקסט בעברית)
            val ix = right - padH - iw / 2; val s = iw / 12f
            cv.drawRoundRect(RectF(ix - 2.5f * s, cy - 6 * s, ix + 2.5f * s, cy + 2 * s), 2.5f * s, 2.5f * s, icon)
            iconStroke.strokeWidth = 1.6f * s
            cv.drawArc(RectF(ix - 5 * s, cy - 4 * s, ix + 5 * s, cy + 4.5f * s), 0f, 180f, false, iconStroke)
            cv.drawLine(ix, cy + 4.5f * s, ix, cy + 7 * s, iconStroke)
            val tx = right - padH - iw - gap - tw / 2
            cv.drawText(label, tx, cy - (text.descent() + text.ascent()) / 2, text)
        }
    }
}
