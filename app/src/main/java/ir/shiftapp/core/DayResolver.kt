package ir.shiftapp.core

object EntryType {
    const val MANUAL_SHIFT = "MANUAL_SHIFT"   // تغییر دستی شیفت
    const val EXCHANGE = "EXCHANGE"           // تعویض شیفت
    const val LEAVE = "LEAVE"                 // مرخصی
    const val OVERTIME = "OVERTIME"           // اضافه‌کاری (دقیقه)
    const val REST = "REST"                   // استراحت (علامت‌گذاری دستی)
    const val NOTE = "NOTE"                   // یادداشت
    val ALL = listOf(MANUAL_SHIFT, EXCHANGE, LEAVE, OVERTIME, REST, NOTE)
}

/** نمای خالص یک ثبت روزانه (مستقل از Room تا قابل Unit Test باشد). */
data class Entry(val type: String, val shiftCode: Int? = null, val minutes: Int? = null, val text: String = "")

data class EffectiveDay(
    val date: JalaliDate,
    val computedCode: Int,        // خروجی خالص الگوریتم
    val effectiveCode: Int,       // بعد از اعمال تغییر دستی/تعویض
    val entries: List<Entry>
) {
    val overridden: Boolean get() = computedCode != effectiveCode
    val onLeave: Boolean get() = entries.any { it.type == EntryType.LEAVE }
    val overtimeMinutes: Int get() = entries.filter { it.type == EntryType.OVERTIME }.sumOf { it.minutes ?: 0 }
}

/**
 * جداسازی الگوریتم اصلی از override ها (همان اصلی که در APK اصلی هم بود):
 * computedCode همیشه خروجی خالص ShiftCalculator است؛ تغییر دستی/تعویض فقط effectiveCode را عوض می‌کند.
 * اگر چند تغییر در یک روز باشد، آخرین ثبت (به ترتیب ورود) برنده است.
 */
object DayResolver {
    fun resolve(base: JalaliDate, pattern: ShiftPattern, date: JalaliDate, entries: List<Entry>): EffectiveDay {
        val computed = ShiftCalculator.calculateShift(base, date, pattern)
        val override = entries.lastOrNull { (it.type == EntryType.MANUAL_SHIFT || it.type == EntryType.EXCHANGE) && it.shiftCode != null }
        return EffectiveDay(date, computed, override?.shiftCode ?: computed, entries)
    }
}

data class MonthReport(
    val year: Int, val month: Int,
    val daysByShift: Map<Int, Int>,   // کد شیفت مؤثر -> تعداد روز
    val workDays: Int, val leaveDays: Int, val overtimeMinutes: Int, val exchangeCount: Int, val manualChangeCount: Int, val restMarks: Int
) {
}

object ReportCalculator {
    fun month(year: Int, month: Int, base: JalaliDate, pattern: ShiftPattern, entriesByDate: Map<String, List<Entry>>): MonthReport {
        val counts = sortedMapOf<Int, Int>()
        var work = 0; var leave = 0; var ot = 0; var ex = 0; var manual = 0; var rest = 0
        for (d in 1..JalaliCalendar.daysInMonth(year, month)) {
            val date = JalaliDate(year, month, d)
            val e = DayResolver.resolve(base, pattern, date, entriesByDate[date.key].orEmpty())
            counts[e.effectiveCode] = (counts[e.effectiveCode] ?: 0) + 1
            if (e.onLeave) leave++
            if (!e.onLeave && ShiftType.byCode(e.effectiveCode)?.isRest != true) work++
            ot += e.overtimeMinutes
            ex += e.entries.count { it.type == EntryType.EXCHANGE }
            manual += e.entries.count { it.type == EntryType.MANUAL_SHIFT }
            rest += e.entries.count { it.type == EntryType.REST }
        }
        return MonthReport(year, month, counts, work, leave, ot, ex, manual, rest)
    }
}
