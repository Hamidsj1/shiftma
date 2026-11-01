package ir.shiftapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import java.time.LocalDate
import java.time.ZoneId

/**
 * نگه‌دارندهٔ حالت یک بخش که در تنظیمات برنامه (جدول Setting) ذخیره می‌شود، پس با پشتیبان‌گیری و بازیابی می‌آید.
 * update فوراً در UI دیده می‌شود و رشته را ذخیره می‌کند؛ sync مقدار رسیده از دیتابیس را (بارگذاری اولیه یا بازیابی پشتیبان)
 * اعمال می‌کند، مگر اینکه همان چیزی باشد که خودمان تازه نوشته‌ایم.
 */
internal class RawHolder<T>(
    initial: T, private val enc: (T) -> String, private val dec: (String) -> T, private val onWrite: (String) -> Unit
) {
    var data by mutableStateOf(initial)
        private set
    /** با هر بار اعمال مقدار بیرونی (بارگذاری یا بازیابی) زیاد می‌شود تا فیلدهای متنی دوباره ساخته شوند. */
    var epoch by mutableStateOf(0)
        private set
    private val written = ArrayDeque<String>()

    fun update(f: (T) -> T) {
        data = f(data)
        val s = enc(data)
        written.addLast(s); while (written.size > 12) written.removeFirst()
        onWrite(s)
    }

    fun sync(raw: String) {
        if (raw.isEmpty() || raw in written) return
        data = dec(raw)
        epoch++
    }
}

/** شیفت ۷ روز آینده + پیشنهاد بر اساس روز استراحت/کاری. اگر هنوز الگویی انتخاب نشده چیزی نشان نمی‌دهد. */
@Composable
internal fun ShiftWeekStrip(vm: MainVm, restTip: String, workTip: String, compact: Boolean = false) {
    val pat by vm.selected.collectAsState()
    val month by vm.month.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    val p = pat ?: return
    val pattern = remember(p.pattern) { ShiftPattern.parse(p.pattern) }
    val today = remember { JalaliDate.today() }
    class Cell(val date: JalaliDate, val name: String, val rest: Boolean, val color: Long?)
    val cells = remember(p, pattern, month, entries) {
        (0..6).map { i ->
            val d = today.plusDays(i)
            val list = if (d.year == month.year && d.month == month.month) entries[d.key].orEmpty() else emptyList()
            val r = DayResolver.resolve(p.base(), pattern, d, list)
            val t = ShiftType.byCode(r.effectiveCode)
            val rest = r.onLeave || t?.isRest == true
            Cell(d, if (r.onLeave) "مرخصی" else t?.name ?: "—", rest, t?.color)
        }
    }
    val nextRest = cells.drop(1).firstOrNull { it.rest }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.ic_tab_calendar, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                    Text("شیفت‌های هفتهٔ پیش‌رو", style = MaterialTheme.typography.titleSmall)
                }
                ChipRow {
                    cells.forEach { c ->
                        AppChip(
                            "${Fa.WEEK_FULL[c.date.dayOfWeek]} · ${c.name}", c.rest, {},
                            dotColor = c.color?.let { Color(it) }
                        )
                    }
                }
            }
            Text(if (cells[0].rest) restTip else workTip, style = MaterialTheme.typography.bodyMedium)
            if (nextRest != null && !cells[0].rest) {
                HintText("نزدیک‌ترین روز استراحت: ${Fa.WEEK_FULL[nextRest.date.dayOfWeek]} ${Fa.num(nextRest.date.day)} ${Fa.MONTHS[nextRest.date.month - 1]}", color = Pal.green)
            }
        }
    }
}

/** یادآور ساده بر پایهٔ آلارم‌های تکیِ برنامه: یک‌بار یا برای ۷ روز آینده در ساعت دلخواه. */
@Composable
internal fun ReminderDialog(vm: MainVm, defaultLabel: String, onClose: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val ctx = LocalContext.current
    var time by remember { mutableStateOf("18:00") }
    var label by remember { mutableStateOf(defaultLabel) }
    var mode by remember { mutableStateOf(1) }
    val mm = ShiftTime.parseClock(time)
    AppDialog(
        "یادآور", onClose, confirmText = "ثبت", dismissText = "انصراف", confirmEnabled = mm != null && label.isNotBlank(),
        onConfirm = {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val start = LocalDate.now(zone)
            val days = if (mode == 0) 2 else 7
            var id = SingleAlarm.nextId(prefs.alarms)
            val made = ArrayList<SingleAlarm>()
            for (i in 0 until days) {
                val at = SingleAlarm.at(start.plusDays(i.toLong()), mm ?: 0, zone)
                if (at <= now) continue
                made.add(SingleAlarm(id++, at, label.trim()))
                if (mode == 0) break
            }
            if (made.isNotEmpty()) vm.setAlarms(prefs.alarms + made)
            Toast.makeText(ctx, if (made.isEmpty()) "زمان گذشته است" else "یادآور ثبت شد", Toast.LENGTH_SHORT).show()
            onClose()
        }
    ) {
        AppField(time, { time = it }, "ساعت (مثل ۱۸:۳۰)", ltr = true, isError = mm == null)
        AppField(label, { label = it.take(40) }, "عنوان یادآور")
        Segmented(listOf("فقط یک‌بار", "۷ روز آینده"), mode) { mode = it }
        HintText("یادآور با همان سیستم آلارم‌های برنامه کار می‌کند و در «آلارم‌های من» هم دیده و حذف می‌شود.")
    }
}
