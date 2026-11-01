package ir.shiftapp.core

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * دسترسی ویژه (کاشی‌های مخفی: دفترچه تلفن و سامانه‌ها). رمز هر روز عوض می‌شود و کاملاً آفلاین از روی تاریخ و یک کلید محرمانه ساخته می‌شود؛
 * توسعه‌دهنده همان رمز را با tools/shiftma_code.py می‌سازد.
 *
 * ⚠ قبل از انتشار، [SECRET] را عوض کنید (همان مقدار را در tools/shiftma_code.py هم بگذارید) و آن اسکریپت را عمومی نکنید.
 * چون برنامه آفلاین است، کلید داخل APK است؛ این قفل جلوی کاربر عادی را می‌گیرد، نه کسی که APK را باز و بررسی کند.
 */
object SpecialAccess {
    internal const val SECRET = "29bee50893c8bc2df06c5269cb1135b4"
    private const val DIGITS = 6
    private val ZONE: ZoneId = ZoneId.of("Asia/Tehran")   // تاریخ همیشه به وقت ایران؛ تنظیم ساعت‌ِ منطقهٔ گوشی اثری ندارد
    const val MAX_FAILS = 5
    const val LOCK_MS = 60_000L

    private const val PREFS = "shiftma_special"
    private const val KEY_ON = "on"
    private const val KEY_SECTIONS = "sections"

    fun today(): LocalDate = LocalDate.now(ZONE)

    /** رمز ۶ رقمی همان روز: HMAC-SHA256(کلید، "ShiftMa|yyyy-MM-dd") و برش پویا مثل HOTP. */
    fun codeFor(date: LocalDate, secret: String = SECRET): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val h = mac.doFinal("ShiftMa|$date".toByteArray(Charsets.UTF_8))
        val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        var mod = 1
        repeat(DIGITS) { mod *= 10 }
        return (bin % mod).toString().padStart(DIGITS, '0')
    }

    /** رمز واردشده (ارقام فارسی/عربی و فاصله هم پذیرفته می‌شود) با رمز امروز مقایسه می‌شود. */
    fun verify(input: String, date: LocalDate = today(), secret: String = SECRET): Boolean {
        val typed = DateText.normalizeDigits(input).filter { it in '0'..'9' }
        val real = codeFor(date, secret)
        if (typed.length != real.length) return false
        var diff = 0
        for (i in real.indices) diff = diff or (typed[i].code xor real[i].code)   // مقایسهٔ بدون توقف زودهنگام
        return diff == 0
    }

    // ---- ضد حدس‌زدن: بعد از چند رمز غلط پشت‌سرهم، یک دقیقه قفل (فقط تا بسته‌شدن برنامه) ----
    private var fails = 0
    private var lockedUntil = 0L

    fun lockRemainingMs(now: Long = System.currentTimeMillis()): Long = (lockedUntil - now).coerceAtLeast(0)

    /** true = رمز درست بود. اگر قفل موقت فعال باشد همیشه false. */
    fun tryUnlock(ctx: Context, input: String, now: Long = System.currentTimeMillis()): Boolean {
        if (lockRemainingMs(now) > 0) return false
        if (verify(input)) { fails = 0; setOn(ctx, true); return true }
        if (++fails >= MAX_FAILS) { fails = 0; lockedUntil = now + LOCK_MS }
        return false
    }

    // در SharedPreferences جدا نگه‌داری می‌شود (نه جدول تنظیمات)، تا با فایل پشتیبان به گوشی دیگر منتقل نشود.
    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    fun setOn(ctx: Context, on: Boolean) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply() }

    /** بخش‌های مخفی که با «فایل کاشی‌ها» نمایان شده‌اند (بدون رمز روزانه). جدا از تنظیمات است، پس با پشتیبان به گوشی دیگر نمی‌رود. */
    fun sections(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SECTIONS, "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
    fun addSections(ctx: Context, ids: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SECTIONS, (sections(ctx) + ids).sorted().joinToString(",")).apply()
    }
    fun clearSections(ctx: Context) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SECTIONS).apply() }
}
