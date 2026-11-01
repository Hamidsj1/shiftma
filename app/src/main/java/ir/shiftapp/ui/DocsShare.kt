package ir.shiftapp.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.shiftapp.data.PatternFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun docUri(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

/** فایل را با برنامهٔ دیگر باز می‌کند (برای فایل نمایشی، یا زوم بیشتر در PDF). */
fun openDocExternally(ctx: Context, file: File, mime: String) {
    try {
        val uri = docUri(ctx, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(view)
    } catch (e: Exception) {
        Toast.makeText(ctx, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_LONG).show()
    }
}

/** خودِ فایل را از طریق پنجرهٔ اشتراک‌گذاری سیستم می‌فرستد. */
fun shareDocFile(ctx: Context, file: File, mime: String, title: String) {
    try {
        val uri = docUri(ctx, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "ارسال فایل"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "برنامه‌ای برای ارسال فایل پیدا نشد", Toast.LENGTH_LONG).show()
    }
}

/** بستهٔ برنامه غذایی (.shiftma) را می‌سازد و می‌فرستد؛ گیرنده با زدن روی آن، «شیفت ما» را برای باز کردن پیشنهاد می‌گیرد. */
suspend fun shareMealPack(ctx: Context, json: String) {
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "ShiftMa-meals.${PatternFile.EXT}")
            f.writeText(json, Charsets.UTF_8)
            docUri(ctx, f)
        } catch (e: Exception) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, "ساخت فایل برنامه غذایی ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PatternFile.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        putExtra(Intent.EXTRA_SUBJECT, "برنامه غذایی (${AppInfo.APP_NAME})")
        clipData = ClipData.newRawUri(AppInfo.APP_NAME, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try { ctx.startActivity(Intent.createChooser(send, "ارسال برنامه غذایی")) }
    catch (e: Exception) { Toast.makeText(ctx, "برنامه‌ای برای ارسال فایل پیدا نشد", Toast.LENGTH_LONG).show() }
}
