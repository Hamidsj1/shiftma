package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class ShiftCalculatorTest {
    private fun d(y: Int, m: Int, dd: Int) = JalaliDate(y, m, dd)

    /** ۲۰۰ بردار که از اجرای bytecode واقعی APK (کلاس ex.g) به دست آمده‌اند. هدف: Mismatch = 0 */
    @Test fun matchesApkGoldenVectors() {
        val lines = javaClass.classLoader!!.getResourceAsStream("golden_apk_vectors.csv")!!
            .bufferedReader().readLines().drop(1).filter { it.isNotBlank() }
        assertTrue(lines.size >= 100)
        var mismatch = 0
        for (l in lines) {
            val c = l.split(',')
            val base = d(c[0].toInt(), c[1].toInt(), c[2].toInt())
            val target = d(c[3].toInt(), c[4].toInt(), c[5].toInt())
            val got = ShiftCalculator.calculateShift(base, target, ShiftPattern.parse(c[6]))
            if (got != c[7].toInt()) mismatch++
        }
        assertEquals(0, mismatch)
    }

    @Test fun leapYearsAgreeWithApkRuleIn1397To1633() {
        fun apkLeap(y: Int) = Math.floorMod(y, 33) in setOf(1, 5, 9, 13, 17, 22, 26, 30)
        for (y in 1397..1633) assertEquals("year $y", apkLeap(y), JalaliCalendar.isLeap(y))
    }

    @Test fun consecutiveDaysAcrossYearBoundaries() {
        assertEquals(1, d(1398, 1, 1).toJdn() - d(1397, 12, 29).toJdn())
        assertEquals(1, d(1399, 12, 30).toJdn() - d(1399, 12, 29).toJdn())
        assertEquals(1, d(1400, 1, 1).toJdn() - d(1399, 12, 30).toJdn())
    }

    @Test fun invalidDatesRejected() {
        assertFalse(d(1398, 12, 30).isValid)      // APK: بی‌صدا برابر ۱۳۹۹/۱/۱ می‌شد
        assertTrue(d(1399, 12, 30).isValid)
        assertFalse(d(1400, 13, 1).isValid)
        assertThrows(IllegalArgumentException::class.java) { d(1398, 12, 30).toJdn() }
    }

    @Test fun datesBeforeOldEpochAreCalculatedProperly() {
        val p = ShiftPattern.of(listOf(1, 2, 3, 4))
        val base = d(1397, 1, 1)
        assertEquals(4, ShiftCalculator.calculateShift(base, d(1396, 12, 29), p))
        assertEquals(3, ShiftCalculator.calculateShift(base, d(1396, 12, 28), p))
        val far = d(1390, 1, 1)
        assertEquals(p.codes[Math.floorMod(far.toJdn() - base.toJdn(), 4)], ShiftCalculator.calculateShift(base, far, p))
    }

    @Test fun negativeDiffUsesFloorMod() {
        val p = ShiftPattern.of(listOf(1, 2, 3, 4))
        val base = d(1400, 1, 1)
        assertEquals(4, ShiftCalculator.calculateShift(base, d(1399, 12, 30), p))
        assertEquals(4, ShiftCalculator.calculateShift(base, d(1399, 12, 26), p))
        assertEquals(3, ShiftCalculator.calculateShift(base, d(1399, 12, 25), p))
    }

    @Test fun emptyOrBadPatternRejected() {
        assertThrows(IllegalArgumentException::class.java) { ShiftPattern.of(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ShiftPattern.parse("1;x;2") }
        assertThrows(IllegalArgumentException::class.java) { ShiftPattern.parse("") }
    }

    @Test fun todayConversionAndRoundTrip() {
        assertEquals(d(1405, 7, 13), JalaliCalendar.fromLocalDate(java.time.LocalDate.of(2026, 10, 5)))
        for (y in 1380..1450) for (m in 1..12) for (dd in 1..JalaliCalendar.daysInMonth(y, m)) {
            val x = d(y, m, dd); assertEquals(x, JalaliCalendar.fromJdn(x.toJdn()))
        }
    }

    @Test fun weekday() {
        assertEquals(4, d(1397, 1, 1).dayOfWeek) // چهارشنبه، همان نتیجهٔ ex.c در APK
    }
}
