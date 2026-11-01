package ir.shiftapp.core

/** ردیف کاردکس: وضعیت مؤثر یک روز برای الگوی انتخاب‌شده + وقایع ثبت‌شده. */
data class CardexRow(val date: JalaliDate, val day: EffectiveDay) {
    fun has(type: String) = day.entries.any { it.type == type }
    fun count(type: String) = day.entries.count { it.type == type }
}

/** منطق خالص تب «کاردکس»: ستون‌های یادداشت، استراحت، مرخصی، تعویض شیفت، اضافه‌کار. */
object CardexTable {
    /** ترتیب ستون‌ها دقیقاً مثل برنامهٔ نمونه (از راست به چپ). */
    val COLUMNS = listOf(EntryType.NOTE, EntryType.REST, EntryType.LEAVE, EntryType.EXCHANGE, EntryType.OVERTIME)

    fun build(year: Int, month: Int, base: JalaliDate, pattern: ShiftPattern, entriesByDate: Map<String, List<Entry>>): List<CardexRow> =
        (1..JalaliCalendar.daysInMonth(year, month)).map { d ->
            val date = JalaliDate(year, month, d)
            CardexRow(date, DayResolver.resolve(base, pattern, date, entriesByDate[date.key].orEmpty()))
        }
}
