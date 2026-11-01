package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayslipNamesTest {
    @Test fun nameRoundTrip() {
        assertEquals("1405-06-refahi.pdf", PayslipNames.fileName(1405, 6, SlipKind.REFAHI))
        val p = PayslipNames.parse("1405-06-refahi.pdf")!!
        assertEquals(Triple(1405, 6, SlipKind.REFAHI), Triple(p.year, p.month, p.kind))
        val q = PayslipNames.parse(PayslipNames.fileName(1404, 12, SlipKind.HOKMI))!!
        assertEquals(Triple(1404, 12, SlipKind.HOKMI), Triple(q.year, q.month, q.kind))
    }

    @Test fun legacyNameCountsAsHokmi() {
        val p = PayslipNames.parse("1405-06.pdf")!!
        assertEquals(SlipKind.HOKMI, p.kind)
    }

    @Test fun invalidNamesAreRejected() {
        assertNull(PayslipNames.parse("1405-13.pdf"))
        assertNull(PayslipNames.parse("1405-00-hokmi.pdf"))
        assertNull(PayslipNames.parse("1405-06-other.pdf"))
        assertNull(PayslipNames.parse("payslip_1.pdf"))
        assertNull(PayslipNames.parse("1405-06.pdf.tmp"))
    }

    @Test fun previousMonthWrapsYear() {
        assertEquals(1405 to 5, PayslipNames.previous(1405, 6))
        assertEquals(1404 to 12, PayslipNames.previous(1405, 1))
    }

    @Test fun pdfMagic() {
        assertTrue(PayslipNames.isPdf("%PDF-1.4 xxx".toByteArray()))
        assertFalse(PayslipNames.isPdf("<html>".toByteArray()))
        assertFalse(PayslipNames.isPdf(ByteArray(0)))
    }
}
