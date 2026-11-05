package ir.shiftapp.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** یکی از سه اولویت روز. predictMin / actualMin = دقیقه؛ ۰ یعنی هنوز وارد نشده. */
data class PlanPriority(
    val text: String = "", val impact: String = "", val predictMin: Int = 0, val actualMin: Int = 0, val done: Boolean = false
) { val isEmpty: Boolean get() = text.isBlank() }

/** یک ردیف برنامهٔ ساعتی. minute = دقیقهٔ روز (۰..۱۴۳۹) یا -۱ بدون ساعت. buffer = ردیف ثابت «بافر». */
data class PlanRow(
    val id: Int, val minute: Int = -1, val text: String = "", val done: Boolean = false,
    val alarm: Boolean = false, val buffer: Boolean = false
)

/** مرور شبانه. status: ۰ نامشخص، ۱ انجام شد، ۲ نشد، ۳ فردا. */
data class PlanReview(val text: String = "", val status: Int = 0, val reason: String = "")

/** dayType: ۰ کاری، ۱ درسی، ۲ عمومی. dayEpoch = LocalDate.toEpochDay(). */
data class PlanDay(
    val dayEpoch: Int,
    val dayType: Int = 0,
    val plannedMin: Int = 0,
    val priorities: List<PlanPriority> = List(3) { PlanPriority() },
    val rows: List<PlanRow> = listOf(PlanRow(1, -1, "", buffer = true)),
    val review: List<PlanReview> = List(3) { PlanReview() }
) {
    /** هر سه اولویت نوشته شده و انجام شده؟ */
    val allDone: Boolean get() = priorities.size >= 3 && priorities.all { !it.isEmpty && it.done }
    val doneCount: Int get() = priorities.count { !it.isEmpty && it.done }
    val filledCount: Int get() = priorities.count { !it.isEmpty }
    val isBlank: Boolean get() = priorities.all { it.isEmpty } && rows.all { it.text.isBlank() } && review.all { it.text.isBlank() }
}

/** reviewMin: ساعت یادآور مرور شبانه (-۱ = خاموش)؛ alarmsOn: کلید کلی آلارم‌های برنامه. */
data class PlannerState(
    val days: List<PlanDay> = emptyList(),
    val reviewMin: Int = -1,
    val alarmsOn: Boolean = true
) {
    fun day(epoch: Int): PlanDay = days.firstOrNull { it.dayEpoch == epoch } ?: PlanDay(epoch)
    fun withDay(d: PlanDay): PlannerState {
        val rest = days.filter { it.dayEpoch != d.dayEpoch }
        return copy(days = if (d.isBlank && d.plannedMin == 0) rest else (rest + d).sortedBy { it.dayEpoch })
    }
}

/** ارزیابی هفتهٔ منتهی به [endEpoch] (هفت روز، شامل خودش). */
data class WeekEval(val allDoneDays: Int, val avgGapMin: Int?, val measured: Int)

object Planner {
    const val KEEP_DAYS = 120
    const val MAX_ROWS = 24
    val DAY_TYPES = listOf("کاری", "درسی", "عمومی")

    fun weekEval(s: PlannerState, endEpoch: Int): WeekEval {
        val week = ((endEpoch - 6)..endEpoch).map { s.day(it) }
        val gaps = week.flatMap { d -> d.priorities.filter { !it.isEmpty && it.predictMin > 0 && it.actualMin > 0 } }
            .map { kotlin.math.abs(it.predictMin - it.actualMin) }
        return WeekEval(week.count { it.allDone }, if (gaps.isEmpty()) null else (gaps.sum().toDouble() / gaps.size).toInt(), gaps.size)
    }

    /** موارد «فردا»ی مرور دیروز که باید پیشنهاد اولویت امروز شوند. */
    fun carriedFrom(prev: PlanDay): List<String> = prev.review.filter { it.status == 3 && it.text.isNotBlank() }.map { it.text.trim() }

    /** ردیف‌های ساعتی به‌ترتیب ساعت؛ بدون ساعت‌ها آخر، بافر همیشه جای خودش بین آن‌ها با ساعتش. */
    fun sortedRows(rows: List<PlanRow>): List<PlanRow> = rows.sortedWith(compareBy({ it.minute < 0 }, { it.minute }, { it.id }))

    fun nextRowId(rows: List<PlanRow>): Int = (rows.maxOfOrNull { it.id } ?: 0) + 1

    /** یک هشدار آیندهٔ برنامه. */
    data class Alarm(val triggerAtMs: Long, val title: String, val body: String)

