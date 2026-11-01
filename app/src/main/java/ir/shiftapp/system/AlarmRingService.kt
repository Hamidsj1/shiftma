package ir.shiftapp.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaSession
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import ir.shiftapp.R
import ir.shiftapp.ui.AlarmActivity
import kotlinx.coroutines.runBlocking

/** عنوان، متن و نوع آلارمِ در حال زنگ. */
class RingInfo(val title: String, val body: String, val kind: String?)

/**
 * آلارم تکرارشونده با فاصله: هر بار [RING_MS] صدای آلارم گوشی در حلقه پخش می‌شود (و لرزش تکرار می‌شود)، بعد [GAP_MS] سکوت،
 * و دوباره زنگ می‌زند؛ جمعاً [MAX_RINGS] بار، مگر اینکه کاربر «قطع آلارم» یا «۵ دقیقه بعد» را بزند.
 * قطع از مسیر [ReminderScheduler.dismiss] انجام می‌شود.
 *
 * راه‌های قطع (ترکیبی از ساعت گوگل، سامسونگ و آلارمی): ۱) صفحهٔ تمام‌صفحه با دکمه‌های بزرگ «قطع» و «چرت» که روی قفل‌صفحه باز می‌شود،
 * ۲) دکمه‌های همیشه‌ پیدای «قطع آلارم» و «چرت» روی خود اعلان، ۳) لمس اعلان که همان صفحه را باز می‌کند، ۴) کنار زدن اعلان، ۵) دکمه‌های صدا، ۶) کشیدن صفحهٔ آلارم.
 * اعلان در حالت بازشده نوار ۷ روزهٔ تقویم با شیفت هر روز را هم نشان می‌دهد.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rings = 0
    private var session: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var unlockReceiver: BroadcastReceiver? = null

    /**
     * باز شدن قفل گوشی آلارم را خاموش می‌کند (مثل آلارم‌های گوشی). گیرندهٔ تعریف‌شده در مانیفست برای USER_PRESENT
     * از اندروید ۸ به بعد کار نمی‌کند، پس همین‌جا و فقط تا وقتی آلارم زنگ می‌زند ثبت می‌شود.
     */
    private fun startUnlockStop() {
        stopUnlockStop()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { ReminderScheduler.dismiss(applicationContext) }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(r, IntentFilter(Intent.ACTION_USER_PRESENT))
            unlockReceiver = r
        }
    }

    private fun stopUnlockStop() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
    }

    /**
     * دکمه‌های صدا (حتی وقتی صفحهٔ آلارم باز نیست یا صفحه خاموش است) آلارم را قطع می‌کنند:
     * یک نشست رسانهٔ فعال با کنترل صدای «راه دور» دکمه‌های صدا را می‌گیرد و به‌جای تغییر صدا، قطع را اجرا می‌کند.
     */
    private fun startVolumeStop() {
        stopVolumeStop()
        session = AlarmKeyStop.startSession(this, "ShiftMaAlarm") { AlarmKeyStop.stopAll(applicationContext) }
    }

    private fun stopVolumeStop() {
        AlarmKeyStop.stopSession(session)
        session = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "آلارم شیفت"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""
        val single = intent.getBooleanExtra(EXTRA_SINGLE, false)
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)

        val nm = getSystemService(NotificationManager::class.java)
        requestAlarmAudioFocus()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "آلارم در حال پخش", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)      // صدا و لرزش را خود سرویس تکرار می‌کند
            }
        )
        handler.removeCallbacksAndMessages(null)
        ringing = true
        current = RingInfo(title, body, kind)
        openedOnce = false
        val n = buildNotification(title, body, single, kind, emptyList())
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)

        // نوار تقویم از دیتابیس خوانده می‌شود؛ تا آماده شد اعلان بدون صدای دوباره به‌روز می‌شود
        Thread {
            val strip = runCatching { runBlocking { AlarmStrip.load(applicationContext) } }.getOrDefault(emptyList())
            if (strip.isNotEmpty()) handler.post {
                if (ringing) runCatching { nm.notify(NOTIF_ID, buildNotification(title, body, single, kind, strip)) }
            }
        }.start()

        startVolumeStop()
        startUnlockStop()
        // مثل تماس: صفحه را روشن می‌کنیم و صفحهٔ تمام‌صفحهٔ قطع را مستقیم هم باز می‌کنیم (علاوه بر full-screen intent اعلان).
        // اگر سیستم اجازهٔ باز کردن مستقیم ندهد، همان full-screen intent و اعلان می‌مانند.
        wakeScreen()
        openStopScreen(this)
        rings = 0
        ring()
        return START_NOT_STICKY
    }

    /** صفحه را (حتی از حالت خاموش و قفل) روشن می‌کند. wake lock با تایم‌اوت خودش آزاد می‌شود؛ صفحهٔ آلارم هم FLAG_KEEP_SCREEN_ON دارد. */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java) ?: return
            pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "ShiftMa:alarmScreen"
            ).acquire(20_000L)
        }
    }

    private fun alarmScreenIntent(title: String, body: String, single: Boolean, kind: String?): PendingIntent {
        val i = Intent(this, AlarmActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_TITLE, title).putExtra(ReminderScheduler.EXTRA_BODY, body)
        if (kind != null) i.putExtra(ReminderScheduler.EXTRA_KIND, kind)
        return PendingIntent.getActivity(this, 7003, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildNotification(title: String, body: String, single: Boolean, kind: String?, strip: List<StripDay>): Notification {
        val screen = alarmScreenIntent(title, body, single, kind)
        val stop = ReminderScheduler.actionIntent(this, ReminderScheduler.ACTION_DONE, 7005, title, body, kind)
        val snooze = ReminderScheduler.actionIntent(this, ReminderScheduler.ACTION_SNOOZE, 7004, title, body, kind)
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(screen).setFullScreenIntent(screen, true)
            .setDeleteIntent(stop)
            // اجازه می‌دهد کاربر اعلان را کنار بزند؛ setDeleteIntent پایین همان لحظه آلارم را قطع می‌کند.
            .setOngoing(false).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "قطع آلارم", stop).build())
            .addAction(Notification.Action.Builder(null, "چرت ${ReminderScheduler.SNOOZE_MIN} دقیقه", snooze).build())
        if (strip.isNotEmpty()) {
            b.setStyle(Notification.DecoratedCustomViewStyle()).setCustomBigContentView(stripViews(title, body, strip, stop))
        }
        return b.build()
    }

    private fun stripViews(title: String, body: String, strip: List<StripDay>, stop: PendingIntent): RemoteViews {
        val rv = RemoteViews(packageName, R.layout.notif_alarm_strip)
        rv.setOnClickPendingIntent(R.id.na_stop, stop)      // دکمهٔ بزرگ قطع داخل اعلان (برای آدم خواب‌آلود)
        rv.setTextViewText(R.id.na_title, title)
        rv.setTextViewText(R.id.na_body, body)
        rv.setViewVisibility(R.id.na_body, if (body.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
        strip.take(CELLS.size).forEachIndexed { i, d ->
            val c = CELLS[i]
            rv.setTextViewText(c[1], d.weekday)
            rv.setTextViewText(c[2], d.num)
            rv.setTextViewText(c[3], d.shift)
            rv.setInt(c[3], "setBackgroundColor", d.color)
            rv.setInt(c[0], "setBackgroundColor", if (d.today) TODAY_TINT else 0)
        }
        return rv
    }

    /** یک نوبت زنگ: صدا و لرزش، بعد از [RING_MS] قطع؛ اگر نوبت‌ها تمام نشده بعد از [GAP_MS] دوباره. */
    private fun ring() {
        rings++
        AlarmKeyStop.touch(session)      // نشست قطع با دکمهٔ صدا در هر نوبت دوباره بالاترین اولویت را بگیرد
        stopEffects()
        startSound()
        startVibration()
        handler.postDelayed({
            stopEffects()
            if (rings >= MAX_RINGS) stopSelf() else handler.postDelayed({ ring() }, GAP_MS)
        }, RING_MS)
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            p.setDataSource(this, uri)
            p.isLooping = true
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            // صدا نشد: لرزش و اعلان همچنان هست تا کاربر متوجه شود
        }
    }

    /** صدای آلارم باید روی صدای موسیقی/ویدیو اولویت داشته باشد. */
    private fun requestAlarmAudioFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        }
    }

    private fun abandonAlarmAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
        audioFocusRequest = null
        audioManager = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator = v
        runCatching {
            v?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),     // 0 = از ابتدای الگو تکرار شود
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }
    }

    private fun stopEffects() {
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        ringing = false
        current = null
        stopUnlockStop()
        handler.removeCallbacksAndMessages(null)
        stopEffects()
        stopVolumeStop()
        abandonAlarmAudioFocus()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "shift_alarm_ring"
        private const val NOTIF_ID = 7110
        private const val TODAY_TINT = 0x33808CFF

        /** true تا وقتی آلارم در حال زنگ است؛ صفحهٔ آلارم با همین می‌فهمد کی باید بسته شود. */
        @Volatile
        var ringing = false
            private set

        /** عنوان و متن آلارمِ در حال زنگ؛ برای باز کردن صفحهٔ قطع از خود برنامه. */
        @Volatile
        var current: RingInfo? = null
            private set

        @Volatile
        private var openedOnce = false

        /** صفحهٔ تمام‌صفحهٔ قطع/چرت را باز می‌کند؛ false اگر آلارمی در حال زنگ نیست یا سیستم اجازه نداد. */
        fun openStopScreen(ctx: Context): Boolean {
            val c = current ?: return false
            if (!ringing) return false
            val i = Intent(ctx, AlarmActivity::class.java)
                .putExtra(ReminderScheduler.EXTRA_TITLE, c.title).putExtra(ReminderScheduler.EXTRA_BODY, c.body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (c.kind != null) i.putExtra(ReminderScheduler.EXTRA_KIND, c.kind)
            return runCatching { ctx.startActivity(i) }.isSuccess
        }

        /** هر بار که برنامه باز می‌شود و آلارم هنوز زنگ می‌زند، یک‌بار (برای همان زنگ) صفحهٔ قطع را باز می‌کند. */
        fun openStopScreenOnce(ctx: Context) {
            if (!ringing || openedOnce) return
            openedOnce = true
            openStopScreen(ctx)
        }

        /** شناسهٔ ویوهای هر خانهٔ نوار: [خانه، روز هفته، شمارهٔ روز، شیفت]. */
        private val CELLS = listOf(
            intArrayOf(R.id.na_cell0, R.id.na_wd0, R.id.na_num0, R.id.na_sh0),
            intArrayOf(R.id.na_cell1, R.id.na_wd1, R.id.na_num1, R.id.na_sh1),
            intArrayOf(R.id.na_cell2, R.id.na_wd2, R.id.na_num2, R.id.na_sh2),
            intArrayOf(R.id.na_cell3, R.id.na_wd3, R.id.na_num3, R.id.na_sh3),
            intArrayOf(R.id.na_cell4, R.id.na_wd4, R.id.na_num4, R.id.na_sh4),
            intArrayOf(R.id.na_cell5, R.id.na_wd5, R.id.na_num5, R.id.na_sh5),
            intArrayOf(R.id.na_cell6, R.id.na_wd6, R.id.na_num6, R.id.na_sh6)
        )
        private const val EXTRA_SINGLE = "single"
        private const val RING_MS = 60_000L        // هر نوبت ۱ دقیقه زنگ
        private const val GAP_MS = 4 * 60_000L     // ۴ دقیقه سکوت؛ یعنی هر ۵ دقیقه یک بار
        private const val MAX_RINGS = 4            // جمعاً ۴ نوبت (دقیقهٔ ۰، ۵، ۱۰ و ۱۵)

        /** true اگر سرویس شروع شد؛ false یعنی سیستم اجازه نداد (مثلاً محدودیت پس‌زمینه) و باید اعلان معمولی نشان داده شود. */
        fun start(ctx: Context, title: String, body: String, single: Boolean, kind: String?): Boolean {
            val i = Intent(ctx, AlarmRingService::class.java)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title).putExtra(ReminderScheduler.EXTRA_BODY, body)
                .putExtra(EXTRA_SINGLE, single)
            if (kind != null) i.putExtra(ReminderScheduler.EXTRA_KIND, kind)
            return runCatching { ctx.startForegroundService(i) }.isSuccess
        }

        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, AlarmRingService::class.java)) } }
    }
}
