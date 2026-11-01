package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayslipDetectTest {
    @Test fun kindAndMonthFromPageText() {
        val r = PayslipDetect.detect(listOf("فیش رفاهی - مرداد ۱۴۰۵"))
        assertEquals(SlipKind.REFAHI, r.kind)
        assertEquals(5, r.month)
        assertEquals(1405, r.year)
    }

    @Test fun hokmiWithArabicLettersAndDigits() {
        val r = PayslipDetect.detect(listOf("فيش حكمي شهريور ١٤٠٤"))
        assertEquals(SlipKind.HOKMI, r.kind)
        assertEquals(6, r.month)
        assertEquals(1404, r.year)
    }

    @Test fun numericDateIsUsedWhenNoMonthName() {
        val r = PayslipDetect.detect(listOf("دورهٔ ۱۴۰۵/۰۳ حکمی"))
        assertEquals(3, r.month)
        assertEquals(1405, r.year)
        assertEquals(SlipKind.HOKMI, r.kind)
    }

    @Test fun earlierTextWinsAndFirstKindWins() {
        val r = PayslipDetect.detect(listOf("رفاهی و حکمی", "فیش حکمی"))
        assertEquals(SlipKind.REFAHI, r.kind)
    }

    @Test fun shortMonthNamesInsideOtherWordsAreIgnored() {
        // «دی» داخل «دیروز» و «تیر» داخل «تیراژ» ماه حساب نمی‌شود
        val r = PayslipDetect.detect(listOf("دیروز تیراژ"))
        assertNull(r.month)
        assertTrue(r.isEmpty)
    }

    @Test fun missingPartsStayNull() {
        val r = PayslipDetect.detect(listOf("فیش رفاهی"))
        assertEquals(SlipKind.REFAHI, r.kind)
        assertNull(r.month)
    }

    @Test fun pdfTextReadsFlateStreamsAndHexStrings() {
        val content = "BT <FEFF062D06A9064506CC> Tj (Refahi) Tj ET"
        val def = java.util.zip.Deflater()
        def.setInput(content.toByteArray(Charsets.ISO_8859_1)); def.finish()
        val buf = ByteArray(512); val n = def.deflate(buf)
        val pdf = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.ISO_8859_1) +
            buf.copyOf(n) + "\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1)
        val t = PayslipDetect.pdfText(pdf)
        assertTrue(t.contains("Refahi"))
        assertTrue(t.contains("حکمی"))
    }
}
