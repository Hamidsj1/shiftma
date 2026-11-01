package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { out[e.name] = z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
        }
        return out
    }

    private val table = GridTable(
        "عنوان & <تست>", "فروردین", listOf("روز", "شیفت"), listOf(1f, 2f),
        listOf(listOf(GridCell("۱", fg = 0xFFC62828), GridCell("روزکار", bg = 0xFFBADC58)), listOf(GridCell("۲"), GridCell(""))),
        listOf("جمع: ۱۶:۰۰")
    )

    @Test fun producesAllPartsWithEscapedText() {
        val bos = ByteArrayOutputStream(); XlsxWriter.write(table, bos)
        val parts = unzip(bos.toByteArray())
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"), parts.keys)
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("عنوان &amp; &lt;تست&gt;")); assertTrue(sheet.contains("rightToLeft=\"1\""))
        assertTrue(sheet.contains("روزکار")); assertTrue(sheet.contains("جمع: ۱۶:۰۰"))
    }

    @Test fun styleCountsMatchCellXfs() {
        val bos = ByteArrayOutputStream(); XlsxWriter.write(table, bos)
        val styles = unzip(bos.toByteArray()).getValue("xl/styles.xml")
        // ۳ سبک ثابت + (۱ رنگ زمینه، ۱ رنگ متن) => ترکیب‌های (bg,-) (-,fg) (bg,fg) = ۳ => ۶
        assertTrue(styles.contains("<cellXfs count=\"6\">"))
        assertEquals(6, Regex("<xf ").findAll(styles.substringAfter("<cellXfs")).count())
        // هر s="..." در شیت باید کمتر از ۶ باشد
        val sheet = unzip(bos.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(Regex("s=\"(\\d+)\"").findAll(sheet).all { it.groupValues[1].toInt() < 6 })
    }
}
