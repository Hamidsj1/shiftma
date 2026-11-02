package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test fun encodeParseRoundTrip() {
        val m = mapOf("sections" to listOf("notes", "shift"), "blocks" to listOf("tools", "sections"))
        assertEquals(m, HomeLayout.parse(HomeLayout.encode(m)))
        assertEquals("", HomeLayout.encode(emptyMap()))
        assertTrue(HomeLayout.parse("").isEmpty())
        assertTrue(HomeLayout.parse("خراب").isEmpty())
    }

    @Test fun orderKeepsSavedThenAppendsNew() {
        val def = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), HomeLayout.order(def, listOf("c", "a")))
        // شناسهٔ حذف‌شده و تکراری نادیده گرفته می‌شود
        assertEquals(listOf("b", "a", "c", "d"), HomeLayout.order(def, listOf("b", "zzz", "b", "a")))
        assertEquals(def, HomeLayout.order(def, null))
    }

    @Test fun moveSwapsAndIgnoresOutOfRange() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "b", "d"), HomeLayout.move(l, "b", 1))
        assertEquals(listOf("c", "b", "a", "d"), HomeLayout.move(l, "a", 2))
        assertEquals(l, HomeLayout.move(l, "a", -1))
        assertEquals(l, HomeLayout.move(l, "d", 1))
        assertEquals(l, HomeLayout.move(l, "x", 1))
    }

    @Test fun hiddenTilesKeepTheirSlot() {
        // «h» پنهان است؛ جابه‌جایی a و b نباید جای h را عوض کند
        val full = listOf("a", "h", "b", "c")
        val out = HomeLayout.moveVisible(full, listOf("a", "b", "c"), "a", 1)
        assertEquals(listOf("b", "h", "a", "c"), out)
        assertEquals(full, HomeLayout.moveVisible(full, listOf("a", "b", "c"), "a", -1))
    }

    @Test fun moveToInsertsLikeADrop() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), HomeLayout.moveTo(l, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), HomeLayout.moveTo(l, "d", 0))
        assertEquals(l, HomeLayout.moveTo(l, "b", 1))
        assertEquals(l, HomeLayout.moveTo(l, "b", 9))
        assertEquals(l, HomeLayout.moveTo(l, "x", 1))
    }

    @Test fun reorderVisibleKeepsHiddenSlots() {
        val full = listOf("a", "h", "b", "c")
        assertEquals(listOf("c", "h", "a", "b"), HomeLayout.reorderVisible(full, listOf("c", "a", "b")))
        // مجموعهٔ ناهمخوان: بدون تغییر
        assertEquals(full, HomeLayout.reorderVisible(full, listOf("c", "a")))
        assertEquals(full, HomeLayout.reorderVisible(full, listOf("c", "a", "z")))
    }

    @Test fun defaultBlocksAreLinksSectionsEssentialTvWidget() {
        // سامانه‌ها اولین گروه بعد از تقویم؛ سایت‌های ضروری درست زیر آن، بعد تلویزیون؛ بعد بخش‌ها؛ «ابزارها» دیگر گروه صفحهٔ اول نیست
        assertEquals(listOf("links", "sections", "essential", "tv", "widget"), HomeLayout.BLOCKS)
        assertEquals(HomeLayout.BLOCKS, HomeLayout.order(HomeLayout.BLOCKS, listOf("tools")))
    }

    @Test fun migrateDropsOldBlockAndSectionOrderOnceButKeepsLinks() {
        val old = mapOf("blocks" to listOf("sections", "tools", "links"), "sections" to listOf("notes", "shift"), "tools" to listOf("tv"), "links" to listOf("b", "a"))
        val m = HomeLayout.migrate(old)
        assertEquals(mapOf("links" to listOf("b", "a"), "v" to listOf("5")), m)
        // بار دوم دست نمی‌زند: چیدمان تازهٔ کاربر حفظ می‌شود
        val again = m + ("blocks" to listOf("sections", "links"))
        assertEquals(again, HomeLayout.migrate(again))
        // چیدمان خالی (نصب تازه یا بازنشانی) فقط نسخه می‌گیرد
        assertEquals(mapOf("v" to listOf("5")), HomeLayout.migrate(emptyMap()))
        assertEquals(HomeLayout.BLOCKS, HomeLayout.order(HomeLayout.BLOCKS, HomeLayout.migrate(old)["blocks"]))
    }

    @Test fun migrateFromV2DropsOnlyBlockOrder() {
        val v2 = mapOf("blocks" to listOf("sections", "links"), "sections" to listOf("notes", "shift"), "links" to listOf("b", "a"), "v" to listOf("2"))
        assertEquals(mapOf("sections" to listOf("notes", "shift"), "links" to listOf("b", "a"), "v" to listOf("5")), HomeLayout.migrate(v2))
    }

    @Test fun migrateFromV4DropsOnlyBlockOrder() {
        val v4 = mapOf("blocks" to listOf("links", "essential", "tv", "sections"), "sections" to listOf("notes"), "links" to listOf("b", "a"), "v" to listOf("4"))
        assertEquals(mapOf("sections" to listOf("notes"), "links" to listOf("b", "a"), "v" to listOf("5")), HomeLayout.migrate(v4))
    }

    @Test fun migrateFromV3DropsOnlyBlockOrder() {
        val v3 = mapOf("blocks" to listOf("links", "sections", "essential"), "links" to listOf("b", "a"), "v" to listOf("3"))
        assertEquals(mapOf("links" to listOf("b", "a"), "v" to listOf("5")), HomeLayout.migrate(v3))
    }
}
