package ir.shiftapp.core

/**
 * سیاست درخواست امتیاز در بازار. بدون هیچ پاداشی (قوانین بازار) و بدون مزاحمت:
 * بعد از ۱۰ بار باز کردن و ۷ روز از نصب، حداکثر ۳ بار و با فاصلهٔ ۱۴ روز؛ با «دیگر نپرس» برای همیشه خاموش می‌شود.
 * منطق خالص است تا تست‌پذیر بماند؛ ذخیره‌سازی در `ui/RatePrompt.kt`.
 */
object RatePolicy {
    const val MIN_OPENS = 10
    const val MIN_DAYS_SINCE_FIRST = 7
    const val MIN_DAYS_BETWEEN_ASKS = 14
    const val MAX_ASKS = 3

    fun shouldAsk(opens: Int, firstDay: Long, lastAskDay: Long, never: Boolean, today: Long, hasData: Boolean, asks: Int = 0): Boolean {
        if (never || !hasData || firstDay <= 0L || asks >= MAX_ASKS) return false
        if (opens < MIN_OPENS || today - firstDay < MIN_DAYS_SINCE_FIRST) return false
        return lastAskDay <= 0L || today - lastAskDay >= MIN_DAYS_BETWEEN_ASKS
    }
}
