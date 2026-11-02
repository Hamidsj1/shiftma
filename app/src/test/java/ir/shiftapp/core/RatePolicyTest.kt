package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class RatePolicyTest {
    private val d0 = 20000L
    @Test fun notBeforeEnoughUse() {
        assertFalse(RatePolicy.shouldAsk(9, d0, 0, false, d0 + 10, true))
        assertFalse(RatePolicy.shouldAsk(20, d0, 0, false, d0 + 6, true))
    }
    @Test fun asksAfterUseAndDays() = assertTrue(RatePolicy.shouldAsk(10, d0, 0, false, d0 + 7, true))
    @Test fun neverAsksWithoutDataOrAfterNever() {
        assertFalse(RatePolicy.shouldAsk(50, d0, 0, false, d0 + 99, false))
        assertFalse(RatePolicy.shouldAsk(50, d0, 0, true, d0 + 99, true))
    }
    @Test fun spacedBy14Days() {
        assertFalse(RatePolicy.shouldAsk(50, d0, d0 + 10, false, d0 + 23, true, 1))
        assertTrue(RatePolicy.shouldAsk(50, d0, d0 + 10, false, d0 + 24, true, 1))
    }
    @Test fun maxThreeAsks() = assertFalse(RatePolicy.shouldAsk(50, d0, d0 + 10, false, d0 + 99, true, 3))
}
