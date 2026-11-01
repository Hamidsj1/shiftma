package ir.shiftapp.core

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** تاریخ میلادی و قمری (ام‌القری) برای نمایش کنار تاریخ شمسی و ابزار «مبدل تاریخ». shift همان تنظیم lunarShift است. */
object DateInfo {
    val GREG_MONTHS = listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    val HIJRI_MONTHS = listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    data class Hijri(val year: Int, val month: Int, val day: Int)

    fun hijri(date: LocalDate, shift: Int = 0): Hijri? = runCatching {
        val h = HijrahDate.from(date.plusDays(-shift.toLong()))
        Hijri(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }.getOrNull()

    /** تاریخ میلادیِ معادل یک تاریخ قمری؛ null اگر تاریخ قمری معتبر نباشد. */
    fun fromHijri(year: Int, month: Int, day: Int, shift: Int = 0): LocalDate? = runCatching {
        LocalDate.from(HijrahDate.of(year, month, day)).plusDays(shift.toLong())
    }.getOrNull()

    fun gregorianText(d: LocalDate): String = "${Fa.num(d.dayOfMonth)} ${GREG_MONTHS[d.monthValue - 1]} ${Fa.num(d.year)}"
    fun hijriText(h: Hijri): String = "${Fa.num(h.day)} ${HIJRI_MONTHS[h.month - 1]} ${Fa.num(h.year)}"
}
