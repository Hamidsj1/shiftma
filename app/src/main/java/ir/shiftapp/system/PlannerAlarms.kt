package ir.shiftapp.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ir.shiftapp.core.Planner
import ir.shiftapp.data.AppDb
import ir.shiftapp.data.prefsOnce
import ir.shiftapp.ui.MainActivity
import java.time.ZoneId

/**
 * آلارم‌های «برنامهٔ روزانه»؛ زمان‌بندی مستقل از یادآور شیفت/یادداشت (کد درخواست و کنش جدا) تا با آن‌ها تداخل نکند.
 * فقط نزدیک‌ترین هشدار نگه داشته می‌شود؛ بعد از شلیک، تغییر داده یا ریبوت دوباره از [ReminderScheduler.reschedule] ساخته می‌شود.
 * نمایش و صدا از همان سرویس آلارم موجود (AlarmRingService) است.
 */
object PlannerAlarms {
    const val ACTION_FIRE = "ir.shiftapp.action.PLANNER_FIRE"
    const val EXTRA_TITLE = "planner_title"
    const val EXTRA_BODY = "planner_body"
    private const val REQ = 7010

    private fun pi(ctx: Context, title: String = "", body: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    suspend fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(app))
        val prefs = AppDb.get(app).dao().prefsOnce()
        val next = Planner.nextAlarm(Planner.decode(prefs.plannerRaw), System.currentTimeMillis(), ZoneId.systemDefault()) ?: return
        val show = PendingIntent.getActivity(
            app, 7011, Intent(app, MainActivity::class.java).putExtra(ir.shiftapp.ui.EXTRA_SECTION, "planner"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try { am.setAlarmClock(AlarmManager.AlarmClockInfo(next.triggerAtMs, show), pi(app, next.title, next.body)) }
        catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMs, pi(app, next.title, next.body)) }
    }
}
