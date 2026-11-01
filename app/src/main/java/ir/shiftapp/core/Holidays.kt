package ir.shiftapp.core

enum class DayKind { NORMAL, FRIDAY, OFFICIAL }

/**
 * تعطیلات ثابتِ تقویم شمسی. تعطیلات قمری (تاسوعا، عاشورا، عید فطر و ...) هر سال جابه‌جا می‌شوند و
 * اینجا حدس زده نمی‌شوند؛ کاربر آن‌ها را در «تعطیلات» وارد می‌کند (جدول holidays).
 */
object FixedHolidays {
    val LIST: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 1) to "جشن نوروز", (1 to 2) to "عیدنوروز", (1 to 3) to "عیدنوروز", (1 to 4) to "عیدنوروز",
        (1 to 12) to "روز جمهوری اسلامی", (1 to 13) to "روز طبیعت (سیزده‌به‌در)",
        (3 to 14) to "رحلت امام خمینی", (3 to 15) to "قیام ۱۵ خرداد",
        (11 to 22) to "پیروزی انقلاب اسلامی", (12 to 29) to "ملی شدن صنعت نفت"
    )
}

class HolidayCalendar(private val custom: Map<String, String>, private val fridayOff: Boolean) {
    /** عنوان تعطیلی رسمی (ثابت یا واردشده توسط کاربر)؛ null اگر تعطیل رسمی نیست. */
    fun title(date: JalaliDate): String? = custom[date.key] ?: FixedHolidays.LIST[date.month to date.day]

    /** تعطیل رسمی بر جمعه مقدم است تا ساعت کار در تعطیل رسمی دوبار شمرده نشود. */
    fun kind(date: JalaliDate): DayKind = when {
        title(date) != null -> DayKind.OFFICIAL
        fridayOff && date.dayOfWeek == 6 -> DayKind.FRIDAY
        else -> DayKind.NORMAL
    }
}
