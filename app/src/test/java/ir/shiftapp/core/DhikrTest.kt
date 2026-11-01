package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrTest {
    private fun tapN(s: DhikrState, n: Int, day: Int = 100): DhikrState {
        var x = s
        repeat(n) { x = x.tap(day) }
        return x
    }

    @Test fun zahraHasThreePhasesTotal100() {
        val c = DhikrState().current()
        assertEquals("zahra", c.id)
        assertEquals(100, c.total)
        assertEquals(listOf(34, 33, 33), c.phases.map { it.target })
    }

    @Test fun positionMovesThroughPhases() {
        assertEquals(0 to 0, tapN(DhikrState(), 0).current().position)
        assertEquals(0 to 33, tapN(DhikrState(), 33).current().position)
        assertEquals(1 to 0, tapN(DhikrState(), 34).current().position)
        assertEquals(2 to 0, tapN(DhikrState(), 67).current().position)
        val done = tapN(DhikrState(), 100).current()
        assertTrue(done.done)
        assertEquals(2 to 33, done.position)
    }

    @Test fun tapStopsAtTotalAndDoesNotCountExtra() {
        val s = tapN(DhikrState(), 100)
        val again = s.tap(100)
        assertEquals(s, again)
        assertEquals(100, again.todayCount(100))
        assertEquals(100L, again.lifetime)
    }

    @Test fun todayRollsOverWithDay() {
        var s = tapN(DhikrState(), 3, day = 100)
        assertEquals(3, s.todayCount(100))
        assertEquals(0, s.todayCount(101))
        s = s.tap(101)
        assertEquals(1, s.todayCount(101))
        assertEquals(4L, s.lifetime)
    }

    @Test fun undoStepsBackAndAdjustsStats() {
        val s = tapN(DhikrState(), 5).undo(100)
        assertEquals(4, s.current().count)
        assertEquals(4, s.todayCount(100))
        assertEquals(4L, s.lifetime)
        assertEquals(DhikrState(), DhikrState().undo(100))   // روی صفر چیزی عوض نمی‌شود
    }

    @Test fun resetClearsOnlyThatCounter() {
        var s = tapN(DhikrState(), 10).select("salavat")
        s = tapN(s, 4)
        s = s.reset("zahra")
        assertEquals(0, s.counters().first { it.id == "zahra" }.count)
        assertEquals(4, s.counters().first { it.id == "salavat" }.count)
    }

    @Test fun selectIgnoresUnknownId() {
        assertEquals("zahra", DhikrState().select("nope").selected)
        assertEquals("tahlil", DhikrState().select("tahlil").selected)
    }

    @Test fun customAddValidateAndSelect() {
        val s = DhikrState().addCustom("  یا فتاح  ", 313)
        assertEquals(1, s.custom.size)
        assertEquals("یا فتاح", s.custom[0].title)
        assertEquals(s.custom[0].id, s.selected)
        assertEquals(313, s.current().total)
        assertTrue(s.current().custom)
        // نامعتبرها نادیده گرفته می‌شوند
        assertEquals(s, s.addCustom("", 10))
        assertEquals(s, s.addCustom("x", 0))
        assertEquals(s, s.addCustom("x", DhikrPresets.MAX_TARGET + 1))
    }

    @Test fun customLimitAndUniqueIds() {
        var s = DhikrState()
        repeat(DhikrPresets.MAX_CUSTOM + 3) { s = s.addCustom("ذکر $it", 10) }
        assertEquals(DhikrPresets.MAX_CUSTOM, s.custom.size)
        assertEquals(s.custom.size, s.custom.map { it.id }.toSet().size)
    }

    @Test fun removeCustomFallsBackToDefault() {
        var s = DhikrState().addCustom("الف", 5)
        val id = s.custom[0].id
        s = tapN(s, 2).removeCustom(id)
        assertTrue(s.custom.isEmpty())
        assertEquals(DhikrPresets.DEFAULT_ID, s.selected)
        assertFalse(s.counts.containsKey(id))
        assertEquals(s, s.removeCustom("zzz"))
        // شناسهٔ بعدی دوباره قابل استفاده است و با قبلی تداخل ندارد
        assertEquals("c1", s.addCustom("ب", 5).selected)
    }

    @Test fun codecRoundTrip() {
        var s = DhikrState().addCustom("ذکر من", 40)
        s = tapN(s, 7, day = 200).select("zahra")
        s = tapN(s, 12, day = 200).qazaAdd("fajr", 30).qazaAdd("roza", 5)
        assertEquals(s, DhikrCodec.decode(DhikrCodec.encode(s)))
    }

    @Test fun codecIsTolerantOfGarbage() {
        assertEquals(DhikrState(), DhikrCodec.decode(null))
        assertEquals(DhikrState(), DhikrCodec.decode(""))
        assertEquals(DhikrState(), DhikrCodec.decode("garbage"))
        assertEquals(DhikrState(), DhikrCodec.decode("9\u001dx\u001d1\u001d1\u001d1\u001d\u001d\u001d"))     // نسخهٔ ناشناخته
        // مقدارهای خراب/بیرون از بازه تصحیح یا نادیده گرفته می‌شوند
        val raw = listOf("1", "zahra", "abc", "-5", "x", "zahra\u001f999999\u001eq\u001f1", "c1\u001f\u001f5\u001ec2\u001fok\u001f0", "bogus\u001f3\u001efajr\u001f-4")
            .joinToString("\u001d")
        val s = DhikrCodec.decode(raw)
        assertEquals(0, s.todayEpoch)
        assertEquals(0, s.today)
        assertEquals(0L, s.lifetime)
        assertTrue(s.custom.isEmpty())
        assertEquals(DhikrPresets.MAX_TARGET, s.counts["zahra"])
        assertEquals(mapOf("fajr" to 0), s.qaza)
    }

    @Test fun counterCountIsClampedToTotal() {
        val s = DhikrState(counts = mapOf("zahra" to 5000))
        assertEquals(100, s.current().count)
        assertTrue(s.current().done)
    }

    @Test fun qazaAddClampsAndIgnoresUnknown() {
        var s = DhikrState().qazaAdd("fajr", 3)
        assertEquals(3, s.qazaOf("fajr"))
        s = s.qazaAdd("fajr", -10)
        assertEquals(0, s.qazaOf("fajr"))
        s = s.qazaAdd("fajr", Qaza.MAX + 50)
        assertEquals(Qaza.MAX, s.qazaOf("fajr"))
        assertEquals(s, s.qazaAdd("unknown", 5))
    }

    @Test fun qazaDayAffectsOnlyFivePrayers() {
        val s = DhikrState().qazaAdd("roza", 2).qazaDay(3)
        assertEquals(15, s.qazaPrayerTotal)
        assertEquals(2, s.qazaOf("roza"))
        val back = s.qazaDay(-2)
        assertEquals(5, back.qazaPrayerTotal)
        assertEquals(0, back.qazaDay(-9).qazaPrayerTotal)
    }
}
