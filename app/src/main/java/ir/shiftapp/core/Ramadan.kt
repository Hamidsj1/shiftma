package ir.shiftapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * حالت رمضان: اگر ماه قمری رمضان بود (با همان تنظیم lunarShift تقویم)، شمارش معکوس پایان سحر و افطار؛
 * وگرنه اگر کمتر از ۶۰ روز مانده باشد، «چند روز تا رمضان». اوقات از همان محاسبهٔ آفلاین اذان می‌آید
 * (سحر = اذان صبح، افطار = اذان مغرب). منطق خالص و تست‌پذیر.
 */
object Ramadan {
    const val MONTH = 9

    enum class Phase { SAHAR, FASTING, NIGHT }

    /**
     * [day] روز ماه رمضان؛ [phase] مرحله؛ [targetMs] لحظهٔ پایان سحر/افطار/سحر فردا
     * (در شب آخر که فردا رمضان نیست null است).
     */
    data class Status(val day: Int, val phase: Phase, val targetMs: Long?)

    fun dayOf(date: LocalDate, shift: Int): Int? =
        DateInfo.hijri(date, shift)?.takeIf { it.month == MONTH }?.day

    fun isRamadan(date: LocalDate, shift: Int): Boolean = dayOf(date, shift) != null

    /** چند روز تا اول رمضان (۱ تا ۶۰)؛ null اگر الان رمضان است یا دورتر از ۶۰ روز مانده. */
    fun daysUntil(today: LocalDate, shift: Int): Int? {
        val h = DateInfo.hijri(today, shift) ?: return null
        if (h.month == MONTH) return null
        val year = if (h.month < MONTH) h.year else h.year + 1
        val start = DateInfo.fromHijri(year, MONTH, 1, shift) ?: return null
        return ChronoUnit.DAYS.between(today, start).toInt().takeIf { it in 1..60 }
    }

    /** null یعنی امروز رمضان نیست یا وقت‌ها (مثلاً در عرض‌های بالا) محاسبه نشدند. */
    fun status(nowMs: Long, zone: ZoneId, lat: Double, lon: Double, method: PrayerMethod, shift: Int): Status? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = dayOf(today, shift) ?: return null
        val t = PrayerCalc.times(today, lat, lon, zone, method)
        val fajr = t.atMs(Prayer.FAJR, zone) ?: return null
        val maghrib = t.atMs(Prayer.MAGHRIB, zone) ?: return null
        return when {
            nowMs < fajr -> Status(day, Phase.SAHAR, fajr)
            nowMs < maghrib -> Status(day, Phase.FASTING, maghrib)
            else -> {
                val tomorrow = today.plusDays(1)
                val nextFajr = if (isRamadan(tomorrow, shift))
                    PrayerCalc.times(tomorrow, lat, lon, zone, method).atMs(Prayer.FAJR, zone) else null
                Status(day, Phase.NIGHT, nextFajr)
            }
        }
    }

    /** متن عنوان کارت. */
    fun title(s: Status): String = when (s.phase) {
        Phase.SAHAR -> "تا پایان سحر"
        Phase.FASTING -> "تا افطار"
        Phase.NIGHT -> if (s.targetMs != null) "تا سحر فردا" else "ماه رمضان به پایان رسید"
    }
}
