package ir.shiftapp.core

import java.net.URI
import java.net.URL
import java.net.URLDecoder

/** آدرسی که صفحه درخواست کرده + صفحهٔ مبدأ (Referer). */
data class SeenUrl(val url: String, val referer: String?)

/** فهرست آدرس‌های ثبت‌شده توسط WebView (حداکثر [max] مورد، بدون تکرار). thread-safe چون از ریسمان WebView صدا زده می‌شود. */
class RequestLog(private val max: Int = 200) {
    private val items = ArrayList<SeenUrl>()

    fun add(url: String, referer: String?) {
        synchronized(items) {
            if (items.any { it.url == url }) return
            items.add(SeenUrl(url, referer))
            if (items.size > max) items.removeAt(0)
        }
    }

    /** جدیدترین اول. */
    fun newestFirst(): List<SeenUrl> = synchronized(items) { items.toList().asReversed() }
    fun clear() = synchronized(items) { items.clear() }
}

/** منطق خالص پیدا کردن فایل PDF اصلی؛ بدون وابستگی به اندروید تا تست شود. */
object PdfFinder {
    const val MAX_SEEN = 200
    const val MAX_CANDIDATES = 80
    const val MAX_PDF_BYTES = 30 * 1024 * 1024

    private val STATIC_EXT = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot", ".map")
    private val UNSAFE_WORDS = listOf("logout", "signout", "log-out", "sign-out")

    /** فایل‌های ظاهری/استاتیک ثبت نمی‌شوند. */
    fun isStatic(u: String): Boolean {
        val p = u.substringBefore('?').lowercase()
        return STATIC_EXT.any { p.endsWith(it) }
    }

    /** درخواستی که ارزش ثبت دارد: GET و http(s) و غیر استاتیک. */
    fun shouldLog(method: String, u: String): Boolean =
        method == "GET" && (u.startsWith("http://") || u.startsWith("https://")) && !isStatic(u)

    /** آدرس‌هایی که دوباره فرستادنشان ممکن است نشست را ببندد (خروج از حساب) هرگز تکرار نمی‌شوند. */
    fun isUnsafeToReplay(u: String): Boolean = u.lowercase().let { l -> UNSAFE_WORDS.any { it in l } }

    fun isPdfMagic(head: ByteArray, n: Int): Boolean =
        n >= 5 && String(head, 0, 5, Charsets.ISO_8859_1) == "%PDF-"

    /** مقدار پارامتر file= (اگر بود) به‌صورت آدرس کامل نسبت به خود آدرس؛ نمایشگرهای PDF فایل را همین‌طور می‌گیرند. */
    fun fileParamUrl(u: String): String? = try {
        val q = URI(u).rawQuery ?: return null
        val raw = q.split('&').firstOrNull { it.startsWith("file=") }?.substringAfter('=') ?: return null
        val f = URLDecoder.decode(raw, "UTF-8")
        if (f.isBlank()) null else URL(URL(u), f).toString()
    } catch (e: Exception) { null }

    /**
     * فهرست نامزدها: ابتدا آدرس‌های فریم/embed/object صفحه، سپس آدرس‌های ثبت‌شده (جدیدترین اول).
     * آدرس کامل‌شدهٔ file= بلافاصله بعد از آدرس مادرش می‌آید. فقط http(s)، بدون تکرار، حداکثر [max].
     */
    fun candidates(base: String, domUrls: List<String>, seen: List<SeenUrl>, max: Int = MAX_CANDIDATES): List<SeenUrl> {
        val out = LinkedHashMap<String, String?>()
        fun add(u: String?, ref: String?) {
            if (u.isNullOrBlank()) return
            if (!(u.startsWith("http://") || u.startsWith("https://"))) return
            if (!out.containsKey(u)) out[u] = ref
        }
        fun addWithFile(u: String, ref: String?) {
            add(u, ref)
            fileParamUrl(u)?.let { add(it, u) }
        }
        domUrls.map { it.trim() }.filter { it.isNotEmpty() }.forEach { addWithFile(it, base) }
        seen.forEach { addWithFile(it.url, it.referer ?: base) }
        return out.entries.map { SeenUrl(it.key, it.value) }.take(max)
    }
}
