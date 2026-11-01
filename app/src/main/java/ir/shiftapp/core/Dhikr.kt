package ir.shiftapp.core

/*
 * ذکرشمار، نماز و روزهٔ قضا: منطق خالص (بدون وابستگی به اندروید و تست‌پذیر).
 * همهٔ وضعیت در یک رشتهٔ فشرده (کلید pref_dhikr) ذخیره می‌شود و با پشتیبان می‌آید.
 */

/** یک مرحله از ذکر (مثلاً «الله اکبر» ۳۴ بار). */
data class DhikrPhase(val text: String, val target: Int)

/** تعریف ثابت یک ذکر آماده. */
data class DhikrDef(val id: String, val title: String, val phases: List<DhikrPhase>)

/** ذکر دلخواه کاربر (یک مرحله با هدف مشخص). */
data class CustomDhikr(val id: String, val title: String, val target: Int)

/** ذکر آمادهٔ نمایش: تعریف + پیشرفت فعلی. */
data class DhikrCounter(val id: String, val title: String, val phases: List<DhikrPhase>, val count: Int, val custom: Boolean) {
    val total: Int get() = phases.sumOf { it.target }
    val done: Boolean get() = count >= total

    /** (شمارهٔ مرحله از صفر، شمارش داخل آن مرحله)؛ بعد از پایان، آخرین مرحلهٔ پُر. */
    val position: Pair<Int, Int>
        get() {
            var left = count
            phases.forEachIndexed { i, p ->
                if (left < p.target) return i to left
                left -= p.target
            }
            return phases.lastIndex to phases.last().target
        }
}

object DhikrPresets {
    const val DEFAULT_ID = "zahra"
    const val MAX_CUSTOM = 12
    const val MAX_TARGET = 100_000

    val ALL: List<DhikrDef> = listOf(
        DhikrDef(
            "zahra", "تسبیحات حضرت زهرا (س)",
            listOf(DhikrPhase("الله اکبر", 34), DhikrPhase("الحمدلله", 33), DhikrPhase("سبحان الله", 33))
        ),
        DhikrDef("salavat", "صلوات", listOf(DhikrPhase("اللهم صل علی محمد و آل محمد", 100))),
        DhikrDef("estighfar", "استغفار", listOf(DhikrPhase("استغفر الله ربی و اتوب الیه", 100))),
        DhikrDef("tahlil", "لا إله إلا الله", listOf(DhikrPhase("لا إله إلا الله", 100))),
        DhikrDef("hawqala", "لا حول و لا قوة إلا بالله", listOf(DhikrPhase("لا حول و لا قوة إلا بالله", 100))),
        DhikrDef("tasbih", "سبحان الله و بحمده", listOf(DhikrPhase("سبحان الله و بحمده", 100)))
    )
}

/** نمازها و روزهٔ قضا. */
object Qaza {
    const val MAX = 99_999
    val KEYS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha", "roza")

    /** پنج نمازِ یک «روز کامل». */
    val PRAYER_KEYS = KEYS.take(5)

    fun title(key: String): String = when (key) {
        "fajr" -> "نماز صبح"
        "dhuhr" -> "نماز ظهر"
        "asr" -> "نماز عصر"
        "maghrib" -> "نماز مغرب"
        "isha" -> "نماز عشا"
        "roza" -> "روزهٔ قضا"
        else -> key
    }
}

