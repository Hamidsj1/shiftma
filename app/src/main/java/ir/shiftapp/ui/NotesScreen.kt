package ir.shiftapp.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.phonebook.normalizeFa
import java.time.Instant
import java.time.ZoneId

/*
 * بخش یادداشت‌ها: متن، چک‌لیست و یادآور (با تکرار). ذخیره در تنظیمات (کلید pref_notes) → همراه بکاپ؛
 * یادآورها با همان موتور آلارم‌های تکی (ReminderScheduler) زمان‌بندی می‌شوند.
 */

internal fun MainVm.setNotes(list: List<Note>) = setPref(PrefKeys.NOTES, Note.encode(list))

private val NOTE_COLORS = listOf(0L, 0xFFFFD166, 0xFF8C9BFF, 0xFF4ECDC4, 0xFFFF9F68)

@Composable
private fun noteColor(i: Int): Color = if (i in 1..4) Color(NOTE_COLORS[i]) else MaterialTheme.colorScheme.outlineVariant

private fun repeatLabel(r: String) = when (r) {
    Note.REPEAT_DAILY -> "روزانه"
    Note.REPEAT_WEEKLY -> "هفتگی"
    Note.REPEAT_MONTHLY -> "ماهانه"
    else -> ""
}

/** «امروز ۱۸:۰۰»، «فردا ۰۶:۳۰»، «چهارشنبه ۲۳ مهر ۱۶:۰۰». */
internal fun noteWhen(ms: Long, nowMs: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val d = JalaliCalendar.fromLocalDate(t.toLocalDate())
    val today = JalaliCalendar.fromLocalDate(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate())
    val diff = d.toJdn() - today.toJdn()
    val day = when (diff) { 0 -> "امروز"; 1 -> "فردا"; else -> "${WEEK_FULL[d.dayOfWeek]} ${d.day.fa()} ${MONTHS[d.month - 1]}" }
    return "$day ${Fa.digits(ShiftTime.fmt(t.hour * 60 + t.minute))}"
}

@Composable
private fun Tag(text: String, error: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clip(RoundedCornerShape10).background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private val RoundedCornerShape10 = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

@Composable
private fun NoteCard(n: Note, nowMs: Long, zone: ZoneId, onClick: () -> Unit, onToggleItem: (Int) -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(Pal.card).border(1.dp, Pal.hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(noteColor(n.color)))
        Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(n.displayTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.pinned) Text("📌", style = MaterialTheme.typography.bodySmall)
            }
            if (n.body.isNotBlank() && n.body.trim() != n.displayTitle) {
                Text(n.body.trim(), style = MaterialTheme.typography.bodyMedium, color = Pal.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            n.items.take(4).forEachIndexed { i, it ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.Checkbox) { onToggleItem(i) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (it.done) Ico(R.drawable.ic_check_circle, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    else Box(Modifier.size(20.dp).border(1.5.dp, Pal.muted, CircleShape))
                    Text(
                        it.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (it.done) TextDecoration.LineThrough else null,
                        color = if (it.done) Pal.muted else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (n.items.size > 4) HintText("و ${(n.items.size - 4).fa()} مورد دیگر")
            val at = n.reminderAt
            if (at != null || n.items.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (at != null) Tag((if (at <= nowMs) "گذشته · " else "⏰ ") + noteWhen(at, nowMs, zone), error = at <= nowMs)
                if (at != null && n.repeat != Note.REPEAT_NONE) Tag("🔁 " + repeatLabel(n.repeat))
                if (n.items.isNotEmpty()) Tag("${n.doneCount.fa()} از ${n.items.size.fa()}")
            }
        }
    }
}

@Composable
fun NotesSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Note?>(null) }     // id = 0 یعنی یادداشت جدید
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    BackHandler { if (query.isNotEmpty()) query = "" else onHome() }

    val all = prefs.notes
    val q = normalizeFa(query)
    val list = all.filter { n ->
        val okFilter = when (filter) { 1 -> n.reminderAt != null; 2 -> n.type == Note.TYPE_CHECK; 3 -> n.pinned; else -> true }
        okFilter && (q.isEmpty() || normalizeFa(n.title + " " + n.body + " " + n.items.joinToString(" ") { it.text }).contains(q))
    }.let { l ->
        if (filter == 1) l.sortedBy { it.reminderAt }
        else l.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }
    val active = all.count { (it.reminderAt ?: 0L) > now }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTopBar(
                    title = "یادداشت‌ها", leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome,
                    subtitle = if (all.isEmpty()) "متن، چک‌لیست و یادآور" else "${all.size.fa()} یادداشت" + if (active > 0) " · ${active.fa()} یادآور فعال" else ""
                )
                SearchField(query, onChange = { query = it }, modifier = Modifier.padding(horizontal = 16.dp))
                Segmented(listOf("همه", "یادآورها", "چک‌لیست", "سنجاق‌شده"), filter, Modifier.padding(horizontal = 16.dp)) { filter = it }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("یادداشت جدید", style = MaterialTheme.typography.labelLarge) },
                icon = { Ico(R.drawable.ic_add, size = 22.dp) },
                onClick = { editing = Note(id = 0) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyState(
                    R.drawable.ic_home_notes,
                    if (all.isEmpty()) "هنوز یادداشتی نیست" else "یادداشتی پیدا نشد",
                    Modifier.align(Alignment.Center),
                    text = if (all.isEmpty()) "با دکمهٔ «یادداشت جدید» متن، چک‌لیست یا یادآور بسازید." else "فیلتر یا عبارت جست‌وجو را عوض کنید."
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { n ->
                        NoteCard(n, now, zone, onClick = { editing = n }, onToggleItem = { i ->
                            vm.setNotes(all.map { x ->
                                if (x.id != n.id) x else x.copy(items = x.items.mapIndexed { j, c -> if (j == i) c.copy(done = !c.done) else c }, updatedAt = System.currentTimeMillis())
                            })
                        })
                    }
                }
            }
        }
    }
    editing?.let { n -> NoteEditorDialog(vm, prefs, n) { editing = null } }
}

