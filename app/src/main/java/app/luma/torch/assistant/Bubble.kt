package app.luma.torch.assistant

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

// מראה בועת הצ'אט (החלונית של עזר): צבע ומצב בהיר / כהה / אוטומטי
object Bubble {
    // לכל צבע: עיקרי, כהה, בהיר
    class Tone(val main: Int, val deep: Int, val light: Int)

    val TONES = listOf(
        Tone(0xFF2A6FD6.toInt(), 0xFF0A2A66.toInt(), 0xFF6FAEF8.toInt()),   // כחול
        Tone(0xFF7B4FD6.toInt(), 0xFF2A0A66.toInt(), 0xFFB49BFF.toInt()),   // סגול
        Tone(0xFF0C9A82.toInt(), 0xFF063F3A.toInt(), 0xFF5FE0C8.toInt()),   // טורקיז
        Tone(0xFF2E9B4F.toInt(), 0xFF0D3B1A.toInt(), 0xFF8BE0A2.toInt()),   // ירוק
        Tone(0xFFD6457B.toInt(), 0xFF5A0B3A.toInt(), 0xFFFF9CC0.toInt()),   // ורוד
        Tone(0xFFE07A1F.toInt(), 0xFF5A2A06.toInt(), 0xFFFFC07A.toInt()),   // כתום
    )
    val MODES = listOf("בהיר", "כהה", "אוטומטי")

    fun color(c: Context) = Assistant.prefs(c).getInt("bubble_color", 0).coerceIn(0, TONES.size - 1)
    fun mode(c: Context) = Assistant.prefs(c).getInt("bubble_mode", 2).coerceIn(0, 2)
    fun setColor(c: Context, i: Int) = Assistant.prefs(c).edit().putInt("bubble_color", i).apply()
    fun setMode(c: Context, i: Int) = Assistant.prefs(c).edit().putInt("bubble_mode", i).apply()

    fun tone(c: Context) = TONES[color(c)]

    fun dark(c: Context) = when (mode(c)) {
        0 -> false
        1 -> true
        else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    // ערבוב שני צבעים (f=0 → a, f=1 → b)
    fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * f).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }
}
