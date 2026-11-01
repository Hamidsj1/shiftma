package ir.shiftapp.phonebook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

/** کارهای مرتبط با سیستم: تماس، کپی، اشتراک‌گذاری و افزودن به مخاطبین گوشی. */
object PhoneActions {
    private const val PREFS = "phonebook_settings"
    private const val KEY_PREFIX = "ext_prefix"

    /** پیش‌شماره‌ای که قبل از شماره‌های ۴ رقمی (داخلی) اضافه می‌شود؛ پیش‌فرض خالی است. */
    fun loadPrefix(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX, "") ?: ""

    fun savePrefix(ctx: Context, prefix: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREFIX, prefix).apply()
    }

    /** فقط شمارهٔ دقیقاً ۴ رقمی (داخلی) پیش‌شماره می‌گیرد؛ بقیه بدون تغییر می‌مانند. */
    fun dialable(number: String, prefix: String): String {
        val n = number.trim()
        val isExtension = n.length == 4 && n.all { it.isDigit() }
        return if (isExtension) prefix + n else n
    }

    /** آیا این مخاطب شمارهٔ ۴ رقمی (داخلی) دارد که با پیش‌شمارهٔ خالی ناقص ذخیره می‌شود؟ */
    fun hasExtension(c: Contact): Boolean = listOf(c.mobile, c.landline).any { n -> n.trim().let { it.length == 4 && it.all(Char::isDigit) } }

    fun dial(ctx: Context, number: String, prefix: String) {
        if (number.isBlank()) return
        try {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dialable(number, prefix), null)))
        } catch (e: Exception) {
            Toast.makeText(ctx, "برنامهٔ تماس پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    /** باز کردن لینک پیام‌رسان (اپ نصب باشد در خود اپ، وگرنه مرورگر). */
    fun openUrl(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            val web = Social.webFallback(url)
            if (web != null) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))); return } catch (_: Exception) {}
            }
            Toast.makeText(ctx, "برنامه‌ای برای باز کردن پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    fun sms(ctx: Context, number: String) {
        if (number.isBlank()) return
        try {
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number.trim())))
        } catch (e: Exception) {
            Toast.makeText(ctx, "برنامهٔ پیامک پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    /** برای پیام‌رسان‌هایی که لینک مستقیم با شماره ندارند: شماره را کپی می‌کند و خود اپ را باز می‌کند تا آنجا جست‌وجو کنید. */
    fun openApp(ctx: Context, m: Messenger, number: String) {
        val launch = m.pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Toast.makeText(ctx, "${m.label} نصب نیست", Toast.LENGTH_SHORT).show()
            return
        }
        copy(ctx, number)
        try { ctx.startActivity(launch) } catch (e: Exception) { Toast.makeText(ctx, "باز نشد", Toast.LENGTH_SHORT).show() }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("number", text))
        Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
    }

    fun shareText(c: Contact): String {
        val sb = StringBuilder(c.name)
        if (c.mobile.isNotBlank()) sb.append("\nهمراه: ").append(c.mobile)
        if (c.landline.isNotBlank()) sb.append("\nمحل: ").append(c.landline)
        if (c.dept.isNotBlank()) sb.append("\nامور: ").append(c.dept)
        if (c.unit.isNotBlank()) sb.append("\nواحد: ").append(c.unit)
        return sb.toString()
    }

    fun share(ctx: Context, c: Contact) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, c.name)
            putExtra(Intent.EXTRA_TEXT, shareText(c))
        }
        try {
            ctx.startActivity(Intent.createChooser(send, "اشتراک‌گذاری مخاطب"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "برنامه‌ای برای اشتراک‌گذاری پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

    /** امور و واحد که در بخش «یادداشت» مخاطب گوشی ذخیره می‌شود. */
    fun noteText(c: Contact): String {
        val parts = mutableListOf<String>()
        if (c.dept.isNotBlank()) parts.add("امور: ${c.dept}")
        if (c.unit.isNotBlank()) parts.add("واحد: ${c.unit}")
        return parts.joinToString("\n")
    }

    /** یک مخاطب: صفحهٔ «مخاطب جدید» گوشی پر شده باز می‌شود و کاربر ذخیره را می‌زند. */
    fun addToPhone(ctx: Context, c: Contact, prefix: String, photo: ByteArray?) {
        val phones = mutableListOf<Pair<String, Int>>()
        if (c.mobile.isNotBlank()) phones.add(dialable(c.mobile, prefix) to ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        if (c.landline.isNotBlank()) phones.add(dialable(c.landline, prefix) to ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, c.name)
            if (phones.size > 0) {
                putExtra(ContactsContract.Intents.Insert.PHONE, phones[0].first)
                putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phones[0].second)
            }
            if (phones.size > 1) {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, phones[1].first)
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE, phones[1].second)
            }
            // امور → «شرکت/سازمان» و واحد → «سمت»؛ این دو فیلد در برنامهٔ مخاطبین همیشه نشان داده می‌شوند. یادداشت هم نگه داشته شده است.
            if (c.dept.isNotBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, c.dept)
            if (c.unit.isNotBlank()) putExtra(ContactsContract.Intents.Insert.JOB_TITLE, c.unit)
            val note = noteText(c)
            if (note.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.NOTES, note)
            if (photo != null) {
                val row = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, photo)
                }
                putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(row))
            }
        }
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "برنامهٔ مخاطبین پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }
}
