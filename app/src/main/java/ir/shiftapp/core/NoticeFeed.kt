package ir.shiftapp.core

import org.json.JSONObject

/** یک اعلان پنل نوتیفیکیشن: [id] یکتا است و هر id فقط یک‌بار به کاربر نشان داده می‌شود. */
data class NoticeItem(val id: String, val title: String, val body: String, val link: String = "", val expiresMs: Long = 0L)

/**
 * فایل JSON پنل نوتیفیکیشن (بدون SDK و بدون سرویس گوگل). نمونه:
 * {"enabled":true,"items":[{"id":"n1","title":"نسخهٔ جدید","body":"برنامه را از بازار به‌روز کنید","link":"https://cafebazaar.ir/app/ir.shiftapp.shiftma","expires":"2026-12-01"}]}
 * link اختیاری و فقط https است. expires اختیاری است (YYYY-MM-DD)؛ بعد از آن تاریخ اعلان نشان داده نمی‌شود.
 */
object NoticeFeed {
    const val MAX_ITEMS = 5
    const val MAX_SEEN = 200

    fun isSafeUrl(u: String): Boolean = u.length < 2000 && u.startsWith("https://", ignoreCase = true)

    /** پیشوند شناسهٔ اعلان‌های محلی در فهرست «دیده‌شده»؛ تا شناسهٔ یکسان در دو خوراک با هم قاطی نشود. */
    const val LOCAL_PREFIX = "L:"

    /**
     * خوراک‌هایی که این کاربر می‌بیند، به ترتیب: «محلی» (از فایل کاشی‌های محل کارش؛ فقط گیت‌هاب) و «عمومی» ([default]).
     * عمومی همیشه هست، مگر فایل کاشی‌ها [publicToo] = false بدهد و خوراک محلی معتبر باشد.
     */
    fun feeds(default: String, local: String, publicToo: Boolean): List<Pair<String, Boolean>> {
        val own = isSafeUrl(local) && MealFeed.isSafeUrl(local)
        val out = ArrayList<Pair<String, Boolean>>()
        if (own) out.add(local to true)
        if ((!own || publicToo) && isSafeUrl(default)) out.add(default to false)
        return out
    }

    /** شناسهٔ ذخیره‌شده در «دیده‌شده‌ها»: اعلان محلی با پیشوند. */
    fun seenKey(id: String, isLocal: Boolean): String = if (isLocal) LOCAL_PREFIX + id else id

    private val DATE_RE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

    /** پایان روزِ (UTC) تاریخ میلادی «YYYY-MM-DD»؛ نامعتبر = 0 (بدون انقضا). */
    fun parseExpiry(s: String): Long {
        val m = DATE_RE.matchEntire(s.trim()) ?: return 0L
        return try {
            java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                .plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e: Exception) { 0L }
    }

    fun parse(json: String): List<NoticeItem> = try {
        val root = JSONObject(json)
        if (!root.optBoolean("enabled", true)) emptyList() else {
            val arr = root.optJSONArray("items")
            val out = ArrayList<NoticeItem>()
            if (arr != null) for (i in 0 until arr.length()) {
                val ob = arr.optJSONObject(i) ?: continue
                val id = ob.optString("id").trim().take(60)
                val title = ob.optString("title").trim().take(100)
                val body = ob.optString("body").trim().take(500)
                if (id.isEmpty() || title.isEmpty()) continue
                val link = ob.optString("link").trim().let { if (isSafeUrl(it)) it else "" }
                out.add(NoticeItem(id, title, body, link, parseExpiry(ob.optString("expires"))))
                if (out.size >= MAX_ITEMS) break
            }
            out
        }
    } catch (e: Exception) { emptyList() }

    /** اعلان‌های تازه: ندیده‌شده و منقضی‌نشده. */
    fun fresh(items: List<NoticeItem>, seen: Set<String>, nowMs: Long): List<NoticeItem> =
        items.filter { it.id !in seen && (it.expiresMs == 0L || nowMs < it.expiresMs) }

    /** فهرست شناسه‌های دیده‌شده؛ برای جلوگیری از رشد بی‌پایان فقط آخرین [MAX_SEEN] مورد نگه داشته می‌شود. */
    fun mergeSeen(seen: List<String>, shown: List<String>): List<String> = (seen + shown).distinct().takeLast(MAX_SEEN)
}
