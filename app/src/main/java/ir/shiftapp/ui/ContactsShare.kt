package ir.shiftapp.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.shiftapp.data.PatternFile
import ir.shiftapp.phonebook.Contact
import ir.shiftapp.phonebook.PhonebookVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * مخاطبین را به‌صورت فایل .shiftma (type = contacts) می‌سازد و پنجرهٔ اشتراک‌گذاری سیستم را باز می‌کند.
 * گیرنده وقتی روی فایل بزند، گوشی‌اش «شیفت ما» را برای بازکردن پیشنهاد می‌دهد.
 * [only] = null یعنی همهٔ مخاطبین.
 */
suspend fun shareContactsFile(ctx: Context, vm: PhonebookVm, only: List<Contact>?, withPhotos: Boolean, tiles: org.json.JSONObject? = null) {
    val export = vm.buildContactsExport(only, withPhotos, tiles)
    if (export == null) {
        Toast.makeText(ctx, "ساخت فایل مخاطبین ممکن نشد یا مخاطبی وجود ندارد", Toast.LENGTH_LONG).show()
        return
    }
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "ShiftMa-contacts.${PatternFile.EXT}")
            f.writeText(export.json, Charsets.UTF_8)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        } catch (e: Exception) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, "ساخت فایل مخاطبین ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    if (export.photosDropped) Toast.makeText(ctx, "حجم عکس‌ها زیاد بود؛ فایل بدون عکس ساخته شد", Toast.LENGTH_LONG).show()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PatternFile.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        putExtra(Intent.EXTRA_SUBJECT, "مخاطبین (${AppInfo.APP_NAME})")
        clipData = ClipData.newRawUri(AppInfo.APP_NAME, uri)      // مجوز خواندن را برای برنامهٔ مقصد قطعی می‌کند
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, "ارسال فایل مخاطبین"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "برنامه‌ای برای ارسال فایل پیدا نشد", Toast.LENGTH_LONG).show()
    }
}
