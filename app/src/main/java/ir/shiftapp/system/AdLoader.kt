package ir.shiftapp.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ir.shiftapp.core.AdFeed
import ir.shiftapp.core.AdItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * پنل تبلیغاتی اختصاصی (بدون SDK): فایل JSON تبلیغ را از [FEED_URL] می‌گیرد و فایل و تصویرها را کش می‌کند.
 * بدون اینترنت، آخرین تبلیغ کش‌شده نشان داده می‌شود. جزئیات: docs/AD_PANEL.md
 */
object AdLoader {
    /** نشانی فایل JSON تبلیغ (فقط https). خالی = پنل خاموش است و هیچ‌چیز از اینترنت گرفته نمی‌شود. */
    const val FEED_URL = "https://raw.githubusercontent.com/Hamidsj1/shiftma-ads/main/ad.json"

    val enabled: Boolean get() = AdFeed.isSafeUrl(FEED_URL)

    /** خوراک اصلی این کاربر: خوراک محل کار (از فایل کاشی‌ها) یا همان خوراک عمومی [FEED_URL]. */
    fun feedFor(custom: String): String = AdFeed.pick(FEED_URL, custom)

    /** جملهٔ تکمیلی متن حریم خصوصی؛ فقط وقتی پنل فعال است. */
    val privacyNote: String
        get() = if (enabled) " اگر پنل اطلاع‌رسانی فعال باشد، فقط یک تصویر و پیوند از اینترنت دریافت می‌شود و اطلاعات شخصی شما جایی ارسال نمی‌شود." else ""

    private const val MAX_JSON = 100_000
    private const val MAX_IMAGE = 1_500_000
    private const val REFRESH_MS = 3L * 60 * 60 * 1000      // حداکثر هر ۳ ساعت یک‌بار فایل را دوباره بگیر

    class Loaded(val item: AdItem, val bitmap: Bitmap)

    private fun root(ctx: Context): File = File(ctx.filesDir, "ad").apply { mkdirs() }
    private fun dirName(feedUrl: String): String = "f_" + Integer.toHexString(feedUrl.hashCode())

    /** هر خوراک پوشهٔ کش خودش را دارد (تبلیغ محل کار و عمومی کنار هم). پوشهٔ خوراک‌هایی که دیگر فعال نیستند پاک می‌شود. */
    private fun prepare(ctx: Context, feedUrl: String, active: Collection<String> = listOf(feedUrl)): File {
        val r = root(ctx)
        val keep = active.map { dirName(it) }.toSet() + dirName(feedUrl)
        r.listFiles()?.forEach { f -> if (f.isFile || f.name !in keep) f.deleteRecursively() }
        return File(r, dirName(feedUrl)).apply { mkdirs() }
    }
    private fun imageFile(d: File, url: String): File = File(d, "img_" + Integer.toHexString(url.hashCode()))

    /** خوراک‌های این کاربر به ترتیب نمایش: محل کار و (در صورت درخواست فایل کاشی) عمومی. */
    fun feedsFor(custom: String, publicAds: Boolean): List<String> = AdFeed.feeds(FEED_URL, custom, publicAds)

    /** تبلیغ‌های کش‌شده (فوری و بدون اینترنت). */
    suspend fun cached(ctx: Context, feedUrl: String = FEED_URL, active: Collection<String> = listOf(feedUrl)): List<Loaded> = withContext(Dispatchers.IO) {
        if (!AdFeed.isSafeUrl(feedUrl)) return@withContext emptyList()
        val d = prepare(ctx, feedUrl, active)
        val feed = File(d, "feed.json")
        if (!feed.exists()) return@withContext emptyList()
        try {
            AdFeed.parse(feed.readText()).mapNotNull { fromDisk(d, it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * فایل را از اینترنت تازه می‌کند. اگر لازم نبود یا خطا شد null برمی‌گرداند (همان کش می‌ماند).
     * برای عوض کردن تصویر یک تبلیغ، نام/نشانی فایل تصویر را عوض کنید (تصویرها با نشانی کش می‌شوند).
     */
    suspend fun refresh(ctx: Context, feedUrl: String = FEED_URL, active: Collection<String> = listOf(feedUrl)): List<Loaded>? = withContext(Dispatchers.IO) {
        if (!AdFeed.isSafeUrl(feedUrl)) return@withContext null
        try {
            val d = prepare(ctx, feedUrl, active)
            val feed = File(d, "feed.json")
            if (feed.exists() && System.currentTimeMillis() - feed.lastModified() < REFRESH_MS) return@withContext null
            val json = String(download(feedUrl, MAX_JSON), Charsets.UTF_8)
            val items = AdFeed.parse(json)
            val loaded = ArrayList<Loaded>()
            for (item in items) {
                val f = imageFile(d, item.imageUrl)
                if (!f.exists()) {
                    val bytes = download(item.imageUrl, MAX_IMAGE)
                    if (decode(bytes) == null) continue
                    f.writeBytes(bytes)
                }
                fromDisk(d, item)?.let { loaded.add(it) }
            }
            feed.writeText(json)
            val keep = items.map { imageFile(d, it.imageUrl).name }.toSet() + "feed.json"
            d.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
            loaded
        } catch (e: Exception) {
            null
        }
    }

    private fun fromDisk(d: File, item: AdItem): Loaded? {
        val f = imageFile(d, item.imageUrl)
        if (!f.exists()) return null
        val bmp = decode(f.readBytes()) ?: return null
        return Loaded(item, bmp)
    }

    /** تصویر بنر؛ ابعاد غیرعادی رد می‌شود و تصویر بزرگ کوچک‌نمونه‌برداری می‌شود. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4000 || bounds.outHeight > 1500) return null
        var sample = 1
        while (bounds.outWidth / sample > 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun download(url: String, max: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ShiftMa (ad panel)")
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            if (c.url.protocol != "https") throw IllegalStateException("not https")      // بعد از ریدایرکت هم فقط https
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            c.inputStream.use { i ->
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) throw IllegalStateException("too large")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }
}
