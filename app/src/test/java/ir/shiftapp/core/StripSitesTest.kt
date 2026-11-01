package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StripSitesTest {
    private val tv = StripSites.TV_DEFAULTS

    @Test fun telewebionDefaultUsesCurrentAddress() {
        assertEquals("https://telewebion.net", tv.first { it.key == "telewebion" }.url)
    }

    @Test fun defaultsAreValidAndUnique() {
        for (strip in Strip.values()) {
            val d = StripSites.defaultsOf(strip)
            assertTrue(d.isNotEmpty())
            assertEquals(d.size, d.map { it.key }.toSet().size)
            assertEquals(d.size, d.map { it.url.lowercase() }.toSet().size)
            d.forEach {
                assertNull(it.title, StripSites.problem(it.title, it.url))
                assertTrue(it.key, Regex("[a-z0-9_]{1,30}").matches(it.key))
            }
            val back = StripSites.decode(StripSites.encode(d))!!
            assertEquals(d.map { it.key }, back.map { it.key })
            assertEquals(d.map { it.url }, back.map { it.url })
        }
    }

    @Test fun sitesOnTheSameRegistrableDomainGetDistinctKeys() {
        val keys = StripSites.ESSENTIAL_DEFAULTS.map { it.key }
        assertTrue("ihio_gov_ir" in keys && "tax_gov_ir" in keys)
        assertEquals("digikala_com", StripSites.defaultKey("https://www.digikala.com"))
    }

    @Test fun nothingStoredOrBrokenMeansDefaults() {
        assertEquals(tv.map { it.key }, StripSites.resolve(Strip.TV, "").map { it.key })
        assertEquals(tv.map { it.key }, StripSites.resolve(Strip.TV, "not json").map { it.key })
        assertEquals(tv.map { it.key }, StripSites.resolve(Strip.TV, """[{"key":"a","title":"x","url":"http://bad"}]""").map { it.key })
    }

    @Test fun deliberatelyEmptyListStaysEmpty() {
        assertTrue(StripSites.resolve(Strip.TV, StripSites.encode(emptyList())).isEmpty())
    }

    @Test fun legacyCustomEssentialSitesFollowTheDefaultsUntilFirstEdit() {
        val legacy = TilesPackFile.encodeSites(listOf(SiteTile("mysite", "سایت من", "", "https://mysite.example.ir", false, null)))
        val list = StripSites.resolve(Strip.ESSENTIAL, "", legacy)
        assertEquals(StripSites.ESSENTIAL_DEFAULTS.size + 1, list.size)
        assertEquals("mysite", list.last().key)
        val saved = StripSites.encode(StripSites.remove(list, "mysite"))
        assertEquals(StripSites.ESSENTIAL_DEFAULTS.size, StripSites.resolve(Strip.ESSENTIAL, saved, legacy).size)
    }

    @Test fun addPrefixesHttpsAndRejectsBadInput() {
        val r = StripSites.add(tv, "سایت جدید", "example.com")
        assertNull(r.error)
        assertEquals(tv.size + 1, r.list!!.size)
        assertEquals("https://example.com", r.list!!.last().url)
        assertEquals("examplecom", r.list!!.last().key)
        assertNotNull(StripSites.add(r.list!!, "دوباره", "https://EXAMPLE.com").error)
        assertNotNull(StripSites.add(tv, "x", "http://example.com").error)
        assertNotNull(StripSites.add(tv, "  ", "example.com").error)
        assertNotNull(StripSites.add(tv, "x", "not a url").error)
        assertNotNull(StripSites.add(tv, "x".repeat(TilesPackFile.MAX_TITLE + 1), "example.com").error)
    }

    @Test fun addMakesKeysUnique() {
        val a = StripSites.add(tv, "الف", "https://example.com/a").list!!
        val b = StripSites.add(a, "ب", "https://example.com/b").list!!
        assertEquals(b.size, b.map { it.key }.toSet().size)
    }

    @Test fun updateKeepsKeyAndPosition() {
        val r = StripSites.update(tv, "aparat", "آپارات من", "aparat.example.ir")
        assertNull(r.error)
        val list = r.list!!
        assertEquals(tv.map { it.key }, list.map { it.key })
        assertEquals("آپارات من", list[1].title)
        assertEquals("https://aparat.example.ir", list[1].url)
        assertNotNull(StripSites.update(tv, "aparat", "x", "https://telewebion.net").error)
        assertNotNull(StripSites.update(tv, "nope", "x", "example.com").error)
    }

    @Test fun moveSwapsNeighboursAndStopsAtTheEnds() {
        val m = StripSites.move(tv, "aparat", -1)
        assertEquals(listOf("aparat", "telewebion"), m.take(2).map { it.key })
        assertEquals(tv.map { it.key }, StripSites.move(tv, "telewebion", -1).map { it.key })
        assertEquals(tv.map { it.key }, StripSites.move(tv, tv.last().key, 1).map { it.key })
        assertEquals(tv.map { it.key }, StripSites.move(tv, "unknown", 1).map { it.key })
    }

    @Test fun removeDropsOnlyThatSite() {
        val r = StripSites.remove(tv, "namava")
        assertEquals(tv.size - 1, r.size)
        assertTrue(r.none { it.key == "namava" })
    }
}
