package ir.shiftapp.system

import android.content.Context
import ir.shiftapp.core.TilesPack
import ir.shiftapp.core.TilesPackFile

/**
 * کاشی‌های پیش‌فرضِ داخل برنامه: اگر فایل `assets/default_tiles.shiftma` (همان قالب «فایل کاشی‌ها») وجود داشته باشد،
 * سامانه‌هایش یک‌بار در اولین اجرا اضافه می‌شوند و بخش‌های `sections`اش همیشه نمایان‌اند.
 * در حال حاضر این فایل عمداً وجود ندارد: هیچ سامانه‌ای پیش‌فرض نیست و دفترچه تلفن و برنامه غذایی پنهان‌اند؛
 * همه فقط با وارد کردن یک فایل .shiftma (type = tiles) اضافه یا نمایان می‌شوند (pack = null یعنی بدون پیش‌فرض).
 */
object DefaultTiles {
    const val ASSET = "default_tiles.shiftma"
    const val SEED_PREFS = "shiftma_seed"
    const val SEED_KEY = "tiles_v1"

    @Volatile private var cached: TilesPack? = null

    /** null اگر فایل نبود یا خراب بود. */
    fun pack(ctx: Context): TilesPack? = cached ?: runCatching {
        ctx.applicationContext.assets.open(ASSET).use { TilesPackFile.fromJson(it.readBytes().toString(Charsets.UTF_8)) }
    }.getOrNull().also { cached = it }

    fun sections(ctx: Context): Set<String> = pack(ctx)?.sections.orEmpty()

    fun seeded(ctx: Context) = ctx.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE).getBoolean(SEED_KEY, false)
    fun markSeeded(ctx: Context) { ctx.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEED_KEY, true).apply() }
}
