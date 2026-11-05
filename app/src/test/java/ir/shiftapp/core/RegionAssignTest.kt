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
        assertEquals("sungun", RegionAssign.pick(two, setOf("my.nicico.com", "other.ir"), emptySet(), "")!!.id)
        assertEquals("daralu", RegionAssign.pick(two, setOf("b.daralu.ir"), emptySet(), "")!!.id)
    }

    @Test fun ambiguousOrUnknownHostPicksNothing() {
        assertNull(RegionAssign.pick(two, setOf("my.nicico.com", "b.daralu.ir"), emptySet(), ""))      // دو منطقه جور شد
        assertNull(RegionAssign.pick(two, setOf("unknown.ir"), emptySet(), ""))
        assertNull(RegionAssign.pick(two, emptySet(), emptySet(), ""))
        assertNull(RegionAssign.pick(emptyList(), setOf("my.nicico.com"), emptySet(), "sungun"))
    }

    @Test fun moreMatchingHostsWinsWhenOneHostIsShared() {
        val shared = RegionAssign.parse(json(regions = rule("sungun", "\"my.nicico.com\",\"a.sungun.ir\"") + "," + rule("daralu", "\"my.nicico.com\",\"b.daralu.ir\"")))!!
        assertNull(RegionAssign.pick(shared, setOf("my.nicico.com"), emptySet(), ""))                       // فقط میزبان مشترک: نامشخص
        assertEquals("sungun", RegionAssign.pick(shared, setOf("my.nicico.com", "a.sungun.ir"), emptySet(), "")!!.id)
        assertEquals("daralu", RegionAssign.pick(shared, setOf("b.daralu.ir", "my.nicico.com", "x.ir"), emptySet(), "")!!.id)
        assertNull(RegionAssign.pick(shared, setOf("a.sungun.ir", "b.daralu.ir"), emptySet(), ""))         // مساوی
    }

    @Test fun tagWinsOverHostsAndUnknownTagFallsBackToHosts() {
        assertEquals("daralu", RegionAssign.pick(two, setOf("my.nicico.com"), emptySet(), "daralu")!!.id)
        assertEquals("sungun", RegionAssign.pick(two, emptySet(), emptySet(), "SUNGUN")!!.id)      // بدون سامانه هم با برچسب
        assertEquals("sungun", RegionAssign.pick(two, setOf("my.nicico.com"), emptySet(), "nosuch")!!.id)
        assertNull(RegionAssign.pick(two, emptySet(), emptySet(), "nosuch"))
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

    @Test fun sigIsStableAcrossCaseSlashQueryAndMatchesThePanelAlgorithm() {
        assertEquals("725d07ba9076", RegionAssign.sig("https://my.nicico.com/sungun"))      // همان مقدار ادمین پنل (sigOf)
        assertEquals("725d07ba9076", RegionAssign.sig("https://MY.nicico.com/Sungun/?a=1#x"))
        assertEquals("725d07ba9076", RegionAssign.sig("https://www.my.nicico.com/sungun/"))
        assertEquals("c4d4494b6ac9", RegionAssign.sig("https://my.nicico.com"))
        assertNull(RegionAssign.sig("ftp://my.nicico.com/x")); assertNull(RegionAssign.sig("not a url")); assertNull(RegionAssign.sig(""))
    }

    @Test fun sharedHostButDifferentPathsAreTotallyDistinguishable() {
        val s = RegionAssign.sig("https://my.nicico.com/sungun")!!; val d = RegionAssign.sig("https://my.nicico.com/daralu")!!
        val rules = RegionAssign.parse(json(regions =
            rule("sungun", "\"my.nicico.com\"", ""","sigs":["$s"]""") + "," + rule("daralu", "\"my.nicico.com\"", ""","sigs":["$d"]""")))!!
        assertNull(RegionAssign.pick(rules, setOf("my.nicico.com"), emptySet(), ""))                      // فقط میزبان مشترک: نامشخص
        assertEquals("sungun", RegionAssign.pick(rules, setOf("my.nicico.com"), setOf(s), "")!!.id)
        assertEquals("daralu", RegionAssign.pick(rules, setOf("my.nicico.com"), setOf(d, "000000000000"), "")!!.id)
        assertNull(RegionAssign.pick(rules, setOf("my.nicico.com"), setOf(s, d), ""))                     // مساوی
    }

    @Test fun badSigsAreDropped() {
        val r = RegionAssign.parse(json(regions = rule("a", "\"x.ir\"", ""","sigs":["725D07BA9076","short","zzzzzzzzzzzz",5]""")))!!
        assertEquals(setOf("725d07ba9076"), r[0].sigs)
    }

    @Test fun phoneSigsNormalizeDigitsPrefixesAndSeparators() {
        val a = RegionAssign.phoneSigs(listOf("09121234567"))
        assertEquals(a, RegionAssign.phoneSigs(listOf("+98 912 123 4567")))
        assertEquals(a, RegionAssign.phoneSigs(listOf("۰۹۱۲۱۲۳۴۵۶۷")))
        assertEquals(a, RegionAssign.phoneSigs(listOf("٠٩١٢١٢٣٤٥٦٧")))
        assertEquals(2, RegionAssign.phoneSigs(listOf("1234, 5678")).size)
        assertEquals(RegionAssign.phoneSigs(listOf("1234")), RegionAssign.phoneSigs(listOf("داخلی ۱۲۳۴")))
        assertTrue(RegionAssign.phoneSigs(listOf("", "12", "abc")).isEmpty())
        assertEquals(6, a.single().length)
        assertEquals(setOf("03ac67"), RegionAssign.phoneSigs(listOf("1234")))      // مقدار مرجع مشترک با ادمین پنل (phoneSigs)
    }

    private fun ph(vararg n: String) = RegionAssign.phoneSigs(n.toList())
    private fun phoneRules(): List<RegionRule> {
        fun arr(s: Set<String>) = s.joinToString(",") { "\"$it\"" }
        val a = ph("1001", "1002", "1003", "1004"); val b = ph("2001", "2002", "2003", "2004")
        return RegionAssign.parse(json(regions = rule("a", "\"my.nicico.com\"", ""","phones":[${arr(a)}]""") + "," + rule("b", "\"my.nicico.com\"", ""","phones":[${arr(b)}]""")))!!
    }

    @Test fun phonesDecideOnlyWithEnoughHitsAndOnlyAsAuxiliary() {
        val rules = phoneRules(); val host = setOf("my.nicico.com")
        assertEquals("b", RegionAssign.pick(rules, host, emptySet(), "", ph("2001", "2002", "2003", "9"))!!.id)
        assertNull(RegionAssign.pick(rules, host, emptySet(), "", ph("2001", "2002")))                     // کمتر از آستانه
        assertNull(RegionAssign.pick(rules, host, emptySet(), "", ph("1001", "1002", "1003", "2001", "2002", "2003")))   // مساوی
        assertEquals("b", RegionAssign.pick(rules, emptySet(), emptySet(), "", ph("2001", "2002", "2003"))!!.id)         // بدون سامانه
    }

    @Test fun anUrlMatchAlwaysBeatsPhones() {
        val s = RegionAssign.sig("https://my.nicico.com/a")!!
        val r = RegionAssign.parse(json(regions = rule("a", "\"my.nicico.com\"", ""","sigs":["$s"]""") + "," +
            rule("b", "\"my.nicico.com\"", ""","phones":[${ph("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12").joinToString(",") { "\"$it\"" }}]""")))!!
        val many = ph("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
        assertEquals("a", RegionAssign.pick(r, setOf("my.nicico.com"), setOf(s), "", many)!!.id)      // ۱۰۰ > سقف ۹۰
    }

    @Test fun badPhoneSigsAreDropped() {
        val r = RegionAssign.parse(json(regions = rule("a", "\"x.ir\"", ""","phones":["ABCDEF","12345","zzzzzz","c35a37"]""")))!!
        assertEquals(setOf("abcdef", "c35a37"), r[0].phones)
    }
}
