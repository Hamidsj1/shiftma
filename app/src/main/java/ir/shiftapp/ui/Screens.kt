package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.shiftapp.R
import ir.shiftapp.data.PatternEntity
import ir.shiftapp.core.*

internal val MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
internal val WEEK_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
internal val WEEK_FULL = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
internal val TYPE_FA = mapOf(
    EntryType.MANUAL_SHIFT to "تغییر دستی", EntryType.EXCHANGE to "تعویض شیفت", EntryType.LEAVE to "مرخصی",
    EntryType.OVERTIME to "اضافه‌کاری", EntryType.REST to "استراحت", EntryType.NOTE to "یادداشت"
)
fun Int.fa(): String = toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
internal fun shiftName(code: Int) = ShiftType.byCode(code)?.name ?: "کد ${code.fa()}"
internal fun shiftColor(code: Int) = Color(ShiftType.byCode(code)?.color ?: 0xFFCCCCCC)

/** «شیفت A»؛ اگر نام از قبل با «شیفت» شروع شود تکرار نمی‌شود. */
internal fun groupTitle(name: String) = if (name.startsWith("شیفت")) name else "شیفت $name"

/** روز جاری: پر با نیلی تیره، عدد سفید و نوار کوچک رنگ شیفت زیر عدد؛ در تقویم، تقویم صفحهٔ اول و ویجت یکسان. */
internal val TODAY_BG = androidx.compose.ui.graphics.Color(0xFF3F36C9)

/** حرف گروه (A..D) از نام الگو، مثل «A» یا «شیفت D»؛ برای نشان رنگی. null اگر نام این شکل نباشد. */
internal fun groupLetter(name: String): Char? {
    val t = name.trim().removePrefix("شیفت").trim()
    return if (t.length == 1 && t[0] in 'A'..'H') t[0] else null
}

/** رنگ ثابت هر گروه تا گروه‌ها در فهرست و عنوان با یک نگاه تشخیص داده شوند. */
internal fun groupColor(letter: Char): androidx.compose.ui.graphics.Color = when (letter) {
    'A' -> androidx.compose.ui.graphics.Color(0xFF4FB3E8)
    'B' -> androidx.compose.ui.graphics.Color(0xFF2E9E6B)
    'C' -> androidx.compose.ui.graphics.Color(0xFF8E5BD0)
    'D' -> androidx.compose.ui.graphics.Color(0xFFE05C9A)
    else -> androidx.compose.ui.graphics.Color(0xFF5F6B7A)
}

/** آیکن و رنگ هر نوع ثبت؛ در کاردکس، تقویم و پنجرهٔ روز یکسان استفاده می‌شود. */
internal fun entryIcon(type: String): Int = when (type) {
    EntryType.NOTE -> R.drawable.ic_note
    EntryType.REST -> R.drawable.ic_home
    EntryType.LEAVE -> R.drawable.ic_sun
    EntryType.EXCHANGE -> R.drawable.ic_swap
    EntryType.OVERTIME -> R.drawable.ic_timer
    else -> R.drawable.ic_edit
}

@Composable
internal fun entryColor(type: String): Color = when (type) {
    EntryType.NOTE -> MaterialTheme.colorScheme.primary
    EntryType.REST -> Color(0xFF5B7DB1)
    EntryType.LEAVE -> Pal.green
    EntryType.EXCHANGE -> Pal.amber
    EntryType.OVERTIME -> Pal.coral
    else -> MaterialTheme.colorScheme.secondary
}

private val EVENT_SHORT = mapOf(
    EntryType.NOTE to "یادداشت", EntryType.REST to "استراحت", EntryType.LEAVE to "مرخصی",
    EntryType.EXCHANGE to "تعویض", EntryType.OVERTIME to "اضافه"
)

// ===================== قاب مشترک جدول‌ها =====================

/** قاب مشترک چهارشیفت و کاردکس: کارت گرد، سرستون ثابت، ردیف‌های اسکرول‌شونده. */
@Composable
internal fun TableShell(modifier: Modifier, header: @Composable RowScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.clip(shape).background(Pal.card).border(1.dp, Pal.hairline, shape)) {
        Row(
            Modifier.fillMaxWidth().background(Pal.tableHeader).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, content = header
        )
        body()
    }
}

/** یک ردیف جدول؛ ردیف امروز پس‌زمینهٔ ملایم و نوار رنگی کنار ردیف دارد. نوار ۳dp در سرستون هم رزرو می‌شود تا ستون‌ها هم‌تراز بمانند. */
@Composable
internal fun TableRow(isToday: Boolean, rowHeight: Dp, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(rowHeight).background(if (isToday) Pal.today else Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent))
            content()
        }
        HorizontalDivider(thickness = 0.5.dp, color = Pal.hairline)
    }
}

