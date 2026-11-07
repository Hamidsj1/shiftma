package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FabPosTest {
    @Test fun roundTrip() {
        assertEquals(0.25f to 0.75f, FabPos.parse(FabPos.encode(0.25f, 0.75f)))
    }
    @Test fun badInputIsDefault() {
        assertNull(FabPos.parse(null)); assertNull(FabPos.parse("")); assertNull(FabPos.parse("a,b")); assertNull(FabPos.parse("1,2,3")); assertNull(FabPos.parse("NaN,0.5"))
    }
    @Test fun clampsOutOfRange() {
        assertEquals(1f to 0f, FabPos.parse("5,-2"))
        assertEquals(0, FabPos.toPx(-1f, 500)); assertEquals(500, FabPos.toPx(3f, 500)); assertEquals(0, FabPos.toPx(0.5f, -10))
    }
    @Test fun toFracHandlesZeroRange() {
        assertEquals(0f, FabPos.toFrac(40f, 0)); assertEquals(0.5f, FabPos.toFrac(250f, 500)); assertEquals(1f, FabPos.toFrac(900f, 500))
    }
}
