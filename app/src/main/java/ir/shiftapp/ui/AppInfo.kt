package ir.shiftapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * اطلاعات ناشر؛ هر مقدار را که خالی بگذارید، بخش مربوط به آن در صفحهٔ «درباره» نمایش داده نمی‌شود.
 */
object AppInfo {
    const val APP_NAME = "شیفت ما"
    /** نام شما یا تیم شما، مثلاً «توسعه‌دهنده: ...» */
    const val DEVELOPER = ""
    /**
     * ایمیل بازخورد داخل برنامه؛ عمداً خالی است تا ایمیل در برنامه نمایش داده نشود (ایمیل فقط برای حساب فروشگاه‌ها استفاده می‌شود).
     * راه ارتباط داخل برنامه تلگرام است.
     */
    const val CONTACT_EMAIL = "apps4524@gmail.com"
    /** شناسهٔ تلگرام بدون @؛ دکمهٔ «ارتباط در تلگرام» در صفحهٔ درباره برنامهٔ تلگرام را باز می‌کند. خالی = دکمه نمایش داده نشود. */
    const val TELEGRAM_ID = "H_Ntt"
    /** لینک صفحهٔ برنامه در کافه‌بازار/مایکت؛ بعد از انتشار اینجا بگذارید تا در اشتراک‌گذاری هم بیاید. */
    const val STORE_URL = ""

    /** متن توضیحات که هنگام ارسال فایل به پیام‌رسان‌ها می‌رود؛ هر چه می‌خواهید اینجا بنویسید. */
    const val CAPTION = "«شیفت ما» — جدول شیفت، یادآور، اوقات شرعی، حقوق و اضافه‌کار و ابزارهای کاری، همه در یک برنامه."

    /**
     * true = توضیحات همراه فایل (EXTRA_TEXT) می‌رود. بعضی پیام‌رسان‌های ایرانی (ایتا، بله، روبیکا) وقتی متن هم باشد
     * فقط متن را می‌گیرند و فایل را رها می‌کنند؛ پس پیش‌فرض false است: توضیحات کپی می‌شود و کاربر در پیام‌رسان می‌چسباند.
     */
    const val CAPTION_AS_TEXT = false

    fun shareCaption(): String = buildString {
        append(CAPTION)
        if (STORE_URL.isNotBlank()) append("\n").append(STORE_URL)
        if (TELEGRAM_ID.isNotBlank()) append("\nتلگرام: @").append(TELEGRAM_ID)
        if (CONTACT_EMAIL.isNotBlank()) append("\nایمیل: ").append(CONTACT_EMAIL)
    }

    /** توضیحات را به ارسال فایل وصل می‌کند: یا همراه فایل، یا (پیش‌فرض) در کلیپ‌بورد تا فایل هیچ‌جا گم نشود. */
    fun attachCaption(ctx: Context, send: Intent) {
        val text = shareCaption()
        if (CAPTION_AS_TEXT) { send.putExtra(Intent.EXTRA_TEXT, text); return }
        try {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(APP_NAME, text))
            Toast.makeText(ctx, "متن توضیحات کپی شد؛ اگر خواستید در پیام‌رسان کنار فایل بچسبانید", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { /* کلیپ‌بورد در دسترس نیست؛ فایل بدون توضیحات می‌رود */ }
    }
}
