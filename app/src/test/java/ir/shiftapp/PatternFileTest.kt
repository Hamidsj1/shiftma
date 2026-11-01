package ir.shiftapp

import ir.shiftapp.core.PatternPresets
import ir.shiftapp.data.Backup
import ir.shiftapp.data.PatternEntity
import ir.shiftapp.data.PatternFile
import ir.shiftapp.data.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternFileTest {
    private fun sample() = PatternEntity(0, "A", "شرکت", 1405, 7, 19, "1;1;4;3;3;4;4;4", "مسئول", "سرشیفت", "تولید")

    @Test fun roundTripKeepsEveryField() {
        assertEquals(listOf(sample()), PatternFile.fromJson(PatternFile.toJson(listOf(sample()))))
    }

    @Test fun optionalFieldsMayBeMissing() {
        val json = """{"app":"shiftma","type":"patterns","version":1,"patterns":[{"name":"B","baseYear":1405,"baseMonth":7,"baseDay":21,"pattern":"1;4"}]}"""
        val p = PatternFile.fromJson(json).single()
        assertEquals("", p.supervisor); assertEquals("", p.headSupervisor); assertEquals("", p.section); assertEquals("", p.company)
    }

    @Test fun rejectsForeignOrInvalidFiles() {
        assertThrows(IllegalArgumentException::class.java) { PatternFile.fromJson("""{"app":"other","type":"patterns","version":1,"patterns":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { PatternFile.fromJson("""{"app":"shiftma","type":"patterns","version":1,"patterns":[]}""") }
        val badDate = """{"app":"shiftma","type":"patterns","version":1,"patterns":[{"name":"A","baseYear":1400,"baseMonth":12,"baseDay":31,"pattern":"1;4"}]}"""
        assertThrows(IllegalArgumentException::class.java) { PatternFile.fromJson(badDate) }
        val badPattern = """{"app":"shiftma","type":"patterns","version":1,"patterns":[{"name":"A","baseYear":1405,"baseMonth":7,"baseDay":1,"pattern":"9;9"}]}"""
        assertThrows(IllegalArgumentException::class.java) { PatternFile.fromJson(badPattern) }
    }

    @Test fun backupKeepsNewPatternFields() {
        val s = Snapshot(listOf(sample().copy(id = 1)), emptyList(), emptyList(), emptyList())
        assertEquals(s, Backup.fromJson(Backup.toJson(s)))
    }

    @Test fun presetStartDaysAreValid() {
        // روز شروع پیش‌فرض همهٔ الگوها از تاریخ لنگر حساب می‌شود (نه «روز ماه»)
        val today = ir.shiftapp.core.JalaliDate(1405, 8, 10)
        PatternPresets.ALL.forEach { p ->
            val s = p.defaultStart(today)
            assertTrue(p.title, s.isValid && s <= today)
            assertEquals(p.title, 0, Math.floorMod(s.toJdn() - PatternPresets.ANCHOR.toJdn(), p.codes.size))
            assertTrue(p.title, today.toJdn() - s.toJdn() < p.codes.size)
        }
    }
}
