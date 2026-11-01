package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FeaturesTest {
    private val base = JalaliDate(1400, 1, 1)           // = 2021-03-21 (یکشنبه)
    private val pattern = ShiftPattern.of(listOf(1, 2, 3, 4))
    private val noHolidays = HolidayCalendar(emptyMap(), false)
    private val cal = HolidayCalendar(emptyMap(), true)
    private val utc: ZoneId = ZoneOffset.UTC

    // ---------- تاریخ ----------
    @Test fun jalaliToLocalDate() {
        assertEquals(java.time.LocalDate.of(2021, 3, 21), base.toLocalDate())
        assertEquals(base, JalaliCalendar.fromLocalDate(base.toLocalDate()))
    }

    // ---------- تنظیمات ----------
    @Test fun prefsRoundTripAndDefaults() {
        assertEquals(Prefs(), Prefs.from(emptyMap()))
        val p = Prefs(theme = "dark", reminderEnabled = true, reminderLeadMin = 90,
            notes = listOf(Note(3, Note.TYPE_CHECK, "t", "b", listOf(CheckItem("x", true)), 1, true, 5L, Note.REPEAT_DAILY, true, 7L)),
            alarms = listOf(SingleAlarm(1, 1_700_000_000_000L, "دکتر"), SingleAlarm(2, 1_700_000_100_000L, kind = SingleAlarm.KIND_SUB_OUT, enabled = false)),
            subOutMin = 1430, subInMin = 10, reminderCodes = setOf(3), leaveCapDays = 30, fridayOff = false, autoBackup = true, autoBackupKeep = 3,
            shiftTimes = Prefs.DEFAULT_TIMES + (5 to ShiftTime(480, 1020)))
        assertEquals(p, Prefs.from(p.toMap()))
    }
    @Test fun prefsIgnoreCorruptValues() {
        val p = Prefs.from(mapOf(PrefKeys.THEME to "neon", PrefKeys.time(1) to "abc", PrefKeys.time(2) to "600-600", PrefKeys.LEAVE_CAP to "-4"))
        assertEquals("system", p.theme)
        assertEquals(Prefs.DEFAULT_TIMES[1], p.shiftTimes[1]); assertEquals(Prefs.DEFAULT_TIMES[2], p.shiftTimes[2])
        assertEquals(26, p.leaveCapDays)
    }
    @Test fun defaultDayAndNightTimes() {
        assertEquals(ShiftTime(7 * 60, 19 * 60), Prefs.DEFAULT_TIMES[1])    // روزکار ۰۷:۰۰ تا ۱۹:۰۰
        assertEquals(ShiftTime(19 * 60, 7 * 60), Prefs.DEFAULT_TIMES[3])    // شبکار ۱۹:۰۰ تا ۰۷:۰۰
        assertTrue(Prefs.DEFAULT_TIMES[3]!!.crossesMidnight); assertEquals(720, Prefs.DEFAULT_TIMES[3]!!.durationMin)
    }
    @Test fun shiftTimeDuration() {
        assertEquals(480, ShiftTime(360, 840).durationMin)
        assertEquals(480, ShiftTime(1320, 360).durationMin)      // شبکار از نیمه‌شب می‌گذرد
        assertTrue(ShiftTime(1320, 360).crossesMidnight)
    }
    @Test fun clockParsing() {
        assertEquals(21 * 60 + 5, ShiftTime.parseClock("۲۱:۰۵")); assertEquals(0, ShiftTime.parseClock("00:00"))
        assertNull(ShiftTime.parseClock("24:00")); assertNull(ShiftTime.parseClock("12:60")); assertNull(ShiftTime.parseClock("abc"))
    }

    // ---------- تعطیلات ----------
    @Test fun holidayKinds() {
        assertEquals(DayKind.OFFICIAL, cal.kind(JalaliDate(1400, 1, 1)))
        assertEquals(DayKind.FRIDAY, cal.kind(JalaliDate(1400, 1, 6)))       // 2021-03-26 جمعه
        assertEquals(DayKind.NORMAL, cal.kind(JalaliDate(1400, 1, 7)))
        assertEquals(DayKind.NORMAL, noHolidays.kind(JalaliDate(1400, 1, 6)))
        assertEquals(DayKind.OFFICIAL, cal.kind(JalaliDate(1400, 1, 13)))     // جمعه و تعطیل رسمی => رسمی
    }
    @Test fun customHoliday() {
        val c = HolidayCalendar(mapOf("14000220" to "عاشورا"), false)
        assertEquals("عاشورا", c.title(JalaliDate(1400, 2, 20))); assertEquals(DayKind.OFFICIAL, c.kind(JalaliDate(1400, 2, 20)))
    }

    // ---------- ساعت کار ----------
    @Test fun monthHoursAndHolidayWork() {
        val r = HoursCalculator.month(1400, 1, base, pattern, emptyMap(), Prefs.DEFAULT_TIMES, cal)
        assertEquals(8, r.daysByShift[1]); assertEquals(8, r.daysByShift[2]); assertEquals(8, r.daysByShift[3])
        assertEquals(8 * 720 + 8 * 480 + 8 * 720, r.totalMinutes)
        // تعطیل رسمی با شیفت کاری: ۱(روزکار) ۲(عصرکار) ۳(شبکار) و ۱۳ (روزکار)؛ ۴ و ۱۲ استراحت‌اند
        assertEquals(4, r.officialHolidayDays); assertEquals(720 + 480 + 720 + 720, r.officialHolidayMinutes)
        // جمعه‌های غیررسمی: ۶(عصرکار) و ۲۷(شبکار) کار؛ ۲۰ استراحت
        assertEquals(2, r.fridayDays); assertEquals(480 + 720, r.fridayMinutes)
        assertTrue(r.shiftsWithoutTime.isEmpty())
    }
    @Test fun leaveAndRestDaysHaveNoHours() {
        val e = mapOf(JalaliDate(1400, 1, 1).key to listOf(Entry(EntryType.LEAVE)))
        val r = HoursCalculator.month(1400, 1, base, pattern, e, Prefs.DEFAULT_TIMES, noHolidays)
        assertEquals(7, r.daysByShift[1]); assertEquals(8 * 720 + 8 * 480 + 8 * 720 - 720, r.totalMinutes)
    }
    @Test fun unconfiguredShiftIsReported() {
        val r = HoursCalculator.month(1400, 1, base, ShiftPattern.of(listOf(5)), emptyMap(), Prefs.DEFAULT_TIMES, noHolidays)
        assertEquals(0, r.totalMinutes); assertEquals(setOf(5), r.shiftsWithoutTime)
    }

    // ---------- مرخصی ----------
    @Test fun leaveBalanceCountsEachDayOnce() {
        val e = mapOf(
            JalaliDate(1400, 1, 1).key to listOf(Entry(EntryType.LEAVE), Entry(EntryType.LEAVE)),   // روزکار، دو ثبت = یک روز
            JalaliDate(1400, 1, 4).key to listOf(Entry(EntryType.LEAVE))                              // روز استراحت
        )
        val b = LeaveCalculator.year(1400, 26, base, pattern, e, countRestDays = false)
        assertEquals(1, b.usedDays); assertEquals(25, b.remainingDays); assertFalse(b.exceeded)
        assertEquals(2, LeaveCalculator.year(1400, 26, base, pattern, e, countRestDays = true).usedDays)
        assertTrue(LeaveCalculator.year(1400, 0, base, pattern, e, false).exceeded)
    }
    @Test fun leaveOnlyThatYear() {
        val e = mapOf(JalaliDate(1401, 1, 1).key to listOf(Entry(EntryType.LEAVE)))
        assertEquals(0, LeaveCalculator.year(1400, 26, base, pattern, e, true).usedDays)
    }

    // ---------- روز مشترک ----------
    @Test fun commonDays() {
        val a = FourShiftColumn(1, "A", "", base, pattern)
        val b = FourShiftColumn(2, "B", "", base, ShiftPattern.of(listOf(1, 4)))
        val r = CommonDaysFinder.find(1400, 1, a, b)
        // روز۱: (۱،۱) هر دو کار با شیفت یکسان؛ روز۲: (۲،۴) فقط A؛ روز۳: (۳،۱) هر دو کار؛ روز۴: (۴،۴) هر دو استراحت
        fun days(l: List<JalaliDate>) = l.map { it.day }
        assertTrue(1 in days(r.bothWork)); assertTrue(1 in days(r.bothSameShift))
        assertTrue(3 in days(r.bothWork)); assertFalse(3 in days(r.bothSameShift))
        assertTrue(2 in days(r.onlyA)); assertTrue(4 in days(r.bothOff))
        assertEquals(31, r.bothWork.size + r.bothOff.size + r.onlyA.size + r.onlyB.size)
    }

    // ---------- یادآور ----------
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun leaveDaySkipsReminder() {
        val prefs = Prefs(reminderEnabled = true, reminderLeadMin = 60)
        val e = mapOf(JalaliDate(1400, 1, 2).key to listOf(Entry(EntryType.LEAVE)))
        val r = ReminderPlanner.next(ms(2021, 3, 21, 10, 0), utc, base, pattern, e, prefs)!!
        assertEquals(JalaliDate(1400, 1, 3), r.date); assertTrue(r.body.contains("شبکار"))
    }
    @Test fun restDayAndUnselectedShiftsGetNoReminder() {
        val prefs = Prefs(reminderEnabled = true, reminderCodes = setOf(3))
        val r = ReminderPlanner.next(ms(2021, 3, 21, 10, 0), utc, base, pattern, emptyMap(), prefs)!!
        assertEquals(JalaliDate(1400, 1, 3), r.date)
    }
    @Test fun leadReminderPicksEarliestAcrossShifts() {
        val prefs = Prefs(reminderEnabled = true, reminderLeadMin = 180)
        val r = ReminderPlanner.next(ms(2021, 3, 21, 0, 0), utc, base, pattern, emptyMap(), prefs)!!
        assertEquals(ms(2021, 3, 21, 4, 0), r.triggerAtMs)       // روزکار ۰۷:۰۰ منهای ۳ ساعت
        assertTrue(r.body.contains("۰۷:۰۰"))
    }
    @Test fun pastTriggersAreSkipped() {
        val prefs = Prefs(reminderEnabled = true, reminderLeadMin = 180)
        val r = ReminderPlanner.next(ms(2021, 3, 21, 5, 0), utc, base, pattern, emptyMap(), prefs)!!
        assertEquals(ms(2021, 3, 22, 11, 0), r.triggerAtMs)      // عصرکار ۱/۲ ساعت ۱۴:۰۰ منهای ۳ ساعت
    }
    @Test fun oldEveningPrefsAreIgnored() {
        val p = Prefs.from(mapOf("pref_rem_mode" to "EVENING_BEFORE", "pref_rem_evening" to "1260", PrefKeys.REM_LEAD to "45"))
        assertEquals(45, p.reminderLeadMin)
    }
    @Test fun leadDefaultIsThirtyMinutes() { assertEquals(30, Prefs().reminderLeadMin) }
    @Test fun singleAlarmWinsWhenEarlierAndWorksWithReminderOff() {
        val now = ms(2021, 3, 21, 10, 0)
        val alarms = listOf(SingleAlarm(5, ms(2021, 3, 21, 12, 30), "دکتر"))
        val off = ReminderPlanner.next(now, utc, base, pattern, emptyMap(), Prefs(reminderEnabled = false, alarms = alarms))!!
        assertEquals(5L, off.alarmId); assertTrue(off.isSingle); assertEquals("دکتر", off.title)
        val on = ReminderPlanner.next(now, utc, base, pattern, emptyMap(), Prefs(reminderEnabled = true, reminderLeadMin = 0, alarms = alarms))!!
        assertEquals(5L, on.alarmId)                       // ۱۲:۳۰ زودتر از شروع عصرکار فردا
        val later = listOf(SingleAlarm(6, ms(2021, 3, 25, 12, 30), "x"))
        val shift = ReminderPlanner.next(now, utc, base, pattern, emptyMap(), Prefs(reminderEnabled = true, reminderLeadMin = 0, alarms = later))!!
        assertFalse(shift.isSingle)
    }
    @Test fun disabledOrPastSingleAlarmsAreIgnored() {
        val now = ms(2021, 3, 21, 10, 0)
        val alarms = listOf(SingleAlarm(1, ms(2021, 3, 21, 9, 0)), SingleAlarm(2, ms(2021, 3, 22, 9, 0), enabled = false))
        assertNull(ReminderPlanner.next(now, utc, null, null, emptyMap(), Prefs(alarms = alarms)))
    }
    @Test fun alarmEncodingRoundTripAndCorruption() {
        val l = listOf(SingleAlarm(1, 100, "a b"), SingleAlarm(2, 200, "", SingleAlarm.KIND_SUB_IN, false))
        assertEquals(l, SingleAlarm.decode(SingleAlarm.encode(l)))
        assertEquals(emptyList<SingleAlarm>(), SingleAlarm.decode("garbage"))
        assertEquals(emptyList<SingleAlarm>(), SingleAlarm.decode(null))
        assertEquals(3L, SingleAlarm.nextId(l + SingleAlarm(2, 1)))
    }
    @Test fun subNightPlansTwoAlarmsAcrossMidnight() {
        val night = java.time.LocalDate.of(2021, 3, 21)
        val l = SubNight.plan(ms(2021, 3, 21, 18, 0), utc, night, 23 * 60 + 55, 5, 10)
        assertEquals(2, l.size)
        assertEquals(ms(2021, 3, 21, 23, 55), l[0].triggerAtMs); assertEquals(SingleAlarm.KIND_SUB_OUT, l[0].kind)
        assertEquals(ms(2021, 3, 22, 0, 5), l[1].triggerAtMs); assertEquals(SingleAlarm.KIND_SUB_IN, l[1].kind)
        // ساعت ۲۳:۵۸: فقط ورود مجدد مانده
        assertEquals(1, SubNight.plan(ms(2021, 3, 21, 23, 58), utc, night, 23 * 60 + 55, 5, 10).size)
        assertEquals(0, SubNight.plan(ms(2021, 3, 22, 1, 0), utc, night, 23 * 60 + 55, 5, 10).size)
        assertEquals(night, SubNight.nightOf(l, ms(2021, 3, 21, 18, 0), utc))
        assertEquals(night, SubNight.nightOf(l.drop(1), ms(2021, 3, 22, 0, 0), utc))
        assertNull(SubNight.nightOf(l, ms(2021, 3, 22, 1, 0), utc))
        assertEquals("ثبت خروج قبل از نیمه‌شب", l[0].title)
    }
    // ---------- یادداشت‌ها ----------
    @Test fun noteEncodingRoundTripWithTrickyText() {
        val n = listOf(
            Note(1, Note.TYPE_CHECK, "خرید %25 و\u001f", "خط۱\nخط۲\u001e", listOf(CheckItem("برنج", true), CheckItem("روغن\u001d%1D")), 2, true, 123456L, Note.REPEAT_WEEKLY, true, 99L),
            Note(2, Note.TYPE_TEXT, "", "", emptyList(), 0, false, null, Note.REPEAT_NONE, false, 0L)
        )
        assertEquals(n, Note.decode(Note.encode(n)))
        assertEquals(emptyList<Note>(), Note.decode("garbage"))
        assertEquals(3L, Note.nextId(n + Note(2)))
        assertEquals("برنج", n[0].items[0].text); assertEquals(1, n[0].doneCount)
        assertTrue(n[1].isBlank); assertEquals("بدون عنوان", n[1].displayTitle)
    }
    @Test fun repeatingNoteMovesToNextOccurrence() {
        val at = ms(2021, 3, 21, 18, 0)
        assertNull(NoteReminders.nextOccurrence(at, Note.REPEAT_NONE, at, utc))
        assertEquals(ms(2021, 3, 22, 18, 0), NoteReminders.nextOccurrence(at, Note.REPEAT_DAILY, at, utc))
        assertEquals(ms(2021, 3, 24, 18, 0), NoteReminders.nextOccurrence(at, Note.REPEAT_DAILY, ms(2021, 3, 23, 18, 0), utc))
        assertEquals(ms(2021, 3, 28, 18, 0), NoteReminders.nextOccurrence(at, Note.REPEAT_WEEKLY, at, utc))
        // ماهانه شمسی: ۱ فروردین ۱۴۰۰ → ۱ اردیبهشت = ۱۱ آوریل ۲۰۲۱
        assertEquals(ms(2021, 4, 21, 18, 0), NoteReminders.nextOccurrence(at, Note.REPEAT_MONTHLY, at, utc))
    }
    @Test fun monthlyClampsDayAtMonthEnd() {
        val j = JalaliDate(1400, 6, 31)   // شهریور ۳۱ روزه است؛ مهر ۳۰ روزه
        val at = j.toLocalDate().atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val next = NoteReminders.nextOccurrence(at, Note.REPEAT_MONTHLY, at, utc)!!
        val d = JalaliCalendar.fromLocalDate(java.time.Instant.ofEpochMilli(next).atZone(utc).toLocalDate())
        assertEquals(JalaliDate(1400, 7, 30), d)
    }
    @Test fun afterFireAndCatchUp() {
        val now = ms(2021, 3, 21, 18, 0)
        val once = Note(1, reminderAt = now, repeat = Note.REPEAT_NONE)
        assertNull(NoteReminders.afterFire(once, now, utc).reminderAt)
        val daily = Note(2, reminderAt = ms(2021, 3, 18, 18, 0), repeat = Note.REPEAT_DAILY)
        assertEquals(ms(2021, 3, 22, 18, 0), NoteReminders.catchUp(listOf(daily), now, utc)[0].reminderAt)
        assertEquals(once, NoteReminders.catchUp(listOf(once), now - 1, utc)[0])
    }
    @Test fun noteReminderCompetesWithShiftAndAlarms() {
        val now = ms(2021, 3, 21, 10, 0)
        val notes = listOf(Note(7, Note.TYPE_REMIND, "قسط", "پرداخت", reminderAt = ms(2021, 3, 21, 11, 0), alarm = true), Note(8, reminderAt = null))
        val r = ReminderPlanner.next(now, utc, null, null, emptyMap(), Prefs(notes = notes))!!
        assertTrue(r.isNote); assertEquals(7L, r.noteId); assertEquals("قسط", r.title); assertEquals(true, r.alarm)
        val early = ReminderPlanner.next(now, utc, null, null, emptyMap(), Prefs(notes = notes, alarms = listOf(SingleAlarm(1, ms(2021, 3, 21, 10, 30)))))!!
        assertTrue(early.isSingle)
        assertEquals("پرداخت", NoteReminders.body(notes[0]))
        assertEquals("روغن، چای", NoteReminders.body(Note(9, items = listOf(CheckItem("برنج", true), CheckItem("روغن"), CheckItem("چای")))))
    }
    @Test fun noCodesMeansNoReminder() {
        assertNull(ReminderPlanner.next(ms(2021, 3, 21, 0, 0), utc, base, pattern, emptyMap(), Prefs(reminderCodes = emptySet())))
    }

    // ---------- مدل خروجی ----------
    @Test fun cardexModelShape() {
        val t = ExportModels.cardex(1400, 1, "A", base, pattern, emptyMap(), cal, Prefs.DEFAULT_TIMES)
        assertEquals(31, t.rows.size)
        assertTrue(t.rows.all { it.size == t.headers.size }); assertEquals(t.headers.size, t.weights.size)
        assertTrue(t.rows[0][3].text.contains("تعطیل"))          // ۱/۱ جشن نوروز
        assertTrue(t.footer.first().contains(Fa.hm(8 * 720 + 8 * 480 + 8 * 720)))
    }
    @Test fun fourShiftModelDropsEmptyColumns() {
        val cols = listOf(FourShiftColumn(1, "A", "ش", base, pattern), null, FourShiftColumn(2, "B", "", base, pattern), null)
        val t = ExportModels.fourShift(1400, 1, cols, cal)
        assertEquals(4, t.headers.size); assertTrue(t.rows.all { it.size == 4 })
    }
}
