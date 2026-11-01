package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpecialAccessTest {
    private val key = "c766de2cae4f19f4dba8cfa021be2abb"

    /** مقادیر مرجع را tools/shiftma_code.py ساخته است؛ یعنی اسکریپت توسعه‌دهنده و برنامه یک رمز می‌سازند. */
    @Test fun matchesDeveloperScript() {
        assertEquals("498077", SpecialAccess.codeFor(LocalDate.of(2026, 10, 14), key))
        assertEquals("428756", SpecialAccess.codeFor(LocalDate.of(2026, 1, 1), key))
        assertEquals("334305", SpecialAccess.codeFor(LocalDate.of(2027, 3, 21), key))
    }

    @Test fun codeIsSixDigitsAndChangesPerDay() {
        val a = SpecialAccess.codeFor(LocalDate.of(2026, 10, 14), key)
        val b = SpecialAccess.codeFor(LocalDate.of(2026, 10, 15), key)
        assertEquals(6, a.length); assertTrue(a.all { it.isDigit() })
        assertNotEquals(a, b)
        assertNotEquals(a, SpecialAccess.codeFor(LocalDate.of(2026, 10, 14), key + "x"))
    }

    @Test fun verifyAcceptsTodayOnlyAndPersianDigits() {
        val d = LocalDate.of(2026, 10, 14)
        assertTrue(SpecialAccess.verify("498077", d, key))
        assertTrue(SpecialAccess.verify(" ۴۹۸۰۷۷ ", d, key))
        assertFalse(SpecialAccess.verify("498078", d, key))
        assertFalse(SpecialAccess.verify("49807", d, key))
        assertFalse(SpecialAccess.verify("", d, key))
        assertFalse(SpecialAccess.verify("498077", d.plusDays(1), key))
    }
}
