package ir.shiftapp.core

import org.json.JSONObject

/** وضعیت یک بخش: نمایان، پنهان، یا پولی (قفل تا وارد کردن کد). */
enum class SectionState { SHOW, HIDE, PAID }

/** تنظیمات دریافتی از گیت‌هاب برای یک محل. بخشی که در فایل نیامده، نمایان است. */
data class RemoteConfig(val sections: Map<String, SectionState> = emptyMap(), val payLink: String = "") {
    fun stateOf(id: String): SectionState = sections[id] ?: SectionState.SHOW
    fun visible(id: String): Boolean = stateOf(id) != SectionState.HIDE
    fun needsPayment(id: String): Boolean = stateOf(id) == SectionState.PAID
}

/**
 * فایل JSON تنظیمات هر محل (روی گیت‌هاب، کنار ad.json و notice.json). نمونه:
 * {"enabled":true,"payLink":"https://example.ir/pay","sections":{"sport":"hide","reading":"paid","meals":"show"}}
 * مقدارها: show | hide | paid. شناسهٔ بخش‌ها همان شناسهٔ HomeSection است (sport، reading، english، …).
 * enabled=false یعنی همهٔ قاعده‌ها نادیده گرفته شوند (همه چیز نمایان و رایگان).
 */
object RemoteConfigFile {
    const val MAX_SECTIONS = 40
    const val MAX_JSON = 50_000

    /** این بخش‌ها هرگز پنهان یا پولی نمی‌شوند، تا یک اشتباه در فایل راه تنظیمات را نبندد. */
    val PROTECTED = setOf("settings", "about", "shift")

    private val ID_RE = Regex("[a-z0-9_]{1,30}")

    /** فایل خراب = null (برنامه آخرین تنظیم سالم را نگه می‌دارد)؛ فایل سالم ولی خاموش = تنظیم خالی. */
    fun parseOrNull(json: String): RemoteConfig? = try {
        val root = JSONObject(json)
        if (!root.optBoolean("enabled", true)) RemoteConfig() else {
            val out = LinkedHashMap<String, SectionState>()
            root.optJSONObject("sections")?.let { ob ->
                val keys = ob.keys()
                while (keys.hasNext() && out.size < MAX_SECTIONS) {
                    val rawKey = keys.next()
                    val id = rawKey.trim().lowercase()
                    if (!ID_RE.matches(id) || id in PROTECTED) continue
                    val st = when (ob.optString(rawKey, "").trim().lowercase()) {
                        "hide" -> SectionState.HIDE
                        "paid" -> SectionState.PAID
                        "show" -> SectionState.SHOW
                        else -> continue
                    }
                    if (st != SectionState.SHOW) out[id] = st
                }
            }
            val link = root.optString("payLink").trim().let { if (NoticeFeed.isSafeUrl(it)) it else "" }
            RemoteConfig(out, link)
        }
    } catch (e: Exception) { null }

    fun parse(json: String): RemoteConfig = parseOrNull(json) ?: RemoteConfig()
}
