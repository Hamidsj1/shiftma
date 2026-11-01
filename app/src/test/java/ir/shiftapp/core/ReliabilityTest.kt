package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityTest {
    private fun rec(due: Long, delay: Long, kind: String = FireLog.KIND_ALARM) = FireRecord(due, due + delay, kind)

    @Test fun encodeDecodeRoundTrip() {
        val list = listOf(rec(1000, 0), rec(2000, 90_000, FireLog.KIND_PRAYER), rec(3000, 5_000, FireLog.KIND_SHIFT))
        assertEquals(list, FireLog.decode(FireLog.encode(list)))
    }

    @Test fun decodeIgnoresGarbageAndNeverThrows() {
        assertTrue(FireLog.decode(null).isEmpty())
        assertTrue(FireLog.decode("").isEmpty())
        assertTrue(FireLog.decode("abc;1,2;x,y,alarm;1,2,unknown").isEmpty())
        // رکورد سالم کنار رکورد خراب حفظ می‌شود
        assertEquals(listOf(rec(10, 5)), FireLog.decode("bad;10,15,alarm;1,2"))
    }

    @Test fun appendKeepsOnlyLastMax() {
        var list = emptyList<FireRecord>()
        for (i in 1..(FireLog.MAX + 10)) list = FireLog.append(list, rec(i.toLong(), 0))
        assertEquals(FireLog.MAX, list.size)
        assertEquals(11L, list.first().dueMs)
        assertEquals((FireLog.MAX + 10).toLong(), list.last().dueMs)
    }

    @Test fun lateThresholdIsOneMinute() {
        assertFalse(FireLog.isLate(rec(0, 60_000)))
        assertTrue(FireLog.isLate(rec(0, 60_001)))
        assertFalse(FireLog.isLate(rec(0, -500)))   // زودتر از برنامه (ساعت سیستم عوض شده) دیررسیده نیست
    }

    @Test fun summaryCountsLateAndWorst() {
        val s = FireLog.summary(listOf(rec(0, 0), rec(0, 120_000), rec(0, 300_000), rec(0, -9_000)))
        assertEquals(4, s.total)
        assertEquals(2, s.late)
        assertEquals(300_000L, s.worstDelayMs)
        assertEquals(FireSummary(0, 0, 0L), FireLog.summary(emptyList()))
    }

    @Test fun delayLabels() {
        assertEquals("به‌موقع", FireLog.delayLabel(0))
        assertEquals("به‌موقع", FireLog.delayLabel(9_999))
        assertEquals(Fa.digits("30 ثانیه دیر"), FireLog.delayLabel(30_000))
        assertEquals(Fa.digits("2 دقیقه دیر"), FireLog.delayLabel(125_000))
        assertEquals(Fa.digits("1 ساعت دیر"), FireLog.delayLabel(3_600_000))
        assertEquals(Fa.digits("1 ساعت و 5 دقیقه دیر"), FireLog.delayLabel(3_900_000))
    }

    @Test fun kindTitles() {
        assertEquals("اذان", FireLog.kindTitle(FireLog.KIND_PRAYER))
        assertEquals("آلارم", FireLog.kindTitle(FireLog.KIND_ALARM))
        assertEquals("هشدار", FireLog.kindTitle("x"))
    }

    @Test fun reportListsLast20AndHeader() {
        val list = (1..25).map { rec(it * 1000L, 0) }
        val text = FireLog.report(list, "Xiaomi", 34) { "t$it" }
        assertTrue(text.contains("Xiaomi"))
        assertTrue(text.contains("API 34"))
        assertEquals(1 + 1 + 1 + 20, text.lines().size)   // عنوان + دستگاه + شمارش + ۲۰ ردیف
        assertFalse(text.contains("t1000 "))              // قدیمی‌ترها بیرون می‌مانند
    }

    @Test fun oemTipsBrandMatching() {
        assertTrue(OemGuide.hasBrandTips("Xiaomi"))
        assertTrue(OemGuide.hasBrandTips("POCO"))
        assertTrue(OemGuide.hasBrandTips("HUAWEI"))
        assertTrue(OemGuide.hasBrandTips("samsung"))
        assertTrue(OemGuide.hasBrandTips("OnePlus"))
        assertTrue(OemGuide.hasBrandTips("TECNO"))
        assertFalse(OemGuide.hasBrandTips("Google"))
        assertFalse(OemGuide.hasBrandTips(""))
    }

    @Test fun genericTipsAlwaysPresent() {
        assertTrue(OemGuide.tips("Google").size >= 2)
        assertTrue(OemGuide.tips("Xiaomi").size > OemGuide.tips("Google").size)
    }
}
