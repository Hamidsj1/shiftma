package ir.shiftapp.docs

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** یک مجتمع در «بستهٔ برنامه غذایی»: نام، برچسب ماه (مثلاً «مهر ۱۴۰۵») و فایل برنامه. */
class MealPackItem(val name: String, val label: String, val fileName: String, val bytes: ByteArray)

/**
 * بستهٔ برنامه غذایی با همان پسوند `.shiftma` (type = "meals"): ادمین فایل برنامه هر مجتمع را از درایو، جیمیل یا حافظه انتخاب می‌کند،
 * بسته را با هر پیام‌رسان یا ایمیل می‌فرستد، و کاربر با زدن روی آن، «شیفت ما» باز و برنامه را ذخیره می‌کند.
 */
object MealPackFile {
    const val TYPE = "meals"
    const val VERSION = 1
    const val MAX_CHARS = 15_000_000
    private const val MAX_ITEMS = 40
    private const val MAX_NAME = 80
    private const val MAX_LABEL = 40

    /** آرایهٔ مجتمع‌ها؛ همان آرایه‌ای که داخل فایل «ارسال انتخابی» (کلید `meals`) هم می‌آید. */
    fun toArray(items: List<MealPackItem>): JSONArray = JSONArray().also { a ->
        items.forEach { i ->
            a.put(JSONObject().put("name", i.name).put("label", i.label).put("fileName", i.fileName)
                .put("data", Base64.getEncoder().encodeToString(i.bytes)))
        }
    }

    fun toJson(items: List<MealPackItem>): String = JSONObject().apply {
        put("app", "shiftma"); put("type", TYPE); put("version", VERSION)
        put("complexes", toArray(items))
    }.toString()

    fun fromJson(text: String): List<MealPackItem> {
        val o = JSONObject(text)
        require(o.optString("app") == "shiftma" && o.optString("type") == TYPE) { "این فایل، بستهٔ برنامه غذایی «شیفت ما» نیست" }
        require(o.getInt("version") <= VERSION) { "نسخهٔ این فایل جدیدتر از برنامه است؛ برنامه را به‌روز کنید" }
        return fromArray(o.getJSONArray("complexes"))
    }

    /** خواندن و اعتبارسنجی آرایهٔ مجتمع‌ها (فایل برنامه غذایی یا کلید `meals` در فایل ارسال انتخابی). */
    fun fromArray(a: JSONArray): List<MealPackItem> {
        require(a.length() in 1..MAX_ITEMS) { "تعداد مجتمع‌های فایل نامعتبر است" }
        return (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            val name = j.getString("name").trim()
            val label = j.optString("label", "").trim()
            val fileName = j.getString("fileName").trim()
            require(name.isNotBlank() && name.length <= MAX_NAME) { "نام مجتمع نامعتبر است" }
            require(label.length <= MAX_LABEL) { "متن بیش از حد طولانی در فایل" }
            require(DocTypes.kindOf(fileName, null) != null) { "نوع فایل برنامه غذایی پشتیبانی نمی‌شود" }
            val bytes = try { Base64.getDecoder().decode(j.getString("data")) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("فایل برنامه غذایی خراب است") }
            require(bytes.isNotEmpty()) { "فایل برنامه غذایی خالی است" }
            MealPackItem(name, label, fileName.take(120), bytes)
        }
    }
}
