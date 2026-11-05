package ir.shiftapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.data.PatternEntity
import kotlinx.coroutines.launch

/** نوار رنگی یک چرخهٔ شیفت (هر روز یک مربع کوچک). */
@Composable
internal fun CycleStrip(codes: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        codes.forEach { c -> Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(shiftColor(c))) }
    }
}

@Composable
fun PatternsScreen(vm: MainVm) {
    val patterns by vm.patterns.collectAsState()
    val sel by vm.selected.collectAsState()
    var edit by remember { mutableStateOf<PatternEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var presetCodes by remember { mutableStateOf<List<Int>?>(null) }
    var presetDay by remember { mutableStateOf<JalaliDate?>(null) }
    var crewPreset by remember { mutableStateOf<PatternPreset?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PatternEntity?>(null) }
    // فقط الگوی در حال اجرا (انتخاب‌شده) نشان داده می‌شود؛ بقیه پنهان‌اند و حذف نمی‌شوند (ستون‌های جدول چهارشیفت به آن‌ها وابسته است)
    // مجموعه‌ها (مثل گروه‌های A تا D): در فهرست یک ردیف با چیپ گروه‌ها، نه چند کارت جدا
    val groups = remember(patterns) { PatternSets.group(patterns) { PatternSets.keyOf(it.name, it.setName) } }
    val activeGroup = groups.firstOrNull { g -> g.any { it.id == sel?.id } }
    val rows: List<Any> = if (!showOthers) listOfNotNull(sel)
        else groups.sortedByDescending { it === activeGroup }.flatMap { g -> if (g.size >= 2 && g !== activeGroup) listOf<Any>(g) else if (g === activeGroup) listOfNotNull(sel) else g }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openPatternFile(uri) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("الگوی جدید", { creating = true }, Modifier.weight(1f), icon = R.drawable.ic_add)
                AppButton("الگوهای آماده", { showPresets = true }, Modifier.weight(1f), icon = R.drawable.ic_patterns, style = BtnStyle.Tonal)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("ورود از فایل", { filePicker.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = R.drawable.ic_download, style = BtnStyle.Outline)
                AppButton("ارسال الگوها", { scope.launch { sharePatternFile(ctx, patterns) } }, Modifier.weight(1f), icon = R.drawable.ic_share, style = BtnStyle.Outline, enabled = patterns.isNotEmpty())
            }
        }
        if (patterns.size > 1) item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ showOthers = !showOthers }) {
                    Text(if (showOthers) "بستن بایگانی" else "بایگانی و برنامه‌های دیگر (${(patterns.size - 1).fa()} مورد)")
                }
                if (showOthers) HintText("برنامه‌های قبلی و گروه‌های دیگر؛ هیچ‌چیز پاک نشده است. روی گروه موردنظر بزنید تا فعال شود.")
            }
        }
        if (patterns.isEmpty()) item {
            EmptyState(R.drawable.ic_patterns, "هنوز الگویی نیست", text = "یک الگوی دلخواه بسازید یا از الگوهای آماده شروع کنید.")
        }
        // چیپ‌های جابه‌جایی بین گروه‌های مجموعهٔ فعال (بالای کارت فعال)
        if (activeGroup != null && activeGroup.size >= 2) item(key = "set-active") {
            SetSwitcher(PatternSets.keyOf(activeGroup[0].name, activeGroup[0].setName), activeGroup, sel?.id) { vm.select(it); showOthers = false }
        }
        items(rows, key = { r -> if (r is PatternEntity) r.id else "set-" + ((r as List<*>).first() as PatternEntity).id }) { row ->
          if (row is List<*>) {
            @Suppress("UNCHECKED_CAST") val g = row as List<PatternEntity>
            SetSwitcher(PatternSets.keyOf(g[0].name, g[0].setName), g, null) { vm.select(it); showOthers = false }
          } else {
            val p = row as PatternEntity
            val active = sel?.id == p.id
            val codes = remember(p.pattern) { runCatching { ShiftPattern.parse(p.pattern).codes }.getOrDefault(emptyList()) }
            AppCard(Modifier.fillMaxWidth(), onClick = { vm.select(p.id); showOthers = false }) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groupLetter(p.name)?.let { gl ->
                            Box(Modifier.size(36.dp).clip(CircleShape).background(groupColor(gl)), contentAlignment = Alignment.Center) {
                                Text(gl.toString(), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.company.isNotBlank()) Text(p.company, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                        }
                        if (active) Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Ico(R.drawable.ic_check, size = 16.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("فعال", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else Text("انتخاب", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    }
                    val extra = listOf("مسئول شیفت" to p.supervisor, "سرشیفت کل" to p.headSupervisor, "بخش" to p.section).filter { it.second.isNotBlank() }
                    if (extra.isNotEmpty()) Text(extra.joinToString("  |  ") { "${it.first}: ${it.second}" }, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    CycleStrip(codes)
                    Text(
                        "چرخه ${codes.size.fa()} روزه  ·  تاریخ مبنا ${p.baseYear.fa()}/${p.baseMonth.fa()}/${p.baseDay.fa()}",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SmallIconButton(R.drawable.ic_edit, "ویرایش", { edit = p })
                        SmallIconButton(R.drawable.ic_delete, "حذف", { deleting = p }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
          }
        }
    }
    if (showPresets) PresetsDialog(
        onPick = { presetCodes = it.codes; presetDay = it.defaultStart(); showPresets = false; creating = true },
        onCrews = { crewPreset = it; showPresets = false },
        onClose = { showPresets = false })
    crewPreset?.let { CrewsDialog(vm, it) { crewPreset = null } }
    if (creating || edit != null) PatternDialog(vm, edit, presetCodes = presetCodes, startDay = presetDay) { creating = false; edit = null; presetCodes = null; presetDay = null }
    deleting?.let { p ->
        ConfirmDialog("«${p.name}» حذف شود؟ ثبت‌های روزانهٔ این الگو هم از بین می‌رود.", "حذف", { vm.deletePattern(p.id); deleting = null }, { deleting = null }, danger = true)
    }
}

@Composable
fun PresetsDialog(onPick: (PatternPreset) -> Unit, onCrews: (PatternPreset) -> Unit, onClose: () -> Unit) {
    AppDialog("الگوهای آماده", onClose, confirmText = "بستن", onConfirm = onClose) { PresetCards(onPick, onCrews) }
}

/** فهرست الگوهای آماده؛ هم در دیالوگ و هم در صفحهٔ خوش‌آمد استفاده می‌شود. */
@Composable
fun PresetCards(onPick: (PatternPreset) -> Unit, onCrews: (PatternPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HintText("روی الگو بزنید تا فقط همان یک الگو اضافه شود، یا «ساخت گروه‌ها» را بزنید تا همهٔ گروه‌های لازم برای پوشش کامل یک‌جا ساخته شوند.")
        PatternPresets.ALL.forEach { pr -> PresetCard(pr, onPick, onCrews) }
        SectionLabel("الگوهای ساده (مثل نسخهٔ قبلی)")
        PatternPresets.CLASSIC.forEach { pr ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onPick(pr) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pr.title, style = MaterialTheme.typography.titleSmall)
                    CycleStrip(pr.codes)
                    Text("${pr.codes.size.fa()} روز", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
            }
        }
        ShiftLegend()
    }
}

/** کارت یک الگوی آماده (عنوان، توضیح، نوار چرخه و در الگوهای چندگروهه دکمهٔ ساخت همهٔ گروه‌ها). */
@Composable
fun PresetCard(pr: PatternPreset, onPick: (PatternPreset) -> Unit, onCrews: (PatternPreset) -> Unit) {
        AppCard(Modifier.fillMaxWidth(), onClick = { onPick(pr) }) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pr.title, style = MaterialTheme.typography.titleSmall)
                Text("${pr.description} (${pr.codes.size.fa()} روز)", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                CycleStrip(pr.codes)
                if (pr.crews > 1) {
                    Text(
                        "برای پوشش کامل: ${pr.crews.fa()} گروه. ترتیب شروع: ${pr.startSequence().joinToString(" ← ")}؛ هر کدام ${pr.offsetDays.fa()} روز بعد از قبلی",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                    Text("شروع پیش‌فرض گروه A: ${Fa.digits(DateText.format(pr.defaultStart()))} (قابل تغییر)", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    AppButton("ساخت ${pr.crews.fa()} گروه (${'A'}–${'A' + pr.crews - 1})", { onCrews(pr) }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
                }
            }
        }
}

/**
 * انتخاب مرحله‌به‌مرحلهٔ الگو (مورد ۱۱): الگو (قبلاً انتخاب شده) ← «گروه من» ← تاریخ شروع دورهٔ گروه من (+ اطلاعات اختیاری).
 * همهٔ گروه‌های الگو ساخته می‌شوند (برای جدول چهارشیفت) ولی گروه من فعال می‌شود.
 */
@Composable
fun CrewsDialog(vm: MainVm, preset: PatternPreset, onClose: () -> Unit) {
    val t = remember { JalaliDate.today() }
    var step by remember { mutableStateOf(0) }
    var mine by remember { mutableStateOf(0) }
    var company by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(DateText.format(preset.startOf(0, preset.defaultStart(t)))) }
    var head by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun pickMine(i: Int) { mine = i; dateText = DateText.format(preset.startOf(i, preset.defaultStart(t))) }
    AppDialog(
        if (step == 0) "گام ۱ از ۲: گروه من" else "گام ۲ از ۲: تاریخ شروع", onClose,
        confirmText = if (step == 0) "بعدی" else "ساخت و فعال‌سازی", dismissText = if (step == 0) "انصراف" else "قبلی",
        onDismissClick = { if (step == 0) onClose() else step = 0 },
        onConfirm = {
            if (step == 0) step = 1 else {
                val d = DateText.parse(dateText)
                err = if (d == null) "تاریخ شروع دوره نامعتبر است (مثال: ۱۴۰۵/۰۷/۱۳)"
                else vm.createCrews(preset, company, preset.aStartFromMine(mine, d), head, section, mine)
                if (err == null) onClose()
            }
        }
    ) {
        Text(preset.title, style = MaterialTheme.typography.titleSmall)
        if (step == 0) {
            HintText("شما عضو کدام گروه هستید؟ همهٔ گروه‌های ${'A'} تا ${'A' + preset.crews - 1} برای جدول چهارشیفت ساخته می‌شوند، ولی صفحه‌ها با گروه شما شروع می‌شوند. بعداً هم می‌شود گروه را عوض کرد.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                (0 until preset.crews).forEach { i ->
                    val on = i == mine
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(groupColor('A' + i))
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton, onClickLabel = "گروه ${'A' + i}") { pickMine(i) },
                        contentAlignment = Alignment.Center
                    ) { Text(('A' + i).toString(), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White) }
                }
            }
            HintText("ترتیب شروع گروه‌ها: ${preset.startSequence().joinToString(" ← ")}؛ هر کدام ${preset.offsetDays.fa()} روز بعد از قبلی.")
        } else {
            HintText("تاریخی را بنویسید که دورهٔ گروه ${'A' + mine} از آن شروع می‌شود (روز اول چرخه). پیشنهاد برنامه بر اساس امروز پر شده است؛ تاریخ بقیهٔ گروه‌ها خودکار حساب می‌شود.")
            AppField(dateText, { dateText = DateText.typing(it) }, "تاریخ شروع دورهٔ گروه ${'A' + mine}", placeholder = "مثلا : ۱۴۰۵/۰۷/۱۳", keyboard = KeyboardType.Number, ltr = true, isError = err != null, supporting = err)
            AppField(company, { company = it }, "عنوان شرکت (اختیاری)")
            AppField(head, { head = it }, "سرشیفت کل (اختیاری)")
            AppField(section, { section = it }, "بخش / واحد (اختیاری)")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternDialog(vm: MainVm, p: PatternEntity?, assignSlot: Int? = null, selectNew: Boolean = false, onSaved: () -> Unit = {}, presetCodes: List<Int>? = null, startDay: JalaliDate? = null, onClose: () -> Unit) {
    val t = remember { JalaliDate.today() }
    var company by remember { mutableStateOf(p?.company ?: "") }
    var name by remember { mutableStateOf(p?.name ?: "") }
    var supervisor by remember { mutableStateOf(p?.supervisor ?: "") }
    var head by remember { mutableStateOf(p?.headSupervisor ?: "") }
    var section by remember { mutableStateOf(p?.section ?: "") }
    var dateText by remember { mutableStateOf(DateText.format(p?.base() ?: startDay ?: t)) }
    val codes = remember { mutableStateListOf<Int>().apply { p?.let { addAll(ShiftPattern.parse(it.pattern).codes) }; presetCodes?.let { addAll(it) } } }
    var err by remember { mutableStateOf<String?>(null) }
    AppDialog(
        if (p == null) "شیفت جدید" else "ویرایش شیفت", onClose, confirmText = "ذخیره", dismissText = "انصراف",
        onConfirm = {
            val d = DateText.parse(dateText)
            err = if (d == null) "تاریخ شروع دوره نامعتبر است (مثال: ۱۴۰۵/۰۷/۱۳)"
            else vm.savePattern(p?.id ?: 0L, name, company, d.year, d.month, d.day, codes.toList(), assignSlot, selectNew, supervisor, head, section)
            if (err == null) { onSaved(); onClose() }
        }
    ) {
        AppField(company, { company = it }, "عنوان شرکت", placeholder = "مثلا : ذوب آهن")
        AppField(name, { name = it }, "نام شیفت", placeholder = "مثلا : A")
        AppField(supervisor, { supervisor = it }, "مسئول شیفت (اختیاری)")
        AppField(head, { head = it }, "سرشیفت کل (اختیاری)")
        AppField(section, { section = it }, "بخش / واحد (اختیاری)")

        HorizontalDivider(color = Pal.hairline)
        Text("چرخهٔ شیفت", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HintText("یک دوره از شیفت را با لمس نوع شیفت‌ها پشت‌سرهم بسازید.")
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftType.DEFAULTS.filter { it.code in 1..5 }.forEach { st ->
                ShiftChip(st.code, Modifier.clickable(role = Role.Button) { codes.add(st.code) }, label = "+ ${st.name}")
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (codes.isEmpty()) Text("(دوره خالی است)", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                codes.forEachIndexed { i, c -> ShiftChip(c, compact = true, label = "${(i + 1).fa()}. ${shiftName(c)}", modifier = Modifier.padding(horizontal = 4.dp)) }
            }
            if (codes.isNotEmpty()) Text("طول دوره: ${codes.size.fa()} روز", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("حذف آخری", { if (codes.isNotEmpty()) codes.removeAt(codes.lastIndex) }, Modifier.weight(1f), style = BtnStyle.Outline)
            AppButton("پاک کردن", { codes.clear() }, Modifier.weight(1f), style = BtnStyle.Outline)
        }
        AppField(dateText, { dateText = DateText.typing(it) }, "تاریخ شروع دوره", placeholder = "مثلا : ۱۴۰۵/۰۷/۱۳", keyboard = KeyboardType.Number, ltr = true, isError = err != null, supporting = err)
    }
}

@Composable
fun EmployeesScreen(vm: MainVm) {
    val list by vm.employees.collectAsState()
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(name, { name = it }, "نام همکار", Modifier.weight(1f))
            AppButton("افزودن", { vm.addEmployee(name); name = "" }, Modifier.padding(top = 4.dp), icon = R.drawable.ic_add, enabled = name.isNotBlank())
        }
        if (list.isEmpty()) EmptyState(R.drawable.ic_people, "همکاری ثبت نشده", text = "همکارانی که با آن‌ها شیفت عوض می‌کنید را اینجا اضافه کنید تا هنگام «تعویض شیفت» انتخابشان کنید.")
        else AppCard(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.heightIn(max = 600.dp)) {
                items(list, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        SmallIconButton(R.drawable.ic_delete, "حذف", { vm.deleteEmployee(e.id) }, tint = MaterialTheme.colorScheme.error)
                    }
                    RowDivider()
                }
            }
        }
    }
}


/** ردیف یک مجموعهٔ الگو: عنوان مجموعه و یک چیپ رنگی برای هر گروه (A، B، ...)؛ لمس هر چیپ همان گروه را فعال می‌کند. */
@Composable
private fun SetSwitcher(title: String, members: List<PatternEntity>, activeId: Long?, onPick: (Long) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                members.forEach { m ->
                    val letter = PatternSets.letterOf(m.name)
                    val on = m.id == activeId
                    val color = letter?.let { groupColor(it) } ?: Pal.muted
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = if (on) 1f else 0.85f))
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable(role = Role.Button, onClickLabel = "فعال‌سازی ${m.name}") { onPick(m.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text((letter ?: m.name.firstOrNull() ?: '?').toString(), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}
