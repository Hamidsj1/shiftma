package ir.shiftapp.system

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import ir.shiftapp.core.SpecialAccess
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.data.AppDb

/**
 * متن گزارش برای ارسال به توسعه‌دهنده: نسخه، دستگاه، وضعیت مجوزها و ویجت‌ها، «تعداد» داده‌ها و آخرین رویدادها.
 * هیچ دادهٔ شخصی نمی‌آید (نام، تلفن، متن یادداشت، آدرس سایت، رمز). فقط با لمس طولانی و رمز روزانه دیده می‌شود.
 */
object DiagReport {
    suspend fun build(ctx: Context): String {
        val app = ctx.applicationContext
        val sb = StringBuilder()
        fun line(k: String, v: Any?) { sb.append(k).append(": ").append(v ?: "؟").append('\n') }
        val pi = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        sb.append("=== گزارش شیفت ما ===\n")
        line("نسخه", "${pi?.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi?.longVersionCode else @Suppress("DEPRECATION") pi?.versionCode})")
        line("بسته", app.packageName)
        line("اندروید", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("دستگاه", "${Build.MANUFACTURER} ${Build.MODEL}")
        line("زبان/منطقه", "${java.util.Locale.getDefault()} / ${java.util.TimeZone.getDefault().id}")
        val dm = app.resources.displayMetrics
        line("صفحه", "${dm.widthPixels}x${dm.heightPixels} @${dm.density}")
        line("نصب اولیه", pi?.firstInstallTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(it)) })
        line("آخرین به‌روزرسانی برنامه", pi?.lastUpdateTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(it)) })

        sb.append("\n— مجوزها و سیستم —\n")
        runCatching { line("اعلان‌ها فعال", NotificationManagerCompat.from(app).areNotificationsEnabled()) }
        runCatching { if (Build.VERSION.SDK_INT >= 31) line("آلارم دقیق مجاز", (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) }
        runCatching { line("بهینه‌سازی باتری نادیده", (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(app.packageName)) }

        sb.append("\n— ویجت‌ها —\n")
        runCatching {
            val m = AppWidgetManager.getInstance(app)
            listOf("ShiftWidget", "ShiftStripWidget", "EnglishWidget", "PrayerWidget", "NextReminderWidget").forEach {
                line(it, m.getAppWidgetIds(ComponentName(app.packageName, "ir.shiftapp.system.$it")).size)
            }
        }

        sb.append("\n— داده‌ها (فقط تعداد) —\n")
        runCatching {
            val dao = AppDb.get(app).dao()
            val p = dao.prefsOnce()
            line("الگوهای شیفت", runCatching { dao.patternsOnce().size }.getOrNull())
            line("یادداشت‌ها", p.notes.size)
            line("آلارم‌ها (کل/فعال)", "${p.alarms.size}/${p.alarms.count { it.enabled }}")
            line("سامانه‌ها", TilesPackFile.decodeSites(p.sitesRaw).size)
            line("خوراک‌ها (غذا/تبلیغ/اعلان/تنظیم)", listOf(p.mealFeed, p.adFeed, p.noticeFeed, p.configFeed).joinToString("/") { if (it.isNotEmpty()) "بله" else "خیر" })
        }
        line("دسترسی ویژه", SpecialAccess.isOn(app))
        line("بخش‌های بازشده با فایل", SpecialAccess.sections(app).size)

        sb.append("\n— به‌روزرسانی آنلاین —\n")
        line("آخرین بررسی موفق", UpdateLog.lastOkCheck(app).takeIf { it > 0 }?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(it)) } ?: "هرگز")
        line("نتیجهٔ آخرین بررسی", if (UpdateLog.lastCheck(app) == 0L) "—" else if (UpdateLog.lastOk(app)) "موفق" else "ناموفق: ${UpdateLog.lastError(app)}")

        sb.append("\n— رویدادهای اخیر —\n")
        val log = DiagLog.read(app)
        sb.append(if (log.isBlank()) "(خالی)" else log).append('\n')
        return sb.toString()
    }
}
