package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFeedTest {
    @Test fun parsesValidItems() {
        val j = """{"enabled":true,"items":[{"id":"a1","image":"https://x.ir/a.png","link":"https://x.ir/o","title":"تخفیف"}]}"""
        val r = AdFeed.parse(j)
        assertEquals(1, r.size)
        assertEquals("a1", r[0].id)
        assertEquals("https://x.ir/o", r[0].linkUrl)
    }

    @Test fun rejectsNonHttps() {
        val j = """{"items":[{"image":"http://x.ir/a.png","link":"https://x.ir/o"},{"image":"https://x.ir/a.png","link":"javascript:alert(1)"}]}"""
        assertTrue(AdFeed.parse(j).isEmpty())
    }

    @Test fun disabledOrBrokenMeansNoAds() {
        assertTrue(AdFeed.parse("""{"enabled":false,"items":[{"image":"https://x.ir/a.png","link":"https://x.ir/o"}]}""").isEmpty())
        assertTrue(AdFeed.parse("not json").isEmpty())
        assertTrue(AdFeed.parse("{}").isEmpty())
    }

    @Test fun capsItemCount() {
        val items = (1..9).joinToString(",") { """{"image":"https://x.ir/$it.png","link":"https://x.ir/l"}""" }
        assertEquals(AdFeed.MAX_ITEMS, AdFeed.parse("""{"items":[$items]}""").size)
    }

    @Test fun urlCheck() {
        assertTrue(AdFeed.isSafeUrl("https://a.ir/x"))
        assertFalse(AdFeed.isSafeUrl("http://a.ir/x"))
        assertFalse(AdFeed.isSafeUrl(""))
    }

    @Test fun pickUsesTheWorkplaceFeedOnlyWhenItIsSafe() {
        val def = "https://raw.githubusercontent.com/u/shiftma-ads/main/ad.json"
        val own = "https://raw.githubusercontent.com/u/shiftma-ads/main/sungun/ad.json"
        org.junit.Assert.assertEquals(own, AdFeed.pick(def, own))
        org.junit.Assert.assertEquals(def, AdFeed.pick(def, ""))
        org.junit.Assert.assertEquals(def, AdFeed.pick(def, "https://evil.example.com/ad.json"))
        org.junit.Assert.assertEquals(def, AdFeed.pick(def, "http://raw.githubusercontent.com/ad.json"))
    }

    @Test fun tilesPackCarriesTheAdsFeed() {
        val own = "https://raw.githubusercontent.com/u/shiftma-ads/main/sungun/ad.json"
        val p = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", own))
        org.junit.Assert.assertEquals(own, p.adsFeed)
        org.junit.Assert.assertEquals("", TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"))).adsFeed)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", "https://evil.example.com/ad.json"))
        }
    }

    @Test fun itemsCarryAnOptionalTileKey() {
        val j = """{"items":[{"image":"https://x.ir/a.png","link":"https://x.ir/o","tile":"meals"},{"image":"https://x.ir/b.png","link":"https://x.ir/o"},{"image":"https://x.ir/c.png","link":"https://x.ir/o","tile":"BAD KEY!"}]}"""
        val r = AdFeed.parse(j)
        assertEquals(listOf("meals", "", ""), r.map { it.tile })
        assertEquals(1, AdFeed.forTile(r, "meals") { it.tile }.size)
        assertEquals(2, AdFeed.forTile(r, "") { it.tile }.size)
    }

    @Test fun feedsAreWorkplaceFirstAndPublicOnlyWhenAsked() {
        val def = "https://raw.githubusercontent.com/u/shiftma-ads/main/ad.json"
        val own = "https://raw.githubusercontent.com/u/shiftma-ads/main/sungun/ad.json"
        assertEquals(listOf(def), AdFeed.feeds(def, "", false))
        assertEquals(listOf(def), AdFeed.feeds(def, "", true))
        assertEquals(listOf(own), AdFeed.feeds(def, own, false))
        assertEquals(listOf(own, def), AdFeed.feeds(def, own, true))
        assertEquals(listOf(def), AdFeed.feeds(def, "http://evil.example.com/ad.json", false))
    }

    @Test fun tilesPackCarriesPublicAdsOnlyWithAWorkplaceFeed() {
        val own = "https://raw.githubusercontent.com/u/shiftma-ads/main/sungun/ad.json"
        assertTrue(TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", own, true)).publicAds)
        assertFalse(TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", own)).publicAds)
        assertFalse(TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", "", true)).publicAds)
    }
}
