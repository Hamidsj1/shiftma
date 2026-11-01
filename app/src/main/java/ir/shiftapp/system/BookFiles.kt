package ir.shiftapp.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ir.shiftapp.core.AzanSounds
import ir.shiftapp.core.Books
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** پوشهٔ کتاب‌های برنامه (filesDir/books): دانلودشده از مرورگر برنامه یا افزوده‌شده از گوشی. جدا از بکاپ و بدون دسترسی برنامهٔ دیگر. */
object BookFiles {
    fun dir(ctx: Context): File = File(ctx.filesDir, "books").apply { mkdirs() }

    /** تازه‌ترین کتاب اول. */
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(ctx: Context, name: String): Boolean = Books.isSafeName(name) && File(dir(ctx), name).delete()

    fun rename(ctx: Context, old: String, newTitle: String): Boolean {
        if (!Books.isSafeName(old)) return false
        val src = File(dir(ctx), old)
        if (!src.isFile) return false
        val ext = Books.extOf(old)
        val target = Books.sanitize(newTitle.trim().ifBlank { return false }, ext).let { if (Books.extOf(it) != ext) "$it.$ext" else it }
        if (target == old) return true
        val taken = list(ctx).map { it.name }.toSet() - old
        return src.renameTo(File(dir(ctx), Books.uniqueName(target, taken)))
    }

    /** کپی کتابِ انتخاب‌شده از گوشی؛ نام فایل یا پیام خطا. */
    suspend fun import(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tmp = File(dir(ctx), "import.part")
        try {
            val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
            } ?: "کتاب"
            val ext = Books.extOf(display)
            val mimeExt = Books.extFromMime(ctx.contentResolver.getType(uri))
            if (ext !in Books.EXT && mimeExt == null) return@withContext Result.failure(IllegalArgumentException("فقط فایل کتاب (PDF، EPUB و …) پشتیبانی می‌شود"))
            val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(IOException("فایل باز نشد"))
            copyLimited(input, tmp)
            finish(ctx, tmp, Books.sanitize(display, mimeExt ?: "pdf"))
        } catch (e: Exception) {
            tmp.delete(); Result.failure(e)
        }
    }

    /** دانلود فقط با پیوند https و فقط وقتی کاربر «ذخیره» را می‌زند؛ با همان کوکی و صفحهٔ مبدأ مرورگر برنامه. */
    suspend fun download(
        ctx: Context, url: String,
        referer: String? = null, cookie: String? = null, userAgent: String? = null, nameHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!AzanSounds.isHttps(u)) return@withContext Result.failure(IllegalArgumentException("پیوند باید با https شروع شود"))
        val tmp = File(dir(ctx), "download-${System.nanoTime()}.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "ShiftMa")
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            }
            if (conn.responseCode !in 200..299) return@withContext Result.failure(IllegalStateException("سرور پاسخ نداد (${conn.responseCode})"))
            val type = conn.contentType.orEmpty().lowercase()
            if (type.startsWith("text/html")) return@withContext Result.failure(IllegalStateException("این پیوند به فایل کتاب اشاره نمی‌کند؛ دکمهٔ دانلود خود سایت را بزنید"))
            if (conn.contentLengthLong > Books.MAX_BYTES) return@withContext Result.failure(IllegalStateException("فایل بزرگ‌تر از ۱۰۰ مگابایت است"))
            val hint = nameHint?.takeIf { it.isNotBlank() } ?: u.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
            val fallback = Books.extFromMime(type) ?: "pdf"
            copyLimited(conn.inputStream, tmp)
            finish(ctx, tmp, Books.sanitize(hint, fallback))
        } catch (e: Exception) {
            tmp.delete(); Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun copyLimited(input: java.io.InputStream, to: File) {
        var total = 0L
        input.use { i ->
            to.outputStream().use { o ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > Books.MAX_BYTES) throw IllegalStateException("فایل بزرگ‌تر از ۱۰۰ مگابایت است")
                    o.write(buf, 0, n)
                }
            }
        }
    }

    private fun finish(ctx: Context, tmp: File, name: String): Result<String> {
        if (tmp.length() < Books.MIN_BYTES) { tmp.delete(); return Result.failure(IllegalStateException("فایل خیلی کوچک است؛ پیوند را بررسی کنید")) }
        val dest = Books.uniqueName(name, list(ctx).map { it.name }.toSet())
        return if (tmp.renameTo(File(dir(ctx), dest))) Result.success(dest) else { tmp.delete(); Result.failure(IOException("ذخیرهٔ فایل ممکن نشد")) }
    }
}
