package ir.shiftapp.core

import org.json.JSONObject

/**
 * یک قاعدهٔ منطقه از فایل عمومی `regions.json`: [hosts] = میزبان سامانه‌هایی که کاربران این منطقه دارند،
 * و آدرس‌های خوراک همان منطقه. [configFeed] الزامی است (هدف همین است)؛ بقیه اختیاری و خالی = ندارد.
 */
class RegionRule(
    val id: String, val hosts: Set<String>, val configFeed: String,
    val adsFeed: String = "", val publicAds: Boolean = false,
    val noticeFeed: String = "", val publicNotices: Boolean = true,
    /** اثرانگشت آدرس کامل سامانه‌های این منطقه (نه فقط میزبان)؛ [RegionAssign.sig]. آدرس‌ها در فایل عمومی دیده نمی‌شوند. */
    val sigs: Set<String> = emptySet(),
    /** اثرانگشت (۶ هگز) شماره‌هایی از دفترچه که فقط در همین منطقه هست؛ شاخص کمکی. [RegionAssign.phoneSigs] */
    val phones: Set<String> = emptySet()
)

/**
 * وصل‌کردن خودکار یک گوشی به منطقه‌اش، وقتی فایل کاشی‌هایی که دست‌به‌دست شده `configFeed` نداشته است.
 * قاعده‌ها از فایل عمومی می‌آیند (پیش‌فرض خاموش: `enabled` باید true باشد). تطبیق فقط روی خود گوشی انجام می‌شود:
 *  ۱) اگر گوشی برچسب منطقه دارد (کلید `region` فایلی که وارد شده) و قاعده‌ای با همان شناسه هست، همان؛
 *  ۲) وگرنه کاشی‌های گوشی با قاعده‌ها مقایسه می‌شود: اول **آدرس کامل** سامانه‌ها (اثرانگشت [sig]، چون میزبان‌ها مثل my.nicico.com
 *     بین منطقه‌ها مشترک است ولی مسیرها فرق دارد)، بعد میزبان‌ها. نمره = ۱۰۰×(آدرس‌های مشترک) + (میزبان‌های مشترک)؛
 *     قاعده‌ای می‌برد که نمرهٔ بیشینه و یکتا (>۰) دارد؛ مساوی = چندمعنایی = هیچ.
 *  ۳) شاخص کمکی دفترچهٔ تلفن برنامه: شماره‌های فقط‌متعلق‌به‌یک‌منطقه (ادمین پنل شماره‌های مشترک بین مناطق را حذف می‌کند). فقط وقتی
 *     دست‌کم [PHONE_MIN_HITS] شماره بخورد حساب می‌شود و حداکثر [PHONE_MAX_SCORE] نمره می‌دهد؛ پس هرگز بر یک آدرس کامل سامانه غلبه نمی‌کند.
 *     اثرانگشت‌ها کوتاه‌اند تا شمارهٔ کسی از فایل عمومی برنگردد.
 * فقط آدرس‌های https گیت‌هاب ([MealFeed.isSafeUrl]) پذیرفته می‌شوند. جزئیات: docs/REGION_ASSIGN.md
 */
object RegionAssign {
    const val MAX_JSON = 400_000
    const val MAX_RULES = 40
    const val MAX_HOSTS = 40
    const val MAX_SIGS = 300
    const val MAX_PHONES = 2000
    const val PHONE_MIN_HITS = 3
    const val PHONE_MAX_SCORE = 90
    const val PHONE_POINTS = 10
    private val ID_RE = Regex("[a-z0-9_]{1,30}")
    private val SIG_RE = Regex("[0-9a-f]{12}")
    private val PHONE_RE = Regex("[0-9a-f]{6}")
    private val PHONE_SPLIT = Regex("[,،;/|]+")
    private val HOST_RE = Regex("[a-z0-9]([a-z0-9.-]{0,98}[a-z0-9])?")

    fun isId(s: String): Boolean = ID_RE.matches(s)

