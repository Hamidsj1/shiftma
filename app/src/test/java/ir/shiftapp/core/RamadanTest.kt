package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RamadanTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val lat = 35.6892
    private val lon = 51.3890
    private val method = PrayerMethod.SHIA

    // ۱ رمضان ۱۴۴۷ و ۱ شوال ۱۴۴۷ را از خود تقویم می‌گیریم تا به عدد ثابت وابسته نباشیم
    private val start: LocalDate = DateInfo.fromHijri(1447, 9, 1)!!
    private val shawwal: LocalDate = DateInfo.fromHijri(1447, 10, 1)!!

    private fun at(date: LocalDate, hour: Int) = SingleAlarm.at(date, hour * 60, zone)
    private fun times(date: LocalDate) = PrayerCalc.times(date, lat, lon, zone, method)

    @Test fun dayAndMembership() {
        assertTrue(Ramadan.isRamadan(start, 0))
        assertEquals(1, Ramadan.dayOf(start, 0))
        assertEquals(5, Ramadan.dayOf(start.plusDays(4), 0))
        assertFalse(Ramadan.isRamadan(start.minusDays(1), 0))
        assertFalse(Ramadan.isRamadan(shawwal, 0))
        assertTrue(Ramadan.isRamadan(shawwal.minusDays(1), 0))
    }

    @Test fun lunarShiftMovesTheMonth() {
        // با شیفت +۱، تقویم ما یک روز زودتر ماه جدید را می‌گیرد؛ روز ۱ رمضان یک روز دیرتر می‌شود
        assertEquals(Ramadan.dayOf(start, 0), Ramadan.dayOf(start.plusDays(1), 1))
    }

    @Test fun daysUntilCountsToFirstDay() {
        assertEquals(10, Ramadan.daysUntil(start.minusDays(10), 0))
        assertEquals(1, Ramadan.daysUntil(start.minusDays(1), 0))
        assertNull(Ramadan.daysUntil(start, 0))                    // خودِ رمضان
        assertNull(Ramadan.daysUntil(start.minusDays(100), 0))     // دورتر از ۶۰ روز
        assertNull(Ramadan.daysUntil(shawwal.plusDays(5), 0))      // بعد از رمضان؛ سال بعد دور است
    }

    @Test fun notRamadanGivesNullStatus() {
        assertNull(Ramadan.status(at(start.minusDays(3), 12), zone, lat, lon, method, 0))
    }

    @Test fun saharBeforeFajr() {
        val day = start.plusDays(4)
        val s = Ramadan.status(at(day, 3), zone, lat, lon, method, 0)
        assertNotNull(s)
        assertEquals(Ramadan.Phase.SAHAR, s!!.phase)
        assertEquals(5, s.day)
        assertEquals(times(day).atMs(Prayer.FAJR, zone), s.targetMs)
        assertEquals("تا پایان سحر", Ramadan.title(s))
    }

    @Test fun fastingCountsToIftar() {
        val day = start.plusDays(4)
        val s = Ramadan.status(at(day, 12), zone, lat, lon, method, 0)!!
        assertEquals(Ramadan.Phase.FASTING, s.phase)
        assertEquals(times(day).atMs(Prayer.MAGHRIB, zone), s.targetMs)
        assertEquals("تا افطار", Ramadan.title(s))
    }

    @Test fun nightCountsToTomorrowsFajr() {
        val day = start.plusDays(4)
        val s = Ramadan.status(at(day, 22), zone, lat, lon, method, 0)!!
        assertEquals(Ramadan.Phase.NIGHT, s.phase)
        assertEquals(times(day.plusDays(1)).atMs(Prayer.FAJR, zone), s.targetMs)
        assertEquals("تا سحر فردا", Ramadan.title(s))
    }

    @Test fun lastNightHasNoNextSahar() {
        val last = shawwal.minusDays(1)
        val s = Ramadan.status(at(last, 22), zone, lat, lon, method, 0)!!
        assertEquals(Ramadan.Phase.NIGHT, s.phase)
        assertNull(s.targetMs)
        assertEquals("ماه رمضان به پایان رسید", Ramadan.title(s))
    }

    @Test fun targetIsAlwaysInTheFuture() {
        val day = start.plusDays(9)
        for (h in listOf(1, 3, 5, 9, 12, 15, 17, 19, 21, 23)) {
            val now = at(day, h)
            val s = Ramadan.status(now, zone, lat, lon, method, 0)!!
            val t = s.targetMs
            if (t != null) assertTrue("hour $h", t > now)
        }
    }
}
