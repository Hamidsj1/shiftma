package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateInfoTest {
    @Test fun gregorianTextIsPersian() {
        assertEquals("۱۲ اکتبر ۲۰۲۶", DateInfo.gregorianText(LocalDate.of(2026, 10, 12)))
    }

    @Test fun hijriRoundTrips() {
        for (shift in -2..2) {
            var d = LocalDate.of(2026, 1, 1)
            repeat(400) {
                val h = DateInfo.hijri(d, shift)
                assertNotNull(h)
                assertEquals("$d shift=$shift", d, DateInfo.fromHijri(h!!.year, h.month, h.day, shift))
                d = d.plusDays(1)
            }
        }
    }

    @Test fun invalidHijriIsNull() {
        assertNull(DateInfo.fromHijri(1448, 13, 1))
        assertNull(DateInfo.fromHijri(1448, 1, 31))
    }
}
