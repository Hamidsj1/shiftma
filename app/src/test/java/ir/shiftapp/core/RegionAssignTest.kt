package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionAssignTest {
    private val cfg = "https://raw.githubusercontent.com/Hamidsj1/shiftma-ads/main"

    private fun json(enabled: Boolean = true, regions: String) = """{"enabled":$enabled,"regions":[$regions]}"""
    private fun rule(id: String, hosts: String, extra: String = "") =
        """{"id":"$id","hosts":[$hosts],"configFeed":"$cfg/$id/config.json"$extra}"""

    private val two = RegionAssign.parse(json(regions = rule("sungun", "\"my.nicico.com\",\"a.sungun.ir\"") + "," + rule("daralu", "\"b.daralu.ir\"")))!!

    @Test fun disabledOrMissingEnabledMeansNoRules() {
        assertTrue(RegionAssign.parse(json(enabled = false, regions = rule("a", "\"x.ir\"")))!!.isEmpty())
        assertTrue(RegionAssign.parse("""{"regions":[${rule("a", "\"x.ir\"")}]}""")!!.isEmpty())   // پیش‌فرض خاموش
    }

    @Test fun brokenFileIsNullSoOldRulesStay() {
        assertNull(RegionAssign.parse("{not json"))
        assertNull(RegionAssign.parse(" ".repeat(RegionAssign.MAX_JSON + 1)))
    }

    @Test fun ruleNeedsSafeConfigFeedAndValidId() {
        val bad = json(regions = listOf(
            """{"id":"a","hosts":["x.ir"],"configFeed":"http://raw.githubusercontent.com/o/r/main/c.json"}""",
            """{"id":"b","hosts":["x.ir"],"configFeed":"https://evil.example.com/c.json"}""",
            """{"id":"BAD ID","hosts":["x.ir"],"configFeed":"$cfg/c.json"}""",
            """{"id":"","hosts":["x.ir"],"configFeed":"$cfg/c.json"}""",
            """{"id":"ok","hosts":["x.ir"],"configFeed":"$cfg/ok/config.json"}"""
        ).joinToString(","))
        assertEquals(listOf("ok"), RegionAssign.parse(bad)!!.map { it.id })
    }

    @Test fun duplicateIdKeepsFirstAndHostsAreSanitized() {
        val r = RegionAssign.parse(json(regions = rule("a", "\"X.Ir\",\"bad host\",\"nodot\",\"ok.ir\"") + "," + rule("a", "\"z.ir\"")))!!
        assertEquals(1, r.size)
        assertEquals(setOf("x.ir", "ok.ir"), r[0].hosts)
    }

    @Test fun unsafeAdsAndNoticeFeedsAreDropped() {
        val r = RegionAssign.parse(json(regions = rule("a", "\"x.ir\"", ""","adsFeed":"https://evil.example.com/a.json","noticeFeed":"$cfg/a/notice.json","publicNotices":false""")))!!
        assertEquals("", r[0].adsFeed); assertFalse(r[0].publicAds)
        assertEquals("$cfg/a/notice.json", r[0].noticeFeed); assertFalse(r[0].publicNotices)
    }

    @Test fun pickByUniqueHost() {
        assertEquals("sungun", RegionAssign.pick(two, setOf("my.nicico.com", "other.ir"), "")!!.id)
        assertEquals("daralu", RegionAssign.pick(two, setOf("b.daralu.ir"), "")!!.id)
    }

    @Test fun ambiguousOrUnknownHostPicksNothing() {
        assertNull(RegionAssign.pick(two, setOf("my.nicico.com", "b.daralu.ir"), ""))      // دو منطقه جور شد
        assertNull(RegionAssign.pick(two, setOf("unknown.ir"), ""))
        assertNull(RegionAssign.pick(two, emptySet(), ""))
        assertNull(RegionAssign.pick(emptyList(), setOf("my.nicico.com"), "sungun"))
    }

    @Test fun tagWinsOverHostsAndUnknownTagFallsBackToHosts() {
        assertEquals("daralu", RegionAssign.pick(two, setOf("my.nicico.com"), "daralu")!!.id)
        assertEquals("sungun", RegionAssign.pick(two, emptySet(), "SUNGUN")!!.id)      // بدون سامانه هم با برچسب
        assertEquals("sungun", RegionAssign.pick(two, setOf("my.nicico.com"), "nosuch")!!.id)
        assertNull(RegionAssign.pick(two, emptySet(), "nosuch"))
    }

    @Test fun tagIsCleanedInFiles() {
        assertEquals("", RegionAssign.cleanTag("Bad Tag!"))
        assertEquals("sungun", RegionAssign.cleanTag(" Sungun "))
        val p = TilesPackFile.fromJson("""{"app":"shiftma","type":"tiles","version":1,"sites":[],"sections":["phonebook"],"region":" Sungun "}""")
        assertEquals("sungun", p.region)
        assertEquals("", TilesPackFile.fromJson("""{"app":"shiftma","type":"tiles","version":1,"sites":[],"sections":["phonebook"],"region":"bad tag"}""").region)
        assertEquals("", TilesPackFile.fromJson("""{"app":"shiftma","type":"tiles","version":1,"sites":[],"sections":["phonebook"]}""").region)
    }

    @Test fun exportCarriesTagAndTagAloneDoesNotCountAsFeeds() {
        val f = Feeds(configFeed = "$cfg/a/config.json", region = "a")
        val back = TilesPackFile.fromObject(TilesPackFile.toJsonObject(emptyList(), setOf("phonebook"), f))
        assertEquals("a", back.region); assertEquals("$cfg/a/config.json", back.configFeed)
        assertTrue(Feeds(region = "a").isEmpty)
        assertFalse(f.isEmpty)
        assertNotNull(TilesPackFile.withFeeds(null, f))
        assertNull(TilesPackFile.withFeeds(null, Feeds(region = "a")))
    }
}
