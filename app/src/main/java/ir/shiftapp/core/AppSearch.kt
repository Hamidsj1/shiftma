package ir.shiftapp.core

/*
 * جست‌وجوی سراسری درون برنامه: منطق خالص (بدون وابستگی به اندروید و تست‌پذیر).
 * هر مورد یک «مقصد» است (بخش، ابزار، سامانه یا یادداشت). تطبیق بی‌توجه به ی/ي، ک/ك، نیم‌فاصله، اعراب و نوع رقم است
 * و همهٔ کلمه‌های جست‌وجو باید پیدا شوند (AND).
 */

/** [id] شناسهٔ مقصد (شناسهٔ بخش یا نشانی سایت)؛ [keywords] واژه‌های هم‌معنی برای پیدا شدن. */
data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val keywords: List<String> = emptyList(),
    val group: String = ""
)

object AppSearch {
    const val DEFAULT_LIMIT = 8

    /** نرمال‌سازی برای مقایسه: حروف عربی، اعراب، نیم‌فاصله، ارقام و فاصله‌های اضافه. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = when (ch) {
                'ي', 'ى', 'ئ' -> 'ی'
                'ك' -> 'ک'
                'ة', 'ۀ' -> 'ه'
                'أ', 'إ', 'آ' -> 'ا'
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                '\u200c', '\u200d', '\u200f', '\u200e', '\u0640' -> continue    // نیم‌فاصله، جداکننده‌های نامرئی و کشیده
                in '\u064B'..'\u065F', '\u0670' -> continue                      // اعراب
                else -> ch.lowercaseChar()
            }
            sb.append(c)
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    private fun words(normalized: String): List<String> = normalized.split(' ').filter { it.isNotEmpty() }

    /** امتیاز یک کلمه برای یک مورد؛ ۰ یعنی پیدا نشد. */
    private fun tokenScore(token: String, title: String, keywords: List<String>, subtitle: String): Int {
        var best = 0
        if (title.startsWith(token)) best = 100
        else if (words(title).any { it.startsWith(token) }) best = 80
        else if (title.contains(token)) best = 60
        if (best < 40 && keywords.any { it.startsWith(token) || words(it).any { w -> w.startsWith(token) } }) best = 40
        if (best < 25 && keywords.any { it.contains(token) }) best = 25
        if (best < 10 && subtitle.contains(token)) best = 10
        return best
    }

    /** امتیاز کل؛ ۰ اگر حتی یک کلمه پیدا نشود. */
    fun score(item: SearchItem, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        val title = normalize(item.title)
        val sub = normalize(item.subtitle)
        val keys = item.keywords.map { normalize(it) }
        var total = 0
        for (t in tokens) {
            val s = tokenScore(t, title, keys, sub)
            if (s == 0) return 0
            total += s
        }
        return total
    }

    /** نتایج مرتب‌شده؛ برای پرسش خالی فهرست خالی برمی‌گردد. تساوی امتیاز با ترتیب اولیهٔ فهرست حل می‌شود. */
    fun search(items: List<SearchItem>, query: String, limit: Int = DEFAULT_LIMIT): List<SearchItem> {
        val tk = tokens(query)
        if (tk.isEmpty()) return emptyList()
        return items.withIndex()
            .map { (i, item) -> Triple(score(item, tk), i, item) }
            .filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, Int, SearchItem>> { it.first }.thenBy { it.second })
            .take(limit)
            .map { it.third }
    }

    /** واژه‌های هم‌معنی هر بخش/ابزار (برای پیدا شدن با نام‌های دیگر). */
    fun keywordsFor(id: String): List<String> = KEYWORDS[id].orEmpty()

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "shift" to listOf("شیفت", "تقویم", "مرخصی", "اضافه کاری", "الگو", "گروه", "کاردکس", "چهارشیفت", "گزارش", "پشتیبان", "آلارم", "یادآور", "تنظیمات", "تعطیلات", "تعویض", "خروجی", "اکسل", "pdf", "جدول"),
        "phonebook" to listOf("تلفن", "مخاطب", "شماره", "تماس", "داخلی"),
        "payslips" to listOf("فیش", "حقوق", "فیش حقوقی", "pdf", "مقایسه"),
        "meals" to listOf("غذا", "منو", "ناهار", "شام", "سلف"),
        "notes" to listOf("یادداشت", "چک لیست", "یادآور", "نوت", "لیست"),
        "files" to listOf("فایل", "عکس", "مدرک", "pdf", "سند"),
        "income" to listOf("دریافتی", "حقوق", "پاداش", "عیدی", "راندمان", "درآمد", "نمودار"),
        "prayer" to listOf("اذان", "نماز", "قبله", "اوقات", "ظهر", "مغرب", "عشا", "صبح", "عصر", "قبله نما"),
        "english" to listOf("انگلیسی", "لغت", "زبان", "فلش کارت", "آزمون", "وکب", "english"),
        "pay" to listOf("حقوق", "اضافه کار", "شب کاری", "جمعه کاری", "فیش", "محاسبه"),
        "sport" to listOf("ورزش", "تمرین", "رژیم", "کالری", "وزن", "آب", "تغذیه", "حرکت"),
        "reading" to listOf("مطالعه", "کتاب", "کتابخوانی", "تایمر", "خواندن"),
        "dhikr" to listOf("ذکر", "تسبیح", "صلوات", "رکعت", "قضا", "رمضان", "افطار", "سحر", "استغفار", "تسبیحات"),
        "convert" to listOf("تبدیل", "واحد", "طول", "وزن", "دما", "حجم"),
        "age" to listOf("سن", "تولد", "سالگرد"),
        "datecv" to listOf("تاریخ", "شمسی", "میلادی", "قمری", "تبدیل تاریخ"),
        "tv" to listOf("تلویزیون", "تلوبیون", "پخش زنده", "شبکه"),
        "settings" to listOf("تنظیمات", "تم", "پوسته", "تاریک", "پشتیبان", "بازیابی", "یادآور", "آلارم", "باتری", "اطمینان", "اذان دیر", "ساعت شیفت", "گزارش پخش", "آلارم آزمایشی"),
        "about" to listOf("درباره", "اشتراک", "ارسال برنامه", "معرفی", "نسخه", "دوستان"),
        "span" to listOf("مدت", "فاصله", "سابقه", "بیمه", "سال", "ماه", "روز", "تاریخ", "محاسبه"),
        "imgcompress" to listOf("عکس", "تصویر", "حجم", "کاهش", "فشرده", "کوچک", "آپلود", "ارسال")
    )
}
