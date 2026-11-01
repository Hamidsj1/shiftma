package ir.shiftapp.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * یک نتیجهٔ جست‌وجوی اذان. اگر [url] خالی و [itemId] پر باشد، یک «مجموعه» در Internet Archive است که
 * باید فایل‌هایش را جداگانه گرفت ([AzanCatalog.parseArchiveFiles]).
 */
data class AzanHit(
    val title: String, val url: String, val sizeBytes: Long, val source: String,
    val license: String? = null, val itemId: String? = null
) { val isItem: Boolean get() = url.isBlank() && itemId != null }

/**
 * جست‌وجوی اذان از دو منبع عمومی و بدون کلید: ویکی‌انبار (Wikimedia Commons؛ پروانهٔ فایل‌ها مشخص است) و
 * Internet Archive. فقط ساخت نشانی و تفسیر پاسخ JSON اینجاست (تست‌پذیر)؛ اتصال در `system/AzanSearch.kt`.
 */
object AzanCatalog {
    const val SRC_COMMONS = "ویکی‌انبار"
    const val SRC_ARCHIVE = "Internet Archive"
    private val AUDIO_EXT = setOf("mp3", "ogg", "oga", "m4a", "wav", "opus")
    private val SAFE_ID = Regex("^[A-Za-z0-9._-]{1,120}$")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun commonsUrl(q: String, limit: Int = 15): String =
        "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=$limit" +
            "&gsrsearch=${enc("${q.trim()} filetype:audio")}&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata"

    fun archiveSearchUrl(q: String, rows: Int = 15): String =
        "https://archive.org/advancedsearch.php?q=${enc("(${q.trim()}) AND mediatype:audio")}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&rows=$rows&page=1&sort%5B%5D=downloads+desc&output=json"

    fun archiveMetaUrl(id: String): String = "https://archive.org/metadata/$id"

    private fun niceTitle(raw: String): String =
        raw.removePrefix("File:").substringBeforeLast('.').replace('_', ' ').trim().ifBlank { raw }

    fun parseCommons(json: String): List<AzanHit> {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = ArrayList<Pair<Int, AzanHit>>()
        for (k in pages.keys()) {
            val p = pages.optJSONObject(k) ?: continue
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val url = info.optString("url")
            val mime = info.optString("mime").lowercase()
            val size = info.optLong("size")
            if (!url.startsWith("https://") || !(mime.startsWith("audio/") || mime == "application/ogg")) continue
            if (size < AzanSounds.MIN_BYTES || size > AzanSounds.MAX_BYTES) continue
            val lic = info.optJSONObject("extmetadata")?.optJSONObject("LicenseShortName")?.optString("value")?.takeIf { it.isNotBlank() }
            out += p.optInt("index", 999) to AzanHit(niceTitle(p.optString("title")), url, size, SRC_COMMONS, lic)
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    private fun textOf(o: Any?): String? = when (o) {
        is String -> o
        is JSONArray -> o.optString(0)
        else -> null
    }?.trim()?.takeIf { it.isNotBlank() }

    fun parseArchiveSearch(json: String): List<AzanHit> {
        val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val out = ArrayList<AzanHit>()
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            val id = d.optString("identifier")
            if (!SAFE_ID.matches(id)) continue
            out += AzanHit(textOf(d.opt("title")) ?: id, "", 0L, SRC_ARCHIVE, null, id)
        }
        return out
    }

    fun parseArchiveFiles(id: String, json: String, max: Int = 25): List<AzanHit> {
        if (!SAFE_ID.matches(id)) return emptyList()
        val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<AzanHit>()
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val name = f.optString("name")
            if (name.isBlank() || f.optString("format") == "Metadata") continue
            if (name.substringAfterLast('.', "").lowercase() !in AUDIO_EXT) continue
            val size = f.optString("size").toLongOrNull() ?: continue
            if (size < AzanSounds.MIN_BYTES || size > AzanSounds.MAX_BYTES) continue
            if (!seen.add(name.substringBeforeLast('.'))) continue      // نسخهٔ مشتق همان فایل را دوباره نشان نده
            val path = name.split('/').joinToString("/") { enc(it).replace("+", "%20") }
            out += AzanHit(niceTitle(name.substringAfterLast('/')), "https://archive.org/download/$id/$path", size, SRC_ARCHIVE, null, id)
            if (out.size >= max) break
        }
        return out
    }
}
