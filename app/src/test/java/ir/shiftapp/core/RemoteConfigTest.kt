package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class RemoteConfigTest {
    private val cfgUrl = "https://raw.githubusercontent.com/Hamidsj1/shiftma-ads/main/sungun/config.json"

    @Test fun parsesHidePaidShow() {
        val c = RemoteConfigFile.parse("""{"enabled":true,"payLink":"https://example.ir/pay","sections":{"sport":"hide","reading":"paid","meals":"show"}}""")
        assertEquals(SectionState.HIDE, c.stateOf("sport"))
        assertEquals(SectionState.PAID, c.stateOf("reading"))
        assertEquals(SectionState.SHOW, c.stateOf("meals"))
        assertFalse(c.visible("sport"))
        assertTrue(c.visible("reading"))
        assertTrue(c.needsPayment("reading"))
        assertFalse(c.needsPayment("sport"))
        assertEquals("https://example.ir/pay", c.payLink)
    }

    @Test fun missingSectionIsVisibleAndFree() {
        val c = RemoteConfigFile.parse("""{"sections":{"sport":"hide"}}""")
        assertTrue(c.visible("notes"))
        assertFalse(c.needsPayment("notes"))
        assertEquals(RemoteConfig(), RemoteConfigFile.parse("{}"))
    }

    @Test fun protectedSectionsNeverHiddenOrPaid() {
        val c = RemoteConfigFile.parse("""{"sections":{"settings":"hide","about":"paid","shift":"hide","sport":"hide"}}""")
        assertTrue(c.visible("settings"))
        assertTrue(c.visible("about"))
        assertTrue(c.visible("shift"))
        assertFalse(c.needsPayment("about"))
        assertFalse(c.visible("sport"))
    }

    @Test fun badEntriesAreSkippedNotFatal() {
        val c = RemoteConfigFile.parse("""{"sections":{"sport":"hidee","Reading ":"PAID","bad id!":"hide","":"hide","english":"hide"}}""")
        assertEquals(SectionState.SHOW, c.stateOf("sport"))      // مقدار ناشناخته نادیده گرفته می‌شود
        assertEquals(SectionState.PAID, c.stateOf("reading"))    // کلید و مقدار تمیز و کوچک‌حرف می‌شوند
        assertEquals(SectionState.HIDE, c.stateOf("english"))
        assertEquals(setOf("reading", "english"), c.sections.keys)
    }

    @Test fun disabledMeansEverythingVisibleAndFree() {
        val c = RemoteConfigFile.parse("""{"enabled":false,"payLink":"https://example.ir/p","sections":{"sport":"hide","reading":"paid"}}""")
        assertEquals(RemoteConfig(), c)
        assertTrue(c.visible("sport"))
        assertFalse(c.needsPayment("reading"))
    }

    @Test fun brokenFileIsNullSoLastGoodConfigStays() {
        assertNull(RemoteConfigFile.parseOrNull("not json"))
        assertNull(RemoteConfigFile.parseOrNull(""))
        assertEquals(RemoteConfig(), RemoteConfigFile.parse("not json"))
        assertNotNull(RemoteConfigFile.parseOrNull("{}"))
    }

    @Test fun unsafePayLinkDropped() {
        assertEquals("", RemoteConfigFile.parse("""{"payLink":"http://example.ir/pay"}""").payLink)
        assertEquals("", RemoteConfigFile.parse("""{"payLink":"javascript:alert(1)"}""").payLink)
        assertEquals("https://zarinp.al/x", RemoteConfigFile.parse("""{"payLink":" https://zarinp.al/x "}""").payLink)
    }

    @Test fun sectionCountIsCapped() {
        val body = (1..100).joinToString(",") { "\"s$it\":\"hide\"" }
        assertEquals(RemoteConfigFile.MAX_SECTIONS, RemoteConfigFile.parse("""{"sections":{$body}}""").sections.size)
    }

    // ---- کد بخش پولی: باید با tools/shiftma_paid_code.py (کلید پیش‌فرض) یکی باشد ----
    @Test fun codeMatchesPythonTool() {
        // با کلید آزمایشی (نه کلید واقعی)؛ مقدارها را همان الگوریتم tools/shiftma_paid_code.py با همین کلید می‌دهد
        val k = "test-key-not-secret"
        assertEquals("81314765", PaidAccess.codeFor("AB12CD34", "reading", k))
        assertEquals("30291507", PaidAccess.codeFor("AB12CD34", "sport", k))
        assertEquals("65409290", PaidAccess.codeFor("ZZZZ2222", "english", k))
    }

    @Test fun codeIsBoundToPhoneAndSection() {
        val real = PaidAccess.codeFor("AB12CD34", "reading")
        assertEquals(8, real.length)
        assertTrue(PaidAccess.verify(real, "AB12CD34", "reading"))
        assertFalse(PaidAccess.verify(real, "AB12CD34", "sport"))       // بخش دیگر
        assertFalse(PaidAccess.verify(real, "ZZZZ2222", "reading"))     // گوشی دیگر
        assertFalse(PaidAccess.verify("", "AB12CD34", "reading"))
        assertFalse(PaidAccess.verify(real.dropLast(1), "AB12CD34", "reading"))
    }

    @Test fun verifyToleratesCaseSpacesAndPersianDigits() {
        val real = PaidAccess.codeFor("AB12CD34", "reading")
        val fa = real.map { "۰۱۲۳۴۵۶۷۸۹"[it - '0'] }.joinToString("")
        assertTrue(PaidAccess.verify(fa, "AB12CD34", "reading"))
        assertTrue(PaidAccess.verify(real.substring(0, 4) + " " + real.substring(4), "AB12CD34", "reading"))
        assertTrue(PaidAccess.verify(real, "ab12cd34", "reading"))
    }

    @Test fun lockoutRemainingIsZeroWhenNeverLocked() {
        assertEquals(0L, PaidAccess.lockRemainingMs(0L))
    }

    // ---- فایل کاشی‌ها: کلید configFeed ----
    @Test fun tilesFileCarriesConfigFeed() {
        val p = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), emptySet(), configFeed = cfgUrl))
        assertEquals(cfgUrl, p.configFeed)
        assertEquals("", TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"))).configFeed)
    }

    @Test fun tilesFileRejectsUnsafeConfigFeed() {
        assertThrows(IllegalArgumentException::class.java) {
            TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), configFeed = "https://evil.example.com/c.json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), configFeed = "http://raw.githubusercontent.com/a/b/c.json"))
        }
    }
}
