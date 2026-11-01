package ir.shiftapp.income

import ir.shiftapp.core.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeTest {
    private fun day(y: Int, m: Int, d: Int) = JalaliDate(y, m, d).toLocalDate().toEpochDay().toInt()

    @Test fun formatAndParse() {
        assertEquals("۱٬۲۳۴٬۵۶۷", IncomeStats.format(1234567))
        assertEquals("۰", IncomeStats.format(0))
        assertEquals("۹۹۹", IncomeStats.format(999))
        assertEquals(1234567L, IncomeStats.parse("۱٬۲۳۴٬۵۶۷"))
        assertEquals(1234567L, IncomeStats.parse("1,234,567 تومان"))
        assertNull(IncomeStats.parse(""))
        assertNull(IncomeStats.parse("۰۰۰"))
        assertNull(IncomeStats.parse("1234567890123"))
    }

    @Test fun monthAndYearTotals() {
        val l = listOf(
            IncomeEntry(1, day(1405, 7, 1), "حقوق مهر", 30_000_000, 0),
            IncomeEntry(2, day(1405, 7, 20), "اضافه‌کار", 5_000_000, 1),
            IncomeEntry(3, day(1405, 8, 1), "حقوق آبان", 31_000_000, 0),
            IncomeEntry(4, day(1404, 7, 5), "پارسال", 9_000_000, 2)
        )
        val mehr = IncomeStats.inMonth(l, 1405, 7)
        assertEquals(listOf(2L, 1L), mehr.map { it.id })                 // جدیدترین بالا
        assertEquals(35_000_000L, IncomeStats.total(mehr))
        assertEquals(listOf(0 to 30_000_000L, 1 to 5_000_000L), IncomeStats.byKind(mehr))
        val yr = IncomeStats.yearTotals(l, 1405)
        assertEquals(35_000_000L, yr[6]); assertEquals(31_000_000L, yr[7]); assertEquals(0L, yr[0])
    }

    @Test fun codecRoundTripAndGarbage() {
        val st = IncomeState(listOf(IncomeEntry(1, 20000, "پاداش", 1_500_000, 2, "عیدی \"ویژه\""), IncomeEntry(2, 20001, "حقوق", 7, 0)))
        assertEquals(st, IncomeCodec.decode(IncomeCodec.encode(st)))
        assertEquals(IncomeState(), IncomeCodec.decode("garbage"))
        assertEquals(3L, st.nextId())
    }

    @Test fun bonusPointsBothDirections() {
        // ۵۰ هزار امتیاز × ۷۹۲۹ ریال (۷۹۲٫۹ تومان) = ۳۹٬۶۴۵٬۰۰۰ تومان
        assertEquals(39_645_000L, IncomeStats.amountFromPoints(50_000.0, 7929))
        // ۳۴ میلیون تومان واریزی ÷ ۷۹۲٫۹ = ۴۲٬۸۸۰٫۵۷ امتیاز
        assertEquals(42880.565, IncomeStats.pointsFromAmount(34_000_000, 7929), 0.001)
        assertEquals("۴۲٬۸۸۰٫۵۷", IncomeStats.formatPoints(IncomeStats.pointsFromAmount(34_000_000, 7929)))
        assertEquals("۷۹۲٫۹", IncomeStats.formatPoints(7929 / 10.0))
        assertEquals("۵۰٬۰۰۰", IncomeStats.formatPoints(50_000.0))
        assertEquals(0.0, IncomeStats.pointsFromAmount(1000, 0), 0.0)
    }

    @Test fun parseDecimal() {
        assertEquals(42880.57, IncomeStats.parseDecimal("۴۲٬۸۸۰٫۵۷")!!, 1e-9)
        assertEquals(50000.0, IncomeStats.parseDecimal("50,000")!!, 0.0)
        assertEquals(0.5, IncomeStats.parseDecimal(".5")!!, 0.0)
        assertNull(IncomeStats.parseDecimal(""))
        assertNull(IncomeStats.parseDecimal("0"))
        assertNull(IncomeStats.parseDecimal("1.2.3"))
    }

    @Test fun pointsSurviveCodecAndOldRowsLoad() {
        val st = IncomeState(listOf(IncomeEntry(1, 20000, "راندمان مهر", 39_645_000, KIND_POINTS, "", 50_000.0, 7929)))
        assertEquals(st, IncomeCodec.decode(IncomeCodec.encode(st)))
        val old = """{"e":[[1,20000,"حقوق",100,0,""]]}"""          // ذخیرهٔ قدیمی بدون امتیاز
        val e = IncomeCodec.decode(old).entries.single()
        assertEquals(0.0, e.points, 0.0); assertEquals(0L, e.rialPerPoint)
    }
}
