package ir.shiftapp.core

/**
 * سیاست درخواست امتیاز در بازار. بدون هیچ پاداشی (قوانین بازار) و بدون مزاحمت:
 * بعد از چند بار استفاده و چند روز، حداکثر ماهی یک‌بار؛ با «دیگر نپرس» برای همیشه خاموش می‌شود.
 * منطق خالص است تا تست‌پذیر بماند؛ ذخیره‌سازی در `ui/RatePrompt.kt`.
 */
object RatePolicy {
    const val MIN_OPENS = 5
    const val MIN_DAYS_SINCE_FIRST = 3
    const val MIN_DAYS_BETWEEN_ASKS = 30

    fun shouldAsk(opens: Int, firstDay: Long, lastAskDay: Long, never: Boolean, today: Long, hasData: Boolean): Boolean {
        if (never || !hasData || firstDay <= 0L) return false
        if (opens < MIN_OPENS || today - firstDay < MIN_DAYS_SINCE_FIRST) return false
        return lastAskDay <= 0L || today - lastAskDay >= MIN_DAYS_BETWEEN_ASKS
    }
}
