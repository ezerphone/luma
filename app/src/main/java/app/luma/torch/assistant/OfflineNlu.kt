package app.luma.torch.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder

// מנוע ההבנה של "עוזר אופליין" (בשימוש ברשות): מסווג משפט לאחת מ-144 פקודות.
// מאפיינים: מילים (ספרות → #), זוגות מילים, חלקי מילים של 2–4 אותיות, מגובבים ל-16384 תאים (FNV-1a), מנורמלים;
// ציון לינארי + softmax. מחזיר את שתי הפקודות המובילות ואת הביטחון.
class OfflineNlu(model: ByteArray, val labels: List<String>) {
    private val dim: Int
    private val classes: Int
    private val w: FloatArray
    private val bias: FloatArray

    init {
        val b = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN)
        dim = b.int; classes = b.int
        require(dim == 16384 && classes == labels.size) { "model/labels mismatch" }
        w = FloatArray(dim * classes); b.asFloatBuffer().get(w)
        b.position(8 + dim * classes * 4)
        bias = FloatArray(classes); b.asFloatBuffer().get(bias)
    }

    class Result(val label: String, val prob: Float, val second: String, val secondProb: Float)

    fun classify(text: String): Result {
        val z = bias.copyOf()
        for ((k, v) in features(text)) {
            val base = k * classes
            for (j in 0 until classes) z[j] += w[base + j] * v
        }
        val m = z.max()
        var sum = 0.0
        val p = DoubleArray(classes) { val e = Math.exp((z[it] - m).toDouble()); sum += e; e }
        val o = (0 until classes).sortedByDescending { p[it] }
        return Result(labels[o[0]], (p[o[0]] / sum).toFloat(), labels[o[1]], (p[o[1]] / sum).toFloat())
    }

    companion object {
        private val QUOTES = setOf('\''.code, '"'.code, '`'.code, 8217, 8216, 8220, 8221, 1523, 1524)

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.lowercase()) {
                val o = ch.code
                if (o in QUOTES) continue
                if (o in 1425..1479 && o != 1470) continue
                val c = when (o) { 1498 -> 1499; 1501 -> 1502; 1503 -> 1504; 1507 -> 1508; 1509 -> 1510; else -> o }
                if (c in 1488..1514 || c in 97..122 || c in 48..57) sb.append(c.toChar()) else sb.append(' ')
            }
            return sb.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        }

        fun features(text: String): Map<Int, Float> {
            val c = normalize(text)
            val toks = if (c.isEmpty()) emptyList() else c.split(' ').map { w ->
                val sb = StringBuilder(); var dig = false
                for (ch in w) { if (ch in '0'..'9') { if (!dig) sb.append('#'); dig = true } else { sb.append(ch); dig = false } }
                sb.toString()
            }
            val f = ArrayList<String>()
            var prev = "^"
            for (t in toks) {
                f += "w|$t"; f += "b|$prev|$t"
                val g = "<$t>"
                for (n in 2..4) for (i in 0..g.length - n) f += "c|" + g.substring(i, i + n)
                prev = t
            }
            val counts = HashMap<Int, Double>()
            for (s in f) {
                var h = -2128831035
                for (ch in s) h = (h xor ch.code) * 16777619
                val k = ((h.toLong() and 0xFFFFFFFFL) % 16384).toInt()
                counts[k] = (counts[k] ?: 0.0) + 1.0
            }
            val n = Math.sqrt(counts.values.sumOf { it * it })
            if (n == 0.0) return emptyMap()
            return counts.mapValues { (it.value / n).toFloat() }
        }
    }
}