data class DhikrState(
    val selected: String = DhikrPresets.DEFAULT_ID,
    val counts: Map<String, Int> = emptyMap(),
    val custom: List<CustomDhikr> = emptyList(),
    val todayEpoch: Int = 0,
    val today: Int = 0,
    val lifetime: Long = 0L,
    val qaza: Map<String, Int> = emptyMap()
) {
    fun counters(): List<DhikrCounter> {
        val presets = DhikrPresets.ALL.map { d ->
            val total = d.phases.sumOf { it.target }
            DhikrCounter(d.id, d.title, d.phases, (counts[d.id] ?: 0).coerceIn(0, total), false)
        }
        val mine = custom.map { c ->
            DhikrCounter(c.id, c.title, listOf(DhikrPhase(c.title, c.target)), (counts[c.id] ?: 0).coerceIn(0, c.target), true)
        }
        return presets + mine
    }

    /** ذکر انتخاب‌شده؛ اگر شناسه ناشناخته بود اولین ذکر. */
    fun current(): DhikrCounter = counters().let { all -> all.firstOrNull { it.id == selected } ?: all.first() }

    /** شمار امروز (اگر روز عوض شده باشد صفر). */
    fun todayCount(epochDay: Int): Int = if (todayEpoch == epochDay) today else 0

    /** یک شمارش؛ بعد از پایان ذکر چیزی عوض نمی‌شود. */
    fun tap(epochDay: Int): DhikrState {
        val c = current()
        if (c.done) return this
        return copy(
            counts = counts + (c.id to c.count + 1),
            todayEpoch = epochDay, today = todayCount(epochDay) + 1, lifetime = lifetime + 1
        )
    }

    /** یک شمارش به عقب (اصلاح اشتباه)؛ آمار امروز و کل هم کم می‌شود. */
    fun undo(epochDay: Int): DhikrState {
        val c = current()
        if (c.count <= 0) return this
        return copy(
            counts = counts + (c.id to c.count - 1),
            todayEpoch = epochDay, today = (todayCount(epochDay) - 1).coerceAtLeast(0), lifetime = (lifetime - 1).coerceAtLeast(0L)
        )
    }

    fun reset(id: String): DhikrState = copy(counts = counts - id)

    fun select(id: String): DhikrState = if (counters().any { it.id == id }) copy(selected = id) else this

    /** ذکر دلخواه تازه؛ اگر عنوان خالی، هدف نامعتبر یا سقف پر باشد تغییری نمی‌کند. شناسهٔ آن انتخاب می‌شود. */
    fun addCustom(title: String, target: Int): DhikrState {
        val t = title.filter { it != '\u001d' && it != '\u001e' && it != '\u001f' }.trim().take(40)
        if (t.isEmpty() || target !in 1..DhikrPresets.MAX_TARGET || custom.size >= DhikrPresets.MAX_CUSTOM) return this
        val next = (custom.mapNotNull { it.id.removePrefix("c").toIntOrNull() }.maxOrNull() ?: 0) + 1
        val id = "c$next"
        return copy(custom = custom + CustomDhikr(id, t, target), selected = id)
    }

    fun removeCustom(id: String): DhikrState {
        if (custom.none { it.id == id }) return this
        return copy(
            custom = custom.filter { it.id != id }, counts = counts - id,
            selected = if (selected == id) DhikrPresets.DEFAULT_ID else selected
        )
    }

    // ───── قضا ─────
    fun qazaOf(key: String): Int = qaza[key] ?: 0

    fun qazaAdd(key: String, delta: Int): DhikrState =
        if (key !in Qaza.KEYS) this else copy(qaza = qaza + (key to (qazaOf(key) + delta).coerceIn(0, Qaza.MAX)))

    /** یک روز کامل (پنج نماز) اضافه یا کم می‌شود. */
    fun qazaDay(delta: Int): DhikrState = Qaza.PRAYER_KEYS.fold(this) { s, k -> s.qazaAdd(k, delta) }

    val qazaPrayerTotal: Int get() = Qaza.PRAYER_KEYS.sumOf { qazaOf(it) }
}

/** رشتهٔ فشرده برای ذخیره در تنظیمات؛ خواندنِ مقدار خراب هیچ‌وقت استثنا نمی‌دهد. */
object DhikrCodec {
    private const val GS = '\u001d'   // جداکنندهٔ بخش‌ها
    private const val RS = '\u001e'   // جداکنندهٔ رکوردها
    private const val FS = '\u001f'   // جداکنندهٔ فیلدها
    private const val VERSION = "1"

    fun encode(s: DhikrState): String = listOf(
        VERSION, s.selected, s.todayEpoch.toString(), s.today.toString(), s.lifetime.toString(),
        s.counts.entries.joinToString(RS.toString()) { "${it.key}$FS${it.value}" },
        s.custom.joinToString(RS.toString()) { "${it.id}$FS${it.title}$FS${it.target}" },
        s.qaza.entries.joinToString(RS.toString()) { "${it.key}$FS${it.value}" }
    ).joinToString(GS.toString())

    private fun records(s: String): List<List<String>> =
        if (s.isEmpty()) emptyList() else s.split(RS).map { it.split(FS) }

    fun decode(raw: String?): DhikrState {
        if (raw.isNullOrBlank()) return DhikrState()
        val p = raw.split(GS)
        if (p.size < 8 || p[0] != VERSION) return DhikrState()

        val custom = ArrayList<CustomDhikr>()
        for (f in records(p[6])) {
            if (f.size < 3 || f[0].isBlank() || f[1].isBlank()) continue
            val target = f[2].toIntOrNull() ?: continue
            if (target !in 1..DhikrPresets.MAX_TARGET || custom.size >= DhikrPresets.MAX_CUSTOM) continue
            custom.add(CustomDhikr(f[0], f[1].take(40), target))
        }
        val counts = LinkedHashMap<String, Int>()
        for (f in records(p[5])) {
            if (f.size < 2) continue
            val n = f[1].toIntOrNull() ?: continue
            counts[f[0]] = n.coerceIn(0, DhikrPresets.MAX_TARGET)
        }
        val qaza = LinkedHashMap<String, Int>()
        for (f in records(p[7])) {
            if (f.size < 2 || f[0] !in Qaza.KEYS) continue
            val n = f[1].toIntOrNull() ?: continue
            qaza[f[0]] = n.coerceIn(0, Qaza.MAX)
        }
        return DhikrState(
            selected = p[1].ifBlank { DhikrPresets.DEFAULT_ID },
            counts = counts, custom = custom,
            todayEpoch = p[2].toIntOrNull() ?: 0,
            today = (p[3].toIntOrNull() ?: 0).coerceAtLeast(0),
            lifetime = (p[4].toLongOrNull() ?: 0L).coerceAtLeast(0L),
            qaza = qaza
        )
    }
}
