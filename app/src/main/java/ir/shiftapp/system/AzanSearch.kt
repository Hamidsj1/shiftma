package ir.shiftapp.system

import ir.shiftapp.core.AzanCatalog
import ir.shiftapp.core.AzanHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** جست‌وجوی اذان در ویکی‌انبار و Internet Archive؛ فقط وقتی کاربر دکمهٔ «جست‌وجو» را می‌زند اجرا می‌شود. */
object AzanSearch {
    private const val MAX_RESPONSE = 4_000_000

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 12_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ShiftMa/1.0 (Android; azan search)")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) throw IllegalStateException("سرور پاسخ نداد (${c.responseCode})")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            c.inputStream.use { i ->
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_RESPONSE) throw IllegalStateException("پاسخ سرور بیش از حد بزرگ بود")
                    out.write(buf, 0, n)
                }
            }
            out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }

    /** ویکی‌انبار (با پروانهٔ مشخص) و بعد مجموعه‌های Internet Archive؛ اگر یکی از منبع‌ها خطا داد، نتیجهٔ دیگری نشان داده می‌شود. */
    suspend fun search(q: String): Result<List<AzanHit>> {
        if (q.trim().length < 2) return Result.failure(IllegalArgumentException("حداقل دو حرف بنویسید"))
        val commons = runCatching { AzanCatalog.parseCommons(get(AzanCatalog.commonsUrl(q))) }
        val archive = runCatching { AzanCatalog.parseArchiveSearch(get(AzanCatalog.archiveSearchUrl(q))) }
        if (commons.isFailure && archive.isFailure) return Result.failure(commons.exceptionOrNull() ?: IllegalStateException("جست‌وجو انجام نشد"))
        return Result.success(commons.getOrDefault(emptyList()) + archive.getOrDefault(emptyList()))
    }

    /** فایل‌های صوتی یک مجموعهٔ Internet Archive. */
    suspend fun files(item: AzanHit): Result<List<AzanHit>> = runCatching {
        val id = item.itemId ?: throw IllegalArgumentException("مجموعه نامعتبر است")
        AzanCatalog.parseArchiveFiles(id, get(AzanCatalog.archiveMetaUrl(id)))
    }
}
