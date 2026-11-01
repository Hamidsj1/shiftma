package ir.shiftapp.system

import android.app.NotificationManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import ir.shiftapp.core.FireLog
import ir.shiftapp.core.FireRecord

/*
 * اطمینان از آلارم و اذان: وضعیت مجوزها و محدودیت‌های سیستم که می‌تواند هشدار را دیر برساند یا بی‌صدا کند،
 * و راه رفتن به صفحهٔ تنظیم هر کدام. منطق خالص (گزارش و راهنمای سازنده) در core/Reliability.kt است.
 */

/** یک مورد بررسی. [required] = بدون آن هشدار عملاً کار نمی‌کند؛ در غیر این صورت «پیشنهادی» است. */
data class ReliabilityItem(
    val id: String,
    val title: String,
    val ok: Boolean,
    val detail: String,
    val required: Boolean,
    val fix: Intent?
)

object ReliabilityCheck {
    private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    fun collect(ctx: Context): List<ReliabilityItem> {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val out = ArrayList<ReliabilityItem>()

        // ۱) اعلان‌ها (بدون آن نه آلارم نمایش داده می‌شود و نه اذان)
        val notifOn = nm.areNotificationsEnabled()
        out += ReliabilityItem(
            "notif", "اعلان‌های برنامه", notifOn,
            if (notifOn) "روشن است." else "خاموش است؛ آلارم، یادآور و اذان نمایش داده نمی‌شوند.",
            required = true,
            fix = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )

        // ۲) کانال آلارم (اگر کاربر دستی «بی‌صدا/مسدود» کرده باشد)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = nm.getNotificationChannel(ReminderScheduler.CH_ALARM)
            val blocked = ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE
            if (ch != null) out += ReliabilityItem(
                "channel", "کانال «آلارم شیفت»", !blocked,
                if (blocked) "مسدود شده است؛ آلارم‌ها صدا نمی‌دهند." else "فعال است.",
                required = true,
                fix = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderScheduler.CH_ALARM)
            )
        }

        // ۳) بهینه‌سازی باتری (مهم‌ترین عامل دیر رسیدن آلارم در بعضی گوشی‌ها)
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        out += ReliabilityItem(
            "battery", "محدودیت باتری", batteryOk,
            if (batteryOk) "برنامه از بهینه‌سازی باتری مستثنا است."
            else "سیستم ممکن است در حالت خواب، هشدار را چند دقیقه دیر برساند. «همهٔ برنامه‌ها» را باز کنید و «شیفت ما» را روی «بهینه نشود» بگذارید.",
            required = false,
            // صفحهٔ مستقیم همین برنامه، نه فهرست عمومی برنامه‌ها.
            fix = if (Build.VERSION.SDK_INT >= 23) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx))
            } else null
        )

        // ۴) آلارم دقیق (فقط برای حالت «اعلان»؛ حالت «آلارم» با setAlarmClock بدون آن هم دقیق است)
        if (Build.VERSION.SDK_INT >= 31) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exact = am.canScheduleExactAlarms()
            out += ReliabilityItem(
                "exact", "آلارم‌های دقیق", exact,
                if (exact) "مجاز است." else "فقط وقتی لازم است که نوع هشدار را «اعلان» گذاشته باشید؛ حالت «آلارم» بدون آن هم دقیق است.",
                required = false,
                fix = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(ctx))
            )
        }

        // ۵) نمایش تمام‌صفحه روی قفل (اندروید ۱۴ به بعد)
        if (Build.VERSION.SDK_INT >= 34) {
            val full = nm.canUseFullScreenIntent()
            out += ReliabilityItem(
                "fullscreen", "صفحهٔ آلارم روی قفل‌صفحه", full,
                if (full) "مجاز است." else "بدون آن، صفحهٔ بزرگ آلارم روی قفل‌صفحه باز نمی‌شود و فقط اعلان می‌بینید.",
                required = false,
                fix = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(ctx))
            )
        }

        // ۶) نمایش روی برنامه‌های دیگر: به آلارم اجازه می‌دهد وقتی برنامه بسته است صفحهٔ قطع را مثل تماس باز کند
        val overlay = Settings.canDrawOverlays(ctx)
        out += ReliabilityItem(
            "overlay", "باز شدن صفحهٔ آلارم مثل تماس", overlay,
            if (overlay) "مجاز است." else "بدون آن، روی بعضی گوشی‌ها صفحه روشن نمی‌شود یا صفحهٔ آلارم روی قفل‌صفحه باز نمی‌شود. «نمایش روی برنامه‌های دیگر» را برای «شیفت ما» روشن کنید." +
                if (isXiaomi()) " روی شیائومی در صفحهٔ بازشده «نمایش در قفل‌صفحه» و «نمایش پنجره‌های پاپ‌آپ در پس‌زمینه» را هم «مجاز» کنید." else "",
            required = false,
            fix = if (isXiaomi()) miuiPermissionsIntent(ctx) else Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(ctx))
        )

        // ۷) اجازهٔ تنظیمات محدودشده: در اندروید ۱۳+ بدون آن، گزینهٔ سرویس دسترسی‌پذیری خاکستری است؛ باید قبل از روشن کردن آن باشد
        if (Build.VERSION.SDK_INT >= 33) {
            val svcOn = AlarmKeyStop.isKeyServiceEnabled(ctx)
            out += ReliabilityItem(
                "restricted", "مرحلهٔ ۱: اجازهٔ تنظیمات محدودشده", svcOn,
                if (svcOn) "انجام شده است."
                else "قبل از روشن کردن «قطع آلارم با دکمهٔ صدا»: «رفتن به تنظیمات» ← سه‌نقطهٔ بالا ← «اجازهٔ تنظیمات محدودشده» (تأیید با اثر انگشت/رمز). اگر سه‌نقطه نبود، یک‌بار مرحلهٔ ۲ را باز کنید و روی گزینهٔ خاکستری بزنید تا پیام بیاید، بعد برگردید و این مرحله را انجام دهید.",
                required = false,
                fix = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))
            )
        }

        // ۸) قطع آلارم با دکمهٔ صدا در همهٔ حالت‌ها (سرویس دسترسی‌پذیری؛ پیشنهادی)
        val keyOn = AlarmKeyStop.isKeyServiceEnabled(ctx)
        out += ReliabilityItem(
            "volumekey", if (Build.VERSION.SDK_INT >= 33) "مرحلهٔ ۲: قطع آلارم با دکمهٔ صدا در همه‌حال" else "قطع آلارم با دکمهٔ صدا در همه‌حال", keyOn,
            if (keyOn) "روشن است؛ حتی با صفحهٔ خاموش یا برنامهٔ بسته، دکمهٔ صدا آلارم و اذان را قطع می‌کند."
            else "بدون آن، اندروید همیشه دکمهٔ صدا را به برنامه نمی‌رساند. در «سرویس‌های دانلودشده/نصب‌شده» گزینهٔ «قطع آلارم با دکمهٔ صدا» را روشن کنید. فقط وقتی آلارم زنگ می‌زند دکمهٔ صدا را می‌گیرد و چیزی از صفحه نمی‌خواند. اگر گزینه خاکستری بود: اطلاعات برنامه ← سه‌نقطه ← «اجازهٔ تنظیمات محدودشده».",
            required = false,
            fix = AlarmKeyStop.accessibilitySettingsIntent()
        )
        return out
    }

    private fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.orEmpty().lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }

    /** صفحهٔ مجوزهای شیائومی (قفل‌صفحه، پاپ‌آپ پس‌زمینه)؛ اگر نبود open() به «اطلاعات برنامه» برمی‌گردد. */
    private fun miuiPermissionsIntent(ctx: Context): Intent =
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", ctx.packageName)

    /** تعداد موارد ناموفق؛ برای نشان دادن «نیازمند اقدام» کنار عنوان. */
    fun problems(items: List<ReliabilityItem>): Int = items.count { !it.ok }

    /** اگر صفحهٔ اختصاصی روی این گوشی نبود، صفحهٔ «اطلاعات برنامه» باز می‌شود. */
    fun open(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }
}

/** ذخیرهٔ «گزارش پخش» در SharedPreferences جدا از تنظیمات (در پشتیبان نمی‌آید و با بازیابی پشتیبان پاک نمی‌شود). */
object ReliabilityStore {
    private const val PREFS = "shiftma_reliability"
    private const val KEY = "fire_log"
    private val lock = Any()

    fun load(ctx: Context): List<FireRecord> = synchronized(lock) {
        FireLog.decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun record(ctx: Context, dueMs: Long, firedMs: Long, kind: String) = synchronized(lock) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = FireLog.append(FireLog.decode(sp.getString(KEY, null)), FireRecord(dueMs, firedMs, kind))
        sp.edit().putString(KEY, FireLog.encode(list)).apply()
    }

    fun clear(ctx: Context) = synchronized(lock) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
