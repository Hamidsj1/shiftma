package ir.shiftapp.data

import ir.shiftapp.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Backup/Restore کامل: الگوها، تاریخ‌های مبنا، تغییرات دستی، مرخصی، اضافه‌کاری، استراحت، تعویض، همکاران، یادداشت و تنظیمات. */
object Backup {
    const val VERSION = 3   // ۲: holidays، ۳: مسئول شیفت/سرشیفت کل/بخش (پشتیبان‌های قدیمی‌تر هنوز خوانده می‌شوند)

    fun toJson(s: Snapshot): String = JSONObject().apply {
        put("app", "shiftma"); put("version", VERSION)
        put("patterns", JSONArray().also { a -> s.patterns.forEach { p -> a.put(JSONObject()
            .put("id", p.id).put("name", p.name).put("company", p.company)
            .put("baseYear", p.baseYear).put("baseMonth", p.baseMonth).put("baseDay", p.baseDay).put("pattern", p.pattern)
            .put("supervisor", p.supervisor).put("headSupervisor", p.headSupervisor).put("section", p.section).put("setName", p.setName)) } })
        put("entries", JSONArray().also { a -> s.entries.forEach { e -> a.put(JSONObject()
            .put("id", e.id).put("patternId", e.patternId).put("dateKey", e.dateKey).put("type", e.type)
            .put("shiftCode", e.shiftCode ?: JSONObject.NULL).put("minutes", e.minutes ?: JSONObject.NULL)
            .put("text", e.text).put("employeeId", e.employeeId ?: JSONObject.NULL)) } })
        put("employees", JSONArray().also { a -> s.employees.forEach { e -> a.put(JSONObject().put("id", e.id).put("name", e.name).put("note", e.note)) } })
        put("settings", JSONArray().also { a -> s.settings.forEach { e -> a.put(JSONObject().put("key", e.key).put("value", e.value)) } })
        put("holidays", JSONArray().also { a -> s.holidays.forEach { h -> a.put(JSONObject().put("dateKey", h.dateKey).put("title", h.title)) } })
    }.toString(1)

    /** هر ورودی نامعتبر باعث خطا می‌شود و چیزی در دیتابیس تغییر نمی‌کند. */
    fun fromJson(text: String): Snapshot {
        val o = JSONObject(text)
        require(o.optString("app") == "shiftma") { "این فایل پشتیبان این برنامه نیست" }
        require(o.getInt("version") <= VERSION) { "نسخهٔ پشتیبان جدیدتر از برنامه است" }
        fun <T> arr(name: String, f: (JSONObject) -> T): List<T> =
            o.getJSONArray(name).let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } }
        fun JSONObject.intOrNull(k: String) = if (isNull(k)) null else getInt(k)
        fun JSONObject.longOrNull(k: String) = if (isNull(k)) null else getLong(k)
        val patterns = arr("patterns") {
            val p = PatternEntity(it.getLong("id"), it.getString("name"), it.getString("company"),
                it.getInt("baseYear"), it.getInt("baseMonth"), it.getInt("baseDay"), it.getString("pattern"),
                it.optString("supervisor", ""), it.optString("headSupervisor", ""), it.optString("section", ""), it.optString("setName", "").take(60))
            require(JalaliDate(p.baseYear, p.baseMonth, p.baseDay).isValid) { "تاریخ مبنای نامعتبر در پشتیبان" }
            ShiftPattern.parse(p.pattern); p
        }
        val entries = arr("entries") {
            val e = DayEntry(it.getLong("id"), it.getLong("patternId"), it.getString("dateKey"), it.getString("type"),
                it.intOrNull("shiftCode"), it.intOrNull("minutes"), it.getString("text"), it.longOrNull("employeeId"))
            require(e.type in EntryType.ALL) { "نوع ثبت نامعتبر: ${e.type}" }
            require(JalaliDate.fromKey(e.dateKey).isValid) { "تاریخ نامعتبر: ${e.dateKey}" }; e
        }
        val holidays = if (o.has("holidays")) arr("holidays") {
            val h = Holiday(it.getString("dateKey"), it.getString("title"))
            require(JalaliDate.fromKey(h.dateKey).isValid) { "تاریخ تعطیلی نامعتبر: ${h.dateKey}" }; h
        } else emptyList()
        return Snapshot(patterns, entries,
            arr("employees") { Employee(it.getLong("id"), it.getString("name"), it.getString("note")) },
            arr("settings") { Setting(it.getString("key"), it.getString("value")) }, holidays)
    }
}

object CsvExport {
    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private val WEEK = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    /** CSV با BOM (UTF-8) تا Excel فارسی را درست باز کند. */
    fun month(year: Int, month: Int, base: JalaliDate, pattern: ShiftPattern, entries: Map<String, List<Entry>>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("تاریخ,روز هفته,شیفت الگو,شیفت نهایی,مرخصی,اضافه‌کاری(دقیقه),یادداشت\r\n")
        for (d in 1..JalaliCalendar.daysInMonth(year, month)) {
            val date = JalaliDate(year, month, d)
            val r = DayResolver.resolve(base, pattern, date, entries[date.key].orEmpty())
            val name = { c: Int -> ShiftType.byCode(c)?.name ?: "کد $c" }
            sb.append("%04d/%02d/%02d".format(year, month, d)).append(',').append(WEEK[date.dayOfWeek]).append(',')
                .append(name(r.computedCode)).append(',').append(name(r.effectiveCode)).append(',')
                .append(if (r.onLeave) "بله" else "").append(',').append(r.overtimeMinutes).append(',')
                .append(esc(r.entries.filter { it.type == EntryType.NOTE }.joinToString(" | ") { it.text })).append("\r\n")
        }
        return sb.toString()
    }
}
