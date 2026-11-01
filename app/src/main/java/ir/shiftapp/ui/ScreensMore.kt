package ir.shiftapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*

private data class MoreItem(val key: String, val title: String, val subtitle: String, val icon: Int)

private val MORE_REPORT = listOf(
    MoreItem("report", "گزارش و خروجی", "گزارش ماه، PDF، Excel، تصویر و پشتیبان", R.drawable.ic_report)
)
private val MORE_PLAN = listOf(
    MoreItem("patterns", "الگوهای شیفت", "ساخت، ویرایش و ارسال الگوها", R.drawable.ic_patterns),
    MoreItem("common", "یافتن روز مشترک", "برای تعویض شیفت با همکار", R.drawable.ic_swap),
    MoreItem("leave", "مانده مرخصی سالانه", "مصرف‌شده و باقی‌مانده", R.drawable.ic_sun),
    MoreItem("holidays", "تعطیلات رسمی", "تعطیلات ثابت و افزودن تعطیلی", R.drawable.ic_event),
    MoreItem("employees", "همکاران", "فهرست همکاران برای تعویض شیفت", R.drawable.ic_people)
)
private val MORE_APP = listOf(
    MoreItem("settings", "تنظیمات", "ساعت شیفت، یادآور، تم، پشتیبان", R.drawable.ic_settings),
    MoreItem("about", "درباره و اشتراک‌گذاری", "معرفی برنامه و ارسال برای دوستان", R.drawable.ic_info)
)

/** عنوان زیرصفحه‌های «بیشتر» برای نوار بالا. */
internal fun moreTitle(key: String): String =
    (MORE_REPORT + MORE_PLAN + MORE_APP).firstOrNull { it.key == key }?.title ?: "بیشتر"

@Composable
private fun MoreGroup(title: String, items: List<MoreItem>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        AppCard(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, it ->
                SettingRow(it.title, subtitle = it.subtitle, icon = it.icon, onClick = { onOpen(it.key) }) {
                    Ico(R.drawable.ic_chevron_right, size = 22.dp, tint = Pal.muted)
                }
                if (i < items.lastIndex) RowDivider()
            }
        }
    }
}

/** محتوای کشوی «جدول شیفت»: گزارش، برنامه‌ریزی، تنظیمات و درباره؛ جایگزین تب «بیشتر». */
@Composable
internal fun MoreDrawerSheet(selected: String?, onOpen: (String) -> Unit) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                "منو و تنظیمات", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            DrawerGroup("گزارش", MORE_REPORT, selected, onOpen)
            DrawerGroup("برنامه‌ریزی", MORE_PLAN, selected, onOpen)
            DrawerGroup("برنامه", MORE_APP, selected, onOpen)
        }
    }
}

