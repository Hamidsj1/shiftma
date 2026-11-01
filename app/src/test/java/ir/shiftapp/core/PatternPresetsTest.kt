package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternPresetsTest {
    @Test fun allCodesAreKnown() {
        PatternPresets.ALL.forEach { p ->
            assertTrue(p.title, p.codes.isNotEmpty())
            p.codes.forEach { assertTrue("${p.title}: کد $it", ShiftType.byCode(it) != null) }
        }
    }

    /** در الگوهای چندگروهه، هر شیفتِ کاری در هر روز دقیقاً توسط یک گروه پوشش داده می‌شود. */
    @Test fun crewPresetsCoverEveryShiftExactlyOnce() {
        PatternPresets.ALL.filter { it.crews > 1 }.forEach { p ->
            val len = p.codes.size
            val work = p.codes.filter { ShiftType.byCode(it)?.isRest != true }.toSet()
            for (day in 0 until len * p.crews) {
                for (c in work) {
                    val n = (0 until p.crews).count { i -> p.codes[Math.floorMod(day - p.slotOf(i) * p.offsetDays, len)] == c }
                    assertEquals("${p.title} روز $day شیفت $c", 1, n)
                }
            }
        }
    }

    @Test fun classicPresetsAreValidAndUnique() {
        val c = PatternPresets.CLASSIC
        assertTrue(c.isNotEmpty())
        c.forEach { p -> p.codes.forEach { assertTrue("${p.title}: کد $it", ShiftType.byCode(it) != null) } }
        assertEquals(c.size, c.map { it.codes }.distinct().size)
        assertEquals(listOf(1, 4, 4, 4, 4), c.first { it.title == "۱ روزکار ۴ استراحت" }.codes)
        assertTrue(c.none { cl -> PatternPresets.ALL.any { it.codes == cl.codes } })
    }

    @Test fun featuredPresetsExistAndAreUnique() {
        assertEquals(5, PatternPresets.FEATURED.size)
        assertEquals(5, PatternPresets.FEATURED.map { it.title }.toSet().size)
        assertTrue(PatternPresets.FEATURED.all { it in PatternPresets.ALL })
    }
}
