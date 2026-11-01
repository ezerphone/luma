package app.luma.torch.assistant


// נרמול טקסט עברי (ניקוד, גרשיים, אותיות סופיות) — משמש את חילוץ הפרטים בעוזר
object Features {
    private val QUOTES = setOf('\''.code, '"'.code, '`'.code, 0x2019, 0x2018, 0x201C, 0x201D, 0x05F3, 0x05F4)
    private const val PREFIX = "והבלכמש"

    fun finalToRegular(c: Int): Int = when (c) {
        0x05DA -> 0x05DB; 0x05DD -> 0x05DE; 0x05DF -> 0x05E0; 0x05E3 -> 0x05E4; 0x05E5 -> 0x05E6; else -> c
    }

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            val o = ch.code
            if (o in QUOTES) continue
            if (o in 0x0591..0x05C7 && o != 0x05BE) continue
            val c = finalToRegular(o)
            if (c in 0x05D0..0x05EA || c in 97..122 || c in 48..57) sb.append(c.toChar()) else sb.append(' ')
        }
        return sb.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun maskDigits(t: String): String {
        val sb = StringBuilder(); var prev = false
        for (ch in t) {
            if (ch in '0'..'9') { if (!prev) sb.append('#'); prev = true } else { sb.append(ch); prev = false }
        }
        return sb.toString()
    }

    fun stems(t: String): List<String> {
        val res = ArrayList<String>(3)
        var cur = t
        repeat(3) {
            if (cur.length - 1 >= 3 && PREFIX.indexOf(cur[0]) >= 0) { cur = cur.substring(1); res += cur } else return res
        }
        return res
    }

    fun raw(text: String): List<String> {
        val toks = normalize(text).split(' ').filter { it.isNotEmpty() }.map { maskDigits(it) }
        val f = ArrayList<String>()
        var prev = "^"
        for (t in toks) {
            f += "w|$t"
            val st = stems(t)
            for (s in st) f += "s|$s"
            val base = st.lastOrNull() ?: t
            f += "b|$prev|$base"
            val g = "<$t>"
            for (n in 2..4) for (i in 0..g.length - n) f += "c|" + g.substring(i, i + n)
            prev = base
        }
        if (toks.isNotEmpty()) f += "b|$prev|$"
        f += "n|" + minOf(toks.size, 6)
        return f
    }

    private fun fnv(s: String): Long {
        var h = 0x811C9DC5L
        for (ch in s) h = ((h xor ch.code.toLong()) * 0x01000193L) and 0xFFFFFFFFL
        return h
    }

    fun vector(text: String, dim: Int): Map<Int, Double> {
        val counts = HashMap<Int, Double>()
        for (f in raw(text)) { val k = (fnv(f) % dim).toInt(); counts[k] = (counts[k] ?: 0.0) + 1.0 }
        val n = Math.sqrt(counts.values.sumOf { it * it })
        if (n == 0.0) return emptyMap()
        return counts.mapValues { it.value / n }
    }
}
