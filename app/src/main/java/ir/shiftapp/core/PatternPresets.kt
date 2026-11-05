package ir.shiftapp.core

/**
 * الگوی آماده؛ کدها: ۱ روزکار، ۲ عصرکار، ۳ شبکار، ۴ استراحت، ۵ شیفت ۲۴ ساعته.
 * crews > 1 یعنی برای پوشش کامل به این تعداد گروه نیاز است و شروع هر گروه offsetDays روز بعد از گروه قبلی است
 * (در همهٔ این الگوها هر شیفت در هر روز دقیقاً توسط یک گروه پوشش داده می‌شود).
 */
data class PatternPreset(
    val title: String, val description: String, val codes: List<Int>,
    val crews: Int = 1, val offsetDays: Int = 0,
    /** اگر مقدار داشته باشد، crewOrder[i] = «نوبت شروع» گروه i (۰ = اولین). مثلاً [0,2,1,3] یعنی گروه‌های B و C جابه‌جا شروع می‌شوند. */
    val crewOrder: List<Int>? = null
) {
    /** نوبت شروع گروه i (۰ تا crews-1). */
    fun slotOf(i: Int): Int = crewOrder?.get(i) ?: i
    /** حروف گروه‌ها به ترتیب شروع، مثلاً A، C، B، D */
    fun startSequence(): List<Char> = (0 until crews).sortedBy { slotOf(it) }.map { 'A' + it }

    /**
     * روز شروع پیش‌فرض دوره (گروه A): نزدیک‌ترین روز تا [today] (خودِ امروز هم ممکن است) که فاصله‌اش از [PatternPresets.ANCHOR]
     * مضرب طول چرخه باشد. چون فقط به فاصله‌ی روزها بستگی دارد، با عوض شدن ماه (۳۰/۳۱ روزه) جابه‌جا نمی‌شود.
     */
    /** تاریخ شروع دورهٔ گروه [crew] (۰ = A) وقتی گروه A از [aStart] شروع می‌کند. */
    fun startOf(crew: Int, aStart: JalaliDate): JalaliDate = aStart.plusDays(slotOf(crew) * offsetDays)

    /** برعکس [startOf]: اگر «گروه من» ([crew]) از [mineStart] شروع می‌کند، گروه A از چه روزی شروع می‌کند. */
    fun aStartFromMine(crew: Int, mineStart: JalaliDate): JalaliDate = mineStart.plusDays(-slotOf(crew) * offsetDays)

    fun defaultStart(today: JalaliDate = JalaliDate.today()): JalaliDate {
        val len = codes.size.coerceAtLeast(1)
        return today.plusDays(-Math.floorMod(today.toJdn() - PatternPresets.ANCHOR.toJdn(), len))
    }
}

object PatternPresets {
    /** تاریخ لنگر: روز اول چرخهٔ گروه A در همهٔ الگوهای آماده (۱۹ مهر ۱۴۰۵). روز شروع پیش‌فرض هر ماه از روی همین حساب می‌شود. */
    val ANCHOR = JalaliDate(1405, 7, 19)
    private fun rep(code: Int, n: Int) = List(n) { code }
    private const val H24 = "ساعت شیفت را در تنظیمات وارد کنید (۰۸:۰۰ تا ۰۸:۰۰ یعنی ۲۴ ساعت)."

    val ALL: List<PatternPreset> = listOf(
        PatternPreset("۲ روز – ۱ استراحت – ۲ شب – ۳ استراحت", "چرخهٔ ۸ روزه: روز، روز، استراحت، شب، شب، سه روز استراحت",
            listOf(1, 1, 4, 3, 3, 4, 4, 4), crews = 4, offsetDays = 2, crewOrder = listOf(0, 2, 1, 3)),
        PatternPreset("۲۴ ساعت کار – ۴۸ ساعت استراحت", "چرخهٔ ۳ روزه برای ۳ گروه. $H24",
            listOf(5, 4, 4), crews = 3, offsetDays = 1),
        PatternPreset("۲۴ ساعت کار – ۷۲ ساعت استراحت", "چرخهٔ ۴ روزه برای ۴ گروه. $H24",
            listOf(5, 4, 4, 4), crews = 4, offsetDays = 1),
        PatternPreset("۲ روز – ۲ عصر – ۲ شب – ۲ استراحت", "چرخهٔ ۸ روزهٔ سه‌شیفته",
            rep(1, 2) + rep(2, 2) + rep(3, 2) + rep(4, 2), crews = 4, offsetDays = 2),
        PatternPreset("۲ روز – ۲ شب – ۴ استراحت", "چرخهٔ ۸ روزهٔ دوشیفته (ساعت‌های طولانی)",
            rep(1, 2) + rep(3, 2) + rep(4, 4), crews = 4, offsetDays = 2),
        PatternPreset("۳ روز – ۳ شب – ۳ استراحت", "چرخهٔ ۹ روزهٔ دوشیفته",
            rep(1, 3) + rep(3, 3) + rep(4, 3), crews = 3, offsetDays = 3),
        PatternPreset("روز – عصر – شب – استراحت", "چرخهٔ ۴ روزهٔ ساده",
            listOf(1, 2, 3, 4), crews = 4, offsetDays = 1),
        PatternPreset("۷ صبح – ۷ عصر – ۷ شب", "چرخش هفتگی، بدون استراحت داخل چرخه",
            rep(1, 7) + rep(2, 7) + rep(3, 7), crews = 3, offsetDays = 7),
        PatternPreset("۴ روز – ۴ استراحت", "چرخهٔ ۸ روزه، فقط روزکار",
            rep(1, 4) + rep(4, 4), crews = 2, offsetDays = 4),
        PatternPreset("۷ روز – ۷ استراحت", "چرخهٔ ۱۴ روزه (مناسب کار در محل‌های دورافتاده)",
            rep(1, 7) + rep(4, 7), crews = 2, offsetDays = 7),
        PatternPreset("اداری ۵ + ۲", "پنج روز کار، دو روز استراحت", rep(1, 5) + rep(4, 2)),
        PatternPreset("۶ + ۱", "شش روز کار، یک روز استراحت", rep(1, 6) + rep(4, 1)),
        PatternPreset("۲-۲-۳ (پیتمن)", "چرخهٔ ۱۴ روزه؛ آخر هفته‌ها یک‌درمیان آزاد",
            rep(1, 2) + rep(4, 2) + rep(1, 3) + rep(4, 2) + rep(1, 2) + rep(4, 3))
    )

