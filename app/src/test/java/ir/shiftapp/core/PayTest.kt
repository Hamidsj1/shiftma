package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class PayTest {
    private fun hr(h: Int, m: Int = 0) = h * 60 + m

    @Test fun nightMinutesOfShifts() {
        assertEquals(hr(8), HoursCalculator.nightMinutes(ShiftTime(hr(19), hr(7))))   // ۱۹ تا ۰۷ → ۲۲ تا ۰۶
        assertEquals(0, HoursCalculator.nightMinutes(ShiftTime(hr(7), hr(19))))
        assertEquals(hr(8), HoursCalculator.nightMinutes(ShiftTime(hr(22), hr(6))))
        assertEquals(hr(6), HoursCalculator.nightMinutes(ShiftTime(0, hr(8))))
        assertEquals(hr(1), HoursCalculator.nightMinutes(ShiftTime(hr(15), hr(23))))
        assertEquals(hr(7), HoursCalculator.nightMinutes(ShiftTime(hr(23), hr(7))))
        assertEquals(hr(8), HoursCalculator.nightMinutes(ShiftTime(hr(6), hr(6))))    // ۲۴ ساعته از ۰۶: فقط شبِ بعدش (۲۲ تا ۰۶)
    }

    @Test fun monthWithNightShiftsCountsNightHours() {
        val times = mapOf(1 to ShiftTime(hr(7), hr(19)), 3 to ShiftTime(hr(19), hr(7)))
        val pattern = ShiftPattern.of(listOf(1, 3, 4))
        val cal = HolidayCalendar(emptyMap(), fridayOff = false)
        val h = HoursCalculator.month(1405, 7, JalaliDate(1405, 7, 1), pattern, emptyMap(), times, cal)
        val nights = h.daysByShift[3] ?: 0
        assertTrue(nights > 0)
        assertEquals(nights * hr(8), h.nightMinutes)
        assertEquals((h.daysByShift[1] ?: 0) * hr(12) + nights * hr(12), h.totalMinutes)
    }

    @Test fun payBreakdown() {
        val h = HoursReport(
            minutesByShift = emptyMap(), daysByShift = emptyMap(), totalMinutes = 12_000,
            officialHolidayMinutes = 0, officialHolidayDays = 0, fridayMinutes = 720, fridayDays = 1,
            overtimeMinutes = 60, shiftsWithoutTime = emptySet(), nightMinutes = 480
        )
        val s = PaySettings(baseMonthly = 22_000_000, shiftPct = 10.0, benefits = 1_000_000, deductionPct = 7.0)
        val r = PayCalculator.compute(h, s, 30)
        assertEquals(100_000L, r.hourly)
        assertEquals(11_314, r.dueMinutes)
        assertEquals(686, r.autoOvertimeMinutes); assertEquals(60, r.manualOvertimeMinutes); assertEquals(746, r.overtimeMinutes)
        val by = r.lines.associate { it.key to it.amount }
        assertEquals(22_000_000L, by["base"]); assertEquals(2_200_000L, by["shift"]); assertEquals(1_740_667L, by["overtime"])
        assertEquals(280_000L, by["night"]); assertEquals(480_000L, by["friday"]); assertEquals(0L, by["holiday"]); assertEquals(1_000_000L, by["benefits"])
        assertEquals(27_700_667L, r.gross); assertEquals(1_939_047L, r.deduction); assertEquals(25_761_620L, r.net)
    }

    @Test fun belowDueHoursHasNoOvertimeAndZeroPercentSkipsLines() {
        val h = HoursReport(emptyMap(), emptyMap(), 9_000, 0, 0, 0, 0, 0, emptySet(), 0)
        val r = PayCalculator.compute(h, PaySettings(baseMonthly = 10_000_000, overtimePct = 0.0), 30)
        assertEquals(0, r.overtimeMinutes)
        assertEquals(10_000_000L, r.gross)
        assertNull(r.lines.firstOrNull { it.key == "shift" }); assertNull(r.lines.firstOrNull { it.key == "benefits" })
    }

    @Test fun manualDueHoursOverridesAuto() {
        val h = HoursReport(emptyMap(), emptyMap(), hr(200), 0, 0, 0, 0, 0, emptySet(), 0)
        val r = PayCalculator.compute(h, PaySettings(baseMonthly = 22_000_000, dueHours = 192.0), 30)
        assertEquals(hr(192), r.dueMinutes); assertEquals(hr(8), r.autoOvertimeMinutes)
        assertEquals(1_120_000L, r.lines.first { it.key == "overtime" }.amount)    // ۸ ساعت × ۱۰۰٬۰۰۰ × ۱٫۴
    }

    @Test fun notConfiguredWithoutBase() {
        assertFalse(PaySettings().configured)
        assertTrue(PaySettings(baseMonthly = 1).configured)
        val r = PayCalculator.compute(HoursReport(emptyMap(), emptyMap(), 100, 0, 0, 0, 0, 0, emptySet()), PaySettings(), 30)
        assertEquals(0L, r.gross)
    }

    @Test fun prefsRoundTripAndBadValues() {
        val p = Prefs(pay = PaySettings(22_000_000, 220.0, 192.0, 40.0, 35.0, 40.0, 40.0, 22.5, 1_500_000, 7.0))
        assertEquals(p, Prefs.from(p.toMap()))
        val bad = Prefs.from(mapOf(PrefKeys.PAY_BASE to "-5", PrefKeys.PAY_DIVISOR to "0", PrefKeys.PAY_OT to "abc", PrefKeys.PAY_DEDUCT to "500", PrefKeys.PAY_NIGHT to "NaN"))
        assertEquals(PaySettings(), bad.pay)
    }
}
