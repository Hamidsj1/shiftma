package ir.shiftapp.core

import ir.shiftapp.data.AppDb
import org.junit.Assert.*
import org.junit.Test

class PatternSetsTest {
    @Test fun letterOfParsesGroupNames() {
        assertEquals('A', PatternSets.letterOf("A"))
        assertEquals('D', PatternSets.letterOf("شیفت D"))
        assertNull(PatternSets.letterOf("پلی‌اکریل A"))
        assertNull(PatternSets.letterOf("AB"))
    }

    @Test fun oldLetterNamedPatternsFormOneSetWithoutData() {
        assertEquals(PatternSets.DEFAULT_SET, PatternSets.keyOf("B", ""))
        assertEquals("", PatternSets.keyOf("الگوی من", ""))
        assertEquals("چهارشیفته", PatternSets.keyOf("A", "چهارشیفته"))
    }

    @Test fun groupKeepsOrderAndSingles() {
        data class P(val n: String, val s: String)
        val l = listOf(P("A", ""), P("x", ""), P("B", ""), P("y", ""), P("C", "T"), P("D", "T"))
        val g = PatternSets.group(l) { PatternSets.keyOf(it.n, it.s) }
        assertEquals(listOf(listOf("A", "B"), listOf("x"), listOf("y"), listOf("C", "D")), g.map { x -> x.map { it.n } })
    }

    private data class Q(val id: Long, val n: String, val s: String)
    private fun slots(all: List<Q>, sel: Q) = PatternSets.crewSlots(all, sel, 4, { PatternSets.keyOf(it.n, it.s) }, { PatternSets.letterOf(it.n) }, { it.id })

    @Test fun crewSlotsFollowsSetInLetterOrder() {
        val all = listOf(Q(1, "A", "X"), Q(2, "B", "X"), Q(3, "C", "X"), Q(4, "D", "X"), Q(5, "B", "Y"), Q(6, "A", "Y"))
        assertEquals(listOf(1L, 2L, 3L, 4L), slots(all, all[2]))
        assertEquals(listOf(6L, 5L, 0L, 0L), slots(all, all[4]))          // دو عضو: ترتیب A, B و ستون‌های بعدی خالی
        assertNull(slots(all + Q(7, "الگوی من", ""), Q(7, "الگوی من", "")))   // مستقل: پیشنهادی نیست
        assertNull(slots(listOf(Q(1, "A", "Z")), Q(1, "A", "Z")))             // مجموعهٔ تک‌عضوی
    }

    @Test fun followsGroupOnlyWhenUntouched() {
        assertTrue(PatternSets.followsGroup(listOf(0, 0, 0, 0), null))
        assertTrue(PatternSets.followsGroup(listOf(1, 2, 3, 4), listOf(1, 2, 3, 4)))
        assertFalse(PatternSets.followsGroup(listOf(1, 2, 9, 4), listOf(1, 2, 3, 4)))   // ستون دستی عوض‌شده: دست نمی‌خورد
        assertFalse(PatternSets.followsGroup(listOf(1, 2, 3, 4), null))
    }

    @Test fun migrationOnlyAddsOptionalColumnWithEmptyDefault() {
        // مهاجرت واقعی روی دستگاه تست می‌شود؛ اینجا قفل می‌کنیم که فقط یک ADD COLUMN با پیش‌فرض خالی باشد (هیچ حذف/بازنویسی دادهٔ کاربر نیست)
        assertEquals("ALTER TABLE `shift_patterns` ADD COLUMN `setName` TEXT NOT NULL DEFAULT ''", AppDb.SQL_3_4)
        assertFalse(AppDb.SQL_3_4.contains("DROP", ignoreCase = true))
        assertFalse(AppDb.SQL_3_4.contains("DELETE", ignoreCase = true))
        assertEquals(3, AppDb.MIGRATION_3_4.startVersion); assertEquals(4, AppDb.MIGRATION_3_4.endVersion)
    }
}
