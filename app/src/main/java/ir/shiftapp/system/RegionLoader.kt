package ir.shiftapp.system

import android.content.Context
import ir.shiftapp.core.Feeds
import ir.shiftapp.core.PrefKeys
import ir.shiftapp.core.RegionAssign
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.core.feeds
import ir.shiftapp.data.AppDb
import ir.shiftapp.data.Setting
import ir.shiftapp.data.prefsOnce
import ir.shiftapp.phonebook.PhoneDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * وصل‌کردن خودکار و بی‌صدای گوشی به منطقه‌اش (`configFeed` و در صورت نیاز آدرس بنر/اعلان)، وقتی فایل کاشی‌های دست‌به‌دست‌شده
 * آدرس نداشته است. قاعده‌ها از فایل عمومی [FEED_URL] می‌آید؛ تا وقتی `enabled` در آن روشن نشده هیچ اتفاقی نمی‌افتد.
 * فقط وقتی کار می‌کند که `configFeed` گوشی خالی باشد؛ آدرس موجود هرگز عوض نمی‌شود. چیزی از گوشی به جایی فرستاده نمی‌شود
 * (فقط یک درخواست GET ساده برای خود فایل). جزئیات: docs/REGION_ASSIGN.md
 */
object RegionLoader {
    /** فایل عمومی قاعده‌های منطقه (کنار ad.json و notice.json). */
    const val FEED_URL = "https://raw.githubusercontent.com/Hamidsj1/shiftma-ads/main/regions.json"

    private const val FLAGS = "shiftma_flags"
    private const val KEY_TAG = "region_tag"
    private const val KEY_JSON = "region_rules"
    private const val KEY_NEXT = "region_next_fetch"
    private const val OK_MS = 6L * 60 * 60 * 1000
    private const val RETRY_MS = 30L * 60 * 1000
    private val lock = Mutex()

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)

    /** برچسب منطقهٔ آخرین فایلی که وارد شده (کلید `region`)؛ خالی = ندارد. */
    fun regionTag(ctx: Context): String = RegionAssign.cleanTag(sp(ctx).getString(KEY_TAG, "").orEmpty())

    /** برچسب نامعتبر یا خالی نادیده گرفته می‌شود (برچسب قبلی می‌ماند). */
    fun setRegionTag(ctx: Context, raw: String) {
        val t = RegionAssign.cleanTag(raw)
        if (t.isNotEmpty()) sp(ctx).edit().putString(KEY_TAG, t).apply()
    }

    /** آدرس‌ها و برچسب منطقهٔ این گوشی برای گذاشتن در فایل‌های ارسالی؛ null اگر خواندن ممکن نشد. */
    suspend fun currentFeeds(ctx: Context): Feeds? = withContext(Dispatchers.IO) {
        runCatching { AppDb.get(ctx).dao().prefsOnce().feeds().copy(region = regionTag(ctx)) }.getOrNull()
    }

    /** اگر `configFeed` ندارد و منطقه‌اش مشخص است، آدرس‌ها را در تنظیمات می‌گذارد. با هر باز شدن و هر تغییر کاشی‌ها امن است. */
    suspend fun check(ctx: Context) = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDb.get(ctx).dao()
                val p = dao.prefsOnce()
                if (p.configFeed.isNotEmpty()) return@withContext
                val sp = sp(ctx)
                val now = System.currentTimeMillis()
                var raw = sp.getString(KEY_JSON, "").orEmpty()
                if (raw.isEmpty() || now >= sp.getLong(KEY_NEXT, 0L)) {
                    val fresh = runCatching { download() }.getOrNull()
                    if (fresh != null && RegionAssign.parse(fresh) != null) {
                        raw = fresh
                        sp.edit().putString(KEY_JSON, fresh).putLong(KEY_NEXT, now + OK_MS).apply()
                    } else sp.edit().putLong(KEY_NEXT, now + RETRY_MS).apply()      // خطا یا فایل خراب: قاعده‌های قبلی می‌مانند
                }
                val rules = RegionAssign.parse(raw) ?: return@withContext
                val sites = TilesPackFile.decodeSites(p.sitesRaw)
                val hosts = sites.map { TilesPackFile.hostOf(it.url) }.filter { it.isNotEmpty() }.toSet()
                val sigs = sites.mapNotNull { RegionAssign.sig(it.url) }.toSet()
                // شاخص کمکی: شماره‌های دفترچهٔ برنامه (جدا از مخاطبین گوشی)؛ فقط اگر قاعده‌ای شمارهٔ یکتا داشته باشد خوانده می‌شود
                val phones = if (rules.any { it.phones.isNotEmpty() })
                    runCatching {
                        val pdb = PhoneDb(ctx)
                        try { RegionAssign.phoneSigs(pdb.search("").flatMap { listOf(it.mobile, it.landline) }) } finally { pdb.close() }
                    }.getOrDefault(emptySet())
                else emptySet()
                val rule = RegionAssign.pick(rules, hosts, sigs, regionTag(ctx), phones) ?: return@withContext
                dao.put(Setting(PrefKeys.CONFIG_FEED, rule.configFeed))
                if (p.adFeed.isEmpty() && rule.adsFeed.isNotEmpty()) {
                    dao.put(Setting(PrefKeys.AD_FEED, rule.adsFeed)); dao.put(Setting(PrefKeys.AD_PUBLIC, rule.publicAds.toString()))
                }
                if (p.noticeFeed.isEmpty() && rule.noticeFeed.isNotEmpty()) {
                    dao.put(Setting(PrefKeys.NOTICE_FEED, rule.noticeFeed)); dao.put(Setting(PrefKeys.NOTICE_PUBLIC, rule.publicNotices.toString()))
                }
                DiagLog.i("REGION", "assigned ${rule.id}")
            } catch (e: Exception) {
                DiagLog.w("REGION", "check failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun download(): String {
        val c = URL(FEED_URL).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ShiftMa (region)")
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
                    if (total > RegionAssign.MAX_JSON) throw IllegalStateException("too large")
                    out.write(buf, 0, r)
                }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } finally { c.disconnect() }
    }
}