/** «یادآورهای نزدیک» برای صفحهٔ اول: حداکثر دو یادداشتِ دارای یادآور آینده. */
@Composable
internal fun UpcomingNotes(prefs: Prefs, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val next = prefs.notes.filter { (it.reminderAt ?: 0L) > now }.sortedBy { it.reminderAt }.take(2)
    if (next.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("یادآورهای نزدیک")
        next.forEach { n ->
            val shape = MaterialTheme.shapes.large
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(Pal.card).border(1.dp, Pal.hairline, shape)
                    .clickable(role = Role.Button, onClick = onOpen)
            ) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(noteColor(n.color)))
                Row(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(n.displayTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Tag("⏰ " + noteWhen(n.reminderAt!!, now, zone))
                }
            }
        }
    }
}

// ───────────────────────── ویرایشگر ─────────────────────────

@Composable
private fun NoteEditorDialog(vm: MainVm, prefs: Prefs, initial: Note, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { JalaliDate.today() }
    val requestNotif = rememberNotifRequest()
    val p by vm.selected.collectAsState()
    val isNew = initial.id == 0L

    // تاریخ و ساعت اولیه از یادآور فعلی (اگر هست)
    val initT = initial.reminderAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var type by remember { mutableStateOf(if (isNew) Note.TYPE_TEXT else initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var body by remember { mutableStateOf(initial.body) }
    val items = remember { mutableStateListOf<CheckItem>().apply { addAll(initial.items.ifEmpty { if (initial.type == Note.TYPE_CHECK) listOf(CheckItem("")) else emptyList() }) } }
    var color by remember { mutableIntStateOf(initial.color) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var remindOn by remember { mutableStateOf(initial.reminderAt != null || initial.type == Note.TYPE_REMIND) }
    var pick by remember { mutableIntStateOf(if (initT != null) 3 else 1) }          // ۰ امروز، ۱ فردا، ۲ شیفت بعدی، ۳ انتخاب روز
    var date by remember { mutableStateOf(initT?.let { JalaliCalendar.fromLocalDate(it.toLocalDate()) } ?: today.plusDays(1)) }
    var month by remember { mutableStateOf(JalaliDate(date.year, date.month, 1)) }
    var minute by remember { mutableStateOf<Int?>(initT?.let { it.hour * 60 + it.minute }) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var alarm by remember { mutableStateOf(initial.alarm) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun workCodeOf(d: JalaliDate): Int? = p?.let {
        runCatching { DayResolver.resolve(it.base(), ShiftPattern.parse(it.pattern), d, emptyList()).effectiveCode }.getOrNull()
    }?.takeIf { ShiftType.byCode(it)?.isRest != true }
    fun nextShiftDay(): JalaliDate? = (1..60).map { today.plusDays(it) }.firstOrNull { workCodeOf(it) != null }
    val remind = remindOn || type == Note.TYPE_REMIND

    fun save() {
        val now = System.currentTimeMillis()
        val cleanItems = items.filter { it.text.isNotBlank() }.map { it.copy(text = it.text.trim()) }
        val draft = Note(
            id = if (isNew) Note.nextId(prefs.notes) else initial.id, type = type, title = title.trim(),
            body = if (type == Note.TYPE_CHECK) "" else body.trim(), items = if (type == Note.TYPE_CHECK) cleanItems else emptyList(),
            color = color, pinned = pinned, updatedAt = now
        )
        if (draft.isBlank) { Toast.makeText(ctx, "یادداشت خالی است", Toast.LENGTH_SHORT).show(); return }
        var at: Long? = null
        if (remind) {
            val m = minute
            if (m == null) { Toast.makeText(ctx, "ساعت یادآور را وارد کنید", Toast.LENGTH_SHORT).show(); return }
            at = SingleAlarm.at(date.toLocalDate(), m, zone)
            if (at <= now) {
                at = NoteReminders.nextOccurrence(at, repeat, now, zone)       // تکرارشونده: به نوبت بعد؛ یک‌باره: null
                if (at == null) { Toast.makeText(ctx, "این زمان گذشته است", Toast.LENGTH_SHORT).show(); return }
            }
        }
        val note = draft.copy(reminderAt = at, repeat = if (remind) repeat else Note.REPEAT_NONE, alarm = remind && alarm)
        vm.setNotes(if (isNew) prefs.notes + note else prefs.notes.map { if (it.id == note.id) note else it })
        if (remind) requestNotif()
        onClose()
    }

    AppDialog(
        if (isNew) "یادداشت جدید" else "ویرایش یادداشت", onClose, confirmText = "ذخیره", dismissText = "انصراف", onConfirm = { save() }
    ) {
        Segmented(listOf("متن", "چک‌لیست", "یادآور"), Note.TYPES.indexOf(type)) { i ->
            type = Note.TYPES[i]
            if (type == Note.TYPE_CHECK && items.isEmpty()) items.add(CheckItem(""))
        }
        AppField(title, { title = it.take(80) }, "عنوان")
        if (type == Note.TYPE_CHECK) {
            items.forEachIndexed { i, it ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Checkbox) { items[i] = it.copy(done = !it.done) }, contentAlignment = Alignment.Center) {
                        if (it.done) Ico(R.drawable.ic_check_circle, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
                        else Box(Modifier.size(22.dp).border(1.5.dp, Pal.muted, CircleShape))
                    }
                    AppField(it.text, { v -> items[i] = it.copy(text = v.take(120)) }, "مورد ${(i + 1).fa()}", Modifier.weight(1f))
                    SmallIconButton(R.drawable.ic_close, "حذف مورد", { items.removeAt(i) })
                }
            }
            AppButton("مورد جدید", { items.add(CheckItem("")) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Outline)
        } else {
            AppField(body, { body = it.take(2000) }, if (type == Note.TYPE_REMIND) "چه چیزی یادم بیاید؟" else "متن یادداشت", singleLine = false)
        }

        if (type != Note.TYPE_REMIND) SwitchRow("یادآوری", remindOn, { remindOn = it },
            Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer))
        if (remind) {
            Segmented(listOf("امروز", "فردا", "شیفت بعدی", "انتخاب روز"), pick) { i ->
                pick = i
                when (i) {
                    0 -> date = today
                    1 -> date = today.plusDays(1)
                    2 -> nextShiftDay()?.let { date = it }
                    else -> month = JalaliDate(date.year, date.month, 1)
                }
            }
            if (pick == 3) {
                MonthGrid(month, date, today, ::workCodeOf, { month = month.plusDays(-1).let { JalaliDate(it.year, it.month, 1) } },
                    { month = month.plusDays(JalaliCalendar.daysInMonth(month.year, month.month)).let { JalaliDate(it.year, it.month, 1) } }) { date = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockField("ساعت", minute, { minute = it }, Modifier.weight(1f), onInvalid = { minute = null })
            }
            Segmented(listOf("یک‌بار", "روزانه", "هفتگی", "ماهانه"), Note.REPEATS.indexOf(repeat)) { repeat = Note.REPEATS[it] }
            SwitchRow("با صدای آلارم", alarm, { alarm = it }, Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
                subtitle = if (alarm) "حتی در حالت بی‌صدا هشدار می‌دهد" else "اعلان معمولی")
            HintText(jalaliLine(date, minute, repeat))
        }

        Text("رنگ", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            (0..4).forEach { c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(noteColor(c))
                        .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else Pal.hairline, CircleShape)
                        .clickable(role = Role.RadioButton) { color = c }
                )
            }
        }
        SwitchRow("سنجاق به بالا", pinned, { pinned = it }, Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer))
        if (!isNew) AppButton("حذف یادداشت", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_delete, style = BtnStyle.Danger)
    }
    if (confirmDelete) ConfirmDialog("این یادداشت حذف شود؟", "حذف", onOk = {
        vm.setNotes(prefs.notes.filter { it.id != initial.id }); confirmDelete = false; onClose()
    }, onCancel = { confirmDelete = false }, danger = true)
}

private fun jalaliLine(d: JalaliDate, minute: Int?, repeat: String): String {
    val base = "${WEEK_FULL[d.dayOfWeek]} ${d.day.fa()} ${MONTHS[d.month - 1]}" + (minute?.let { " ساعت " + Fa.digits(ShiftTime.fmt(it)) } ?: "")
    return when (repeat) {
        Note.REPEAT_DAILY -> "از $base، هر روز"
        Note.REPEAT_WEEKLY -> "از $base، هر هفته"
        Note.REPEAT_MONTHLY -> "از $base، هر ماه شمسی (روز ${d.day.fa()}ام)"
        else -> base
    }
}
