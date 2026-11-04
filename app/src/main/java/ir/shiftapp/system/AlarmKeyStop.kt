package ir.shiftapp.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.VolumeProvider
import android.os.Handler
import android.os.Looper
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * قطع هر آلارمِ در حال پخش (آلارم شیفت، آلارم تکی، آلارم آزمایشی، اذان) با دکمه‌های صدا؛ حتی وقتی برنامه بسته است،
 * صفحه خاموش است یا هیچ صفحه‌ای از برنامه دیده نمی‌شود. سه لایه دارد، از ضعیف به قوی:
 *  ۱) نشست رسانهٔ «کنترل صدای راه دور» ([startSession]) — بدون هیچ مجوزی؛ ولی اندروید تضمین نمی‌کند دکمه همیشه به آن برسد
 *     (مثلاً وقتی برنامه‌ای مثل پخش‌کنندهٔ ویدیو کنترل صدای خودش را قفل کرده است)؛
 *  ۲) صفحهٔ تمام‌صفحهٔ آلارم ([ir.shiftapp.ui.AlarmActivity]) وقتی باز است؛
 *  ۳) سرویس دسترسی‌پذیری [AlarmKeyService] که اگر کاربر روشنش کند، دکمه‌های صدا را در همهٔ حالت‌ها پیش از هر برنامهٔ دیگر می‌گیرد.
 */
object AlarmKeyStop {
    fun isVolumeKey(code: Int) =
        code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE

    /** true اگر آلارم یا اذانی در حال پخش (یا بین دو نوبت زنگ) است. */
    fun isRinging(): Boolean = AlarmRingService.ringing || AzanService.playing

