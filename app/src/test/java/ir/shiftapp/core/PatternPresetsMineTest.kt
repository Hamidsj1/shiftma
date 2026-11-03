package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class PatternPresetsMineTest {
    private val p = PatternPresets.ALL[0]     // ۸ روزه، ۴ گروه، فاصلهٔ ۲ روز، ترتیب شروع A, C, B, D
    private val a = JalaliDate(1405, 8, 5)

    @Test fun startOfMatchesCreateCrewsRule() {
        assertEquals(a, p.startOf(0, a))
        assertEquals(a.plusDays(2 * p.slotOf(2)), p.startOf(2, a))
    }
    @Test fun aStartFromMineIsInverseForEveryCrew() {
        for (i in 0 until p.crews) assertEquals(a, p.aStartFromMine(i, p.startOf(i, a)))
    }
}
