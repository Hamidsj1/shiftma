package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class OccasionsTest {
    private val cal = HolidayCalendar(emptyMap(), fridayOff = true)

    @Test fun occasionKeysAreValidAndNotHolidays() {
        Occasions.LIST.keys.forEach { (m, d) ->
            assertTrue("$m/$d", JalaliCalendar.isValid(1404, m, d))
            assertNull("$m/$d is a holiday", FixedHolidays.LIST[m to d])
        }
    }

    @Test fun upcomingIsOrderedAndHasHolidaysAndOccasions() {
        val u = UpcomingOccasions.upcoming(JalaliDate(1405, 7, 19), cal)
        assertEquals(u.sortedBy { it.daysLeft }, u)
        assertTrue(u.any { it.holiday } && u.any { !it.holiday })
        assertTrue(u.any { it.title == "روز دانش‌آموز" })
    }

    @Test fun daysLeftWording() {
        assertEquals("امروز", daysLeftText(0)); assertEquals("فردا", daysLeftText(1))
    }

    @Test fun globalOccasionsUseGregorianDate() {
        // ۱۴۰۵/۰۲/۱۱ = 2026-05-01
        assertEquals("روز جهانی کارگر", Occasions.global(JalaliDate(1405, 2, 11)))
        assertNull(Occasions.global(JalaliDate(1405, 2, 12)))
    }

    @Test fun lunarOccasionsAppearOncePerYearAndShiftMoves() {
        val start = JalaliDate(1405, 1, 1)
        val days = (0 until 355).map { start.plusDays(it) }
        for (title in listOf("عاشورا", "عید فطر", "عید غدیر خم", "اربعین حسینی")) {
            assertEquals(title, 1, days.count { title in Occasions.lunar(it) })
        }
        val d = days.first { "عاشورا" in Occasions.lunar(it) }
        assertTrue("عاشورا" in Occasions.lunar(d.plusDays(1), shift = 1))
        assertTrue("عاشورا" !in Occasions.lunar(d.plusDays(1), shift = 0))
    }

    @Test fun prefsLunarShiftRoundTripAndBounds() {
        assertEquals(-1, Prefs.from(Prefs(lunarShift = -1).toMap()).lunarShift)
        assertEquals(0, Prefs.from(mapOf(PrefKeys.LUNAR_SHIFT to "9")).lunarShift)
    }
}
