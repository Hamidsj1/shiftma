package ir.shiftapp.phonebook

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * پایگاه دادهٔ دفترچه تلفن. اگر فایل assets/contacts.csv وجود داشته باشد، بار اول از آن پر می‌شود؛
 * در نسخهٔ منتشرشده این فایل را نگذارید (فهرست افراد داخل APK قرار می‌گیرد).
 */
class PhoneDb(private val ctx: Context) : SQLiteOpenHelper(ctx, "phonebook.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE contacts (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, mobile TEXT, landline TEXT, photo TEXT, dept TEXT, unit TEXT, social TEXT)"
        )
        try { seedFromAsset(db) } catch (e: Exception) { /* فایلی نیست: خالی شروع می‌شود */ }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE contacts ADD COLUMN photo TEXT")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE contacts ADD COLUMN dept TEXT")
            db.execSQL("ALTER TABLE contacts ADD COLUMN unit TEXT")
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE contacts ADD COLUMN social TEXT")
    }

    fun hasAsset(): Boolean = try { ctx.assets.open("contacts.csv").close(); true } catch (e: Exception) { false }

    private fun seedFromAsset(db: SQLiteDatabase) {
        val text = ctx.assets.open("contacts.csv").bufferedReader(Charsets.UTF_8).use { it.readText() }
        parseCsv(text).forEach { db.insert("contacts", null, values(PhotoStore.attach(ctx, it))) }
    }

    /** همه چیز را پاک می‌کند و دوباره از فایل داخلی می‌خواند؛ اگر فایل خوانده نشود، برمی‌گردد. */
    fun reloadFromAsset() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("contacts", null, null)
            seedFromAsset(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** همهٔ مخاطبین را پاک می‌کند (بدون خواندن فایل داخلی). */
    fun deleteAll() { writableDatabase.delete("contacts", null, null) }

    /** همه را پاک می‌کند و [items] را می‌گذارد؛ در یک تراکنش، پس اگر خطا شود چیزی پاک نمی‌شود. */
    fun replaceAll(items: List<Contact>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("contacts", null, null)
            items.forEach { db.insert("contacts", null, values(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(): Int = DatabaseUtils.queryNumEntries(readableDatabase, "contacts").toInt()

    fun search(q: String): List<Contact> {
        val out = mutableListOf<Contact>()
        val sql = "SELECT id, name, mobile, landline, photo, dept, unit, social FROM contacts"
        val cursor = if (q.isEmpty()) {
            readableDatabase.rawQuery("$sql ORDER BY name COLLATE NOCASE", null)
        } else {
            val like = "%$q%"
            readableDatabase.rawQuery(
                "$sql WHERE name LIKE ? OR mobile LIKE ? OR landline LIKE ? OR dept LIKE ? OR unit LIKE ? " +
                    "ORDER BY name COLLATE NOCASE",
                arrayOf(like, like, like, like, like)
            )
        }
        cursor.use {
            while (it.moveToNext()) {
                out.add(
                    Contact(
                        it.getLong(0), it.getString(1),
                        it.getString(2) ?: "", it.getString(3) ?: "", it.getString(4) ?: "",
                        it.getString(5) ?: "", it.getString(6) ?: "", social = it.getString(7) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun insert(c: Contact) { writableDatabase.insert("contacts", null, values(c)) }

    fun update(c: Contact) {
        writableDatabase.update("contacts", values(c), "id = ?", arrayOf(c.id.toString()))
    }

    fun delete(c: Contact) {
        writableDatabase.delete("contacts", "id = ?", arrayOf(c.id.toString()))
    }

    /** شناسه و مسیر عکسِ مخاطب کاملاً یکسان (نام، دو شماره، امور، واحد)؛ null اگر نیست. */
    fun findSame(c: Contact): Pair<Long, String>? {
        val cur = readableDatabase.rawQuery(
            "SELECT id, IFNULL(photo, '') FROM contacts WHERE name = ? AND mobile = ? AND landline = ? " +
                "AND IFNULL(dept, '') = ? AND IFNULL(unit, '') = ? LIMIT 1",
            arrayOf(c.name, c.mobile, c.landline, c.dept, c.unit)
        )
        return cur.use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }
    }

    fun setPhoto(id: Long, path: String) {
        writableDatabase.update("contacts", ContentValues().apply { put("photo", path) }, "id = ?", arrayOf(id.toString()))
    }

    /** اگر مخاطب کاملاً یکسانی نباشد اضافه می‌کند. */
    fun insertIfNew(c: Contact): Boolean {
        if (findSame(c) != null) return false
        insert(c)
        return true
    }

    /** چند عملیات پشت‌سرهم در یک تراکنش (برای وارد کردن فایل بزرگ، سریع‌تر و همه‌یاهیچ). */
    fun <T> batch(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = block()
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    private fun values(c: Contact) = ContentValues().apply {
        put("name", c.name); put("mobile", c.mobile); put("landline", c.landline)
        put("photo", c.photo); put("dept", c.dept); put("unit", c.unit); put("social", c.social)
    }
}
