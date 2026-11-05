package ir.shiftapp.core

import org.json.JSONArray

/** یک به‌روزرسانی راه‌دور برای سایتی که از قبل در گوشی است (کلید = [SiteTile.key]). title/subtitle خالی = بدون تغییر. */
data class SiteUpdate(val key: String, val url: String, val title: String = "", val subtitle: String = "")

/**
 * منطق خالص به‌روزرسانی آدرس سایت‌ها از config.json. اصول:
 *  - فقط https، میزبان معتبر، بدون نام‌کاربری داخل آدرس و بدون IP؛
 *  - فقط سایتِ موجود (با کلید)، هرگز افزودن؛ فقط خود آدرس/عنوان/توضیح، نه نام کاربری و رمز؛
 *  - سایتی که کاربر خودش ویرایش کرده ([SiteTile.edited]) دست‌نخورده می‌ماند (ویرایش دستی اولویت دارد).
 */
object SiteUpdates {
    const val MAX_UPDATES = 30
    private val KEY_RE = Regex("[a-z0-9_]{1,30}")
    private val IP_RE = Regex("[0-9.]+|\\[.*\\]")

    fun isSafeSiteUrl(u: String): Boolean {
        if (u.length > TilesPackFile.MAX_URL || !u.startsWith("https://") || u.contains('@')) return false
        val h = TilesPackFile.hostOf(u)
        return h.isNotEmpty() && h.contains('.') && !IP_RE.matches(h)
    }

    fun parse(a: JSONArray?): List<SiteUpdate> {
        if (a == null) return emptyList()
        val out = ArrayList<SiteUpdate>()
        for (i in 0 until minOf(a.length(), MAX_UPDATES)) {
            val o = a.optJSONObject(i) ?: continue
            val key = o.optString("key").trim().lowercase()
            val url = o.optString("url").trim()
            val title = o.optString("title").trim()
            val sub = o.optString("subtitle").trim()
            if (!KEY_RE.matches(key) || !isSafeSiteUrl(url)) continue
            if (title.length > TilesPackFile.MAX_TITLE || sub.length > TilesPackFile.MAX_SUB) continue
            if (out.any { it.key == key }) continue
            out.add(SiteUpdate(key, url, title, sub))
        }
        return out
    }

    /** فهرست تازه، یا null اگر چیزی تغییر نمی‌کند (تا بی‌دلیل نوشته نشود). */
    fun apply(sites: List<SiteTile>, updates: List<SiteUpdate>): List<SiteTile>? {
        if (updates.isEmpty()) return null
        val byKey = updates.associateBy { it.key }
        var changed = false
        val out = sites.map { s ->
            val u = byKey[s.key]
            if (u == null || s.edited) s else {
                val t = u.title.ifEmpty { s.title }
                val sb = if (u.subtitle.isEmpty()) s.subtitle else u.subtitle
                if (t == s.title && sb == s.subtitle && u.url == s.url) s
                else { changed = true; SiteTile(s.key, t, sb, u.url, s.pdf, s.logo, edited = false) }
            }
        }
        return if (changed) out else null
    }

    /** متن تغییرها بین فهرست قدیم و جدید، برای تاریخچهٔ به‌روزرسانی (فقط آدرس/عنوان). */
    fun changes(old: List<SiteTile>, fresh: List<SiteTile>): List<String> = fresh.mapNotNull { n ->
        val o = old.firstOrNull { it.key == n.key } ?: return@mapNotNull null
        when {
            o.url != n.url -> "«${n.title}»: آدرس از ${TilesPackFile.hostOf(o.url)} به ${TilesPackFile.hostOf(n.url)} تغییر کرد"
            o.title != n.title || o.subtitle != n.subtitle -> "«${n.title}»: عنوان/توضیح به‌روز شد"
            else -> null
        }
    }

    /** سایت‌هایی که تغییر راه‌دور دارند ولی چون کاربر خودش ویرایششان کرده اعمال نمی‌شود. */
    fun skippedEdited(sites: List<SiteTile>, updates: List<SiteUpdate>): List<SiteTile> {
        val byKey = updates.associateBy { it.key }
        return sites.filter { s -> s.edited && byKey[s.key]?.let { it.url != s.url } == true }
    }
}
