package ir.shiftapp.core

import kotlin.math.roundToInt

/**
 * جای دکمهٔ شناورِ تماس در صفحهٔ اول؛ به‌شکل کسری (۰..۱) از محدودهٔ قابل جابه‌جایی ذخیره می‌شود
 * تا با چرخش صفحه یا اندازهٔ دیگر هم داخل صفحه بماند. منطق خالص و قابل تست.
 */
object FabPos {
    fun clamp(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)

    fun encode(x: Float, y: Float): String = "${clamp(x)},${clamp(y)}"

    /** «x,y» → جفت کسر؛ خالی یا خراب = null (جای پیش‌فرض). */
    fun parse(raw: String?): Pair<Float, Float>? {
        val p = raw?.split(',') ?: return null
        if (p.size != 2) return null
        val x = p[0].trim().toFloatOrNull() ?: return null
        val y = p[1].trim().toFloatOrNull() ?: return null
        if (x.isNaN() || y.isNaN()) return null
        return clamp(x) to clamp(y)
    }

    fun toPx(frac: Float, range: Int): Int = (clamp(frac) * range.coerceAtLeast(0)).roundToInt()

    fun toFrac(px: Float, range: Int): Float = if (range <= 0) 0f else clamp(px / range)
}
