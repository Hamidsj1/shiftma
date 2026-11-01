package ir.shiftapp.core

/** یک ستون از جدول چهارشیفت: یک الگوی شیفت (نام + شرکت) با تاریخ مبنا و ثبت‌های روزانهٔ خودش. */
data class FourShiftColumn(
    val patternId: Long,
    val title: String,
    val subtitle: String,
    val base: JalaliDate,
    val pattern: ShiftPattern,
    val entriesByDate: Map<String, List<Entry>> = emptyMap()
)

/** یک ردیف جدول: تاریخ + وضعیت مؤثر هر ستون (null = ستون خالی). */
data class FourShiftRow(val date: JalaliDate, val cells: List<EffectiveDay?>)

/** منطق خالص تب «چهارشیفت»: برای هر روز ماه، شیفت هر چهار ستون را با همان [DayResolver] برنامه حساب می‌کند. */
object FourShiftTable {
    const val SLOTS = 4

    fun build(year: Int, month: Int, columns: List<FourShiftColumn?>): List<FourShiftRow> {
        require(columns.size == SLOTS) { "جدول چهارشیفت دقیقاً $SLOTS ستون دارد" }
        return (1..JalaliCalendar.daysInMonth(year, month)).map { d ->
            val date = JalaliDate(year, month, d)
            FourShiftRow(date, columns.map { c ->
                c?.let { DayResolver.resolve(it.base, it.pattern, date, it.entriesByDate[date.key].orEmpty()) }
            })
        }
    }
}
