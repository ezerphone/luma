package app.luma.torch.assistant

import java.time.DayOfWeek
import java.time.LocalDate

// לוח עברי (חישוב בלבד, בלי אינטרנט): תאריך עברי, חגים, ראש חודש, ספירת העומר, מולד ופרשת השבוע (מנהג ארץ ישראל).
// אלגוריתמים לפי Reingold & Dershowitz, "Calendrical Calculations". חודשים: ניסן=1 … אדר=12, אדר ב'=13, תשרי=7.
object HebCal {
    private const val EPOCH = -1373427L          // 1 תשרי שנה 1, בספירת R.D.
    private const val RD_1970 = 719163L

    fun leap(y: Int) = (7L * y + 1) % 19 < 7
    fun lastMonth(y: Int) = if (leap(y)) 13 else 12

    private fun elapsed(y: Int): Long {
        val months = Math.floorDiv(235L * y - 234, 19L)
        val parts = 12084L + 13753L * months
        var days = 29L * months + Math.floorDiv(parts, 25920L)
        if (Math.floorMod(3 * (days + 1), 7L) < 3) days++
        return days
    }

    private fun correction(y: Int): Long {
        val ny0 = elapsed(y - 1); val ny1 = elapsed(y); val ny2 = elapsed(y + 1)
        return if (ny2 - ny1 == 356L) 2 else if (ny1 - ny0 == 382L) 1 else 0
    }

    fun newYear(y: Int) = EPOCH + elapsed(y) + correction(y)
    fun daysInYear(y: Int) = newYear(y + 1) - newYear(y)
    private fun longHeshvan(y: Int) = daysInYear(y) % 10 == 5L
    private fun shortKislev(y: Int) = daysInYear(y) % 10 == 3L

    fun monthDays(m: Int, y: Int): Int =
        if (m in setOf(2, 4, 6, 10, 13) || (m == 12 && !leap(y)) || (m == 8 && !longHeshvan(y)) || (m == 9 && shortKislev(y))) 29 else 30

    fun fixed(y: Int, m: Int, d: Int): Long {
        var r = newYear(y) + d - 1
        if (m < 7) {
            for (mm in 7..lastMonth(y)) r += monthDays(mm, y)
            for (mm in 1 until m) r += monthDays(mm, y)
        } else for (mm in 7 until m) r += monthDays(mm, y)
        return r
    }

    data class HDate(val y: Int, val m: Int, val d: Int)

    fun rd(date: LocalDate) = date.toEpochDay() + RD_1970
    fun date(rd: Long): LocalDate = LocalDate.ofEpochDay(rd - RD_1970)

    fun fromRd(rd: Long): HDate {
        var y = ((rd - EPOCH) * 98496.0 / 35975351.0).toInt()
        while (newYear(y + 1) <= rd) y++
        while (newYear(y) > rd) y--
        val start = if (rd < fixed(y, 1, 1)) 7 else 1
        var m = start
        while (rd > fixed(y, m, monthDays(m, y))) m = if (m == lastMonth(y)) 1 else m + 1
        return HDate(y, m, (rd - fixed(y, m, 1) + 1).toInt())
    }

    fun of(date: LocalDate) = fromRd(rd(date))

    // ---------- טקסט ----------
    private val ONES = listOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val TENS = listOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val HUND = listOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")

    fun gematria(n0: Int): String {
        val n = n0 % 1000
        var s = HUND[n / 100]
        val r = n % 100
        s += when (r) { 15 -> "טו"; 16 -> "טז"; else -> TENS[r / 10] + ONES[r % 10] }
        return if (s.length == 1) "$s׳" else s.dropLast(1) + "״" + s.last()
    }

    fun monthName(m: Int, y: Int) = when (m) {
        1 -> "ניסן"; 2 -> "אייר"; 3 -> "סיוון"; 4 -> "תמוז"; 5 -> "אב"; 6 -> "אלול"; 7 -> "תשרי"; 8 -> "חשוון"; 9 -> "כסלו"
        10 -> "טבת"; 11 -> "שבט"; 12 -> if (leap(y)) "אדר א׳" else "אדר"; else -> "אדר ב׳"
    }

    fun format(h: HDate) = "${gematria(h.d)} ב${monthName(h.m, h.y)} ${gematria(h.y)}"
    fun format(date: LocalDate) = format(of(date))

    // ---------- חגים ----------
    private fun adar(y: Int) = if (leap(y)) 13 else 12

