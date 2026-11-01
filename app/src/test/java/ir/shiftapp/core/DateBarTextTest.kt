package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateBarTextTest {
    private val today = JalaliDate(1405, 7, 27)

    @Test fun titleIsJalaliDateWithWeekday() {
        val c = DateBarText.build(today, "۱۹ اکتبر ۲۰۲۶", "۷ ربیع‌الثانی ۱۴۴۸", null, null, null, null)
        assertEquals("${Fa.WEEK_FULL[today.dayOfWeek]} ${Fa.num(27)} مهر ${Fa.num(1405)}", c.title)
        assertEquals("۱۹ اکتبر ۲۰۲۶ · ۷ ربیع‌الثانی ۱۴۴۸", c.text)
        assertNull(c.sub)
        assertEquals(c.text, c.big)
    }

    @Test fun hijriMissingFallsBackToGregorianOnly() {
        assertEquals("۱۹ اکتبر ۲۰۲۶", DateBarText.build(today, "۱۹ اکتبر ۲۰۲۶", null, null, null, null, null).text)
    }

    @Test fun shiftHolidayAndPrayerLinesAppearOnlyWhenKnown() {
        val c = DateBarText.build(today, "g", "h", "روزکار ۰۷:۰۰–۱۹:۰۰", "استراحت", "تعطیل نمونه", "اذان ظهر ۱۱:۵۱")
        assertEquals("امروز: روزکار ۰۷:۰۰–۱۹:۰۰", c.sub)
        assertEquals(listOf("g · h", "تعطیل: تعطیل نمونه", "امروز: روزکار ۰۷:۰۰–۱۹:۰۰ · فردا: استراحت", "اذان ظهر ۱۱:۵۱"), c.big.lines())
        // بدون الگو: خط شیفت و زیرعنوان نیست
        val none = DateBarText.build(today, "g", "h", null, null, "", null)
        assertTrue(none.big.lines().size == 1 && none.sub == null)
    }
}
