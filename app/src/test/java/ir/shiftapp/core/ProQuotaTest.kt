package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class ProQuotaTest {
    @Test fun remainingCountsDownAndStopsAtZero() {
        assertEquals(ProQuota.FREE_USES, ProQuota.remaining(0))
        assertEquals(ProQuota.FREE_USES - 1, ProQuota.remaining(1))
        assertEquals(0, ProQuota.remaining(ProQuota.FREE_USES))
        assertEquals(0, ProQuota.remaining(ProQuota.FREE_USES + 10))
    }

    @Test fun freeUsesAreAllowedThenBlocked() {
        for (used in 0 until ProQuota.FREE_USES) assertTrue("used=$used", ProQuota.canUse(used, pro = false))
        assertFalse(ProQuota.canUse(ProQuota.FREE_USES, pro = false))
        assertFalse(ProQuota.canUse(ProQuota.FREE_USES + 3, pro = false))
    }

    @Test fun purchaseUnlocksForever() {
        assertTrue(ProQuota.canUse(ProQuota.FREE_USES, pro = true))
        assertTrue(ProQuota.canUse(1000, pro = true))
    }

    @Test fun customFreeLimit() {
        assertTrue(ProQuota.canUse(2, pro = false, free = 3))
        assertFalse(ProQuota.canUse(3, pro = false, free = 3))
        assertEquals(1, ProQuota.remaining(2, free = 3))
    }
}
