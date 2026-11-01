package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class NoticeFeedTest {
    @Test fun parsesValidItemsAndDropsBad() {
        val json = """{"enabled":true,"items":[
            {"id":"n1","title":"سلام","body":"متن","link":"https://a.ir/x","expires":"2026-12-01"},
            {"id":"","title":"بدون شناسه"},
            {"id":"n2","title":"لینک ناامن","body":"b","link":"http://a.ir"},
            {"id":"n3","body":"بدون عنوان"}]}"""
        val l = NoticeFeed.parse(json)
        assertEquals(listOf("n1", "n2"), l.map { it.id })
        assertEquals("https://a.ir/x", l[0].link)
        assertEquals("", l[1].link)
        assertTrue(l[0].expiresMs > 0)
    }
    @Test fun disabledOrBrokenIsEmpty() {
        assertTrue(NoticeFeed.parse("""{"enabled":false,"items":[{"id":"a","title":"t"}]}""").isEmpty())
        assertTrue(NoticeFeed.parse("not json").isEmpty())
    }
    @Test fun maxFiveItems() {
        val items = (1..9).joinToString(",") { """{"id":"i$it","title":"t"}""" }
        assertEquals(5, NoticeFeed.parse("""{"items":[$items]}""").size)
    }
    @Test fun freshSkipsSeenAndExpired() {
        val exp = NoticeFeed.parseExpiry("2026-01-01")
        val items = listOf(NoticeItem("a", "t", ""), NoticeItem("b", "t", ""), NoticeItem("c", "t", "", "", exp), NoticeItem("d", "t", "", "", exp))
        val now = exp + 1
        assertEquals(listOf("b"), NoticeFeed.fresh(items, setOf("a"), now).map { it.id })
        assertEquals(listOf("b", "c", "d"), NoticeFeed.fresh(items, setOf("a"), exp - 1).map { it.id })
    }
    @Test fun expiryParsing() {
        assertEquals(0L, NoticeFeed.parseExpiry(""))
        assertEquals(0L, NoticeFeed.parseExpiry("2026-13-40"))
        assertTrue(NoticeFeed.parseExpiry("2026-10-21") > 0)
    }
    @Test fun seenIsCapped() {
        val big = (1..300).map { "x$it" }
        val m = NoticeFeed.mergeSeen(big, listOf("new"))
        assertEquals(NoticeFeed.MAX_SEEN, m.size)
        assertEquals("new", m.last())
    }

    private val pub = "https://raw.githubusercontent.com/u/shiftma-ads/main/notice.json"
    private val own = "https://raw.githubusercontent.com/u/shiftma-ads/main/sungun/notice.json"

    @Test fun feedsPublicOnlyWhenNoLocal() {
        assertEquals(listOf(pub to false), NoticeFeed.feeds(pub, "", true))
        assertEquals(listOf(pub to false), NoticeFeed.feeds(pub, "https://evil.example.com/n.json", true))
    }
    @Test fun feedsLocalAndPublicByDefault() {
        assertEquals(listOf(own to true, pub to false), NoticeFeed.feeds(pub, own, true))
    }
    @Test fun feedsLocalOnlyWhenFileAsks() {
        assertEquals(listOf(own to true), NoticeFeed.feeds(pub, own, false))
    }
    @Test fun localOnlyWorksWithoutPublicUrl() {
        assertEquals(listOf(own to true), NoticeFeed.feeds("", own, true))
        assertTrue(NoticeFeed.feeds("", "", true).isEmpty())
    }
    @Test fun seenKeysDoNotCollide() {
        assertEquals("n1", NoticeFeed.seenKey("n1", false))
        assertEquals("L:n1", NoticeFeed.seenKey("n1", true))
        val items = listOf(NoticeItem("n1", "t", ""), NoticeItem("L:n1", "t", ""))
        assertEquals(listOf("L:n1"), NoticeFeed.fresh(items, setOf("n1"), 0L).map { it.id })
    }
    @Test fun tilesPackCarriesNoticeFeed() {
        val p = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", "", false, own))
        assertEquals(own, p.noticeFeed)
        assertTrue(p.publicNotices)
        val q = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", "", false, own, false))
        assertFalse(q.publicNotices)
        assertEquals("", TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"))).noticeFeed)
        assertThrows(IllegalArgumentException::class.java) {
            TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "", "", false, "https://evil.example.com/n.json"))
        }
    }
}
