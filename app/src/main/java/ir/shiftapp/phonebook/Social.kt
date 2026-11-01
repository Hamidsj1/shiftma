package ir.shiftapp.phonebook

/** پیام‌رسان‌های پشتیبانی‌شده. byNumber = با خود شماره هم می‌شود چت را باز کرد (در صورت فعال بودن). */
enum class Messenger(val key: String, val label: String, val byNumber: Boolean, val pkg: String? = null) {
    WHATSAPP("wa", "واتساپ", true),
    TELEGRAM("tg", "تلگرام", true),
    // pkg = نام پکیج اپ؛ این سه لینک مستقیم با شماره ندارند: شماره کپی و اپ باز می‌شود تا آنجا جست‌وجو کنید
    EITAA("eitaa", "ایتا", false, "ir.eitaa.messenger"),
    BALE("bale", "بله", false, "ir.nasim"),
    RUBIKA("rubika", "روبیکا", false, "app.rbmain.a")
}

/**
 * ذخیرهٔ پیام‌رسان‌های مخاطب در یک رشته: «wa=1;tg=ali_id;eitaa=x;bale=y;rubika=z».
 * مقدار «1» یعنی «با شماره» (فقط واتساپ و تلگرام)؛ در غیر این‌صورت مقدار، آیدی است.
 */
object Social {
    /** فقط حروف و ارقام لاتین، _ و . ؛ @ ابتدایی و لینک کامل حذف می‌شود. */
    fun cleanId(s: String): String {
        val t = s.trim().substringAfterLast('/').removePrefix("@")
        return t.filter { (it.isLetterOrDigit() && it.code < 128) || it == '_' || it == '.' }
    }

    fun parse(s: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        s.split(';').forEach { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@forEach
            val k = part.substring(0, i).trim()
            val v = part.substring(i + 1).trim()
            val m = Messenger.values().firstOrNull { it.key == k } ?: return@forEach
            val ok = when {
                m == Messenger.WHATSAPP -> v == "1"          // واتساپ فقط با شماره
                v == "1" -> m.byNumber
                else -> cleanId(v) == v && v.isNotEmpty()
            }
            if (ok) out[m.key] = v
        }
        return out
    }

    fun encode(m: Map<String, String>): String =
        Messenger.values().mapNotNull { x -> m[x.key]?.takeIf { it.isNotEmpty() }?.let { "${x.key}=$it" } }.joinToString(";")

    /** رشتهٔ نامعتبر یا خراب را پاک‌سازی می‌کند (برای CSV). */
    fun normalize(s: String): String = encode(parse(s))

    /** شمارهٔ بین‌المللی بدون + (مثلاً 98912...)؛ null اگر شماره به‌اندازهٔ کافی معتبر نیست. */
    fun intlNumber(raw: String): String? {
        var n = fixDigits(raw).filter { it.isDigit() }
        if (n.startsWith("00")) n = n.drop(2)
        else if (n.startsWith("0")) n = "98" + n.drop(1)
        else if (n.length == 10 && n.startsWith("9")) n = "98$n"
        return n.takeIf { it.length in 8..15 }
    }

    fun link(m: Messenger, value: String, mobile: String): String? = when (m) {
        Messenger.WHATSAPP -> intlNumber(mobile)?.let { "https://wa.me/$it" }
        // tg:// مستقیم اپ تلگرام را باز می‌کند؛ اگر نصب نبود PhoneActions.openUrl به لینک t.me برمی‌گردد
        Messenger.TELEGRAM -> if (value == "1") intlNumber(mobile)?.let { "tg://resolve?phone=$it" } else "tg://resolve?domain=$value"
        Messenger.EITAA -> "https://eitaa.com/$value"
        Messenger.BALE -> "https://ble.ir/$value"
        Messenger.RUBIKA -> "https://rubika.ir/$value"
    }

    /**
     * دکمه‌های پیام‌رسان برای این مخاطب: (عنوان، لینک). واتساپ و تلگرام برای هر مخاطبِ دارای موبایل همیشه هست
     * (تلگرام با آیدی اگر ثبت شده، وگرنه با شماره)؛ ایتا، بله و روبیکا فقط وقتی آیدی ثبت شده باشد.
     */
    fun links(c: Contact): List<Pair<String, String>> {
        val p = parse(c.social)
        return Messenger.values().mapNotNull { m ->
            val v = p[m.key] ?: if (m.byNumber) "1" else return@mapNotNull null
            link(m, v, c.mobile)?.let { m.label to it }
        }
    }

    /** پیام‌رسان‌هایی که آیدی ندارند و فقط با «کپی شماره + باز کردن اپ» می‌شود در آن‌ها گشت (اگر مخاطب موبایل دارد). */
    fun searchOnly(c: Contact): List<Messenger> {
        if (c.mobile.isBlank()) return emptyList()
        val p = parse(c.social)
        return Messenger.values().filter { it.pkg != null && !p.containsKey(it.key) }
    }

    /** لینک وب جایگزین برای tg:// وقتی اپ تلگرام نصب نیست. */
    fun webFallback(url: String): String? = when {
        url.startsWith("tg://resolve?phone=") -> "https://t.me/+" + url.substringAfter("phone=")
        url.startsWith("tg://resolve?domain=") -> "https://t.me/" + url.substringAfter("domain=")
        else -> null
    }
}
