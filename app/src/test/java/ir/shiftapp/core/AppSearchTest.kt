package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private val shift = SearchItem("shift", "جدول شیفت", "تقویم، گروه‌ها، گزارش و مرخصی", AppSearch.keywordsFor("shift"))
    private val notes = SearchItem("notes", "یادداشت‌ها", "متن، چک‌لیست و یادآور", AppSearch.keywordsFor("notes"))
    private val dhikr = SearchItem("dhikr", "ذکر، قضا و رمضان", "ذکرشمار، رکعت‌شمار، نماز قضا و افطار", AppSearch.keywordsFor("dhikr"))
    private val english = SearchItem("english", "یادگیری انگلیسی", "لغت روز، فلش‌کارت و آزمون", AppSearch.keywordsFor("english"))
    private val all = listOf(shift, notes, dhikr, english)

    @Test fun normalizeUnifiesArabicLettersAndZwnj() {
        assertEquals("کتاب", AppSearch.normalize("كتاب"))
        assertEquals("علی", AppSearch.normalize("علي"))
        assertEquals("رکعتشمار", AppSearch.normalize("رکعت\u200cشمار"))
        assertEquals("ذکر", AppSearch.normalize("ذِکْر"))      // اعراب حذف می‌شود
    }

    @Test fun normalizeUnifiesDigitsAndSpaces() {
        assertEquals("123 45", AppSearch.normalize("  ۱۲۳   ٤٥ "))
        assertEquals("pdf", AppSearch.normalize("PDF"))
    }

    @Test fun emptyQueryGivesNothing() {
        assertTrue(AppSearch.search(all, "").isEmpty())
        assertTrue(AppSearch.search(all, "   ").isEmpty())
    }

    @Test fun matchesTitleKeywordAndSubtitle() {
        assertEquals("shift", AppSearch.search(all, "شیفت").first().id)                 // عنوان
        assertEquals(listOf("shift"), AppSearch.search(all, "اکسل").map { it.id })     // واژهٔ کلیدی
        assertEquals(listOf("dhikr"), AppSearch.search(all, "افطار").map { it.id })    // زیرعنوان و کلیدی
    }

    @Test fun ignoresArabicLettersAndZwnjInQuery() {
        assertEquals(listOf("dhikr"), AppSearch.search(all, "رکعت شمار").map { it.id })
        assertEquals(listOf("dhikr"), AppSearch.search(all, "رکعتشمار").map { it.id })
        assertEquals(listOf("english"), AppSearch.search(all, "فلش كارت").map { it.id })
    }

    @Test fun allWordsMustMatch() {
        assertTrue(AppSearch.search(all, "شیفت ورزش").isEmpty())
        assertEquals(listOf("shift"), AppSearch.search(all, "شیفت مرخصی").map { it.id })
    }

    @Test fun titleBeatsKeyword() {
        // «یادآور» عنوان یادداشت‌ها نیست ولی کلیدی آن و زیرعنوانش است؛ عنوانِ شروع‌شونده باید بالاتر بیاید
        val reminder = SearchItem("rem", "یادآور شیفت", "", emptyList())
        val res = AppSearch.search(listOf(notes, reminder), "یادآور")
        assertEquals(listOf("rem", "notes"), res.map { it.id })
    }

    @Test fun tieKeepsOriginalOrder() {
        val a = SearchItem("a", "تست الف")
        val b = SearchItem("b", "تست ب")
        assertEquals(listOf("a", "b"), AppSearch.search(listOf(a, b), "تست").map { it.id })
        assertEquals(listOf("b", "a"), AppSearch.search(listOf(b, a), "تست").map { it.id })
    }

    @Test fun limitApplies() {
        val many = (1..20).map { SearchItem("i$it", "مورد $it") }
        assertEquals(8, AppSearch.search(many, "مورد").size)
        assertEquals(3, AppSearch.search(many, "مورد", limit = 3).size)
    }

    @Test fun noMatchGivesEmpty() {
        assertTrue(AppSearch.search(all, "zzzz").isEmpty())
    }

    @Test fun everyKeywordedIdIsKnown() {
        // هر شناسه‌ای که واژهٔ کلیدی دارد باید واقعاً یکی از بخش‌های برنامه باشد (غلط تایپی در شناسه گرفته شود)
        val known = setOf("shift", "phonebook", "payslips", "meals", "notes", "files", "income", "prayer", "english", "pay", "sport", "reading", "dhikr", "convert", "age", "datecv", "tv", "settings", "about", "span", "imgcompress")
        known.forEach { assertTrue("no keywords for $it", AppSearch.keywordsFor(it).isNotEmpty()) }
        assertTrue(AppSearch.keywordsFor("nope").isEmpty())
    }
}
