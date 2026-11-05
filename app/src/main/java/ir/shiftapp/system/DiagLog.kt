package ir.shiftapp.system

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * گزارش عملکرد و خطای کوچک روی همین گوشی (فایل متنی، حداکثر ~۱۲۸ کیلوبایت؛ قدیمی‌ها خودکار پاک می‌شوند).
 * فقط رویداد و زمان ثبت می‌شود؛ هرگز نام، تلفن، متن یادداشت، آدرس سایت یا رمز. هیچ‌جا ارسال نمی‌شود؛
 * فقط با کلید مخفی و رمز روزانه، کاربر خودش آن را کپی می‌کند. جزئیات: docs/DIAGNOSTICS.md
 */
object DiagLog {
    const val FILE = "diag.log"
    const val MAX_BYTES = 128 * 1024
    const val KEEP_BYTES = 64 * 1024
    private val lock = Any()
    private var dir: File? = null
    private var prevHandler: Thread.UncaughtExceptionHandler? = null

    fun init(ctx: Context) { if (dir == null) dir = ctx.applicationContext.filesDir }

    /** ثبت خطاهای کنترل‌نشده (کرش) پیش از بسته شدن برنامه؛ سپس همان رفتار پیش‌فرض ادامه می‌یابد. */
    fun installCrashHandler(ctx: Context) {
        init(ctx)
        if (prevHandler != null) return
        prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { e("CRASH", "thread=${t.name}", e) }
            prevHandler?.uncaughtException(t, e)
        }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg + (t?.let { "\n" + trace(it) } ?: ""))

    /** زمان‌سنجی: بلوک را اجرا می‌کند و مدت را ثبت می‌کند (فقط اگر از [slowMs] بیشتر شد یا [always]). */
    inline fun <T> timed(tag: String, what: String, slowMs: Long = 400, always: Boolean = false, block: () -> T): T {
        val t0 = System.currentTimeMillis()
        try { return block() } finally {
            val d = System.currentTimeMillis() - t0
            if (always || d >= slowMs) i(tag, "$what ${d}ms" + if (d >= slowMs) " (کند)" else "")
        }
    }

    internal fun trace(t: Throwable): String {
        val sb = StringBuilder()
        var c: Throwable? = t; var depth = 0
        while (c != null && depth < 3) {
            sb.append(if (depth == 0) "" else "Caused by: ").append(c.javaClass.name).append(": ").append(c.message.orEmpty().take(200)).append('\n')
            c.stackTrace.take(12).forEach { sb.append("  at ").append(it).append('\n') }
            c = c.cause; depth++
        }
        return sb.toString().trimEnd()
    }

    private fun write(level: String, tag: String, msg: String) {
        val d = dir ?: return
        runCatching {
            synchronized(lock) {
                val f = File(d, FILE)
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                f.appendText("$ts $level/$tag $msg\n")
                if (f.length() > MAX_BYTES) f.writeText(trimTail(f.readText(), KEEP_BYTES))
            }
        }
    }

    /** آخرین حدود [keep] نویسه، از ابتدای یک خط کامل. */
    internal fun trimTail(text: String, keep: Int): String {
        if (text.length <= keep) return text
        val cut = text.substring(text.length - keep)
        val nl = cut.indexOf('\n')
        return if (nl >= 0) cut.substring(nl + 1) else cut
    }

    fun read(ctx: Context, maxLines: Int = 200): String = synchronized(lock) {
        val f = File(ctx.applicationContext.filesDir, FILE)
        if (!f.isFile) "" else runCatching { f.readLines().takeLast(maxLines).joinToString("\n") }.getOrDefault("")
    }

    fun clear(ctx: Context) { synchronized(lock) { runCatching { File(ctx.applicationContext.filesDir, FILE).delete() } } }
}
