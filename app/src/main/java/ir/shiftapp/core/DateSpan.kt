package ir.shiftapp.core

/** مدت بین دو تاریخ شمسی: به شیوهٔ تقویم (سال، ماه، روز) و به شیوهٔ بیمه (ماه ۳۰ روز، سال ۳۶۰ روز). */
class SpanResult(val years: Int, val months: Int, val days: Int, val totalDays: Int, val insYears: Int, val insMonths: Int, val insDays: Int)

object DateSpan {
    /** [inclusive] = روز شروع و روز پایان هر دو شمرده شوند. null اگر پایان قبل از شروع باشد. */
    fun compute(from: JalaliDate, to: JalaliDate, inclusive: Boolean = true): SpanResult? {
        if (to < from) return null
        val end = if (inclusive) to.plusDays(1) else to      // پایانِ باز
        var y = end.year - from.year
        var m = end.month - from.month
        var d = end.day - from.day
        if (d < 0) {
            m--
            val pm = if (end.month == 1) 12 else end.month - 1
            val py = if (end.month == 1) end.year - 1 else end.year
            d += JalaliCalendar.daysInMonth(py, pm)
        }
        if (m < 0) { y--; m += 12 }
        val total = to.toJdn() - from.toJdn() + if (inclusive) 1 else 0
        val ins = maxOf(0, days360(from, to) - if (inclusive) 0 else 1)
        return SpanResult(y, m, d, total, ins / 360, ins % 360 / 30, ins % 30)
    }

    private fun clampDay(date: JalaliDate, asEnd: Boolean): Int {
        val last = JalaliCalendar.daysInMonth(date.year, date.month)
        return if (asEnd && date.day == last) 30 else minOf(date.day, 30)
    }

    /** روزهای بازهٔ بسته به روش ۳۰/۳۶۰ (روز ۳۱ و آخر ماه = ۳۰). */
    fun days360(from: JalaliDate, to: JalaliDate): Int =
        (to.year - from.year) * 360 + (to.month - from.month) * 30 + (clampDay(to, true) - clampDay(from, false)) + 1
}
