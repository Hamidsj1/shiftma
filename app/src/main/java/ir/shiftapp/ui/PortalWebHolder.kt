package ir.shiftapp.ui

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView

/**
 * نگه‌داشتن موقت صفحهٔ سامانه‌ها (WebView) در حافظهٔ برنامه، تا با رفتن به «فیش‌های من»، تقویم یا هر بخش دیگر و برگشتن،
 * همان سامانه با همان ورود و همان صفحه باز شود و دوباره نام کاربری و رمز لازم نباشد.
 * فقط در حافظهٔ پروسه و فقط تا [IDLE_MS] پس از آخرین استفاده؛ بعد از آن یا با بسته شدن پروسه، صفحه از اول بارگذاری می‌شود.
 * حداکثر [MAX] سامانه نگه داشته می‌شود (قدیمی‌ترین کنار می‌رود).
 */
object PortalWebHolder {
    const val IDLE_MS = 15 * 60 * 1000L
    const val MAX = 2

    private class Entry(val web: WebView, val ctx: MutableContextWrapper, val startUrl: String, var lastUsed: Long)
    private val entries = LinkedHashMap<String, Entry>()
    private val handler = Handler(Looper.getMainLooper())

    class Result(val web: WebView, val reused: Boolean)

    /** WebViewِ این سامانه: اگر نگه داشته شده و تازه است همان، وگرنه یکی نو. در هر دو حالت به [activity] وصل می‌شود. */
    fun obtain(activity: Context, key: String, startUrl: String, now: Long = System.currentTimeMillis()): Result {
        expire(now)
        val e = entries.remove(key)
        if (e != null && e.startUrl == startUrl) {
            e.ctx.baseContext = activity
            (e.web.parent as? ViewGroup)?.removeView(e.web)
            return Result(e.web, true)
        }
        e?.let { destroy(it) }
        val ctx = MutableContextWrapper(activity)
        return Result(WebView(ctx).also { holders[it] = Pair(ctx, startUrl) }, false)
    }

    private val holders = java.util.WeakHashMap<WebView, Pair<MutableContextWrapper, String>>()

    /** هنگام بسته شدن صفحه: WebView را از صفحه جدا و نگه می‌دارد. [keep] = false یعنی همین‌جا نابود شود. */
    fun release(app: Context, key: String, web: WebView, keep: Boolean = true, now: Long = System.currentTimeMillis()) {
        val h = holders[web]
        (web.parent as? ViewGroup)?.removeView(web)
        if (!keep || h == null) { runCatching { web.stopLoading(); web.destroy() }; holders.remove(web); return }
        h.first.baseContext = app.applicationContext        // به Activity در حال بسته شدن وصل نماند (نشت حافظه)
        entries.remove(key)?.let { destroy(it) }
        entries[key] = Entry(web, h.first, h.second, now)
        while (entries.size > MAX) { val old = entries.keys.first(); entries.remove(old)?.let { destroy(it) } }
        // با گذشت زمان مجاز، خودش آزاد شود
        handler.postDelayed({ expire(System.currentTimeMillis()) }, IDLE_MS + 1000)
    }

    /** همه را آزاد می‌کند (مثلاً کمبود حافظه). */
    fun clear() { entries.values.toList().forEach { destroy(it) }; entries.clear() }

    private fun expire(now: Long) {
        val it = entries.entries.iterator()
        while (it.hasNext()) { val e = it.next().value; if (now - e.lastUsed > IDLE_MS) { destroy(e); it.remove() } }
    }

    private fun destroy(e: Entry) { runCatching { e.web.stopLoading(); e.web.destroy() }; holders.remove(e.web) }
}
