package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class DateTextTest {
    @Test fun parsesLatinPersianAndArabicDigits() {
        val d = JalaliDate(1405, 7, 13)
        assertEquals(d, DateText.parse("1405/07/13"))
        assertEquals(d, DateText.parse("۱۴۰۵/۰۷/۱۳"))
        assertEquals(d, DateText.parse("\u0661\u0664\u0660\u0665-7-13"))
        assertEquals(d, DateText.parse("  1405.7.13 "))
    }
    @Test fun rejectsInvalid() {
        assertNull(DateText.parse("")); assertNull(DateText.parse("1405/07"))
        assertNull(DateText.parse("1405/13/01")); assertNull(DateText.parse("1405/06/32"))
        assertNull(DateText.parse("abcd/07/13")); assertNull(DateText.parse("1405/07/31"))
    }
    @Test fun formatRoundTrip() {
        assertEquals("1405/07/03", DateText.format(JalaliDate(1405, 7, 3)))
        assertEquals(JalaliDate(1405, 7, 3), DateText.parse(DateText.format(JalaliDate(1405, 7, 3))))
    }
}
