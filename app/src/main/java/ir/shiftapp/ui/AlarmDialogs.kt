package ir.shiftapp.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.data.PatternEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * پنجره‌های آلارم: انتخاب‌گر مدت قبل از شروع، آلارم‌های تکی و «جانشین شب».
 * همه‌چیز در تنظیمات ذخیره می‌شود (کلید pref_alarms) و با بکاپ هم می‌آید.
 */

private const val MAX_LEAD_MIN = 72 * 60

internal fun MainVm.setAlarms(list: List<SingleAlarm>) = setPref(PrefKeys.ALARMS, SingleAlarm.encode(list))

/** درخواست مجوز اعلان (اندروید ۱۳ به بالا)؛ بدون مجوز آلارم صدا نمی‌دهد. */
@Composable
internal fun rememberNotifRequest(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private fun clockOfMs(ms: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return Fa.digits(ShiftTime.fmt(t.hour * 60 + t.minute))
}

private fun jalaliText(d: JalaliDate) = "${WEEK_FULL[d.dayOfWeek]} ${d.day.fa()} ${MONTHS[d.month - 1]}"

// ───────────────────────── مدت قبل از شروع ─────────────────────────

@Composable
private fun StepButton(text: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** «+ مقدار −» با میان‌بُرهای همزمان / ۳۰ دقیقه / ۱ ساعت / ۲ ساعت؛ لمس مقدار، ورود دستی ساعت و دقیقه را باز می‌کند. */
@Composable
internal fun LeadPicker(minutes: Int, onChange: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val presets = listOf(0, 30, 60, 120)
    fun step(v: Int) = if (v >= 120) 15 else 5
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("قبل از شروع شیفت", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton("+", "افزایش") { onChange((minutes + step(minutes)).coerceAtMost(MAX_LEAD_MIN)) }
            Box(
                Modifier.weight(1f).height(46.dp).clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { editing = true },
                contentAlignment = Alignment.Center
            ) {
                Text(leadLabel(minutes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            StepButton("−", "کاهش") { onChange((minutes - step(minutes)).coerceAtLeast(0)) }
        }
        Segmented(listOf("همزمان", "۳۰ دقیقه", "۱ ساعت", "۲ ساعت"), presets.indexOf(minutes)) { onChange(presets[it]) }
    }
    if (editing) LeadEditDialog(minutes, { onChange(it); editing = false }, { editing = false })
}

@Composable
private fun LeadEditDialog(minutes: Int, onSave: (Int) -> Unit, onClose: () -> Unit) {
    var hh by remember { mutableStateOf(Fa.digits((minutes / 60).toString())) }
    var mm by remember { mutableStateOf(Fa.digits((minutes % 60).toString())) }
    val h = Fa.latin(hh).toIntOrNull() ?: 0
    val m = Fa.latin(mm).toIntOrNull() ?: 0
    val bad = m > 59 || h * 60 + m > MAX_LEAD_MIN
    AppDialog("مدت دلخواه", onClose, confirmText = "تأیید", onConfirm = { if (!bad) onSave(h * 60 + m) },
        dismissText = "انصراف", confirmEnabled = !bad) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(hh, { hh = it.filter { c -> c.isDigit() }.take(2) }, "ساعت", Modifier.weight(1f), keyboard = KeyboardType.Number)
            AppField(mm, { mm = it.filter { c -> c.isDigit() }.take(2) }, "دقیقه", Modifier.weight(1f), keyboard = KeyboardType.Number,
                isError = m > 59, supporting = if (m > 59) "حداکثر ۵۹" else null)
        }
        HintText("حداکثر ۷۲ ساعت. صفر یعنی هم‌زمان با شروع شیفت.")
    }
}

// ───────────────────────── ساعت هر شیفت (یک خط) ─────────────────────────

@Composable
internal fun ShiftTimeLine(code: Int, prefs: Prefs, vm: MainVm) {
    val t = prefs.shiftTimes[code]
    var start by remember { mutableStateOf(t?.startMin) }
    var end by remember { mutableStateOf(t?.endMin) }
    fun save() { val s = start; val e = end; if (s != null && e != null && (s != e || code == 5)) vm.setPref(PrefKeys.time(code), "$s-$e") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShiftChip(code, Modifier.width(64.dp), compact = true)
        ClockField("شروع", t?.startMin, { start = it; save() }, Modifier.weight(1f))
        ClockField("پایان", t?.endMin, { end = it; save() }, Modifier.weight(1f))
    }
}

// ───────────────────────── آلارم‌های تکی ─────────────────────────

@Composable
internal fun SingleAlarmsDialog(vm: MainVm, p: PatternEntity?, prefs: Prefs, onClose: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val today = remember { JalaliDate.today() }
    val list = prefs.alarms.filter { !it.isSub && it.triggerAtMs > now }.sortedBy { it.triggerAtMs }
    AppDialog("آلارم‌های تکی", onClose, confirmText = "بستن", onConfirm = onClose) {
        if (list.isEmpty()) HintText("هنوز آلارمی نساخته‌اید. آلارم در همان روز و ساعت با صدای آلارم می‌زند؛ می‌توانید آن را یک‌بار یا تکرارشونده (هر روز یا روزهای دلخواه هفته) بسازید. با لمس هر روز در تقویم صفحهٔ اول هم می‌شود برای همان روز آلارم ساخت.")
        list.forEach { a ->
            val d = JalaliCalendar.fromLocalDate(Instant.ofEpochMilli(a.triggerAtMs).atZone(zone).toLocalDate())
            val diff = d.toJdn() - today.toJdn()
            val rel = when { diff <= 0 -> "امروز"; diff == 1 -> "فردا"; else -> "${diff.fa()} روز دیگر" }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(clockOfMs(a.triggerAtMs, zone), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (a.enabled) MaterialTheme.colorScheme.onSurface else Pal.muted, modifier = Modifier.padding(start = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label.ifBlank { "آلارم" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    HintText("${jalaliText(d)} · $rel" + (if (a.repeating) " · ${SingleAlarm.repeatText(a.repeat)}" else ""))
                }
                Switch(checked = a.enabled, onCheckedChange = { v -> vm.setAlarms(prefs.alarms.map { if (it.id == a.id) it.copy(enabled = v) else it }) })
                SmallIconButton(R.drawable.ic_delete, "حذف آلارم", { vm.setAlarms(prefs.alarms.filter { it.id != a.id }) },
                    tint = MaterialTheme.colorScheme.error)
            }
        }
        AppButton("آلارم جدید", { adding = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    }
    if (adding) NewAlarmDialog(vm, p, prefs) { adding = false }
}

@Composable
internal fun NewAlarmDialog(vm: MainVm, p: PatternEntity?, prefs: Prefs, initialDate: JalaliDate? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { JalaliDate.today() }
    val requestNotif = rememberNotifRequest()
    val fixed = initialDate != null                                // از تقویم صفحهٔ اول: روز از قبل مشخص است
    var pick by remember { mutableStateOf(1) }                    // ۰ امروز، ۱ فردا، ۲ شیفت بعدی، ۳ انتخاب روز
    var date by remember { mutableStateOf(initialDate ?: today.plusDays(1)) }
    var month by remember { mutableStateOf(JalaliDate(date.year, date.month, 1)) }
    var rep by remember { mutableStateOf(0) }                     // ۰ یک‌بار، ۱ هر روز، ۲ روزهای انتخابی
    var days by remember { mutableStateOf(SingleAlarm.bit(date.toLocalDate().dayOfWeek)) }
    val mask = when (rep) { 0 -> 0; 1 -> SingleAlarm.ALL_DAYS; else -> days }
    var minute by remember { mutableStateOf<Int?>(null) }
    var label by remember { mutableStateOf("") }

    fun codeOf(d: JalaliDate): Int? = p?.let {
        runCatching { DayResolver.resolve(it.base(), ShiftPattern.parse(it.pattern), d, emptyList()).effectiveCode }.getOrNull()
    }
    fun workCodeOf(d: JalaliDate): Int? = codeOf(d)?.takeIf { ShiftType.byCode(it)?.isRest != true }
    fun nextShiftDay(): JalaliDate? = (1..60).map { today.plusDays(it) }.firstOrNull { workCodeOf(it) != null }

    val mm = minute
    val code = codeOf(date)
    val summary = if (mm == null) "ساعت را وارد کنید (مثلاً ۰۵:۳۰)" else
        "${jalaliText(date)} ساعت ${Fa.digits(ShiftTime.fmt(mm))}" + (code?.let { " · روز شما: ${shiftName(it)}" } ?: "") +
            (if (mask != 0) "\n${SingleAlarm.repeatText(mask)} (از اولین روز مطابق)" else "")

    AppDialog("آلارم جدید", onClose, confirmText = "ذخیره", dismissText = "انصراف", confirmEnabled = mm != null && (rep != 2 || days != 0), onConfirm = {
        if (mm != null) {
            val at = SingleAlarm.at(date.toLocalDate(), mm, zone)
            val now = System.currentTimeMillis()
            val base = SingleAlarm(SingleAlarm.nextId(prefs.alarms), at, label.trim(), repeat = mask)
            // تکراری: اولین روزِ مطابق با ماسک از همین تاریخ به بعد؛ یک‌باره: باید در آینده باشد
            val ready = if (mask == 0) base.takeIf { at > now } else base.advance(maxOf(now, at - 1), zone)
            if (ready == null) {
                Toast.makeText(ctx, "این زمان گذشته است", Toast.LENGTH_SHORT).show()
            } else {
                vm.setAlarms(prefs.alarms + ready)
                requestNotif()
                onClose()
            }
        }
    }) {
        if (!fixed) Segmented(listOf("امروز", "فردا", "شیفت بعدی", "انتخاب روز"), pick) { i ->
            pick = i
            when (i) {
                0 -> date = today
                1 -> date = today.plusDays(1)
                2 -> nextShiftDay()?.let { date = it }
                else -> month = JalaliDate(date.year, date.month, 1)
            }
        }
        if (!fixed && pick == 3) {
            MonthGrid(month, date, today, ::workCodeOf, { month = month.plusDays(-1).let { JalaliDate(it.year, it.month, 1) } },
                { month = month.plusDays(JalaliCalendar.daysInMonth(month.year, month.month)).let { JalaliDate(it.year, it.month, 1) } }) { date = it }
        }
        if (fixed) Text(jalaliText(date), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockField("ساعت", null, { minute = it }, Modifier.weight(1f), onInvalid = { minute = null })
            AppField(label, { label = it.take(30) }, "برچسب (اختیاری)", Modifier.weight(1.4f))
        }
        Segmented(listOf("یک‌بار", "هر روز", "روزهای هفته"), rep) { rep = it }
        if (rep == 2) {
            ChipRow {
                SingleAlarm.WEEK.forEach { (dow, _, short) ->
                    val on = (days and SingleAlarm.bit(dow)) != 0
                    AppChip(short, on, { days = days xor SingleAlarm.bit(dow) })
                }
            }
            if (days == 0) HintText("حداقل یک روز را انتخاب کنید.")
        }
        Text(
            summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(12.dp)
        )
    }
}

@Composable
internal fun MonthGrid(
    month: JalaliDate, selected: JalaliDate, today: JalaliDate, codeOf: (JalaliDate) -> Int?,
    onPrev: () -> Unit, onNext: () -> Unit, onSelect: (JalaliDate) -> Unit
) {
    val days = JalaliCalendar.daysInMonth(month.year, month.month)
    val first = month.dayOfWeek
    val cells: List<Int?> = List(first) { null } + (1..days).map { it } + List((7 - (first + days) % 7) % 7) { null }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(R.drawable.ic_chevron_left, "ماه قبل", onPrev)
            Text("${MONTHS[month.month - 1]} ${month.year.fa()}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            SmallIconButton(R.drawable.ic_chevron_right, "ماه بعد", onNext)
        }
        Row(Modifier.fillMaxWidth()) {
            WEEK_SHORT.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Pal.muted, textAlign = TextAlign.Center) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.weight(1f).height(36.dp)) else {
                        val d = JalaliDate(month.year, month.month, day)
                        val past = d < today
                        val sel = d == selected
                        val code = codeOf(d)
                        Box(
                            Modifier.weight(1f).height(36.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(when { sel -> MaterialTheme.colorScheme.primary; code != null -> shiftColor(code).copy(alpha = 0.45f); else -> Color.Transparent })
                                .then(if (past) Modifier else Modifier.clickable(role = Role.Button) { onSelect(d) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.fa(), style = MaterialTheme.typography.bodySmall,
                                color = when { sel -> MaterialTheme.colorScheme.onPrimary; past -> Pal.muted.copy(alpha = 0.4f); else -> MaterialTheme.colorScheme.onSurface },
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        HintText("رنگ روزها بر اساس الگوی شیفت شماست.")
    }
}

// ───────────────────────── جانشین شب ─────────────────────────

/** «جانشین شب»: شبی که خارج از شیفت خودتان جانشین می‌مانید؛ دو هشدار برای ثبت خروج و ورود مجدد دور نیمه‌شب. */
@Composable
internal fun SubNightDialog(vm: MainVm, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val requestNotif = rememberNotifRequest()
    val now = System.currentTimeMillis()
    val active = SubNight.active(prefs.alarms, now)
    var night by remember { mutableStateOf(0) }
    // دو حالت: ۵ دقیقه (۲۳:۵۵ و ۰۰:۰۵) یا ۱۰ دقیقه (۲۳:۵۰ و ۰۰:۱۰) فاصله از نیمه‌شب
    var gap by remember { mutableStateOf(if (prefs.subInMin >= 8 || prefs.subOutMin <= 24 * 60 - 8) 10 else 5) }
    val outMin = 24 * 60 - gap
    val inMin = gap
    AppDialog(
        "جانشین شب", onClose, confirmText = if (active.isEmpty()) "فعال کن" else "به‌روزرسانی", dismissText = if (active.isEmpty()) "انصراف" else "خاموش کردن",
        onDismissClick = if (active.isEmpty()) null else { { vm.setAlarms(prefs.alarms.filterNot { it.isSub }); onClose() } },
        onConfirm = {
            run {
                val rest = prefs.alarms.filterNot { it.isSub }
                val plan = SubNight.plan(now, zone, LocalDate.now(zone).plusDays(night.toLong()), outMin, inMin, SingleAlarm.nextId(rest))
                if (plan.isEmpty()) Toast.makeText(ctx, "زمان این شب گذشته است", Toast.LENGTH_SHORT).show()
                else {
                    vm.setPref(PrefKeys.SUB_OUT, outMin.toString()); vm.setPref(PrefKeys.SUB_IN, inMin.toString())
                    vm.setAlarms(rest + plan); requestNotif(); onClose()
                }
            }
        }
    ) {
        HintText("وقتی خارج از شیفت خودتان جانشین می‌مانید، برای ثبت اضافه‌کاری در دو روز باید قبل و بعد از ساعت ۱۲ شب کارت‌زنی و چهره‌زنی کنید. این دو آلارم یادتان می‌اندازند.")
        Segmented(listOf("امشب", "فردا شب", "پس‌فردا شب"), night) { night = it }
        Text("فاصله از ساعت ۱۲ شب", style = MaterialTheme.typography.labelLarge)
        Segmented(listOf("۵ دقیقه", "۱۰ دقیقه"), if (gap == 5) 0 else 1) { gap = if (it == 0) 5 else 10 }
        HintText("ثبت خروج ساعت ${Fa.digits(ShiftTime.fmt(outMin))} (قبل از نیمه‌شب) و ثبت ورود ساعت ${Fa.digits(ShiftTime.fmt(inMin))} (بعد از نیمه‌شب).")
        if (active.isNotEmpty()) {
            val n = SubNight.nightOf(prefs.alarms, now, zone)?.let { JalaliCalendar.fromLocalDate(it) }
            HintText("فعال: " + (n?.let { "شب ${jalaliText(it)}" } ?: "") + " · " + active.joinToString(" و ") { clockOfMs(it.triggerAtMs, zone) })
        }
        HintText("بعد از هر هشدار می‌توانید «انجام دادم» یا «۵ دقیقه بعد» بزنید. بعد از اجرا، جانشین شب خودکار خاموش می‌شود.")
    }
}
