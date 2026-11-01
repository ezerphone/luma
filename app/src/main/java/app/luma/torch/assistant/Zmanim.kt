package app.luma.torch.assistant

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.*

// זמני היום בלי אינטרנט: חישוב אסטרונומי לפי עיר שנבחרה (זמנים משוערים, שיטת הגר"א; צאת הכוכבים 8.5°, עלות השחר 16.1°).
object Zmanim {
    class City(val name: String, val lat: Double, val lon: Double, val candles: Int)

    val CITIES = listOf(
        City("ירושלים", 31.778, 35.235, 40), City("בני ברק", 32.084, 34.834, 20), City("תל אביב", 32.080, 34.781, 20),
        City("חיפה", 32.794, 34.990, 30), City("בית שמש", 31.747, 34.988, 20), City("מודיעין עילית", 31.933, 35.043, 20),
        City("ביתר עילית", 31.698, 35.116, 40), City("אלעד", 32.052, 34.951, 20), City("פתח תקווה", 32.088, 34.887, 20),
        City("אשדוד", 31.804, 34.655, 20), City("נתניה", 32.321, 34.853, 20), City("צפת", 32.965, 35.496, 20),
        City("טבריה", 32.795, 35.531, 20), City("באר שבע", 31.252, 34.791, 20), City("רחובות", 31.894, 34.811, 20), City("קריית גת", 31.610, 34.764, 20))

    fun city(c: Context) = CITIES[Assistant.prefs(c).getInt("city", 0).coerceIn(0, CITIES.size - 1)]

    // זמן עלייה/שקיעה של השמש לזווית נתונה מתחת לאופק (Almanac for Computers)
    private fun sun(date: LocalDate, cy: City, below: Double, rising: Boolean): LocalDateTime? {
        val zen = Math.toRadians(90 + below)
        val lngHour = cy.lon / 15
        val t = date.dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
        l = (l % 360 + 360) % 360
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = (ra % 360 + 360) % 360
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(zen) - sinDec * sin(Math.toRadians(cy.lat))) / (cosDec * cos(Math.toRadians(cy.lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val tt = h + ra - 0.06571 * t - 6.622
        val ut = ((tt - lngHour) % 24 + 24) % 24
        val secs = (ut * 3600).roundToLong()
        return date.atStartOfDay().plusSeconds(secs).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }

    private fun sunrise(d: LocalDate, cy: City) = sun(d, cy, 0.833, true)
    private fun sunset(d: LocalDate, cy: City) = sun(d, cy, 0.833, false)
    private fun tzeit(d: LocalDate, cy: City) = sun(d, cy, 8.5, false)
    private fun alot(d: LocalDate, cy: City) = sun(d, cy, 16.1, true)
    private fun hour(d: LocalDate, cy: City, h: Double): LocalDateTime? {
        val r = sunrise(d, cy) ?: return null; val s = sunset(d, cy) ?: return null
        val len = java.time.Duration.between(r, s).seconds / 12.0
        return r.plusSeconds((len * h).roundToLong())
    }

    private fun hm(t: LocalDateTime?) = t?.let { Assistant.hm(it) } ?: "?"

    fun answer(c: Context, zman: String, dayOffset: Int): String {
        val cy = city(c)
        val now = LocalDateTime.now()
        val d = now.toLocalDate().plusDays(dayOffset.toLong())
        val which = if (dayOffset == 1) "מחר" else "היום"
        val friday = if (now.dayOfWeek == DayOfWeek.FRIDAY && (sunset(now.toLocalDate(), cy)?.isAfter(now) == true)) now.toLocalDate()
                     else now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        val saturday = if (now.dayOfWeek == DayOfWeek.SATURDAY && (tzeit(now.toLocalDate(), cy)?.isAfter(now) == true)) now.toLocalDate()
                       else now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        val text = when (zman) {
            "sunset" -> "השקיעה $which ב-${hm(sunset(d, cy))}"
            "sunrise" -> "הזריחה $which ב-${hm(sunrise(d, cy))}"
            "candles" -> "הדלקת נרות ב-${hm(sunset(friday, cy)?.minusMinutes(cy.candles.toLong()))}"
            "shabbat_end" -> "צאת השבת ב-${hm(tzeit(saturday, cy))}"
            "chatzot" -> "חצות היום $which ב-${hm(hour(d, cy, 6.0))}"
            "chatzot_night" -> "חצות הלילה ב-${hm(hour(d, cy, 6.0)?.plusHours(12))}"
            "dawn" -> "עלות השחר $which ב-${hm(alot(d, cy))}"
            "shema" -> "סוף זמן קריאת שמע $which ב-${hm(hour(d, cy, 3.0))}"
            "tefila" -> "סוף זמן תפילה $which ב-${hm(hour(d, cy, 4.0))}"
            "mincha" -> "מנחה גדולה $which ב-${hm(hour(d, cy, 6.5))}"
            "plag" -> "פלג המנחה $which ב-${hm(hour(d, cy, 10.75))}"
            "fast_start" -> "תחילת הצום (עלות השחר) $which ב-${hm(alot(d, cy))}"
            "fast_end" -> "סוף הצום (צאת הכוכבים) $which ב-${hm(tzeit(d, cy))}"
            else -> "זמני $which: זריחה ${hm(sunrise(d, cy))}, סוף קריאת שמע ${hm(hour(d, cy, 3.0))}, חצות ${hm(hour(d, cy, 6.0))}, " +
                    "שקיעה ${hm(sunset(d, cy))}, צאת הכוכבים ${hm(tzeit(d, cy))}"
        }
        return "$text (לפי ${cy.name}, בערך)"
    }
}
