package ir.shiftapp.phonebook

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.util.UUID

/** ذخیرهٔ عکس مخاطبین که در فایل CSV به‌صورت base64 آمده‌اند (داخل حافظهٔ خصوصی برنامه، مثل عکس‌های انتخابی). */
object PhotoStore {
    /** base64 → فایل JPEG؛ اگر دادهٔ تصویر معتبر نباشد null (و هیچ فایلی ساخته نمی‌شود). */
    fun save(ctx: Context, b64: String): String? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            val f = File(ctx.filesDir, "p_${UUID.randomUUID()}.jpg")
            f.writeBytes(bytes)
            f.absolutePath
        }
    } catch (e: Exception) { null }

    /** مخاطب را با مسیر عکس ذخیره‌شده برمی‌گرداند (اگر عکسی در فایل نبود، همان مخاطب). */
    fun attach(ctx: Context, c: Contact): Contact =
        if (c.photoData.isEmpty()) c else c.copy(photo = save(ctx, c.photoData) ?: "", photoData = "")
}
