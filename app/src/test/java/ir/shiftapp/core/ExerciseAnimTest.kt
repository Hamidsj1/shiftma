package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ExerciseAnimTest {
    @Test fun everyAnimMatchesARealExercise() {
        ExerciseAnims.ALL.forEach { assertNotNull("حرکت ${it.id} در SportData نیست", SportData.exercise(it.id)) }
        assertEquals(ExerciseAnims.ALL.size, ExerciseAnims.ALL.map { it.id }.toSet().size)
    }

    @Test fun keysAreWellFormed() {
        ExerciseAnims.ALL.forEach { a ->
            assertTrue("${a.id}: حداقل دو ژست", a.keys.size >= 2)
            assertTrue("${a.id}: دورهٔ معقول", a.periodMs in 500..6000)
            a.keys.forEach { assertEquals(ExerciseAnims.POSE_SIZE, it.size) }
        }
    }

    @Test fun limbLengthsStayConstant() {
        fun d(a: Pt, b: Pt) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        ExerciseAnims.ALL.forEach { a ->
            for (i in 0..40) {
                val q = ExerciseAnims.pose(a, i / 40f)
                assertEquals(11.0, d(q.hip, q.kneeN), 0.05)
                assertEquals(11.0, d(q.kneeN, q.ankleN), 0.05)
                assertEquals(11.0, d(q.hip, q.kneeF), 0.05)
                assertEquals(8.0, d(q.shoulder, q.elbowN), 0.05)
                assertEquals(8.0, d(q.elbowN, q.handN), 0.05)
                assertEquals(8.0, d(q.shoulder, q.elbowF), 0.05)
            }
        }
    }

    @Test fun figureStaysOnCanvas() {
        ExerciseAnims.ALL.forEach { a ->
            for (i in 0..60) {
                val q = ExerciseAnims.pose(a, i / 60f)
                listOf(q.head, q.handN, q.handF, q.ankleN, q.ankleF, q.toeN, q.toeF, q.hip).forEach {
                    assertTrue("${a.id}: x=${it.x}", it.x in 0f..ExerciseAnims.W)
                    assertTrue("${a.id}: y=${it.y}", it.y in 0f..ExerciseAnims.FLOOR + 1f)
                }
            }
        }
    }

    @Test fun loopIsSeamless() {
        ExerciseAnims.ALL.forEach { a ->
            val s = ExerciseAnims.sample(a.keys, 0f)
            val e = ExerciseAnims.sample(a.keys, 0.99999f)
            for (k in s.indices) assertEquals(s[k].toDouble(), e[k].toDouble(), 0.05)
        }
    }
}
