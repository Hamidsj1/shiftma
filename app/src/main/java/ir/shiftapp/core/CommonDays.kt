package ir.shiftapp.core

data class CommonDays(
    val bothWork: List<JalaliDate>,       // هر دو سر کارند
    val bothOff: List<JalaliDate>,        // هیچ‌کدام کار نمی‌کنند (استراحت یا مرخصی)
    val bothSameShift: List<JalaliDate>,  // هر دو یک شیفت یکسان دارند
    val onlyA: List<JalaliDate>,          // فقط A کار می‌کند (B آزاد است)
    val onlyB: List<JalaliDate>
)

object CommonDaysFinder {
    private fun works(e: EffectiveDay) = !e.onLeave && ShiftType.byCode(e.effectiveCode)?.isRest != true

    fun find(year: Int, month: Int, a: FourShiftColumn, b: FourShiftColumn): CommonDays {
        val bothWork = mutableListOf<JalaliDate>(); val bothOff = mutableListOf<JalaliDate>()
        val same = mutableListOf<JalaliDate>(); val onlyA = mutableListOf<JalaliDate>(); val onlyB = mutableListOf<JalaliDate>()
        for (d in 1..JalaliCalendar.daysInMonth(year, month)) {
            val date = JalaliDate(year, month, d)
            val ea = DayResolver.resolve(a.base, a.pattern, date, a.entriesByDate[date.key].orEmpty())
            val eb = DayResolver.resolve(b.base, b.pattern, date, b.entriesByDate[date.key].orEmpty())
            val wa = works(ea); val wb = works(eb)
            when {
                wa && wb -> { bothWork.add(date); if (ea.effectiveCode == eb.effectiveCode) same.add(date) }
                !wa && !wb -> bothOff.add(date)
                wa -> onlyA.add(date)
                else -> onlyB.add(date)
            }
        }
        return CommonDays(bothWork, bothOff, same, onlyA, onlyB)
    }
}
