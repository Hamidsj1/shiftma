package ir.shiftapp.core

data class GridCell(val text: String, val bg: Long? = null, val fg: Long? = null)

/** مدل مستقل از قالب: همین جدول هم به PDF، هم Excel و هم تصویر تبدیل می‌شود (ستون اول = سمت راست). */
data class GridTable(
    val title: String, val subtitle: String,
    val headers: List<String>, val weights: List<Float>,
    val rows: List<List<GridCell>>, val footer: List<String> = emptyList()
)

object ExportModels {
    private const val HOLIDAY_FG = 0xFFC62828L
    private fun shiftName(c: Int) = ShiftType.byCode(c)?.name ?: "کد ${Fa.num(c)}"
    private fun shiftBg(c: Int) = ShiftType.byCode(c)?.color

    fun cardex(
        year: Int, month: Int, patternName: String, base: JalaliDate, pattern: ShiftPattern,
        entries: Map<String, List<Entry>>, holidays: HolidayCalendar, times: Map<Int, ShiftTime>
    ): GridTable {
        val rows = (1..JalaliCalendar.daysInMonth(year, month)).map { d ->
            val date = JalaliDate(year, month, d)
            val e = DayResolver.resolve(base, pattern, date, entries[date.key].orEmpty())
            val kind = holidays.kind(date)
            val fg = if (kind != DayKind.NORMAL) HOLIDAY_FG else null
            val notes = e.entries.filter { it.type == EntryType.NOTE }.map { it.text }.filter { it.isNotBlank() }
            val note = listOfNotNull(holidays.title(date)?.let { "تعطیل: $it" }, notes.takeIf { it.isNotEmpty() }?.joinToString(" | ")).joinToString(" — ")
            fun mark(has: Boolean) = GridCell(if (has) "•" else "")
            listOf(
                GridCell(Fa.num(d), fg = fg),
                GridCell(Fa.WEEK_FULL[date.dayOfWeek], fg = fg),
                GridCell(shiftName(e.effectiveCode) + if (e.overridden) " *" else "", bg = shiftBg(e.effectiveCode)),
                GridCell(note),
                mark(e.entries.any { it.type == EntryType.REST }),
                mark(e.onLeave),
                mark(e.entries.any { it.type == EntryType.EXCHANGE }),
                GridCell(if (e.overtimeMinutes > 0) Fa.hm(e.overtimeMinutes) else "")
            )
        }
        val h = HoursCalculator.month(year, month, base, pattern, entries, times, holidays)
        val perShift = h.minutesByShift.entries.joinToString(" | ") { (c, m) -> "${shiftName(c)} ${Fa.hm(m)}" }
        val footer = buildList {
            add("جمع ساعت کار: ${Fa.hm(h.totalMinutes)}" + if (perShift.isNotEmpty()) "  ($perShift)" else "")
            add("کار در تعطیل رسمی: ${Fa.num(h.officialHolidayDays)} روز، ${Fa.hm(h.officialHolidayMinutes)}  |  کار در جمعه: ${Fa.num(h.fridayDays)} روز، ${Fa.hm(h.fridayMinutes)}")
            add("اضافه‌کاری ثبت‌شده: ${Fa.hm(h.overtimeMinutes)}")
            if (h.shiftsWithoutTime.isNotEmpty()) add("ساعت این شیفت‌ها تنظیم نشده (صفر حساب شد): " + h.shiftsWithoutTime.joinToString("، ") { shiftName(it) })
        }
        return GridTable(
            "کاردکس شیفت $patternName", "${Fa.MONTHS[month - 1]} ${Fa.num(year)}",
            listOf("روز", "هفته", "شیفت", "یادداشت", "استراحت", "مرخصی", "تعویض", "اضافه‌کار"),
            listOf(0.8f, 1.5f, 1.5f, 3.2f, 1.1f, 1.1f, 1.1f, 1.5f), rows, footer
        )
    }

    /** ستون‌های خالی (null) در خروجی نمی‌آیند. */
    fun fourShift(year: Int, month: Int, columns: List<FourShiftColumn?>, holidays: HolidayCalendar): GridTable {
        val used = columns.filterNotNull()
        val table = FourShiftTable.build(year, month, columns)
        val rows = table.map { r ->
            val kind = holidays.kind(r.date)
            val fg = if (kind != DayKind.NORMAL) HOLIDAY_FG else null
            listOf(GridCell(Fa.num(r.date.day), fg = fg), GridCell(Fa.WEEK_FULL[r.date.dayOfWeek], fg = fg)) +
                r.cells.filterIndexed { i, _ -> columns[i] != null }.map { c ->
                    GridCell(shiftName(c!!.effectiveCode) + if (c.overridden) " *" else "", bg = shiftBg(c.effectiveCode))
                }
        }
        return GridTable(
            "جدول چهارشیفت", "${Fa.MONTHS[month - 1]} ${Fa.num(year)}",
            listOf("روز", "هفته") + used.map { if (it.subtitle.isBlank()) it.title else "${it.title} (${it.subtitle})" },
            listOf(0.8f, 1.5f) + used.map { 1.5f }, rows
        )
    }
}
