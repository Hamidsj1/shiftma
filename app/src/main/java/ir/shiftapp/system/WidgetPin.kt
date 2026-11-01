package ir.shiftapp.system

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/** افزودن ویجت از داخل خود برنامه (اندروید ۸ به بالا)؛ برای گوشی‌هایی که ویجت را در فهرست ابزارک‌ها نشان نمی‌دهند. */
object WidgetPin {
    fun supported(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported

    /** true یعنی درخواست به لانچر رفت؛ لانچر معمولاً یک پنجرهٔ تأیید نشان می‌دهد. */
    fun pin(ctx: Context, provider: Class<*>): Boolean = runCatching {
        Build.VERSION.SDK_INT >= 26 &&
            AppWidgetManager.getInstance(ctx).requestPinAppWidget(ComponentName(ctx, provider), null, null)
    }.getOrDefault(false)
}
