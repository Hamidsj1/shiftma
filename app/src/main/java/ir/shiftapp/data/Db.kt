package ir.shiftapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {
    @Query("SELECT * FROM shift_patterns ORDER BY id") abstract fun patterns(): Flow<List<PatternEntity>>
    @Query("SELECT * FROM shift_patterns ORDER BY id") abstract suspend fun patternsOnce(): List<PatternEntity>
    @Insert abstract suspend fun insert(p: PatternEntity): Long
    @Update abstract suspend fun update(p: PatternEntity)
    @Query("DELETE FROM shift_patterns WHERE id=:id") abstract suspend fun deletePattern(id: Long)
    @Query("DELETE FROM day_entries WHERE patternId=:id") abstract suspend fun deleteEntriesOfPattern(id: Long)

    @Query("SELECT * FROM day_entries WHERE patternId=:pid AND dateKey BETWEEN :from AND :to ORDER BY dateKey, id")
    abstract fun entries(pid: Long, from: String, to: String): Flow<List<DayEntry>>
    @Query("SELECT * FROM day_entries WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey, id")
    abstract fun entriesInRange(from: String, to: String): Flow<List<DayEntry>>
    @Query("SELECT * FROM day_entries ORDER BY id") abstract suspend fun allEntries(): List<DayEntry>
    @Query("SELECT * FROM day_entries ORDER BY id") abstract fun allEntriesFlow(): Flow<List<DayEntry>>
    @Query("SELECT * FROM day_entries WHERE patternId=:pid AND dateKey BETWEEN :from AND :to ORDER BY dateKey, id")
    abstract suspend fun entriesOnce(pid: Long, from: String, to: String): List<DayEntry>
    @Insert abstract suspend fun insert(e: DayEntry): Long
    @Query("DELETE FROM day_entries WHERE id=:id") abstract suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM employees ORDER BY id") abstract fun employees(): Flow<List<Employee>>
    @Query("SELECT * FROM employees ORDER BY id") abstract suspend fun employeesOnce(): List<Employee>
    @Insert abstract suspend fun insert(e: Employee): Long
    @Query("DELETE FROM employees WHERE id=:id") abstract suspend fun deleteEmployee(id: Long)

    @Query("SELECT * FROM settings") abstract suspend fun settingsOnce(): List<Setting>
    @Query("SELECT * FROM settings") abstract fun settings(): Flow<List<Setting>>

    @Query("SELECT * FROM holidays ORDER BY dateKey") abstract fun holidays(): Flow<List<Holiday>>
    @Query("SELECT * FROM holidays ORDER BY dateKey") abstract suspend fun holidaysOnce(): List<Holiday>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun put(h: Holiday)
    @Query("DELETE FROM holidays WHERE dateKey=:k") abstract suspend fun deleteHoliday(k: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putHolidays(l: List<Holiday>)
    @Query("DELETE FROM holidays") abstract suspend fun clearHolidays()
    @Query("SELECT value FROM settings WHERE `key`=:k") abstract suspend fun setting(k: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun put(s: Setting)

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putPatterns(l: List<PatternEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putEntries(l: List<DayEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putEmployees(l: List<Employee>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putSettings(l: List<Setting>)
    @Query("DELETE FROM shift_patterns") abstract suspend fun clearPatterns()
    @Query("DELETE FROM day_entries") abstract suspend fun clearEntries()
    @Query("DELETE FROM employees") abstract suspend fun clearEmployees()
    @Query("DELETE FROM settings") abstract suspend fun clearSettings()

    /** بازیابی اتمی: یا همه‌چیز جایگزین می‌شود یا هیچ‌چیز. */
    @Transaction
    open suspend fun replaceAll(s: Snapshot) {
        clearEntries(); clearPatterns(); clearEmployees(); clearSettings(); clearHolidays()
        putPatterns(s.patterns); putEntries(s.entries); putEmployees(s.employees); putSettings(s.settings); putHolidays(s.holidays)
    }
}

data class Snapshot(
    val patterns: List<PatternEntity>, val entries: List<DayEntry>,
    val employees: List<Employee>, val settings: List<Setting>,
    val holidays: List<Holiday> = emptyList()
)

@Database(entities = [PatternEntity::class, DayEntry::class, Employee::class, Setting::class, Holiday::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        /** نسخهٔ ۲: فقط جدول تعطیلات اضافه شد؛ داده‌های قبلی دست‌نخورده می‌مانند. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `holidays` (`dateKey` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`dateKey`))")
            }
        }
        /** نسخهٔ ۳: سه فیلد اختیاری برای الگو (مسئول شیفت، سرشیفت کل، بخش)؛ داده‌های قبلی دست‌نخورده می‌مانند. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shift_patterns` ADD COLUMN `supervisor` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shift_patterns` ADD COLUMN `headSupervisor` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `shift_patterns` ADD COLUMN `section` TEXT NOT NULL DEFAULT ''")
            }
        }
        /** نسخهٔ ۴: ستون اختیاری مجموعهٔ الگو (setName)؛ همهٔ داده‌های قبلی دست‌نخورده می‌مانند و مقدار پیش‌فرض خالی است. */
        const val SQL_3_4 = "ALTER TABLE `shift_patterns` ADD COLUMN `setName` TEXT NOT NULL DEFAULT ''"
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(SQL_3_4) }
        }
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "shiftma.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { inst = it }
        }
    }
}

// ---------- کمک‌های مشترک بین UI، یادآور، ویجت و پشتیبان خودکار ----------
suspend fun AppDao.selectedPattern(): PatternEntity? {
    val all = patternsOnce()
    val id = setting("selected_pattern")?.toLongOrNull()
    return all.firstOrNull { it.id == id } ?: all.firstOrNull()
}
suspend fun AppDao.prefsOnce(): ir.shiftapp.core.Prefs = ir.shiftapp.core.Prefs.from(settingsOnce().associate { it.key to it.value })
suspend fun AppDao.holidayCalendarOnce(fridayOff: Boolean): ir.shiftapp.core.HolidayCalendar =
    ir.shiftapp.core.HolidayCalendar(holidaysOnce().associate { it.dateKey to it.title }, fridayOff)
suspend fun AppDao.entriesByDateOnce(pid: Long, from: ir.shiftapp.core.JalaliDate, to: ir.shiftapp.core.JalaliDate): Map<String, List<ir.shiftapp.core.Entry>> =
    entriesOnce(pid, from.key, to.key).groupBy({ it.dateKey }, { ir.shiftapp.core.Entry(it.type, it.shiftCode, it.minutes, it.text) })
