package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class RatePolicyTest {
    private val d0 = 20000L
    @Test fun notBeforeEnoughUse() {
        assertFalse(RatePolicy.shouldAsk(4, d0, 0, false, d0 + 10, true))
        assertFalse(RatePolicy.shouldAsk(9, d0, 0, false, d0 + 2, true))
    }
    @Test fun asksAfterUseAndDays() = assertTrue(RatePolicy.shouldAsk(5, d0, 0, false, d0 + 3, true))
    @Test fun neverAsksWithoutDataOrAfterNever() {
        assertFalse(RatePolicy.shouldAsk(50, d0, 0, false, d0 + 99, false))
        assertFalse(RatePolicy.shouldAsk(50, d0, 0, true, d0 + 99, true))
    }
    @Test fun atMostOncePerMonth() {
        assertFalse(RatePolicy.shouldAsk(50, d0, d0 + 10, false, d0 + 39, true))
        assertTrue(RatePolicy.shouldAsk(50, d0, d0 + 10, false, d0 + 40, true))
    }
}
