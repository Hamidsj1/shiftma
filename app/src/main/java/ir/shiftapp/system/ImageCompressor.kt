package ir.shiftapp.system

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import ir.shiftapp.core.CompressSettings
import ir.shiftapp.core.ImageCompress
import java.io.ByteArrayOutputStream
import java.io.File

/** کاهش حجم عکس: کوچک‌کردن ابعاد + فشرده‌سازی JPEG/WebP؛ ذخیره در گالری و اشتراک‌گذاری. اطلاعات EXIF (مثل مکان) در خروجی نمی‌ماند. */
object ImageCompressor {
    class Result(
        val name: String,
        val mime: String,
        val bytes: ByteArray,
        val before: Long,
        val thumb: Bitmap?,
        val note: String?
    ) {
        val after: Long get() = bytes.size.toLong()
        fun withName(n: String) = Result(n, mime, bytes, before, thumb, note)
    }

    private fun queryMeta(ctx: Context, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return name to size
    }

    private fun applyOrientation(m: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.postRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
        }
    }

    @Suppress("DEPRECATION")
    private fun encode(bmp: Bitmap, webp: Boolean, quality: Int): ByteArray {
        val out = ByteArrayOutputStream(256 * 1024)
        val fmt = when {
            !webp -> Bitmap.CompressFormat.JPEG
            Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.WEBP
        }
        bmp.compress(fmt, quality, out)
        return out.toByteArray()
    }

    /** یک عکس را طبق تنظیمات کوچک می‌کند. خطا (عکس نبودن، کمبود حافظه و …) را پرتاب می‌کند. */
    fun compress(ctx: Context, uri: Uri, s: CompressSettings): Result {
        val cr = ctx.contentResolver
        val (display, sz) = queryMeta(ctx, uri)
        val srcMime = cr.getType(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "این فایل عکس نیست" }

        val orientation = cr.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()
        } ?: ExifInterface.ORIENTATION_NORMAL

        val side = s.maxSide.coerceIn(100, ImageCompress.ORIGINAL_CAP)
        val (tw, th) = ImageCompress.fitSize(w, h, side)
        val opts = BitmapFactory.Options().apply { inSampleSize = ImageCompress.sampleSize(w, h, side) }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("عکس خوانده نشد")

        // کوچک‌کردن و چرخش EXIF در یک گذر
        val m = Matrix()
        m.postScale(tw.toFloat() / raw.width, th.toFloat() / raw.height)
        applyOrientation(m, orientation)
        var cur = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (cur !== raw) raw.recycle()

        // JPEG شفافیت ندارد: PNG شفاف روی زمینهٔ سفید می‌نشیند
        if (!s.webp && cur.hasAlpha()) {
            val flat = Bitmap.createBitmap(cur.width, cur.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(cur, 0f, 0f, null) }
            cur.recycle()
            cur = flat
        }

        val target = s.targetKb?.let { it * 1024L }
        val data: ByteArray
        if (target == null) {
            data = encode(cur, s.webp, s.quality.coerceIn(ImageCompress.MIN_QUALITY, ImageCompress.MAX_QUALITY))
        } else {
            var tries = 0
            var found: ByteArray? = null
            while (found == null) {
                val q = ImageCompress.bestQuality(target) { encode(cur, s.webp, it).size.toLong() }
                if (q != null) {
                    found = encode(cur, s.webp, q)
                } else if (tries++ >= 8 || minOf(cur.width, cur.height) < 200) {
                    found = encode(cur, s.webp, ImageCompress.MIN_QUALITY)   // بهترین تلاش؛ کوچک‌تر از این بی‌کیفیت می‌شود
                } else {
                    // حتی با کمترین کیفیت زیاد است: ابعاد را ۱۵٪ کم کن و دوباره
                    val smaller = Bitmap.createScaledBitmap(cur, (cur.width * 0.85f).toInt().coerceAtLeast(1), (cur.height * 0.85f).toInt().coerceAtLeast(1), true)
                    if (smaller !== cur) cur.recycle()
                    cur = smaller
                }
            }
            data = checkNotNull(found)
        }

        // بندانگشتی کوچک برای فهرست نتیجه
        val k = 160f / maxOf(cur.width, cur.height)
        val thumb = if (k < 1f) Bitmap.createScaledBitmap(cur, (cur.width * k).toInt().coerceAtLeast(1), (cur.height * k).toInt().coerceAtLeast(1), true) else cur

        val before = if (sz >= 0) sz else (cr.openInputStream(uri)?.use { it.readBytes().size.toLong() } ?: 0L)
        if (before > 0 && data.size >= before) {
            // عکس از قبل بهینه بود؛ خروجی بزرگ‌تر از ورودی به درد نمی‌خورد، پس فایل اصلی بدون تغییر می‌ماند
            val orig = cr.openInputStream(uri)?.use { it.readBytes() } ?: data
            val mime = srcMime ?: "image/jpeg"
            return Result(ImageCompress.outName(display, ImageCompress.extForMime(mime)), mime, orig, before, thumb, "از قبل بهینه بود؛ بدون تغییر ماند")
        }
        val mime = if (s.webp) "image/webp" else "image/jpeg"
        return Result(ImageCompress.outName(display, if (s.webp) "webp" else "jpg"), mime, data, before, thumb, null)
    }

    /** ذخیره در گالری (پوشهٔ Pictures/ShiftMa). فقط اندروید ۱۰ به بالا؛ در غیر این‌صورت false. */
    fun saveToGallery(ctx: Context, r: Result): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val cr = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, r.name)
            put(MediaStore.Images.Media.MIME_TYPE, r.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ShiftMa")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        return try {
            val os = cr.openOutputStream(uri) ?: throw IllegalStateException("no stream")
            os.use { it.write(r.bytes) }
            v.clear()
            v.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, v, null, null)
            true
        } catch (e: Exception) {
            runCatching { cr.delete(uri, null, null) }
            false
        }
    }

    /** پنجرهٔ اشتراک‌گذاری سیستم برای یک یا چند عکس. */
    fun share(ctx: Context, list: List<Result>): Boolean {
        if (list.isEmpty()) return false
        return try {
            val dir = File(ctx.cacheDir, "share/compressed")
            dir.deleteRecursively()
            dir.mkdirs()
            val uris = ArrayList<Uri>()
            for (r in list) {
                val f = File(dir, r.name)
                f.writeBytes(r.bytes)
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            }
            val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
            else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
            send.type = if (list.map { it.mime }.distinct().size == 1) list[0].mime else "image/*"
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "ارسال عکس‌های کم‌حجم"))
            true
        } catch (e: Exception) {
            false
        }
    }
}
