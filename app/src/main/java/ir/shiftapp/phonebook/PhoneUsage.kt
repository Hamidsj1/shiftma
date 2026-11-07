package ir.shiftapp.phonebook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * آمار تماس‌های دفترچه تلفن؛ فقط روی همین گوشی (SharedPreferences جدا، نه دیتابیس مخاطبین).
 * آمار به «شماره» وصل است، نه شناسهٔ مخاطب: با «جایگزینی همهٔ مخاطبین» شناسه‌ها عوض می‌شود ولی شماره‌ها می‌مانند.
 */
object PhoneUsage {
    data class Stat(val count: Int, val last: Long)

    const val MAX_STATS = 400
    private const val PREFS = "shiftma_phone_use"

    /** کلید شماره: فقط ارقام (فارسی/عربی هم)، برای شمارهٔ ≥۱۰ رقمی ۱۰ رقم آخر (تا 0912… و +98912… یکی باشند). */
    fun key(number: String): String {
        val d = fixDigits(number).filter { it in '0'..'9' }
        return if (d.length >= 10) d.takeLast(10) else d
    }

    fun scoreOf(c: Contact, stats: Map<String, Stat>): Stat? {
        val hits = listOf(c.mobile, c.landline).map { key(it) }.filter { it.isNotEmpty() }.distinct().mapNotNull { stats[it] }
        if (hits.isEmpty()) return null
        return Stat(hits.sumOf { it.count }, hits.maxOf { it.last })
    }

    /** مخاطبین استفاده‌شده بالا (بیشترین تعداد، بعد تازه‌ترین)؛ بقیه با همان ترتیب قبلی (نام). مرتب‌سازی پایدار است. */
    fun rank(list: List<Contact>, stats: Map<String, Stat>): List<Contact> {
        if (stats.isEmpty()) return list
        val scored = list.map { it to scoreOf(it, stats) }
        val used = scored.filter { it.second != null }.sortedWith(compareByDescending<Pair<Contact, Stat?>> { it.second!!.count }.thenByDescending { it.second!!.last })
        return used.map { it.first } + scored.filter { it.second == null }.map { it.first }
    }

    fun bump(stats: Map<String, Stat>, number: String, now: Long): Map<String, Stat> {
        val k = key(number)
        if (k.isEmpty()) return stats
        val m = stats.toMutableMap()
        m[k] = Stat((m[k]?.count ?: 0) + 1, now)
        if (m.size > MAX_STATS) m.entries.sortedWith(compareBy<Map.Entry<String, Stat>> { it.value.count }.thenBy { it.value.last }).take(m.size - MAX_STATS).forEach { m.remove(it.key) }
        return m
    }

    // ---- ذخیره ----
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadStats(ctx: Context): Map<String, Stat> = runCatching {
        val o = JSONObject(sp(ctx).getString("stats", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { Stat(it.getInt(0), it.getLong(1)) } }
    }.getOrDefault(emptyMap())

    fun saveStats(ctx: Context, s: Map<String, Stat>) {
        val o = JSONObject(); s.forEach { (k, v) -> o.put(k, JSONArray().put(v.count).put(v.last)) }
        sp(ctx).edit().putString("stats", o.toString()).apply()
    }
}
