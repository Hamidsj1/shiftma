package ir.shiftapp.core

/*
 * اطمینان از آلارم و اذان: منطق خالص (بدون وابستگی به اندروید و تست‌پذیر).
 * - گزارش پخش: هر بار که یادآور/آلارم/اذان شلیک می‌کند، «زمان برنامه‌ریزی‌شده» و «زمان واقعی شلیک» ثبت می‌شود؛
 *   اختلاف آن‌ها نشان می‌دهد سیستم گوشی (صرفه‌جویی باتری، Doze) هشدار را دیر رسانده یا نه.
 * - راهنمای سازنده: تنظیمات خاص هر برند که جلوی مرگ برنامه در پس‌زمینه را می‌گیرد.
 */

/** یک رکورد پخش: [dueMs] زمان برنامه‌ریزی‌شده، [firedMs] زمان واقعی رسیدن به برنامه. */
data class FireRecord(val dueMs: Long, val firedMs: Long, val kind: String) {
    val delayMs: Long get() = firedMs - dueMs
}

/** خلاصهٔ گزارش برای نمایش بالای فهرست. */
data class FireSummary(val total: Int, val late: Int, val worstDelayMs: Long)

object FireLog {
    const val KIND_SHIFT = "shift"
    const val KIND_ALARM = "alarm"
    const val KIND_PRAYER = "prayer"
    const val KIND_NOTE = "note"

    /** حداکثر تعداد رکوردهای نگه‌داشته‌شده. */
    const val MAX = 40

    /** بیش از این مقدار تأخیر یعنی «دیر رسیده». */
    const val LATE_MS = 60_000L

    private const val FS = ','
    private const val RS = ';'

    fun encode(list: List<FireRecord>): String =
        list.joinToString(RS.toString()) { "${it.dueMs}$FS${it.firedMs}$FS${it.kind}" }

    /** مقدار خراب نادیده گرفته می‌شود؛ هیچ‌وقت استثنا نمی‌دهد. */
    fun decode(raw: String?): List<FireRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RS).mapNotNull { rec ->
            val p = rec.split(FS)
            if (p.size < 3) return@mapNotNull null
            val due = p[0].toLongOrNull() ?: return@mapNotNull null
            val fired = p[1].toLongOrNull() ?: return@mapNotNull null
            val kind = p[2].takeIf { it in KINDS } ?: return@mapNotNull null
            FireRecord(due, fired, kind)
        }
    }

    private val KINDS = setOf(KIND_SHIFT, KIND_ALARM, KIND_PRAYER, KIND_NOTE)

    /** رکورد تازه را اضافه می‌کند و فقط [MAX] مورد آخر را نگه می‌دارد. */
    fun append(list: List<FireRecord>, rec: FireRecord): List<FireRecord> = (list + rec).takeLast(MAX)

    fun isLate(r: FireRecord): Boolean = r.delayMs > LATE_MS

    fun kindTitle(kind: String): String = when (kind) {
        KIND_SHIFT -> "یادآور شیفت"
        KIND_ALARM -> "آلارم"
        KIND_PRAYER -> "اذان"
        KIND_NOTE -> "یادآور یادداشت"
        else -> "هشدار"
    }

    fun summary(list: List<FireRecord>): FireSummary =
        FireSummary(list.size, list.count { isLate(it) }, list.maxOfOrNull { it.delayMs.coerceAtLeast(0L) } ?: 0L)

    /** «به‌موقع» یا «۲ دقیقه دیر» یا «۱ ساعت و ۵ دقیقه دیر». تأخیر کمتر از ۱۰ ثانیه عادی است. */
    fun delayLabel(delayMs: Long): String {
        if (delayMs < 10_000L) return "به‌موقع"
        val sec = delayMs / 1000L
        if (sec < 60L) return Fa.digits("$sec ثانیه دیر")
        val min = sec / 60L
        if (min < 60L) return Fa.digits("$min دقیقه دیر")
        val h = min / 60L
        val m = min % 60L
        return Fa.digits(if (m == 0L) "$h ساعت دیر" else "$h ساعت و $m دقیقه دیر")
    }

    /** متن قابل اشتراک برای فرستادن به پشتیبان/سازنده. [clock] تبدیل زمان به متن را بر عهده می‌گیرد (برای تست‌پذیری). */
    fun report(list: List<FireRecord>, manufacturer: String, sdk: Int, clock: (Long) -> String): String {
        val s = summary(list)
        return buildString {
            appendLine("گزارش پخش یادآور و اذان — شیفت ما")
            appendLine("دستگاه: $manufacturer / اندروید API $sdk")
            appendLine(Fa.digits("تعداد: ${s.total}، دیررسیده: ${s.late}"))
            list.takeLast(20).forEach {
                appendLine("${kindTitle(it.kind)} — برنامه ${clock(it.dueMs)} — ${delayLabel(it.delayMs)}")
            }
        }.trimEnd()
    }
}

