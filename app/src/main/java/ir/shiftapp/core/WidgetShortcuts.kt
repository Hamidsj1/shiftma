package ir.shiftapp.core

/**
 * منطق خالص ردیف میان‌بر ویجت ۴×۲: تلفن (ثابت) + سامانه‌ها با ورق‌زدن.
 * قاعده: همان چیزی که در صفحهٔ اول پنهان است در ویجت هم پنهان است (تلفن فقط با دسترسی ویژه/فایل کاشی و وقتی پنل مخفی‌اش نکرده).
 */
object WidgetShortcuts {
    const val PER_PAGE = 4

    fun phoneVisible(special: Boolean, enabledSections: Set<String>, remoteVisible: Boolean): Boolean =
        (special || "phonebook" in enabledSections) && remoteVisible

    /** سامانه‌ها به ترتیب چیدمان صفحهٔ اول (شناسهٔ هر سامانه در چیدمان = آدرس آن). */
    fun sites(sitesRaw: String, layoutRaw: String): List<SiteTile> {
        val all = TilesPackFile.decodeSites(sitesRaw)
        if (all.isEmpty()) return all
        val order = HomeLayout.order(all.map { it.url }, HomeLayout.parse(layoutRaw)["links"])
        val byUrl = all.associateBy { it.url }
        return order.mapNotNull { byUrl[it] }
    }

    fun pageCount(n: Int): Int = if (n <= 0) 1 else (n + PER_PAGE - 1) / PER_PAGE
    fun clampPage(page: Int, n: Int): Int = page.coerceIn(0, pageCount(n) - 1)
    fun <T> pageItems(list: List<T>, page: Int): List<T> = list.drop(clampPage(page, list.size) * PER_PAGE).take(PER_PAGE)
    /** آیا ردیف اصلاً چیزی برای نمایش دارد؟ */
    fun rowVisible(phone: Boolean, siteCount: Int): Boolean = phone || siteCount > 0
}
