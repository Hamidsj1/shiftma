package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class RepeatAlarmTest {
    private val utc = ZoneOffset.UTC
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = SingleAlarm.at(LocalDate.of(y, m, d), h * 60 + min, utc)

    @Test fun oneShotHasNoNext() {
        assertNull(SingleAlarm(1, ms(2026, 10, 17, 6, 0)).advance(ms(2026, 10, 17, 6, 0), utc))
    }

    @Test fun dailyMovesToTomorrowSameTime() {
        val a = SingleAlarm(1, ms(2026, 10, 17, 6, 30), repeat = SingleAlarm.ALL_DAYS)
        assertEquals(ms(2026, 10, 18, 6, 30), a.advance(ms(2026, 10, 17, 6, 30), utc)!!.triggerAtMs)
    }

    @Test fun selectedWeekdaysSkipOthers() {
        // ۲۰۲۶-۱۰-۱۷ شنبه است؛ فقط شنبه و دوشنبه
        val mask = SingleAlarm.bit(DayOfWeek.SATURDAY) or SingleAlarm.bit(DayOfWeek.MONDAY)
        val a = SingleAlarm(1, ms(2026, 10, 17, 5, 0), repeat = mask)
        val n1 = a.advance(ms(2026, 10, 17, 5, 0), utc)!!
        assertEquals(ms(2026, 10, 19, 5, 0), n1.triggerAtMs)                          // دوشنبه
        assertEquals(ms(2026, 10, 24, 5, 0), n1.advance(n1.triggerAtMs, utc)!!.triggerAtMs)  // شنبهٔ بعد
    }

    @Test fun missedRepeatingAlarmCatchesUp() {
        val a = SingleAlarm(1, ms(2026, 10, 10, 6, 0), repeat = SingleAlarm.ALL_DAYS)
        val n = a.advance(ms(2026, 10, 17, 12, 0), utc)!!
        assertEquals(ms(2026, 10, 18, 6, 0), n.triggerAtMs)
        assertTrue(n.triggerAtMs > ms(2026, 10, 17, 12, 0))
    }

    @Test fun encodeDecodeKeepsRepeatAndOldFormatStillLoads() {
        val l = listOf(SingleAlarm(1, 1_700_000_000_000L, "ورزش", repeat = 0b0010101))
        assertEquals(l, SingleAlarm.decode(SingleAlarm.encode(l)))
        val old = "3\u001f1700000000000\u001fقدیمی\u001fUSER\u001ftrue"       // بدون فیلد تکرار
        assertEquals(0, SingleAlarm.decode(old).single().repeat)
    }

    @Test fun repeatText() {
        assertEquals("", SingleAlarm.repeatText(0))
        assertEquals("هر روز", SingleAlarm.repeatText(SingleAlarm.ALL_DAYS))
        assertEquals("هر شنبه، دوشنبه", SingleAlarm.repeatText(SingleAlarm.bit(DayOfWeek.MONDAY) or SingleAlarm.bit(DayOfWeek.SATURDAY)))
    }
}
