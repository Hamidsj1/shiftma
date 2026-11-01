package ir.shiftapp.reading

val BOOK_KINDS = listOf("کتاب", "درس / جزوه", "مقاله")
val BOOK_STATUS = listOf("می‌خواهم بخوانم", "در حال خواندن", "تمام شد")

data class Book(
    val id: Long, val title: String, val author: String = "", val kind: Int = 0,
    val totalPages: Int = 0, val page: Int = 0, val status: Int = 1, val rating: Int = 0, val finishedDay: Int = 0
) {
    val progress: Float get() = if (totalPages > 0) (page.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
}

data class BookNote(val id: Long, val bookId: Long, val page: Int, val text: String, val day: Int)

/** یک نوبت مطالعه. bookId = ۰ یعنی مطالعهٔ آزاد (بدون کتاب مشخص). day همان LocalDate.toEpochDay() است. */
data class ReadSession(val day: Int, val bookId: Long, val minutes: Int, val pages: Int)

data class ReadingState(
    val books: List<Book> = emptyList(),
    val notes: List<BookNote> = emptyList(),
    val sessions: List<ReadSession> = emptyList(),
    val goalMin: Int = 30,
    val yearGoalBooks: Int = 12
) {
    fun nextId(): Long = maxOf(books.maxOfOrNull { it.id } ?: 0L, notes.maxOfOrNull { it.id } ?: 0L) + 1L
}

object ReadingStats {
    fun minutesOn(s: List<ReadSession>, day: Int): Int = s.filter { it.day == day }.sumOf { it.minutes }
    fun pagesOn(s: List<ReadSession>, day: Int): Int = s.filter { it.day == day }.sumOf { it.pages }

    /** دقیقهٔ هر روز در ۷ روز اخیر، از قدیمی به جدید (آخری = امروز). */
    fun last7(s: List<ReadSession>, today: Int): List<Int> = (6 downTo 0).map { minutesOn(s, today - it) }

    /** روزهای پیاپی مطالعه؛ اگر امروز هنوز چیزی ثبت نشده، از دیروز شمرده می‌شود تا رشته نپرد. */
    fun streak(s: List<ReadSession>, today: Int): Int {
        val days = s.filter { it.minutes > 0 }.map { it.day }.toSet()
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    /** ثبت یک نوبت و به‌روزرسانی کتاب: صفحه جلو می‌رود و در رسیدن به آخر کتاب، «تمام شد» می‌شود. */
    fun apply(st: ReadingState, ses: ReadSession): ReadingState {
        val books = st.books.map { b ->
            if (b.id != ses.bookId) b else {
                var page = b.page + ses.pages
                if (b.totalPages > 0) page = minOf(page, b.totalPages)
                val done = b.totalPages > 0 && page >= b.totalPages
                b.copy(
                    page = page,
                    status = if (done) 2 else if (b.status == 0) 1 else b.status,
                    finishedDay = if (done && b.status != 2) ses.day else b.finishedDay
                )
            }
        }
        val trimmed = (st.sessions + ses).takeLast(800)
        return st.copy(books = books, sessions = trimmed)
    }

    fun finishedBetween(books: List<Book>, fromDay: Int, toDay: Int): List<Book> =
        books.filter { it.status == 2 && it.finishedDay in fromDay..toDay }
}
