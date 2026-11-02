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
 * خالی = دریافت خودکار ندارد / تبلیغ عمومی.
 */
class TilesPack(val sites: List<SiteTile>, val sections: Set<String>, val mealsFeed: String = "", val adsFeed: String = "", val publicAds: Boolean = false, val noticeFeed: String = "", val publicNotices: Boolean = true, val configFeed: String = "")

/**
 * بستهٔ کاشی‌ها با همان پسوند `.shiftma` (type = "tiles"). پیش‌فرض‌ها از `assets/default_tiles.shiftma` می‌آیند (system/DefaultTiles.kt)؛
 * فایل‌های دیگر را هم کاربر با زدن روی آن (یا «وارد کردن فایل کاشی‌ها» در تنظیمات) اضافه می‌کند.
 * نام کاربری و رمز هرگز داخل فایل نمی‌آید.
 */
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

    fun toJsonObject(sites: List<SiteTile>, sections: Set<String>, mealsFeed: String = "", adsFeed: String = "", publicAds: Boolean = false, noticeFeed: String = "", publicNotices: Boolean = true, configFeed: String = ""): JSONObject = JSONObject().apply {
        put("app", "shiftma"); put("type", TYPE); put("version", VERSION)
        put("sites", sitesToJson(sites))
        put("sections", JSONArray().also { a -> sections.sorted().forEach { a.put(it) } })
        if (mealsFeed.isNotEmpty()) put("mealsFeed", mealsFeed)
        if (adsFeed.isNotEmpty()) put("adsFeed", adsFeed)
        if (adsFeed.isNotEmpty() && publicAds) put("publicAds", true)
        if (noticeFeed.isNotEmpty()) { put("noticeFeed", noticeFeed); if (!publicNotices) put("publicNotices", false) }
        if (configFeed.isNotEmpty()) put("configFeed", configFeed)
    }

    fun toJson(sites: List<SiteTile>, sections: Set<String>, mealsFeed: String = "", adsFeed: String = "", publicAds: Boolean = false, noticeFeed: String = "", publicNotices: Boolean = true, configFeed: String = ""): String =
        toJsonObject(sites, sections, mealsFeed, adsFeed, publicAds, noticeFeed, publicNotices, configFeed).toString()

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
        return TilesPack(sites, sections, feed, ads, ads.isNotEmpty() && o.optBoolean("publicAds", false), notice, o.optBoolean("publicNotices", true), config)
    }
}
