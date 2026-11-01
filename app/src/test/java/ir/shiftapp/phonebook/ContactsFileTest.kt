package ir.shiftapp.phonebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ContactsFileTest {
    private fun sample() = Contact(name = "ریاست شیفت", mobile = "09121234567", landline = "5020", dept = "مدیریت", unit = "ریاست شیفت", social = "wa=1;tg=ali_id")

    @Test fun roundTripKeepsFields() {
        val back = ContactsFile.fromJson(ContactsFile.toJson(listOf(sample()))).single()
        assertEquals(sample(), back)
    }

    @Test fun photoTravelsAsBase64() {
        val bytes = ByteArray(300) { (it % 200).toByte() }
        val back = ContactsFile.fromJson(ContactsFile.toJson(listOf(sample())) { bytes }).single()
        assertEquals(Base64.getEncoder().encodeToString(bytes), back.photoData)
    }

    @Test fun persianDigitsAreFixedAndOptionalFieldsMayBeMissing() {
        val json = """{"app":"shiftma","type":"contacts","version":1,"contacts":[{"name":"الف","landline":"۵۰۲۰"}]}"""
        val c = ContactsFile.fromJson(json).single()
        assertEquals("5020", c.landline); assertEquals("", c.mobile); assertEquals("", c.photoData)
    }

    @Test fun rejectsForeignOrInvalidFiles() {
        assertThrows(IllegalArgumentException::class.java) { ContactsFile.fromJson("""{"app":"shiftma","type":"patterns","version":1,"contacts":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { ContactsFile.fromJson("""{"app":"other","type":"contacts","version":1,"contacts":[{"name":"x"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { ContactsFile.fromJson("""{"app":"shiftma","type":"contacts","version":1,"contacts":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { ContactsFile.fromJson("""{"app":"shiftma","type":"contacts","version":99,"contacts":[{"name":"x"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { ContactsFile.fromJson("""{"app":"shiftma","type":"contacts","version":1,"contacts":[{"name":" "}]}""") }
    }

    @Test fun badPhotoIsDroppedNotFatal() {
        val json = """{"app":"shiftma","type":"contacts","version":1,"contacts":[{"name":"x","photo":"not an image!!"}]}"""
        assertEquals("", ContactsFile.fromJson(json).single().photoData)
    }

    @Test fun typeIsDetectedFromHead() {
        assertEquals("contacts", ContactsFile.typeOf("""{"app":"shiftma","type":"contacts","version":1,"contacts":[]}"""))
        assertEquals("patterns", ContactsFile.typeOf("""{"app":"shiftma","type":"patterns","version":1,"patterns":[]}"""))
        assertEquals("patterns", ContactsFile.typeOf("not json"))
    }

    @Test fun prefixIsOptionalAndValidated() {
        val withPre = ContactsFile.toJson(listOf(sample()), null, "+98211234,")
        assertEquals("+98211234,", ContactsFile.prefixOf(withPre))
        assertEquals(1, ContactsFile.fromJson(withPre).size)
        assertEquals("", ContactsFile.prefixOf(ContactsFile.toJson(listOf(sample()))))
        assertEquals("", ContactsFile.prefixOf(ContactsFile.toJson(listOf(sample()), null, "x y")))
    }
}
