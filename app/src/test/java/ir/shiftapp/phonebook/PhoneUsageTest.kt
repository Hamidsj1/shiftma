package ir.shiftapp.phonebook

import org.junit.Assert.*
import org.junit.Test

class PhoneUsageTest {
    private val a = Contact(1, "آرش", mobile = "09121112233")
    private val b = Contact(2, "بهرام", mobile = "09124445566", landline = "1234")
    private val c = Contact(3, "ستاره", landline = "4321")
    private val all = listOf(a, b, c)   // ترتیب نام

    @Test fun keyIgnoresPrefixAndDigitsStyle() {
        assertEquals("9121112233", PhoneUsage.key("09121112233"))
        assertEquals("9121112233", PhoneUsage.key("+98 912 111 2233"))
        assertEquals("9121112233", PhoneUsage.key("۰۹۱۲۱۱۱۲۲۳۳"))
        assertEquals("1234", PhoneUsage.key("۱۲۳۴"))
        assertEquals("", PhoneUsage.key("---"))
    }

    @Test fun noStatsKeepsOrder() {
        assertEquals(all, PhoneUsage.rank(all, emptyMap()))
    }

    @Test fun usedContactsMoveUpByCountThenRecency() {
        var s = emptyMap<String, PhoneUsage.Stat>()
        s = PhoneUsage.bump(s, "4321", 100)          // ستاره ۱ بار
        s = PhoneUsage.bump(s, "09124445566", 200)   // بهرام ۱ بار، تازه‌تر
        assertEquals(listOf(2L, 3L, 1L), PhoneUsage.rank(all, s).map { it.id })
        s = PhoneUsage.bump(s, "4321", 300)          // ستاره ۲ بار
        assertEquals(listOf(3L, 2L, 1L), PhoneUsage.rank(all, s).map { it.id })
    }

    @Test fun eitherNumberCountsForTheContact() {
        val s = PhoneUsage.bump(PhoneUsage.bump(emptyMap(), "1234", 1), "09124445566", 2)   // هر دو شمارهٔ بهرام
        assertEquals(2, PhoneUsage.scoreOf(b, s)!!.count)
        assertNull(PhoneUsage.scoreOf(a, s))
    }

    @Test fun statsPrunedToLimit() {
        var s = emptyMap<String, PhoneUsage.Stat>()
        for (i in 0 until PhoneUsage.MAX_STATS + 20) s = PhoneUsage.bump(s, "55" + i.toString().padStart(6, '0'), i.toLong())
        assertEquals(PhoneUsage.MAX_STATS, s.size)
    }

    @Test fun historyDedupesTrimsAndCaps() {
        var h = emptyList<String>()
        h = PhoneUsage.addHistory(h, "  علی ")
        h = PhoneUsage.addHistory(h, "۱۲۳۴")
        h = PhoneUsage.addHistory(h, "1234")          // همان ۱۲۳۴ با رقم دیگر: یکی می‌شود و به ابتدا می‌آید
        assertEquals(listOf("1234", "علی"), h)
        assertEquals(h, PhoneUsage.addHistory(h, "ب"))   // کوتاه‌تر از ۲ نویسه ثبت نمی‌شود
        for (i in 0 until 30) h = PhoneUsage.addHistory(h, "جست $i")
        assertEquals(PhoneUsage.MAX_HISTORY, h.size)
        assertEquals("جست 29", h.first())
    }

    @Test fun suggestionsFilterByPrefix() {
        val h = listOf("علی رضایی", "برق", "علی")
        assertEquals(h, PhoneUsage.suggestions(h, ""))
        assertEquals(listOf("علی رضایی"), PhoneUsage.suggestions(h, "علی"))   // خودِ متن تایپ‌شده پیشنهاد نمی‌شود
        assertTrue(PhoneUsage.suggestions(h, "x").isEmpty())
    }
}
