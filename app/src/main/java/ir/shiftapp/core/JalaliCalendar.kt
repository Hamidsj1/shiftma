package ir.shiftapp.core

import java.time.LocalDate

/** تاریخ شمسی. اعتبارسنجی با [isValid] انجام می‌شود (برخلاف APK اصلی که هیچ اعتبارسنجی نداشت). */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {
    val isValid: Boolean get() = JalaliCalendar.isValid(year, month, day)
    fun toJdn(): Int = JalaliCalendar.toJdn(this)
    fun toLocalDate(): LocalDate = JalaliCalendar.toLocalDate(this)
    fun plusDays(n: Int): JalaliDate = JalaliCalendar.fromJdn(toJdn() + n)
    /** شمارهٔ روز هفته؛ شنبه=0 ... جمعه=6 */
    val dayOfWeek: Int get() = Math.floorMod(toJdn() + 2, 7)
    /** کلید ذخیره‌سازی YYYYMMDD (سازگار با RDate در APK اصلی) */
    val key: String get() = "%04d%02d%02d".format(year, month, day)
    override fun compareTo(other: JalaliDate) =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    companion object {
        fun fromKey(k: String) = JalaliDate(k.substring(0, 4).toInt(), k.substring(4, 6).toInt(), k.substring(6, 8).toInt())
        fun today(): JalaliDate = JalaliCalendar.fromLocalDate(LocalDate.now())
    }
}

/**
 * موتور تاریخ شمسی بر پایهٔ شمارهٔ روز ژولینی (JDN) و الگوریتم Borkowski (آرایهٔ break-year
 * همان آرایه‌ای است که در کلاس xw در APK اصلی پیدا شد). یک موتور برای همه‌چیز: هم «امروز» و هم محاسبهٔ شیفت.
 */
object JalaliCalendar {
    const val MIN_YEAR = 1
    const val MAX_YEAR = 3000
    private const val UNIX_EPOCH_JDN = 2440588
    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private class JalCal(val leapRaw: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        require(jy >= BREAKS[0] && jy < BREAKS.last()) { "سال خارج از محدودهٔ پشتیبانی: $jy" }
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (Math.floorMod(jump, 33) / 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((Math.floorMod(n, 33) + 3) / 4)
        if (Math.floorMod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = (gy / 4) - (((gy / 100) + 1) * 3 / 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        val leap = Math.floorMod(Math.floorMod(n + 1, 33) - 1, 4)
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 + (153 * Math.floorMod(gm + 9, 12) + 2) / 5 + gd - 34840408
        d = d - (((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752
        return d
    }

    fun isLeap(year: Int): Boolean = jalCal(year).leapRaw == 0

    fun daysInMonth(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        month == 12 -> if (isLeap(year)) 30 else 29
        else -> throw IllegalArgumentException("ماه نامعتبر: $month")
    }

    fun isValid(y: Int, m: Int, d: Int): Boolean =
        y in MIN_YEAR..MAX_YEAR && m in 1..12 && d in 1..daysInMonth(y, m)

    fun toJdn(date: JalaliDate): Int = toJdn(date.year, date.month, date.day)

    fun toJdn(y: Int, m: Int, d: Int): Int {
        require(isValid(y, m, d)) { "تاریخ نامعتبر: $y/$m/$d" }
        val r = jalCal(y)
        return g2d(r.gy, 3, r.march) + (m - 1) * 31 - (m / 7) * (m - 7) + d - 1
    }

    fun fromJdn(jdn: Int): JalaliDate {
        val gy = LocalDate.ofEpochDay((jdn - UNIX_EPOCH_JDN).toLong()).year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leapRaw == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }

    fun toLocalDate(d: JalaliDate): LocalDate = LocalDate.ofEpochDay((toJdn(d) - UNIX_EPOCH_JDN).toLong())

    fun fromLocalDate(d: LocalDate): JalaliDate = fromJdn((d.toEpochDay() + UNIX_EPOCH_JDN).toInt())
}
