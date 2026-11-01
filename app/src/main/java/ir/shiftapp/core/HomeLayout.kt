package ir.shiftapp.core

/**
 * چیدمان دلخواه صفحهٔ اول: ترتیب کاشی‌های هر گروه و ترتیب خود گروه‌ها. منطق خالص و قابل تست.
 * ذخیره به‌شکل «کلید=شناسه,شناسه;کلید=…» (کلیدها: sections، links، blocks و v = نسخهٔ چیدمان).
 */
object HomeLayout {
    /**
     * گروه‌های صفحهٔ اول به ترتیب پیش‌فرض (زیر تقویم): سامانه‌ها، سایت‌های ضروری (درست زیر سامانه‌ها)، تلویزیون (زیر سایت‌های ضروری)، بخش‌ها، ویجت.
     * «ابزارها» دیگر گروه صفحهٔ اول نیست و داخل کشوی «بخش‌های دیگر» است.
     */
    val BLOCKS = listOf("links", "essential", "tv", "sections", "widget")

    /** شمارهٔ نسخهٔ چیدمان؛ با عوض شدنِ ترتیب پیش‌فرض بالا می‌رود تا چیدمان قدیمیِ ذخیره‌شده یک‌بار کنار برود. */
    const val VERSION_KEY = "v"
    const val VERSION = "4"

    /**
     * نسخهٔ ۲: ترتیب گروه‌ها و بخش‌ها عوض شد (سامانه‌ها اول، تلفن و فیش‌های من ردیف اول بخش‌ها) و گروه «ابزارها» حذف شد.
     * چیدمان قدیمیِ گروه‌ها و بخش‌ها یک‌بار پاک می‌شود؛ ترتیب کاشی‌های سامانه‌ها (links) دست‌نخورده می‌ماند.
     */
    fun migrate(m: Map<String, List<String>>): Map<String, List<String>> {
        val v = m[VERSION_KEY]?.firstOrNull()
        if (v == VERSION) return m
        // نسخهٔ ۳ و ۴: «سایت‌های ضروری» زیر «سامانه‌ها» و «تلویزیون» زیر آن آمد؛ فقط ترتیب گروه‌ها یک‌بار پاک می‌شود و ترتیب کاشی‌ها و بخش‌ها می‌ماند
        if (v == "2" || v == "3") return m.filterKeys { it != "blocks" && it != VERSION_KEY } + (VERSION_KEY to listOf(VERSION))
        return m.filterKeys { it != "blocks" && it != "sections" && it != "tools" && it != VERSION_KEY } + (VERSION_KEY to listOf(VERSION))
    }

    fun parse(raw: String): Map<String, List<String>> =
        raw.split(';').mapNotNull { part ->
            val k = part.substringBefore('=', "").trim()
            if (k.isEmpty() || !part.contains('=')) null
            else k to part.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }.toMap()

    fun encode(m: Map<String, List<String>>): String =
        m.filter { it.value.isNotEmpty() }.entries.joinToString(";") { (k, v) -> "$k=" + v.joinToString(",") }

    /**
     * ترتیب کامل: اول شناسه‌های ذخیره‌شده (فقط آن‌هایی که هنوز وجود دارند، بدون تکرار)،
     * بعد شناسه‌های تازه به ترتیب پیش‌فرض. پس با اضافه شدن بخش جدید در نسخهٔ بعد، چیدمان کاربر خراب نمی‌شود.
     */
    fun order(default: List<String>, saved: List<String>?): List<String> {
        val known = default.toSet()
        val head = saved.orEmpty().filter { it in known }.distinct()
        return head + default.filter { it !in head }
    }

    /** [id] را به اندازهٔ [delta] خانه در لیست جابه‌جا می‌کند (جای دو عنصر عوض می‌شود)؛ خارج از لیست = بدون تغییر. */
    fun <T> move(list: List<T>, id: T, delta: Int): List<T> {
        val i = list.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in list.indices) return list
        return list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    /**
     * جابه‌جایی فقط بین عناصر دیدنی: کاشی‌های پنهان (مثل مخفی‌ها یا اوقات شرعی خاموش) جای خود را در [full] نگه می‌دارند
     * تا وقتی دوباره نمایان شدند همان‌جای قبلی باشند.
     */
    fun moveVisible(full: List<String>, visible: List<String>, id: String, delta: Int): List<String> {
        val vis = full.filter { it in visible }
        val moved = move(vis, id, delta)
        if (moved == vis) return full
        val slots = full.indices.filter { full[it] in visible }
        val out = full.toMutableList()
        slots.forEachIndexed { k, pos -> out[pos] = moved[k] }
        return out
    }

    /** [id] را برمی‌دارد و در جای [to] می‌گذارد (بقیه یک خانه جابه‌جا می‌شوند)؛ مثل رها کردن بعد از کشیدن. */
    fun <T> moveTo(list: List<T>, id: T, to: Int): List<T> {
        val from = list.indexOf(id)
        if (from < 0 || to !in list.indices || to == from) return list
        return list.toMutableList().also { it.removeAt(from); it.add(to, id) }
    }

    /**
     * ترتیب تازهٔ عناصر دیدنی ([newVisible]) را در ترتیب کامل [full] اعمال می‌کند؛ عناصر پنهان جای خود را نگه می‌دارند.
     * اگر [newVisible] با عناصر دیدنی [full] هم‌مجموعه نباشد، [full] بدون تغییر برمی‌گردد.
     */
    fun reorderVisible(full: List<String>, newVisible: List<String>): List<String> {
        val set = newVisible.toSet()
        val slots = full.indices.filter { full[it] in set }
        if (slots.size != newVisible.size || slots.map { full[it] }.toSet() != set) return full
        val out = full.toMutableList()
        slots.forEachIndexed { k, pos -> out[pos] = newVisible[k] }
        return out
    }
}
