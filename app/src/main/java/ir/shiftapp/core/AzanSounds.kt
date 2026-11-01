package ir.shiftapp.core

import java.net.URLDecoder

/**
 * صدای هشدار اذان در یک رشته (`Prefs.prayerSound`) ذخیره می‌شود:
 * خالی = پیش‌فرض گوشی، `builtin:…` = اذان همراه برنامه، `file:نام` = فایل داخل پوشهٔ اذان برنامه (دانلودی یا افزوده‌شده)،
 * هر چیز دیگر = نشانی صدای سیستم (سازگاری با نسخه‌های قبل).
 */
object AzanSounds {
    const val MAX_BYTES = 20L * 1024 * 1024
    const val MIN_BYTES = 10_000L
    const val BUILTIN = "builtin:moazenzadeh"
    const val BUILTIN_TITLE = "اذان پیش‌فرض برنامه"
    private const val FILE_PREFIX = "file:"
    private val EXT = setOf("mp3", "m4a", "aac", "ogg", "wav", "opus")

    enum class Kind { DEFAULT, BUILTIN, FILE, SYSTEM }

    fun kind(token: String): Kind = when {
        token.isBlank() -> Kind.DEFAULT
        token == BUILTIN -> Kind.BUILTIN
        token.startsWith(FILE_PREFIX) -> Kind.FILE
        else -> Kind.SYSTEM
    }

    fun fileToken(name: String) = FILE_PREFIX + name

    /** نام فایلِ یک توکن `file:`؛ اگر نام ناامن بود (مسیر، نقطهٔ ابتدایی) null. */
    fun fileName(token: String): String? =
        if (token.startsWith(FILE_PREFIX)) token.substring(FILE_PREFIX.length).takeIf { isSafeName(it) } else null

    fun isSafeName(n: String): Boolean =
        n.isNotBlank() && n.length <= 80 && '/' !in n && '\\' !in n && !n.startsWith(".") && !n.endsWith(".part")

    fun isHttps(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true) && url.trim().length > 12

    /** نام فایل امن (فقط حرف/رقم/نقطه/خط) با پسوند صوتی؛ اگر پسوند صوتی نداشت `.mp3` می‌گیرد. */
    fun sanitize(raw: String): String {
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        var n = decoded.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }.joinToString("").trim('_', '.')
        if (n.isBlank() || n.all { it == '_' || it == '.' }) n = "azan"
        val ext = n.substringAfterLast('.', "").lowercase()
        if (ext !in EXT) n += ".mp3"
        if (n.length > 60) {
            val e = n.substringAfterLast('.')
            n = n.substring(0, 60 - e.length - 1) + "." + e
        }
        return n
    }

    fun nameFromUrl(url: String): String =
        sanitize(url.trim().substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/'))

    /** نام نمایشی بدون پسوند. */
    fun display(fileName: String): String = fileName.substringBeforeLast('.', fileName).replace('_', ' ')

    /** آیا نشانی به یک فایل صوتی اشاره می‌کند (از روی پسوند، بدون پرسش و #)؟ برای تشخیص فایل‌های صفحه در مرورگر برنامه. */
    fun looksLikeAudioUrl(url: String): Boolean {
        val path = url.trim().substringBefore('#').substringBefore('?').lowercase()
        return (path.startsWith("http://") || path.startsWith("https://")) && path.substringAfterLast('.', "") in EXT
    }

    /**
     * ورودی نوار مرورگر: نشانی کامل، نام سایت (مثل `masjed.ir`) یا عبارت جست‌وجو.
     * [engine] یکی از `google` یا `bing`.
     */
    fun browserTarget(input: String, engine: String = "google"): String {
        val t = input.trim()
        val bing = engine == "bing"
        if (t.isEmpty()) return if (bing) "https://www.bing.com/" else "https://www.google.com/"
        if (t.startsWith("https://", true) || t.startsWith("http://", true)) return t
        if (' ' !in t && '.' in t && !t.endsWith(".") && t.all { it.code < 128 }) return "https://$t"
        val q = java.net.URLEncoder.encode(t, "UTF-8")
        return (if (bing) "https://www.bing.com/search?q=" else "https://www.google.com/search?q=") + q
    }
}
