package ir.shiftapp.core

import java.math.BigDecimal
import java.math.MathContext

// ───────────────────────── تبدیل واحد ─────────────────────────

/** مقدار پایه = مقدار × toBase + offset (offset فقط برای دماست؛ پایهٔ دما سانتی‌گراد است). */
data class ConvUnit(val name: String, val toBase: Double, val offset: Double = 0.0)

data class ConvCategory(val title: String, val units: List<ConvUnit>)

object UnitConverter {
    val CATEGORIES: List<ConvCategory> = listOf(
        ConvCategory("طول", listOf(
            ConvUnit("متر", 1.0), ConvUnit("کیلومتر", 1000.0), ConvUnit("سانتی‌متر", 0.01), ConvUnit("میلی‌متر", 0.001),
            ConvUnit("مایل", 1609.344), ConvUnit("یارد", 0.9144), ConvUnit("فوت", 0.3048), ConvUnit("اینچ", 0.0254),
            ConvUnit("مایل دریایی", 1852.0)
        )),
        ConvCategory("وزن", listOf(
            ConvUnit("کیلوگرم", 1.0), ConvUnit("گرم", 0.001), ConvUnit("میلی‌گرم", 0.000001), ConvUnit("تن", 1000.0),
            ConvUnit("پوند", 0.45359237), ConvUnit("اونس", 0.028349523125), ConvUnit("مثقال", 0.0046)
        )),
        ConvCategory("دما", listOf(
            ConvUnit("سانتی‌گراد", 1.0), ConvUnit("فارنهایت", 5.0 / 9.0, -160.0 / 9.0), ConvUnit("کلوین", 1.0, -273.15)
        )),
        ConvCategory("حجم", listOf(
            ConvUnit("لیتر", 1.0), ConvUnit("میلی‌لیتر", 0.001), ConvUnit("متر مکعب", 1000.0),
            ConvUnit("گالن (آمریکا)", 3.785411784), ConvUnit("کوارت", 0.946352946), ConvUnit("فنجان (آمریکا)", 0.2365882365)
        )),
        ConvCategory("مساحت", listOf(
            ConvUnit("متر مربع", 1.0), ConvUnit("کیلومتر مربع", 1_000_000.0), ConvUnit("هکتار", 10_000.0), ConvUnit("آر", 100.0),
            ConvUnit("فوت مربع", 0.09290304), ConvUnit("اینچ مربع", 0.00064516), ConvUnit("ایکر", 4046.8564224)
        )),
        ConvCategory("سرعت", listOf(
            ConvUnit("متر بر ثانیه", 1.0), ConvUnit("کیلومتر بر ساعت", 1.0 / 3.6), ConvUnit("مایل بر ساعت", 0.44704),
            ConvUnit("گره دریایی", 1852.0 / 3600.0)
        )),
        ConvCategory("فشار", listOf(
            ConvUnit("پاسکال", 1.0), ConvUnit("کیلوپاسکال", 1000.0), ConvUnit("بار", 100_000.0), ConvUnit("اتمسفر", 101_325.0),
            ConvUnit("psi", 6894.757293168), ConvUnit("میلی‌متر جیوه", 133.322387415)
        )),
        ConvCategory("داده", listOf(
            ConvUnit("بایت", 1.0), ConvUnit("کیلوبایت", 1024.0), ConvUnit("مگابایت", 1024.0 * 1024), ConvUnit("گیگابایت", 1024.0 * 1024 * 1024),
            ConvUnit("ترابایت", 1024.0 * 1024 * 1024 * 1024), ConvUnit("بیت", 0.125)
        )),
        ConvCategory("زمان", listOf(
            ConvUnit("ثانیه", 1.0), ConvUnit("دقیقه", 60.0), ConvUnit("ساعت", 3600.0), ConvUnit("روز", 86_400.0), ConvUnit("هفته", 604_800.0)
        ))
    )

    fun convert(from: ConvUnit, to: ConvUnit, value: Double): Double {
        val base = value * from.toBase + from.offset
        return (base - to.offset) / to.toBase
    }

    /** ورودی کاربر (ارقام فارسی/عربی، ویرگول یا ٫ به‌جای نقطه) → عدد؛ null اگر معتبر نیست. */
    fun parse(s: String): Double? {
        val t = s.trim().map {
            when (it) {
                in '۰'..'۹' -> '0' + (it - '۰')
                in '٠'..'٩' -> '0' + (it - '٠')
                '٫', ',', '،' -> '.'
                else -> it
            }
        }.joinToString("")
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** حداکثر ۸ رقم معنادار، بدون صفرهای اضافه؛ برای مقدارهای خیلی بزرگ/کوچک نمادگذاری علمی. */
    fun format(x: Double): String {
        if (x == 0.0 || !x.isFinite()) return "0"
        val bd = BigDecimal(x).round(MathContext(8)).stripTrailingZeros()
        return if (bd.scale() < -12 || bd.scale() > 12) bd.toString() else bd.toPlainString()
    }
}

// ───────────────────────── محاسبهٔ سن ─────────────────────────

data class AgeResult(
    val years: Int, val months: Int, val days: Int, val totalDays: Int,
    val nextBirthday: JalaliDate, val daysToBirthday: Int, val weekday: Int, val gregorian: java.time.LocalDate
)

object AgeCalc {
    /** سن به سال/ماه/روز شمسی؛ null اگر تولد در آینده باشد. */
    fun compute(birth: JalaliDate, today: JalaliDate): AgeResult? {
        if (birth > today) return null
        var y = today.year - birth.year
        var m = today.month - birth.month
        var d = today.day - birth.day
        if (d < 0) {
            m -= 1
            val pm = if (today.month == 1) 12 else today.month - 1
            val py = if (today.month == 1) today.year - 1 else today.year
            d += JalaliCalendar.daysInMonth(py, pm)
        }
        if (m < 0) { y -= 1; m += 12 }
        fun bday(year: Int) = JalaliDate(year, birth.month, minOf(birth.day, JalaliCalendar.daysInMonth(year, birth.month)))
        var next = bday(today.year)
        if (next < today) next = bday(today.year + 1)
        return AgeResult(
            y, m, d, today.toJdn() - birth.toJdn(), next, next.toJdn() - today.toJdn(),
            birth.dayOfWeek, birth.toLocalDate()
        )
    }
}
