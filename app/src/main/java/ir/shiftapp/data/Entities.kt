package ir.shiftapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shift_patterns")
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val company: String = "",
    val baseYear: Int, val baseMonth: Int, val baseDay: Int,   // تاریخ مبنا؛ برای هر الگو جدا (مثل ShiftFormat در APK اصلی)
    val pattern: String,                                         // "1;1;2;2;4;3;3;4"
    val supervisor: String = "",       // مسئول شیفت (اختیاری)
    val headSupervisor: String = "",   // سرشیفت کل (اختیاری)
    val section: String = ""           // بخش / واحد (اختیاری)
)

/** مرخصی / اضافه‌کاری / استراحت / تعویض / تغییر دستی / یادداشت در یک جدول با ستون type. */
@Entity(tableName = "day_entries", indices = [Index("patternId", "dateKey")])
data class DayEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val dateKey: String,            // YYYYMMDD
    val type: String,
    val shiftCode: Int? = null,
    val minutes: Int? = null,
    val text: String = "",
    val employeeId: Long? = null    // همکار (برای تعویض شیفت)
)

@Entity(tableName = "employees")
data class Employee(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val note: String = "")

@Entity(tableName = "settings")
data class Setting(@PrimaryKey val key: String, val value: String)

/** تعطیلی رسمیِ واردشده توسط کاربر (مثلاً تعطیلات قمری که هر سال جابه‌جا می‌شوند). */
@Entity(tableName = "holidays")
data class Holiday(@PrimaryKey val dateKey: String, val title: String)
