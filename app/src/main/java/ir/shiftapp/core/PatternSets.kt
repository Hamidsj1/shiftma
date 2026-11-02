package ir.shiftapp.core

/**
 * «مجموعهٔ الگو»: چند الگو (مثلاً گروه‌های A تا D یک الگوی چهارشیفته) که در فهرست یک مجموعه نشان داده می‌شوند، نه چند کارت جدا.
 * ستون `setName` در جدول الگوها؛ الگوهای قدیمی (setName خالی) که نامشان یک حرف گروه است (A، «شیفت D»، …) به‌صورت پیش‌فرض یک مجموعه حساب می‌شوند،
 * پس بدون دست‌زدن به داده‌ها هم فهرست جمع‌وجور می‌شود. منطق خالص و تست‌شده.
 */
object PatternSets {
    const val DEFAULT_SET = "گروه‌های شیفت"

    /** حرف گروه (A..H) از نامی مثل «A» یا «شیفت D»؛ وگرنه null. */
    fun letterOf(name: String): Char? {
        val t = name.trim().removePrefix("شیفت").trim()
        return if (t.length == 1 && t[0] in 'A'..'H') t[0] else null
    }

    /** کلید مجموعه؛ رشتهٔ خالی = الگوی مستقل. */
    fun keyOf(name: String, setName: String): String =
        setName.trim().ifEmpty { if (letterOf(name) != null) DEFAULT_SET else "" }

    /** گروه‌بندی با حفظ ترتیب اولین دیده‌شدن؛ هر مورد با کلید خالی یک گروه تکی است. */
    fun <T> group(items: List<T>, key: (T) -> String): List<List<T>> {
        val out = ArrayList<MutableList<T>>()
        val index = HashMap<String, MutableList<T>>()
        for (it in items) {
            val k = key(it)
            if (k.isEmpty()) { out.add(mutableListOf(it)); continue }
            val g = index[k]
            if (g != null) g.add(it) else { val n = mutableListOf(it); index[k] = n; out.add(n) }
        }
        return out
    }
}
