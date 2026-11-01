package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetTextTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private fun at(d: LocalDate, h: Int, m: Int = 0) = SingleAlarm.at(d, h * 60 + m, zone)
    private val day = LocalDate.of(2026, 10, 18)      // یکشنبه

    @Test fun sameDayIsToday() {
        assertEquals(Fa.digits("امروز 14:20"), WidgetText.dayAndTime(at(day, 10), at(day, 14, 20), zone))
    }

    @Test fun nextDayIsTomorrow() {
        assertEquals(Fa.digits("فردا 06:00"), WidgetText.dayAndTime(at(day, 22), at(day.plusDays(1), 6), zone))
    }

    @Test fun midnightCrossingCountsCalendarDaysNotHours() {
        // ۲۳:۳۰ تا ۰۰:۳۰ فقط یک ساعت فاصله دارد ولی «فردا» است
        assertEquals(Fa.digits("فردا 00:30"), WidgetText.dayAndTime(at(day, 23, 30), at(day.plusDays(1), 0, 30), zone))
    }

    @Test fun laterDaysShowWeekdayAndJalaliDate() {
        // ۲۹ مهر ۱۴۰۵ = چهارشنبه ۲۱ اکتبر ۲۰۲۶
        assertEquals(Fa.digits("چهارشنبه 29 مهر 06:00"), WidgetText.dayAndTime(at(day, 10), at(LocalDate.of(2026, 10, 21), 6), zone))
    }

    @Test fun nextLineAddsRemaining() {
        assertEquals(
            Fa.digits("امروز 14:20 · 4 ساعت و 20 دقیقه دیگر"),
            WidgetText.nextLine(at(day, 10), at(day, 14, 20), zone)
        )
    }
}
