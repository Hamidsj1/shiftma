package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class DayResolverTest {
    private val base = JalaliDate(1400, 1, 1)
    private val pattern = ShiftPattern.of(listOf(1, 2, 3, 4))

    @Test fun overridesDoNotChangeComputedCode() {
        val date = JalaliDate(1400, 1, 2) // computed = 2
        val r = DayResolver.resolve(base, pattern, date, listOf(Entry(EntryType.EXCHANGE, shiftCode = 3)))
        assertEquals(2, r.computedCode); assertEquals(3, r.effectiveCode); assertTrue(r.overridden)
    }

    @Test fun lastOverrideWins() {
        val r = DayResolver.resolve(base, pattern, base, listOf(
            Entry(EntryType.MANUAL_SHIFT, shiftCode = 2), Entry(EntryType.EXCHANGE, shiftCode = 4)))
        assertEquals(4, r.effectiveCode)
    }

    @Test fun monthReportCounts() {
        val entries = mapOf(
            JalaliDate(1400, 1, 1).key to listOf(Entry(EntryType.LEAVE), Entry(EntryType.OVERTIME, minutes = 90)),
            JalaliDate(1400, 1, 2).key to listOf(Entry(EntryType.EXCHANGE, shiftCode = 1))
        )
        val rep = ReportCalculator.month(1400, 1, base, pattern, entries)
        assertEquals(31, rep.daysByShift.values.sum())
        assertEquals(1, rep.leaveDays); assertEquals(90, rep.overtimeMinutes); assertEquals(1, rep.exchangeCount)
        // 31 روز: چرخهٔ ۴تایی => 8 دوره کامل + 3 روز(1,2,3). روز دوم با تعویض شد 1
        assertEquals(9, rep.daysByShift[1]); assertEquals(7, rep.daysByShift[2]); assertEquals(8, rep.daysByShift[3]); assertEquals(7, rep.daysByShift[4])
    }
}
