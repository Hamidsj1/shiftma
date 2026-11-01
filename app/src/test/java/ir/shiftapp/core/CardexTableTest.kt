package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class CardexTableTest {
    // چرخهٔ ۸روزهٔ خوانده‌شده از تصویر نمونه (نام شیفت دلخواه است): استراحت، روزکار×۲، عصرکار×۲، استراحت، شبکار×۲
    private val pattern = ShiftPattern.of(listOf(4, 1, 1, 2, 2, 4, 3, 3))
    private val base = JalaliDate(1405, 7, 1)

    @Test fun columnOrderMatchesApp() {
        assertEquals(listOf(EntryType.NOTE, EntryType.REST, EntryType.LEAVE, EntryType.EXCHANGE, EntryType.OVERTIME), CardexTable.COLUMNS)
    }

    @Test fun rowsCoverWholeMonthWithPatternShifts() {
        val rows = CardexTable.build(1405, 7, base, pattern, emptyMap())
        assertEquals(30, rows.size)
        assertEquals(pattern.codes[0], rows[0].day.effectiveCode)
        assertEquals(pattern.codes[7], rows[7].day.effectiveCode)
        assertEquals(pattern.codes[0], rows[8].day.effectiveCode)      // چرخهٔ ۸روزه تکرار می‌شود
        assertTrue(rows.none { it.has(EntryType.NOTE) })
    }

    @Test fun matchesScreenshotDays1To12() {
        // تصویر: ۱ مهر ۱۴۰۵ تا ۱۲ مهر
        val expected = listOf(4, 1, 1, 2, 2, 4, 3, 3, 4, 1, 1, 2)
        val rows = CardexTable.build(1405, 7, base, pattern, emptyMap())
        assertEquals(expected, rows.take(12).map { it.day.effectiveCode })
    }

    @Test fun eventsAppearOnTheirDayOnly() {
        val k = JalaliDate(1405, 7, 3).key
        val rows = CardexTable.build(1405, 7, base, pattern, mapOf(k to listOf(
            Entry(EntryType.NOTE, text = "جلسه"), Entry(EntryType.OVERTIME, minutes = 90), Entry(EntryType.OVERTIME, minutes = 30))))
        assertTrue(rows[2].has(EntryType.NOTE)); assertEquals(2, rows[2].count(EntryType.OVERTIME))
        assertEquals(120, rows[2].day.overtimeMinutes)
        assertFalse(rows[1].has(EntryType.NOTE)); assertFalse(rows[3].has(EntryType.OVERTIME))
    }

    @Test fun exchangeChangesEffectiveShiftButNotComputed() {
        val k = JalaliDate(1405, 7, 1).key
        val row = CardexTable.build(1405, 7, base, pattern, mapOf(k to listOf(Entry(EntryType.EXCHANGE, shiftCode = 3)))).first()
        assertTrue(row.has(EntryType.EXCHANGE)); assertEquals(3, row.day.effectiveCode)
        assertEquals(pattern.codes[0], row.day.computedCode); assertTrue(row.day.overridden)
    }
}
