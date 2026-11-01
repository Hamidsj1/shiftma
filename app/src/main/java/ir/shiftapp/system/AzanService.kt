package ir.shiftapp.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import ir.shiftapp.core.AzanSounds
import ir.shiftapp.ui.MainActivity

/**
 * پخش کامل اذان (چند دقیقه) وقتی هشدار می‌زند. اعلان معمولی صدایش را زود قطع می‌کند، پس برای اذانِ همراه برنامه
 * و فایل‌های پوشهٔ برنامه یک سرویس پیش‌زمینه با دکمهٔ «توقف» پخش می‌کند.
 */
class AzanService : Service() {
    private var session: android.media.session.MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "اذان"
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val token = intent.getStringExtra(EXTRA_SOUND) ?: ""
        val alarm = intent.getBooleanExtra(EXTRA_ALARM, true)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "پخش اذان", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)      // صدا را خود سرویس پخش می‌کند؛ اعلان فقط برای دیده شدن و قطع کردن است
        })
        val open = PendingIntent.getActivity(
            this, 7007, Intent(this, MainActivity::class.java).putExtra(ir.shiftapp.ui.EXTRA_SECTION, "prayer"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 7008, Intent(this, AzanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val screen = PendingIntent.getActivity(
            this, 7009, stopScreenIntent(title, body), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nb = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(stop).setDeleteIntent(stop)       // لمس یا کنار زدن اعلان هم اذان را قطع می‌کند
            .setOngoing(false).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "توقف", stop).build())
        if (alarm) nb.setFullScreenIntent(screen, true)          // مثل تماس: روی قفل‌صفحه صفحهٔ بزرگ قطع باز می‌شود
        val n = nb.build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)

        // دکمه‌های صدا (حتی با صفحهٔ خاموش یا برنامهٔ بسته) اذانِ در حال پخش را قطع می‌کنند
        AlarmKeyStop.stopSession(session)
        session = AlarmKeyStop.startSession(this, "ShiftMaAzan") { AlarmKeyStop.stopAll(applicationContext) }
        playing = true
        if (alarm) {
            // مثل آلارم: صفحه روشن شود و صفحهٔ تمام‌صفحهٔ قطع (کشیدن / دکمهٔ بزرگ / دکمهٔ صدا) باز شود
            AlarmKeyStop.wakeScreen(this)
            runCatching { startActivity(stopScreenIntent(title, body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        val ok = AzanPlayer.playToken(this, token, alarm) { finish() }
        if (!ok) {   // فایل پاک شده یا خراب است: با صدای پیش‌فرض اعلان بده
            finish()
            ReminderScheduler.showPrayer(this, title, body, alarm, "")
        }
        return START_NOT_STICKY
    }

    private fun stopScreenIntent(title: String, body: String): Intent =
        Intent(this, ir.shiftapp.ui.AlarmActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_TITLE, title).putExtra(ReminderScheduler.EXTRA_BODY, body)
            .putExtra(ReminderScheduler.EXTRA_KIND, KIND_AZAN)

    private fun finish() {
        AzanPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        playing = false
        AlarmKeyStop.stopSession(session)
        session = null
        AzanPlayer.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "azan_play_v2"
        private const val NOTIF_ID = 7102
        private const val ACTION_STOP = "ir.shiftapp.AZAN_STOP"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_ALARM = "alarm"

        /** نوع آلارمِ صفحهٔ تمام‌صفحه برای اذان (چرت ندارد). */
        const val KIND_AZAN = "azan"

        /** true تا وقتی اذان از سرویس در حال پخش است؛ دکمه‌های صدا با همین می‌فهمند چیزی برای قطع کردن هست. */
        @Volatile
        var playing = false
            private set

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, AzanService::class.java)) } }

        /** نقطهٔ ورود همهٔ هشدارهای اذان (گیرندهٔ آلارم و دکمهٔ آزمایشی). */
        fun trigger(ctx: Context, title: String, body: String, alarm: Boolean, token: String) {
            // صدای همراه برنامه و فایل‌ها همیشه از سرویس پخش می‌شوند؛ صدای سیستمی/پیش‌فرض هم در حالت «آلارم»،
            // تا همهٔ آلارم‌ها با دکمهٔ صدا قابل قطع باشند (اعلان معمولی صدایش را سیستم پخش می‌کند و راه قطعی ندارد).
            val viaService = when (AzanSounds.kind(token)) {
                AzanSounds.Kind.BUILTIN, AzanSounds.Kind.FILE -> true
                else -> alarm
            }
            if (viaService) {
                val i = Intent(ctx, AzanService::class.java)
                    .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body).putExtra(EXTRA_SOUND, token).putExtra(EXTRA_ALARM, alarm)
                val ok = runCatching { ctx.startForegroundService(i) }.isSuccess
                if (!ok) ReminderScheduler.showPrayer(ctx, title, body, alarm, token.takeIf { AzanSounds.kind(it) == AzanSounds.Kind.SYSTEM } ?: "")
            } else ReminderScheduler.showPrayer(ctx, title, body, alarm, token)
        }
    }
}
