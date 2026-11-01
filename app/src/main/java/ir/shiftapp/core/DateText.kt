package ir.shiftapp.core

/** تبدیل متن تاریخ شمسی (مثلاً ۱۴۰۵/۰۷/۱۳ یا 1405-7-13) به [JalaliDate]. */
object DateText {
    fun normalizeDigits(s: String): String = buildString {
        for (ch in s) append(
            when (ch) {
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')   // ۰..۹
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')   // ٠..٩
                else -> ch
            }
        )
    }

    /** جداکننده‌هایی که کاربر می‌تواند بین سال و ماه و روز بزند: / - . فاصله، ویرگول (لاتین و فارسی)، ؛ و ٫ */
    private val SEPARATORS = "/-. ,\u060C;\u061B\u066B\u2044\u2212\u2013"

    /**
     * ورودی تاریخ در حین تایپ: ارقام فارسی/عربی/لاتین پذیرفته می‌شوند و بدون زدن «/» خودکار قالب می‌گیرند
     * (۱۴۰۵۰۷۱۳ ← ۱۴۰۵/۰۷/۱۳). اگر کاربر خودش دو جداکننده بزند (مثل ۱۴۰۵/۷/۳) همان‌طور که نوشته نگه داشته می‌شود.
     */
    fun typing(raw: String): String {
        val s = normalizeDigits(raw)
        val manual = s.count { it in SEPARATORS } >= 2
        val out = if (manual) {
            val sb = StringBuilder()
            for (ch in s) when {
                ch in '0'..'9' -> sb.append(ch)
                ch in SEPARATORS && sb.isNotEmpty() && sb.last() != '/' -> sb.append('/')
            }
            sb.toString().take(10)
        } else {
            val d = s.filter { it in '0'..'9' }.take(8)
            buildString {
                append(d.take(4))
                if (d.length > 4) append('/').append(d.substring(4, minOf(6, d.length)))
                if (d.length > 6) append('/').append(d.substring(6))
            }
        }
        return Fa.digits(out)
    }

    /** null یعنی قالب یا تاریخ نامعتبر. فقط ارقام (۱۴۰۵۰۷۱۳) هم پذیرفته می‌شود. */
    fun parse(text: String): JalaliDate? {
        val norm = normalizeDigits(text.trim())
        if (norm.length == 8 && norm.all { it in '0'..'9' }) {
            return JalaliDate(norm.substring(0, 4).toInt(), norm.substring(4, 6).toInt(), norm.substring(6).toInt()).takeIf { it.isValid }
        }
        val parts = norm.split(*SEPARATORS.toCharArray()).filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return JalaliDate(y, m, d).takeIf { it.isValid }
    }

    fun format(d: JalaliDate): String = "%04d/%02d/%02d".format(d.year, d.month, d.day)

    // ───────────── ماه به‌صورت عدد یا نام (برای مبدل تاریخ) ─────────────

    private val GREG_ALIASES = listOf(
        listOf("ژانویه", "جانویه", "january", "jan"), listOf("فوریه", "فبریه", "february", "feb"),
        listOf("مارس", "march", "mar"), listOf("آوریل", "اپریل", "april", "apr"),
        listOf("مه", "می", "may"), listOf("ژوئن", "جون", "june", "jun"),
        listOf("ژوئیه", "جولای", "july", "jul"), listOf("اوت", "آگوست", "august", "aug"),
        listOf("سپتامبر", "september", "sep", "sept"), listOf("اکتبر", "اکتوبر", "october", "oct"),
        listOf("نوامبر", "نوآمبر", "november", "nov"), listOf("دسامبر", "december", "dec")
    )
    private val HIJRI_ALIASES = listOf(
        listOf("محرم"), listOf("صفر"), listOf("ربیع الاول", "ربیع‌الاول", "ربیع اول"), listOf("ربیع الثانی", "ربیع‌الثانی", "ربیع ثانی", "ربیع الآخر"),
        listOf("جمادی الاول", "جمادی‌الاول", "جمادی اول"), listOf("جمادی الثانی", "جمادی‌الثانی", "جمادی ثانی", "جمادی الآخر"),
        listOf("رجب"), listOf("شعبان"), listOf("رمضان"), listOf("شوال"), listOf("ذی القعده", "ذی‌القعده", "ذیقعده"), listOf("ذی الحجه", "ذی‌الحجه", "ذیحجه")
    )

    /** برای مقایسهٔ نام ماه: یکسان‌سازی ی/ک، حذف نیم‌فاصله، فاصله و اعراب. */
    private fun key(s: String): String = buildString {
        for (ch in normalizeDigits(s.trim())) when (ch) {
            'ي', 'ى' -> append('ی')
            'ك' -> append('ک')
            'ة' -> append('ه')
            '\u200c', '\u200d', 'ـ', ' ', '-', '\'', '.', '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652' -> {}
            else -> append(ch.lowercaseChar())
        }
    }

    private fun aliasesOf(kind: Int): List<List<String>> = when (kind) {
        0 -> Fa.MONTHS.map { listOf(it) }
        1 -> GREG_ALIASES
        else -> HIJRI_ALIASES
    }

    /**
     * ماهِ تایپ‌شده را به شمارهٔ ۱ تا ۱۲ برمی‌گرداند. هم عدد (۷ یا 07) و هم نام (مهر، october، اکتبر، رمضان) پذیرفته می‌شود.
     * kind: ۰ شمسی، ۱ میلادی، ۲ قمری. نام ناقص (مثلاً «اسف») فقط وقتی یکتا باشد پذیرفته می‌شود. null = نامعتبر.
     */
    fun parseMonth(kind: Int, text: String): Int? {
        val t = key(text)
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { return if (it in 1..12) it else null }
        val names = aliasesOf(kind)
        names.forEachIndexed { i, l -> if (l.any { key(it) == t }) return i + 1 }
        if (t.length < 2) return null
        val hits = names.withIndex().filter { (_, l) -> l.any { key(it).startsWith(t) } }.map { it.index }
        return if (hits.size == 1) hits[0] + 1 else null
    }

    fun monthName(kind: Int, month: Int): String? = aliasesOf(kind).getOrNull(month - 1)?.firstOrNull()
}
