package ir.shiftapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

/** وقت‌های قابل‌نمایش. فهرست نمایشیِ هر روش در [PrayerMethod.shown] است. */
enum class Prayer(val id: String, val title: String) {
    FAJR("fajr", "اذان صبح"),
    SUNRISE("sunrise", "طلوع آفتاب"),
    DHUHR("dhuhr", "اذان ظهر"),
    ASR("asr", "اذان عصر"),
    SUNSET("sunset", "غروب آفتاب"),
    MAGHRIB("maghrib", "اذان مغرب"),
    ISHA("isha", "اذان عشا"),
    MIDNIGHT("midnight", "نیمه‌شب شرعی");

    companion object { fun byId(id: String): Prayer? = values().firstOrNull { it.id == id } }
}

/**
 * روش محاسبه. زاویه‌ها «افت خورشید زیر افق» بر حسب درجه‌اند.
 * شیعه: مؤسسهٔ ژئوفیزیک دانشگاه تهران (صبح ۱۷٫۷°، مغرب ۴٫۵°)، نیمه‌شب = میانهٔ غروب تا اذان صبح بعد.
 * اهل سنت: رابطهٔ جهان اسلام (صبح ۱۸°، عشا ۱۷°)، عصر با سایهٔ هم‌اندازه، مغرب = غروب.
 */
data class PrayerMethod(
    val id: String, val title: String,
    val fajrAngle: Double, val ishaAngle: Double?, val maghribAngle: Double?, val asrFactor: Int?,
    val shown: List<Prayer>
) {
    companion object {
        val SHIA = PrayerMethod(
            "shia", "شیعه", 17.7, null, 4.5, null,
            listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.SUNSET, Prayer.MAGHRIB, Prayer.MIDNIGHT)
        )
        val SUNNI = PrayerMethod(
            "sunni", "اهل سنت", 18.0, 17.0, null, 1,
            listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        )
        val ALL = listOf(SHIA, SUNNI)
        fun byId(id: String?): PrayerMethod = ALL.firstOrNull { it.id == id } ?: SHIA
    }
}

/** وقت‌های یک روز؛ مقدار = دقیقه از نیمه‌شبِ همان روز (نیمه‌شب شرعی ممکن است بیشتر از ۱۴۴۰ باشد). */
data class DayTimes(val date: LocalDate, val minutes: Map<Prayer, Int>) {
    fun atMs(p: Prayer, zone: ZoneId): Long? = minutes[p]?.let { SingleAlarm.at(date, it, zone) }
}

data class NextPrayer(val prayer: Prayer, val atMs: Long, val minuteOfDay: Int)

/** محاسبهٔ نجومی بدون اینترنت (روش PrayTimes). خطا در حد یک‌دو دقیقه نسبت به تقویم‌های رسمی. */
object PrayerCalc {
    private const val D2R = PI / 180.0
    private fun sind(d: Double) = sin(d * D2R)
    private fun cosd(d: Double) = cos(d * D2R)
    private fun fix(a: Double, m: Double) = a - m * floor(a / m)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private class Solar(date: LocalDate, val lat: Double, val lon: Double) {
        private val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lon / (15.0 * 24.0)

        /** (میل خورشید به درجه، معادلهٔ زمان به ساعت) */
        private fun pos(t: Double): Pair<Double, Double> {
            val d = jd + t - 2451545.0
            val g = fix(357.529 + 0.98560028 * d, 360.0)
            val q = fix(280.459 + 0.98564736 * d, 360.0)
            val l = fix(q + 1.915 * sind(g) + 0.020 * sind(2 * g), 360.0)
            val e = 23.439 - 0.00000036 * d
            val ra = fix(atan2(cosd(e) * sind(l), cosd(l)) / D2R / 15.0, 24.0)
            return asin(sind(e) * sind(l)) / D2R to (q / 15.0 - ra)
        }

        fun noon(t: Double): Double = fix(12.0 - pos(t).second, 24.0)

        /** ساعت (خورشیدی) رسیدن خورشید به افتِ [angle]؛ NaN اگر در این عرض جغرافیایی رخ ندهد. */
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val dec = pos(t).first
            val v = (-sind(angle) - sind(dec) * sind(lat)) / (cosd(dec) * cosd(lat))
            if (v.isNaN() || v < -1.0 || v > 1.0) return Double.NaN
            val h = acos(v) / D2R / 15.0
            return if (ccw) noon(t) - h else noon(t) + h
        }

