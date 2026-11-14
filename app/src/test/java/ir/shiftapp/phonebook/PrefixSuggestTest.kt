package ir.shiftapp.phonebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefixSuggestTest {
    private fun c(landline: String, mobile: String = "") = Contact(name = "x", landline = landline, mobile = mobile)

    @Test fun suggestsCommonSevenDigitsFromFullLandlines() {
        val s = PrefixSuggest.suggest(listOf(c("5020"), c("5021"), c("04141711234"), c("۰۴۱۴۱۷۱۵۶۷۸"), c("02188776655")))!!
        assertEquals("0414171", s.local)
        assertEquals("+98414171", s.intl)
        assertEquals(2, s.extensions)
    }

    @Test fun internationalFormIsUnderstood() {
        val s = PrefixSuggest.suggest(listOf(c("1234"), c("+984141711111"), c("00984141712222")))!!
        assertEquals("0414171", s.local)
    }

    @Test fun noExtensionsOrSingleSampleOrMobilesGivesNull() {
        assertNull(PrefixSuggest.suggest(listOf(c("04141711234"), c("04141715678"))))                 // داخلی ندارد
        assertNull(PrefixSuggest.suggest(listOf(c("5020"), c("04141711234"))))                         // فقط یک نمونه
        assertNull(PrefixSuggest.suggest(listOf(c("5020"), c("", "09121234567"), c("", "09121234568")))) // موبایل شمرده نمی‌شود
    }

    @Test fun sungunHasKnownPrefixOthersAskUser() {
        assertEquals("0414171", PrefixSuggest.forRegion("sungun"))
        assertEquals("0414171", PrefixSuggest.forRegion(" Sungun "))
        assertNull(PrefixSuggest.forRegion("sarcheshmeh"))
        assertNull(PrefixSuggest.forRegion(""))
    }

    @Test fun sungunDetectedFromManyLocalMobiles() {
        val many = (1..40).map { c("", "0914${1000000 + it}") } + (1..20).map { c("", "0912${1000000 + it}") }
        assertEquals("0414171", PrefixSuggest.byNumbers(many))
        assertNull(PrefixSuggest.byNumbers((1..10).map { c("", "0914${1000000 + it}") }))                         // کم است
        assertNull(PrefixSuggest.byNumbers((1..40).map { c("", "0914${1000000 + it}") } + (1..200).map { c("", "0912${1000000 + it}") }))   // سهم کم
        assertNull(PrefixSuggest.byNumbers((1..50).map { c("04144541111") }))                                        // ثابت‌ها حساب نمی‌شوند
    }

    @Test fun dialableAppliesToExtensionsOnly() {
        assertEquals("04141715020", PhoneActions.dialable("5020", "0414171"))
        assertEquals("+984141715020", PhoneActions.dialable("5020", "+98414171"))
        assertEquals("09121234567", PhoneActions.dialable("09121234567", "0414171"))
    }
}
