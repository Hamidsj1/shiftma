package ir.shiftapp.core

/**
 * متن اعلان همیشگی «تاریخ و اطلاعات» در نوار اعلان‌های گوشی. منطق خالص و قابل تست؛
 * ساختن و نمایش اعلان در system/DateBar.kt است.
 */
object DateBarText {
    /** [title] تاریخ شمسی با روز هفته؛ [text] خط دوم (میلادی و قمری)؛ [sub] شیفت امروز کنار نام برنامه؛ [big] متن کامل اعلان بازشده. */
    class Content(val title: String, val text: String, val sub: String?, val big: String)

    fun build(
        today: JalaliDate, gregorian: String, hijri: String?,
        todayShift: String?, tomorrowShift: String?, holiday: String?, prayer: String?
    ): Content {
        val title = "${Fa.WEEK_FULL[today.dayOfWeek]} ${Fa.num(today.day)} ${Fa.MONTHS[today.month - 1]} ${Fa.num(today.year)}"
        val dateLine = if (hijri != null) "$gregorian · $hijri" else gregorian
        val lines = buildList {
            add(dateLine)
            if (!holiday.isNullOrBlank()) add("تعطیل: $holiday")
            if (todayShift != null) add("امروز: $todayShift" + if (tomorrowShift != null) " · فردا: $tomorrowShift" else "")
            if (!prayer.isNullOrBlank()) add(prayer)
        }
        return Content(title, dateLine, todayShift?.let { "امروز: $it" }, lines.joinToString("\n"))
    }
}
