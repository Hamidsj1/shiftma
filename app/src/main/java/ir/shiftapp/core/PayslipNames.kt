package ir.shiftapp.core

/** نوع فیش: حکمی یا رفاهی. [code] در نام فایل می‌آید. */
enum class SlipKind(val code: String, val label: String) {
    HOKMI("hokmi", "حکمی"),
    REFAHI("refahi", "رفاهی");

    companion object { fun fromCode(c: String): SlipKind? = values().firstOrNull { it.code == c } }
}

/**
 * نام‌گذاری فایل فیش‌ها: «سال-ماه-نوع.pdf» با سال و ماه شمسی (مثل 1405-06-refahi.pdf).
 * فایل‌های قدیمی «سال-ماه.pdf» (بدون نوع) به‌عنوان «حکمی» خوانده می‌شوند. منطق خالص و قابل تست.
 */
object PayslipNames {
    private val RE = Regex("""^(\d{4})-(\d{2})(?:-([a-z]+))?\.pdf$""")

    class Parsed(val year: Int, val month: Int, val kind: SlipKind)

    fun fileName(year: Int, month: Int, kind: SlipKind): String = "%04d-%02d-%s.pdf".format(year, month, kind.code)

    /** null یعنی نام فایل معتبر نیست (سال ۴ رقمی، ماه ۱ تا ۱۲، نوع شناخته‌شده یا بدون نوع). */
    fun parse(name: String): Parsed? {
        val m = RE.find(name) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        if (mo !in 1..12) return null
        val kindCode = m.groupValues[3]
        val kind = if (kindCode.isEmpty()) SlipKind.HOKMI else SlipKind.fromCode(kindCode) ?: return null
        return Parsed(y, mo, kind)
    }

    /** ماه قبل از (سال، ماه)؛ فیش معمولاً مال ماه گذشته است. */
    fun previous(year: Int, month: Int): Pair<Int, Int> = if (month == 1) (year - 1) to 12 else year to (month - 1)

    fun isPdf(bytes: ByteArray): Boolean = bytes.size > 5 && String(bytes, 0, 5, Charsets.ISO_8859_1) == "%PDF-"
}
