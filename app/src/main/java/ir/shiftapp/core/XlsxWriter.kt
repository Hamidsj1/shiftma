package ir.shiftapp.core

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** نویسندهٔ حداقلیِ XLSX بدون هیچ کتابخانه‌ای: فقط یک شیت، متن inline، رنگ پس‌زمینه و جهت راست‌به‌چپ. */
object XlsxWriter {
    private fun esc(s: String) = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (c.code >= 32 || c == '\n' || c == '\t') append(c)   // کاراکترهای کنترلی در XML مجاز نیستند
            }
        }
    }
    private fun colName(i: Int): String { var n = i; val sb = StringBuilder(); do { sb.insert(0, ('A' + n % 26)); n = n / 26 - 1 } while (n >= 0); return sb.toString() }
    private fun rgb(v: Long) = "FF%06X".format(v and 0xFFFFFF)

    fun write(t: GridTable, out: OutputStream) {
        // ایندکس سبک: 0 پیش‌فرض، 1 سرستون، 2 عنوان، سپس یک سبک برای هر رنگ پس‌زمینه
        val fills = (t.rows.flatten().mapNotNull { it.bg }).distinct()
        val fgs = (t.rows.flatten().mapNotNull { it.fg }).distinct()
        // xf ها به ترتیب: 0,1,2 ثابت؛ سپس برای هر ترکیب (bg in -1..n-1) × (fg in -1..m-1) به‌جز (-1,-1)
        data class Xf(val bi: Int, val fi: Int)
        val combos = mutableListOf<Xf>()
        for (b in -1 until fills.size) for (f in -1 until fgs.size) if (!(b == -1 && f == -1)) combos.add(Xf(b, f))
        fun styleIndex(c: GridCell): Int {
            val bi = c.bg?.let { fills.indexOf(it) } ?: -1
            val fi = c.fg?.let { fgs.indexOf(it) } ?: -1
            if (bi == -1 && fi == -1) return 0
            return 3 + combos.indexOf(Xf(bi, fi))
        }

        val styles = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<fonts count=\"${2 + fgs.size + 1}\">")
            append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>")
            fgs.forEach { append("<font><sz val=\"11\"/><color rgb=\"${rgb(it)}\"/><name val=\"Calibri\"/></font>") }
            append("<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font></fonts>")
            append("<fills count=\"${3 + fills.size}\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>")
            append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF5252B5\"/><bgColor indexed=\"64\"/></patternFill></fill>")
            fills.forEach { append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"${rgb(it)}\"/><bgColor indexed=\"64\"/></patternFill></fill>") }
            append("</fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>")
            append("<border><left style=\"thin\"><color rgb=\"FFBBBBBB\"/></left><right style=\"thin\"><color rgb=\"FFBBBBBB\"/></right><top style=\"thin\"><color rgb=\"FFBBBBBB\"/></top><bottom style=\"thin\"><color rgb=\"FFBBBBBB\"/></bottom><diagonal/></border></borders>")
            append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
            append("<cellXfs count=\"${3 + combos.size}\">")
            append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>")
            append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>")
            append("<xf numFmtId=\"0\" fontId=\"${2 + fgs.size}\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>")
            combos.forEach { x ->
                val font = if (x.fi >= 0) 2 + x.fi else 0
                val fill = if (x.bi >= 0) 3 + x.bi else 0
                append("<xf numFmtId=\"0\" fontId=\"$font\" fillId=\"$fill\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>")
            }
            append("</cellXfs></styleSheet>")
        }

        fun cell(col: Int, row: Int, text: String, style: Int) =
            if (text.isEmpty()) "<c r=\"${colName(col)}$row\" s=\"$style\"/>"
            else "<c r=\"${colName(col)}$row\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(text)}</t></is></c>"

        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews><cols>")
            t.weights.forEachIndexed { i, w -> append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"${"%.1f".format(java.util.Locale.ROOT, w * 9f)}\" customWidth=\"1\"/>") }
            append("</cols><sheetData>")
            var r = 1
            append("<row r=\"$r\">${cell(0, r, t.title, 2)}</row>"); r++
            append("<row r=\"$r\">${cell(0, r, t.subtitle, 0)}</row>"); r += 2
            append("<row r=\"$r\">"); t.headers.forEachIndexed { i, h -> append(cell(i, r, h, 1)) }; append("</row>"); r++
            for (row in t.rows) { append("<row r=\"$r\">"); row.forEachIndexed { i, c -> append(cell(i, r, c.text, styleIndex(c))) }; append("</row>"); r++ }
            r++
            for (f in t.footer) { append("<row r=\"$r\">${cell(0, r, f, 0)}</row>"); r++ }
            append("</sheetData></worksheet>")
        }

        ZipOutputStream(out).use { z ->
            fun put(name: String, body: String) { z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="کاردکس" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheet)
        }
    }
}
