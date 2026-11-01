package ir.shiftapp.phonebook

data class Contact(
    val id: Long = 0,
    val name: String,
    val mobile: String = "",
    val landline: String = "",
    val photo: String = "",
    val dept: String = "",   // امور
    val unit: String = "",   // واحد
    val social: String = "",  // پیام‌رسان‌ها (Social.encode)
    val photoData: String = "" // فقط هنگام وارد کردن CSV: عکس به‌صورت base64 (در دیتابیس ذخیره نمی‌شود؛ بعد از وارد کردن به فایل تبدیل می‌شود)
)

/** ارقام فارسی و عربی را به انگلیسی تبدیل می‌کند. */
fun fixDigits(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        sb.append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** آیا متن شبیه دادهٔ base64 یک عکس است؟ (کاراکترهای مجاز و طول معقول) */
internal fun looksLikeBase64Image(s: String): Boolean =
    s.length >= 100 && s.all { it.isLetterOrDigit() && it.code < 128 || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }

/**
 * خط‌های CSV: name,mobile,landline[,dept,unit[,position[,photo]]]  (خط شروع‌شده با # توضیح است).
 * ستون هفتم (اختیاری) عکس JPEG به‌صورت base64 و ستون هشتم (اختیاری) پیام‌رسان‌ها مثل wa=1;tg=ali;rubika=x است؛ ستون position فعلاً خوانده و نادیده گرفته می‌شود.
 * فایل‌های قدیمی با ۳ تا ۵ ستون مثل قبل کار می‌کنند.
 */
fun parseCsv(text: String): List<Contact> {
    val out = mutableListOf<Contact>()
    text.removePrefix("\uFEFF").lines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val p = line.split(",").map { it.trim() }
        if (p[0].isEmpty()) return@forEach
        out.add(
            Contact(
                name = p[0],
                mobile = fixDigits(p.getOrElse(1) { "" }),
                landline = fixDigits(p.getOrElse(2) { "" }),
                dept = p.getOrElse(3) { "" },
                unit = p.getOrElse(4) { "" },
                social = Social.normalize(p.getOrElse(7) { "" }),
                photoData = p.getOrElse(6) { "" }.takeIf { looksLikeBase64Image(it) } ?: ""
            )
        )
    }
    return out
}

/** برای جست‌وجو: ارقام یکسان، ی/ک عربی به فارسی، حذف نیم‌فاصله و کشیده، حروف کوچک. */
fun normalizeFa(s: String): String {
    val sb = StringBuilder()
    for (ch in fixDigits(s)) {
        when (ch) {
            'ي', 'ى' -> sb.append('ی')
            'ك' -> sb.append('ک')
            '\u200c', '\u200d', 'ـ', '\u064B', '\u064C', '\u064D', '\u064E', '\u064F', '\u0650', '\u0651', '\u0652' -> {}
            else -> sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString().replace(Regex("\\s+"), " ").trim()
}

/** آیا همهٔ کلمات جست‌وجو در یکی از فیلدهای مخاطب (نام، شماره‌ها، امور، واحد) هستند؟ */
fun Contact.matches(query: String): Boolean {
    val words = normalizeFa(query).split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val hay = normalizeFa(listOf(name, mobile, landline, dept, unit).joinToString(" "))
    return words.all { hay.contains(it) }
}
