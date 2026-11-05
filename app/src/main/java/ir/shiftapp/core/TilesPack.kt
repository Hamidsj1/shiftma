package ir.shiftapp.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Base64

/** یک کاشی سامانه: کلید (برای نام کاربری و رمز ذخیره‌شده)، عنوان، توضیح، آدرس https، دکمهٔ PDF و آرم اختیاری (تصویر کوچک). */
/** [edited] = کاربر خودش ویرایش کرده؛ به‌روزرسانی راه‌دور (config.json) دیگر به آن دست نمی‌زند. فقط در ذخیرهٔ خود برنامه می‌آید، نه در فایل ارسالی. */
class SiteTile(val key: String, val title: String, val subtitle: String, val url: String, val pdf: Boolean, val logo: ByteArray?, val edited: Boolean = false)

/**
 * محتوای یک فایل کاشی‌ها: سامانه‌ها + شناسهٔ بخش‌های مخفی که با این فایل نمایان می‌شوند (phonebook، meals)
 * + [mealsFeed] اختیاری: آدرس خوراک برنامه غذایی شرکت (MealFeed) و [adsFeed] اختیاری: آدرس خوراک تبلیغ همان محل (AdFeed.feeds) و [publicAds]: کاربر این محل تبلیغ عمومی را هم ببیند (پیش‌فرض نه).
 * [noticeFeed] اختیاری: خوراک اعلان‌های «محلی» همین محل (NoticeFeed، گیت‌هاب) و [publicNotices]: کنارش اعلان‌های عمومی برنامه هم برسد (پیش‌فرض بله).
 * [configFeed] اختیاری: آدرس config.json همین محل (RemoteConfigFile، گیت‌هاب) برای نمایش/پنهان/پولی کردن بخش‌ها.
 * [region] اختیاری: شناسهٔ محل فرستنده (مثل sungun)؛ فایل‌های بدون آدرس هم با آن به منطقه‌شان وصل می‌شوند (RegionAssign).
 * خالی = دریافت خودکار ندارد / تبلیغ عمومی.
 */
class TilesPack(val sites: List<SiteTile>, val sections: Set<String>, val mealsFeed: String = "", val adsFeed: String = "", val publicAds: Boolean = false, val noticeFeed: String = "", val publicNotices: Boolean = true, val configFeed: String = "", val region: String = "")

/**
 * بستهٔ کاشی‌ها با همان پسوند `.shiftma` (type = "tiles"). پیش‌فرض‌ها از `assets/default_tiles.shiftma` می‌آیند (system/DefaultTiles.kt)؛
 * فایل‌های دیگر را هم کاربر با زدن روی آن (یا «وارد کردن فایل کاشی‌ها» در تنظیمات) اضافه می‌کند.
 * نام کاربری و رمز هرگز داخل فایل نمی‌آید.
 */
/**
 * آدرس‌های خوراک «منطقه» (برنامه غذایی، بنر/تبلیغ، اعلان، تنظیمات راه‌دور). این‌ها باید همراه هر فایل ارسالی بروند
 * تا گیرنده‌ای که فایل را دست‌به‌دست گرفته هم به همان منطقه وصل شود. خالی = آن خوراک را ندارد.
 */
data class Feeds(
    val mealsFeed: String = "", val adsFeed: String = "", val publicAds: Boolean = false,
    val noticeFeed: String = "", val publicNotices: Boolean = true, val configFeed: String = "",
    /** برچسب منطقه (شناسهٔ محل)؛ همراه آدرس‌ها می‌رود و تنها نمی‌تواند فایل را معتبر کند. */
    val region: String = ""
) {
    val isEmpty: Boolean get() = mealsFeed.isEmpty() && adsFeed.isEmpty() && noticeFeed.isEmpty() && configFeed.isEmpty()
    companion object { val NONE = Feeds() }
}

/** خوراک‌های ذخیره‌شدهٔ این گوشی؛ آدرس ناامن یا خراب نادیده گرفته می‌شود. */
fun Prefs.feeds(): Feeds = Feeds(
    mealFeed.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(),
    adFeed.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(), adPublic,
    noticeFeed.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(), noticePublic,
    configFeed.takeIf { MealFeed.isSafeUrl(it) }.orEmpty()
)

