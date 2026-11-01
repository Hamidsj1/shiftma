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

    // ───── روز شروع پیش‌فرض (تاریخ لنگر ۱۹ مهر ۱۴۰۵) ─────

    private val first get() = PatternPresets.ALL.first()      // ۲ روز – ۱ استراحت – ۲ شب – ۳ استراحت

    @Test fun startIsAnchorOnAnchorDay() {
        assertEquals(PatternPresets.ANCHOR, first.defaultStart(PatternPresets.ANCHOR))
    }

    @Test fun startMovesBackToLastCycleStart() {
        assertEquals(JalaliDate(1405, 8, 5), first.defaultStart(JalaliDate(1405, 8, 10)))     // ۱۰ آبان ← ۵ آبان
        assertEquals(JalaliDate(1405, 8, 29), first.defaultStart(JalaliDate(1405, 9, 1)))     // آبان ۳۰ روزه است؛ ۱ آذر ← ۲۹ آبان
        assertEquals(JalaliDate(1405, 8, 5), first.defaultStart(JalaliDate(1405, 8, 12)))     // ۱۲ آبان هنوز در همان دوره
    }

    /** ترتیب و شروع گروه‌ها با جدول چهارشیفت واقعی (۱ تا ۹ آبان ۱۴۰۵) یکی است. */
    @Test fun crewStartsMatchRealTable() {
        val a = first.defaultStart(JalaliDate(1405, 8, 10))
        fun codeOn(crew: Int, d: JalaliDate): Int {
            val start = a.plusDays(first.slotOf(crew) * first.offsetDays)
            return first.codes[Math.floorMod(d.toJdn() - start.toJdn(), first.codes.size)]
        }
        // هر ردیف: روز ماه آبان ← کدهای گروه‌های A..D (۱ روزکار، ۳ شبکار، ۴ استراحت)
        val table = mapOf(
            1 to listOf(3, 1, 4, 4), 2 to listOf(4, 1, 3, 4), 3 to listOf(4, 4, 3, 1), 4 to listOf(4, 3, 4, 1),
            5 to listOf(1, 3, 4, 4), 6 to listOf(1, 4, 4, 3), 7 to listOf(4, 4, 1, 3), 8 to listOf(3, 4, 1, 4), 9 to listOf(3, 1, 4, 4)
        )
        table.forEach { (day, want) ->
            val got = (0..3).map { codeOn(it, JalaliDate(1405, 8, day)) }
            assertEquals("روز $day", want, got)
        }
    }
}
