package ir.shiftapp.core

data class HoursReport(
    val minutesByShift: Map<Int, Int>,   // کد شیفت -> مجموع دقیقهٔ کار
    val daysByShift: Map<Int, Int>,
    val totalMinutes: Int,
    val officialHolidayMinutes: Int, val officialHolidayDays: Int,   // کار در تعطیل رسمی
    val fridayMinutes: Int, val fridayDays: Int,                      // کار در جمعهٔ غیرتعطیل‌رسمی
    val overtimeMinutes: Int,                                          // اضافه‌کاریِ ثبت‌شدهٔ دستی
    val shiftsWithoutTime: Set<Int>,                                   // شیفت‌هایی که ساعتشان تنظیم نشده (۰ حساب شده‌اند)
    val nightMinutes: Int = 0                                          // ساعت‌های کار بین ۲۲ تا ۶ بامداد (ماده ۵۳ قانون کار)
)

/**
 * جمع ساعت کار ماه. روز مرخصی و روز استراحت ساعت ندارد.
 * شیفتی که از نیمه‌شب می‌گذرد (شبکار) کامل به روز شروعش نسبت داده می‌شود.
 */
object HoursCalculator {
    private val NIGHT_WINDOWS = listOf(0 to 360, 1320 to 1800, 2760 to 3240)   // ۰۰–۰۶ ، ۲۲–۰۶ فردا، ۲۲–۰۶ پس‌فردا (شیفت حداکثر ۲۴ ساعته)

    /** دقیقه‌های یک شیفت که در ۲۲:۰۰ تا ۰۶:۰۰ می‌افتد. */
    fun nightMinutes(t: ShiftTime): Int {
        val s = t.startMin; val e = s + t.durationMin
        return NIGHT_WINDOWS.sumOf { (a, b) -> maxOf(0, minOf(e, b) - maxOf(s, a)) }
    }

    fun month(
        year: Int, month: Int, base: JalaliDate, pattern: ShiftPattern,
        entriesByDate: Map<String, List<Entry>>, times: Map<Int, ShiftTime>, holidays: HolidayCalendar
    ): HoursReport {
        val minutes = sortedMapOf<Int, Int>(); val days = sortedMapOf<Int, Int>(); val missing = sortedSetOf<Int>()
        var total = 0; var night = 0; var offMin = 0; var offDays = 0; var friMin = 0; var friDays = 0; var ot = 0
        for (d in 1..JalaliCalendar.daysInMonth(year, month)) {
            val date = JalaliDate(year, month, d)
            val e = DayResolver.resolve(base, pattern, date, entriesByDate[date.key].orEmpty())
            ot += e.overtimeMinutes
            if (e.onLeave || ShiftType.byCode(e.effectiveCode)?.isRest == true) continue
            val c = e.effectiveCode
            val t = times[c]
            if (t == null) missing.add(c)
            val m = t?.durationMin ?: 0
            days[c] = (days[c] ?: 0) + 1
            minutes[c] = (minutes[c] ?: 0) + m
            total += m
            night += t?.let { nightMinutes(it) } ?: 0
            when (holidays.kind(date)) {
                DayKind.OFFICIAL -> { offMin += m; offDays++ }
                DayKind.FRIDAY -> { friMin += m; friDays++ }
                DayKind.NORMAL -> {}
            }
        }
        return HoursReport(minutes, days, total, offMin, offDays, friMin, friDays, ot, missing, night)
    }
}
