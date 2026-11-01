package ir.shiftapp.phonebook

import org.junit.Assert.*
import org.junit.Test

class SocialTest {
    @Test fun intlNumberVariants() {
        assertEquals("989121234567", Social.intlNumber("09121234567"))
        assertEquals("989121234567", Social.intlNumber("+98 912 123 4567"))
        assertEquals("989121234567", Social.intlNumber("۰۹۱۲۱۲۳۴۵۶۷"))
        assertEquals("989121234567", Social.intlNumber("00989121234567"))
        assertEquals("989121234567", Social.intlNumber("9121234567"))
        assertNull(Social.intlNumber("1234"))
        assertNull(Social.intlNumber(""))
    }

    @Test fun encodeParseRoundTripAndCleaning() {
        val m = mapOf("wa" to "1", "tg" to "ali_1", "rubika" to "r.x")
        assertEquals(m, Social.parse(Social.encode(m)))
        assertEquals("ali_1", Social.cleanId("@ali_1"))
        assertEquals("ali_1", Social.cleanId("https://t.me/ali_1"))
        assertEquals("", Social.parse("eitaa=1;wa=2;xx=3;bale=").let { Social.encode(it) })
        assertEquals("wa=1", Social.normalize("wa=1;eitaa=1"))
    }

    @Test fun linksPerMessenger() {
        val c = Contact(name = "t", mobile = "09121234567", social = "wa=1;tg=1;eitaa=e1;bale=b1;rubika=r1")
        val l = Social.links(c).toMap()
        assertEquals("https://wa.me/989121234567", l["واتساپ"])
        assertEquals("tg://resolve?phone=989121234567", l["تلگرام"])
        assertEquals("https://t.me/+989121234567", Social.webFallback(l["تلگرام"]!!))
        assertEquals("https://eitaa.com/e1", l["ایتا"])
        assertEquals("https://ble.ir/b1", l["بله"])
        assertEquals("https://rubika.ir/r1", l["روبیکا"])
        assertTrue(Social.searchOnly(c).isEmpty())
        assertEquals(listOf(Messenger.EITAA, Messenger.BALE, Messenger.RUBIKA), Social.searchOnly(Contact(name = "t", mobile = "0912", social = "wa=1")))
        assertEquals(listOf(Messenger.BALE), Social.searchOnly(Contact(name = "t", mobile = "0912", social = "eitaa=a;rubika=b")))
        assertEquals("tg://resolve?domain=ali", Social.links(Contact(name = "t", social = "tg=ali")).single().second)
        assertEquals("https://t.me/ali", Social.webFallback("tg://resolve?domain=ali"))
        assertTrue(Social.links(Contact(name = "t", mobile = "", social = "wa=1")).isEmpty())
        assertEquals(listOf("واتساپ", "تلگرام"), Social.links(Contact(name = "t", mobile = "09121234567")).map { it.first })
    }

    @Test fun csvEighthColumnIsParsed() {
        val c = parseCsv("علی,09121234567,,,,,,wa=1;rubika=r1").single()
        assertEquals("wa=1;rubika=r1", c.social)
        assertEquals("", parseCsv("علی,0912,").single().social)
    }
}