@Composable
private fun DrawerGroup(title: String, items: List<MoreItem>, selected: String?, onOpen: (String) -> Unit) {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = Pal.muted,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
    )
    items.forEach { m ->
        NavigationDrawerItem(
            label = {
                Column {
                    Text(m.title, style = MaterialTheme.typography.titleSmall)
                    Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            icon = { Ico(m.icon, size = 24.dp) },
            selected = selected == m.key,
            onClick = { onOpen(m.key) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
fun MoreScreen(vm: MainVm, sub: String?, setSub: (String?) -> Unit) {
    if (sub == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            MoreGroup("گزارش", MORE_REPORT, setSub)
            MoreGroup("برنامه‌ریزی", MORE_PLAN, setSub)
            MoreGroup("برنامه", MORE_APP, setSub)
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (sub) {
            "report" -> ReportScreen(vm)
            "patterns" -> PatternsScreen(vm)
            "common" -> CommonDaysScreen(vm)
            "leave" -> LeaveScreen(vm)
            "holidays" -> HolidaysScreen(vm)
            "employees" -> EmployeesScreen(vm)
            "settings" -> SettingsScreen(vm)
            "about" -> AboutScreen()
        }
    }
}

// ===================== گزارش + ساعت کار + خروجی =====================
@Composable
private fun ExportRow(icon: Int, title: String, onSave: () -> Unit, onShare: (() -> Unit)?) {
    SettingRow(title, icon = icon, onClick = onSave) {
        Row {
            SmallIconButton(R.drawable.ic_download, "ذخیره", onSave, tint = MaterialTheme.colorScheme.primary)
            if (onShare != null) SmallIconButton(R.drawable.ic_share, "ارسال", onShare, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ReportScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val sel by vm.selected.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    val rep = remember(month, sel, entries) { vm.report() }
    val hours = remember(month, sel, entries, prefs, cal) { vm.hours() }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(vm.mime(ExportKind.CSV))) { it?.let { u -> vm.export(ExportKind.CSV, u) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(vm.mime(ExportKind.PDF))) { it?.let { u -> vm.export(ExportKind.PDF, u) } }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(vm.mime(ExportKind.XLSX))) { it?.let { u -> vm.export(ExportKind.XLSX, u) } }
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(vm.mime(ExportKind.PNG_FOUR))) { it?.let { u -> vm.export(ExportKind.PNG_FOUR, u) } }
    val bak = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val res = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    val importShiftma = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openPatternFile) }
    var confirmRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonthSwitcher(vm, month)
        if (rep == null) EmptyState(R.drawable.ic_report, "الگویی انتخاب نشده", text = "برای دیدن گزارش، یک الگوی شیفت انتخاب کنید.")
        else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("روز کاری", rep.workDays.fa(), R.drawable.ic_event, Modifier.weight(1f).fillMaxHeight())
                StatTile("مرخصی (روز)", rep.leaveDays.fa(), R.drawable.ic_sun, Modifier.weight(1f).fillMaxHeight(), tint = Pal.green)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("اضافه‌کاری (ساعت:دقیقه)", Fa.hm(rep.overtimeMinutes), R.drawable.ic_timer, Modifier.weight(1f).fillMaxHeight(), tint = Pal.coral)
                StatTile("تعویض / تغییر دستی", "${rep.exchangeCount.fa()} / ${rep.manualChangeCount.fa()}", R.drawable.ic_swap, Modifier.weight(1f).fillMaxHeight(), tint = Pal.amber)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("روزها به تفکیک شیفت")
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rep.daysByShift.forEach { (c, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShiftChip(c, Modifier.width(110.dp), compact = true)
                                Spacer(Modifier.weight(1f))
                                Text("${n.fa()} روز", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (rep.restMarks > 0) HintText("علامت استراحت ثبت‌شده: ${rep.restMarks.fa()}")
                    }
                }
            }
        }
        if (hours != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ساعت کار ماه")
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        hours.minutesByShift.forEach { (c, m) ->
                            val t = prefs.shiftTimes[c]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(shiftName(c), style = MaterialTheme.typography.titleSmall)
                                    val span = t?.let { "${Fa.digits(ShiftTime.fmt(it.startMin))} تا ${Fa.digits(ShiftTime.fmt(it.endMin))}" }
                                    Text(listOfNotNull("${(hours.daysByShift[c] ?: 0).fa()} روز", span).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                                }
                                Text(Fa.hm(m) + " ساعت", style = MaterialTheme.typography.titleSmall)
                            }
                            RowDividerInset()
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("جمع ساعت کار", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(Fa.hm(hours.totalMinutes) + " ساعت", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("کار در تعطیل رسمی: ${hours.officialHolidayDays.fa()} روز، ${Fa.hm(hours.officialHolidayMinutes)} ساعت", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                        Text("کار در جمعه: ${hours.fridayDays.fa()} روز، ${Fa.hm(hours.fridayMinutes)} ساعت", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                        if (hours.shiftsWithoutTime.isNotEmpty())
                            Text("ساعت این شیفت‌ها در تنظیمات مشخص نشده (صفر حساب شد): " + hours.shiftsWithoutTime.joinToString("، ") { shiftName(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        HintText("ساعت‌ها طبق تنظیمات «ساعت شیفت‌ها» حساب می‌شوند؛ شبکارِ عبوردهنده از نیمه‌شب کامل به روز شروعش نسبت داده می‌شود.")
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("خروجی ماه جاری")
            AppCard(Modifier.fillMaxWidth()) {
                ExportRow(R.drawable.ic_note, "کاردکس PDF", { pdf.launch(vm.suggestedName(ExportKind.PDF)) }, { vm.share(ExportKind.PDF) })
                RowDivider()
                ExportRow(R.drawable.ic_table, "کاردکس Excel", { xlsx.launch(vm.suggestedName(ExportKind.XLSX)) }, { vm.share(ExportKind.XLSX) })
                RowDivider()
                ExportRow(R.drawable.ic_note, "کاردکس CSV", { csv.launch(vm.suggestedName(ExportKind.CSV)) }, null)
                RowDivider()
                ExportRow(R.drawable.ic_image, "جدول چهارشیفت (تصویر)", { png.launch(vm.suggestedName(ExportKind.PNG_FOUR)) }, { vm.share(ExportKind.PNG_FOUR) })
            }
            HintText("آیکن پایین‌آمدن: ذخیره در گوشی  ·  آیکن اشتراک: ارسال به گروه یا پیام‌رسان", Modifier.padding(horizontal = 6.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("پشتیبان‌گیری")
            AppCard(Modifier.fillMaxWidth()) {
                SettingRow("پشتیبان‌گیری (Backup)", subtitle = "ذخیرهٔ همهٔ اطلاعات در یک فایل", icon = R.drawable.ic_backup, onClick = { bak.launch("myshift-backup.json") })
                RowDivider()
                SettingRow("بازیابی (Restore)", subtitle = "جایگزینی اطلاعات با فایل پشتیبان", icon = R.drawable.ic_restore, onClick = { confirmRestore = true })
                RowDivider()
                SettingRow("وارد کردن فایل شیفت‌ما (.shiftma)", subtitle = "الگو، مخاطبین، برنامه غذایی یا کاشی‌ها؛ قبل از افزودن تأیید می‌گیرد", icon = R.drawable.ic_upload, onClick = { importShiftma.launch(arrayOf("*/*")) })
            }
        }
    }
    if (confirmRestore) ConfirmDialog(
        "تمام اطلاعات فعلی با محتوای فایل جایگزین می‌شود. ادامه می‌دهید؟", "ادامه",
        { confirmRestore = false; res.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
        { confirmRestore = false }, title = "بازیابی پشتیبان", danger = true
    )
}

@Composable
private fun RowDividerInset() { HorizontalDivider(color = Pal.hairline) }

// ===================== یافتن روز مشترک =====================
@Composable
private fun PatternPicker(label: String, patterns: List<ir.shiftapp.data.PatternEntity>, selectedId: Long, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(patterns.firstOrNull { it.id == selectedId }?.name ?: "انتخاب", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Ico(R.drawable.ic_expand_more, size = 20.dp, tint = Pal.muted)
            }
        }
        DropdownMenu(open, { open = false }) {
            patterns.forEach { p -> DropdownMenuItem(text = { Text(p.name + if (p.company.isNotBlank()) " (${p.company})" else "") }, onClick = { onPick(p.id); open = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommonDaysScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val patterns by vm.patterns.collectAsState()
    val all by vm.entriesAllPatterns.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    var aId by remember { mutableLongStateOf(0L) }
    var bId by remember { mutableLongStateOf(0L) }
    val a = patterns.firstOrNull { it.id == aId } ?: patterns.getOrNull(0)
    val b = patterns.firstOrNull { it.id == bId } ?: patterns.getOrNull(1)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MonthSwitcher(vm, month)
        if (patterns.size < 2) {
            EmptyState(R.drawable.ic_swap, "برای مقایسه دو الگو لازم است", text = "شیفتِ همکار (گروه شیفت او) را در «الگوهای شیفت» تعریف کنید.")
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PatternPicker("شیفت اول", patterns, a?.id ?: 0L, { aId = it }, Modifier.weight(1f))
            PatternPicker("شیفت دوم", patterns, b?.id ?: 0L, { bId = it }, Modifier.weight(1f))
        }
        if (a == null || b == null) return@Column
        if (a.id == b.id) {
            HintText("دو شیفت متفاوت انتخاب کنید.", color = MaterialTheme.colorScheme.error)
            return@Column
        }
        fun col(p: ir.shiftapp.data.PatternEntity) = FourShiftColumn(p.id, p.name, p.company, p.base(), ShiftPattern.parse(p.pattern), all[p.id].orEmpty())
        val res = remember(month, a, b, all) { CommonDaysFinder.find(month.year, month.month, col(a), col(b)) }

        @Composable
        fun block(title: String, days: List<JalaliDate>, icon: Int) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(icon, size = 34.dp, iconSize = 18.dp, circle = true)
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${days.size.fa()} روز", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    if (days.isEmpty()) Text("—", color = Pal.muted)
                    else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { d ->
                            val hol = cal.kind(d) != DayKind.NORMAL
                            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                                Text(d.day.fa(), style = MaterialTheme.typography.labelLarge, color = if (hol) Pal.holiday else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        block("هر دو سر کارند", res.bothWork, R.drawable.ic_people)
        block("با شیفت یکسان", res.bothSameShift, R.drawable.ic_check_circle)
        block("هر دو استراحت/مرخصی‌اند", res.bothOff, R.drawable.ic_sun)
        block("فقط «${a.name}» کار می‌کند (${b.name} آزاد است)", res.onlyA, R.drawable.ic_swap)
        block("فقط «${b.name}» کار می‌کند (${a.name} آزاد است)", res.onlyB, R.drawable.ic_swap)
        HintText("عدد قرمز = تعطیل رسمی یا جمعه. تعویض شیفت معمولاً روزی ممکن است که یکی کار می‌کند و دیگری آزاد است.")
    }
}

// ===================== مانده مرخصی =====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaveScreen(vm: MainVm) {
    val year by vm.leaveYear.collectAsState()
    val sel by vm.selected.collectAsState()
    val rawLeave by vm.leaveEntries.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val bal = remember(year, sel, rawLeave, prefs) { vm.leaveBalance() }
    var capText by remember(prefs.leaveCapDays) { mutableStateOf(prefs.leaveCapDays.toString()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BarIconButton(R.drawable.ic_chevron_left, "سال قبل", { vm.leaveYear.value = year - 1 })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("سال ${year.fa()}", style = MaterialTheme.typography.titleMedium)
                Text("الگوی ${sel?.name ?: "؟"}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
            BarIconButton(R.drawable.ic_chevron_right, "سال بعد", { vm.leaveYear.value = year + 1 })
        }
        if (bal == null) {
            EmptyState(R.drawable.ic_sun, "الگویی انتخاب نشده", text = "ابتدا یک الگوی شیفت انتخاب کنید.")
            return@Column
        }
        val frac = if (bal.capDays <= 0) 0f else (bal.usedDays.toFloat() / bal.capDays).coerceIn(0f, 1f)
        val tone = if (bal.exceeded) MaterialTheme.colorScheme.error else Pal.green
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (bal.exceeded) "بیش از سقف" else "باقی‌مانده", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text((if (bal.exceeded) -bal.remainingDays else bal.remainingDays).fa(), style = MaterialTheme.typography.displaySmall, color = tone)
                    Text("روز", style = MaterialTheme.typography.titleMedium, color = Pal.muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                LinearProgressIndicator(
                    progress = { frac }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = if (bal.exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                Row {
                    Text("مصرف‌شده: ${bal.usedDays.fa()} روز", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("سقف: ${bal.capDays.fa()} روز", style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
                }
            }
        }
        AppField(capText, { v ->
            capText = v.filter(Char::isDigit).take(3)
            capText.toIntOrNull()?.takeIf { it in 0..366 }?.let { vm.setPref(PrefKeys.LEAVE_CAP, it.toString()) }
        }, "سقف مرخصی سالانه (روز)", keyboard = KeyboardType.Number)
        AppCard(Modifier.fillMaxWidth()) {
            SwitchRow("مرخصیِ روز استراحت هم کم شود", prefs.leaveCountsRestDays, { vm.setPref(PrefKeys.LEAVE_REST, it.toString()) },
                subtitle = "مرخصیِ ثبت‌شده روی روز استراحت هم از سقف کم می‌شود")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("روزهای مرخصی")
            AppCard(Modifier.fillMaxWidth()) {
                if (bal.dates.isEmpty()) Text("—", Modifier.padding(16.dp), color = Pal.muted)
                else FlowRow(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bal.dates.forEach {
                        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("${WEEK_FULL[it.dayOfWeek]} ${it.day.fa()} ${MONTHS[it.month - 1]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            HintText("مرخصی را از «تقویم» یا «کاردکس» روی همان روز ثبت کنید. هر روز فقط یک‌بار شمرده می‌شود.", Modifier.padding(horizontal = 6.dp))
        }
    }
}

// ===================== تعطیلات رسمی =====================
@Composable
fun HolidaysScreen(vm: MainVm) {
    val list by vm.holidays.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var date by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            SwitchRow("جمعه‌ها تعطیل هفتگی حساب شوند", prefs.fridayOff, { vm.setPref(PrefKeys.FRIDAY_OFF, it.toString()) }, icon = R.drawable.ic_event)
        }
        SettingsGroup("افزودن تعطیلی", icon = R.drawable.ic_add) {
            HintText("تعطیلات قمری (تاسوعا، عاشورا، اعیاد و ...) هر سال جابه‌جا می‌شوند؛ طبق تقویم رسمی همان سال اینجا وارد کنید.")
            AppField(date, { date = it }, "تاریخ (مثال: ۱۴۰۵/۰۷/۲۰)", keyboard = KeyboardType.Number, ltr = true)
            AppField(title, { title = it }, "عنوان")
            err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            AppButton("افزودن", { err = vm.addHoliday(date, title); if (err == null) { date = ""; title = "" } }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("تعطیلات واردشده")
            AppCard(Modifier.fillMaxWidth()) {
                if (list.isEmpty()) Text("—", Modifier.padding(16.dp), color = Pal.muted)
                list.forEachIndexed { i, h ->
                    val d = JalaliDate.fromKey(h.dateKey)
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.titleSmall)
                            Text("${d.year.fa()}/${d.month.fa()}/${d.day.fa()}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                        }
                        SmallIconButton(R.drawable.ic_delete, "حذف", { vm.deleteHoliday(h.dateKey) }, tint = MaterialTheme.colorScheme.error)
                    }
                    if (i < list.lastIndex) RowDivider()
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("تعطیلات ثابت (هر سال، داخل برنامه)")
            AppCard(Modifier.fillMaxWidth()) {
                val fixed = FixedHolidays.LIST.entries.sortedBy { it.key.first * 100 + it.key.second }
                fixed.forEachIndexed { i, (k, t) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${k.second.fa()} ${MONTHS[k.first - 1]}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    }
                    if (i < fixed.lastIndex) RowDivider()
                }
            }
        }
    }
}
