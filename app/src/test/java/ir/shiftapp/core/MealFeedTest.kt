package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MealFeedTest {
    private val raw = "https://raw.githubusercontent.com/acme/meals/main"

    @Test fun onlyHttpsGithubHostsAreSafe() {
        assertTrue(MealFeed.isSafeUrl("$raw/a.json"))
        assertTrue(MealFeed.isSafeUrl("https://acme.github.io/meals/a.pdf"))
        assertTrue(MealFeed.isSafeUrl("https://github.com/acme/meals/releases/download/v1/a.pdf"))
        assertFalse(MealFeed.isSafeUrl("http://raw.githubusercontent.com/a.json"))
        assertFalse(MealFeed.isSafeUrl("https://evil.example.com/a.pdf"))
        assertFalse(MealFeed.isSafeUrl("https://githubusercontent.com.evil.com/a.pdf"))
        assertFalse(MealFeed.isSafeUrl(""))
    }

    @Test fun parsesValidItemsAndSkipsBadOnes() {
        val json = """{"enabled":true,"complexes":[
            {"name":"مجتمع الف","label":"مهر ۱۴۰۵","file":"$raw/a.pdf"},
            {"name":"","label":"x","file":"$raw/b.pdf"},
            {"name":"مجتمع ب","file":"https://evil.example.com/c.pdf"},
            {"name":"مجتمع الف","label":"تکراری","file":"$raw/d.pdf"},
            {"name":"مجتمع ج","file":"$raw/e.png"}]}"""
        val items = MealFeed.parse(json)
        assertEquals(listOf("مجتمع الف", "مجتمع ج"), items.map { it.name })
        assertEquals("مهر ۱۴۰۵", items[0].label)
        assertEquals("", items[1].label)
    }

    @Test fun disabledOrBrokenFeedMeansNothing() {
        assertTrue(MealFeed.parse("""{"enabled":false,"complexes":[{"name":"a","file":"$raw/a.pdf"}]}""").isEmpty())
        assertTrue(MealFeed.parse("not json").isEmpty())
        assertTrue(MealFeed.parse("{}").isEmpty())
    }

    @Test fun limitsTheNumberOfItems() {
        val many = (1..50).joinToString(",") { """{"name":"m$it","file":"$raw/$it.pdf"}""" }
        assertEquals(MealFeed.MAX_ITEMS, MealFeed.parse("""{"complexes":[$many]}""").size)
    }

    @Test fun fileNameIgnoresQueryAndFragment() {
        assertEquals("a.pdf", MealFeed.fileNameOf("$raw/dir/a.pdf?raw=1#x"))
    }

    @Test fun tilesPackCarriesTheFeedAndRejectsUnsafeOnes() {
        val feed = "$raw/company-a.json"
        val pack = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), feed))
        assertEquals(feed, pack.mealsFeed)
        assertTrue("meals" in pack.sections)
        assertEquals("", TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"))).mealsFeed)
        val onlyFeed = TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), emptySet(), feed))
        assertEquals(feed, onlyFeed.mealsFeed)
        assertThrows(IllegalArgumentException::class.java) {
            TilesPackFile.fromJson(TilesPackFile.toJson(emptyList(), setOf("meals"), "https://evil.example.com/a.json"))
        }
    }

    @Test fun packsWithoutFeedLoadAsBefore() {
        val p = TilesPackFile.fromJson("""{"app":"shiftma","type":"tiles","version":1,"sections":["phonebook"]}""")
        assertEquals("", p.mealsFeed)
    }
}