@Composable
internal fun DateCell(date: JalaliDate, holiday: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.day.fa(), style = MaterialTheme.typography.titleMedium, color = if (holiday) Pal.holiday else MaterialTheme.colorScheme.onSurface)
        Text(WEEK_FULL[date.dayOfWeek], style = MaterialTheme.typography.labelSmall, color = if (holiday) Pal.holiday else Pal.muted, maxLines = 1)
    }
}

@Composable
internal fun HeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = Pal.muted, textAlign = TextAlign.Center, maxLines = 1)
}

// ===================== تب چهارشیفت =====================

@Composable
fun FourShiftScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val patterns by vm.patterns.collectAsState()
    val slots by vm.slots.collectAsState()
    val allEntries by vm.entriesAllPatterns.collectAsState()
    var pickSlot by remember { mutableStateOf<Int?>(null) }
    var menuSlot by remember { mutableStateOf<Int?>(null) }
    var editPattern by remember { mutableStateOf<PatternEntity?>(null) }      // ویرایش شیفتِ ستون از همین منو
    val today = remember { JalaliDate.today() }
    val cal by vm.holidayCalendar.collectAsState()
    val listState = rememberLazyListState()

    val columns = remember(patterns, slots, allEntries) {
        slots.map { id ->
            patterns.firstOrNull { it.id == id }?.let { p ->
                FourShiftColumn(p.id, p.name, p.company, p.base(), ShiftPattern.parse(p.pattern), allEntries[p.id].orEmpty())
            }
        }
    }
    val rows = remember(month, columns) { FourShiftTable.build(month.year, month.month, columns) }
    LaunchedEffect(month) {
        val inThisMonth = month.year == today.year && month.month == today.month
        listState.scrollToItem(if (inThisMonth) maxOf(0, today.day - 3) else 0)
    }

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthSwitcher(vm, month)
        TableShell(
            Modifier.weight(1f).fillMaxWidth(),
            header = {
                Spacer(Modifier.width(3.dp))
                HeaderLabel("روز", Modifier.weight(1.1f))
                columns.forEachIndexed { i, c ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 46.dp).clickable(role = Role.Button) { if (c == null) pickSlot = i else menuSlot = i },
                        contentAlignment = Alignment.Center
                    ) {
                        if (c == null) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Ico(R.drawable.ic_add, size = 20.dp, tint = MaterialTheme.colorScheme.primary, desc = "افزودن شیفت") }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                                Text(c.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                if (c.subtitle.isNotBlank()) Text(c.subtitle, style = MaterialTheme.typography.labelSmall, color = Pal.muted, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        ) {
            if (columns.all { it == null }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ic_info, size = 18.dp, tint = Pal.muted)
                    HintText("برای افزودن شیفت، روی «+» بالای هر ستون بزنید.")
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(rows) { r ->
                    TableRow(r.date == today, 56.dp) {
                        DateCell(r.date, cal.kind(r.date) != DayKind.NORMAL, Modifier.weight(1.1f))
                        r.cells.forEach { cell ->
                            Box(Modifier.weight(1f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                if (cell != null) ShiftChip(cell.effectiveCode, Modifier.fillMaxWidth(), marked = cell.overridden, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }

    menuSlot?.let { i ->
        val col = columns.getOrNull(i)
        AppDialog(col?.title ?: "", { menuSlot = null }, confirmText = "تغییر شیفت", onConfirm = { menuSlot = null; pickSlot = i },
            dismissText = "بستن") {
            if (!col?.subtitle.isNullOrBlank()) Text(col?.subtitle.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
            AppButton("ویرایش شیفت", {
                editPattern = patterns.firstOrNull { it.id == col?.patternId }; menuSlot = null
            }, Modifier.fillMaxWidth(), icon = R.drawable.ic_edit, style = BtnStyle.Tonal, enabled = col != null)
            AppButton("حذف از جدول", { vm.clearSlot(i); menuSlot = null }, Modifier.fillMaxWidth(), icon = R.drawable.ic_delete, style = BtnStyle.Danger)
        }
    }
    pickSlot?.let { i -> SlotPickDialog(vm, i) { pickSlot = null } }
    editPattern?.let { p -> PatternDialog(vm, p, onClose = { editPattern = null }) }
}

/** انتخاب شیفت برای یک ستون چهارشیفت (slot) یا برای کاردکس (slot = null) + «افزودن...». */
@Composable
fun SlotPickDialog(vm: MainVm, slot: Int?, onClose: () -> Unit) {
    val patterns by vm.patterns.collectAsState()
    var sel by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    AppDialog(
        "شیفت مورد نظر را انتخاب کنید", onClose, confirmText = "نمایش",
        onConfirm = { sel?.let { if (slot != null) vm.setSlot(slot, it) else vm.select(it); onClose() } },
        confirmEnabled = sel != null, dismissText = "انصراف"
    ) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.tertiaryContainer)
                .clickable(role = Role.Button) { creating = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(R.drawable.ic_add, size = 36.dp, iconSize = 20.dp, container = MaterialTheme.colorScheme.tertiary, tint = MaterialTheme.colorScheme.onTertiary, circle = true)
            Column {
                Text("افزودن...", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("اضافه کردن شیفت کاری جدید به برنامه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
        }
        patterns.forEach { p ->
            val on = sel == p.id
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.RadioButton) { sel = p.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    if (p.company.isNotBlank()) Text(p.company, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
                if (on) Ico(R.drawable.ic_check_circle, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (creating) PatternDialog(vm, null, assignSlot = slot, selectNew = slot == null, onSaved = onClose, onClose = { creating = false })
}

// ===================== تب کاردکس =====================

/** کاردکس: شیفت الگوی انتخاب‌شده در هر روز + نشانه‌های ثبت وقایع (یادداشت، استراحت، مرخصی، تعویض شیفت، اضافه‌کار). */
@Composable
fun CardexScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val p by vm.selected.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<JalaliDate, String>?>(null) }
    val today = remember { JalaliDate.today() }
    val cal by vm.holidayCalendar.collectAsState()
    val listState = rememberLazyListState()
    val rows = remember(month, p, entries) {
        p?.let { CardexTable.build(month.year, month.month, it.base(), ShiftPattern.parse(it.pattern), entries) }.orEmpty()
    }
    LaunchedEffect(month) {
        val inThisMonth = month.year == today.year && month.month == today.month
        listState.scrollToItem(if (inThisMonth) maxOf(0, today.day - 3) else 0)
    }

    Column(Modifier.fillMaxSize().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthSwitcher(vm, month)
        TableShell(
            Modifier.weight(1f).fillMaxWidth(),
            header = {
                Spacer(Modifier.width(3.dp))
                HeaderLabel("روز", Modifier.weight(1.1f))
                CardexTable.COLUMNS.forEach { t ->
                    Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ico(entryIcon(t), size = 20.dp, tint = entryColor(t))
                        Text(EVENT_SHORT[t] ?: "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Pal.muted, maxLines = 1, softWrap = false)
                    }
                }
                Box(Modifier.weight(1.3f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(role = Role.Button) { picking = true }.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p?.let { groupTitle(it.name) } ?: "انتخاب شیفت", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 60.dp))
                        Ico(R.drawable.ic_expand_more, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        ) {
            if (p == null) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ic_info, size = 18.dp, tint = Pal.muted)
                    HintText("ابتدا روی «انتخاب شیفت» بزنید و یک شیفت انتخاب یا اضافه کنید.")
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(rows) { r ->
                    val hol = cal.kind(r.date) != DayKind.NORMAL
                    TableRow(r.date == today, 56.dp) {
                        DateCell(r.date, hol, Modifier.weight(1.1f).fillMaxHeight().clickable { dialog = r.date to EntryType.NOTE }.wrapContentHeight(Alignment.CenterVertically))
                        CardexTable.COLUMNS.forEach { t ->
                            val has = r.has(t)
                            val col = entryColor(t)
                            Box(Modifier.weight(0.8f).fillMaxHeight().clickable { dialog = r.date to t }, contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (has) col.copy(alpha = 0.16f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) { Ico(entryIcon(t), size = 20.dp, tint = if (has) col else Pal.muted.copy(alpha = 0.28f), desc = TYPE_FA[t]) }
                                if (t == EntryType.OVERTIME && r.day.overtimeMinutes > 0) {
                                    Box(
                                        Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).clip(CircleShape).background(Pal.coral).padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${r.day.overtimeMinutes.fa()}د", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp), color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Box(Modifier.weight(1.3f).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            ShiftChip(r.day.effectiveCode, Modifier.fillMaxWidth(), marked = r.day.overridden, compact = true)
                        }
                    }
                }
            }
        }
    }
    if (picking) SlotPickDialog(vm, null) { picking = false }
    dialog?.let { (d, t) -> DayDialog(vm, d, t) { dialog = null } }
}

// ===================== راهنمای رنگ شیفت‌ها =====================

/** راهنمای رنگ شیفت‌ها (در تقویم و الگوهای آماده). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShiftLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShiftType.DEFAULTS.filter { it.code in 1..5 }.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(Color(st.color)))
                Text(st.name, style = MaterialTheme.typography.labelMedium, color = Pal.muted)
            }
        }
    }
}
