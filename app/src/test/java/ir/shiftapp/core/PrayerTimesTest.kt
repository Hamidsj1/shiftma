package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * مقادیر مرجع با پیاده‌سازی مستقل (پایتون، همان الگوریتم PrayTimes) برای تهران و مشهد ۲۰ مهر ۱۴۰۵ حساب شده‌اند.
 * خطای مجاز ±۱ دقیقه (گرد کردن).
 */
class PrayerTimesTest {
    private val iran = ZoneOffset.ofHoursMinutes(3, 30)
    private val day = LocalDate.of(2026, 10, 12)
    private fun m(h: Int, mm: Int) = h * 60 + mm
    private fun near(expected: Int, actual: Int?, what: String) {
        assertNotNull(what, actual)
        assertTrue("$what: expected $expected got $actual", kotlin.math.abs(expected - actual!!) <= 1)
    }

    @Test fun tehranShia() {
        val t = PrayerCalc.times(day, 35.6892, 51.3890, iran, PrayerMethod.SHIA).minutes
        near(m(4, 45), t[Prayer.FAJR], "fajr"); near(m(6, 8), t[Prayer.SUNRISE], "sunrise")
        near(m(11, 51), t[Prayer.DHUHR], "dhuhr"); near(m(17, 33), t[Prayer.SUNSET], "sunset")
        near(m(17, 51), t[Prayer.MAGHRIB], "maghrib"); near(m(23, 9), t[Prayer.MIDNIGHT], "midnight")
        assertNull(t[Prayer.ASR]); assertNull(t[Prayer.ISHA])
    }

    @Test fun mashhadShia() {
        val t = PrayerCalc.times(day, 36.2605, 59.6168, iran, PrayerMethod.SHIA).minutes
        near(m(4, 12), t[Prayer.FAJR], "fajr"); near(m(11, 18), t[Prayer.DHUHR], "dhuhr")
        near(m(17, 18), t[Prayer.MAGHRIB], "maghrib"); near(m(22, 36), t[Prayer.MIDNIGHT], "midnight")
    }

    @Test fun tehranSunni() {
        val t = PrayerCalc.times(day, 35.6892, 51.3890, iran, PrayerMethod.SUNNI).minutes
        near(m(4, 43), t[Prayer.FAJR], "fajr"); near(m(15, 5), t[Prayer.ASR], "asr")
        near(m(17, 33), t[Prayer.MAGHRIB], "maghrib = sunset"); near(m(18, 53), t[Prayer.ISHA], "isha")
        assertNull(t[Prayer.MIDNIGHT])
    }

    @Test fun orderIsMonotonicEverywhereInIran() {
        for (c in PrayerCities.ALL) for (d in listOf(LocalDate.of(2026, 3, 21), LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
            val t = PrayerCalc.times(d, c.lat, c.lon, iran, PrayerMethod.SHIA).minutes
            val seq = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.SUNSET, Prayer.MAGHRIB, Prayer.MIDNIGHT).map { t[it] ?: error("${c.name} $d $it") }
            assertEquals("${c.name} $d", seq.sorted(), seq)
        }
    }

    @Test fun cityIdsAreUniqueAndCoordinatesInIran() {
        assertEquals(PrayerCities.ALL.size, PrayerCities.ALL.map { it.id }.toSet().size)
        PrayerCities.ALL.forEach { assertTrue(it.name, it.lat in 24.0..40.0 && it.lon in 43.0..64.0) }
        assertNotNull(PrayerCities.byId(PrayerCities.DEFAULT))
    }

    @Test fun nextPrayerAfterMidnightIsFajr() {
        val now = SingleAlarm.at(day, m(0, 30), iran)
        val n = PrayerCalc.next(now, iran, 35.6892, 51.3890, PrayerMethod.SHIA)!!
        assertEquals(Prayer.FAJR, n.prayer)
        near(m(4, 45), n.minuteOfDay, "next fajr")
    }

    @Test fun qiblaTehran() {
        assertEquals(218.4, Qibla.bearing(35.6892, 51.3890), 0.3)
        assertEquals(1944.0, Qibla.distanceKm(35.6892, 51.3890), 8.0)
        assertEquals("جنوب غربی", Qibla.cardinal(218.4))
        assertEquals(0.0, Qibla.distanceKm(Qibla.KAABA_LAT, Qibla.KAABA_LON), 0.01)
        assertEquals(10.0, Qibla.diff(5.0, 355.0), 1e-9)
        assertEquals(-10.0, Qibla.diff(355.0, 5.0), 1e-9)
    }

    @Test fun windowsCrossMidnight() {
        assertTrue(PrayerRules.inWindow(m(23, 0), m(22, 0), m(6, 0)))
        assertTrue(PrayerRules.inWindow(m(5, 59), m(22, 0), m(6, 0)))
        assertFalse(PrayerRules.inWindow(m(6, 0), m(22, 0), m(6, 0)))
        assertTrue(PrayerRules.inWindow(m(10, 0), m(9, 0), m(11, 0)))
        assertFalse(PrayerRules.inWindow(m(10, 0), m(10, 0), m(10, 0)))
    }

