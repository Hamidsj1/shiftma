package ir.shiftapp.core

import ir.shiftapp.sport.SportCodec
import ir.shiftapp.sport.SportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {
    @Test fun parseReps() {
        val p = WorkoutPlan.parse("۳×۱۵")
        assertEquals(3, p.sets); assertEquals(15, p.reps); assertEquals(0, p.seconds)
        val r = WorkoutPlan.parse("۳×۸ تا ۱۲")
        assertEquals(8, r.reps)
    }

    @Test fun parseTimedAndMinutes() {
        val t = WorkoutPlan.parse("۳×۳۰ ثانیه")
        assertEquals(3, t.sets); assertEquals(30, t.seconds); assertEquals(0, t.reps)
        val m = WorkoutPlan.parse("۳۰ تا ۴۵ دقیقه")
        assertEquals(1, m.sets); assertEquals(1800, m.seconds)
        val rounds = WorkoutPlan.parse("۸ دور")
        assertEquals(8, rounds.sets); assertEquals(30, rounds.seconds)
    }

    @Test fun parsePerSideAndFallback() {
        assertTrue(WorkoutPlan.parse("۳×۱۰ برای هر پا").perSide)
        val f = WorkoutPlan.parse("نامعلوم")
        assertEquals(3, f.sets); assertEquals(10, f.reps)
    }

    @Test fun everyProgramDayBuildsSteps() {
        SportData.PROGRAMS.forEach { p ->
            p.days.forEach { d ->
                val steps = WorkoutPlan.fromDay(d)
                assertEquals("${p.id}/${d.title}", d.items.size, steps.size)
                assertTrue(WorkoutPlan.estimateMinutes(steps) in 1..120)
            }
        }
    }

    @Test fun phasesAlternateAndEndWithWork() {
        val steps = WorkoutPlan.fromDay(SportData.PROGRAMS[0].days[0])
        val ph = WorkoutPlan.phases(steps)
        assertEquals(PhaseKind.READY, ph.first().kind)
        assertEquals(PhaseKind.WORK, ph.last().kind)
        assertEquals(steps.sumOf { it.sets }, ph.count { it.kind == PhaseKind.WORK })
        assertEquals(steps.sumOf { it.sets } - 1, ph.count { it.kind == PhaseKind.REST })
    }

    @Test fun unitsAndLogValue() {
        assertEquals(WorkoutPlan.UNIT_SEC, WorkoutPlan.unitOf(SportData.exercise("plank")))
        assertEquals(WorkoutPlan.UNIT_MIN, WorkoutPlan.unitOf(SportData.exercise("walk")))
        assertEquals(WorkoutPlan.UNIT_REPS, WorkoutPlan.unitOf(SportData.exercise("pushup")))
        val st = WorkoutPlan.step("walk", "۳۰ تا ۴۵ دقیقه")
        assertNotNull(st)
        assertEquals(30, WorkoutPlan.logValue(st!!, SportData.exercise("walk"), 1800))
    }

    @Test fun programDayRoundTrip() {
        val s = SportState(activeProgram = "ppl", programDay = 2)
        val back = SportCodec.decodeStr(SportCodec.encodeStr(s))
        assertEquals(2, back.programDay)
        assertEquals("ppl", back.activeProgram)
    }
}