    /** صفحه را (حتی از حالت خاموش و قفل) تا ۲۰ ثانیه روشن می‌کند؛ مثل تماس. */
    @Suppress("DEPRECATION")
    fun wakeScreen(ctx: Context) {
        runCatching {
            val pm = ctx.getSystemService(PowerManager::class.java) ?: return
            pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "ShiftMa:alarmScreen"
            ).acquire(20_000L)
        }
    }

    /** همهٔ صداهای در حال پخش را قطع می‌کند. */
    fun stopAll(ctx: Context) {
        val app = ctx.applicationContext
        ReminderScheduler.dismiss(app)
        AzanService.stop(app)
    }

    /**
     * منطق «لمس کوتاه = قطع آلارم، نگه‌داشتن = کم/زیاد کردن صدا». اندروید بعد از حدود نیم‌ثانیه نگه‌داشتن، رویدادهای تکراری
     * (repeatCount > 0) می‌فرستد؛ تا آن موقع دکمه «لمس کوتاه» حساب می‌شود و با رها کردن، آلارم قطع می‌شود.
     * [onHold] برای هر تکرار (حداکثر هر [throttleMs]) با کد دکمه صدا زده می‌شود.
     */
    class HoldGesture(
        private val onTap: () -> Unit, private val onHold: (keyCode: Int) -> Unit,
        private val now: () -> Long = { System.currentTimeMillis() }, private val throttleMs: Long = 220L
    ) {
        private var downCode = -1
        private var tap = false
        private var lastHold = 0L

        fun down(code: Int, repeatCount: Int) {
            if (repeatCount == 0) { downCode = code; tap = true; return }
            downCode = code
            tap = false                       // نگه داشته شد؛ رها کردن دیگر آلارم را قطع نمی‌کند
            val t = now()
            if (t - lastHold >= throttleMs) { lastHold = t; onHold(code) }
        }

        /** true = این رها کردن مال دکمه‌ای بود که ما گرفته بودیم (باید بلعیده شود). */
        fun up(code: Int): Boolean {
            if (downCode != code) return false
            val wasTap = tap
            downCode = -1; tap = false
            if (wasTap) onTap()
            return true
        }
    }

    private const val VOL_PREFS = "shiftma_volsave"

    /** صدای آلارم سیستم را یک پله کم/زیاد می‌کند؛ مقدار اولیه یک‌بار ذخیره می‌شود تا بعد از آلارم برگردانده شود. */
    fun adjustAlarmVolume(ctx: Context, raise: Boolean) {
        runCatching {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return
            val sp = ctx.applicationContext.getSharedPreferences(VOL_PREFS, Context.MODE_PRIVATE)
            if (!sp.contains("v")) sp.edit().putInt("v", am.getStreamVolume(AudioManager.STREAM_ALARM)).apply()
            am.adjustStreamVolume(AudioManager.STREAM_ALARM, if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        }
    }

    /** بعد از پایان آلارم/اذان: اگر با نگه‌داشتن دکمه صدا کم شده بود، به مقدار قبلی برمی‌گردد تا آلارم بعدی بی‌صدا نماند. */
    fun restoreVolume(ctx: Context) {
        runCatching {
            val sp = ctx.applicationContext.getSharedPreferences(VOL_PREFS, Context.MODE_PRIVATE)
            if (!sp.contains("v")) return
            val v = sp.getInt("v", -1)
            sp.edit().remove("v").apply()
            if (v >= 0) ctx.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0)
        }
    }

    /** نشست رسانه‌ای که دکمه‌های صدا را می‌گیرد و به‌جای تغییر بلندی، [onVolume] را اجرا می‌کند. */
    fun startSession(ctx: Context, tag: String, onVolume: () -> Unit): MediaSession? = runCatching {
        val s = MediaSession(ctx, tag)
        touch(s)
        val app = ctx.applicationContext
        val h = Handler(Looper.getMainLooper())
        var lastCall = 0L
        var held = false
        val stop = Runnable { onVolume() }
        // نشست رسانه‌ای رها شدن دکمه را نمی‌بیند: اگر تا ۴۵۰ms تکرار نیامد = لمس کوتاه (قطع)؛ اگر تکرار آمد = نگه داشتن (کم/زیاد کردن صدا)
        fun adjust(direction: Int) {
            val t = System.currentTimeMillis()
            if (t - lastCall > 700) held = false
            if (!held && lastCall != 0L && t - lastCall < 200) { h.removeCallbacks(stop); held = true }
            lastCall = t
            if (held) AlarmKeyStop.adjustAlarmVolume(app, raise = direction > 0)
            else { h.removeCallbacks(stop); h.postDelayed(stop, 450L) }
        }
        s.setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 1, 0) {
            override fun onAdjustVolume(direction: Int) { adjust(direction) }
            override fun onSetVolumeTo(volume: Int) { onVolume() }
        })
        s.isActive = true
        s
    }.getOrNull()

    /** وضعیت «در حال پخش» را دوباره اعلام می‌کند تا این نشست بالاترین اولویت را برای دکمه‌های صدا داشته باشد. */
    fun touch(s: MediaSession?) {
        if (s == null) return
        runCatching {
            s.setPlaybackState(PlaybackState.Builder().setActions(0).setState(PlaybackState.STATE_PLAYING, 0L, 1f).build())
        }
    }

    fun stopSession(s: MediaSession?) {
        if (s == null) return
        runCatching { s.isActive = false }
        runCatching { s.release() }
    }

    /** آیا کاربر سرویس دسترسی‌پذیری [AlarmKeyService] را روشن کرده است؟ */
    fun isKeyServiceEnabled(ctx: Context): Boolean {
        val enabled = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }
            .getOrNull().orEmpty()
        if (enabled.isBlank()) return false
        val cn = ComponentName(ctx, AlarmKeyService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), ignoreCase = true) || it.equals(cn.flattenToShortString(), ignoreCase = true)
        }
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

/**
 * فقط برای قطع آلارم با دکمهٔ صدا: وقتی آلارم یا اذان در حال پخش است، دکمه‌های صدا را می‌گیرد و آلارم را قطع می‌کند.
 * در بقیهٔ اوقات هیچ دکمه‌ای را نمی‌گیرد. هیچ رویداد یا محتوایی از صفحه خوانده نمی‌شود (در تنظیمات سرویس نوع رویدادی ثبت نشده است).
 */
class AlarmKeyService : AccessibilityService() {
    private val gesture = AlarmKeyStop.HoldGesture(
        onTap = { AlarmKeyStop.stopAll(applicationContext) },
        onHold = { code -> AlarmKeyStop.adjustAlarmVolume(applicationContext, raise = code == KeyEvent.KEYCODE_VOLUME_UP) }
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!AlarmKeyStop.isVolumeKey(code)) return false
        if (event.action == KeyEvent.ACTION_UP) return gesture.up(code)      // رها کردن: لمس کوتاه = قطع؛ بعد از نگه‌داشتن = فقط بلعیده می‌شود
        if (!AlarmKeyStop.isRinging()) return false
        gesture.down(code, event.repeatCount)
        return true
    }
}
