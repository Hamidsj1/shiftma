package ir.shiftapp.system

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import ir.shiftapp.R
import ir.shiftapp.core.AzanSounds

/** پخش‌کنندهٔ اذان (یک نمونهٔ فعال). هم برای پیش‌شنیدن در برنامه و هم برای [AzanService]. باید روی نخ اصلی صدا زده شود. */
object AzanPlayer {
    private var mp: MediaPlayer? = null

    fun stop() {
        mp?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        mp = null
    }

    private fun start(alarm: Boolean, async: Boolean, onDone: (Boolean) -> Unit, setSource: MediaPlayer.() -> Unit): Boolean {
        stop()
        val p = MediaPlayer()
        return try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            p.setSource()
            p.setOnCompletionListener { if (mp === it) stop(); onDone(true) }
            p.setOnErrorListener { _, _, _ -> if (mp === p) stop(); onDone(false); true }
            mp = p
            if (async) { p.setOnPreparedListener { it.start() }; p.prepareAsync() } else { p.prepare(); p.start() }
            true
        } catch (e: Exception) {
            runCatching { p.release() }
            if (mp === p) mp = null
            false
        }
    }

    /** پخش صدای ذخیره‌شده در تنظیمات؛ false اگر منبع در دسترس نبود (مثلاً فایل پاک شده). */
    fun playToken(ctx: Context, token: String, alarm: Boolean, onDone: (Boolean) -> Unit = {}): Boolean = when (AzanSounds.kind(token)) {
        AzanSounds.Kind.BUILTIN -> start(alarm, false, onDone) {
            ctx.resources.openRawResourceFd(R.raw.azan_builtin).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
        AzanSounds.Kind.FILE -> {
            val f = AzanFiles.fileFor(ctx, token)
            if (f == null) false else start(alarm, false, onDone) { setDataSource(f.absolutePath) }
        }
        AzanSounds.Kind.SYSTEM -> start(alarm, false, onDone) { setDataSource(ctx, Uri.parse(token)) }
        AzanSounds.Kind.DEFAULT -> start(alarm, false, onDone) {
            setDataSource(ctx, RingtoneManager.getDefaultUri(if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION))
        }
    }

    /** پخش آنلاین (استریم) از یک پیوند https؛ فقط با درخواست صریح کاربر. */
    fun playUrl(url: String, alarm: Boolean, onDone: (Boolean) -> Unit = {}): Boolean =
        AzanSounds.isHttps(url) && start(alarm, true, onDone) { setDataSource(url.trim()) }
}
