package ir.shiftapp.core

import kotlin.math.roundToInt

/** تنظیمات «کاهش حجم عکس». maxSide = بلندترین ضلع به پیکسل؛ targetKb اگر پر باشد کیفیت را خودکار پیدا می‌کند. */
data class CompressSettings(
    val maxSide: Int = 2048,
    val quality: Int = 75,
    val targetKb: Int? = null,
    val webp: Boolean = false
)

/** منطق خالص (بدون Android) برای ابزار «کاهش حجم عکس»؛ کار با Bitmap در system/ImageCompressor.kt است. */
object ImageCompress {
    const val MIN_QUALITY = 30
    const val MAX_QUALITY = 95

    /** گزینهٔ «اصلی» همین سقف است تا عکس‌های ۵۰ مگاپیکسلی حافظه را پر نکنند. */
    const val ORIGINAL_CAP = 4096
    val SIDES = listOf(ORIGINAL_CAP, 2048, 1600, 1280, 1024, 800)

    /** ابعاد جدید با حفظ نسبت؛ اگر عکس از سقف کوچک‌تر باشد بدون تغییر. */
    fun fitSize(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val big = maxOf(w, h)
        if (maxSide <= 0 || big <= maxSide) return w to h
        val k = maxSide.toDouble() / big
        return maxOf(1, (w * k).roundToInt()) to maxOf(1, (h * k).roundToInt())
    }

    /** بزرگ‌ترین توان ۲ برای inSampleSize که بلندترین ضلعِ دیکود‌شده هنوز ≥ maxSide بماند. */
    fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        val big = maxOf(w, h)
        var s = 1
        while (maxSide > 0 && big / (s * 2) >= maxSide) s *= 2
        return s
    }

    /**
     * بیشترین کیفیتی که حجم خروجی از target بیشتر نشود (جست‌وجوی دودویی).
     * size(q) حجم بایتیِ خروجی با کیفیت q است. اگر حتی با کمترین کیفیت هم زیاد باشد null.
     */
    fun bestQuality(target: Long, lo: Int = MIN_QUALITY, hi: Int = MAX_QUALITY, size: (Int) -> Long): Int? {
        if (size(lo) > target) return null
        var a = lo
        var b = hi
        while (a < b) {
            val mid = (a + b + 1) / 2
            if (size(mid) <= target) a = mid else b = mid - 1
        }
        return a
    }

    /** «۳۴۰ کیلوبایت» / «۱٫۲ مگابایت». */
    fun formatSize(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        val s = when {
            b < 1024 -> "$b بایت"
            b < 1024L * 1024 -> "${(b / 1024.0).roundToInt()} کیلوبایت"
            else -> "%.1f".format(java.util.Locale.US, b / 1048576.0).replace('.', '٫') + " مگابایت"
        }
        return Fa.digits(s)
    }

    /** درصد کاهش حجم (۰ تا ۱۰۰). */
    fun savedPercent(before: Long, after: Long): Int =
        if (before <= 0) 0 else ((before - after) * 100.0 / before).roundToInt().coerceIn(0, 100)

    /** «حجم هدف» کاربر به کیلوبایت؛ ارقام فارسی هم پذیرفته می‌شود. خارج از ۱۰ تا ۵۰۰۰۰ یا خالی → null. */
    fun parseTargetKb(text: String): Int? {
        val n = Fa.latin(text).trim().toIntOrNull() ?: return null
        return if (n in 10..50_000) n else null
    }

    fun extForMime(mime: String?): String = when (mime?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    /** نام فایل خروجی: «نام‌اصلی_small.jpg»؛ نویسه‌های ممنوعِ نام فایل برداشته می‌شود. */
    fun outName(display: String?, ext: String): String {
        val base = (display ?: "").substringBeforeLast('.', display ?: "")
            .map { if (it in "/\\:*?\"<>|") '_' else it }.joinToString("").trim()
        return (if (base.isEmpty()) "image" else base) + "_small." + ext
    }

    /** اگر نام قبلاً در taken بود، قبل از پسوند «_۲»، «_۳» … می‌گذارد تا چند عکس هم‌نام روی هم نیفتند. */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        var i = 2
        while ("${base}_$i$ext" in taken) i++
        return "${base}_$i$ext"
    }
}
