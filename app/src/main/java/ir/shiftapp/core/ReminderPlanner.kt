package ir.shiftapp.core

import java.time.Instant
import java.time.ZoneId

/** alarmId ≠ 0 یعنی آلارم تکی (همیشه با صدای آلارم)؛ ۰ یعنی یادآور شیفت. */
data class Reminder(
    val triggerAtMs: Long, val title: String, val body: String, val date: JalaliDate, val code: Int,
    val alarmId: Long = 0L,
    val noteId: Long = 0L,
    val alarm: Boolean? = null,         // null = طبق تنظیم «نوع هشدار» شیفت‌ها
    val prayerId: String? = null        // ≠ null یعنی هشدار اذان (شناسهٔ [Prayer])
) {
    val isPrayer: Boolean get() = prayerId != null
    val isSingle: Boolean get() = alarmId != 0L
    val isNote: Boolean get() = noteId != 0L
}

/**
 * منطق خالص یادآور: «نزدیک‌ترین یادآورِ بعد از اکنون» را برمی‌گرداند.
 * دو منبع: یادآور شیفت (چند دقیقه قبل از شروع شیفت) و آلارم‌های تکی (از جمله جانشین شب).
 * روز مرخصی و شیفت‌های انتخاب‌نشده یادآور ندارند. شیفت تعویضی/دستی با کد مؤثر حساب می‌شود.
 */
object ReminderPlanner {
    fun next(
        nowMs: Long, zone: ZoneId, base: JalaliDate?, pattern: ShiftPattern?,
        entries: Map<String, List<Entry>>, prefs: Prefs, horizonDays: Int = 45
    ): Reminder? {
        val shift = if (prefs.reminderEnabled && base != null && pattern != null)
            nextShift(nowMs, zone, base, pattern, entries, prefs, horizonDays) else null
        val single = nextSingle(nowMs, zone, prefs.alarms)
        val note = nextNote(nowMs, zone, prefs.notes)
        val prayer = nextPrayer(nowMs, zone, prefs)
        return listOfNotNull(shift, single, note, prayer).minByOrNull { it.triggerAtMs }
    }

    /**
     * نزدیک‌ترین هشدار اذان (اختیاری؛ فقط وقتی «امکانات مذهبی» روشن است و کاربر برای وقتی هشدار گذاشته).
     * هشدارهایی که داخل «ساعت سکوت» می‌افتند (مثلاً خواب بعد از شبکاری) رد می‌شوند.
     */
    fun nextPrayer(nowMs: Long, zone: ZoneId, prefs: Prefs, horizonDays: Int = 3): Reminder? {
        if (!prefs.prayerOn || prefs.prayerAlerts.isEmpty()) return null
        val place = prefs.prayerPlace
        val method = prefs.prayerMethod
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Reminder? = null
        for (i in -1..horizonDays) {
            val date = today.plusDays(i.toLong())
            val times = PrayerCalc.times(date, place.lat, place.lon, zone, method)
            for (p in method.shown) {
                if (p.id !in prefs.prayerAlerts) continue
                val min = times.minutes[p] ?: continue
                val prayerMs = SingleAlarm.at(date, min, zone)
                val trig = prayerMs - prefs.prayerLeadMin * 60_000L
                if (trig <= nowMs) continue
                if (prefs.prayerQuietOn && PrayerRules.inWindow(PrayerRules.localMinute(trig, zone), prefs.prayerQuietStart, prefs.prayerQuietEnd)) continue
                val cur = best
                if (cur != null && trig >= cur.triggerAtMs) continue
                val clock = Fa.digits(ShiftTime.fmt(min % 1440))
                val body = (if (prefs.prayerLeadMin > 0) "${Fa.num(prefs.prayerLeadMin)} دقیقه به ${p.title} مانده؛ " else "") +
                    "ساعت $clock به افق ${place.name}"
                val d = JalaliCalendar.fromLocalDate(Instant.ofEpochMilli(trig).atZone(zone).toLocalDate())
                best = Reminder(trig, p.title, body, d, 0, alarm = prefs.prayerAlarm, prayerId = p.id)
            }
        }
        return best
    }

    /** نزدیک‌ترین یادآور یادداشت‌ها (فقط آن‌هایی که reminderAt آیندهٔ معتبر دارند). */
    fun nextNote(nowMs: Long, zone: ZoneId, notes: List<Note>): Reminder? {
        val n = notes.filter { (it.reminderAt ?: 0L) > nowMs }.minByOrNull { it.reminderAt!! } ?: return null
        val d = JalaliCalendar.fromLocalDate(Instant.ofEpochMilli(n.reminderAt!!).atZone(zone).toLocalDate())
        return Reminder(n.reminderAt, n.displayTitle, NoteReminders.body(n), d, 0, noteId = n.id, alarm = n.alarm)
    }

    fun nextSingle(nowMs: Long, zone: ZoneId, alarms: List<SingleAlarm>): Reminder? {
        val a = alarms.filter { it.enabled && it.triggerAtMs > nowMs }.minByOrNull { it.triggerAtMs } ?: return null
        val d = JalaliCalendar.fromLocalDate(Instant.ofEpochMilli(a.triggerAtMs).atZone(zone).toLocalDate())
        return Reminder(a.triggerAtMs, a.title, a.body, d, 0, alarmId = a.id, alarm = true)
    }

    private fun nextShift(
        nowMs: Long, zone: ZoneId, base: JalaliDate, pattern: ShiftPattern,
        entries: Map<String, List<Entry>>, prefs: Prefs, horizonDays: Int
    ): Reminder? {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val todayJ = JalaliCalendar.fromLocalDate(now.toLocalDate())
        var best: Reminder? = null
        for (i in 0..horizonDays) {
            val date = todayJ.plusDays(i)
            val r = DayResolver.resolve(base, pattern, date, entries[date.key].orEmpty())
            if (r.onLeave) continue
            val code = r.effectiveCode
            if (code !in prefs.reminderCodes) continue
            val t = prefs.shiftTimes[code] ?: continue
            val name = ShiftType.byCode(code)?.name ?: "شیفت ${Fa.num(code)}"
            val trigger = date.toLocalDate().atStartOfDay(zone).plusMinutes((t.startMin - prefs.reminderLeadMin).toLong())
            val ms = trigger.toInstant().toEpochMilli()
            if (ms <= nowMs) continue
            val cur = best
            if (cur == null || ms < cur.triggerAtMs)
                best = Reminder(ms, "یادآور شیفت", "$name ساعت ${Fa.digits(ShiftTime.fmt(t.startMin))} شروع می‌شود", date, code)
        }
        return best
    }
}
