package ir.shiftapp.core

/** متن فارسی مشترک بین UI، اعلان‌ها و خروجی‌ها. */
object Fa {
    val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val WEEK_FULL = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    fun digits(s: String): String = buildString {
        for (ch in s) append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
    }
    fun num(n: Int): String = digits(n.toString())

    /** ارقام فارسی/عربی را به انگلیسی برمی‌گرداند (برای تبدیل ورودی کاربر به عدد). */
    fun latin(s: String): String = buildString {
        for (ch in s) append(when (ch) { in '۰'..'۹' -> '0' + (ch - '۰'); in '٠'..'٩' -> '0' + (ch - '٠'); else -> ch })
    }

    /** دقیقه -> «ساعت:دقیقه» مثل ۱۹۲:۳۰ */
    fun hm(totalMinutes: Int): String = digits("${totalMinutes / 60}:${"%02d".format(totalMinutes % 60)}")
}
