package ir.shiftapp.data

import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.ShiftPattern
import org.json.JSONArray
import org.json.JSONObject

/**
 * فایل الگو با پسوند اختصاصی `.shiftma` (متن JSON). هر الگو: نام، شرکت، تاریخ شروع دوره، چرخه و فیلدهای اختیاری
 * (مسئول شیفت، سرشیفت کل، بخش). برای ارسال الگوها یا گروه‌ها به همکاران.
 */
object PatternFile {
    const val EXT = "shiftma"
    const val MIME = "application/x-shiftma"
    const val VERSION = 1
    private const val MAX_PATTERNS = 100
    private const val MAX_TEXT = 120

    /** آرایهٔ JSON الگوها؛ هم در فایل الگو و هم در فایل ارسال انتخابی (BundleFile) استفاده می‌شود. */
    fun toArray(list: List<PatternEntity>): JSONArray = JSONArray().also { a ->
        list.forEach { p ->
            a.put(JSONObject().put("name", p.name).put("company", p.company)
                .put("baseYear", p.baseYear).put("baseMonth", p.baseMonth).put("baseDay", p.baseDay).put("pattern", p.pattern)
                .put("supervisor", p.supervisor).put("headSupervisor", p.headSupervisor).put("section", p.section))
        }
    }

    fun toJson(list: List<PatternEntity>): String = JSONObject().apply {
        put("app", "shiftma"); put("type", "patterns"); put("version", VERSION)
        put("patterns", toArray(list))
    }.toString(1)

    /** هر ورودی نامعتبر باعث خطا می‌شود (IllegalArgumentException یا JSONException)؛ شناسه‌ها همیشه صفر می‌شوند. */
    fun fromJson(text: String): List<PatternEntity> {
        val o = JSONObject(text)
        require(o.optString("app") == "shiftma" && o.optString("type") == "patterns") { "این فایل، فایل الگوی «شیفت ما» نیست" }
        require(o.getInt("version") <= VERSION) { "نسخهٔ این فایل جدیدتر از برنامه است؛ برنامه را به‌روز کنید" }
        return fromArray(o.getJSONArray("patterns"))
    }

    /** اعتبارسنجی و خواندن آرایهٔ الگوها (همان قواعد فایل الگو). */
    fun fromArray(a: JSONArray): List<PatternEntity> {
        require(a.length() in 1..MAX_PATTERNS) { "تعداد الگوهای فایل نامعتبر است" }
        return (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            val p = PatternEntity(
                0, j.getString("name").trim(), j.optString("company", "").trim(),
                j.getInt("baseYear"), j.getInt("baseMonth"), j.getInt("baseDay"), j.getString("pattern"),
                j.optString("supervisor", "").trim(), j.optString("headSupervisor", "").trim(), j.optString("section", "").trim()
            )
            require(p.name.isNotBlank()) { "نام الگو خالی است" }
            require(listOf(p.name, p.company, p.supervisor, p.headSupervisor, p.section).all { it.length <= MAX_TEXT }) { "متن بیش از حد طولانی در فایل" }
            require(JalaliDate(p.baseYear, p.baseMonth, p.baseDay).isValid) { "تاریخ شروع نامعتبر در فایل" }
            ShiftPattern.parse(p.pattern)
            p
        }
    }
}
