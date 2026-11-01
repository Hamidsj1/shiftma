package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*

private val INK = Color(0xFF1B1D2A)

@Composable
fun CalendarScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val sel by vm.selected.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    val patterns by vm.patterns.collectAsState()
    var dayDialog by remember { mutableStateOf<JalaliDate?>(null) }
    val today = remember { JalaliDate.today() }
    val cal by vm.holidayCalendar.collectAsState()

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShiftProgressStrip(vm)      // نوار باریک شیفت در جریان (فقط وقتی شیفتی در جریان است)
        MonthSwitcher(vm, month)

        // انتخاب الگو (گروه شیفت) برای نمایش در تقویم
        var open by remember { mutableStateOf(false) }
        var showOccasions by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { open = true }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Ico(R.drawable.ic_patterns, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                    Text(sel?.name ?: "الگویی نیست", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                    Ico(R.drawable.ic_expand_more, size = 18.dp, tint = Pal.muted)
                }
                DropdownMenu(open, { open = false }) {
                    patterns.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.select(p.id); open = false }) }
                }
            }
            Row(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { showOccasions = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Ico(R.drawable.ic_event, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                Text("مناسبت‌ها", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        if (showOccasions) OccasionsDialog(vm, cal) { showOccasions = false }
        if (sel == null) {
            EmptyState(R.drawable.ic_tab_calendar, "الگویی تعریف نشده", text = "برای شروع، در بخش «بیشتر ← الگوهای شیفت» یک الگو و تاریخ مبنا تعریف کنید.")
            return
        }
        val p = sel!!; val base = p.base(); val pat = remember(p.pattern) { ShiftPattern.parse(p.pattern) }

        Row(Modifier.fillMaxWidth()) {
            WEEK_SHORT.forEachIndexed { i, w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
                    color = if (i == 6) Pal.holiday else Pal.muted
                )
            }
        }
        val offset = JalaliDate(month.year, month.month, 1).dayOfWeek
        val days = JalaliCalendar.daysInMonth(month.year, month.month)
        LazyVerticalGrid(
            GridCells.Fixed(7), Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(offset) { Spacer(Modifier.height(1.dp)) }
            items((1..days).toList()) { d ->
                val date = JalaliDate(month.year, month.month, d)
                val r = DayResolver.resolve(base, pat, date, entries[date.key].orEmpty())
                DayCell(d, r, cal.kind(date) != DayKind.NORMAL, date == today) { dayDialog = date }
            }
        }
        ReminderStrip(vm)           // نوار باریک یادآوری‌ها زیر تقویم
        MonthOccasions(cal, vm.prefs.collectAsState().value.lunarShift, month.year, month.month)
        CalendarLegend()
    }
    dayDialog?.let { DayDialog(vm, it) { dayDialog = null } }
}

@Composable
private fun DayCell(day: Int, r: EffectiveDay, holiday: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(60.dp).clip(shape).background(shiftColor(r.effectiveCode))
            .then(if (isToday) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.fa(), style = MaterialTheme.typography.titleSmall, color = if (holiday) Color(0xFFB71C1C) else INK)
            Text(
                shiftName(r.effectiveCode), style = MaterialTheme.typography.labelSmall, color = INK.copy(alpha = 0.78f),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
            )
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (r.overridden) Dot(Pal.markOverride, 5.dp)
            if (r.onLeave) Dot(Pal.markLeave, 5.dp)
            if (r.overtimeMinutes > 0) Dot(Pal.markOvertime, 5.dp)
            if (r.entries.any { it.type == EntryType.NOTE }) Dot(Pal.markNote, 5.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShiftLegend()
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Pal.markOverride to "تغییر دستی", Pal.markLeave to "مرخصی",
                Pal.markOvertime to "اضافه‌کاری", Pal.markNote to "یادداشت"
            ).forEach { (c, t) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(c, 8.dp)
                    Text(t, style = MaterialTheme.typography.labelMedium, color = Pal.muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayDialog(vm: MainVm, date: JalaliDate, initialType: String = EntryType.NOTE, onClose: () -> Unit) {
    val p = vm.selected.collectAsState().value ?: return
    val entries by vm.entriesByDate.collectAsState()
    val raw by vm.rawEntries.collectAsState()
    val employees by vm.employees.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    val lunarShift = vm.prefs.collectAsState().value.lunarShift
    val r = DayResolver.resolve(p.base(), ShiftPattern.parse(p.pattern), date, entries[date.key].orEmpty())
    var type by remember { mutableStateOf(initialType) }
    var code by remember { mutableStateOf<Int?>(null) }
    var minutes by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var emp by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    AppDialog(
        "${WEEK_FULL[date.dayOfWeek]} ${date.day.fa()} ${MONTHS[date.month - 1]} ${date.year.fa()}", onClose,
        confirmText = "بستن", onConfirm = onClose
    ) {
        // وضعیت روز
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("شیفت طبق الگو", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                ShiftChip(r.computedCode, compact = true, modifier = Modifier.widthIn(min = 84.dp))
            }
            if (r.overridden) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("شیفت نهایی", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                ShiftChip(r.effectiveCode, compact = true, modifier = Modifier.widthIn(min = 84.dp))
            }
            val title = cal.title(date)
            if (title != null) Text("تعطیل رسمی: $title", style = MaterialTheme.typography.labelLarge, color = Pal.holiday)
            else if (cal.kind(date) == DayKind.FRIDAY) Text("جمعه (تعطیل هفتگی)", style = MaterialTheme.typography.labelLarge, color = Pal.holiday)
            Occasions.all(date, lunarShift).forEach { (t, k) -> Text("مناسبت: $t${kindTag(k)}", style = MaterialTheme.typography.labelLarge, color = Pal.muted) }
        }

        // ثبت‌های همین روز
        val dayEntries = raw.filter { it.dateKey == date.key }
        dayEntries.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconBadge(entryIcon(e.type), size = 32.dp, iconSize = 18.dp, container = entryColor(e.type).copy(alpha = 0.16f), tint = entryColor(e.type), circle = true)
                Text(buildString {
                    append(TYPE_FA[e.type] ?: e.type)
                    e.shiftCode?.let { append(": ${shiftName(it)}") }
                    e.minutes?.let { append(": ${it.fa()} دقیقه") }
                    employees.firstOrNull { it.id == e.employeeId }?.let { append(" (${it.name})") }
                    if (e.text.isNotBlank()) append(" — ${e.text}")
                }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                SmallIconButton(R.drawable.ic_delete, "حذف", { vm.deleteEntry(e.id) }, tint = MaterialTheme.colorScheme.error)
            }
        }

        HorizontalDivider(color = Pal.hairline)
        Text("افزودن", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EntryType.ALL.forEach { t -> AppChip(TYPE_FA[t] ?: t, type == t, { type = t; err = null }) }
        }
        if (type == EntryType.MANUAL_SHIFT || type == EntryType.EXCHANGE) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ShiftType.DEFAULTS.forEach { s -> AppChip(s.name, code == s.code, { code = s.code }, dotColor = shiftColor(s.code)) }
            }
        }
        if (type == EntryType.EXCHANGE && employees.isNotEmpty()) {
            Text("با همکار", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                employees.forEach { e -> AppChip(e.name, emp == e.id, { emp = if (emp == e.id) null else e.id }) }
            }
        }
        if (type == EntryType.OVERTIME) AppField(minutes, { minutes = it.filter(Char::isDigit) }, "دقیقه", keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
        AppField(text, { text = it }, "توضیح / یادداشت", singleLine = false)
        err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        AppButton("ثبت", {
            err = vm.addEntry(date, type, code.takeIf { type == EntryType.MANUAL_SHIFT || type == EntryType.EXCHANGE },
                minutes.toIntOrNull().takeIf { type == EntryType.OVERTIME }, text, emp.takeIf { type == EntryType.EXCHANGE })
            if (err == null) { text = ""; minutes = ""; code = null; emp = null }
        }, Modifier.fillMaxWidth(), icon = R.drawable.ic_check)
    }
}

internal fun kindTag(k: OccasionKind): String = when (k) {
    OccasionKind.SOLAR -> ""
    OccasionKind.GLOBAL -> " (جهانی)"
    OccasionKind.LUNAR -> " (قمری، تقریبی)"
}

private fun shiftLabel(n: Int): String = when {
    n < 0 -> "−${(-n).fa()}"
    n > 0 -> "+${n.fa()}"
    else -> "۰"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccasionsDialog(vm: MainVm, cal: HolidayCalendar, onClose: () -> Unit) {
    val today = remember { JalaliDate.today() }
    val shift = vm.prefs.collectAsState().value.lunarShift
    var filter by remember { mutableStateOf<OccasionKind?>(null) }   // null = همه
    val upcoming = remember(cal, shift) { UpcomingOccasions.upcoming(today, cal, shift) }
    val shown = upcoming.filter { filter == null || it.kind == filter }.take(40)
    AppDialog("مناسبت‌ها", onClose, confirmText = "بستن", onConfirm = onClose) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf<Pair<String, OccasionKind?>>("همه" to null, "ایران" to OccasionKind.SOLAR, "جهانی" to OccasionKind.GLOBAL, "قمری" to OccasionKind.LUNAR)
                .forEach { (t, k) -> AppChip(t, filter == k, { filter = k }) }
        }
        if (filter == null || filter == OccasionKind.LUNAR) {
            Text("جابه‌جایی مناسبت‌های قمری نسبت به تقویم رسمی (روز؛ مثبت = دیرتر)", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (-2..2).forEach { n -> AppChip(shiftLabel(n), shift == n, { vm.setPref(PrefKeys.LUNAR_SHIFT, n.toString()) }) }
            }
        }
        SectionLabel("پیش‌رو")
        if (shown.isEmpty()) Text("—", color = Pal.muted)
        shown.forEach { u -> CountdownRow(u.title + kindTag(u.kind), u.date, u.daysLeft, u.holiday) }
        HintText("مناسبت‌های قمری بر پایهٔ محاسبه‌اند و ممکن است با رؤیت ماه در ایران ۱ تا ۲ روز فرق کنند؛ با دکمه‌های بالا تنظیم کنید. تعطیلی‌های رسمی قمری را هم از «بیشتر ← تعطیلات رسمی» وارد کنید.")
    }
}

@Composable
private fun CountdownRow(title: String, date: JalaliDate, daysLeft: Int, holiday: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (holiday) Pal.holiday else MaterialTheme.colorScheme.onSurface)
            Text("${WEEK_FULL[date.dayOfWeek]} ${date.day.fa()} ${MONTHS[date.month - 1]}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
        Text(daysLeftText(daysLeft), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** مناسبت‌های ماهِ نمایش‌داده‌شده، زیر تقویم. */
@Composable
private fun MonthOccasions(cal: HolidayCalendar, shift: Int, year: Int, mon: Int) {
    val items = remember(cal, shift, year, mon) {
        (1..JalaliCalendar.daysInMonth(year, mon)).flatMap { d ->
            val date = JalaliDate(year, mon, d)
            val h = cal.title(date)
            buildList {
                if (h != null) add(Triple(d, h, true))
                Occasions.all(date, shift).forEach { (t, k) -> if (t != h) add(Triple(d, t + kindTag(k), false)) }
            }
        }
    }
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().heightIn(max = 88.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { (d, t, holiday) ->
            Text("${d.fa()} ${MONTHS[mon - 1]} · $t", style = MaterialTheme.typography.bodySmall, color = if (holiday) Pal.holiday else Pal.muted)
        }
    }
}
