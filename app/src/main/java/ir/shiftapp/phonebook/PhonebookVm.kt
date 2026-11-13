package ir.shiftapp.phonebook

import android.app.Application
import android.content.ContentProviderOperation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhonebookVm(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app
    private val db = PhoneDb(app)

    val query = MutableStateFlow("")
    val contacts = MutableStateFlow<List<Contact>>(emptyList())
    val total = MutableStateFlow(0)
    val message = MutableStateFlow<String?>(null)
    val prefix = MutableStateFlow(PhoneActions.loadPrefix(app))

    private var stats: Map<String, PhoneUsage.Stat> = PhoneUsage.loadStats(app)

    private val thumbs = LruCache<String, Bitmap>(120)
    private var refreshJob: Job? = null

    init { refresh() }

    fun hasAsset(): Boolean = db.hasAsset()

    /** همهٔ مخاطبین (بدون توجه به جست‌وجوی فعلی)؛ برای انتخاب در «ارسال انتخابی». */
    suspend fun allContacts(): List<Contact> = withContext(Dispatchers.IO) { runCatching { db.search("") }.getOrDefault(emptyList()) }

    fun setQuery(q: String) { query.value = q; refresh() }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val q = query.value
            val res = withContext(Dispatchers.IO) {
                val all = db.search("")
                // شماره‌هایی که زیاد جست‌وجو یا تماس گرفته شده‌اند بالای لیست می‌آیند
                PhoneUsage.rank(if (normalizeFa(q).isEmpty()) all else all.filter { it.matches(q) }, stats) to all.size
            }
            contacts.value = res.first
            total.value = res.second
        }
    }

    /** تماس گرفتن با [number]: شمارندهٔ شماره یکی زیاد می‌شود و مخاطب همین حالا بالای لیست می‌آید. */
    fun recordUse(c: Contact, number: String = c.mobile.ifBlank { c.landline }) {
        stats = PhoneUsage.bump(stats, number, System.currentTimeMillis())
        PhoneUsage.saveStats(ctx, stats)
        refresh()
    }

    fun setPrefix(p: String) {
        val clean = fixDigits(p.trim()).replace('،', ',').replace(" ", "")
        PhoneActions.savePrefix(ctx, clean)
        prefix.value = clean
        message.value = if (clean.isEmpty()) "پیش‌شماره حذف شد" else "پیش‌شماره ذخیره شد"
    }

    /** old = نسخهٔ قبلی هنگام ویرایش (برای پاک‌کردن عکس قدیمی)؛ برای مخاطب جدید null. */
    fun save(old: Contact?, c: Contact) {
        viewModelScope.launch {
            val fixed = c.copy(
                name = c.name.trim(), mobile = fixDigits(c.mobile.trim()), landline = fixDigits(c.landline.trim()),
                dept = c.dept.trim(), unit = c.unit.trim()
            )
            withContext(Dispatchers.IO) {
                if (fixed.id == 0L) db.insert(fixed) else db.update(fixed)
                if (old != null && old.photo.isNotEmpty() && old.photo != fixed.photo) deletePhotoFile(old.photo)
            }
            refresh()
        }
    }

    fun delete(c: Contact) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.delete(c); deletePhotoFile(c.photo) }
            refresh()
        }
    }

    // ---------- عکس‌ها ----------

    fun deletePhotoFile(path: String) {
        if (path.isEmpty()) return
        File(path).delete()
        thumbs.remove(path)
    }

    fun thumb(path: String): Bitmap? {
        if (path.isEmpty()) return null
        thumbs.get(path)?.let { return it }
        if (!File(path).exists()) return null
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
        thumbs.put(path, bmp)
        return bmp
    }

    fun photoBytes(path: String): ByteArray? {
        if (path.isEmpty()) return null
        val f = File(path)
        return if (f.exists()) f.readBytes() else null
    }

    /** عکس انتخاب‌شده را با حداکثر ۵۱۲ پیکسل داخل حافظهٔ برنامه ذخیره می‌کند. */
    suspend fun savePickedPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) null else {
                val scale = 512f / maxOf(bmp.width, bmp.height)
                val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                val f = File(ctx.filesDir, "p_${System.currentTimeMillis()}.jpg")
                f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                f.absolutePath
            }
        } catch (e: Exception) { null }
    }

    // ---------- ورود و انتقال ----------

    private class ImportResult(val added: Int, val total: Int, val photos: Int)

    /**
     * [replace] = false: فقط مخاطبین جدید اضافه می‌شوند (تکراری‌ها نادیده گرفته می‌شوند).
     * [replace] = true: همهٔ مخاطبین فعلی پاک و با محتوای فایل جایگزین می‌شوند؛ اگر فایل خالی یا خراب باشد چیزی پاک نمی‌شود.
     */
    fun importCsv(uri: Uri, replace: Boolean = false) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                try {
                    val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseCsv(text)
                } catch (e: Exception) { null }
            }
            applyImport(items, replace)
        }
    }

    /** مخاطبین خوانده‌شده از فایل .shiftma (با همان قاعدهٔ افزودن/جایگزینی فایل CSV). */
    fun importContacts(items: List<Contact>, replace: Boolean) {
        viewModelScope.launch { applyImport(items, replace) }
    }

    private suspend fun applyImport(items: List<Contact>?, replace: Boolean) {
        val r = withContext(Dispatchers.IO) {
            try {
                if (items == null) return@withContext null
                if (replace) {
                    if (items.isEmpty()) return@withContext null
                    val old = db.search("")
                    val ready = items.map { PhotoStore.attach(ctx, it) }
                    db.replaceAll(ready)
                    old.forEach { deletePhotoFile(it.photo) }
                    ImportResult(ready.size, ready.size, ready.count { it.photo.isNotEmpty() })
                } else {
                    var added = 0
                    var photos = 0
                    db.batch {
                        items.forEach { c ->
                            val same = db.findSame(c)
                            if (same == null) {
                                val withPhoto = PhotoStore.attach(ctx, c)
                                db.insert(withPhoto)
                                added++
                                if (withPhoto.photo.isNotEmpty()) photos++
                            } else if (c.photoData.isNotEmpty() && same.second.isEmpty()) {
                                // مخاطب از قبل هست ولی عکس ندارد: فقط عکس را اضافه کن
                                PhotoStore.save(ctx, c.photoData)?.let { db.setPhoto(same.first, it); photos++ }
                            }
                        }
                    }
                    ImportResult(added, items.size, photos)
                }
            } catch (e: Exception) { null }
        }
        refresh()
        val fa = { n: Int -> ir.shiftapp.core.Fa.num(n) }
        val photoNote = if (r != null && r.photos > 0) " · ${fa(r.photos)} عکس" else ""
        message.value = when {
            r == null -> if (replace) "فایل خالی است یا خوانده نشد؛ مخاطبین فعلی دست‌نخورده ماند" else "فایل خوانده نشد"
            replace -> "${fa(r.added)} مخاطب جایگزین شد$photoNote"
            else -> "${fa(r.added)} مخاطب جدید اضافه شد (${fa(r.total - r.added)} مورد تکراری یا نامعتبر)$photoNote"
        }
    }

    // ---------- فایل خروجی .shiftma (اشتراک‌گذاری مخاطبین) ----------

    class ContactsExport(val json: String, val count: Int, val withPhotos: Boolean, val photosDropped: Boolean)

    /**
     * فایل مخاطبین را می‌سازد. [only] = null یعنی همهٔ مخاطبین. اگر با عکس‌ها از سقف حجم بگذرد، خودکار بدون عکس ساخته می‌شود
     * (photosDropped = true). null یعنی مخاطبی نیست یا ساخت ممکن نشد.
     */
    suspend fun buildContactsExport(only: List<Contact>?, withPhotos: Boolean, tiles: org.json.JSONObject? = null): ContactsExport? = withContext(Dispatchers.IO) {
        try {
            val list = only ?: db.search("")
            if (list.isEmpty()) return@withContext null
            if (withPhotos) {
                val full = ContactsFile.toJson(list, tiles, prefix.value) { photoBytes(it.photo) }
                if (full.length <= ContactsFile.MAX_CHARS) return@withContext ContactsExport(full, list.size, true, false)
            }
            val bare = ContactsFile.toJson(list, tiles, prefix.value)
            ContactsExport(bare, list.size, false, withPhotos)
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }

    /** حذف همهٔ مخاطبین برنامه (و عکس‌هایشان)؛ مخاطبین دفترچهٔ خود گوشی دست‌نخورده می‌مانند. */
    fun deleteAll() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val old = db.search("")
                db.deleteAll()
                old.forEach { deletePhotoFile(it.photo) }
                old.size
            }
            refresh()
            message.value = "${ir.shiftapp.core.Fa.num(n)} مخاطب حذف شد"
        }
    }

    fun reloadFromAsset() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val old = db.search("")
                    db.reloadFromAsset()
                    old.forEach { deletePhotoFile(it.photo) }
                    true
                } catch (e: Exception) { false }
            }
            refresh()
            if (!ok) message.value = "فایل contacts.csv خوانده نشد"
        }
    }

    /** انتقال همهٔ مخاطبین (نام، شماره‌ها، یادداشت، عکس) به مخاطبین گوشی؛ اجازهٔ WRITE_CONTACTS لازم است. */
    fun exportAll() {
        viewModelScope.launch {
            message.value = "در حال انتقال..."
            val pre = prefix.value
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val list = db.search("")
                    var done = 0
                    list.chunked(10).forEach { chunk ->
                        val ops = ArrayList<ContentProviderOperation>()
                        chunk.forEach { c ->
                            val idx = ops.size
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                                    .build()
                            )
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, idx)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
                                    .build()
                            )
                            if (c.mobile.isNotBlank()) ops.add(phoneOp(idx, PhoneActions.dialable(c.mobile, pre), ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE))
                            if (c.landline.isNotBlank()) ops.add(phoneOp(idx, PhoneActions.dialable(c.landline, pre), ContactsContract.CommonDataKinds.Phone.TYPE_WORK))
                            if (c.dept.isNotBlank() || c.unit.isNotBlank()) {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, idx)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, c.dept)
                                        .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, c.unit)
                                        .build()
                                )
                            }
                            val note = PhoneActions.noteText(c)
                            if (note.isNotEmpty()) {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, idx)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
                                        .build()
                                )
                            }
                            val bytes = photoBytes(c.photo)
                            if (bytes != null) {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, idx)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                                        .build()
                                )
                            }
                        }
                        ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                        done += chunk.size
                    }
                    done
                }
            }
            message.value = res.fold({ "$it مخاطب به دفترچهٔ گوشی منتقل شد" }, { "خطا در انتقال: ${it.message}" })
        }
    }

    private fun phoneOp(backRef: Int, number: String, type: Int): ContentProviderOperation =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backRef)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, type)
            .build()

    override fun onCleared() {
        db.close()
        super.onCleared()
    }
}
