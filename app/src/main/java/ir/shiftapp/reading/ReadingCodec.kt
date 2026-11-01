package ir.shiftapp.reading

import org.json.JSONArray
import org.json.JSONObject

/** ذخیره/بازیابی حالت مطالعه به‌صورت یک رشتهٔ JSON در تنظیمات برنامه (همراه فایل پشتیبان می‌آید). */
object ReadingCodec {
    fun encode(st: ReadingState): String = JSONObject().apply {
        put("goal", st.goalMin); put("yearGoal", st.yearGoalBooks)
        put("books", JSONArray().also { a -> st.books.forEach { b -> a.put(JSONObject()
            .put("id", b.id).put("t", b.title).put("a", b.author).put("k", b.kind).put("tp", b.totalPages)
            .put("p", b.page).put("s", b.status).put("r", b.rating).put("fd", b.finishedDay)) } })
        put("notes", JSONArray().also { a -> st.notes.forEach { n -> a.put(JSONObject()
            .put("id", n.id).put("b", n.bookId).put("p", n.page).put("t", n.text).put("d", n.day)) } })
        put("sessions", JSONArray().also { a -> st.sessions.forEach { s -> a.put(JSONArray().put(s.day).put(s.bookId).put(s.minutes).put(s.pages)) } })
    }.toString()

    fun decode(text: String): ReadingState = try {
        val o = JSONObject(text)
        val books = ArrayList<Book>()
        o.optJSONArray("books")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let {
            books.add(Book(it.optLong("id"), it.optString("t"), it.optString("a"), it.optInt("k"), it.optInt("tp"),
                it.optInt("p"), it.optInt("s", 1), it.optInt("r"), it.optInt("fd")))
        } }
        val notes = ArrayList<BookNote>()
        o.optJSONArray("notes")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let {
            notes.add(BookNote(it.optLong("id"), it.optLong("b"), it.optInt("p"), it.optString("t"), it.optInt("d")))
        } }
        val sessions = ArrayList<ReadSession>()
        o.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let {
            sessions.add(ReadSession(it.optInt(0), it.optLong(1), it.optInt(2), it.optInt(3)))
        } }
        ReadingState(books, notes, sessions, o.optInt("goal", 30).coerceIn(5, 600), o.optInt("yearGoal", 12).coerceIn(1, 500))
    } catch (e: Exception) { ReadingState() }
}
