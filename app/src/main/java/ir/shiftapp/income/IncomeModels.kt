package ir.shiftapp.income

import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliCalendar
import ir.shiftapp.core.JalaliDate
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** «راندمان» (آخر لیست تا ذخیره‌های قبلی به‌هم نخورند): پاداشی که بر اساس امتیاز حساب می‌شود. */
val INCOME_KINDS = listOf("حقوق", "اضافه‌کار", "پاداش", "عیدی", "کمک‌هزینه", "سایر", "راندمان")
const val KIND_POINTS = 6
/** ارزش پیش‌فرض هر امتیاز راندمان به ریال (۷۹۲٫۹ تومان). */
const val DEFAULT_RIAL_PER_POINT = 7929L

/**
 * یک دریافتی. day همان LocalDate.toEpochDay() است؛ مبلغ به تومان.
 * برای راندمان: points = تعداد امتیاز و rialPerPoint = ارزش هر امتیاز به ریال (برای بقیه ۰).
 */
data class IncomeEntry(
    val id: Long, val day: Int, val title: String, val amount: Long, val kind: Int = 0, val note: String = "",
    val points: Double = 0.0, val rialPerPoint: Long = 0
)

data class IncomeState(val entries: List<IncomeEntry> = emptyList()) {
    fun nextId(): Long = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
}

object IncomeStats {
    const val MAX_AMOUNT = 999_999_999_999L

    fun jalali(day: Int): JalaliDate = JalaliCalendar.fromLocalDate(LocalDate.ofEpochDay(day.toLong()))

    /** دریافتی‌های یک ماه شمسی، جدیدترین بالا. */
    fun inMonth(list: List<IncomeEntry>, year: Int, month: Int): List<IncomeEntry> =
        list.filter { val j = jalali(it.day); j.year == year && j.month == month }
            .sortedWith(compareByDescending<IncomeEntry> { it.day }.thenByDescending { it.id })

    fun total(list: List<IncomeEntry>): Long = list.sumOf { it.amount }

    /** جمع هر دسته (فقط دسته‌های غیرصفر)، بزرگ‌ترین اول. */
    fun byKind(list: List<IncomeEntry>): List<Pair<Int, Long>> =
        list.groupBy { it.kind.coerceIn(0, INCOME_KINDS.size - 1) }.map { (k, v) -> k to v.sumOf { it.amount } }
            .filter { it.second > 0 }.sortedByDescending { it.second }

    /** جمع هر یک از ۱۲ ماهِ یک سال شمسی (اندیس ۰ = فروردین). */
    fun yearTotals(list: List<IncomeEntry>, year: Int): List<Long> {
        val out = LongArray(12)
        list.forEach { val j = jalali(it.day); if (j.year == year) out[j.month - 1] += it.amount }
        return out.toList()
    }

    /** ۱۲۳۴۵۶۷ → «۱٬۲۳۴٬۵۶۷» */
    fun format(a: Long): String {
        val s = a.coerceAtLeast(0).toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c -> if (i > 0 && (s.length - i) % 3 == 0) sb.append('٬'); sb.append(c) }
        return Fa.digits(sb.toString())
    }

    /** امتیاز × ارزش هر امتیاز (ریال) ÷ ۱۰ = مبلغ به تومان، گرد‌شده. */
    fun amountFromPoints(points: Double, rialPerPoint: Long): Long = Math.round(points * rialPerPoint / 10.0)

    /** مبلغ واریزی (تومان) ÷ ارزش هر امتیاز = تعداد امتیاز (مبلغ × ۱۰ چون ارزش امتیاز به ریال است). */
    fun pointsFromAmount(amountToman: Long, rialPerPoint: Long): Double =
        if (rialPerPoint <= 0) 0.0 else amountToman * 10.0 / rialPerPoint

    /** عدد اعشاری با ارقام فارسی/انگلیسی؛ «٫» «.» «/» اعشار و «٬» «,» جداکنندهٔ هزارگان‌اند. صفر، منفی یا نامعتبر = null. */
    fun parseDecimal(text: String): Double? {
        val t = Fa.latin(text).replace("٬", "").replace(",", "").replace('٫', '.').replace('/', '.')
        val clean = t.filter { it in '0'..'9' || it == '.' }
        if (clean.isEmpty() || clean.count { it == '.' } > 1) return null
        val v = clean.toDoubleOrNull() ?: return null
        return if (v.isFinite() && v > 0.0 && v <= 1e9) v else null
    }

    /** ۴۲۸۸۰٫۵۶۵ → «۴۲٬۸۸۰٫۵۷»؛ صفرهای آخر اعشار حذف می‌شود (۷۹۲٫۹۰ → ۷۹۲٫۹). */
    fun formatPoints(p: Double): String {
        val r = Math.round(p * 100) / 100.0
        val whole = r.toLong()
        val frac = Math.round((r - whole) * 100).toInt().toString().padStart(2, '0').trimEnd('0')
        return format(whole) + if (frac.isEmpty()) "" else "٫" + Fa.digits(frac)
    }

    /** ورودی کاربر (ارقام فارسی/انگلیسی، با یا بدون جداکننده) → عدد؛ خالی یا بیش‌ازحد بزرگ = null. */
    fun parse(text: String): Long? {
        val d = Fa.latin(text).filter { it in '0'..'9' }.trimStart('0')
        if (d.isEmpty() || d.length > 12) return null
        return d.toLongOrNull()
    }
}

object IncomeCodec {
    fun encode(st: IncomeState): String = JSONObject().put("e", JSONArray().also { a ->
        st.entries.forEach { a.put(JSONArray().put(it.id).put(it.day).put(it.title).put(it.amount).put(it.kind).put(it.note).put(it.points).put(it.rialPerPoint)) }
    }).toString()

    fun decode(text: String): IncomeState = try {
        val a = JSONObject(text).optJSONArray("e")
        val list = ArrayList<IncomeEntry>()
        if (a != null) for (i in 0 until a.length()) a.optJSONArray(i)?.let {
            list.add(IncomeEntry(it.optLong(0), it.optInt(1), it.optString(2), it.optLong(3).coerceIn(0, IncomeStats.MAX_AMOUNT), it.optInt(4), it.optString(5),
                it.optDouble(6, 0.0).takeIf { d -> d.isFinite() && d >= 0.0 } ?: 0.0, it.optLong(7).coerceAtLeast(0)))
        }
        IncomeState(list)
    } catch (e: Exception) { IncomeState() }
}
