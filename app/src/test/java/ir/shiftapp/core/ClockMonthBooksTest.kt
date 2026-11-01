package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class ClockMonthBooksTest {
    @Test fun clockWithoutColon() {
        assertEquals(5 * 60 + 30, ShiftTime.parseClock("530")); assertEquals(5 * 60 + 30, ShiftTime.parseClock("0530"))
        assertEquals(17 * 60 + 30, ShiftTime.parseClock("1730")); assertEquals(17 * 60, ShiftTime.parseClock("17")); assertEquals(5 * 60, ShiftTime.parseClock("5"))
        assertEquals(5 * 60 + 30, ShiftTime.parseClock("5.30")); assertEquals(5 * 60 + 30, ShiftTime.parseClock("5 30")); assertEquals(5 * 60 + 30, ShiftTime.parseClock("۰۵۳۰"))
        assertNull(ShiftTime.parseClock("2460")); assertNull(ShiftTime.parseClock("24")); assertNull(ShiftTime.parseClock("12345")); assertNull(ShiftTime.parseClock("17:")); assertNull(ShiftTime.parseClock(""))
    }

    @Test fun monthByNumberOrName() {
        assertEquals(7, DateText.parseMonth(0, "7")); assertEquals(7, DateText.parseMonth(0, "۰۷")); assertEquals(7, DateText.parseMonth(0, "مهر"))
        assertEquals(2, DateText.parseMonth(0, "ارديبهشت")); assertEquals(12, DateText.parseMonth(0, "اسف")); assertNull(DateText.parseMonth(0, "ا")); assertNull(DateText.parseMonth(0, "13"))
        assertEquals(10, DateText.parseMonth(1, "October")); assertEquals(10, DateText.parseMonth(1, "اکتبر")); assertEquals(7, DateText.parseMonth(1, "ژوئیه")); assertNull(DateText.parseMonth(1, "ju"))
        assertEquals(9, DateText.parseMonth(2, "رمضان")); assertEquals(3, DateText.parseMonth(2, "ربیع‌الاول")); assertEquals(4, DateText.parseMonth(2, "ربیع الثانی")); assertNull(DateText.parseMonth(2, "جمادی"))
        assertEquals("مهر", DateText.monthName(0, 7)); assertEquals("رمضان", DateText.monthName(2, 9))
    }

    @Test fun bookNamesAndUrls() {
        assertEquals("کتاب خوب.pdf", Books.sanitize("کتاب خوب.pdf")); assertEquals("a_b_c.pdf", Books.sanitize("a/b?c")); assertEquals("x.epub", Books.sanitize("x.epub"))
        assertEquals("my book.pdf", Books.nameFromUrl("https://s.ir/files/my%20book.pdf?x=1"))
        assertTrue(Books.looksLikeBookUrl("https://a.com/x/b.PDF?dl=1")); assertFalse(Books.looksLikeBookUrl("https://a.com/x")); assertFalse(Books.looksLikeBookUrl("ftp://a/b.pdf"))
        assertEquals("pdf", Books.extFromMime("application/pdf; charset=x")); assertNull(Books.extFromMime("text/html"))
        assertEquals("a (2).pdf", Books.uniqueName("a.pdf", setOf("a.pdf"))); assertEquals("a (3).pdf", Books.uniqueName("a.pdf", setOf("a.pdf", "a (2).pdf")))
        assertFalse(Books.isSafeName("../x.pdf")); assertTrue(Books.isSafeName("x.pdf"))
    }
}
