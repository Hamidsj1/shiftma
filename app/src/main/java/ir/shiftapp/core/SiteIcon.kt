package ir.shiftapp.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * آیکن خود سایت برای کاشی‌هایی که فایل کاشی‌ها آرم نداده است: یک بار از همان سایت (فقط https و همان دامنه) خوانده،
 * کوچک و روی همین گوشی نگه‌داری می‌شود. اگر خوانده نشد، آیکن عمومی نشان داده می‌شود و دفعهٔ بعد دوباره تلاش می‌کند.
 */
object SiteIcon {
    private const val MAX_HTML = 400_000
    private const val MAX_IMG = 400_000
    private const val OUT_PX = 128
    private val failed: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    private fun cacheFile(ctx: Context, key: String) = File(File(ctx.filesDir, "site_icons").apply { mkdirs() }, "$key.png")

    suspend fun load(ctx: Context, key: String, pageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = cacheFile(ctx, key)
        if (f.isFile) return@withContext runCatching { f.readBytes() }.getOrNull()
        if (key in failed) return@withContext null
        val bytes = runCatching { fetch(pageUrl) }.getOrNull()
        if (bytes == null) { failed.add(key); return@withContext null }
        runCatching { f.writeBytes(bytes) }
        bytes
    }

    fun forget(ctx: Context, key: String) { runCatching { cacheFile(ctx, key).delete() }; failed.remove(key) }

    private fun get(url: String, max: Int): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            if (c.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) return null
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }

    private fun fetch(pageUrl: String): ByteArray? {
        val base = URI(pageUrl)
        val site = PortalAutofill.siteOf(base.host.orEmpty())
        val scored = ArrayList<Pair<Int, String>>()
        get(pageUrl, MAX_HTML)?.let { html ->
            Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(String(html, Charsets.UTF_8)).forEach { m ->
                val rel = attr(m.value, "rel")?.lowercase() ?: return@forEach
                if ("icon" !in rel) return@forEach
                val href = attr(m.value, "href") ?: return@forEach
                val size = attr(m.value, "sizes")?.substringBefore('x')?.toIntOrNull() ?: 0
                scored.add((if ("apple-touch" in rel) 1000 else 0) + size to href)
            }
        }
        val urls = scored.sortedByDescending { it.first }.mapNotNull { runCatching { base.resolve(it.second).toString() }.getOrNull() }.toMutableList()
        urls.add(base.resolve("/favicon.ico").toString())
        for (u in urls.distinct().take(5)) {
            val uri = runCatching { URI(u) }.getOrNull() ?: continue
            // فقط https و همان دامنهٔ سامانه
            if (!"https".equals(uri.scheme, ignoreCase = true) || PortalAutofill.siteOf(uri.host.orEmpty()) != site) continue
            val png = runCatching { get(u, MAX_IMG)?.let { toPng(it) } }.getOrNull()
            if (png != null) return png
        }
        return null
    }

    private fun toPng(b: ByteArray): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size) ?: return null
        val scale = OUT_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}

/** خواندن همگام (بدون شبکه) آیکن ذخیره‌شدهٔ سایت برای ویجت؛ null اگر هنوز دریافت نشده. */
fun SiteIcon.cachedBytes(ctx: Context, key: String): ByteArray? =
    runCatching { File(File(ctx.filesDir, "site_icons"), "$key.png").takeIf { it.isFile }?.readBytes() }.getOrNull()
