package ir.shiftapp.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CheckItem(val text: String, val done: Boolean = false)

/**
 * یادداشت: متن، چک‌لیست یا یادآور. هر یادداشت می‌تواند یادآور (تاریخ/ساعت + تکرار) داشته باشد.
 * reminderAt = لحظهٔ مطلقِ یادآورِ بعدی (میلی‌ثانیه)؛ null یعنی یادآور ندارد یا انجام شده.
 */
data class Note(
    val id: Long,
    val type: String = TYPE_TEXT,
    val title: String = "",
    val body: String = "",
    val items: List<CheckItem> = emptyList(),
    val color: Int = 0,                 // ۰ = بدون رنگ، ۱..۴ = رنگ‌ها
    val pinned: Boolean = false,
    val reminderAt: Long? = null,
    val repeat: String = REPEAT_NONE,
    val alarm: Boolean = false,         // true = با صدای آلارم، false = اعلان معمولی
    val updatedAt: Long = 0L
) {
    /** عنوان نمایشی: عنوان، وگرنه اولین خط متن، وگرنه اولین مورد چک‌لیست. */
    val displayTitle: String get() = title.ifBlank {
        body.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: items.firstOrNull()?.text?.take(40) ?: "بدون عنوان"
    }
    val isBlank: Boolean get() = title.isBlank() && body.isBlank() && items.none { it.text.isNotBlank() }
    val doneCount: Int get() = items.count { it.done }

    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_CHECK = "CHECK"
        const val TYPE_REMIND = "REMIND"
        const val REPEAT_NONE = "NONE"
        const val REPEAT_DAILY = "DAILY"
        const val REPEAT_WEEKLY = "WEEKLY"
        const val REPEAT_MONTHLY = "MONTHLY"
        val TYPES = listOf(TYPE_TEXT, TYPE_CHECK, TYPE_REMIND)
        val REPEATS = listOf(REPEAT_NONE, REPEAT_DAILY, REPEAT_WEEKLY, REPEAT_MONTHLY)
        const val MAX_NOTES = 500

        // جداکننده‌ها: رکورد ۰x1E، فیلد ۰x1F، آیتم چک‌لیست ۰x1D، فیلد آیتم ۰x1C؛ با درصد-کدگذاری امن می‌شوند.
        private fun esc(s: String) = s.replace("%", "%25").replace("\u001f", "%1F").replace("\u001e", "%1E").replace("\u001d", "%1D").replace("\u001c", "%1C")
        private fun unesc(s: String) = s.replace("%1F", "\u001f").replace("%1E", "\u001e").replace("%1D", "\u001d").replace("%1C", "\u001c").replace("%25", "%")

        fun encode(list: List<Note>): String = list.joinToString("\u001e") { n ->
            listOf(
                n.id.toString(), n.type, esc(n.title), esc(n.body),
                n.items.joinToString("\u001d") { (if (it.done) "1" else "0") + "\u001c" + esc(it.text) },
                n.color.toString(), n.pinned.toString(), (n.reminderAt ?: 0L).toString(), n.repeat, n.alarm.toString(), n.updatedAt.toString()
            ).joinToString("\u001f")
        }

        /** مقدار خراب نادیده گرفته می‌شود؛ هیچ‌وقت استثنا نمی‌دهد. */
        fun decode(s: String?): List<Note> {
            if (s.isNullOrBlank()) return emptyList()
            return s.split('\u001e').mapNotNull { rec ->
                val p = rec.split('\u001f')
                if (p.size < 11) return@mapNotNull null
                val id = p[0].toLongOrNull() ?: return@mapNotNull null
                val items = if (p[4].isEmpty()) emptyList() else p[4].split('\u001d').mapNotNull { it0 ->
                    val q = it0.split('\u001c', limit = 2)
                    if (q.size < 2) null else CheckItem(unesc(q[1]), q[0] == "1")
                }
                val at = p[7].toLongOrNull() ?: 0L
                Note(
                    id = id, type = p[1].takeIf { it in TYPES } ?: TYPE_TEXT, title = unesc(p[2]), body = unesc(p[3]), items = items,
                    color = p[5].toIntOrNull()?.coerceIn(0, 4) ?: 0, pinned = p[6].toBooleanStrictOrNull() ?: false,
                    reminderAt = at.takeIf { it > 0 }, repeat = p[8].takeIf { it in REPEATS } ?: REPEAT_NONE,
                    alarm = p[9].toBooleanStrictOrNull() ?: false, updatedAt = p[10].toLongOrNull() ?: 0L
                )
            }
        }

        fun nextId(list: List<Note>): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1L
    }
}

/** منطق خالص یادآور یادداشت‌ها (قابل‌تست، بدون اندروید). */
object NoteReminders {
    /**
     * نوبت بعدی یک یادآور تکرارشونده، اولین زمانِ بعد از [afterMs]، با همان ساعتِ روز در [zone].
     * ماهانه بر اساس ماه شمسی است (مثلاً «۲۰ام هر ماه»؛ اگر ماه ۳۰ روزه باشد روز به آخر ماه می‌رسد).
     * برای REPEAT_NONE null برمی‌گرداند.
     */
    fun nextOccurrence(atMs: Long, repeat: String, afterMs: Long, zone: ZoneId): Long? {
        if (repeat == Note.REPEAT_NONE) return null
        val start = Instant.ofEpochMilli(atMs).atZone(zone)
        val time = start.toLocalTime()
        var date = start.toLocalDate()
        var guard = 0
        fun ms(d: LocalDate) = d.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val baseDay = JalaliCalendar.fromLocalDate(date).day
        var cur = ms(date)
        while (cur <= afterMs && guard++ < 5000) {
            date = when (repeat) {
                Note.REPEAT_DAILY -> date.plusDays(1)
                Note.REPEAT_WEEKLY -> date.plusDays(7)
                else -> {
                    val j = JalaliCalendar.fromLocalDate(date)
                    val ny = if (j.month == 12) j.year + 1 else j.year
                    val nm = if (j.month == 12) 1 else j.month + 1
                    JalaliDate(ny, nm, minOf(baseDay, JalaliCalendar.daysInMonth(ny, nm))).toLocalDate()
                }
            }
            cur = ms(date)
        }
        return if (cur > afterMs) cur else null
    }

    /** بعد از شلیک: یادآور تکرارشونده به نوبت بعد می‌رود؛ یک‌باره خاموش (reminderAt = null) می‌شود. */
    fun afterFire(n: Note, nowMs: Long, zone: ZoneId): Note {
        val at = n.reminderAt ?: return n
        return n.copy(reminderAt = nextOccurrence(at, n.repeat, nowMs, zone))
    }

    /** یادآورهای تکرارشونده‌ای که زمانشان گذشته (مثلاً گوشی خاموش بوده) به نوبت بعد می‌روند. */
    fun catchUp(list: List<Note>, nowMs: Long, zone: ZoneId): List<Note> =
        list.map { n -> if (n.reminderAt != null && n.reminderAt <= nowMs && n.repeat != Note.REPEAT_NONE) afterFire(n, nowMs, zone) else n }

    /** متن اعلان یادآور. */
    fun body(n: Note): String = when {
        n.items.isNotEmpty() && n.body.isBlank() ->
            n.items.filter { !it.done && it.text.isNotBlank() }.take(3).joinToString("، ") { it.text }.ifEmpty { "همهٔ موارد انجام شده" }
        n.body.isNotBlank() -> n.body.lineSequence().first { it.isNotBlank() }.take(80)
        else -> "یادآور"
    }
}
