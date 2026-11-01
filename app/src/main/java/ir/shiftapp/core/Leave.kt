package ir.shiftapp.core

data class LeaveBalance(val year: Int, val capDays: Int, val usedDays: Int, val dates: List<JalaliDate>) {
    val remainingDays: Int get() = capDays - usedDays
    val exceeded: Boolean get() = remainingDays < 0
}

object LeaveCalculator {
    /**
     * مصرف مرخصی سال شمسی. هر روز فقط یک‌بار شمرده می‌شود (حتی با چند ثبت).
     * پیش‌فرض: مرخصیِ ثبت‌شده روی روز استراحتِ چرخه از سقف کم نمی‌شود (countRestDays=false)؛ این قاعده بسته به شرکت فرق دارد.
     */
    fun year(
        year: Int, capDays: Int, base: JalaliDate, pattern: ShiftPattern,
        entriesByDate: Map<String, List<Entry>>, countRestDays: Boolean
    ): LeaveBalance {
        val prefix = "%04d".format(year)
        val dates = entriesByDate.keys.filter { it.startsWith(prefix) }.sorted().mapNotNull { k ->
            val es = entriesByDate[k].orEmpty()
            if (es.none { it.type == EntryType.LEAVE }) return@mapNotNull null
            val date = JalaliDate.fromKey(k)
            val r = DayResolver.resolve(base, pattern, date, es)
            val rest = ShiftType.byCode(r.effectiveCode)?.isRest == true
            if (rest && !countRestDays) null else date
        }
        return LeaveBalance(year, capDays, dates.size, dates)
    }
}
