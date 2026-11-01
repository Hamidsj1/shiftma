package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftProgressTest {
    private val morning = 1 to ShiftTime(360, 840)      // ۰۶:۰۰ تا ۱۴:۰۰
    private val night = 3 to ShiftTime(1320, 360)       // ۲۲:۰۰ تا ۰۶:۰۰ فردا

    @Test fun insideDayShift() {
        val r = ShiftProgress.compute(600, null, morning)!!
        assertEquals(1, r.code); assertEquals(240, r.elapsedMin); assertEquals(480, r.totalMin); assertEquals(240, r.remainingMin)
        assertEquals(0.5f, r.fraction, 0.001f)
    }

    @Test fun outsideDayShiftIsNull() {
        assertNull(ShiftProgress.compute(300, null, morning))
        assertNull(ShiftProgress.compute(840, null, morning))
    }

    @Test fun nightShiftBeforeMidnightUsesToday() {
        val r = ShiftProgress.compute(1380, null, night)!!
        assertEquals(60, r.elapsedMin); assertEquals(480, r.totalMin)
    }

    @Test fun nightShiftAfterMidnightUsesYesterday() {
        val r = ShiftProgress.compute(120, night, null)!!
        assertEquals(3, r.code); assertEquals(240, r.elapsedMin); assertEquals(240, r.remainingMin)
        assertNull(ShiftProgress.compute(400, night, null))
    }

    @Test fun yesterdaysNightWinsOverTodaysMorningInTheEarlyHours() {
        assertEquals(3, ShiftProgress.compute(100, night, morning)!!.code)
        assertEquals(1, ShiftProgress.compute(400, night, morning)!!.code)
    }

    @Test fun clockText() {
        assertEquals("۴:۳۷", ShiftProgress.clock(277))
        assertEquals("۰:۰۵", ShiftProgress.clock(5))
    }
}
