package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class WidgetShortcutsTest {
    private fun raw(vararg s: Pair<String, String>) = TilesPackFile.encodeSites(s.map { SiteTile(it.first, it.first, "", it.second, false, null) })

    @Test fun phoneHiddenUnlessSpecialOrFileAndNotHiddenByPanel() {
        assertFalse(WidgetShortcuts.phoneVisible(false, emptySet(), true))
        assertTrue(WidgetShortcuts.phoneVisible(true, emptySet(), true))
        assertTrue(WidgetShortcuts.phoneVisible(false, setOf("phonebook"), true))
        assertFalse(WidgetShortcuts.phoneVisible(true, setOf("phonebook"), false))
    }

    @Test fun rowHiddenWhenNothingToShow() {
        assertFalse(WidgetShortcuts.rowVisible(false, 0))
        assertTrue(WidgetShortcuts.rowVisible(true, 0))
        assertTrue(WidgetShortcuts.rowVisible(false, 1))
    }

    @Test fun sitesFollowHomeLayoutOrder() {
        val r = raw("a" to "https://a.ir", "b" to "https://b.ir", "c" to "https://c.ir")
        val l = HomeLayout.encode(mapOf("links" to listOf("https://c.ir", "https://a.ir")))
        assertEquals(listOf("c", "a", "b"), WidgetShortcuts.sites(r, l).map { it.key })
        assertEquals(listOf("a", "b", "c"), WidgetShortcuts.sites(r, "").map { it.key })
        assertTrue(WidgetShortcuts.sites("", l).isEmpty())
    }

    @Test fun paging() {
        val l = (1..9).toList()
        assertEquals(3, WidgetShortcuts.pageCount(9))
        assertEquals(1, WidgetShortcuts.pageCount(0))
        assertEquals(listOf(5, 6, 7, 8), WidgetShortcuts.pageItems(l, 1))
        assertEquals(listOf(9), WidgetShortcuts.pageItems(l, 2))
        assertEquals(listOf(9), WidgetShortcuts.pageItems(l, 99))      // صفحهٔ بیرون از محدوده به آخرین صفحه می‌رود
        assertEquals(0, WidgetShortcuts.clampPage(-3, 9))
    }
}
