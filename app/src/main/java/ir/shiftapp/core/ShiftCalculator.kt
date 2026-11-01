package ir.shiftapp.core

/** نوع شیفت. کدهای ۱ تا ۶ دقیقاً همان کدهای APK اصلی‌اند. */
data class ShiftType(val code: Int, val name: String, val color: Long, val isRest: Boolean = false) {
    companion object {
        val DEFAULTS = listOf(
            ShiftType(1, "روزکار", 0xFFFFD166),
            ShiftType(2, "عصرکار", 0xFFFF9F68),
            ShiftType(3, "شبکار", 0xFF8C9BFF),
            ShiftType(4, "استراحت", 0xFFDDE5F0, isRest = true),
            ShiftType(5, "۲۴ ساعته", 0xFF4ECDC4),
            ShiftType(6, "جدید ۲", 0xFFB8C2D3),
        )
        fun byCode(code: Int) = DEFAULTS.firstOrNull { it.code == code }
    }
}

/** الگوی چرخه‌ای شیفت؛ در دیتابیس به شکل «1;1;2;2;4;3;3;4» (قالب APK اصلی) ذخیره می‌شود. */
class ShiftPattern private constructor(val codes: List<Int>) {
    val cycleLength: Int get() = codes.size
    fun serialize() = codes.joinToString(";")

    companion object {
        /** برخلاف APK اصلی: الگوی خالی یا کد ناشناخته بی‌صدا نادیده گرفته نمی‌شود و خطا می‌دهد. */
        fun of(codes: List<Int>): ShiftPattern {
            require(codes.isNotEmpty()) { "الگوی شیفت نباید خالی باشد" }
            require(codes.all { it >= 1 }) { "کد شیفت نامعتبر در الگو: $codes" }
            return ShiftPattern(codes.toList())
        }
        fun parse(text: String): ShiftPattern =
            of(text.split(';').filter { it.isNotBlank() }.map {
                it.trim().toIntOrNull() ?: throw IllegalArgumentException("کد شیفت نامعتبر: '$it'")
            })
    }
}

/**
 * ShiftCalculator — نسخهٔ IMPROVED.
 * فرمول هستهٔ APK اصلی (اختلاف روز نسبت به مبدأ، باقیمانده بر طول چرخه، اصلاح منفی) حفظ شده؛
 * خطاهای APK اصلی برطرف شده‌اند:
 *  1) تاریخ قبل از ۱۳۹۷ دیگر به شمارندهٔ ثابت ۱- نمی‌رسد (JDN واقعی برای هر سال معتبر).
 *  2) تاریخ نامعتبر (مثلاً ۳۰ اسفند سال عادی) رد می‌شود، نه اینکه با روز بعد یکی شود.
 *  3) یک قانون کبیسهٔ واحد برای کل برنامه (در ۱۳۹۷..۱۶۳۳ دقیقاً برابر قانون ۳۳ ساله‌ی APK).
 *  4) الگوی خالی و کد نامعتبر پذیرفته نمی‌شود.
 */
object ShiftCalculator {
    fun shiftIndex(base: JalaliDate, target: JalaliDate, cycleLength: Int): Int {
        require(cycleLength > 0) { "طول چرخه باید مثبت باشد" }
        val diff = target.toJdn() - base.toJdn()
        return Math.floorMod(diff, cycleLength)
    }

    fun calculateShift(base: JalaliDate, target: JalaliDate, pattern: ShiftPattern): Int =
        pattern.codes[shiftIndex(base, target, pattern.cycleLength)]
}