    @Test fun shiftOverlap() {
        val dayShift = ShiftTime(7 * 60, 19 * 60); val night = ShiftTime(19 * 60, 7 * 60)
        assertTrue(PrayerRules.inShift(m(12, 0), dayShift, null))
        assertFalse(PrayerRules.inShift(m(5, 0), dayShift, null))
        assertTrue(PrayerRules.inShift(m(5, 0), null, night))      // ادامهٔ شبکاری دیشب
        assertTrue(PrayerRules.inShift(m(21, 0), night, null))
        assertFalse(PrayerRules.inShift(m(12, 0), night, night))
    }

    @Test fun remainingText() {
        assertEquals("۱۰ دقیقه", PrayerRules.remaining(10 * 60_000L))
        assertEquals("۲ ساعت و ۵ دقیقه", PrayerRules.remaining(125 * 60_000L))
        assertEquals("کمتر از یک دقیقه", PrayerRules.remaining(20_000L))
    }

    // ---- هشدار اذان در برنامه‌ریز و تنظیمات ----

    @Test fun offByDefaultAndNoAlertsMeansNothing() {
        assertFalse(Prefs().prayerOn)
        val now = SingleAlarm.at(day, 0, iran)
        assertNull(ReminderPlanner.nextPrayer(now, iran, Prefs(prayerOn = false, prayerAlerts = setOf("fajr"))))
        assertNull(ReminderPlanner.nextPrayer(now, iran, Prefs(prayerOn = true)))
    }

    @Test fun plannerPicksNextAlertWithLead() {
        val now = SingleAlarm.at(day, m(0, 10), iran)
        val prefs = Prefs(prayerOn = true, prayerAlerts = setOf("fajr", "dhuhr"), prayerLeadMin = 10)
        val r = ReminderPlanner.nextPrayer(now, iran, prefs)!!
        val fajr = PrayerCalc.times(day, 35.6892, 51.3890, iran, PrayerMethod.SHIA).atMs(Prayer.FAJR, iran)!!
        assertEquals(fajr - 10 * 60_000L, r.triggerAtMs)
        assertEquals("fajr", r.prayerId); assertTrue(r.isPrayer); assertEquals(true, r.alarm)
        assertEquals("اذان صبح", r.title)
    }

    @Test fun quietHoursSkipSleepTimeAlerts() {
        val now = SingleAlarm.at(day, m(0, 10), iran)
        val prefs = Prefs(prayerOn = true, prayerAlerts = setOf("fajr", "dhuhr"), prayerQuietOn = true, prayerQuietStart = m(3, 0), prayerQuietEnd = m(8, 0))
        val r = ReminderPlanner.nextPrayer(now, iran, prefs)!!
        assertEquals("dhuhr", r.prayerId)
        val allQuiet = prefs.copy(prayerAlerts = setOf("fajr"))
        assertNull(ReminderPlanner.nextPrayer(now, iran, allQuiet))
    }

    @Test fun plannerMergesPrayerWithOtherSources() {
        val now = SingleAlarm.at(day, m(0, 10), iran)
        val prefs = Prefs(prayerOn = true, prayerAlerts = setOf("fajr"), alarms = listOf(SingleAlarm(1, SingleAlarm.at(day, m(1, 0), iran), "x")))
        assertEquals(1L, ReminderPlanner.next(now, iran, null, null, emptyMap(), prefs)!!.alarmId)   // آلارم ساعت ۱ زودتر از اذان صبح است
        val prefs2 = prefs.copy(alarms = emptyList())
        assertEquals("fajr", ReminderPlanner.next(now, iran, null, null, emptyMap(), prefs2)!!.prayerId)
    }

    @Test fun prefsRoundTripAndBadValues() {
        val p = Prefs(
            prayerOn = true, prayerHintHidden = true, prayerCity = PrayerCities.GPS, prayerLat = 30.5, prayerLon = 57.1, prayerLocName = "موقعیت من",
            prayerMethodId = "sunni", prayerAlerts = setOf("fajr", "maghrib"), prayerLeadMin = 15, prayerAlarm = false,
            prayerSound = "content://media/internal/audio/media/12", prayerQuietOn = true, prayerQuietStart = 1380, prayerQuietEnd = 330
        )
        assertEquals(p, Prefs.from(p.toMap()))
        val bad = Prefs.from(mapOf(
            PrefKeys.PRAYER_CITY to "nowhere", PrefKeys.PRAYER_LAT to "999", PrefKeys.PRAYER_METHOD to "x",
            PrefKeys.PRAYER_ALERTS to "fajr,zzz", PrefKeys.PRAYER_LEAD to "-5", PrefKeys.PRAYER_QUIET to "abc"
        ))
        assertEquals(PrayerCities.DEFAULT, bad.prayerCity); assertEquals(35.6892, bad.prayerLat, 1e-9)
        assertEquals("shia", bad.prayerMethodId); assertEquals(setOf("fajr"), bad.prayerAlerts)
        assertEquals(0, bad.prayerLeadMin); assertEquals(22 * 60, bad.prayerQuietStart)
        assertEquals("تهران", bad.prayerPlace.name)
    }
}
