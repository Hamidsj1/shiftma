package ir.shiftapp.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ir.shiftapp.core.AzanSounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** پوشهٔ اذان برنامه (filesDir/azan): فایل‌های دانلودشده یا افزوده‌شده از گوشی. به هیچ برنامهٔ دیگری دسترسی داده نمی‌شود. */
object AzanFiles {
    const val MAX_BYTES = AzanSounds.MAX_BYTES
    private const val MIN_BYTES = AzanSounds.MIN_BYTES

    fun dir(ctx: Context): File = File(ctx.filesDir, "azan").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.name } ?: emptyList()

    fun fileFor(ctx: Context, token: String): File? =
        AzanSounds.fileName(token)?.let { File(dir(ctx), it) }?.takeIf { it.isFile }

    fun delete(ctx: Context, name: String): Boolean =
        AzanSounds.isSafeName(name) && File(dir(ctx), name).delete()

    /** کپی یک فایل صوتیِ انتخاب‌شده از گوشی به پوشهٔ برنامه؛ نام فایل یا پیام خطا. */
    suspend fun import(ctx: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tmp = File(dir(ctx), "import.part")
        try {
            val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            val name = AzanSounds.sanitize(display ?: "azan")
            val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(IOException("فایل باز نشد"))
            copyLimited(input, tmp)
            finish(ctx, tmp, name)
        } catch (e: Exception) {
            tmp.delete(); Result.failure(e)
        }
    }

    /**
     * دانلود فقط با پیوند https و فقط وقتی کاربر دکمه را می‌زند. حد حجم ۲۰ مگابایت؛ پاسخ متنی/HTML (پیوند اشتباه) رد می‌شود.
     */
    suspend fun download(
        ctx: Context, url: String,
        referer: String? = null, cookie: String? = null, userAgent: String? = null, nameHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val u = url.trim()
        if (!AzanSounds.isHttps(u)) return@withContext Result.failure(IllegalArgumentException("پیوند باید با https شروع شود"))
        val name = if (nameHint.isNullOrBlank()) AzanSounds.nameFromUrl(u) else AzanSounds.sanitize(nameHint)
        val tmp = File(dir(ctx), "$name.part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "ShiftMa")
                // وقتی فایل را از داخل مرورگر برنامه برمی‌داریم، همان نشست (کوکی) و صفحهٔ مبدأ را می‌فرستیم؛ بعضی سایت‌ها بدون آن رد می‌کنند
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            }
            if (conn.responseCode !in 200..299) return@withContext Result.failure(IllegalStateException("سرور پاسخ نداد (${conn.responseCode})"))
            if (conn.contentType.orEmpty().lowercase().startsWith("text/"))
                return@withContext Result.failure(IllegalStateException("این پیوند به فایل صوتی اشاره نمی‌کند"))
            if (conn.contentLengthLong > MAX_BYTES) return@withContext Result.failure(IllegalStateException("فایل بزرگ‌تر از ۲۰ مگابایت است"))
            copyLimited(conn.inputStream, tmp)
            finish(ctx, tmp, name)
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
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw IllegalStateException("فایل بزرگ‌تر از ۲۰ مگابایت است")
                    o.write(buf, 0, n)
                }
            }
        }
    }

    private fun finish(ctx: Context, tmp: File, name: String): Result<String> {
        if (tmp.length() < MIN_BYTES) { tmp.delete(); return Result.failure(IllegalStateException("فایل خیلی کوچک است؛ پیوند را بررسی کنید")) }
        val dest = File(dir(ctx), name)
        if (dest.exists()) dest.delete()
        return if (tmp.renameTo(dest)) Result.success(name) else { tmp.delete(); Result.failure(IOException("ذخیرهٔ فایل ممکن نشد")) }
    }
}
