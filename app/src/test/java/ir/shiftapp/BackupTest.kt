package ir.shiftapp

import ir.shiftapp.core.EntryType
import ir.shiftapp.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val snap = Snapshot(
        listOf(PatternEntity(1, "پلی‌اکریل A", "x", 1398, 1, 5, "1;1;2;2;4;3;3;4")),
        listOf(DayEntry(1, 1, "14000101", EntryType.EXCHANGE, 3, null, "t", 7), DayEntry(2, 1, "14000102", EntryType.OVERTIME, null, 90, "", null)),
        listOf(Employee(7, "علی", "")), listOf(Setting("selected_pattern", "1"))
    )
    @Test fun roundTrip() = assertEquals(snap, Backup.fromJson(Backup.toJson(snap)))
    @Test fun setNameSurvivesRoundTripAndOldBackupsStillLoad() {
        val withSet = snap.copy(patterns = listOf(PatternEntity(1, "A", "x", 1405, 7, 19, "1;1;4;3;3;4;4;4", setName = "چهارشیفته")))
        assertEquals("چهارشیفته", Backup.fromJson(Backup.toJson(withSet)).patterns.single().setName)
        // پشتیبان نسخهٔ قدیم (بدون کلید setName) بدون خطا و با مجموعهٔ خالی خوانده می‌شود
        val o = org.json.JSONObject(Backup.toJson(snap))
        val arr = o.getJSONArray("patterns")
        for (i in 0 until arr.length()) arr.getJSONObject(i).remove("setName")
        val old = o.toString()
        assertFalse(old.contains("setName"))
        assertEquals("", Backup.fromJson(old).patterns.single().setName)
    }
    @Test fun rejectsForeignFile() { assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""{"app":"other","version":1}""") } }
    @Test fun rejectsInvalidDate() {
        val bad = Backup.toJson(snap).replace("14000101", "13980230")
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson(bad) }
    }
    @Test fun rejectsBadPattern() {
        val bad = Backup.toJson(snap).replace("1;1;2;2;4;3;3;4", "1;;x")
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson(bad) }
    }
}

class BackupHolidayTest {
    @Test fun holidaysAndPrefsSurviveRoundTrip() {
        val s = Snapshot(emptyList(), emptyList(), emptyList(),
            listOf(Setting("pref_theme", "dark")), listOf(Holiday("14050720", "تعطیلی آزمایشی")))
        assertEquals(s, Backup.fromJson(Backup.toJson(s)))
    }
    @Test fun version1BackupStillLoads() {
        val v1 = """{"app":"shiftma","version":1,"patterns":[],"entries":[],"employees":[],"settings":[]}"""
        assertEquals(emptyList<Holiday>(), Backup.fromJson(v1).holidays)
    }
    @Test fun rejectsBadHolidayDate() {
        val bad = """{"app":"shiftma","version":2,"patterns":[],"entries":[],"employees":[],"settings":[],"holidays":[{"dateKey":"14000230","title":"x"}]}"""
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson(bad) }
    }
}
