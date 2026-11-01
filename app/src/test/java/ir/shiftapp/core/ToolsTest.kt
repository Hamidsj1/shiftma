package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class ToolsTest {
    private fun u(cat: String, name: String) = UnitConverter.CATEGORIES.first { it.title == cat }.units.first { it.name == name }

    @Test fun lengthAndWeight() {
        assertEquals(1000.0, UnitConverter.convert(u("طول", "کیلومتر"), u("طول", "متر"), 1.0), 1e-9)
        assertEquals(2.54, UnitConverter.convert(u("طول", "اینچ"), u("طول", "سانتی‌متر"), 1.0), 1e-9)
        assertEquals(1000.0, UnitConverter.convert(u("وزن", "کیلوگرم"), u("وزن", "گرم"), 1.0), 1e-9)
    }

    @Test fun temperature() {
        val c = u("دما", "سانتی‌گراد"); val f = u("دما", "فارنهایت"); val k = u("دما", "کلوین")
        assertEquals(32.0, UnitConverter.convert(c, f, 0.0), 1e-9)
        assertEquals(100.0, UnitConverter.convert(f, c, 212.0), 1e-9)
        assertEquals(273.15, UnitConverter.convert(c, k, 0.0), 1e-9)
        assertEquals(-40.0, UnitConverter.convert(f, c, -40.0), 1e-9)
    }

    @Test fun parseAndFormat() {
        assertEquals(12.5, UnitConverter.parse("۱۲٫۵")!!, 0.0)
        assertEquals(3.0, UnitConverter.parse("3")!!, 0.0)
        assertNull(UnitConverter.parse("abc")); assertNull(UnitConverter.parse(""))
        assertEquals("1000", UnitConverter.format(1000.0))
        assertEquals("0.5", UnitConverter.format(0.5))
        assertEquals("1.609344", UnitConverter.format(1.609344))
    }

    @Test fun everyCategoryHasTwoUnitsAndRoundTrips() {
        UnitConverter.CATEGORIES.forEach { c ->
            assertTrue(c.title, c.units.size >= 2)
            val a = c.units[0]; val b = c.units[1]
            assertEquals(c.title, 7.0, UnitConverter.convert(b, a, UnitConverter.convert(a, b, 7.0)), 1e-9)
        }
    }

    @Test fun ageBasic() {
        val r = AgeCalc.compute(JalaliDate(1400, 1, 1), JalaliDate(1405, 7, 19))!!
        assertEquals(Triple(5, 6, 18), Triple(r.years, r.months, r.days))
        assertEquals(JalaliDate(1406, 1, 1), r.nextBirthday)
        assertEquals(JalaliDate(1406, 1, 1).toJdn() - JalaliDate(1405, 7, 19).toJdn(), r.daysToBirthday)
    }

    @Test fun ageBorrowsDaysAndHandlesToday() {
        val r = AgeCalc.compute(JalaliDate(1380, 5, 20), JalaliDate(1405, 7, 10))!!
        assertEquals(Triple(25, 1, 21), Triple(r.years, r.months, r.days))   // روز: ۱۰ − ۲۰ + ۳۱ (طول شهریور)
        val b = AgeCalc.compute(JalaliDate(1380, 7, 19), JalaliDate(1405, 7, 19))!!
        assertEquals(0, b.daysToBirthday); assertEquals(25, b.years)
        assertNull(AgeCalc.compute(JalaliDate(1406, 1, 1), JalaliDate(1405, 7, 19)))
    }
}
