package ir.shiftapp.core

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * تشخیص خودکار نوع (حکمی/رفاهی) و ماه فیش از متن‌های موجود: متن صفحهٔ سامانه هنگام دانلود، آدرس‌ها و
 * (تا حد ممکن) متن داخل خود PDF. نتیجه فقط پیشنهاد است و کاربر در پنجرهٔ ذخیره تأییدش می‌کند.
 */
object PayslipDetect {
    class Result(val kind: SlipKind?, val year: Int?, val month: Int?) {
        val isEmpty get() = kind == null && month == null
    }

    private val RE_DATE = Regex("""(1[34]\d\d)\s*[/\-.]\s*(0?[1-9]|1[0-2])(?!\d)""")
    private val RE_YEAR = Regex("""1[34]\d\d""")

    /** ارقام فارسی/عربی را لاتین و ی/ک عربی را فارسی می‌کند و نیم‌فاصله و کشیده را برمی‌دارد. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                '\u064A', '\u0649' -> sb.append('\u06CC')
                '\u0643' -> sb.append('\u06A9')
                '\u200C', '\u200D', '\u0640', '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** از همهٔ متن‌ها (به ترتیب اولویت) نوع و ماه را پیدا می‌کند. هر چه پیدا نشد null است. */
    fun detect(texts: List<String>): Result {
        var kind: SlipKind? = null
        var month: Int? = null
        var year: Int? = null
        for (raw in texts) {
            val t = normalize(raw)
            if (kind == null) kind = kindOf(t)
            if (month == null) {
                val (y, m) = monthOf(t)
                if (m != null) { month = m; year = y }
            }
            if (kind != null && month != null) break
        }
        return Result(kind, year, month)
    }

    private fun kindOf(t: String): SlipKind? {
        val r = t.indexOf("رفاهی")
        val h = t.indexOf("حکمی")
        return when {
            r < 0 && h < 0 -> null
            r < 0 -> SlipKind.HOKMI
            h < 0 -> SlipKind.REFAHI
            else -> if (r < h) SlipKind.REFAHI else SlipKind.HOKMI     // هر کدام زودتر آمد (عنوان معمولاً اول است)
        }
    }

    /** اول «نام ماه + سال»، بعد الگوی «1405/06». */
    private fun monthOf(t: String): Pair<Int?, Int?> {
        var best: Triple<Int, Int, Int?>? = null     // (جایگاه، ماه، سال)
        Fa.MONTHS.forEachIndexed { i, name ->
            var from = 0
            while (true) {
                val idx = t.indexOf(name, from)
                if (idx < 0) break
                // «دی» و «تیر» کوتاه‌اند؛ باید کلمهٔ جدا باشند تا داخل کلمهٔ دیگر اشتباه نشوند
                val before = t.getOrNull(idx - 1)
                val after = t.getOrNull(idx + name.length)
                val standalone = (before == null || !before.isLetter()) && (after == null || !after.isLetter())
                if (standalone) {
                    val near = t.substring((idx - 12).coerceAtLeast(0), (idx + name.length + 12).coerceAtMost(t.length))
                    val y = RE_YEAR.find(near)?.value?.toInt()
                    if (best == null || idx < best!!.first) best = Triple(idx, i + 1, y)
                    break
                }
                from = idx + 1
            }
        }
        best?.let { return it.third to it.second }
        RE_DATE.find(t)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        return null to null
    }

    /**
     * تلاش سبک برای خواندن متن PDF: جریان‌های Flate را باز می‌کند و رشته‌های متنی (لاتین، UTF-16 هگز) را برمی‌دارد.
     * اگر PDF فونت با نگاشت خاص یا تصویر باشد چیزی پیدا نمی‌شود؛ آن‌وقت کاربر دستی انتخاب می‌کند.
     */
    fun pdfText(bytes: ByteArray, maxChars: Int = 200_000): String {
        val out = StringBuilder()
        val s = String(bytes, Charsets.ISO_8859_1)
        out.append(s.take(50_000).filter { it.code in 32..126 || it == '\n' })
        var pos = 0
        while (out.length < maxChars) {
            val a = s.indexOf("stream", pos)
            if (a < 0) break
            var start = a + 6
            if (s.startsWith("\r\n", start)) start += 2 else if (s.startsWith("\n", start)) start += 1
            val end = s.indexOf("endstream", start)
            if (end < 0) break
            inflate(bytes, start, end - start)?.let { raw ->
                val txt = String(raw, Charsets.ISO_8859_1)
                out.append(txt.filter { it.code in 32..126 || it == '\n' })
                hexStrings(txt, out)
            }
            pos = end + 9
        }
        return out.toString()
    }

    private fun inflate(b: ByteArray, off: Int, len: Int): ByteArray? {
        if (len <= 0) return null
        val inf = Inflater()
        return try {
            inf.setInput(b, off, len)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (!inf.finished() && total < 2_000_000) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                bos.write(buf, 0, n); total += n
            }
            bos.toByteArray()
        } catch (e: Exception) { null } finally { inf.end() }
    }

    /** رشته‌های <FEFF....> یا <0645...> را به‌عنوان UTF-16BE رمزگشایی می‌کند. */
    private fun hexStrings(txt: String, out: StringBuilder) {
        Regex("""<([0-9A-Fa-f]{8,})>""").findAll(txt).take(5000).forEach { m ->
            val h = m.groupValues[1]
            if (h.length % 4 != 0) return@forEach
            val sb = StringBuilder()
            var i = if (h.startsWith("FEFF", ignoreCase = true)) 4 else 0
            while (i + 4 <= h.length) { sb.append(h.substring(i, i + 4).toInt(16).toChar()); i += 4 }
            out.append(sb).append('\n')
        }
    }
}
