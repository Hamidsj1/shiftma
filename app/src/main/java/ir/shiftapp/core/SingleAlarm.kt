package ir.shiftapp.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * آلارم تکی: یک زمان مطلق (میلی‌ثانیه) + برچسب. بعد از شلیک پاک می‌شود.
 * repeat: ماسک روزهای هفته برای تکرار (بیت = DayOfWeek.value − ۱؛ ۰ یعنی یک‌بار، ۱۲۷ یعنی هر روز).
 * kind: USER = آلارم عادی، SUB_OUT / SUB_IN = دو هشدار «جانشین شب» (ثبت خروج قبل و ورود بعد از نیمه‌شب).
 */
data class SingleAlarm(
    val id: Long,
    val triggerAtMs: Long,
    val label: String = "",
    val kind: String = KIND_USER,
    val enabled: Boolean = true,
    val repeat: Int = 0
) {
    val repeating: Boolean get() = (repeat and ALL_DAYS) != 0

    /**
     * اولین زمان تکرار بعد از [nowMs] با همان ساعت روز؛ برای آلارم یک‌باره null (یعنی باید پاک شود).
     * هم بعد از شلیک و هم برای آلارم‌های تکراری‌ای که گوشی خاموش بوده و از دست رفته‌اند استفاده می‌شود.
     */
    fun advance(nowMs: Long, zone: ZoneId): SingleAlarm? {
        if (!repeating) return null
        val t = Instant.ofEpochMilli(triggerAtMs).atZone(zone)
        val minute = t.hour * 60 + t.minute
        val start = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (i in 0..8) {
            val d = start.plusDays(i.toLong())
            if ((repeat and bit(d.dayOfWeek)) == 0) continue
            val at = at(d, minute, zone)
            if (at > nowMs) return copy(triggerAtMs = at)
        }
        return null
    }

    val isSub: Boolean get() = kind == KIND_SUB_OUT || kind == KIND_SUB_IN

    val title: String get() = when (kind) {
        KIND_SUB_OUT -> "ثبت خروج قبل از نیمه‌شب"
        KIND_SUB_IN -> "ثبت ورود بعد از نیمه‌شب"
        else -> label.ifBlank { "آلارم" }
    }
    val body: String get() = if (isSub) "کارت‌زنی و چهره‌زنی را انجام بده." else if (label.isBlank()) "" else "آلارم تکی"

    companion object {
        const val ALL_DAYS = 127
        fun bit(d: DayOfWeek): Int = 1 shl (d.value - 1)

        /** ترتیب هفتهٔ ایرانی با نام کامل و کوتاه. */
        val WEEK: List<Triple<DayOfWeek, String, String>> = listOf(
            Triple(DayOfWeek.SATURDAY, "شنبه", "ش"), Triple(DayOfWeek.SUNDAY, "یکشنبه", "ی"),
            Triple(DayOfWeek.MONDAY, "دوشنبه", "د"), Triple(DayOfWeek.TUESDAY, "سه‌شنبه", "س"),
            Triple(DayOfWeek.WEDNESDAY, "چهارشنبه", "چ"), Triple(DayOfWeek.THURSDAY, "پنجشنبه", "پ"),
            Triple(DayOfWeek.FRIDAY, "جمعه", "ج")
        )

        fun repeatText(mask: Int): String = when {
            (mask and ALL_DAYS) == 0 -> ""
            (mask and ALL_DAYS) == ALL_DAYS -> "هر روز"
            else -> "هر " + WEEK.filter { (mask and bit(it.first)) != 0 }.joinToString("، ") { it.second }
        }

        const val KIND_USER = "USER"
        const val KIND_SUB_OUT = "SUB_OUT"
        const val KIND_SUB_IN = "SUB_IN"
        private const val FS = '\u001f'   // جداکنندهٔ فیلد
        private const val RS = '\u001e'   // جداکنندهٔ رکورد

        private fun clean(s: String) = s.filter { it != FS && it != RS }.take(60)

        fun encode(list: List<SingleAlarm>): String =
            list.joinToString(RS.toString()) { listOf(it.id, it.triggerAtMs, clean(it.label), it.kind, it.enabled, it.repeat).joinToString(FS.toString()) }

        /** مقدار خراب نادیده گرفته می‌شود؛ هیچ‌وقت استثنا نمی‌دهد. */
        fun decode(s: String?): List<SingleAlarm> {
            if (s.isNullOrBlank()) return emptyList()
            return s.split(RS).mapNotNull { rec ->
                val p = rec.split(FS)
                if (p.size < 5) return@mapNotNull null
                val id = p[0].toLongOrNull() ?: return@mapNotNull null
                val at = p[1].toLongOrNull() ?: return@mapNotNull null
                val kind = p[3].takeIf { it == KIND_USER || it == KIND_SUB_OUT || it == KIND_SUB_IN } ?: KIND_USER
                SingleAlarm(id, at, p[2], kind, p[4].toBooleanStrictOrNull() ?: true, (p.getOrNull(5)?.toIntOrNull() ?: 0) and ALL_DAYS)
            }
        }

        /** شمارهٔ یکتا برای آلارم جدید. */
        fun nextId(list: List<SingleAlarm>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L

        /** لحظهٔ مطلقِ «تاریخ + دقیقهٔ روز». */
        fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
            date.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
    }
}

/** منطق «جانشین شب»: شبِ [night] ساعت [outMin] خروج، و بامداد روز بعد ساعت [inMin] ورود مجدد. */
object SubNight {
    const val DEFAULT_OUT = 23 * 60 + 55
    const val DEFAULT_IN = 5

    /** فقط هشدارهای هنوز نیامده ساخته می‌شود؛ اگر هر دو گذشته باشند لیست خالی است. */
    fun plan(nowMs: Long, zone: ZoneId, night: LocalDate, outMin: Int, inMin: Int, startId: Long): List<SingleAlarm> {
        val out = SingleAlarm(startId, SingleAlarm.at(night, outMin, zone), kind = SingleAlarm.KIND_SUB_OUT)
        val inn = SingleAlarm(startId + 1, SingleAlarm.at(night.plusDays(1), inMin, zone), kind = SingleAlarm.KIND_SUB_IN)
        return listOf(out, inn).filter { it.triggerAtMs > nowMs }
    }

    /** آیا جانشین شب فعال است؟ (حداقل یک هشدار آیندهٔ فعال) */
    fun active(list: List<SingleAlarm>, nowMs: Long): List<SingleAlarm> =
        list.filter { it.isSub && it.enabled && it.triggerAtMs > nowMs }.sortedBy { it.triggerAtMs }

    /** شبی که برای آن فعال شده (تاریخ میلادی شب)؛ null اگر فعال نیست. */
    fun nightOf(list: List<SingleAlarm>, nowMs: Long, zone: ZoneId): LocalDate? {
        val first = active(list, nowMs).firstOrNull() ?: return null
        val d = Instant.ofEpochMilli(first.triggerAtMs).atZone(zone).toLocalDate()
        return if (first.kind == SingleAlarm.KIND_SUB_IN) d.minusDays(1) else d
    }
}
