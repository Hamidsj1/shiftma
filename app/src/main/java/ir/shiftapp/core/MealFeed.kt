package ir.shiftapp.core

import org.json.JSONObject

/** یک مجتمع در فایل خوراک برنامه غذایی: نام، برچسب ماه و آدرس فایل (PDF یا عکس). */
class MealFeedItem(val name: String, val label: String, val fileUrl: String)

/**
 * خوراک برنامه غذایی شرکت (فایل JSON روی گیت‌هاب). آدرسش فقط از «فایل کاشی‌ها»ی شرکت (کلید `mealsFeed`) به برنامه می‌رسد؛
 * کاربر عمومی بازار چنین آدرسی ندارد و برنامه برایش هیچ درخواستی نمی‌فرستد. جزئیات: docs/MEAL_FEED.md
 *
 * {"enabled":true,"complexes":[{"name":"مجتمع الف","label":"مهر ۱۴۰۵","file":"https://raw.githubusercontent.com/.../a.pdf"}]}
 */
object MealFeed {
    const val MAX_ITEMS = 20
    const val MAX_JSON = 100_000
    private const val MAX_NAME = 80
    private const val MAX_LABEL = 40

    /** فقط https و فقط میزبان‌های گیت‌هاب (خوراک و فایل‌ها). */
    fun isSafeUrl(u: String): Boolean {
        if (u.length > TilesPackFile.MAX_URL || !u.startsWith("https://")) return false
        val h = TilesPackFile.hostOf(u)
        return h == "raw.githubusercontent.com" || h == "github.com" || h.endsWith(".github.io") || h.endsWith(".githubusercontent.com")
    }

    /** نام فایل از آدرس (بدون پرسمان)؛ نوع فایل از پسوندش تشخیص داده می‌شود. */
    fun fileNameOf(url: String): String = url.substringBefore('#').substringBefore('?').substringAfterLast('/')

    /** فایل خراب یا `enabled=false` یعنی «چیزی نیست»؛ موارد نامعتبر رد می‌شوند و بقیه می‌مانند. */
    fun parse(json: String): List<MealFeedItem> = try {
        val root = JSONObject(json)
        if (!root.optBoolean("enabled", true)) emptyList()
        else {
            val arr = root.optJSONArray("complexes")
            val out = ArrayList<MealFeedItem>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val label = o.optString("label").trim()
                val file = o.optString("file").trim()
                if (name.isEmpty() || name.length > MAX_NAME || label.length > MAX_LABEL || !isSafeUrl(file)) continue
                if (out.any { it.name.equals(name, ignoreCase = true) }) continue
                out.add(MealFeedItem(name, label, file))
                if (out.size >= MAX_ITEMS) break
            }
            out
        }
    } catch (e: Exception) {
        emptyList()
    }
}
