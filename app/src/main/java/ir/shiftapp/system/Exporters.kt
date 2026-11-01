package ir.shiftapp.system

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ir.shiftapp.core.GridCell
import ir.shiftapp.core.GridTable
import java.io.ByteArrayOutputStream
import java.io.File

/** رندر [GridTable] به PDF و PNG با Canvas خودِ اندروید (بدون کتابخانهٔ خارجی). ستون اول سمت راست است. */
object TableRenderer {
    private const val HEADER_BG = 0xFF3949AB.toInt()

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; textAlign = Paint.Align.CENTER
    }

    private fun fit(p: Paint, s: String, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var e = s.length
        while (e > 1 && p.measureText(s.substring(0, e) + "…") > maxW) e--
        return s.substring(0, e) + "…"
    }

    /** مقیاس u: واحد طول (۱ برای PDF، بزرگ‌تر برای تصویر). بازگشتی: y انتهای بلوک. */
    private fun draw(
        c: Canvas, t: GridTable, x0: Float, y0: Float, w: Float, rows: List<List<GridCell>>, u: Float,
        withTitle: Boolean, rowH: Float, hdrH: Float
    ): Float {
        var y = y0
        if (withTitle) {
            c.drawText(t.title, x0 + w / 2, y + 18 * u, paint(17 * u, Color.BLACK, true))
            c.drawText(t.subtitle, x0 + w / 2, y + 36 * u, paint(12 * u, Color.DKGRAY))
            y += 46 * u
        }
        val total = t.weights.sum()
        val colW = t.weights.map { it / total * w }
        fun left(i: Int): Float { var r = x0 + w; for (k in 0..i) r -= colW[k]; return r }
        val border = Paint().apply { style = Paint.Style.STROKE; color = 0xFFBBBBBB.toInt(); strokeWidth = 0.6f * u }
        val fill = Paint()
        t.headers.forEachIndexed { i, h ->
            fill.color = HEADER_BG; c.drawRect(left(i), y, left(i) + colW[i], y + hdrH, fill)
            val p = paint(10.5f * u, Color.WHITE, true)
            c.drawText(fit(p, h, colW[i] - 4 * u), left(i) + colW[i] / 2, y + hdrH / 2 + 4 * u, p)
        }
        y += hdrH
        for (row in rows) {
            row.forEachIndexed { i, cell ->
                if (i >= colW.size) return@forEachIndexed
                val l = left(i)
                cell.bg?.let { fill.color = it.toInt(); c.drawRect(l, y, l + colW[i], y + rowH, fill) }
                c.drawRect(l, y, l + colW[i], y + rowH, border)
                val p = paint(10.5f * u, cell.fg?.toInt() ?: Color.BLACK)
                c.drawText(fit(p, cell.text, colW[i] - 4 * u), l + colW[i] / 2, y + rowH / 2 + 4 * u, p)
            }
            y += rowH
        }
        return y
    }

    fun toPdf(t: GridTable): ByteArray {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 24f
        val rowH = 20f; val hdrH = 26f; val titleH = 46f; val footLine = 16f
        val footReserve = t.footer.size * footLine + 14f
        var idx = 0; var pageNo = 1
        do {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            val avail = h - 2 * m - hdrH - (if (pageNo == 1) titleH else 0f) - footReserve
            val n = maxOf(1, (avail / rowH).toInt())
            val slice = t.rows.subList(idx, minOf(idx + n, t.rows.size))
            var y = draw(page.canvas, t, m, m, w - 2 * m, slice, 1f, pageNo == 1, rowH, hdrH)
            idx += slice.size
            if (idx >= t.rows.size) {
                y += 14f
                val p = paint(10f, Color.BLACK)
                t.footer.forEach { page.canvas.drawText(fit(p, it, w - 2 * m), (w) / 2f, y, p); y += footLine }
            }
            doc.finishPage(page); pageNo++
        } while (idx < t.rows.size)
        val out = ByteArrayOutputStream(); doc.writeTo(out); doc.close()
        return out.toByteArray()
    }

    fun toPng(t: GridTable): ByteArray {
        val width = 1080; val u = width / 600f; val pad = 20f * u
        val rowH = 26f * u; val hdrH = 30f * u; val footLine = 16f * u
        val height = (pad * 2 + 46 * u + hdrH + rowH * t.rows.size + (if (t.footer.isEmpty()) 0f else t.footer.size * footLine + 14 * u)).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        var y = draw(c, t, pad, pad, width - 2 * pad, t.rows, u, true, rowH, hdrH)
        if (t.footer.isNotEmpty()) {
            y += 14 * u
            val p = paint(10f * u, Color.BLACK)
            t.footer.forEach { c.drawText(fit(p, it, width - 2 * pad), width / 2f, y, p); y += footLine }
        }
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle()
        return out.toByteArray()
    }
}

object ShareUtil {
    /** فایل را در cache می‌نویسد و منوی اشتراک‌گذاری (تلگرام، واتساپ، ...) را باز می‌کند. */
    fun shareBytes(ctx: Context, bytes: ByteArray, fileName: String, mime: String) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, fileName); f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "ارسال").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