    /**
     * نزدیک‌ترین هشدار بعد از [nowMs] در امروز و دو روز بعد: ردیف‌های ساعت‌دار با آلارم روشن و انجام‌نشده، و مرور شبانه (اگر روشن است).
     * فقط یک هشدار برمی‌گرداند؛ بعد از شلیک دوباره محاسبه می‌شود (مثل ReminderScheduler).
     */
    fun nextAlarm(s: PlannerState, nowMs: Long, zone: ZoneId): Alarm? {
        if (!s.alarmsOn) return null
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay().toInt()
        var best: Alarm? = null
        fun offer(a: Alarm) { if (a.triggerAtMs > nowMs && (best == null || a.triggerAtMs < best!!.triggerAtMs)) best = a }
        for (e in today..today + 2) {
            val d = s.days.firstOrNull { it.dayEpoch == e } ?: continue
            val date = java.time.LocalDate.ofEpochDay(e.toLong())
            for (r in d.rows) {
                if (r.alarm && !r.done && r.minute in 0..1439) {
                    val text = r.text.ifBlank { if (r.buffer) "بافر ثابت" else "کار برنامه‌ریزی‌شده" }
                    offer(Alarm(SingleAlarm.at(date, r.minute, zone), "برنامهٔ روزانه", text))
                }
            }
        }
        if (s.reviewMin in 0..1439) {
            for (e in today..today + 1) offer(Alarm(SingleAlarm.at(java.time.LocalDate.ofEpochDay(e.toLong()), s.reviewMin, zone), "مرور شبانه", "پنج دقیقه: چه شد، چه ماند، فردا چه؟"))
        }
        return best
    }

    // ───────── ذخیره‌سازی (JSON در تنظیمات؛ با پشتیبان می‌آید) ─────────

    fun encode(s: PlannerState): String = JSONObject().apply {
        put("rm", s.reviewMin); put("al", s.alarmsOn)
        put("days", JSONArray().also { arr ->
            s.days.takeLast(KEEP_DAYS).forEach { d ->
                arr.put(JSONObject().apply {
                    put("e", d.dayEpoch); put("t", d.dayType); put("p", d.plannedMin)
                    put("pr", JSONArray().also { a -> d.priorities.forEach { p ->
                        a.put(JSONObject().put("x", p.text).put("i", p.impact).put("a", p.predictMin).put("b", p.actualMin).put("d", p.done)) } })
                    put("rw", JSONArray().also { a -> d.rows.forEach { r ->
                        a.put(JSONObject().put("id", r.id).put("m", r.minute).put("x", r.text).put("d", r.done).put("al", r.alarm).put("bf", r.buffer)) } })
                    put("rv", JSONArray().also { a -> d.review.forEach { r ->
                        a.put(JSONObject().put("x", r.text).put("s", r.status).put("r", r.reason)) } })
                })
            }
        })
    }.toString()

    /** مقدار خراب هیچ‌وقت استثنا نمی‌دهد؛ حالت خالی برمی‌گردد. */
    fun decode(raw: String?): PlannerState = try {
        if (raw.isNullOrBlank()) PlannerState() else {
            val o = JSONObject(raw)
            val days = o.optJSONArray("days")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::decodeDay) }
            }.orEmpty().distinctBy { it.dayEpoch }.sortedBy { it.dayEpoch }
            PlannerState(days, o.optInt("rm", -1).takeIf { it in 0..1439 } ?: -1, o.optBoolean("al", true))
        }
    } catch (e: Exception) { PlannerState() }

    private fun decodeDay(o: JSONObject): PlanDay? {
        val e = o.optInt("e", Int.MIN_VALUE); if (e == Int.MIN_VALUE) return null
        val pr = o.optJSONArray("pr")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            PlanPriority(it.optString("x").take(200), it.optString("i").take(120), it.optInt("a").coerceIn(0, 1440), it.optInt("b").coerceIn(0, 1440), it.optBoolean("d")) } }.orEmpty()
        val rw = o.optJSONArray("rw")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.take(MAX_ROWS).map {
            PlanRow(it.optInt("id"), it.optInt("m", -1).takeIf { m -> m in 0..1439 } ?: -1, it.optString("x").take(200), it.optBoolean("d"), it.optBoolean("al"), it.optBoolean("bf")) } }.orEmpty()
        val rv = o.optJSONArray("rv")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.take(10).map {
            PlanReview(it.optString("x").take(200), it.optInt("s").coerceIn(0, 3), it.optString("r").take(200)) } }.orEmpty()
        return PlanDay(
            e, o.optInt("t").coerceIn(0, 2), o.optInt("p").coerceIn(0, 1440),
            (pr + List(3) { PlanPriority() }).take(maxOf(3, pr.size)).take(3),
            rw.ifEmpty { listOf(PlanRow(1, -1, "", buffer = true)) },
            (rv + List(3) { PlanReview() }).take(maxOf(3, rv.size))
        )
    }
}
