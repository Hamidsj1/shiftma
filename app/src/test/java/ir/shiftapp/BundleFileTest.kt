package ir.shiftapp

import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.data.BundleFile
import ir.shiftapp.data.PatternEntity
import ir.shiftapp.phonebook.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleFileTest {
    private fun pattern(name: String) = PatternEntity(0, name, "شرکت", 1405, 7, 19, "1;1;4;3;3;4;4;4", "مسئول", "سرشیفت", "تولید")
    private fun site(key: String) = SiteTile(key, "سامانه $key", "توضیح", "https://$key.example.ir/login", false, null)

    @Test fun roundTripKeepsPatternsAndSites() {
        val b = BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A"), pattern("B")), listOf(site("one"), site("two"))))
        assertEquals(listOf(pattern("A"), pattern("B")), b.patterns)
        assertEquals(listOf("one", "two"), b.tiles!!.sites.map { it.key })
        assertTrue(b.tiles!!.sections.isEmpty())
    }

    @Test fun onlyPatternsOrOnlySitesIsAllowed() {
        val p = BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), emptyList()))
        assertEquals(1, p.patterns.size); assertNull(p.tiles)
        val s = BundleFile.fromJson(BundleFile.toJson(emptyList(), listOf(site("one"))))
        assertTrue(s.patterns.isEmpty()); assertNotNull(s.tiles)
    }

    @Test fun emptySelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BundleFile.toJson(emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { BundleFile.fromJson("""{"app":"shiftma","type":"bundle","version":1}""") }
    }

    @Test fun rejectsForeignOrNewerFiles() {
        assertThrows(IllegalArgumentException::class.java) { BundleFile.fromJson("""{"app":"other","type":"bundle","version":1,"patterns":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { BundleFile.fromJson("""{"app":"shiftma","type":"patterns","version":1}""") }
        assertThrows(IllegalArgumentException::class.java) { BundleFile.fromJson("""{"app":"shiftma","type":"bundle","version":99}""") }
    }

    @Test fun fileNeverContainsCredentials() {
        val json = BundleFile.toJson(emptyList(), listOf(site("one")))
        assertFalse(json.contains("password", ignoreCase = true))
        assertEquals(1, TilesPackFile.fromJson(org.json.JSONObject(json).getJSONObject("tiles").toString()).sites.size)
    }

    private fun contact(name: String) = Contact(1, name, "09121234567", "031-1234", "", "امور", "واحد", "wa=1")

    @Test fun contactsRoundTripAlongsidePatternsAndSites() {
        val b = BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), listOf(site("one")), listOf(contact("علی"), contact("رضا"))))
        assertEquals(1, b.patterns.size)
        assertNotNull(b.tiles)
        assertEquals(listOf("علی", "رضا"), b.contacts.map { it.name })
        assertEquals("09121234567", b.contacts[0].mobile)
        assertEquals("امور", b.contacts[0].dept)
    }

    @Test fun onlyContactsIsAllowed() {
        val b = BundleFile.fromJson(BundleFile.toJson(emptyList(), emptyList(), listOf(contact("علی"))))
        assertTrue(b.patterns.isEmpty())
        assertEquals(1, b.contacts.size)
        // سامانه‌ای انتخاب نشده، ولی کاشی دفترچه تلفن باید همراه فایل نمایان شود
        assertTrue(b.tiles!!.sites.isEmpty())
        assertEquals(setOf("phonebook"), b.tiles!!.sections)
    }

    @Test fun oldBundlesWithoutContactsStillLoad() {
        val b = BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), emptyList()))
        assertTrue(b.contacts.isEmpty())
    }

    private fun meal(name: String) = ir.shiftapp.docs.MealPackItem(name, "مهر ۱۴۰۵", "menu.pdf", ByteArray(300) { (it % 251).toByte() })

    @Test fun mealsRoundTripAlongsideEverythingElse() {
        val b = BundleFile.fromJson(
            BundleFile.toJson(listOf(pattern("A")), listOf(site("one")), listOf(contact("علی")), meals = listOf(meal("مجتمع الف"), meal("مجتمع ب")))
        )
        assertEquals(1, b.patterns.size)
        assertNotNull(b.tiles)
        assertEquals(1, b.contacts.size)
        assertEquals(listOf("مجتمع الف", "مجتمع ب"), b.meals.map { it.name })
        assertEquals("مهر ۱۴۰۵", b.meals[0].label)
        org.junit.Assert.assertArrayEquals(meal("x").bytes, b.meals[0].bytes)
    }

    @Test fun onlyMealsIsAllowed() {
        val b = BundleFile.fromJson(BundleFile.toJson(emptyList(), emptyList(), meals = listOf(meal("مجتمع الف"))))
        assertTrue(b.patterns.isEmpty()); assertTrue(b.contacts.isEmpty())
        assertEquals(1, b.meals.size)
        assertEquals(setOf("meals"), b.tiles!!.sections)
    }

    @Test fun contactsAndMealsWriteTheirSectionsSoTilesBecomeVisible() {
        val b = BundleFile.fromJson(
            BundleFile.toJson(listOf(pattern("A")), listOf(site("one")), listOf(contact("علی")), meals = listOf(meal("مجتمع الف")))
        )
        assertEquals(setOf("phonebook", "meals"), b.tiles!!.sections)
        assertEquals(listOf("one"), b.tiles!!.sites.map { it.key })
    }

    @Test fun noSectionsWhenNeitherContactsNorMealsAreSent() {
        val b = BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), listOf(site("one"))))
        assertTrue(b.tiles!!.sections.isEmpty())
        assertNull(BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), emptyList())).tiles)
    }

    @Test fun versionIsTwoOnlyWhenMealsAreIncluded() {
        assertEquals(1, org.json.JSONObject(BundleFile.toJson(listOf(pattern("A")), emptyList())).getInt("version"))
        assertEquals(2, org.json.JSONObject(BundleFile.toJson(listOf(pattern("A")), emptyList(), meals = listOf(meal("الف")))).getInt("version"))
    }

    @Test fun oldBundlesWithoutMealsStillLoad() {
        assertTrue(BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), emptyList())).meals.isEmpty())
    }

    @Test fun brokenMealsAreRejected() {
        val json = """{"app":"shiftma","type":"bundle","version":2,"meals":[{"name":"a","fileName":"a.exe","data":"AAAA"}]}"""
        assertThrows(IllegalArgumentException::class.java) { BundleFile.fromJson(json) }
    }

    @Test fun prefixTravelsWithContactsOnly() {
        val b = BundleFile.fromJson(BundleFile.toJson(emptyList(), emptyList(), listOf(contact("علی")), prefix = "+98211234,"))
        assertEquals("+98211234,", b.prefix)
        // بدون مخاطب، پیش‌شماره نمی‌رود؛ پیش‌شمارهٔ نامعتبر هم حذف می‌شود
        assertEquals("", BundleFile.fromJson(BundleFile.toJson(listOf(pattern("A")), emptyList(), prefix = "123")).prefix)
        assertEquals("", BundleFile.fromJson(BundleFile.toJson(emptyList(), emptyList(), listOf(contact("علی")), prefix = "abc<script>")).prefix)
        assertEquals("", BundleFile.fromJson(BundleFile.toJson(emptyList(), emptyList(), listOf(contact("علی")))).prefix)
    }
}
