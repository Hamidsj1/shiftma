package ir.shiftapp.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.work.*
import ir.shiftapp.core.NoticeFeed
import ir.shiftapp.core.NoticeItem
import ir.shiftapp.ui.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * پنل نوتیفیکیشن اختصاصی (بدون SDK، بدون گوگل‌پلی‌سرویس): فایل JSON را هر چند ساعت می‌گیرد
 * و برای هر اعلانِ تازه یک اعلان گوشی نشان می‌دهد. دو مسیر دارد:
 *  - عمومی: [FEED_URL]، برای همهٔ کاربران؛
 *  - محلی: خوراک مخصوص محل کار کاربر که از فایل کاشی‌ها (`noticeFeed`) می‌آید ([setLocal]).
 * جزئیات: docs/NOTICE_PANEL.md
 */
object NoticeLoader {
    /** نشانی فایل JSON اعلان‌ها (فقط https). خالی = پنل خاموش است و هیچ درخواستی فرستاده نمی‌شود. */
    const val FEED_URL = "https://raw.githubusercontent.com/Hamidsj1/shiftma-ads/main/notice.json"

    private const val WORK = "notice_panel"
    private const val CHANNEL = "notice_panel"
    private const val FLAGS = "shiftma_flags"
    private const val KEY_SEEN = "notice_seen"
    private const val KEY_LOCAL = "notice_local_url"
    private const val KEY_LOCAL_PUBLIC = "notice_local_public"
    private const val MAX_JSON = 50_000
    private const val EVERY_HOURS = 3L

    val enabled: Boolean get() = NoticeFeed.isSafeUrl(FEED_URL)

    /** خوراک محلیِ ذخیره‌شده از فایل کاشی‌ها (خالی = ندارد). */
    fun localUrl(ctx: Context): String =
        ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE).getString(KEY_LOCAL, "").orEmpty()

    /** خوراک‌های این کاربر: (آدرس، محلی؟). */
    private fun feeds(ctx: Context): List<Pair<String, Boolean>> {
        val sp = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)
        return NoticeFeed.feeds(FEED_URL, sp.getString(KEY_LOCAL, "").orEmpty(), sp.getBoolean(KEY_LOCAL_PUBLIC, true))
    }

    /** پنل کار می‌کند اگر دست‌کم یک خوراک (محلی یا عمومی) دارد. */
    fun active(ctx: Context): Boolean = feeds(ctx).isNotEmpty()

    /** خوراک محلی محل کار را ذخیره می‌کند (از فایل کاشی‌ها)؛ آدرس نامعتبر یعنی پاک شود. اگر چیزی عوض شد، همان لحظه یک چک انجام می‌شود. */
    fun setLocal(ctx: Context, url: String, publicToo: Boolean) {
        val clean = if (ir.shiftapp.core.MealFeed.isSafeUrl(url)) url else ""
        val sp = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)
        val changed = sp.getString(KEY_LOCAL, "").orEmpty() != clean || sp.getBoolean(KEY_LOCAL_PUBLIC, true) != publicToo
        if (!changed) return
        sp.edit().putString(KEY_LOCAL, clean).putBoolean(KEY_LOCAL_PUBLIC, publicToo).apply()
        apply(ctx); checkNow(ctx)
    }

    /** زمان‌بندی دوره‌ای را (همیشه فعال؛ بدون کلید خاموش/روشن) برقرار یا اگر خوراکی نیست لغو می‌کند (با هر بار باز شدن برنامه صدا زده می‌شود). */
    fun apply(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (active(ctx)) {
            val req = PeriodicWorkRequestBuilder<NoticeWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else wm.cancelUniqueWork(WORK)
    }

    /** یک بار همین حالا چک کن (مثلاً هنگام باز شدن برنامه). */
    fun checkNow(ctx: Context) {
        if (!active(ctx)) return
        WorkManager.getInstance(ctx.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<NoticeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }

    /** @return false اگر دریافت دست‌کم یکی از خوراک‌ها شکست خورد (برای تلاش دوباره). */
    internal fun run(ctx: Context): Boolean {
        val sources = feeds(ctx)
        if (sources.isEmpty()) return true
        val sp = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)
        var seen = sp.getString(KEY_SEEN, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        // بدون مجوز اعلان، اعلان را «دیده‌شده» نکن تا بعد از دادن مجوز برسد.
        val canPost = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        var allOk = true
        for ((url, isLocal) in sources) {
            val json = try { String(download(url), Charsets.UTF_8) } catch (e: Exception) { allOk = false; continue }
            // اعلان محلی با پیشوند «L:» ذخیره می‌شود تا شناسهٔ یکسان در دو خوراک با هم قاطی نشود.
            val items = NoticeFeed.parse(json).map { if (isLocal) it.copy(id = NoticeFeed.seenKey(it.id, true)) else it }
            val fresh = NoticeFeed.fresh(items, seen.toSet(), System.currentTimeMillis())
            if (fresh.isEmpty() || !canPost) continue
            fresh.forEach { show(ctx, it) }
            seen = NoticeFeed.mergeSeen(seen, fresh.map { it.id })
            sp.edit().putString(KEY_SEEN, seen.joinToString("\n")).apply()
        }
        return allOk
    }

    private fun show(ctx: Context, n: NoticeItem) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "اخبار و اطلاعیه‌ها", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = if (n.link.isNotEmpty()) Intent(Intent.ACTION_VIEW, Uri.parse(n.link)) else Intent(ctx, MainActivity::class.java)
        val code = 8000 + (n.id.hashCode() and 0xFFF)
        val pi = PendingIntent.getActivity(ctx, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title).setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setContentIntent(pi).setAutoCancel(true)
        nm.notify(code, b.build())
    }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ShiftMa (notice panel)")
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            if (c.url.protocol != "https") throw IllegalStateException("not https")
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            c.inputStream.use { i ->
                while (true) {
                    val r = i.read(buf)
                    if (r < 0) break
                    total += r
                    if (total > MAX_JSON) throw IllegalStateException("too large")
                    out.write(buf, 0, r)
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }
}

class NoticeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = if (NoticeLoader.run(applicationContext)) Result.success() else Result.retry()
}
