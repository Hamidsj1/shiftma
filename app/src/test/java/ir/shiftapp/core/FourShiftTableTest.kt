package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class FourShiftTableTest {
    // الگوی ۸روزهٔ نمونهٔ تصویر: استراحت، استراحت، روزکار، روزکار، استراحت، شبکار، شبکار، استراحت
    private val cycle = ShiftPattern.of(listOf(4, 4, 1, 1, 4, 3, 3, 4))
    private val base = JalaliDate(1405, 7, 1)

    private fun col(id: Long, b: JalaliDate = base, e: Map<String, List<Entry>> = emptyMap()) =
        FourShiftColumn(id, "ش$id", "برق فاز 2", b, cycle, e)

    @Test fun mehr1405LayoutMatchesApp() {
        // در نمونه: ۱ مهر ۱۴۰۵ چهارشنبه (شنبه=0 → چهارشنبه=4) و ۱۳ مهر دوشنبه است
        assertEquals(4, JalaliDate(1405, 7, 1).dayOfWeek)
        assertEquals(2, JalaliDate(1405, 7, 13).dayOfWeek)
        val rows = FourShiftTable.build(1405, 7, listOf(col(1), null, null, null))
        assertEquals(30, rows.size)
        assertEquals(JalaliDate(1405, 7, 1), rows.first().date)
    }

    @Test fun emptyColumnsAreNull() {
        val row = FourShiftTable.build(1405, 7, listOf(col(1), null, col(3), null)).first()
        assertNotNull(row.cells[0]); assertNull(row.cells[1]); assertNotNull(row.cells[2]); assertNull(row.cells[3])
    }

    @Test fun columnsUseTheirOwnBaseDate() {
        val rows = FourShiftTable.build(1405, 7, listOf(col(1), col(2, base.plusDays(2)), null, null))
        // ستون ۲ دو روز دیرتر شروع می‌کند: روز ۳ ام ستون ۲ = اولین عنصر چرخه
        assertEquals(cycle.codes[0], rows[0].cells[0]!!.effectiveCode)
        assertEquals(cycle.codes[0], rows[2].cells[1]!!.effectiveCode)
        // قبل از مبنا هم درست محاسبه می‌شود (باقیمانده مثبت)
        assertEquals(cycle.codes[6], rows[0].cells[1]!!.effectiveCode)
    }

    @Test fun overridesApplyPerColumnOnly() {
        val key = JalaliDate(1405, 7, 5).key
        val rows = FourShiftTable.build(1405, 7, listOf(
            col(1, e = mapOf(key to listOf(Entry(EntryType.EXCHANGE, shiftCode = 1)))), col(2), null, null))
        assertTrue(rows[4].cells[0]!!.overridden); assertEquals(1, rows[4].cells[0]!!.effectiveCode)
        assertFalse(rows[4].cells[1]!!.overridden)
    }

    @Test(expected = IllegalArgumentException::class) fun wrongColumnCountRejected() {
        FourShiftTable.build(1405, 7, listOf(col(1)))
    }
}