object TilesPackFile {
    const val TYPE = "tiles"
    const val VERSION = 1
    const val MAX_CHARS = 3_000_000
    const val MAX_SITES = 30
    private const val MAX_LOGO = 90_000
    const val MAX_TITLE = 40
    const val MAX_SUB = 60
    const val MAX_URL = 600
    val SECTIONS = setOf("phonebook", "meals")

    private val KEY_RE = Regex("[a-z0-9_]{1,30}")

    fun hostOf(url: String): String = try { URI(url).host?.lowercase().orEmpty() } catch (e: Exception) { "" }

    /** شناسهٔ سامانه از روی آدرس (مثلاً my.nicico.com → nicicocom)؛ فایل می‌تواند شناسهٔ دلخواه بدهد. */
    fun keyFor(url: String): String =
        PortalAutofill.siteOf(hostOf(url)).replace(Regex("[^a-z0-9]"), "").take(30)

    private fun parseSites(a: JSONArray?, trustEdited: Boolean = false): List<SiteTile> {
        if (a == null) return emptyList()
        require(a.length() <= MAX_SITES) { "تعداد سامانه‌های فایل نامعتبر است" }
        val out = ArrayList<SiteTile>()
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            val title = j.getString("title").trim()
            val sub = j.optString("subtitle", "").trim()
            val url = j.getString("url").trim()
            require(title.isNotEmpty() && title.length <= MAX_TITLE) { "عنوان سامانه نامعتبر است" }
            require(sub.length <= MAX_SUB) { "توضیح سامانه بیش از حد طولانی است" }
            require(url.length <= MAX_URL && url.startsWith("https://") && hostOf(url).isNotEmpty()) { "آدرس سامانه باید با https شروع شود" }
            val key = j.optString("key", "").trim().lowercase().ifEmpty { keyFor(url) }
            require(KEY_RE.matches(key)) { "شناسهٔ سامانه نامعتبر است" }
            require(out.none { it.key == key }) { "سامانهٔ تکراری در فایل" }
            val logoText = j.optString("logo", "")
            val logo = if (logoText.isEmpty()) null else try {
                Base64.getDecoder().decode(logoText)
            } catch (e: IllegalArgumentException) { throw IllegalArgumentException("آرم سامانه خراب است") }
            require(logo == null || logo.size <= MAX_LOGO) { "آرم سامانه بیش از حد بزرگ است" }
            out.add(SiteTile(key, title, sub, url, j.optBoolean("pdf", false), logo, trustEdited && j.optBoolean("edited", false)))
        }
        return out
    }

    private fun sitesToJson(sites: List<SiteTile>, store: Boolean = false): JSONArray = JSONArray().also { a ->
        sites.forEach { s ->
            a.put(JSONObject().put("key", s.key).put("title", s.title).put("subtitle", s.subtitle).put("url", s.url).put("pdf", s.pdf)
                .also { o -> if (store && s.edited) o.put("edited", true) }
                .also { o -> s.logo?.let { o.put("logo", Base64.getEncoder().encodeToString(it)) } })
        }
    }

    /** برای ذخیره در تنظیمات برنامه. */
    fun encodeSites(sites: List<SiteTile>): String = if (sites.isEmpty()) "" else sitesToJson(sites, store = true).toString()

    /** خواندن تنظیمات ذخیره‌شده؛ دادهٔ خراب به‌جای خطا، فهرست خالی می‌دهد. */
    fun decodeSites(raw: String): List<SiteTile> =
        if (raw.isBlank()) emptyList() else runCatching { parseSites(JSONArray(raw), trustEdited = true) }.getOrDefault(emptyList())

    /** کلیدهای خوراک را به شیء کاشی‌ها می‌افزاید (فقط خوراک‌های غیرخالی؛ نبودن کلید یعنی «دست نزن»). */
    fun putFeeds(o: JSONObject, f: Feeds): JSONObject = o.apply {
        if (f.mealsFeed.isNotEmpty()) put("mealsFeed", f.mealsFeed)
        if (f.adsFeed.isNotEmpty()) put("adsFeed", f.adsFeed)
        if (f.adsFeed.isNotEmpty() && f.publicAds) put("publicAds", true)
        if (f.noticeFeed.isNotEmpty()) { put("noticeFeed", f.noticeFeed); if (!f.publicNotices) put("publicNotices", false) }
        if (f.configFeed.isNotEmpty()) put("configFeed", f.configFeed)
        if (f.region.isNotEmpty()) put("region", f.region)
    }

    fun toJsonObject(sites: List<SiteTile>, sections: Set<String>, mealsFeed: String = "", adsFeed: String = "", publicAds: Boolean = false, noticeFeed: String = "", publicNotices: Boolean = true, configFeed: String = "", region: String = ""): JSONObject = JSONObject().apply {
        put("app", "shiftma"); put("type", TYPE); put("version", VERSION)
        put("sites", sitesToJson(sites))
        put("sections", JSONArray().also { a -> sections.sorted().forEach { a.put(it) } })
        putFeeds(this, Feeds(mealsFeed, adsFeed, publicAds, noticeFeed, publicNotices, configFeed, region))
    }

    /** همان شیء کاشی‌ها با خوراک‌های [feeds]؛ برای تعبیه داخل فایل مخاطبین/ارسال انتخابی. */
    fun toJsonObject(sites: List<SiteTile>, sections: Set<String>, feeds: Feeds): JSONObject =
        toJsonObject(sites, sections, feeds.mealsFeed, feeds.adsFeed, feeds.publicAds, feeds.noticeFeed, feeds.publicNotices, feeds.configFeed, feeds.region)

    /** [tiles] موجود را نگه می‌دارد و خوراک‌ها را به آن می‌افزاید؛ اگر [tiles] نبود و خوراکی هست، شیء فقط‌خوراک می‌سازد؛ وگرنه null. */
    fun withFeeds(tiles: JSONObject?, feeds: Feeds): JSONObject? = when {
        tiles != null -> putFeeds(tiles, feeds)
        feeds.isEmpty -> null
        else -> toJsonObject(emptyList(), emptySet(), feeds)
    }

    fun toJson(sites: List<SiteTile>, sections: Set<String>, mealsFeed: String = "", adsFeed: String = "", publicAds: Boolean = false, noticeFeed: String = "", publicNotices: Boolean = true, configFeed: String = "", region: String = ""): String =
        toJsonObject(sites, sections, mealsFeed, adsFeed, publicAds, noticeFeed, publicNotices, configFeed, region).toString()

    fun fromJson(text: String): TilesPack = fromObject(JSONObject(text))

    /** همان فایل کاشی‌ها، ولی از یک JSONObject آماده (مثلاً بخش «tiles» داخل فایل مخاطبین). */
    fun fromObject(o: JSONObject): TilesPack {
        require(o.optString("app") == "shiftma" && o.optString("type") == TYPE) { "این فایل، بستهٔ کاشی‌های «شیفت ما» نیست" }
        require(o.optInt("version", 1) <= VERSION) { "نسخهٔ این فایل جدیدتر از برنامه است؛ برنامه را به‌روز کنید" }
        val sites = parseSites(o.optJSONArray("sites"))
        val sections = LinkedHashSet<String>()
        o.optJSONArray("sections")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optString(i)
                require(s in SECTIONS) { "بخش ناشناخته در فایل" }
                sections.add(s)
            }
        }
        val feed = o.optString("mealsFeed", "").trim()
        require(feed.isEmpty() || MealFeed.isSafeUrl(feed)) { "آدرس برنامه غذایی باید https و روی گیت‌هاب باشد" }
        val ads = o.optString("adsFeed", "").trim()
        require(ads.isEmpty() || MealFeed.isSafeUrl(ads)) { "آدرس پنل اطلاع‌رسانی باید https و روی گیت‌هاب باشد" }
        val notice = o.optString("noticeFeed", "").trim()
        require(notice.isEmpty() || MealFeed.isSafeUrl(notice)) { "آدرس اعلان‌ها باید https و روی گیت‌هاب باشد" }
        val config = o.optString("configFeed", "").trim()
        require(config.isEmpty() || MealFeed.isSafeUrl(config)) { "آدرس تنظیمات باید https و روی گیت‌هاب باشد" }
        require(sites.isNotEmpty() || sections.isNotEmpty() || feed.isNotEmpty() || ads.isNotEmpty() || notice.isNotEmpty() || config.isNotEmpty()) { "فایل کاشی‌ها خالی است" }
        return TilesPack(sites, sections, feed, ads, ads.isNotEmpty() && o.optBoolean("publicAds", false), notice, o.optBoolean("publicNotices", true), config, RegionAssign.cleanTag(o.optString("region", "")))
    }
}
