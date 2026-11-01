package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateSpanTest {
    private fun d(y: Int, m: Int, day: Int) = JalaliDate(y, m, day)

    @Test fun fullYearInclusive() {
        val r = DateSpan.compute(d(1400, 1, 1), d(1400, 12, 29))!!
        assertEquals(1, r.years); assertEquals(0, r.months); assertEquals(0, r.days)
        assertEquals(365, r.totalDays)
        assertEquals(1, r.insYears); assertEquals(0, r.insMonths); assertEquals(0, r.insDays)
    }

    @Test fun sameDayIsOneDayInclusiveZeroExclusive() {
        assertEquals(1, DateSpan.compute(d(1400, 5, 10), d(1400, 5, 10), true)!!.totalDays)
        val z = DateSpan.compute(d(1400, 5, 10), d(1400, 5, 10), false)!!
        assertEquals(0, z.totalDays); assertEquals(0, z.days); assertEquals(0, z.insDays)
    }

    @Test fun monthsAndDaysBorrowFromPreviousMonth() {
        val r = DateSpan.compute(d(1400, 1, 20), d(1400, 3, 10), false)!!
        assertEquals(0, r.years); assertEquals(1, r.months); assertEquals(21, r.days)   // ۲۰ فروردین تا ۲۰ اردیبهشت یک ماه، بعد تا ۱۰ خرداد ۲۱ روز
    }

    @Test fun acrossYearsAndThirtyDayMonths() {
        val r = DateSpan.compute(d(1390, 1, 1), d(1392, 6, 31))!!
        assertEquals(2, r.years); assertEquals(6, r.months); assertEquals(0, r.days)
        assertEquals(2, r.insYears); assertEquals(6, r.insMonths)
    }

    @Test fun endBeforeStartIsNull() {
        assertNull(DateSpan.compute(d(1400, 1, 2), d(1400, 1, 1)))
    }
}
