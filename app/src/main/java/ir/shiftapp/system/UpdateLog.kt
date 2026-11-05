package ir.shiftapp.system

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** تاریخچهٔ کوچک به‌روزرسانی راه‌دور (آدرس سامانه‌ها/تنظیمات): آخرین بررسی و آخرین تغییرها؛ فقط روی همین گوشی. */
object UpdateLog {
    private const val FILE = "shiftma_update_log"
    private const val MAX = 15
    data class Entry(val ts: Long, val text: String)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recordCheck(ctx: Context, ok: Boolean, error: String = "") {
        sp(ctx).edit().putLong("last_ts", System.currentTimeMillis()).putBoolean("last_ok", ok).putString("last_err", error).apply()
        if (ok) sp(ctx).edit().putLong("ok_ts", System.currentTimeMillis()).apply()
    }
    fun lastCheck(ctx: Context): Long = sp(ctx).getLong("last_ts", 0L)
    fun lastOkCheck(ctx: Context): Long = sp(ctx).getLong("ok_ts", 0L)
    fun lastOk(ctx: Context): Boolean = sp(ctx).getBoolean("last_ok", true)
    fun lastError(ctx: Context): String = sp(ctx).getString("last_err", "").orEmpty()

    fun addChanges(ctx: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        lines.forEach { arr.put(JSONObject().put("t", now).put("x", it)) }
        val old = runCatching { JSONArray(sp(ctx).getString("log", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until old.length()) arr.put(old.get(i))
        val cut = JSONArray()
        for (i in 0 until minOf(arr.length(), MAX)) cut.put(arr.get(i))
        sp(ctx).edit().putString("log", cut.toString()).apply()
    }

    fun entries(ctx: Context): List<Entry> {
        val a = runCatching { JSONArray(sp(ctx).getString("log", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> Entry(o.optLong("t"), o.optString("x")) } }
    }
}
