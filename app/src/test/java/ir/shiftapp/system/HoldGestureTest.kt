package ir.shiftapp.system

import org.junit.Assert.*
import org.junit.Test

class HoldGestureTest {
    private var t = 0L
    private var taps = 0
    private val holds = ArrayList<Int>()
    private fun g() = AlarmKeyStop.HoldGesture({ taps++ }, { holds.add(it) }, { t }, 200L)

    @Test fun shortPressStopsOnRelease() {
        val g = g()
        g.down(25, 0)
        assertEquals(0, taps)                // تا رها نشده قطع نمی‌شود
        assertTrue(g.up(25))
        assertEquals(1, taps); assertTrue(holds.isEmpty())
    }

    @Test fun holdAdjustsVolumeAndDoesNotStop() {
        val g = g()
        g.down(25, 0)
        for (i in 1..10) { t += 50; g.down(25, i) }     // ۵۰۰ms تکرار
        assertTrue(g.up(25))
        assertEquals(0, taps)
        assertTrue(holds.isNotEmpty() && holds.all { it == 25 })
        assertTrue(holds.size in 2..4)        // با سقف سرعت (هر ۲۰۰ms)، نه برای هر تکرار
    }

    @Test fun releaseOfUnknownKeyIsNotSwallowed() {
        val g = g()
        assertFalse(g.up(24))
        assertEquals(0, taps)
    }

    @Test fun secondPressAfterHoldStillStopsOnTap() {
        val g = g()
        g.down(24, 0); t += 100; g.down(24, 1); g.up(24)
        g.down(24, 0); g.up(24)
        assertEquals(1, taps)
    }
}
