package ir.shiftapp.core

import org.json.JSONObject

/**
 * یک قاعدهٔ منطقه از فایل عمومی `regions.json`: [hosts] = میزبان سامانه‌هایی که کاربران این منطقه دارند،
 * و آدرس‌های خوراک همان منطقه. [configFeed] الزامی است (هدف همین است)؛ بقیه اختیاری و خالی = ندارد.
 */
class RegionRule(
    val id: String, val hosts: Set<String>, val configFeed: String,
    val adsFeed: String = "", val publicAds: Boolean = false,
    val noticeFeed: String = "", val publicNotices: Boolean = true
)

/**
 * وصل‌کردن خودکار یک گوشی به منطقه‌اش، وقتی فایل کاشی‌هایی که دست‌به‌دست شده `configFeed` نداشته است.
 * قاعده‌ها از فایل عمومی می‌آیند (پیش‌فرض خاموش: `enabled` باید true باشد). تطبیق فقط روی خود گوشی انجام می‌شود:
 *  ۱) اگر گوشی برچسب منطقه دارد (کلید `region` فایلی که وارد شده) و قاعده‌ای با همان شناسه هست، همان؛
 *  ۲) وگرنه میزبان کاشی‌های گوشی با میزبان‌های قاعده‌ها مقایسه می‌شود؛ فقط اگر دقیقاً یک قاعده جور شود؛ چندمعنایی = هیچ.
 * فقط آدرس‌های https گیت‌هاب ([MealFeed.isSafeUrl]) پذیرفته می‌شوند. جزئیات: docs/REGION_ASSIGN.md
 */
object RegionAssign {
    const val MAX_JSON = 60_000
    const val MAX_RULES = 40
    const val MAX_HOSTS = 40
    private val ID_RE = Regex("[a-z0-9_]{1,30}")
    private val HOST_RE = Regex("[a-z0-9]([a-z0-9.-]{0,98}[a-z0-9])?")

    fun isId(s: String): Boolean = ID_RE.matches(s)

    private fun safe(u: String): String = u.trim().takeIf { MealFeed.isSafeUrl(it) }.orEmpty()

    /** فایل خراب = null (برنامه همان قاعده‌های قبلی را نگه می‌دارد)؛ فایل خاموش یا بدون قاعدهٔ معتبر = فهرست خالی. */
    fun parse(json: String): List<RegionRule>? = try {
        if (json.length > MAX_JSON) null else {
            val root = JSONObject(json)
            if (!root.optBoolean("enabled", false)) emptyList() else {
                val arr = root.optJSONArray("regions")
                val out = ArrayList<RegionRule>()
                if (arr != null) for (i in 0 until minOf(arr.length(), MAX_RULES)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim().lowercase()
                    val config = safe(o.optString("configFeed"))
                    if (!isId(id) || config.isEmpty() || out.any { it.id == id }) continue
                    val hosts = LinkedHashSet<String>()
                    o.optJSONArray("hosts")?.let { h ->
                        for (k in 0 until minOf(h.length(), MAX_HOSTS)) {
                            val v = h.optString(k).trim().lowercase()
                            if (v.contains('.') && HOST_RE.matches(v)) hosts.add(v)
                        }
                    }
                    val ads = safe(o.optString("adsFeed"))
                    val notice = safe(o.optString("noticeFeed"))
                    out.add(RegionRule(id, hosts, config, ads, ads.isNotEmpty() && o.optBoolean("publicAds", false), notice, o.optBoolean("publicNotices", true)))
                }
                out
            }
        }
    } catch (e: Exception) { null }

    /** منطقهٔ این گوشی یا null (نامشخص یا چندمعنایی). [siteHosts] = میزبان‌های کاشی‌های گوشی (حروف کوچک). */
    fun pick(rules: List<RegionRule>, siteHosts: Set<String>, tag: String): RegionRule? {
        if (rules.isEmpty()) return null
        val t = tag.trim().lowercase()
        if (t.isNotEmpty()) rules.firstOrNull { it.id == t }?.let { return it }
        if (siteHosts.isEmpty()) return null
        val hit = rules.filter { r -> r.hosts.any { it in siteHosts } }
        return hit.singleOrNull()
    }

    /** برچسب منطقهٔ فایل (کلید `region`)؛ نامعتبر = خالی. */
    fun cleanTag(raw: String): String = raw.trim().lowercase().takeIf { isId(it) }.orEmpty()
}
