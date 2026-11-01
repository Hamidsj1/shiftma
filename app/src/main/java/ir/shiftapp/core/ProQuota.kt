package ir.shiftapp.core

import android.content.Context

/**
 * سهمیهٔ استفادهٔ رایگان از قابلیت‌های ویژه + کشِ محلی وضعیت خرید (کافه‌بازار).
 * - هر قابلیت [FREE_USES] بار رایگان است؛ بعد از آن فقط با خرید درون‌برنامه‌ای باز می‌شود.
 * - منبع اصلی خرید خود کافه‌بازار است (BazaarBilling)؛ این‌جا فقط نتیجه نگه داشته می‌شود تا بدون اینترنت هم کار کند.
 * - شمارنده با پاک شدن دادهٔ برنامه صفر می‌شود؛ ولی خرید با همان حساب بازار دوباره بازیابی می‌شود.
 */
object ProQuota {
    /** تعداد دفعات رایگان برای هر قابلیت (هر بار «اجرا» روی یک دسته عکس یک بار حساب می‌شود). */
    const val FREE_USES = 5

    /** شناسهٔ قابلیت «کاهش حجم عکس». */
    const val FEATURE_COMPRESS = "compress"

    private const val PREFS = "shiftma_pro"

    fun remaining(used: Int, free: Int = FREE_USES): Int = (free - used).coerceAtLeast(0)

    /** true = اجرا مجاز است (خریده یا هنوز از سهمیهٔ رایگان مانده). */
    fun canUse(used: Int, pro: Boolean, free: Int = FREE_USES): Boolean = pro || used < free

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun used(ctx: Context, feature: String): Int = sp(ctx).getInt("used_$feature", 0)

    /** یک استفاده ثبت می‌کند و تعداد جدید را برمی‌گرداند. */
    fun addUse(ctx: Context, feature: String): Int {
        val n = used(ctx, feature) + 1
        sp(ctx).edit().putInt("used_$feature", n).apply()
        return n
    }

    fun isPro(ctx: Context, feature: String): Boolean = sp(ctx).getBoolean("pro_$feature", false)

    fun setPro(ctx: Context, feature: String, value: Boolean) {
        sp(ctx).edit().putBoolean("pro_$feature", value).apply()
    }
}