    // תאריך החג בשנה עברית y (עם הדחיות של הצומות ויום העצמאות)
    fun holiday(key: String, y: Int): Long? {
        fun shift(rd: Long, satTo: Int): Long = if (date(rd).dayOfWeek == DayOfWeek.SATURDAY) rd + satTo else rd
        return when (key) {
            "rosh_hashana" -> fixed(y, 7, 1)
            "gedalia" -> shift(fixed(y, 7, 3), 1)
            "kippur" -> fixed(y, 7, 10)
            "sukkot" -> fixed(y, 7, 15)
            "simchat_torah" -> fixed(y, 7, 22)
            "chanukah" -> fixed(y, 9, 25)
            "tevet10" -> fixed(y, 10, 10)
            "tubishvat" -> fixed(y, 11, 15)
            "esther" -> fixed(y, adar(y), 13).let { if (date(it).dayOfWeek == DayOfWeek.SATURDAY) it - 2 else it }
            "purim" -> fixed(y, adar(y), 14)
            "pesach" -> fixed(y, 1, 15)
            "atzmaut" -> fixed(y, 2, 5).let { when (date(it).dayOfWeek) { DayOfWeek.FRIDAY -> it - 1; DayOfWeek.SATURDAY -> it - 2; DayOfWeek.MONDAY -> it + 1; else -> it } }
            "lagbaomer" -> fixed(y, 2, 18)
            "yerushalayim" -> fixed(y, 2, 28)
            "shavuot" -> fixed(y, 3, 6)
            "tammuz17" -> shift(fixed(y, 4, 17), 1)
            "av9" -> shift(fixed(y, 5, 9), 1)
            else -> null
        }
    }

    val HOLIDAYS = listOf(
        "ראש השנה" to "rosh_hashana", "צום גדליה" to "gedalia", "גדליה" to "gedalia", "יום כיפור" to "kippur", "כיפור" to "kippur",
        "סוכות" to "sukkot", "שמחת תורה" to "simchat_torah", "חנוכה" to "chanukah", "עשרה בטבת" to "tevet10", "טו בשבט" to "tubishvat",
        "תענית אסתר" to "esther", "פורים" to "purim", "פסח" to "pesach", "יום העצמאות" to "atzmaut", "לג בעומר" to "lagbaomer",
        "יום ירושלים" to "yerushalayim", "שבועות" to "shavuot", "יז בתמוז" to "tammuz17", "שבעה עשר בתמוז" to "tammuz17",
        "תשעה באב" to "av9", "תשעה באב" to "av9", "ט באב" to "av9")
    val HOLIDAY_NAMES = mapOf("rosh_hashana" to "ראש השנה", "gedalia" to "צום גדליה", "kippur" to "יום כיפור", "sukkot" to "סוכות",
        "simchat_torah" to "שמחת תורה", "chanukah" to "חנוכה", "tevet10" to "עשרה בטבת", "tubishvat" to "ט״ו בשבט", "esther" to "תענית אסתר",
        "purim" to "פורים", "pesach" to "פסח", "atzmaut" to "יום העצמאות", "lagbaomer" to "ל״ג בעומר", "yerushalayim" to "יום ירושלים",
        "shavuot" to "שבועות", "tammuz17" to "י״ז בתמוז", "av9" to "תשעה באב")

    // המופע הבא של החג (היום או אחריו)
    fun nextHoliday(key: String, today: LocalDate): LocalDate? {
        val y = of(today).y
        for (yy in y..y + 1) holiday(key, yy)?.let { if (it >= rd(today)) return date(it) }
        return null
    }

    // ראש החודש הבא (יום אחד או שניים)
    fun nextRoshChodesh(today: LocalDate): Pair<HDate, List<LocalDate>> {
        val h = of(today)
        var y = h.y; var m = h.m
        if (h.d == 1 || h.d == 30) return HDate(y, m, 1) to listOfNotNull(if (h.d == 30) today else null, if (h.d == 30) today.plusDays(1) else today)
        m = if (m == lastMonth(y)) 1 else m + 1
        if (m == 7) y++
        val first = date(fixed(y, m, 1))
        val prevM = if (m == 1) lastMonth(y) else if (m == 7) lastMonth(y - 1) else m - 1
        val prevY = if (m == 7) y - 1 else y
        val days = if (monthDays(prevM, prevY) == 30) listOf(first.minusDays(1), first) else listOf(first)
        return HDate(y, m, 1) to days
    }

    // ספירת העומר: היום ה-N (בספירה של הלילה שעבר); null מחוץ לימי הספירה
    fun omerDay(today: LocalDate): Int? {
        val h = of(today)
        val n = (rd(today) - fixed(h.y, 1, 16) + 1).toInt()
        return if (n in 1..49) n else null
    }

