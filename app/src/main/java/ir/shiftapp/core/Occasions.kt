package ir.shiftapp.core

import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

enum class OccasionKind { SOLAR, GLOBAL, LUNAR }

/**
 * مناسبت‌ها (غیرتعطیل). سه دسته:
 * ۱) ایرانیِ ثابت در تقویم شمسی؛
 * ۲) جهانی (روزهای ثابت میلادی)؛
 * ۳) قمری: بر پایهٔ تقویم قمری (ام‌القری) محاسبه می‌شود و با رؤیت ماه در ایران ممکن است تا ۱–۲ روز فرق کند؛
 *    کاربر با تنظیم lunarShift آن را با تقویم رسمی تطبیق می‌دهد.
 * تعطیلی‌های رسمی از FixedHolidays / تعطیلاتِ واردشدهٔ کاربر می‌آیند.
 */
object Occasions {
    val LIST: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 20) to "روز ملی فناوری هسته‌ای", (1 to 29) to "روز ارتش",
        (2 to 1) to "روز بزرگداشت سعدی", (2 to 10) to "روز ملی خلیج فارس",
        (2 to 11) to "روز کارگر", (2 to 12) to "روز معلم",
        (3 to 3) to "آزادسازی خرمشهر",
        (6 to 1) to "روز پزشک",
        (7 to 7) to "روز آتش‌نشانی و ایمنی",
        (8 to 13) to "روز دانش‌آموز", (8 to 24) to "روز کتاب و کتابخوانی",
        (9 to 5) to "روز بسیج", (9 to 16) to "روز دانشجو", (9 to 30) to "شب یلدا",
        (11 to 12) to "بازگشت امام خمینی به ایران", (11 to 19) to "روز نیروی هوایی",
        (12 to 15) to "روز درختکاری"
    )

    /** مناسبت‌های جهانی؛ کلید: (ماه میلادی, روز). */
    val GLOBAL: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 1) to "آغاز سال نو میلادی",
        (2 to 14) to "ولنتاین",
        (3 to 8) to "روز جهانی زن",
        (3 to 20) to "روز جهانی شادی",
        (3 to 22) to "روز جهانی آب",
        (4 to 7) to "روز جهانی بهداشت",
        (4 to 22) to "روز جهانی زمین",
        (4 to 23) to "روز جهانی کتاب",
        (4 to 28) to "روز جهانی ایمنی و بهداشت کار",
        (5 to 1) to "روز جهانی کارگر",
        (5 to 12) to "روز جهانی پرستار",
        (6 to 5) to "روز جهانی محیط زیست",
        (7 to 11) to "روز جهانی جمعیت",
        (8 to 12) to "روز جهانی جوانان",
        (9 to 8) to "روز جهانی سواد",
        (9 to 21) to "روز جهانی صلح",
        (9 to 27) to "روز جهانی گردشگری",
        (10 to 1) to "روز جهانی سالمندان",
        (10 to 5) to "روز جهانی معلم",
        (10 to 10) to "روز جهانی سلامت روان",
        (10 to 16) to "روز جهانی غذا",
        (11 to 14) to "روز جهانی دیابت",
        (11 to 20) to "روز جهانی کودک",
        (12 to 1) to "روز جهانی ایدز",
        (12 to 3) to "روز جهانی افراد دارای معلولیت",
        (12 to 10) to "روز جهانی حقوق بشر",
        (12 to 25) to "کریسمس"
    )

    /** مناسبت‌های قمری؛ کلید: ماه*100+روز (۱ محرم … ۱۲ ذی‌الحجه). */
    val LUNAR: Map<Int, String> = mapOf(
        101 to "آغاز سال هجری قمری", 109 to "تاسوعا", 110 to "عاشورا", 125 to "شهادت امام سجاد (ع)",
        207 to "ولادت امام کاظم (ع)", 220 to "اربعین حسینی", 228 to "رحلت پیامبر و شهادت امام حسن (ع)", 230 to "شهادت امام رضا (ع)",
        308 to "شهادت امام حسن عسکری (ع)", 309 to "آغاز امامت امام زمان (عج)", 317 to "ولادت پیامبر (ص) و امام صادق (ع)",
        408 to "ولادت امام حسن عسکری (ع)",
        505 to "ولادت حضرت زینب (س)",
        603 to "شهادت حضرت فاطمه (س)", 620 to "ولادت حضرت فاطمه (س)",
        701 to "ولادت امام باقر (ع)", 703 to "شهادت امام هادی (ع)", 710 to "ولادت امام جواد (ع)", 713 to "ولادت امام علی (ع)",
        725 to "شهادت امام کاظم (ع)", 727 to "مبعث پیامبر (ص)",
        803 to "ولادت امام حسین (ع)", 804 to "ولادت حضرت عباس (ع)", 805 to "ولادت امام سجاد (ع)", 815 to "ولادت امام زمان (عج)",
        901 to "آغاز ماه رمضان", 915 to "ولادت امام حسن مجتبی (ع)", 919 to "ضربت خوردن امام علی (ع)", 921 to "شهادت امام علی (ع)",
        1001 to "عید فطر", 1025 to "شهادت امام صادق (ع)",
        1101 to "ولادت حضرت معصومه (س)", 1111 to "ولادت امام رضا (ع)",
        1201 to "ازدواج امام علی (ع) و حضرت فاطمه (س)", 1209 to "روز عرفه", 1210 to "عید قربان", 1215 to "ولادت امام هادی (ع)",
        1218 to "عید غدیر خم", 1224 to "روز مباهله"
    )

    fun title(date: JalaliDate): String? = LIST[date.month to date.day]

    /** مناسبت‌های ثابتِ میلادی در این روز. */
    fun global(date: JalaliDate): String? = date.toLocalDate().let { GLOBAL[it.monthValue to it.dayOfMonth] }

    /** مناسبت‌های قمریِ این روز؛ shift = تطبیق با رؤیت ایران: n روز یعنی مناسبت‌ها n روز دیرتر (منفی = زودتر) بیایند. ماه ۲۹روزه، رویداد ۳۰ام را به ۲۹ام می‌آورد. */
    fun lunar(date: JalaliDate, shift: Int = 0): List<String> {
        val h = runCatching { HijrahDate.from(date.toLocalDate().plusDays(-shift.toLong())) }.getOrNull() ?: return emptyList()
        val m = h.get(ChronoField.MONTH_OF_YEAR); val d = h.get(ChronoField.DAY_OF_MONTH)
        val out = ArrayList<String>(2)
        LUNAR[m * 100 + d]?.let { out += it }
        if (d == 29 && h.lengthOfMonth() == 29) LUNAR[m * 100 + 30]?.let { out += it }
        return out
    }

    /** همهٔ مناسبت‌های غیرتعطیلِ این روز با نوعشان. */
    fun all(date: JalaliDate, lunarShift: Int = 0): List<Pair<String, OccasionKind>> {
        val out = ArrayList<Pair<String, OccasionKind>>(3)
        title(date)?.let { out += it to OccasionKind.SOLAR }
        global(date)?.let { out += it to OccasionKind.GLOBAL }
        lunar(date, lunarShift).forEach { out += it to OccasionKind.LUNAR }
        return out
    }
}

/** یک مورد از فهرست «مناسبت‌های پیش‌رو». */
data class UpcomingItem(val date: JalaliDate, val title: String, val holiday: Boolean, val daysLeft: Int, val kind: OccasionKind = OccasionKind.SOLAR)

object UpcomingOccasions {
    /** مناسبت‌ها و تعطیلی‌های رسمیِ پیش‌رو (از امروز تا یک سال بعد) به ترتیب تاریخ. */
    fun upcoming(today: JalaliDate, cal: HolidayCalendar, lunarShift: Int = 0, horizonDays: Int = 366, limit: Int = 400): List<UpcomingItem> {
        val out = ArrayList<UpcomingItem>()
        for (i in 0..horizonDays) {
            val d = today.plusDays(i)
            val h = cal.title(d)
            if (h != null) out += UpcomingItem(d, h, true, i)
            Occasions.all(d, lunarShift).forEach { (t, k) -> if (t != h) out += UpcomingItem(d, t, false, i, k) }
            if (out.size >= limit) break
        }
        return out
    }
}

/** «امروز» / «فردا» / «۱۲ روز مانده». */
fun daysLeftText(n: Int): String = when (n) {
    0 -> "امروز"
    1 -> "فردا"
    else -> "${n.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")} روز مانده"
}
