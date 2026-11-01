package ir.shiftapp.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatsTest {
    private fun s(day: Int, min: Int, book: Long = 0, pages: Int = 0) = ReadSession(day, book, min, pages)

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val list = listOf(s(10, 20), s(9, 15), s(8, 30), s(6, 10))
        assertEquals(3, ReadingStats.streak(list, 10))
        assertEquals(3, ReadingStats.streak(list, 11))   // امروز هنوز ثبت نشده
        assertEquals(0, ReadingStats.streak(list, 13))
        assertEquals(0, ReadingStats.streak(emptyList(), 10))
    }

    @Test fun last7IsOldestFirst() {
        val list = listOf(s(10, 20), s(10, 5), s(4, 7))
        assertEquals(listOf(7, 0, 0, 0, 0, 0, 25), ReadingStats.last7(list, 10))
    }

    @Test fun applyMovesPageAndFinishesBook() {
        val st = ReadingState(books = listOf(Book(1, "الف", totalPages = 100, page = 90, status = 0)))
        val a = ReadingStats.apply(st, s(5, 30, 1, 5))
        assertEquals(95, a.books[0].page); assertEquals(1, a.books[0].status)
        val b = ReadingStats.apply(a, s(6, 30, 1, 20))
        assertEquals(100, b.books[0].page); assertEquals(2, b.books[0].status); assertEquals(6, b.books[0].finishedDay)
        assertEquals(3, ReadingStats.apply(b, s(7, 10)).sessions.size)
    }

    @Test fun finishedBetween() {
        val books = listOf(Book(1, "a", status = 2, finishedDay = 50), Book(2, "b", status = 2, finishedDay = 5), Book(3, "c", status = 1))
        assertEquals(listOf(1L), ReadingStats.finishedBetween(books, 10, 100).map { it.id })
    }
}
