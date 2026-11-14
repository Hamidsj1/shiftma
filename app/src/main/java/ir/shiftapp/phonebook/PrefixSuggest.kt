package ir.shiftapp.phonebook

/**
 * پیش‌شمارهٔ داخلی: برای منطقهٔ شناخته‌شده (سونگون) خودکار ([forRegion])، برای بقیه فقط پیشنهاد کمکی در پنجرهٔ پرسش. اگر دفترچه شمارهٔ ۴ رقمی (داخلی) دارد ولی پیش‌شماره تنظیم نشده،
 * بین شماره‌های ثابتِ کامل (۱۱ رقم، شروع با ۰ و غیر از ۰۹) رایج‌ترین ۷ رقم اول (کد شهر + مرکز) را پیشنهاد می‌دهد.
 * پیش‌شماره بعد از تنظیم با [PhoneActions.dialable] روی همهٔ داخلی‌ها (دفترچه، تماس، ذخیره در گوشی، فایل خروجی) اعمال می‌شود.
 */
object PrefixSuggest {
    /** پیش‌شمارهٔ شناخته‌شدهٔ هر منطقه (شناسهٔ `region` فایل کاشی‌ها). منطقه‌های دیگر از کاربر پرسیده می‌شوند. */
    private val KNOWN = mapOf("sungun" to "0414171")

    fun forRegion(tag: String): String? = KNOWN[tag.trim().lowercase()]

    /**
     * تشخیص منطقه از روی خود دفترچه، بدون نیاز به برچسب: موبایل‌هایی که با `0914` شروع می‌شوند مخصوص همین منطقه‌اند،
     * ولی فقط وقتی **تعدادشان زیاد** باشد (دست‌کم [MIN_MOBILE] شماره و [MIN_SHARE_PERCENT] درصد موبایل‌های دفترچه) سونگون حساب می‌شود؛
     * چند شمارهٔ پراکنده کافی نیست. شمارهٔ ثابت‌ها نادیده‌اند چون حدسشان (مثلاً `0414454`) غلط درمی‌آمد.
     */
    fun byNumbers(list: List<Contact>): String? {
        val mobiles = list.flatMap { listOf(it.mobile, it.landline) }.map(::clean).filter { it.length == 11 && it.startsWith("09") && it.all(Char::isDigit) }
        val local = mobiles.count { it.startsWith("0914") }
        return if (local >= MIN_MOBILE && local * 100 >= mobiles.size * MIN_SHARE_PERCENT) SUNGUN_PREFIX else null
    }
    const val MIN_MOBILE = 30
    const val MIN_SHARE_PERCENT = 35
    private const val SUNGUN_PREFIX = "0414171"

    class Suggestion(val local: String, val intl: String, val extensions: Int)

    /** ارقام انگلیسی؛ +98 و 0098 به 0 تبدیل می‌شود. فقط ارقام (و در آغاز +) می‌ماند. */
    internal fun clean(n: String): String {
        val s = fixDigits(n).filter { it.isDigit() || it == '+' }
        return when {
            s.startsWith("+98") -> "0" + s.substring(3)
            s.startsWith("0098") -> "0" + s.substring(4)
            else -> s
        }
    }

    fun suggest(list: List<Contact>): Suggestion? {
        val nums = list.flatMap { listOf(it.mobile, it.landline) }.map(::clean).filter { it.isNotEmpty() }
        val extensions = nums.count { it.length == 4 && it.all(Char::isDigit) }
        if (extensions == 0) return null
        val full = nums.filter { it.length == 11 && it.all(Char::isDigit) && it.startsWith("0") && !it.startsWith("09") }
        val top = full.groupingBy { it.substring(0, 7) }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull() ?: return null
        if (top.value < 2) return null          // یک نمونه کافی نیست؛ حدس اشتباه بدتر از پیشنهاد ندادن است
        return Suggestion(top.key, "+98" + top.key.substring(1), extensions)
    }
}
