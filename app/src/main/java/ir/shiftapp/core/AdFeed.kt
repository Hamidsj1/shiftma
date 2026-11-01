package ir.shiftapp.core

import org.json.JSONObject

/** یک تبلیغ پنل صفحهٔ اول: تصویر بنر + پیوندی که با لمس آن باز می‌شود. */
data class AdItem(val id: String, val imageUrl: String, val linkUrl: String, val title: String, val tile: String = "")

/**
 * فایل JSON پنل تبلیغاتی اختصاصی (بدون SDK). نمونه:
 * {"enabled":true,"items":[{"id":"a1","image":"https://site.ir/ad1.png","link":"https://site.ir/offer","title":"تخفیف ویژه"}]}
 * فقط نشانی‌های https پذیرفته می‌شوند؛ حداکثر ۵ تبلیغ؛ فایل خراب یا enabled=false یعنی «بدون تبلیغ».
 */
object AdFeed {
    const val MAX_ITEMS = 10

    /**
     * خوراک تبلیغ این کاربر: اگر از فایل کاشی‌های محل کارش آدرس تبلیغ (`adsFeed`، گیت‌هاب) دارد همان،
     * وگرنه خوراک عمومی برنامه ([default]).
     */
    fun pick(default: String, custom: String): String = if (MealFeed.isSafeUrl(custom)) custom else default

    private val TILE_RE = Regex("[a-z0-9_]{1,30}")

    /** خوراک‌هایی که این کاربر می‌بیند: خوراک محل کار (اگر امن بود) و، اگر [publicAds] روشن است یا محل کار خوراک ندارد، خوراک عمومی. */
    fun feeds(default: String, custom: String, publicAds: Boolean): List<String> {
        val own = isSafeUrl(custom) && MealFeed.isSafeUrl(custom)
        val list = ArrayList<String>()
        if (own) list.add(custom)
        if ((!own || publicAds) && isSafeUrl(default)) list.add(default)
        return list
    }

    /** تبلیغ‌های یک کاشی (tile برابر کلید کاشی)؛ [tile] خالی = تبلیغ‌های بدون کاشی، یعنی پنل صفحهٔ اول. */
    fun <T> forTile(items: List<T>, tile: String, tileOf: (T) -> String): List<T> = items.filter { tileOf(it) == tile }

    fun isSafeUrl(u: String): Boolean = u.length < 2000 && u.startsWith("https://", ignoreCase = true)

    fun parse(json: String): List<AdItem> {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("enabled", true)) return emptyList()
            val arr = root.optJSONArray("items") ?: return emptyList()
            val out = ArrayList<AdItem>()
            for (i in 0 until arr.length()) {
                val ob = arr.optJSONObject(i) ?: continue
                val image = ob.optString("image")
                val link = ob.optString("link")
                if (!isSafeUrl(image) || !isSafeUrl(link)) continue
                val tile = ob.optString("tile", "").trim().lowercase().let { if (TILE_RE.matches(it)) it else "" }
                out.add(AdItem(ob.optString("id", image), image, link, ob.optString("title"), tile))
                if (out.size >= MAX_ITEMS) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
