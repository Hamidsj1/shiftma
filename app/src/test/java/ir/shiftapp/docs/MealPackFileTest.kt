package ir.shiftapp.docs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MealPackFileTest {
    private val bytes = ByteArray(500) { (it % 251).toByte() }

    @Test fun roundTripKeepsEverything() {
        val back = MealPackFile.fromJson(MealPackFile.toJson(listOf(MealPackItem("مجتمع الف", "مهر ۱۴۰۵", "menu.pdf", bytes)))).single()
        assertEquals("مجتمع الف", back.name); assertEquals("مهر ۱۴۰۵", back.label); assertEquals("menu.pdf", back.fileName)
        assertArrayEquals(bytes, back.bytes)
    }

    @Test fun rejectsForeignOrBrokenPacks() {
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"contacts","version":1,"complexes":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"meals","version":1,"complexes":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"meals","version":9,"complexes":[{"name":"a","fileName":"a.pdf","data":"AAAA"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"meals","version":1,"complexes":[{"name":"a","fileName":"a.exe","data":"AAAA"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"meals","version":1,"complexes":[{"name":"a","fileName":"a.pdf","data":"!!!"}]}""") }
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromJson("""{"app":"shiftma","type":"meals","version":1,"complexes":[{"name":" ","fileName":"a.pdf","data":"AAAA"}]}""") }
    }

    @Test fun docTypesDetectKinds() {
        assertEquals(DocKind.PDF, DocTypes.kindOf("a.PDF", null))
        assertEquals(DocKind.IMAGE, DocTypes.kindOf("photo.jpeg", null))
        assertEquals(DocKind.IMAGE, DocTypes.kindOf("noext", "image/png"))
        assertEquals(DocKind.SLIDES, DocTypes.kindOf("deck.pptx", null))
        assertEquals(DocKind.SLIDES, DocTypes.kindOf("x", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertNull(DocTypes.kindOf("virus.exe", "application/octet-stream"))
        assertEquals("png", DocTypes.extFor(DocKind.IMAGE, "noext", "image/png"))
        assertEquals("application/pdf", DocTypes.mimeOf(DocKind.PDF, "pdf"))
    }

    @Test fun arrayHelpersMatchTheFileFormat() {
        val items = listOf(MealPackItem("الف", "مهر", "a.pdf", bytes), MealPackItem("ب", "", "b.png", bytes))
        val back = MealPackFile.fromArray(MealPackFile.toArray(items))
        assertEquals(listOf("الف", "ب"), back.map { it.name })
        assertThrows(IllegalArgumentException::class.java) { MealPackFile.fromArray(org.json.JSONArray()) }
    }
}
