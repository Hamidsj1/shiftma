package ir.shiftapp.system

import android.content.Context
import androidx.work.*
import ir.shiftapp.core.MealFeed
import ir.shiftapp.core.RemoteConfig
import ir.shiftapp.core.RemoteConfigFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * پنل تنظیمات بخش‌ها (نمایش/پنهان/پولی): فایل config.json محل کاربر را هر چند ساعت از گیت‌هاب می‌گیرد.
 * آدرس فقط از «فایل کاشی‌ها» (کلید `configFeed`) می‌آید؛ بدون آن هیچ درخواستی فرستاده نمی‌شود.
 * مثل [NoticeLoader] بدون SDK و بدون سرویس گوگل است. آخرین تنظیم سالم ذخیره می‌شود و آفلاین هم کار می‌کند.
 */
object ConfigLoader {
    private const val WORK = "config_panel"
    private const val FLAGS = "shiftma_flags"
    private const val KEY_URL = "config_url"
    private const val KEY_JSON = "config_json"
    private const val EVERY_HOURS = 3L

    private val _config = MutableStateFlow(RemoteConfig())

    /** تنظیم فعلی؛ صفحهٔ اول و MainActivity این را می‌خوانند و با رسیدن فایل تازه خودکار به‌روز می‌شوند. */
    val config: StateFlow<RemoteConfig> get() = _config

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)

    /** هنگام باز شدن برنامه صدا بزنید: آخرین تنظیم ذخیره‌شده را بدون اینترنت بالا می‌آورد. */
    fun init(ctx: Context) {
        _config.value = RemoteConfigFile.parse(sp(ctx).getString(KEY_JSON, "").orEmpty().ifEmpty { "{}" })
    }

    fun feedUrl(ctx: Context): String = sp(ctx).getString(KEY_URL, "").orEmpty()

    /** آدرس خوراک این محل (از فایل کاشی‌ها)؛ نامعتبر یا خالی = پاک شود و همه چیز نمایان و رایگان برگردد. */
    fun setFeed(ctx: Context, url: String) {
        val clean = if (MealFeed.isSafeUrl(url)) url else ""
        if (feedUrl(ctx) == clean) { apply(ctx); return }
        sp(ctx).edit().putString(KEY_URL, clean).remove(KEY_JSON).apply()
        _config.value = RemoteConfig()
        apply(ctx)
        checkNow(ctx)
    }

    fun apply(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (feedUrl(ctx).isNotEmpty()) {
            val req = PeriodicWorkRequestBuilder<ConfigWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else wm.cancelUniqueWork(WORK)
    }

    fun checkNow(ctx: Context) {
        if (feedUrl(ctx).isEmpty()) return
        WorkManager.getInstance(ctx.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<ConfigWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }

    /** @return false اگر دریافت یا خواندن فایل شکست خورد (برای تلاش دوباره)؛ در این حالت تنظیم قبلی می‌ماند. */
    internal fun run(ctx: Context): Boolean {
        val url = feedUrl(ctx)
        if (url.isEmpty()) return true
        val json = try { String(download(url), Charsets.UTF_8) } catch (e: Exception) {
            UpdateLog.recordCheck(ctx, false, "دریافت فایل ممکن نشد (اینترنت یا آدرس): ${e.message.orEmpty().take(60)}"); _tick.value++; return false
        }
        val cfg = RemoteConfigFile.parseOrNull(json) ?: run {
            UpdateLog.recordCheck(ctx, false, "فایل تنظیمات معتبر نیست"); _tick.value++; return false   // فایل خراب: بخش‌ها را ناگهان باز/بسته نکن
        }
        sp(ctx).edit().putString(KEY_JSON, json).apply()
        _config.value = cfg
        UpdateLog.recordCheck(ctx, true)
        _tick.value++
        return true
    }

    private val _tick = MutableStateFlow(0)
    /** با هر بررسی (موفق یا ناموفق) عوض می‌شود تا صفحهٔ تنظیمات تازه شود. */
    val tick: StateFlow<Int> get() = _tick

    /** بررسی دستیِ همین لحظه (دکمهٔ تنظیمات). */
    suspend fun refreshNow(ctx: Context): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { run(ctx) }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ShiftMa (config panel)")
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
                    if (total > RemoteConfigFile.MAX_JSON) throw IllegalStateException("too large")
                    out.write(buf, 0, r)
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }
}

class ConfigWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = if (ConfigLoader.run(applicationContext)) Result.success() else Result.retry()
}
