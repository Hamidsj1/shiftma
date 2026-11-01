package ir.shiftapp.core

import java.time.Instant
import java.time.ZoneId

/*
 * متن‌های مشترک ویجت‌ها: «امروز ۱۴:۲۰ · ۲ ساعت دیگر». منطق خالص و تست‌پذیر؛ ویجت‌ها فقط آن را نمایش می‌دهند.
 */
object WidgetText {
    /** «امروز ۱۴:۲۰»، «فردا ۰۶:۰۰» یا «شنبه ۸ آبان ۰۶:۰۰». */
    fun dayAndTime(nowMs: Long, targetMs: Long, zone: ZoneId): String {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val at = Instant.ofEpochMilli(targetMs).atZone(zone)
        val clock = Fa.digits(ShiftTime.fmt(at.hour * 60 + at.minute))
        val diff = at.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        val day = when (diff) {
            0L -> "امروز"
            1L -> "فردا"
            else -> {
                val j = JalaliCalendar.fromLocalDate(at.toLocalDate())
                "${Fa.WEEK_FULL[j.dayOfWeek]} ${Fa.num(j.day)} ${Fa.MONTHS[j.month - 1]}"
            }
        }
        return "$day $clock"
    }

    /** «امروز ۱۴:۲۰ · ۲ ساعت دیگر». */
    fun nextLine(nowMs: Long, targetMs: Long, zone: ZoneId): String =
        "${dayAndTime(nowMs, targetMs, zone)} · ${PrayerRules.remaining(targetMs - nowMs)} دیگر"
}
