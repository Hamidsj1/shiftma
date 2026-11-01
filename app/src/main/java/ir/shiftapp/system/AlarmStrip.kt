package ir.shiftapp.system

import android.content.Context
import ir.shiftapp.core.DayResolver
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.ShiftPattern
import ir.shiftapp.core.ShiftType
import ir.shiftapp.data.AppDb
import ir.shiftapp.data.entriesByDateOnce
import ir.shiftapp.data.selectedPattern

/** یک خانهٔ نوار تقویم آلارم: روز هفته، شمارهٔ روز، نام شیفت و رنگ آن. */
class StripDay(
    val weekday: String, val num: String, val shift: String,
    val color: Int, val rest: Boolean, val today: Boolean
)

/** نوار ۷ روزهٔ شیفت‌ها برای اعلان و صفحهٔ آلارم؛ اگر الگویی انتخاب نشده باشد خالی برمی‌گردد. */
object AlarmStrip {
    private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    private const val DAYS = 7

    suspend fun load(ctx: Context): List<StripDay> {
        return try {
            val dao = AppDb.get(ctx.applicationContext).dao()
            val p = dao.selectedPattern() ?: return emptyList()
            val today = JalaliDate.today()
            val entries = dao.entriesByDateOnce(p.id, today, today.plusDays(DAYS))
            val pattern = ShiftPattern.parse(p.pattern)
            val base = JalaliDate(p.baseYear, p.baseMonth, p.baseDay)
            (0 until DAYS).map { i ->
                val d = today.plusDays(i)
                val r = DayResolver.resolve(base, pattern, d, entries[d.key].orEmpty())
                val t = ShiftType.byCode(r.effectiveCode)
                val rest = r.onLeave || t?.isRest == true
                StripDay(
                    WEEKDAY_SHORT[d.dayOfWeek], Fa.num(d.day),
                    if (r.onLeave) "مرخصی" else (t?.name ?: "—"),
                    (t?.color ?: 0xFFCCCCCC).toInt(), rest, i == 0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
