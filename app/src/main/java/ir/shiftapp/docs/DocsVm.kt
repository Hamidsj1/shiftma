package ir.shiftapp.docs

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.BitmapFactory
import ir.shiftapp.core.Fa
import ir.shiftapp.core.MealFeed
import ir.shiftapp.phonebook.normalizeFa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** فایلی که داخل حافظهٔ خصوصی برنامه ذخیره شده (`files/docs/<id>.<ext>`). */
data class DocFile(val id: String, val title: String, val ext: String, val kind: DocKind, val size: Long, val added: Long) {
    val mime: String get() = DocTypes.mimeOf(kind, ext)
}

/** یک مجتمع در بخش برنامه غذایی؛ file = برنامهٔ ماه (اختیاری). */
data class MealComplex(val id: String, val name: String, val label: String, val file: DocFile?)

/**
 * فایل‌های من (عمومی) و برنامه غذایی مجتمع‌ها (اختصاصی). فهرست‌ها در `files/docs_index.json` و خودِ فایل‌ها در `files/docs/` می‌مانند؛
 * جدا از پایگاه داده و فایل پشتیبان.
 */
class DocsVm(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dir = File(app.filesDir, "docs").apply { mkdirs() }
    private val indexFile = File(app.filesDir, "docs_index.json")
    private val lock = Any()

    val myFiles = MutableStateFlow<List<DocFile>>(emptyList())
    val complexes = MutableStateFlow<List<MealComplex>>(emptyList())
    val message = MutableStateFlow<String?>(null)

    companion object {
        const val MAX_MY_BYTES = 30L * 1024 * 1024
        const val MAX_MEAL_BYTES = 10L * 1024 * 1024
    }

    init { viewModelScope.launch(Dispatchers.IO) { load() } }

    fun fileOf(f: DocFile): File = File(dir, "${f.id}.${f.ext}")

    // ---------- ذخیره و خواندن فهرست ----------

    private fun DocFile.toJson() = JSONObject().put("id", id).put("title", title).put("ext", ext).put("kind", kind.name).put("size", size).put("added", added)

    private fun docFrom(o: JSONObject): DocFile? = try {
        val kind = DocKind.valueOf(o.getString("kind"))
        val f = DocFile(o.getString("id"), o.getString("title"), o.getString("ext"), kind, o.optLong("size"), o.optLong("added"))
        if (fileOf(f).exists()) f else null
    } catch (e: Exception) { null }

    private fun load() {
        try {
            if (!indexFile.exists()) return
            val o = JSONObject(indexFile.readText(Charsets.UTF_8))
            val mine = o.optJSONArray("myFiles") ?: JSONArray()
            myFiles.value = (0 until mine.length()).mapNotNull { docFrom(mine.getJSONObject(it)) }
            val cs = o.optJSONArray("complexes") ?: JSONArray()
            complexes.value = (0 until cs.length()).mapNotNull { i ->
                val c = cs.getJSONObject(i)
                runCatching { MealComplex(c.getString("id"), c.getString("name"), c.optString("label", ""), c.optJSONObject("file")?.let { docFrom(it) }) }.getOrNull()
            }
        } catch (e: Exception) { /* فهرست خراب: خالی شروع می‌شود */ }
    }

    private fun save() = synchronized(lock) {
        val o = JSONObject()
        o.put("myFiles", JSONArray().also { a -> myFiles.value.forEach { a.put(it.toJson()) } })
        o.put("complexes", JSONArray().also { a ->
            complexes.value.forEach { c ->
                val j = JSONObject().put("id", c.id).put("name", c.name).put("label", c.label)
                c.file?.let { j.put("file", it.toJson()) }
                a.put(j)
            }
        })
        val tmp = File(indexFile.parentFile, "docs_index.tmp")
        tmp.writeText(o.toString(), Charsets.UTF_8)
        tmp.renameTo(indexFile)
    }

    // ---------- کپی فایل انتخاب‌شده ----------

    /** فایل را از [uri] داخل حافظهٔ برنامه کپی می‌کند. خطا = IllegalArgumentException با پیام فارسی. */
    private fun importUri(uri: Uri, maxBytes: Long, allow: Set<DocKind> = DocKind.values().toSet()): DocFile {
        var name = "file"
        var mime: String? = ctx.contentResolver.getType(uri)
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) name = it.getString(0)
        }
        val kind = DocTypes.kindOf(name, mime)
        require(kind != null && kind in allow) { "فقط عکس، PDF و فایل نمایشی (پاورپوینت) پشتیبانی می‌شود" }
        val ext = DocTypes.extFor(kind, name, mime)
        val id = UUID.randomUUID().toString()
        val out = File(dir, "$id.$ext")
        var total = 0L
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("فایل باز نشد")
            input.use { i ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        total += n
                        require(total <= maxBytes) { "حجم فایل بیش از ${Fa.num((maxBytes / 1024 / 1024).toInt())} مگابایت است" }
                        o.write(buf, 0, n)
                    }
                }
            }
            require(total > 0) { "فایل خالی است" }
        } catch (e: Exception) {
            out.delete()
            throw if (e is IllegalArgumentException) e else IllegalArgumentException("فایل خوانده نشد")
        }
        return DocFile(id, name.substringBeforeLast('.', name).trim().ifEmpty { "فایل" }.take(80), ext, kind, total, System.currentTimeMillis())
    }

    private fun deleteFile(f: DocFile?) { if (f != null) fileOf(f).delete() }

    // ---------- فایل‌های من ----------

    fun addMyFile(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { importUri(uri, MAX_MY_BYTES) } }
            r.onSuccess { f ->
                withContext(Dispatchers.IO) { myFiles.value = listOf(f) + myFiles.value; save() }
                message.value = "فایل اضافه شد"
            }.onFailure { message.value = it.message ?: "فایل اضافه نشد" }
        }
    }

    fun deleteMyFile(f: DocFile) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteFile(f); myFiles.value = myFiles.value.filter { it.id != f.id }; save()
        }
    }

    fun renameMyFile(f: DocFile, title: String) {
        val t = title.trim().take(80)
        if (t.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { myFiles.value = myFiles.value.map { if (it.id == f.id) it.copy(title = t) else it }; save() }
    }

    // ---------- برنامه غذایی مجتمع‌ها ----------

    fun saveComplex(id: String?, name: String, label: String) {
        val n = name.trim().take(80)
        if (n.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            complexes.value = if (id == null) complexes.value + MealComplex(UUID.randomUUID().toString(), n, label.trim().take(40), null)
            else complexes.value.map { if (it.id == id) it.copy(name = n, label = label.trim().take(40)) else it }
            save()
        }
    }

    fun deleteComplex(c: MealComplex) {
        viewModelScope.launch(Dispatchers.IO) { deleteFile(c.file); complexes.value = complexes.value.filter { it.id != c.id }; save() }
    }

    /** فایل برنامهٔ ماه این مجتمع را می‌گذارد (جایگزین قبلی). عکس، PDF و فایل نمایشی. */
    fun setComplexFile(id: String, uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { importUri(uri, MAX_MEAL_BYTES) } }
            r.onSuccess { f ->
                withContext(Dispatchers.IO) {
                    val old = complexes.value.firstOrNull { it.id == id }?.file
                    complexes.value = complexes.value.map { if (it.id == id) it.copy(file = f) else it }
                    deleteFile(old); save()
                }
                message.value = "فایل برنامه غذایی ذخیره شد"
            }.onFailure { message.value = it.message ?: "فایل ذخیره نشد" }
        }
    }

    // ---------- بستهٔ .shiftma ----------

    /** JSON بستهٔ برنامه غذایی همهٔ مجتمع‌هایی که فایل دارند؛ null اگر چیزی برای ارسال نیست یا حجم زیاد است. */
    suspend fun buildMealPack(): String? = withContext(Dispatchers.IO) {
        try {
            val items = complexes.value.mapNotNull { c ->
                val f = c.file ?: return@mapNotNull null
                MealPackItem(c.name, c.label, "${f.title}.${f.ext}", fileOf(f).readBytes())
            }
            if (items.isEmpty()) { message.value = "هنوز فایلی برای ارسال نیست"; return@withContext null }
            val json = MealPackFile.toJson(items)
            if (json.length > MealPackFile.MAX_CHARS) { message.value = "حجم فایل‌ها برای یک بسته زیاد است"; null } else json
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { message.value = "حجم فایل‌ها برای یک بسته زیاد است"; null }
    }

    /** آیتم‌های بستهٔ برنامه غذایی برای مجتمع‌های انتخاب‌شده در «ارسال انتخابی» (مجتمع بدون فایل یا فایل ناخواناشدنی کنار می‌رود). */
    suspend fun mealItemsOf(selected: List<MealComplex>): List<MealPackItem> = withContext(Dispatchers.IO) {
        selected.mapNotNull { c ->
            val f = c.file ?: return@mapNotNull null
            try { MealPackItem(c.name, c.label, "${f.title}.${f.ext}", fileOf(f).readBytes()) }
            catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
        }
    }

    // ---------- دریافت خودکار برنامه غذایی شرکت (MealFeed، docs/MEAL_FEED.md) ----------

    private val feedState = File(app.filesDir, "meal_feed_state.json")
    private val feedMutex = kotlinx.coroutines.sync.Mutex()

    private fun download(url: String, max: Long): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000; c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            if (c.contentLengthLong > max) throw IOException("big")
            val out = ByteArrayOutputStream()
            c.inputStream.use { i ->
                val buf = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) throw IOException("big")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    /** محتوا واقعاً از نوعی باشد که پسوندش می‌گوید (PDF با %PDF-، عکس با ابعاد قابل خواندن). */
    private fun looksRight(kind: DocKind, b: ByteArray): Boolean = when (kind) {
        DocKind.PDF -> b.size > 5 && String(b, 0, 5, Charsets.ISO_8859_1) == "%PDF-"
        DocKind.IMAGE -> BitmapFactory.Options().let { o ->
            o.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            o.outWidth > 0 && o.outHeight > 0
        }
        else -> false
    }

    /**
     * خوراک برنامه غذایی را (حداکثر هر ۳ ساعت، یا با [force]) می‌گیرد و برای هر مجتمعِ تازه یا تغییرکرده فایلش را
     * جایگزین می‌کند. [feedUrl] خالی یا غیرمجاز = بدون هیچ درخواستی برمی‌گردد. هر خطا بی‌صدا رد می‌شود و دادهٔ فعلی دست‌نخورده می‌ماند.
     */
    suspend fun syncMealFeed(feedUrl: String, force: Boolean = false) {
        if (!MealFeed.isSafeUrl(feedUrl)) return
        feedMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val old = try { JSONObject(feedState.readText(Charsets.UTF_8)) } catch (e: Exception) { JSONObject() }
                    val sameFeed = old.optString("feed") == feedUrl
                    val urls = if (sameFeed) (old.optJSONObject("urls") ?: JSONObject()) else JSONObject()
                    if (sameFeed && !force && System.currentTimeMillis() - old.optLong("at") < 3L * 60 * 60 * 1000) return@withContext
                    val items = MealFeed.parse(String(download(feedUrl, MealFeed.MAX_JSON.toLong()), Charsets.UTF_8))
                    var changed = 0
                    for (item in items) {
                        try {
                            val key = normalizeFa(item.name)
                            val have = complexes.value.firstOrNull { normalizeFa(it.name) == key }
                            if (urls.optString(key) == item.fileUrl && have?.file != null) continue
                            val fileName = MealFeed.fileNameOf(item.fileUrl)
                            val kind = DocTypes.kindOf(fileName, null)
                            if (kind != DocKind.PDF && kind != DocKind.IMAGE) continue
                            val bytes = download(item.fileUrl, MAX_MEAL_BYTES)
                            if (bytes.isEmpty() || !looksRight(kind, bytes)) continue
                            val ext = DocTypes.extFor(kind, fileName, null)
                            val id = UUID.randomUUID().toString()
                            File(dir, "$id.$ext").writeBytes(bytes)
                            val f = DocFile(id, fileName.substringBeforeLast('.', fileName).trim().ifEmpty { item.name }.take(80), ext, kind, bytes.size.toLong(), System.currentTimeMillis())
                            if (have == null) complexes.value = complexes.value + MealComplex(UUID.randomUUID().toString(), item.name, item.label, f)
                            else {
                                complexes.value = complexes.value.map { c -> if (c.id == have.id) c.copy(label = item.label.ifBlank { c.label }, file = f) else c }
                                deleteFile(have.file)
                            }
                            urls.put(key, item.fileUrl)
                            changed++
                        } catch (e: Exception) { /* این مجتمع این بار رد می‌شود؛ دفعهٔ بعد دوباره تلاش می‌شود */ }
                    }
                    if (changed > 0) save()
                    feedState.writeText(JSONObject().put("feed", feedUrl).put("at", System.currentTimeMillis()).put("urls", urls).toString(), Charsets.UTF_8)
                    if (changed > 0) message.value = "برنامه غذایی ${Fa.num(changed)} مجتمع به‌روز شد"
                } catch (e: Exception) { /* بدون اینترنت یا فایل خراب: همان دادهٔ قبلی می‌ماند */ }
            }
        }
    }

    /** بستهٔ دریافتی را وارد می‌کند: مجتمع هم‌نام را به‌روز می‌کند، وگرنه مجتمع تازه می‌سازد. */
    fun importMealPack(items: List<MealPackItem>) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                var done = 0
                for (item in items) {
                    try {
                        val kind = DocTypes.kindOf(item.fileName, null) ?: continue
                        val ext = DocTypes.extFor(kind, item.fileName, null)
                        val id = UUID.randomUUID().toString()
                        File(dir, "$id.$ext").writeBytes(item.bytes)
                        val f = DocFile(id, item.fileName.substringBeforeLast('.', item.fileName).trim().ifEmpty { item.name }.take(80), ext, kind, item.bytes.size.toLong(), System.currentTimeMillis())
                        val key = normalizeFa(item.name)
                        val existing = complexes.value.firstOrNull { c -> normalizeFa(c.name) == key }
                        if (existing == null) complexes.value = complexes.value + MealComplex(UUID.randomUUID().toString(), item.name, item.label, f)
                        else {
                            complexes.value = complexes.value.map { c -> if (c.id == existing.id) c.copy(label = item.label.ifBlank { c.label }, file = f) else c }
                            deleteFile(existing.file)
                        }
                        done++
                    } catch (e: Exception) { /* این مورد رد می‌شود */ }
                }
                save()
                done
            }
            message.value = if (n > 0) "برنامه غذایی ${Fa.num(n)} مجتمع ذخیره شد" else "برنامه غذایی ذخیره نشد"
        }
    }
}
