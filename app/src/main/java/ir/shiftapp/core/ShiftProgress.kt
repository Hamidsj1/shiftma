package ir.shiftapp.core

/** پیشرفت شیفتِ در جریان: چند دقیقه گذشته، از کل چند دقیقه. */
class ShiftProgressInfo(val code: Int, val elapsedMin: Int, val totalMin: Int) {
    val remainingMin: Int get() = (totalMin - elapsedMin).coerceAtLeast(0)
    val fraction: Float get() = if (totalMin <= 0) 0f else (elapsedMin.toFloat() / totalMin).coerceIn(0f, 1f)
}

/**
 * پیدا کردن شیفتِ در جریان با «دقیقهٔ فعلیِ روز» (۰..۱۴۳۹). منطق خالص و قابل تست.
 * [yesterday] و [today] = (کد شیفت، ساعت آن) برای دیروز و امروز، یا null اگر آن روز استراحت/مرخصی است یا ساعتی تنظیم نشده.
 * شیفتی که دیروز شروع شده و از نیمه‌شب گذشته (مثل شبکار) تا ساعت پایانش امروز هم در جریان است.
 */
object ShiftProgress {
    fun compute(nowMin: Int, yesterday: Pair<Int, ShiftTime>?, today: Pair<Int, ShiftTime>?): ShiftProgressInfo? {
        if (yesterday != null && yesterday.second.crossesMidnight) {
            val t = yesterday.second
            if (nowMin < t.endMin) return ShiftProgressInfo(yesterday.first, nowMin + 1440 - t.startMin, t.durationMin)
        }
        if (today != null) {
            val t = today.second
            if (t.crossesMidnight) {
                if (nowMin >= t.startMin) return ShiftProgressInfo(today.first, nowMin - t.startMin, t.durationMin)
            } else if (nowMin >= t.startMin && nowMin < t.endMin) {
                return ShiftProgressInfo(today.first, nowMin - t.startMin, t.durationMin)
            }
        }
        return null
    }

    /** «۴:۳۷» برای باقی‌مانده (ساعت:دقیقه، ارقام فارسی). */
    fun clock(min: Int): String = Fa.digits("${min / 60}:${"%02d".format(min % 60)}")
}
