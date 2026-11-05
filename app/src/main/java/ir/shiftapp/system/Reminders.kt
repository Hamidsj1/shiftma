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
import android.media.AudioAttributes
import android.net.Uri
import android.media.RingtoneManager
import android.os.Build
import ir.shiftapp.core.*
import ir.shiftapp.data.*
import ir.shiftapp.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * فقط «نزدیک‌ترین» یادآور زمان‌بندی می‌شود؛ وقتی شلیک کرد (یا گوشی ریبوت شد، ساعت/منطقهٔ زمانی عوض شد،
 * یا داده‌ها تغییر کرد) دوباره نزدیک‌ترینِ بعدی محاسبه می‌شود. منطق محاسبه در [ReminderPlanner] است و تست‌پذیر.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "ir.shiftapp.action.REMINDER_FIRE"
    const val ACTION_SNOOZE = "ir.shiftapp.action.REMINDER_SNOOZE"
    const val ACTION_DONE = "ir.shiftapp.action.REMINDER_DONE"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_ALARM_ID = "alarm_id"      // ≠ ۰ یعنی آلارم تکی
    const val EXTRA_KIND = "alarm_kind"
    const val EXTRA_NOTE_ID = "note_id"        // ≠ ۰ یعنی یادآور یادداشت
    const val EXTRA_PRAYER = "prayer_id"       // ≠ خالی یعنی هشدار اذان
    const val EXTRA_RING = "ring"              // ۰ = طبق تنظیم شیفت، ۱ = آلارم، ۲ = اعلان
    const val EXTRA_DUE = "due_ms"             // زمان برنامه‌ریزی‌شده؛ برای «گزارش پخش» (ReliabilityStore)
    const val CH_NOTIFY = "shift_reminder"
    const val CH_ALARM = "shift_alarm"
    const val SNOOZE_MIN = 5
    private const val NOTIF_ID = 7100
    private const val NOTIF_PRAYER_ID = 7101

    private fun fireIntent(ctx: Context, next: Reminder?, kind: String?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        if (next != null) {
            i.putExtra(EXTRA_TITLE, next.title).putExtra(EXTRA_BODY, next.body).putExtra(EXTRA_ALARM_ID, next.alarmId)
                .putExtra(EXTRA_NOTE_ID, next.noteId).putExtra(EXTRA_RING, when (next.alarm) { null -> 0; true -> 1; false -> 2 })
                .putExtra(EXTRA_DUE, next.triggerAtMs)
            if (kind != null) i.putExtra(EXTRA_KIND, kind)
            next.prayerId?.let { i.putExtra(EXTRA_PRAYER, it) }
        }
        return PendingIntent.getBroadcast(ctx, 7001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * آلارم‌های گذشته (بیش از ۶۰ دقیقه) از لیست پاک می‌شوند و یادآور تکرارشوندهٔ یادداشت‌ها که زمانشان گذشته به نوبت بعد می‌روند؛
     * فقط اگر چیزی عوض شده باشد نوشته می‌شود (پس حلقهٔ بی‌پایان ساخته نمی‌شود).
     */
    private suspend fun prune(dao: AppDao, prefs: Prefs, now: Long, zone: ZoneId): Prefs {
        var out = prefs
        // آلارم تکراریِ از دست رفته به نوبت بعدی می‌رود؛ آلارم یک‌باره‌ی گذشته پاک می‌شود
        val keep = prefs.alarms.mapNotNull { if (it.triggerAtMs > now - 60 * 60_000L) it else it.advance(now, zone) }
        if (keep != prefs.alarms) {
            dao.put(Setting(PrefKeys.ALARMS, SingleAlarm.encode(keep)))
            out = out.copy(alarms = keep)
        }
        val notes = NoteReminders.catchUp(prefs.notes, now, zone)
        if (notes != prefs.notes) {
            dao.put(Setting(PrefKeys.NOTES, Note.encode(notes)))
            out = out.copy(notes = notes)
        }
        return out
    }

    suspend fun reschedule(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return@withContext
        am.cancel(fireIntent(app, null, null))
        runCatching { PlannerAlarms.schedule(app) }      // برنامهٔ روزانه: آلارم مستقل خودش
        val dao = AppDb.get(app).dao()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val prefs = prune(dao, dao.prefsOnce(), now, zone)
        val p = dao.selectedPattern()
        val today = JalaliCalendar.fromLocalDate(java.time.LocalDate.now(zone))
        val next = runCatching {
            if (p == null) ReminderPlanner.next(now, zone, null, null, emptyMap(), prefs)
            else {
                val entries = dao.entriesByDateOnce(p.id, today, today.plusDays(50))
                ReminderPlanner.next(now, zone, JalaliDate(p.baseYear, p.baseMonth, p.baseDay), ShiftPattern.parse(p.pattern), entries, prefs)
            }
        }.getOrNull() ?: return@withContext
        val kind = if (next.isSingle) prefs.alarms.firstOrNull { it.id == next.alarmId }?.kind else null
        val pi = fireIntent(app, next, kind)
        val asAlarm = next.alarm ?: prefs.reminderAlarm       // آلارم تکی همیشه آلارم است؛ یادداشت طبق انتخاب خودش
        try {
            if (asAlarm) {
                val show = PendingIntent.getActivity(app, 7002, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(next.triggerAtMs, show), pi)
            } else if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMs, pi)   // بدون مجوز دقیق: ممکن است چند دقیقه دیرتر برسد
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMs, pi)
        }
    }

    internal fun actionIntent(ctx: Context, action: String, code: Int, title: String, body: String, kind: String?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body)
        if (kind != null) i.putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** [single]: آلارم تکی؛ دکمه‌های «انجام دادم» و «۵ دقیقه بعد» دارد. */
    fun show(ctx: Context, title: String, body: String, alarm: Boolean, single: Boolean = false, kind: String? = null) {
        // نبودن مجوز اعلان نباید آلارم واقعی را قبل از شروع صدا حذف کند؛
        // سرویس foreground می‌تواند آلارم را پخش کند، حتی اگر اعلانش در اندروید ۱۳+ مخفی شود.
        if (alarm && AlarmRingService.start(ctx, title, body, single, kind)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH_NOTIFY, "یادآور شیفت", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_ALARM, "آلارم شیفت", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
            })
        }
        val open = PendingIntent.getActivity(ctx, 7003, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(ctx, if (alarm) CH_ALARM else CH_NOTIFY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(open).setAutoCancel(true)
        if (alarm) b.setCategory(Notification.CATEGORY_ALARM).setTimeoutAfter(15 * 60_000L)
        if (single) {
            b.addAction(Notification.Action.Builder(null, "انجام دادم", actionIntent(ctx, ACTION_DONE, 7005, title, body, kind)).build())
            b.addAction(Notification.Action.Builder(null, "$SNOOZE_MIN دقیقه بعد", actionIntent(ctx, ACTION_SNOOZE, 7004, title, body, kind)).build())
        } else if (alarm) {
            b.addAction(Notification.Action.Builder(null, "قطع آلارم", actionIntent(ctx, ACTION_DONE, 7005, title, body, kind)).build())
            b.addAction(Notification.Action.Builder(null, "چرت $SNOOZE_MIN دقیقه", actionIntent(ctx, ACTION_SNOOZE, 7004, title, body, kind)).build())
        }
        val n = b.build()
        if (alarm) n.flags = n.flags or Notification.FLAG_INSISTENT      // صدا و لرزش تا قطع اعلان تکرار شود
        nm.notify(NOTIF_ID, n)
    }

    /**
     * اعلان/آلارم اذان. کانالِ اعلان صدایش بعد از ساخت قابل‌تغییر نیست، پس برای هر صدای انتخابی کانال جدا (با شناسهٔ مبتنی بر نشانی صدا) ساخته می‌شود.
     * [soundUri] خالی = صدای پیش‌فرض آلارم/اعلان گوشی.
     */
    fun showPrayer(ctx: Context, title: String, body: String, alarm: Boolean, soundUri: String) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val uri = soundUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
        val chId = "prayer_" + (if (alarm) "alarm_" else "notify_") + Integer.toHexString(uri.toString().hashCode())
        nm.createNotificationChannel(NotificationChannel(chId, "اذان و اوقات شرعی", if (alarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(uri, AudioAttributes.Builder().setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION).build())
            enableVibration(alarm)
        })
        val open = PendingIntent.getActivity(
            ctx, 7006, Intent(ctx, MainActivity::class.java).putExtra(ir.shiftapp.ui.EXTRA_SECTION, "prayer"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(ctx, chId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(open).setAutoCancel(true)
        if (alarm) b.setCategory(Notification.CATEGORY_ALARM)
        nm.notify(NOTIF_PRAYER_ID, b.build())
    }

    fun dismiss(ctx: Context) {
        AlarmRingService.stop(ctx)
        AzanService.stop(ctx)
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(context).dao()
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        // رفتار مورد انتظار آلارم گوشی: با باز شدن قفل، زنگ متوقف شود.
                        ReminderScheduler.dismiss(context)
                    }
                    ReminderScheduler.ACTION_FIRE -> {
                        val prefs = dao.prefsOnce()
                        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ALARM_ID, 0L)
                        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, 0L)
                        val single = id != 0L
                        val prayerId = intent.getStringExtra(ReminderScheduler.EXTRA_PRAYER)
                        val ring = when (intent.getIntExtra(ReminderScheduler.EXTRA_RING, 0)) { 1 -> true; 2 -> false; else -> null }
                        // گزارش پخش: زمان برنامه‌ریزی‌شده در برابر زمان واقعی رسیدن (نشان می‌دهد سیستم گوشی هشدار را دیر رسانده یا نه)
                        val dueMs = intent.getLongExtra(ReminderScheduler.EXTRA_DUE, 0L)
                        if (dueMs > 0L) runCatching {
                            ReliabilityStore.record(
                                context, dueMs, System.currentTimeMillis(),
                                when { prayerId != null -> FireLog.KIND_PRAYER; noteId != 0L -> FireLog.KIND_NOTE; single -> FireLog.KIND_ALARM; else -> FireLog.KIND_SHIFT }
                            )
                        }
                        if (prayerId != null) {
                            // هشدار اذان فقط اگر هنوز امکانات مذهبی روشن است نمایش داده شود
                            if (prefs.prayerOn) AzanService.trigger(
                                context, intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "اذان",
                                intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: "", ring ?: prefs.prayerAlarm, prefs.prayerSound
                            )
                        } else ReminderScheduler.show(
                            context,
                            intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "یادآور شیفت",
                            intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: "",
                            alarm = ring ?: (single || prefs.reminderAlarm), single = single || noteId != 0L,
                            kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)
                        )
                        // آلارم یک‌باره بعد از شلیک پاک می‌شود (جانشین شب هم با پاک‌شدن هر دو هشدار خودکار خاموش می‌شود)؛ آلارم تکراری به نوبت بعد می‌رود
                        if (single) {
                            val nowMs = System.currentTimeMillis()
                            val zoneId = java.time.ZoneId.systemDefault()
                            dao.put(Setting(PrefKeys.ALARMS, SingleAlarm.encode(prefs.alarms.mapNotNull { if (it.id == id) it.advance(nowMs, zoneId) else it })))
                        }
                        // یادداشت: تکرارشونده به نوبت بعد می‌رود، یک‌باره خاموش می‌شود
                        if (noteId != 0L) {
                            val now = System.currentTimeMillis()
                            val zone = java.time.ZoneId.systemDefault()
                            val notes = prefs.notes.map { if (it.id == noteId) NoteReminders.afterFire(it, now, zone) else it }
                            dao.put(Setting(PrefKeys.NOTES, Note.encode(notes)))
                        }
                    }
                    PlannerAlarms.ACTION_FIRE -> ReminderScheduler.show(
                        context, intent.getStringExtra(PlannerAlarms.EXTRA_TITLE) ?: "برنامهٔ روزانه",
                        intent.getStringExtra(PlannerAlarms.EXTRA_BODY) ?: "", alarm = true, single = true
                    )
                    ReminderScheduler.ACTION_DONE -> ReminderScheduler.dismiss(context)
                    ReminderScheduler.ACTION_SNOOZE -> {
                        ReminderScheduler.dismiss(context)
                        val prefs = dao.prefsOnce()
                        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: SingleAlarm.KIND_USER
                        val label = if (kind == SingleAlarm.KIND_USER) intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty() else ""
                        val at = System.currentTimeMillis() + ReminderScheduler.SNOOZE_MIN * 60_000L
                        val list = prefs.alarms + SingleAlarm(SingleAlarm.nextId(prefs.alarms), at, label, kind)
                        dao.put(Setting(PrefKeys.ALARMS, SingleAlarm.encode(list)))
                    }
                }
                // شلیک، ریبوت، تغییر ساعت/منطقهٔ زمانی یا به‌روزرسانی برنامه: نزدیک‌ترین یادآور بعدی را بساز
                ReminderScheduler.reschedule(context)
                ShiftWidget.refresh(context)
                NextReminderWidget.refresh(context)
                PrayerWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
