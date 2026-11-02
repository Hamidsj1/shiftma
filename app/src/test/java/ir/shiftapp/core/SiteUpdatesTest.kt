package ir.shiftapp.core

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SiteUpdatesTest {
    private fun tile(key: String, url: String, edited: Boolean = false, title: String = "T$key") = SiteTile(key, title, "s", url, false, null, edited)

    @Test fun parseKeepsOnlySafeEntries() {
        val a = JSONArray("""[
            {"key":"a","url":"https://new.example.ir/x","title":"جدید"},
            {"key":"b","url":"http://insecure.ir"},
            {"key":"c","url":"https://user@evil.ir"},
            {"key":"d","url":"https://192.168.1.1/x"},
            {"key":"BAD KEY","url":"https://ok.ir"},
            {"key":"a","url":"https://dup.ir"},
            {"key":"e","url":"https://ok.ir"}]""")
        val u = SiteUpdates.parse(a)
        assertEquals(listOf("a", "e"), u.map { it.key })
        assertEquals("جدید", u[0].title)
    }

    @Test fun appliesToExistingOnlyAndNeverAdds() {
        val sites = listOf(tile("a", "https://old.ir"), tile("b", "https://b.ir"))
        val r = SiteUpdates.apply(sites, listOf(SiteUpdate("a", "https://new.ir"), SiteUpdate("zzz", "https://x.ir")))!!
        assertEquals(listOf("a", "b"), r.map { it.key })
        assertEquals("https://new.ir", r[0].url)
        assertEquals("Ta", r[0].title)          // عنوان خالی = بدون تغییر
        assertEquals("https://b.ir", r[1].url)
    }

    @Test fun userEditedSiteIsNeverOverridden() {
        val sites = listOf(tile("a", "https://mine.ir", edited = true))
        assertNull(SiteUpdates.apply(sites, listOf(SiteUpdate("a", "https://remote.ir"))))
    }

    @Test fun noChangeReturnsNull() {
        val sites = listOf(tile("a", "https://same.ir"))
        assertNull(SiteUpdates.apply(sites, listOf(SiteUpdate("a", "https://same.ir"))))
        assertNull(SiteUpdates.apply(sites, emptyList()))
    }

    @Test fun editedFlagSurvivesStorageButNotSharing() {
        val stored = TilesPackFile.encodeSites(listOf(tile("a", "https://x.ir", edited = true)))
        assertTrue(TilesPackFile.decodeSites(stored).single().edited)
        val shared = TilesPackFile.toJson(listOf(tile("a", "https://x.ir", edited = true)), emptySet())
        assertFalse(shared.contains("edited"))
        assertFalse(TilesPackFile.fromJson(shared).sites.single().edited)
    }

    @Test fun remoteConfigParsesSiteUpdates() {
        val cfg = RemoteConfigFile.parse("""{"sites":[{"key":"a","url":"https://new.ir"}],"sections":{"sport":"hide"}}""")
        assertEquals("https://new.ir", cfg.siteUpdates.single().url)
        assertFalse(cfg.visible("sport"))
        assertTrue(RemoteConfigFile.parse("""{"enabled":false,"sites":[{"key":"a","url":"https://new.ir"}]}""").siteUpdates.isEmpty())
    }
}
