package ir.shiftapp.core

import org.json.JSONArray
import org.json.JSONObject

/** یکی از دو نوار افقی صفحهٔ اول؛ [iconPrefix] پیشوند کلیدِ آیکنِ ذخیره‌شدهٔ سایت‌هاست (SiteIcon). */
enum class Strip(val listKey: String, val iconPrefix: String) {
    ESSENTIAL(PrefKeys.ESS_LIST, "ess_"),
    TV(PrefKeys.TV_LIST, "tv_")
}

/** یک سایت در نوار. [key] با ویرایش عوض نمی‌شود تا آیکنِ ذخیره‌شده‌اش گم نشود. */
class StripSite(val key: String, val title: String, val url: String)

/**
 * فهرستِ قابل ویرایش سایت‌های نوارهای «سایت‌های ضروری» و «تلویزیون» (افزودن، ویرایش، جابه‌جایی، حذف). منطق خالص و قابل تست.
 * تا کاربر چیزی را عوض نکرده، فهرست ذخیره نمی‌شود و پیش‌فرض‌های برنامه نشان داده می‌شود (پس اصلاح آدرس پیش‌فرض‌ها به همه می‌رسد).
 * با اولین تغییر، کل فهرست (به همین ترتیب) در تنظیمات ذخیره می‌شود؛ فهرست خالیِ عمدی به‌شکل «[]» ذخیره می‌شود.
 */
object StripSites {
    const val MAX = 60
    private val KEY_RE = Regex("[a-z0-9_]{1,30}")

    /** تلویزیون: آدرس تلوبیون `telewebion.net` است (دامنهٔ قدیمی `.com` دیگر آدرس اصلی نیست). */
    val TV_DEFAULTS: List<StripSite> = listOf(
        StripSite("telewebion", "تلوبیون", "https://telewebion.net"),
        StripSite("aparat", "آپارات", "https://www.aparat.com"),
        StripSite("namava", "نماوا", "https://www.namava.ir"),
        StripSite("filimo", "فیلیمو", "https://www.filimo.com")
    )

    /** شناسهٔ پیش‌فرض از روی میزبان کامل (ihio.gov.ir و tax.gov.ir شناسهٔ یکسان نگیرند). */
    fun defaultKey(url: String): String =
        TilesPackFile.hostOf(url).removePrefix("www.").replace(Regex("[^a-z0-9]"), "_").take(30).ifEmpty { "site" }

    val ESSENTIAL_DEFAULTS: List<StripSite> =
        SiteCatalog.ALL.map { StripSite(defaultKey(it.url), it.title, it.url) }

    fun defaultsOf(strip: Strip): List<StripSite> = if (strip == Strip.TV) TV_DEFAULTS else ESSENTIAL_DEFAULTS

    // ───────── ذخیره و خواندن ─────────

    fun encode(list: List<StripSite>): String = JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("key", it.key).put("title", it.title).put("url", it.url)) }
    }.toString()

    private fun valid(s: StripSite) =
        KEY_RE.matches(s.key) && s.title.isNotEmpty() && s.title.length <= TilesPackFile.MAX_TITLE &&
            s.url.startsWith("https://") && s.url.length <= TilesPackFile.MAX_URL && TilesPackFile.hostOf(s.url).isNotEmpty()

    /** null = چیزی ذخیره نشده یا ذخیره‌شده خراب است (آن‌وقت پیش‌فرض‌ها نشان داده می‌شوند). */
    fun decode(raw: String): List<StripSite>? {
        if (raw.isBlank()) return null
        try {
            val a = JSONArray(raw)
            if (a.length() > MAX) return null
            val out = ArrayList<StripSite>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val s = StripSite(o.getString("key"), o.getString("title").trim(), o.getString("url").trim())
                if (!valid(s) || out.any { it.key == s.key }) return null
                out.add(s)
            }
            return out
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * فهرست دیدنیِ یک نوار. [legacyCustomRaw] سایت‌هایی است که کاربر در نسخه‌های قبل به «سایت‌های ضروری» افزوده بود
     * (کلید `pref_ess_sites`)؛ تا اولین تغییر، پشت پیش‌فرض‌ها می‌آیند.
     */
    fun resolve(strip: Strip, raw: String, legacyCustomRaw: String = ""): List<StripSite> {
        decode(raw)?.let { return it }
        val base = defaultsOf(strip)
        if (strip != Strip.ESSENTIAL) return base
        val out = ArrayList(base)
        TilesPackFile.decodeSites(legacyCustomRaw).forEach { c ->
            val s = StripSite(c.key, c.title, c.url)
            if (valid(s) && out.none { it.key == s.key || it.url.equals(s.url, ignoreCase = true) }) out.add(s)
        }
        return out
    }

    // ───────── بررسی ورودی ─────────

    fun normalizeUrl(url: String): String {
        val u = url.trim()
        return if (u.isNotEmpty() && !u.contains("://")) "https://$u" else u
    }

    /** متن خطای فارسی، یا null اگر عنوان و آدرس درست است. */
    fun problem(title: String, url: String): String? {
        val t = title.trim()
        val u = normalizeUrl(url)
        if (t.isEmpty()) return "عنوان را بنویس"
        if (t.length > TilesPackFile.MAX_TITLE) return "عنوان حداکثر ${Fa.num(TilesPackFile.MAX_TITLE)} حرف باشد"
        if (u.startsWith("http://")) return "فقط آدرس امن (https) پذیرفته می‌شود"
        if (!u.startsWith("https://") || TilesPackFile.hostOf(u).isEmpty() || u.length > TilesPackFile.MAX_URL) return "آدرس درست نیست (مثل example.com)"
        return null
    }

    // ───────── تغییرها ─────────

    /** نتیجهٔ یک تغییر: یا فهرست تازه، یا متن خطا. */
    class Change(val list: List<StripSite>?, val error: String?)

    fun add(list: List<StripSite>, title: String, url: String): Change {
        problem(title, url)?.let { return Change(null, it) }
        val u = normalizeUrl(url)
        if (list.any { it.url.equals(u, ignoreCase = true) }) return Change(null, "این آدرس قبلاً اضافه شده")
        if (list.size >= MAX) return Change(null, "حداکثر ${Fa.num(MAX)} سایت می‌شود؛ یکی را حذف کن")
        var key = TilesPackFile.keyFor(u).ifEmpty { "site" }
        while (list.any { it.key == key }) key = (key + "x").takeLast(30)
        return Change(list + StripSite(key, title.trim(), u), null)
    }

    fun update(list: List<StripSite>, key: String, title: String, url: String): Change {
        if (list.none { it.key == key }) return Change(null, "این سایت دیگر در فهرست نیست")
        problem(title, url)?.let { return Change(null, it) }
        val u = normalizeUrl(url)
        if (list.any { it.key != key && it.url.equals(u, ignoreCase = true) }) return Change(null, "این آدرس قبلاً اضافه شده")
        return Change(list.map { if (it.key == key) StripSite(key, title.trim(), u) else it }, null)
    }

    fun remove(list: List<StripSite>, key: String): List<StripSite> = list.filter { it.key != key }

    /** [key] را [delta] خانه جابه‌جا می‌کند (جای دو سایت عوض می‌شود)؛ خارج از فهرست = بدون تغییر. */
    fun move(list: List<StripSite>, key: String, delta: Int): List<StripSite> {
        val keys = HomeLayout.move(list.map { it.key }, key, delta)
        val byKey = list.associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }
}
