package ir.shiftapp.core

import java.net.URLDecoder

/** قواعد بخش «کتاب‌ها»: پسوندهای مجاز، نام امن فایل و تشخیص پیوند کتاب. منطق خالص (بدون اندروید) تا تست‌پذیر باشد. */
object Books {
    const val MAX_BYTES = 100L * 1024 * 1024
    const val MIN_BYTES = 5_000L
    /** فقط PDF داخل خود برنامه خوانده می‌شود؛ بقیه با برنامهٔ خوانندهٔ گوشی باز می‌شوند. */
    val EXT = setOf("pdf", "epub", "mobi", "azw3", "djvu", "txt", "doc", "docx")

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isPdf(name: String) = extOf(name) == "pdf"

    fun mimeOf(name: String): String = when (extOf(name)) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.ebook"
        "djvu" -> "image/vnd.djvu"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    /** پسوند از نوع محتوای سرور (وقتی نام فایل پسوند ندارد)؛ null اگر کتاب نیست. */
    fun extFromMime(mime: String?): String? {
        val m = mime.orEmpty().lowercase().substringBefore(';').trim()
        return when (m) {
            "application/pdf", "application/x-pdf" -> "pdf"
            "application/epub+zip" -> "epub"
            "application/x-mobipocket-ebook" -> "mobi"
            "image/vnd.djvu", "image/x-djvu" -> "djvu"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            else -> null
        }
    }

    /** نام امن (حرف/رقم فارسی و لاتین، نقطه، خط، فاصله) با پسوند مجاز؛ [fallbackExt] اگر نام پسوند مجاز نداشت. */
    fun sanitize(raw: String, fallbackExt: String = "pdf"): String {
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        var n = decoded.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it == ' ' || it == '‌') it else '_' }
            .joinToString("").trim('_', '.', ' ')
        if (n.isBlank() || n.all { it == '_' || it == '.' }) n = "کتاب"
        val ext = extOf(n)
        if (ext !in EXT) n += ".$fallbackExt"
        if (n.length > 80) {
            val e = n.substringAfterLast('.')
            n = n.substring(0, 80 - e.length - 1).trimEnd('.', ' ') + "." + e
        }
        return n
    }

    fun nameFromUrl(url: String): String =
        sanitize(url.trim().substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/'))

    /** نام نمایشی بدون پسوند. */
    fun display(fileName: String): String = fileName.substringBeforeLast('.', fileName).replace('_', ' ')

    fun isSafeName(n: String): Boolean =
        n.isNotBlank() && n.length <= 90 && '/' !in n && '\\' !in n && !n.startsWith(".") && !n.endsWith(".part")

    /** آیا نشانی به یک فایل کتاب اشاره می‌کند (از روی پسوند، بدون پرسش و #)؟ */
    fun looksLikeBookUrl(url: String): Boolean {
        val path = url.trim().substringBefore('#').substringBefore('?').lowercase()
        return (path.startsWith("http://") || path.startsWith("https://")) && extOf(path) in EXT
    }

    /** نامی که با نام‌های موجود برخورد نکند: «کتاب.pdf» ← «کتاب (2).pdf». */
    fun uniqueName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (true) {
            val c = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            if (c !in existing) return c
            i++
        }
    }
}
