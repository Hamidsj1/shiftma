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

    @Test fun migrationOnlyAddsOptionalColumnWithEmptyDefault() {
        // مهاجرت واقعی روی دستگاه تست می‌شود؛ اینجا قفل می‌کنیم که فقط یک ADD COLUMN با پیش‌فرض خالی باشد (هیچ حذف/بازنویسی دادهٔ کاربر نیست)
        assertEquals("ALTER TABLE `shift_patterns` ADD COLUMN `setName` TEXT NOT NULL DEFAULT ''", AppDb.SQL_3_4)
        assertFalse(AppDb.SQL_3_4.contains("DROP", ignoreCase = true))
        assertFalse(AppDb.SQL_3_4.contains("DELETE", ignoreCase = true))
        assertEquals(3, AppDb.MIGRATION_3_4.startVersion); assertEquals(4, AppDb.MIGRATION_3_4.endVersion)
    }
}
