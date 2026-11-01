package ir.shiftapp.docs

/** نوع فایل‌هایی که برنامه نگه می‌دارد: عکس و PDF داخل برنامه نمایش داده می‌شوند، فایل نمایشی با برنامهٔ دیگر باز می‌شود. */
enum class DocKind(val label: String) { IMAGE("عکس"), PDF("PDF"), SLIDES("فایل نمایشی") }

object DocTypes {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val slideExt = setOf("ppt", "pptx", "pps", "ppsx", "odp", "key")

    /** MIME هایی که انتخاب‌گر فایل نشان می‌دهد (نوع نهایی بعد از انتخاب با نام و MIME بررسی می‌شود). */
    val PICKER_MIMES = arrayOf(
        "image/*", "application/pdf", "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "application/vnd.oasis.opendocument.presentation", "application/octet-stream"
    )

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)

    fun kindOf(name: String, mime: String?): DocKind? {
        val e = extOf(name)
        val m = mime.orEmpty().lowercase()
        return when {
            e == "pdf" || m == "application/pdf" -> DocKind.PDF
            e in imageExt || m.startsWith("image/") -> DocKind.IMAGE
            e in slideExt || m.contains("presentation") || m == "application/vnd.ms-powerpoint" -> DocKind.SLIDES
            else -> null
        }
    }

    /** پسوند مطمئن برای ذخیره: از نام فایل، وگرنه از MIME. */
    fun extFor(kind: DocKind, name: String, mime: String?): String {
        val e = extOf(name)
        val ok = when (kind) { DocKind.PDF -> e == "pdf"; DocKind.IMAGE -> e in imageExt; DocKind.SLIDES -> e in slideExt }
        if (ok) return e
        val m = mime.orEmpty().lowercase()
        return when (kind) {
            DocKind.PDF -> "pdf"
            DocKind.IMAGE -> when { m.contains("png") -> "png"; m.contains("webp") -> "webp"; m.contains("gif") -> "gif"; else -> "jpg" }
            DocKind.SLIDES -> when {
                m.contains("slideshow") -> "ppsx"; m.contains("openxml") -> "pptx"
                m.contains("opendocument") -> "odp"; else -> "ppt"
            }
        }
    }

    fun mimeOf(kind: DocKind, ext: String): String = when (kind) {
        DocKind.PDF -> "application/pdf"
        DocKind.IMAGE -> when (ext) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; else -> "image/jpeg" }
        DocKind.SLIDES -> when (ext) {
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppsx" -> "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "key" -> "application/x-iwork-keynote-sffkey"
            else -> "application/vnd.ms-powerpoint"
        }
    }
}