        fun asr(factor: Int, t: Double): Double {
            val dec = pos(t).first
            val ang = -atan(1.0 / (factor + tan(abs(lat - dec) * D2R))) / D2R
            return angleTime(ang, t, false)
        }
    }

    /** اختلاف ساعت محلی با UTC در ظهر همان روز (ساعت)؛ تابستان‌وقت را هم درست می‌گیرد. */
    private fun tzHours(date: LocalDate, zone: ZoneId): Double =
        zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0

    private fun fajrHours(date: LocalDate, lat: Double, lon: Double, tz: Double, m: PrayerMethod): Double =
        Solar(date, lat, lon).angleTime(m.fajrAngle, 5.0 / 24, true) + tz - lon / 15.0

    fun times(date: LocalDate, lat: Double, lon: Double, zone: ZoneId, method: PrayerMethod): DayTimes {
        val tz = tzHours(date, zone)
        val adj = tz - lon / 15.0
        val s = Solar(date, lat, lon)
        val h = HashMap<Prayer, Double>()
        h[Prayer.FAJR] = s.angleTime(method.fajrAngle, 5.0 / 24, true) + adj
        h[Prayer.SUNRISE] = s.angleTime(0.833, 6.0 / 24, true) + adj
        h[Prayer.DHUHR] = s.noon(12.0 / 24) + adj
        method.asrFactor?.let { h[Prayer.ASR] = s.asr(it, 13.0 / 24) + adj }
        val sunset = s.angleTime(0.833, 18.0 / 24, false) + adj
        h[Prayer.SUNSET] = sunset
        h[Prayer.MAGHRIB] = method.maghribAngle?.let { s.angleTime(it, 18.0 / 24, false) + adj } ?: sunset
        method.ishaAngle?.let { h[Prayer.ISHA] = s.angleTime(it, 18.0 / 24, false) + adj }
        if (Prayer.MIDNIGHT in method.shown) {
            val next = date.plusDays(1)
            val nextFajr = fajrHours(next, lat, lon, tzHours(next, zone), method)
            // نیمه‌شب شرعی: میانهٔ غروب تا اذان صبح روز بعد
            h[Prayer.MIDNIGHT] = sunset + (nextFajr + 24.0 - sunset) / 2.0
        }
        val out = HashMap<Prayer, Int>()
        h.forEach { (p, v) -> if (!v.isNaN() && !v.isInfinite()) out[p] = (v * 60.0).roundToInt() }
        return DayTimes(date, out)
    }

    /** نزدیک‌ترین وقتِ نمایشیِ بعد از [nowMs] (فقط برای نمایش «اذان بعدی»؛ بدون توجه به هشدارها). */
    fun next(nowMs: Long, zone: ZoneId, lat: Double, lon: Double, method: PrayerMethod): NextPrayer? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: NextPrayer? = null
        for (i in -1..2) {
            val date = today.plusDays(i.toLong())
            val t = times(date, lat, lon, zone, method)
            for (p in method.shown) {
                val min = t.minutes[p] ?: continue
                val ms = SingleAlarm.at(date, min, zone)
                if (ms <= nowMs) continue
                val cur = best
                if (cur == null || ms < cur.atMs) best = NextPrayer(p, ms, min % 1440)
            }
        }
        return best
    }
}

/** جهت قبله و فاصله تا کعبه (دایرهٔ عظیمه). */
object Qibla {
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262
    private const val D2R = PI / 180.0

    /** زاویه از شمال حقیقی در جهت عقربه‌های ساعت، ۰ تا ۳۶۰. */
    fun bearing(lat: Double, lon: Double): Double {
        val p1 = lat * D2R; val p2 = KAABA_LAT * D2R; val dl = (KAABA_LON - lon) * D2R
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) / D2R + 360.0) % 360.0
    }

    fun distanceKm(lat: Double, lon: Double): Double {
        val p1 = lat * D2R; val p2 = KAABA_LAT * D2R
        val dp = p2 - p1; val dl = (KAABA_LON - lon) * D2R
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(a))
    }

    private val NAMES = listOf("شمال", "شمال شرقی", "شرق", "جنوب شرقی", "جنوب", "جنوب غربی", "غرب", "شمال غربی")
    fun cardinal(deg: Double): String = NAMES[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]

    /** کوچک‌ترین اختلاف زاویه‌ای علامت‌دار (−۱۸۰ تا ۱۸۰). */
    fun diff(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return d
    }
}

/** قواعد کوچک و تست‌پذیر: بازهٔ ساعت (که از نیمه‌شب می‌گذرد) و هم‌پوشانی با شیفت. */
object PrayerRules {
    /** آیا دقیقهٔ [min] در بازهٔ [start, end) است؟ اگر end < start بازه از نیمه‌شب می‌گذرد؛ start == end یعنی بازه‌ای نیست. */
    fun inWindow(min: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> min in start until end
        else -> min >= start || min < end
    }

    /** آیا دقیقهٔ [min] از امروز در ساعت کار است؟ شیفت دیروزِ شب‌گذر تا [ShiftTime.endMin] امروز ادامه دارد. */
    fun inShift(min: Int, today: ShiftTime?, yesterday: ShiftTime?): Boolean {
        if (today != null) {
            if (!today.crossesMidnight) { if (min in today.startMin until today.endMin) return true }
            else if (min >= today.startMin) return true
        }
        if (yesterday != null && yesterday.crossesMidnight && min < yesterday.endMin) return true
        return false
    }

    fun localMinute(ms: Long, zone: ZoneId): Int = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60 + it.minute }

    /** «۲ ساعت و ۱۰ دقیقه» یا «۱۰ دقیقه»؛ برای کمتر از یک دقیقه «کمتر از یک دقیقه». */
    fun remaining(ms: Long): String {
        val min = (ms / 60_000L).toInt()
        return when {
            min <= 0 -> "کمتر از یک دقیقه"
            min < 60 -> "${Fa.num(min)} دقیقه"
            min % 60 == 0 -> "${Fa.num(min / 60)} ساعت"
            else -> "${Fa.num(min / 60)} ساعت و ${Fa.num(min % 60)} دقیقه"
        }
    }
}