/** راهنمای تنظیمات هر سازنده برای جلوگیری از بسته شدن برنامه و دیر رسیدن آلارم. */
object OemGuide {
    private val GENERIC = listOf(
        "در تنظیمات گوشی، مصرف باتری برنامه را روی «بدون محدودیت» یا «بهینه نشود» بگذارید.",
        "برنامه را از لیست «برنامه‌های اخیر» با کشیدن نبندید؛ در بعضی گوشی‌ها این کار آلارم‌ها را حذف می‌کند."
    )

    private val XIAOMI = listOf(
        "امنیت ← مجوزها ← شروع خودکار (Autostart): «شیفت ما» را روشن کنید.",
        "تنظیمات ← برنامه‌ها ← «شیفت ما» ← صرفه‌جویی باتری: «بدون محدودیت».",
        "در لیست برنامه‌های اخیر، کارت «شیفت ما» را نگه دارید و قفل (Lock) کنید.",
        "برای روشن شدن صفحه و باز شدن صفحهٔ آلارم مثل تماس: تنظیمات ← برنامه‌ها ← «شیفت ما» ← مجوزهای دیگر: «نمایش در قفل‌صفحه» و «نمایش پنجره‌های پاپ‌آپ هنگام اجرا در پس‌زمینه» را روی «مجاز» بگذارید."
    )

    private val HUAWEI = listOf(
        "تنظیمات ← باتری ← راه‌اندازی برنامه (App launch): «شیفت ما» را روی «مدیریت دستی» بگذارید و هر سه گزینه (اجرای خودکار، اجرای ثانویه، اجرا در پس‌زمینه) را روشن کنید.",
        "در لیست برنامه‌های اخیر، «شیفت ما» را قفل کنید."
    )

    private val SAMSUNG = listOf(
        "تنظیمات ← باتری ← محدودیت‌های استفاده در پس‌زمینه: «شیفت ما» را از فهرست «برنامه‌های خواب» و «خواب عمیق» دربیاورید.",
        "تنظیمات ← برنامه‌ها ← «شیفت ما» ← باتری: «بدون محدودیت»."
    )

    private val OPPO_FAMILY = listOf(
        "تنظیمات ← باتری ← «شیفت ما»: اجازهٔ فعالیت در پس‌زمینه و راه‌اندازی خودکار را روشن کنید.",
        "در لیست برنامه‌های اخیر، «شیفت ما» را قفل کنید."
    )

    private val TRANSSION = listOf(
        "تنظیمات ← مدیریت برنامه‌ها ← «شیفت ما» ← مصرف باتری: «بدون محدودیت».",
        "در مدیر راه‌اندازی خودکار (Autostart)، «شیفت ما» را روشن کنید."
    )

    fun tips(manufacturer: String): List<String> {
        val m = manufacturer.trim().lowercase()
        val brand = when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> XIAOMI
            m.contains("huawei") || m.contains("honor") -> HUAWEI
            m.contains("samsung") -> SAMSUNG
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") || m.contains("vivo") -> OPPO_FAMILY
            m.contains("tecno") || m.contains("infinix") || m.contains("itel") -> TRANSSION
            else -> emptyList()
        }
        return brand + GENERIC
    }

    /** true اگر برای این سازنده راهنمای اختصاصی داریم (نه فقط راهنمای عمومی). */
    fun hasBrandTips(manufacturer: String): Boolean = tips(manufacturer).size > GENERIC.size
}
