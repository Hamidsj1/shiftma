package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFinderTest {
    @Test fun staticAndLoggingRules() {
        assertTrue(PdfFinder.isStatic("https://a.com/x/app.JS?v=1"))
        assertTrue(PdfFinder.isStatic("https://a.com/f.woff2"))
        assertFalse(PdfFinder.isStatic("https://a.com/Receipt/Download?id=5"))
        assertTrue(PdfFinder.shouldLog("GET", "https://a.com/r"))
        assertFalse(PdfFinder.shouldLog("POST", "https://a.com/r"))
        assertFalse(PdfFinder.shouldLog("GET", "blob:https://a.com/uuid"))
        assertFalse(PdfFinder.shouldLog("GET", "https://a.com/logo.png"))
    }

    @Test fun magicBytes() {
        assertTrue(PdfFinder.isPdfMagic("%PDF-1.7".toByteArray(), 5))
        assertFalse(PdfFinder.isPdfMagic("<html".toByteArray(), 5))
        assertFalse(PdfFinder.isPdfMagic("%PDF".toByteArray(), 4))
    }

    @Test fun fileParamIsResolvedAgainstFullUrl() {
        assertEquals("https://a.com/files/slip.pdf", PdfFinder.fileParamUrl("https://a.com/viewer/web/viewer.html?file=%2Ffiles%2Fslip.pdf"))
        assertEquals("https://a.com/viewer/web/slip.pdf", PdfFinder.fileParamUrl("https://a.com/viewer/web/viewer.html?x=1&file=slip.pdf"))
        assertEquals("https://b.com/x.pdf", PdfFinder.fileParamUrl("https://a.com/v?file=https%3A%2F%2Fb.com%2Fx.pdf"))
        assertNull(PdfFinder.fileParamUrl("https://a.com/v?x=1"))
    }

    @Test fun candidatesOrderDedupeAndLimit() {
        val seen = listOf(SeenUrl("https://a.com/new", "https://a.com/p"), SeenUrl("https://a.com/old", null), SeenUrl("https://a.com/new", null))
        val c = PdfFinder.candidates("https://a.com/base", listOf("https://a.com/frame?file=/f.pdf", "", "blob:x"), seen)
        assertEquals(
            listOf("https://a.com/frame?file=/f.pdf", "https://a.com/f.pdf", "https://a.com/new", "https://a.com/old"),
            c.map { it.url }
        )
        assertEquals("https://a.com/base", c[0].referer)
        assertEquals("https://a.com/frame?file=/f.pdf", c[1].referer)   // Referer = آدرس مادر
        assertEquals("https://a.com/p", c[2].referer)
        assertEquals("https://a.com/base", c[3].referer)
        assertEquals(2, PdfFinder.candidates("https://a.com", emptyList(), seen, max = 2).size)
    }

    @Test fun logCapsAndDedupes() {
        val log = RequestLog(max = 3)
        listOf("1", "2", "2", "3", "4").forEach { log.add("https://a.com/$it", null) }
        assertEquals(listOf("4", "3", "2"), log.newestFirst().map { it.url.substringAfterLast('/') })
    }

    @Test fun logoutIsNeverReplayed() {
        assertTrue(PdfFinder.isUnsafeToReplay("https://a.com/Authentication/LogOut"))
        assertFalse(PdfFinder.isUnsafeToReplay("https://a.com/LegalReceipt/Slip?id=1"))
    }
}