    private fun sha(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /**
     * اثرانگشت شماره‌ها (موبایل، ثابت، داخلی): هر ورودی روی `, ، ; / |` شکسته می‌شود؛ ارقام فارسی/عربی به لاتین؛ فقط رقم‌ها؛ اگر ≥۱۰ رقم،
     * ۱۰ رقم آخر (بی‌اثرکردن 0 و 98+)؛ کمتر از ۳ رقم نادیده؛ SHA-256 و ۶ نویسهٔ اول هگز. ادمین پنل همین الگوریتم را دارد (phoneSigs).
     */
    fun phoneSigs(raws: Iterable<String>): Set<String> {
        val out = HashSet<String>()
        for (raw in raws) for (part in raw.split(PHONE_SPLIT)) {
            val d = StringBuilder()
            for (ch in part) when (ch) {
                in '0'..'9' -> d.append(ch)
                in '\u06F0'..'\u06F9' -> d.append('0' + (ch - '\u06F0'))
                in '\u0660'..'\u0669' -> d.append('0' + (ch - '\u0660'))
            }
            var s = d.toString()
            if (s.length >= 10) s = s.takeLast(10)
            if (s.length >= 3) out.add(sha(s).take(6))
        }
        return out
    }

    /**
     * اثرانگشت آدرس سامانه: SHA-256 از «میزبان + مسیر» (حروف کوچک، بدون www.، بدون / آخر، بدون query و fragment)، ۱۲ نویسهٔ اول هگز.
     * ادمین پنل همین الگوریتم را دارد (sigOf)؛ هر تغییری باید در هر دو انجام شود. آدرس نامعتبر = null.
     */
    fun sig(url: String): String? = try {
        val u = java.net.URI(url.trim())
        val scheme = u.scheme?.lowercase()
        val host = u.host?.lowercase()?.removePrefix("www.")
        if ((scheme != "https" && scheme != "http") || host.isNullOrEmpty()) null else {
            sha(host + (u.path ?: "").lowercase().trimEnd('/')).take(12)
        }
    } catch (e: Exception) { null }

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
                    val sigs = LinkedHashSet<String>()
                    o.optJSONArray("sigs")?.let { a ->
                        for (k in 0 until minOf(a.length(), MAX_SIGS)) {
                            val v = a.optString(k).trim().lowercase()
                            if (SIG_RE.matches(v)) sigs.add(v)
                        }
                    }
                    val phones = LinkedHashSet<String>()
                    o.optJSONArray("phones")?.let { a ->
                        for (k in 0 until minOf(a.length(), MAX_PHONES)) {
                            val v = a.optString(k).trim().lowercase()
                            if (PHONE_RE.matches(v)) phones.add(v)
                        }
                    }
                    val ads = safe(o.optString("adsFeed"))
                    val notice = safe(o.optString("noticeFeed"))
                    out.add(RegionRule(id, hosts, config, ads, ads.isNotEmpty() && o.optBoolean("publicAds", false), notice, o.optBoolean("publicNotices", true), sigs, phones))
                }
                out
            }
        }
    } catch (e: Exception) { null }

    /** منطقهٔ این گوشی یا null (نامشخص یا چندمعنایی). [siteHosts] = میزبان‌های کاشی‌های گوشی (حروف کوچک)، [siteSigs] = [sig] آدرس‌هایشان، [phoneSigs] = [phoneSigs] شماره‌های دفترچهٔ برنامه (اختیاری). */
    fun pick(rules: List<RegionRule>, siteHosts: Set<String>, siteSigs: Set<String>, tag: String, phoneSigs: Set<String> = emptySet()): RegionRule? {
        if (rules.isEmpty()) return null
        val t = tag.trim().lowercase()
        if (t.isNotEmpty()) rules.firstOrNull { it.id == t }?.let { return it }
        if (siteHosts.isEmpty() && siteSigs.isEmpty() && phoneSigs.isEmpty()) return null
        val score = rules.associateWith { r ->
            val hits = r.phones.count { it in phoneSigs }
            val phoneScore = if (hits >= PHONE_MIN_HITS) minOf(hits * PHONE_POINTS, PHONE_MAX_SCORE) else 0
            100 * r.sigs.count { it in siteSigs } + phoneScore + r.hosts.count { it in siteHosts }
        }
        val best = score.values.maxOrNull() ?: return null
        if (best == 0) return null
        return score.entries.filter { it.value == best }.singleOrNull()?.key
    }

    /** برچسب منطقهٔ فایل (کلید `region`)؛ نامعتبر = خالی. */
    fun cleanTag(raw: String): String = raw.trim().lowercase().takeIf { isId(it) }.orEmpty()
}
