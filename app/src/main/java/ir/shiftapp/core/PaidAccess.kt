package ir.shiftapp.core

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * باز کردن بخش‌های پولی با کد، کاملاً آفلاین و مخصوص همان گوشی.
 * جریان: کاربر از لینک پرداخت می‌کند و «شناسهٔ گوشی» (نمایش‌داده‌شده در صفحهٔ قفل) را برای ادمین می‌فرستد؛
 * ادمین با tools/shiftma_paid_code.py کد ۸ رقمی همان گوشی و همان بخش را می‌سازد؛ کاربر کد را وارد می‌کند.
 * چون کد به شناسهٔ گوشی و بخش گره خورده، با دیگران قابل اشتراک نیست.
 *
 * ⚠ قبل از انتشار [SECRET] را عوض کنید (همان مقدار را در اسکریپت هم بگذارید) و اسکریپت را عمومی نکنید.
 * کلید داخل APK است؛ این قفل جلوی کاربر عادی را می‌گیرد، نه کسی که APK را باز و بررسی کند.
 * با پاک شدن دادهٔ برنامه یا نصب دوباره، شناسهٔ گوشی عوض می‌شود و کد قبلی دیگر کار نمی‌کند.
 */
object PaidAccess {
    internal const val SECRET = "f9343a762e6897f1dc45e90e79a9bd5d"
    private const val DIGITS = 8
    private const val PREFS = "shiftma_paid"
    private const val KEY_ID = "install_id"
    private const val KEY_OPEN = "open"
    private const val ID_LEN = 8
    private const val ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // بدون حرف‌های شبیه (O/0، I/1)
    const val MAX_FAILS = 5
    const val LOCK_MS = 60_000L

    /** کد ۸ رقمی: HMAC-SHA256(کلید، "ShiftMa|paid|شناسه|بخش") با برش پویا (مثل SpecialAccess). */
    fun codeFor(installId: String, sectionId: String, secret: String = SECRET): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val h = mac.doFinal("ShiftMa|paid|$installId|$sectionId".toByteArray(Charsets.UTF_8))
        val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        var mod = 1
        repeat(DIGITS) { mod *= 10 }
        return (bin % mod).toString().padStart(DIGITS, '0')
    }

    fun verify(input: String, installId: String, sectionId: String, secret: String = SECRET): Boolean {
        val typed = DateText.normalizeDigits(input).filter { it in '0'..'9' }
        val real = codeFor(installId.trim().uppercase(), sectionId, secret)
        if (typed.length != real.length) return false
        var diff = 0
        for (i in real.indices) diff = diff or (typed[i].code xor real[i].code)
        return diff == 0
    }

    // ---- ضد حدس‌زدن: بعد از چند کد غلط، یک دقیقه قفل (تا بسته‌شدن برنامه) ----
    private var fails = 0
    private var lockedUntil = 0L
    fun lockRemainingMs(now: Long = System.currentTimeMillis()): Long = (lockedUntil - now).coerceAtLeast(0)

    // ---- ذخیره در SharedPreferences جدا (نه جدول تنظیمات)؛ با فایل پشتیبان به گوشی دیگر نمی‌رود ----
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** شناسهٔ تصادفی این نصب؛ بار اول ساخته می‌شود. کاربر آن را همراه رسید پرداخت برای ادمین می‌فرستد. */
    fun installId(ctx: Context): String {
        val sp = sp(ctx)
        sp.getString(KEY_ID, null)?.let { return it }
        val r = SecureRandom()
        val id = (1..ID_LEN).map { ID_CHARS[r.nextInt(ID_CHARS.length)] }.joinToString("")
        sp.edit().putString(KEY_ID, id).apply()
        return id
    }

    fun isUnlocked(ctx: Context, sectionId: String): Boolean =
        sp(ctx).getString(KEY_OPEN, "").orEmpty().split(',').contains(sectionId)

    /** true = کد درست بود و بخش برای همیشه (روی همین نصب) باز شد. در قفل موقت همیشه false. */
    fun tryUnlock(ctx: Context, sectionId: String, input: String, now: Long = System.currentTimeMillis()): Boolean {
        if (lockRemainingMs(now) > 0) return false
        if (verify(input, installId(ctx), sectionId)) {
            fails = 0
            val open = sp(ctx).getString(KEY_OPEN, "").orEmpty().split(',').filter { it.isNotBlank() }.toSet() + sectionId
            sp(ctx).edit().putString(KEY_OPEN, open.sorted().joinToString(",")).apply()
            return true
        }
        if (++fails >= MAX_FAILS) { fails = 0; lockedUntil = now + LOCK_MS }
        return false
    }
}
