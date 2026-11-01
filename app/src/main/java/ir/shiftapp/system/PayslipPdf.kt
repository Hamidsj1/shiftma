package ir.shiftapp.system

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.URLUtil
import ir.shiftapp.core.PdfFinder
import ir.shiftapp.core.SeenUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * دانلود فایل اصلی PDF فیش حقوقی با نشست خود کاربر (کوکی‌های WebView). هیچ نام کاربری یا رمزی ذخیره نمی‌شود؛
 * کاربر خودش داخل WebView وارد سایت می‌شود و ما فقط همان کوکی نشست را برای درخواست‌های همان سایت می‌فرستیم.
 */
object PayslipPdf {
    class Hit(val url: String, val bytes: ByteArray)

    /** نامزدها را به ترتیب امتحان می‌کند و اولین پاسخی را که با «%PDF-» شروع شود برمی‌گرداند. باید روی ریسمان پس‌زمینه صدا زده شود. */
    fun fetchFirst(candidates: List<SeenUrl>, userAgent: String): Hit? {
        for (c in candidates) {
            val u = c.url
            if (!u.startsWith("https://") || PdfFinder.isUnsafeToReplay(u)) continue   // کوکی روی http نمی‌رود
            var conn: HttpURLConnection? = null
            try {
                conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                CookieManager.getInstance().getCookie(u)?.let { conn.setRequestProperty("Cookie", it) }
                conn.setRequestProperty("User-Agent", userAgent)
                c.referer?.let { conn.setRequestProperty("Referer", it) }
                if (conn.responseCode != 200) continue
                val pin = PushbackInputStream(conn.inputStream, 8)
                val head = ByteArray(5)
                var n = 0
                while (n < 5) {
                    val r = pin.read(head, n, 5 - n)
                    if (r < 0) break
                    n += r
                }
                if (!PdfFinder.isPdfMagic(head, n)) continue
                pin.unread(head, 0, n)
                val bytes = readLimited(pin, PdfFinder.MAX_PDF_BYTES) ?: continue
                return Hit(u, bytes)
            } catch (e: Exception) {
                // این آدرس PDF نبود یا باز نشد؛ سراغ بعدی
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            total += r
            if (total > max) return null
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    /** اندروید ۱۰+ : MediaStore.Downloads (بدون permission)؛ قدیمی‌تر: getExternalFilesDir. محل ذخیره را برمی‌گرداند. */
    fun save(ctx: Context, bytes: ByteArray, name: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("ساخت فایل ممکن نشد")
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IllegalStateException("نوشتن ممکن نشد")
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                ctx.contentResolver.delete(uri, null, null)
                throw e
            }
            return "پوشهٔ Downloads"
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        dir.mkdirs()
        val f = File(dir, name)
        f.outputStream().use { it.write(bytes) }
        return f.absolutePath
    }

    /** دانلود لینک مستقیم یا پاسخ Content-Disposition با DownloadManager و همان کوکی ورود. */
    fun enqueue(ctx: Context, url: String, userAgent: String?, mime: String?, disposition: String?): String {
        val req = DownloadManager.Request(Uri.parse(url))
        CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
        if (!userAgent.isNullOrBlank()) req.addRequestHeader("User-Agent", userAgent)
        if (!mime.isNullOrBlank()) req.setMimeType(mime)
        val fileName = URLUtil.guessFileName(url, disposition, mime)
        req.setTitle(fileName)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        else req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName)   // بدون نیاز به permission
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        return fileName
    }
}
