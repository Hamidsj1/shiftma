package ir.shiftapp.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.VolumeProvider
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

    /** نشست رسانه‌ای که دکمه‌های صدا را می‌گیرد و به‌جای تغییر بلندی، [onVolume] را اجرا می‌کند. */
    fun startSession(ctx: Context, tag: String, onVolume: () -> Unit): MediaSession? = runCatching {
        val s = MediaSession(ctx, tag)
        touch(s)
        s.setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 1, 0) {
            override fun onAdjustVolume(direction: Int) { onVolume() }
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
    private var swallowUpOf = -1

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!AlarmKeyStop.isVolumeKey(code)) return false
        if (event.action == KeyEvent.ACTION_UP) {
            // رها کردن دکمه‌ای که پایین‌رفتنش را برای قطع آلارم گرفته‌ایم هم نباید بلندی را تغییر دهد
            if (swallowUpOf == code) { swallowUpOf = -1; return true }
            return false
        }
        if (!AlarmKeyStop.isRinging()) return false
        if (event.repeatCount == 0) AlarmKeyStop.stopAll(applicationContext)
        swallowUpOf = code
        return true
    }
}
