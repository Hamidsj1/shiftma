package ir.shiftapp.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import ir.shiftapp.core.PayslipNames
import ir.shiftapp.core.SlipKind
import java.io.File

/**
 * فیش‌های ذخیره‌شده در پوشهٔ خصوصی برنامه (files/payslips). از برنامه بیرون نمی‌روند مگر کاربر «اشتراک‌گذاری» بزند،
 * و در فایل پشتیبان برنامه هم نیستند (حساس‌اند).
 */
object PayslipFiles {
    class Slip(val year: Int, val month: Int, val kind: SlipKind, val file: File)

    private fun dir(ctx: Context) = File(ctx.filesDir, "payslips").apply { mkdirs() }

    /** جدیدترین ماه اول؛ در هر ماه حکمی قبل از رفاهی. */
    fun list(ctx: Context): List<Slip> =
        dir(ctx).listFiles().orEmpty().mapNotNull { f -> PayslipNames.parse(f.name)?.let { Slip(it.year, it.month, it.kind, f) } }
            .sortedWith(compareByDescending<Slip> { it.year }.thenByDescending { it.month }.thenBy { it.kind.ordinal })

    fun exists(ctx: Context, year: Int, month: Int, kind: SlipKind) = File(dir(ctx), PayslipNames.fileName(year, month, kind)).exists()

    /** ابتدا در فایل موقت می‌نویسد و بعد جابه‌جا می‌کند تا نیمه‌کاره ذخیره نشود. فیش همان ماه و همان نوع جایگزین می‌شود. */
    fun save(ctx: Context, bytes: ByteArray, year: Int, month: Int, kind: SlipKind): Slip {
        require(PayslipNames.isPdf(bytes)) { "فایل PDF معتبر نیست" }
        val target = File(dir(ctx), PayslipNames.fileName(year, month, kind))
        val tmp = File(dir(ctx), target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) { tmp.delete(); throw IllegalStateException("ذخیره نشد") }
        return Slip(year, month, kind, target)
    }

    fun delete(slip: Slip) = slip.file.delete()

    fun shareUri(ctx: Context, slip: Slip): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", slip.file)

    fun pageCount(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val r = PdfRenderer(fd)
            try { r.pageCount } finally { r.close() }
        }
    } catch (e: Exception) { 0 }

    /** یک صفحه را با عرض [widthPx] روی پس‌زمینهٔ سفید رسم می‌کند؛ null اگر باز نشد. باید روی ریسمان پس‌زمینه صدا زده شود. */
    fun render(file: File, page: Int, widthPx: Int): Bitmap? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val r = PdfRenderer(fd)
            try {
                if (page !in 0 until r.pageCount) null else {
                    val p = r.openPage(page)
                    try {
                        val h = (widthPx.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally { p.close() }
                }
            } finally { r.close() }
        }
    } catch (e: Exception) { null }
}