    /** الگوهایی که در شروع کار (صفحهٔ اول، قبل از ساختن اولین الگو) پیشنهاد می‌شوند؛ پرکاربردترین‌ها. */
    val FEATURED: List<PatternPreset> = listOf(
        "۲ روز – ۱ استراحت – ۲ شب – ۳ استراحت", "۲۴ ساعت کار – ۴۸ ساعت استراحت", "۲ روز – ۲ عصر – ۲ شب – ۲ استراحت",
        "روز – عصر – شب – استراحت", "اداری ۵ + ۲"
    ).mapNotNull { t -> ALL.firstOrNull { it.title == t } }

    /**
     * الگوهای سادهٔ نسخهٔ قبلی برنامه (فهرست «شیفت‌کاری»). هر مورد با قالب فشرده نوشته شده:
     * «۱d ۴r» یعنی ۱ روزکار و ۴ استراحت؛ حرف‌ها: d روزکار، e عصرکار، n شبکار، r استراحت.
     * الگوهایی که کدهایشان دقیقاً با یکی از الگوهای بالا یکی است دوباره اضافه نمی‌شوند.
     */
    private val CLASSIC_SPECS = listOf(
        "1d 4r", "1d 3r", "1d 1r", "1d 1e 1n 2r", "1d 1n 2r", "1d 1n 1r",
        "2d 2r 2n 2r", "2d 2r", "2d 1r 2e 1r 2n 3r", "2d 1r 2e 1r 2n 1r", "2d 1r 2n 3r", "2d 1r", "2d 2e 2r",
        "2d 2e 2n 4r", "2d 2e 2n 2r", "2d 2n 4r", "2d 2n 3r", "2d 2e 1r 2n 1r",
        "3d 2r 3n 4r", "3d 3r", "3d 2r 3e 1r", "3d 1r 3e 1r 3n 1r", "3d 3e 3r", "3d 3e 3n 3r",
        "4d 4r", "4d 3r", "4d 2r", "4d 4n 4r", "4d 1r 4e 1r 4n 2r", "4d 1r 4e 1r", "4d 4e 4n 4r",
        "5d 2r 5e 2r", "5d 2r", "6d 1r 6e 1r", "7d 7r", "7d 7n 7r", "12d 4r", "22d 8r", "14d 14r",
        "3e 3d 3n 3r", "1n 1r", "2n 2r", "3n 2r 3d 2r 3e 2r", "4n 1r 4e 1r 4d 1r"
    )

    private fun codeOf(letter: Char): Int = when (letter) { 'd' -> 1; 'e' -> 2; 'n' -> 3; else -> 4 }
    private fun nameOf(letter: Char): String = when (letter) { 'd' -> "روزکار"; 'e' -> "عصرکار"; 'n' -> "شبکار"; else -> "استراحت" }

    private fun fromSpec(spec: String): PatternPreset {
        val parts = spec.split(' ').map { it.dropLast(1).toInt() to it.last() }
        val codes = parts.flatMap { (n, l) -> List(n) { codeOf(l) } }
        val title = parts.joinToString(" ") { (n, l) -> "${Fa.num(n)} ${nameOf(l)}" }
        return PatternPreset(title, "چرخهٔ ${Fa.num(codes.size)} روزه (مثل نسخهٔ قبلی)", codes)
    }

    val CLASSIC: List<PatternPreset> = CLASSIC_SPECS.map { fromSpec(it) }.filter { c -> ALL.none { it.codes == c.codes } }
}
