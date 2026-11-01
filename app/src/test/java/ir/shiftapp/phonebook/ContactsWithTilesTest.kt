package ir.shiftapp.phonebook

import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.TilesPackFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactsWithTilesTest {
    private val c = Contact(name = "الف", mobile = "09121234567")
    private val site = SiteTile("nicico", "مس من", "سامانه", "https://my.nicico.com", false, null)

    @Test fun bundleCarriesContactsAndSites() {
        val json = ContactsFile.toJson(listOf(c), TilesPackFile.toJsonObject(listOf(site), setOf("phonebook")))
        assertEquals(listOf(c), ContactsFile.fromJson(json))
        val t = ContactsFile.tilesOf(json)!!
        assertEquals("مس من", t.sites.single().title)
        assertEquals("https://my.nicico.com", t.sites.single().url)
        assertEquals(setOf("phonebook"), t.sections)
    }

    @Test fun plainContactsFileHasNoTiles() {
        assertNull(ContactsFile.tilesOf(ContactsFile.toJson(listOf(c))))
    }

    @Test fun brokenTilesDoNotBreakContacts() {
        val json = """{"app":"shiftma","type":"contacts","version":1,"contacts":[{"name":"x"}],"tiles":{"app":"shiftma","type":"tiles","version":1,"sites":[{"title":"a","url":"http://bad"}]}}"""
        assertEquals("contacts", ContactsFile.typeOf(json))
        assertEquals(1, ContactsFile.fromJson(json).size)
        assertNull(ContactsFile.tilesOf(json))
    }

    @Test fun tilesFileStillRoundTrips() {
        val back = TilesPackFile.fromJson(TilesPackFile.toJson(listOf(site), setOf("phonebook", "meals")))
        assertEquals(1, back.sites.size)
        assertEquals(setOf("phonebook", "meals"), back.sections)
    }
}