    // מולד החודש הבא: יום בשבוע, שעה, דקות, חלקים (שעון ירושלים הממוצע, מחצות)
    fun nextMolad(today: LocalDate): Triple<HDate, LocalDate, Triple<Int, Int, Int>> {
        val h = of(today)
        var y = h.y; var m = if (h.m == lastMonth(y)) 1 else h.m + 1
        if (m == 7) y++
        val yy = if (m < 7) y + 1 else y
        val monthsElapsed = (m - 7) + Math.floorDiv(235L * yy - 234, 19L)
        // ברגעים: ימים + חלקים (25920 חלקים ביממה)
        val totalParts = (EPOCH * 25920L - 876L) + monthsElapsed * (29L * 25920 + 13753L)
        val day = Math.floorDiv(totalParts, 25920L)
        val rem = Math.floorMod(totalParts, 25920L)
        val hour = (rem / 1080).toInt(); val p = (rem % 1080).toInt()
        return Triple(HDate(y, m, 1), date(day), Triple(hour, p / 18, p % 18))
    }

    // ---------- פרשת השבוע (ארץ ישראל) ----------
    val PARSHIOT = listOf("בראשית", "נח", "לך לך", "וירא", "חיי שרה", "תולדות", "ויצא", "וישלח", "וישב", "מקץ", "ויגש", "ויחי",
        "שמות", "וארא", "בא", "בשלח", "יתרו", "משפטים", "תרומה", "תצוה", "כי תשא", "ויקהל", "פקודי",
        "ויקרא", "צו", "שמיני", "תזריע", "מצורע", "אחרי מות", "קדושים", "אמור", "בהר", "בחוקותי",
        "במדבר", "נשא", "בהעלותך", "שלח", "קרח", "חוקת", "בלק", "פנחס", "מטות", "מסעי",
        "דברים", "ואתחנן", "עקב", "ראה", "שופטים", "כי תצא", "כי תבוא", "נצבים", "וילך", "האזינו")

    private fun holidayShabbat(rd: Long): String? {
        val h = fromRd(rd)
        return when {
            h.m == 7 && h.d in 1..2 -> "ראש השנה"
            h.m == 7 && h.d == 10 -> "יום כיפור"
            h.m == 7 && h.d in 15..21 -> if (h.d == 15) "סוכות" else "שבת חול המועד סוכות"
            h.m == 7 && h.d == 22 -> "שמיני עצרת ושמחת תורה"
            h.m == 1 && h.d in 15..21 -> if (h.d == 15 || h.d == 21) "פסח" else "שבת חול המועד פסח"
            h.m == 3 && h.d == 6 -> "שבועות"
            else -> null
        }
    }

    private class Seg(val first: Int, val last: Int, val combine: List<Int>) // combine: אינדקס של הפרשה הראשונה בזוג

    fun parasha(shabbat: LocalDate): String {
        val rd = rd(shabbat)
        holidayShabbat(rd)?.let { return it }
        var y = fromRd(rd).y
        if (rd <= fixed(y, 7, 22)) y--               // מחזור הקריאה מתחיל אחרי שמחת תורה
        // כל השבתות במחזור, בלי שבתות חג
        var s = fixed(y, 7, 22) + 1
        while (date(s).dayOfWeek != DayOfWeek.SATURDAY) s++
        val end = fixed(y + 1, 7, 22)
        val sh = ArrayList<Long>()
        while (s < end) { if (holidayShabbat(s) == null) sh += s; s += 7 }
        fun lastBefore(limit: Long) = sh.indexOfLast { it < limit }
        // ניסן–אב של המחזור שייכים לאותה שנה עברית y (השנה מתחלפת בתשרי)
        val lp = leap(y)
        val a1 = lastBefore(fixed(y, 1, 15))     // לפני פסח: צו (פשוטה) / מצורע (מעוברת)
        val a2 = lastBefore(fixed(y, 3, 6))      // לפני שבועות: במדבר
        val a3 = lastBefore(fixed(y, 5, 10))     // לפני תשעה באב (או בו): דברים
        val segs = listOf(
            Seg(0, if (lp) 27 else 24, if (lp) emptyList() else listOf(21)),
            Seg(if (lp) 28 else 25, 33, listOf(26, 28, 31)),
            Seg(34, 43, listOf(41, 38)),
            Seg(44, 52, listOf(50)))
        val bounds = listOf(-1, a1, a2, a3, sh.size - 1)
        val idx = sh.indexOf(rd)
        if (idx < 0) return "?"
        for (k in 0 until 4) {
            val from = bounds[k] + 1; val to = bounds[k + 1]
            if (idx !in from..to) continue
            val slots = to - from + 1
            val seg = segs[k]
            val readings = (seg.first..seg.last).map { listOf(it) }.toMutableList()
            for (c in seg.combine) {
                if (readings.size <= slots) break
                val i = readings.indexOfFirst { it == listOf(c) }
                if (i >= 0 && i + 1 < readings.size && readings[i + 1] == listOf(c + 1)) { readings[i] = listOf(c, c + 1); readings.removeAt(i + 1) }
            }
            val r = readings.getOrNull(idx - from) ?: return "?"
            return r.joinToString("-") { PARSHIOT[it] }
        }
        return "?"
    }
}
