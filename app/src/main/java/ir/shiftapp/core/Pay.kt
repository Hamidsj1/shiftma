package ir.shiftapp.core

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * تنظیمات برآورد حقوق. درصدها پیش‌فرضِ قانون کار هستند (اضافه‌کاری ۴۰٪ ماده ۵۹، شب‌کاری ۳۵٪ ماده ۵۸،
 * جمعه‌کاری ۴۰٪ ماده ۶۲)؛ قرارداد یا آیین‌نامهٔ هر کارخانه می‌تواند فرق داشته باشد، پس همه قابل‌تغییرند. ۰ یعنی «حساب نشود».
 */
data class PaySettings(
    val baseMonthly: Long = 0L,        // حقوق پایه/مبنای ماهانه (تومان)
    val divisor: Double = 220.0,       // مزد ساعتی = حقوق ماهانه ÷ این عدد (۳۰ روز × ۷ ساعت و ۲۰ دقیقه)
    val dueHours: Double = 0.0,        // ساعت کار موظفی ماه؛ ۰ = خودکار (۴۴ ساعت هفتگی تبدیل‌شده به روزهای ماه)
    val overtimePct: Double = 40.0,
    val nightPct: Double = 35.0,
    val fridayPct: Double = 40.0,
    val holidayPct: Double = 40.0,
    val shiftPct: Double = 0.0,        // فوق‌العاده نوبت‌کاری (معمولاً ۱۰ / ۱۵ / ۲۲٫۵)
    val benefits: Long = 0L,           // مزایای ثابت ماهانه (مسکن، خواربار، ...)
    val deductionPct: Double = 0.0     // کسورات تقریبی (بیمه، مالیات) به درصدِ ناخالص
) { val configured: Boolean get() = baseMonthly > 0 }

data class PayLine(val key: String, val title: String, val minutes: Int?, val amount: Long)

data class PayResult(
    val hourly: Long, val dueMinutes: Int, val workedMinutes: Int,
    val autoOvertimeMinutes: Int, val manualOvertimeMinutes: Int,
    val lines: List<PayLine>, val gross: Long, val deduction: Long, val net: Long
) { val overtimeMinutes: Int get() = autoOvertimeMinutes + manualOvertimeMinutes }

/**
 * برآورد ماهانه از روی جدول شیفت. هر عنوان جدا حساب می‌شود و روی هم اثر می‌گذارند (مثلاً ساعتِ شب‌کاریِ جمعه هم شب‌کاری دارد هم جمعه‌کاری).
 * اضافه‌کاری = کارکردِ بیش از موظفی + اضافه‌کاری که خودتان در برنامه ثبت کرده‌اید. کسرکار حساب نمی‌شود.
 */
object PayCalculator {
    fun autoDueMinutes(daysInMonth: Int): Int = (44.0 / 7.0 * daysInMonth * 60.0).roundToInt()

    fun compute(h: HoursReport, s: PaySettings, daysInMonth: Int): PayResult {
        val rate = if (s.divisor > 0) s.baseMonthly / s.divisor else 0.0
        val due = if (s.dueHours > 0) (s.dueHours * 60).roundToInt() else autoDueMinutes(daysInMonth)
        val auto = maxOf(0, h.totalMinutes - due)
        val ot = auto + h.overtimeMinutes
        fun money(min: Int, mult: Double) = (min / 60.0 * rate * mult).roundToLong()
        val lines = buildList {
            add(PayLine("base", "حقوق پایه", null, s.baseMonthly))
            if (s.shiftPct > 0) add(PayLine("shift", "فوق‌العاده نوبت‌کاری", null, (s.baseMonthly * s.shiftPct / 100.0).roundToLong()))
            add(PayLine("overtime", "اضافه‌کاری", ot, money(ot, 1 + s.overtimePct / 100.0)))
            add(PayLine("night", "فوق‌العاده شب‌کاری", h.nightMinutes, money(h.nightMinutes, s.nightPct / 100.0)))
            add(PayLine("friday", "فوق‌العاده جمعه‌کاری", h.fridayMinutes, money(h.fridayMinutes, s.fridayPct / 100.0)))
            add(PayLine("holiday", "فوق‌العاده تعطیل‌کاری", h.officialHolidayMinutes, money(h.officialHolidayMinutes, s.holidayPct / 100.0)))
            if (s.benefits > 0) add(PayLine("benefits", "مزایای ثابت", null, s.benefits))
        }
        val gross = lines.sumOf { it.amount }
        val ded = (gross * s.deductionPct / 100.0).roundToLong()
        return PayResult(rate.roundToLong(), due, h.totalMinutes, auto, h.overtimeMinutes, lines, gross, ded, gross - ded)
    }
}
