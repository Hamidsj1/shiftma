package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlannerTest {
    private val z = ZoneId.of("Asia/Tehran")
    private val d0 = LocalDate.of(2026, 11, 3).toEpochDay().toInt()
    private fun pr(done: Boolean, a: Int = 0, b: Int = 0) = PlanPriority("کار", "", a, b, done)

    @Test fun weekEvalCountsAllDoneAndGap() {
        val full = PlanDay(d0, priorities = listOf(pr(true, 60, 90), pr(true, 30, 30), pr(true)))
        val part = PlanDay(d0 - 1, priorities = listOf(pr(true), pr(false), pr(true)))
        val e = Planner.weekEval(PlannerState(listOf(part, full)), d0)
        assertEquals(1, e.allDoneDays)
        assertEquals(15, e.avgGapMin)      // |60-90|=30 و 0 → میانگین ۱۵
        assertEquals(2, e.measured)
    }
    @Test fun weekEvalIgnoresOlderThanSevenDays() {
        val old = PlanDay(d0 - 7, priorities = listOf(pr(true), pr(true), pr(true)))
        assertEquals(0, Planner.weekEval(PlannerState(listOf(old)), d0).allDoneDays)
    }
    @Test fun nextAlarmPicksNearestUndoneAlarmRow() {
        val now = SingleAlarm.at(LocalDate.ofEpochDay(d0.toLong()), 8 * 60, z)
        val day = PlanDay(d0, rows = listOf(
            PlanRow(1, 7 * 60, "گذشته", alarm = true), PlanRow(2, 9 * 60, "انجام‌شده", done = true, alarm = true),
            PlanRow(3, 11 * 60, "جلسه", alarm = true), PlanRow(4, 10 * 60, "بدون آلارم")
        ))
        val a = Planner.nextAlarm(PlannerState(listOf(day)), now, z)!!
        assertEquals("جلسه", a.body)
        assertEquals(SingleAlarm.at(LocalDate.ofEpochDay(d0.toLong()), 11 * 60, z), a.triggerAtMs)
    }
    @Test fun nextAlarmOffWhenDisabled() {
        val day = PlanDay(d0, rows = listOf(PlanRow(1, 23 * 60, "x", alarm = true)))
        assertNull(Planner.nextAlarm(PlannerState(listOf(day), alarmsOn = false), 0L, z))
    }
    @Test fun reviewAlarmAfterRows() {
        val now = SingleAlarm.at(LocalDate.ofEpochDay(d0.toLong()), 20 * 60, z)
        val a = Planner.nextAlarm(PlannerState(reviewMin = 21 * 60 + 30), now, z)!!
        assertEquals("مرور شبانه", a.title)
    }
    @Test fun codecRoundTripAndGarbage() {
        val s = PlannerState(listOf(PlanDay(d0, 1, 480, listOf(pr(true, 10, 20), PlanPriority(), PlanPriority()),
            listOf(PlanRow(1, 600, "الف", alarm = true), PlanRow(2, 700, "بافر", buffer = true)),
            listOf(PlanReview("ب", 3, "دلیل"), PlanReview(), PlanReview()))), reviewMin = 1290)
        assertEquals(s, Planner.decode(Planner.encode(s)))
        assertEquals(PlannerState(), Planner.decode("{خراب"))
        assertEquals(PlannerState(), Planner.decode(""))
    }
    @Test fun carriedTasksComeFromTomorrowStatus() {
        val prev = PlanDay(d0 - 1, review = listOf(PlanReview("الف", 3), PlanReview("ب", 1), PlanReview("ج", 2)))
        assertEquals(listOf("الف"), Planner.carriedFrom(prev))
    }
}
