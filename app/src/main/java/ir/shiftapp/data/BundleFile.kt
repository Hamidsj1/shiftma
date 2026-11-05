package ir.shiftapp.data

import ir.shiftapp.core.Feeds
import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.TilesPack
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.docs.MealPackFile
import ir.shiftapp.docs.MealPackItem
import ir.shiftapp.phonebook.Contact
import ir.shiftapp.phonebook.ContactsFile
import org.json.JSONObject

/**
 * فایل «ارسال انتخابی» با همان پسوند `.shiftma` (type = "bundle"): هر ترکیبی از الگوها، کاشی‌های سامانه، مخاطبین و برنامه غذایی که کاربر
 * از کشوی صفحهٔ اول انتخاب کرده. کلید `patterns` همان آرایهٔ فایل الگو، `tiles` همان فایل کاشی‌ها، `contacts` همان آرایهٔ فایل مخاطبین
 * و `meals` همان آرایهٔ `complexes` فایل برنامه غذایی است؛ همه اختیاری‌اند ولی دست‌کم یکی باید باشد. نام کاربری و رمز سامانه‌ها هرگز در فایل نیست.
 * اگر مخاطبین یا برنامه غذایی در فایل باشد، `tiles.sections` هم به‌ترتیب `phonebook` و `meals` را دارد تا کاشی‌شان روی گوشی گیرنده نمایان شود.
 * نسخه: ۱ بدون برنامه غذایی، ۲ وقتی کلید `meals` هست (برنامه‌های قدیمی‌تر چنین فایلی را «جدیدتر از برنامه» می‌دانند و گمراه نمی‌شوند).
 */
object BundleFile {
    const val TYPE = "bundle"
    const val VERSION = 2
    const val MAX_CHARS = 15_000_000      // با مخاطبین (و عکس‌هایشان) هم‌سقف فایل مخاطبین

    class Bundle(val patterns: List<PatternEntity>, val tiles: TilesPack?, val contacts: List<Contact> = emptyList(), val meals: List<MealPackItem> = emptyList(), val prefix: String = "")

    fun toJson(
        patterns: List<PatternEntity>, sites: List<SiteTile>,
        contacts: List<Contact> = emptyList(), photoOf: (Contact) -> ByteArray? = { null },
        meals: List<MealPackItem> = emptyList(), prefix: String = "", feeds: Feeds = Feeds.NONE
    ): String {
        require(patterns.isNotEmpty() || sites.isNotEmpty() || contacts.isNotEmpty() || meals.isNotEmpty()) { "چیزی برای ارسال انتخاب نشده است" }
        // کاشی «دفترچه تلفن» و «برنامه غذایی» روی گوشی گیرنده فقط با کلید sections فایل کاشی‌ها نمایان می‌شود؛
        // پس هر وقت مخاطبین یا برنامه غذایی در فایل هست، بخشش هم همراهش نوشته می‌شود (وگرنه داده وارد می‌شود ولی کاشی پنهان می‌ماند)
        val sections = setOfNotNull("phonebook".takeIf { contacts.isNotEmpty() }, "meals".takeIf { meals.isNotEmpty() })
        return JSONObject().apply {
            put("app", "shiftma"); put("type", TYPE); put("version", if (meals.isNotEmpty()) VERSION else 1)
            if (patterns.isNotEmpty()) put("patterns", PatternFile.toArray(patterns))
            // خوراک‌های منطقه (بنر، اعلان، تنظیمات) همراه هر فایل می‌روند تا گیرنده هم به همان منطقه وصل شود؛ خوراک به‌تنهایی فایل را معتبر نمی‌کند
            if (sites.isNotEmpty() || sections.isNotEmpty() || !feeds.isEmpty) put("tiles", TilesPackFile.toJsonObject(sites, sections, feeds))
            if (contacts.isNotEmpty()) put("contacts", ContactsFile.toArray(contacts, photoOf))
            if (meals.isNotEmpty()) put("meals", MealPackFile.toArray(meals))
            // پیش‌شمارهٔ داخلی فرستنده فقط وقتی مخاطبین هست؛ برنامه‌های قدیمی کلید ناشناخته را نادیده می‌گیرند (نسخه عوض نمی‌شود)
            if (contacts.isNotEmpty()) ContactsFile.cleanPrefix(prefix).takeIf { it.isNotEmpty() }?.let { put("prefix", it) }
        }.toString()
    }

    fun fromJson(text: String): Bundle {
        val o = JSONObject(text)
        require(o.optString("app") == "shiftma" && o.optString("type") == TYPE) { "این فایل، فایل ارسال انتخابی «شیفت ما» نیست" }
        require(o.optInt("version", 1) <= VERSION) { "نسخهٔ این فایل جدیدتر از برنامه است؛ برنامه را به‌روز کنید" }
        val patterns = o.optJSONArray("patterns")?.let { PatternFile.fromArray(it) } ?: emptyList()
        val tiles = o.optJSONObject("tiles")?.let { TilesPackFile.fromObject(it) }
        val contacts = o.optJSONArray("contacts")?.takeIf { it.length() > 0 }?.let { ContactsFile.fromArray(it) } ?: emptyList()
        val meals = o.optJSONArray("meals")?.takeIf { it.length() > 0 }?.let { MealPackFile.fromArray(it) } ?: emptyList()
        require(patterns.isNotEmpty() || tiles != null || contacts.isNotEmpty() || meals.isNotEmpty()) { "فایل خالی است" }
        return Bundle(patterns, tiles, contacts, meals, if (contacts.isNotEmpty()) ContactsFile.cleanPrefix(o.optString("prefix", "")) else "")
    }
}
