package ir.shiftapp.phonebook

import ir.shiftapp.core.TilesPack
import ir.shiftapp.core.TilesPackFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * فایل مخاطبین با همان پسوند اختصاصی `.shiftma` (متن JSON، فیلد type = "contacts").
 * وقتی کسی این فایل را بزند، گوشی «شیفت ما» را برای بازکردنش پیشنهاد می‌دهد (intent-filter همین پسوند در Manifest).
 * عکس مخاطب اختیاری است و به‌صورت base64 (JPEG) داخل فایل می‌آید.
 */
object ContactsFile {
    const val TYPE = "contacts"
    const val VERSION = 1
    /** سقف حجم متن فایل هنگام خواندن (برای جلوگیری از پر شدن حافظه). */
    const val MAX_CHARS = 15_000_000
    private const val MAX_CONTACTS = 5000
    private const val MAX_TEXT = 200
    private const val MAX_PHOTO_CHARS = 400_000

    /** [photoOf] برای هر مخاطب بایت‌های عکس را می‌دهد؛ null یعنی بدون عکس (برای فایل سبک همیشه null بدهید). */
    fun toJson(list: List<Contact>, tiles: JSONObject? = null, prefix: String = "", photoOf: (Contact) -> ByteArray? = { null }): String = JSONObject().apply {
        put("app", "shiftma"); put("type", TYPE); put("version", VERSION)
        put("contacts", toArray(list, photoOf))
        // اختیاری: کاشی‌های سامانه‌ها همراه مخاطبین (نسخه‌های قدیمی برنامه این کلید را نادیده می‌گیرند)
        if (tiles != null) put("tiles", tiles)
        // اختیاری: پیش‌شمارهٔ داخلی فرستنده (نسخه‌های قدیمی برنامه این کلید را نادیده می‌گیرند)
        cleanPrefix(prefix).takeIf { it.isNotEmpty() }?.let { put("prefix", it) }
    }.toString()

    /** پیش‌شمارهٔ معتبر (ارقام، + و ویرگول، حداکثر ۳۰ نویسه) یا رشتهٔ خالی؛ ورودی نامعتبر نادیده گرفته می‌شود. */
    fun cleanPrefix(raw: String): String = fixDigits(raw.trim()).replace('،', ',').replace(" ", "").takeIf { Regex("[0-9+,]{1,30}").matches(it) } ?: ""

    /** پیش‌شمارهٔ همراه فایل مخاطبین (اختیاری)؛ نبود یا خراب بودن مانع وارد کردن مخاطبین نمی‌شود. */
    fun prefixOf(text: String): String {
        if (!text.contains("\"prefix\"")) return ""
        return runCatching { cleanPrefix(JSONObject(text).optString("prefix", "")) }.getOrDefault("")
    }

    /** آرایهٔ مخاطبین؛ هم در فایل مخاطبین و هم در فایل «ارسال انتخابی» (کلید contacts) استفاده می‌شود. */
    fun toArray(list: List<Contact>, photoOf: (Contact) -> ByteArray? = { null }): JSONArray = JSONArray().also { a ->
        list.forEach { c ->
            val o = JSONObject().put("name", c.name).put("mobile", c.mobile).put("landline", c.landline)
                .put("dept", c.dept).put("unit", c.unit)
            if (c.social.isNotBlank()) o.put("social", c.social)
            photoOf(c)?.takeIf { it.isNotEmpty() }?.let { o.put("photo", Base64.getEncoder().encodeToString(it)) }
            a.put(o)
        }
    }

    /** هر ورودی نامعتبر باعث IllegalArgumentException یا JSONException می‌شود؛ شناسه‌ها صفر و عکس‌ها در photoData می‌آیند. */
    fun fromJson(text: String): List<Contact> {
        val o = JSONObject(text)
        require(o.optString("app") == "shiftma" && o.optString("type") == TYPE) { "این فایل، فایل مخاطبین «شیفت ما» نیست" }
        require(o.getInt("version") <= VERSION) { "نسخهٔ این فایل جدیدتر از برنامه است؛ برنامه را به‌روز کنید" }
        return fromArray(o.getJSONArray("contacts"))
    }

    fun fromArray(a: JSONArray): List<Contact> {
        require(a.length() in 1..MAX_CONTACTS) { "تعداد مخاطبین فایل نامعتبر است" }
        return (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            val c = Contact(
                name = j.getString("name").trim(),
                mobile = fixDigits(j.optString("mobile", "").trim()),
                landline = fixDigits(j.optString("landline", "").trim()),
                dept = j.optString("dept", "").trim(),
                unit = j.optString("unit", "").trim(),
                social = Social.normalize(j.optString("social", "")),
                photoData = j.optString("photo", "").takeIf { it.length <= MAX_PHOTO_CHARS && looksLikeBase64Image(it) } ?: ""
            )
            require(c.name.isNotBlank()) { "نام یکی از مخاطبین خالی است" }
            require(listOf(c.name, c.mobile, c.landline, c.dept, c.unit).all { it.length <= MAX_TEXT }) { "متن بیش از حد طولانی در فایل" }
            c
        }
    }

    /** کاشی‌های سامانه‌ای که همراه فایل مخاطبین آمده (اختیاری). اگر نباشد یا خراب باشد null می‌دهد و مانع وارد کردن مخاطبین نمی‌شود. */
    fun tilesOf(text: String): TilesPack? {
        if (!text.contains("\"tiles\"")) return null
        return runCatching { JSONObject(text).optJSONObject("tiles")?.let { TilesPackFile.fromObject(it) } }.getOrNull()
    }

    /** نوع فایل .shiftma؛ اول از ابتدای متن (سریع، بدون خواندن کل JSON)، وگرنه با خواندن کامل. پیش‌فرض «patterns» (فایل‌های قدیمی). */
    fun typeOf(text: String): String {
        Regex("\"type\"\\s*:\\s*\"([a-z]+)\"").find(text.take(400))?.groupValues?.get(1)?.let { return it }
        return runCatching { JSONObject(text).optString("type") }.getOrNull().orEmpty().ifEmpty { "patterns" }
    }
}
