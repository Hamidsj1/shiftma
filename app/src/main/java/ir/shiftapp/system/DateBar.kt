package ir.shiftapp.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.data.*
import ir.shiftapp.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * اعلان همیشگی «تاریخ و اطلاعات» در نوار اعلان‌های گوشی: تاریخ شمسی (تیتر)، میلادی و قمری، شیفت امروز و فردا،
 * تعطیلی امروز و اذان بعدی (اگر امکانات مذهبی روشن باشد).
 *
 * - اعلان «در حال انجام» (ongoing) و بی‌صدا است. اندروید ۱۴ به کاربر اجازهٔ کنار زدن آن را می‌دهد؛
 *   در این حالت [DateBarReceiver] با deleteIntent دوباره آن را می‌گذارد، مگر کاربر در تنظیمات خاموشش کرده باشد.
 * - تازه‌سازی: نیمه‌شب، لحظهٔ اذانِ بعدی، ریبوت، عوض شدن ساعت/تاریخ/منطقهٔ زمانی، به‌روزرسانی برنامه،
 *   هر تغییر داده یا تنظیمات (MainVm) و هر بار باز شدن برنامه.
 * - برای نمایش، مجوز اعلان (اندروید ۱۳ به بعد) لازم است.
 */
object DateBar {
    const val ACTION_UPDATE = "ir.shiftapp.action.DATEBAR_UPDATE"
    const val ACTION_DISMISSED = "ir.shiftapp.action.DATEBAR_DISMISSED"
    private const val CHANNEL = "date_bar"
    private const val NOTIF_ID = 7200
    private const val REQ_OPEN = 7201
    private const val REQ_DELETE = 7202
    private const val REQ_NEXT = 7203

    private fun hasPermission(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun receiverIntent(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, DateBarReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** اعلان را می‌گذارد/تازه می‌کند (یا اگر در تنظیمات خاموش است برمی‌دارد) و تازه‌سازی بعدی را زمان‌بندی می‌کند. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return@withContext
        val prefs = runCatching { AppDb.get(ctx).dao().prefsOnce() }.getOrNull() ?: return@withContext
        if (!prefs.dateBar) { cancel(ctx); return@withContext }
        if (!hasPermission(ctx)) return@withContext

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = JalaliDate.today()
        val localToday = today.toLocalDate()
        val gregorian = DateInfo.gregorianText(localToday)
        val hijri = DateInfo.hijri(localToday, prefs.lunarShift)?.let { DateInfo.hijriText(it) }

        // شیفت امروز/فردا و تعطیلی؛ هر خطایی فقط همان بخش را کنار می‌گذارد، نه کل اعلان را
        var todayShift: String? = null
        var tomorrowShift: String? = null
        var holiday: String? = null
        runCatching {
            val dao = AppDb.get(ctx).dao()
            val cal = dao.holidayCalendarOnce(prefs.fridayOff)
            holiday = cal.title(today)
            val p = dao.selectedPattern()
            if (p != null) {
                val tomorrow = today.plusDays(1)
                val entries = dao.entriesByDateOnce(p.id, today, tomorrow)
                val pattern = ShiftPattern.parse(p.pattern)
                val base = JalaliDate(p.baseYear, p.baseMonth, p.baseDay)
                fun label(d: JalaliDate, withTime: Boolean): String {
                    val r = DayResolver.resolve(base, pattern, d, entries[d.key].orEmpty())
                    if (r.onLeave) return "مرخصی"
                    val t = ShiftType.byCode(r.effectiveCode)
                    val name = t?.name ?: "کد ${Fa.num(r.effectiveCode)}"
                    val time = prefs.shiftTimes[r.effectiveCode]?.takeIf { withTime && t?.isRest != true }
                    return if (time != null) "$name ${Fa.digits("${ShiftTime.fmt(time.startMin)}–${ShiftTime.fmt(time.endMin)}")}" else name
                }
                todayShift = label(today, true)
                tomorrowShift = label(tomorrow, false)
            }
        }

        // اذان بعدی: فقط وقتی امکانات مذهبی روشن است
        val nextPrayer = if (prefs.prayerOn) runCatching {
            val pl = prefs.prayerPlace
            PrayerCalc.next(now, zone, pl.lat, pl.lon, prefs.prayerMethod)
        }.getOrNull() else null
        val prayerLine = nextPrayer?.let { "${it.prayer.title} ${Fa.digits(ShiftTime.fmt(it.minuteOfDay))}" }

        val c = DateBarText.build(today, gregorian, hijri, todayShift, tomorrowShift, holiday, prayerLine)

        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تاریخ و اطلاعات", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "تاریخ امروز، شیفت امروز و فردا و اذان بعدی؛ همیشه در نوار اعلان‌ها"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, REQ_OPEN, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(c.title).setContentText(c.text)
            .setStyle(Notification.BigTextStyle().bigText(c.big))
            .setContentIntent(open)
            .setDeleteIntent(receiverIntent(ctx, ACTION_DISMISSED, REQ_DELETE))
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        c.sub?.let { b.setSubText(it) }
        runCatching { nm.notify(NOTIF_ID, b.build()) }

        // تازه‌سازی بعدی: نیمه‌شبِ محلی یا لحظهٔ اذان بعدی، هرکدام زودتر
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 30_000L
        val at = listOfNotNull(midnight, nextPrayer?.atMs?.plus(60_000L)).filter { it > now }.minOrNull() ?: midnight
        scheduleNext(ctx, at)
    }

    private fun scheduleNext(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = receiverIntent(ctx, ACTION_UPDATE, REQ_NEXT)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }
    }

    /** اعلان و تازه‌سازی زمان‌بندی‌شده را برمی‌دارد (وقتی کاربر در تنظیمات خاموشش می‌کند). */
    fun cancel(context: Context) {
        val ctx = context.applicationContext
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        ctx.getSystemService(AlarmManager::class.java)?.cancel(receiverIntent(ctx, ACTION_UPDATE, REQ_NEXT))
    }

    /** از جاهایی که suspend نیستند (مثلاً Activity) صدا زده می‌شود. */
    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { refresh(app) } }
    }
}

/** تازه‌سازی اعلان: زمان‌بندی نیمه‌شب/اذان، ریبوت، تغییر ساعت/تاریخ/منطقهٔ زمانی، به‌روزرسانی برنامه و کنار زدنِ اعلان توسط کاربر. */
class DateBarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { DateBar.refresh(context) } } finally { pending.finish() }
        }
    }
}
