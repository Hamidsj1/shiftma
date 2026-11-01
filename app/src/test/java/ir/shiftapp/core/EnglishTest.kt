package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class EnglishTest {
    @Test fun wordsAreWellFormed() {
        assertTrue(English.WORDS.size >= 150)
        assertEquals(English.WORDS.size, English.WORDS.map { it.id }.toSet().size)
        English.WORDS.forEach { w ->
            assertTrue(w.en.isNotBlank() && w.fa.isNotBlank() && w.example.isNotBlank())
            assertTrue("${w.en} level", w.level in 1..4)
        }
        for (l in 1..4) assertTrue("level $l needs >= 4 words for quiz", English.WORDS.count { it.level == l } >= 4)
    }

    @Test fun wordOfDayIsStableAndSafeForNegatives() {
        assertEquals(English.wordOfDay(20000), English.wordOfDay(20000))
        assertNotEquals(English.wordOfDay(20000, 0), English.wordOfDay(20000, 1))
        assertNotNull(English.wordOfDay(-5, -3))
    }

    @Test fun leitnerBoxesAndDueDates() {
        var p = EnglishProgress()
        val id = 1
        assertTrue(id in p.newIds())
        p = p.answer(id, true, 100)
        assertEquals(CardState(1, 101), p.cards[id])
        p = p.answer(id, true, 101)
        assertEquals(CardState(2, 103), p.cards[id])
        p = p.answer(id, true, 103).answer(id, true, 107)
        assertEquals(4, p.cards[id]!!.box)
        assertEquals(1, p.learned)
        p = p.answer(id, true, 115).answer(id, true, 131)
        assertEquals(CardState(5, 147), p.cards[id])                 // سقف جعبه ۵
        p = p.answer(id, false, 147)
        assertEquals(CardState(1, 148), p.cards[id])
        assertEquals(0, p.learned)
        assertFalse(id in p.newIds())
        assertTrue(p.dueIds(148).contains(id))
        assertFalse(p.dueIds(147).contains(id))
    }

    @Test fun streakAndDailyCount() {
        var p = EnglishProgress()
        p = p.answer(1, true, 10).answer(2, true, 10)
        assertEquals(1, p.streak); assertEquals(2, p.doneOn(10)); assertEquals(0, p.doneOn(11))
        p = p.answer(3, true, 11)
        assertEquals(2, p.streakOn(11)); assertEquals(1, p.doneOn(11))
        assertEquals(2, p.streakOn(12))                               // دیروز مرور شده، هنوز نشکسته
        assertEquals(0, p.streakOn(13))                               // یک روز جا افتاده
        p = p.answer(4, true, 14)
        assertEquals(1, p.streak)
    }

    @Test fun encodeDecodeRoundTripAndCorruptInput() {
        val p = EnglishProgress(goal = 20).answer(1, true, 50).answer(5, false, 50).answer(9, true, 51)
        assertEquals(p, EnglishProgress.decode(p.encode()))
        assertEquals(EnglishProgress(), EnglishProgress.decode(EnglishProgress().encode()))
        assertEquals(EnglishProgress(), EnglishProgress.decode(null))
        assertEquals(EnglishProgress(), EnglishProgress.decode("خراب"))
        assertEquals(EnglishProgress(), EnglishProgress.decode("1;2|x"))
        // ردیف خراب یا لغت ناموجود نادیده گرفته می‌شود، بقیه می‌ماند
        val d = EnglishProgress.decode("10;1;5;5;1|1:2:7,zzz,99999:1:1,3:9:1,4:1:2")
        assertEquals(setOf(1, 4), d.cards.keys)
    }

    @Test fun quizChoicesContainAnswerOnceWithoutDuplicateMeanings() {
        for (w in English.WORDS) {
            val c = English.choices(w, 42L)
            assertEquals(4, c.size)
            assertEquals(1, c.count { it.id == w.id })
            assertEquals(4, c.map { it.fa }.toSet().size)
        }
    }

    @Test fun prefsKeepEnglishProgress() {
        val p = Prefs(english = EnglishProgress().answer(2, true, 9))
        assertEquals(p, Prefs.from(p.toMap()))
    }
}
