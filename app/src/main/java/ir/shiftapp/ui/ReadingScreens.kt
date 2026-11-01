package ir.shiftapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliCalendar
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PrefKeys
import ir.shiftapp.reading.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

// مدل‌های قدیمی نگه داشته شده‌اند تا داده‌های قبلی کاربر و کدک پشتیبان حذف نشوند؛
// مسیر اصلی رابط کاربری دیگر این تب‌ها را نمایش نمی‌دهد.
private typealias ReadHolder = RawHolder<ReadingState>

@Composable
/** بخش مطالعه عمداً ساده نگه داشته شده: فقط کتابخانهٔ فایل‌ها و مسیر دانلود/مطالعه. */
fun ReadingSection(vm: MainVm, initialTab: Int = 0, initialLibFiles: Boolean = false, onHome: () -> Unit) {
    ToolScaffold("کتابخانه", "دانلود و مطالعهٔ فایل‌های کتاب", onHome) {
        BooksTab(openBrowser = initialLibFiles, onBrowserShown = {})
    }
}

// ───────────────────────── تب امروز ─────────────────────────

@Composable
private fun TodayTab(vm: MainVm, h: ReadHolder, today: Int) {
    val st = h.data
    val min = ReadingStats.minutesOn(st.sessions, today)
    val pages = ReadingStats.pagesOn(st.sessions, today)
    val streak = ReadingStats.streak(st.sessions, today)
    var logOpen by remember { mutableStateOf(false) }
    var logMinutes by remember { mutableStateOf(0) }
    var remind by remember { mutableStateOf(false) }
    var goalOpen by rememberSaveable { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مطالعهٔ امروز — ${dayText(today)}", style = MaterialTheme.typography.titleSmall)
            Text("${Fa.num(min)} از ${Fa.num(st.goalMin)} دقیقه", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { (min.toFloat() / st.goalMin).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            HintText("${Fa.num(pages)} صفحه · ${Fa.num(streak)} روز پیاپی")
            if (min >= st.goalMin) Text("هدف امروز کامل شد. آفرین!", style = MaterialTheme.typography.bodyMedium, color = Pal.green)
            TextButton({ goalOpen = !goalOpen }) { Text(if (goalOpen) "بستن" else "تغییر هدف روزانه") }
            if (goalOpen) ChipRow {
                listOf(15, 30, 45, 60, 90).forEach { g ->
                    AppChip("${Fa.num(g)} دقیقه", st.goalMin == g, { h.update { it.copy(goalMin = g) } })
                }
            }
        }
    }

    FocusTimer { m -> logMinutes = m; logOpen = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("ثبت مطالعه", { logMinutes = 0; logOpen = true }, Modifier.weight(1f), icon = R.drawable.ic_add)
        AppButton("یادآور", { remind = true }, Modifier.weight(1f), icon = R.drawable.ic_alarm, style = BtnStyle.Outline)
    }

    val reading = st.books.filter { it.status == 1 }.take(2)
    if (reading.isNotEmpty()) {
        SectionLabel("در حال خواندن")
        reading.forEach { b ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(b.title, style = MaterialTheme.typography.titleSmall)
                    if (b.totalPages > 0) {
                        LinearProgressIndicator(progress = { b.progress }, modifier = Modifier.fillMaxWidth())
                        HintText("صفحهٔ ${Fa.num(b.page)} از ${Fa.num(b.totalPages)}")
                    } else HintText("صفحهٔ ${Fa.num(b.page)}")
                }
            }
        }
    } else if (st.books.isEmpty()) {
        HintText("از تب «کتابخانه» اولین کتاب یا درس خودت را اضافه کن؛ بدون آن هم می‌توانی مطالعهٔ آزاد ثبت کنی.")
    }

    ShiftWeekStrip(
        vm, restTip = "امروز روز استراحتی؛ فرصت خوبی برای مطالعهٔ طولانی‌تر است.",
        workTip = "امروز شیفت داری؛ حتی ۱۵ دقیقه مطالعه هم حساب است."
    )

    if (logOpen) LogSessionDialog(st, logMinutes, onClose = { logOpen = false }) { id, m, p ->
        h.update { ReadingStats.apply(it, ReadSession(today, id, m, p)) }
        logOpen = false
    }
    if (remind) ReminderDialog(vm, "وقت مطالعه", onClose = { remind = false })
}

/** تایمر تمرکز؛ بر پایهٔ ساعت واقعی است، پس اگر برنامه لحظه‌ای کند شود زمان عقب نمی‌ماند. */
@Composable
private fun FocusTimer(onDone: (Int) -> Unit) {
    var endAt by remember { mutableStateOf(0L) }
    var totalMin by remember { mutableStateOf(25) }
    var left by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(endAt) {
        if (endAt > 0L) {
            while (true) {
                val r = ((endAt - System.currentTimeMillis() + 999) / 1000).toInt()
                if (r <= 0) break
                left = r
                delay(500)
            }
            left = 0
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDone(totalMin)
            endAt = 0L
        }
    }
    val running = endAt > 0L
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(R.drawable.ic_timer, size = 36.dp, iconSize = 20.dp, circle = true)
                Text("تایمر تمرکز", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (running) Fa.digits("%d:%02d".format(left / 60, left % 60)) else "آماده",
                    style = MaterialTheme.typography.titleMedium, color = if (running) MaterialTheme.colorScheme.primary else Pal.muted
                )
            }
            ChipRow {
                listOf(15, 25, 45, 60).forEach { m ->
                    AppChip("${Fa.num(m)} دقیقه", false, { totalMin = m; left = m * 60; endAt = System.currentTimeMillis() + m * 60_000L })
                }
                if (running) AppChip("توقف", false, { endAt = 0L; left = 0 })
            }
            HintText("با پایان زمان، گوشی می‌لرزد و می‌توانی همان نوبت را ثبت کنی. برنامه باید باز بماند.")
        }
    }
}

@Composable
private fun LogSessionDialog(st: ReadingState, initialMin: Int, onClose: () -> Unit, onOk: (Long, Int, Int) -> Unit) {
    var minutes by remember { mutableStateOf(if (initialMin > 0) initialMin.toString() else "") }
    var pages by remember { mutableStateOf("") }
    var bookId by remember { mutableStateOf(st.books.firstOrNull { it.status == 1 }?.id ?: 0L) }
    val m = parseNumber(minutes)?.roundToInt() ?: 0
    val p = parseNumber(pages)?.roundToInt() ?: 0
    AppDialog("ثبت مطالعه", onClose, confirmText = "ثبت", onConfirm = { onOk(bookId, m, p) }, dismissText = "انصراف", confirmEnabled = m in 1..1440) {
        ChipRow {
            AppChip("مطالعهٔ آزاد", bookId == 0L, { bookId = 0L })
            st.books.filter { it.status != 2 }.forEach { b -> AppChip(b.title.take(18), bookId == b.id, { bookId = b.id }) }
        }
        AppField(minutes, { minutes = it }, "مدت (دقیقه)", keyboard = KeyboardType.Number)
        if (bookId != 0L) AppField(pages, { pages = it }, "تعداد صفحهٔ خوانده‌شده (اختیاری)", keyboard = KeyboardType.Number)
    }
}

// ───────────────────────── تب کتابخانه ─────────────────────────

@Composable
private fun LibraryTab(h: ReadHolder, today: Int, initialFiles: Boolean = false) {
    var sub by rememberSaveable { mutableStateOf(if (initialFiles) 1 else 0) }
    var startBrowser by rememberSaveable { mutableStateOf(false) }
    Segmented(listOf("پیگیری کتاب و درس", "فایل کتاب‌ها"), sub) { sub = it }
    if (sub == 1) {
        BooksTab(openBrowser = startBrowser, onBrowserShown = { startBrowser = false })
        return
    }
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(-1) }
    var adding by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Book?>(null) }

    fun fix(b: Book, oldFinished: Int): Book = when {
        b.status == 2 -> b.copy(finishedDay = if (oldFinished > 0) oldFinished else today)
        else -> b.copy(finishedDay = 0)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("افزودن کتاب / درس", { adding = true }, Modifier.weight(1f), icon = R.drawable.ic_add)
        AppButton("جست‌وجوی کتاب", { startBrowser = true; sub = 1 }, Modifier.weight(1f), icon = R.drawable.ic_search, style = BtnStyle.Tonal)
    }
    AppField(q, { q = it }, "جست‌وجو در عنوان و نویسنده")
    ChipRow {
        AppChip("همه", filter == -1, { filter = -1 })
        BOOK_STATUS.forEachIndexed { i, n -> AppChip(n, filter == i, { filter = i }) }
    }
    val list = remember(h.data.books, q, filter) {
        h.data.books.filter { (filter == -1 || it.status == filter) && (q.isBlank() || it.title.contains(q.trim()) || it.author.contains(q.trim())) }
    }
    if (list.isEmpty()) EmptyState(R.drawable.ic_home_reading, "چیزی پیدا نشد", text = "کتاب، درس یا مقالهٔ خودت را اضافه کن تا پیشرفتش را دنبال کنی.")
    list.forEach { b -> BookCard(b, h, today, onEdit = { edit = b }) }

    if (adding) BookDialog(null, onClose = { adding = false }) { nb ->
        h.update { it.copy(books = it.books + fix(nb, 0).copy(id = it.nextId())) }
        adding = false
    }
    edit?.let { old ->
        BookDialog(old, onClose = { edit = null }) { nb ->
            h.update { s -> s.copy(books = s.books.map { x -> if (x.id == old.id) fix(nb, old.finishedDay).copy(id = old.id, rating = old.rating) else x }) }
            edit = null
        }
    }
}

@Composable
private fun BookCard(b: Book, h: ReadHolder, today: Int, onEdit: () -> Unit) {
    var open by rememberSaveable(b.id) { mutableStateOf(false) }
    var pageDlg by remember { mutableStateOf(false) }
    var noteDlg by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val notes = h.data.notes.filter { it.bookId == b.id }

    AppCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(R.drawable.ic_home_reading, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(b.author, BOOK_KINDS.getOrElse(b.kind) { "" }, BOOK_STATUS.getOrElse(b.status) { "" }).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
                if (b.status == 2) Ico(R.drawable.ic_check_circle, size = 22.dp, tint = Pal.green, desc = "تمام شد")
            }
            if (b.totalPages > 0) {
                LinearProgressIndicator(progress = { b.progress }, modifier = Modifier.fillMaxWidth())
                HintText("صفحهٔ ${Fa.num(b.page)} از ${Fa.num(b.totalPages)}")
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("ثبت صفحه", { pageDlg = true }, Modifier.weight(1f), style = BtnStyle.Tonal)
                    AppButton("یادداشت", { noteDlg = true }, Modifier.weight(1f), style = BtnStyle.Tonal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("ویرایش", onEdit, Modifier.weight(1f), style = BtnStyle.Outline)
                    AppButton("حذف", { del = true }, Modifier.weight(1f), style = BtnStyle.Danger)
                }
                if (b.status == 2) {
                    Text("امتیاز", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                    ChipRow {
                        (1..5).forEach { r ->
                            AppChip(Fa.num(r), b.rating == r, { h.update { s -> s.copy(books = s.books.map { x -> if (x.id == b.id) x.copy(rating = r) else x }) } })
                        }
                    }
                }
                notes.forEach { n ->
                    RowDivider(Modifier.padding(horizontal = 0.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.text, style = MaterialTheme.typography.bodyMedium)
                            HintText((if (n.page > 0) "صفحهٔ ${Fa.num(n.page)} · " else "") + dayText(n.day))
                        }
                        SmallIconButton(R.drawable.ic_delete, "حذف یادداشت", { h.update { s -> s.copy(notes = s.notes.filter { it.id != n.id }) } }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (pageDlg) PageDialog(b, onClose = { pageDlg = false }) { v ->
        h.update { s ->
            val np = if (b.totalPages > 0) minOf(v, b.totalPages) else v
            val done = b.totalPages > 0 && np >= b.totalPages
            s.copy(books = s.books.map { x ->
                if (x.id != b.id) x else x.copy(
                    page = np,
                    status = if (done) 2 else if (x.status == 0) 1 else x.status,
                    finishedDay = if (done && x.status != 2) today else x.finishedDay
                )
            })
        }
        pageDlg = false
    }
    if (noteDlg) NoteDialog(onClose = { noteDlg = false }) { page, text ->
        h.update { s -> s.copy(notes = s.notes + BookNote(s.nextId(), b.id, page, text, today)) }
        noteDlg = false
    }
    if (del) ConfirmDialog("«${b.title}» و یادداشت‌های آن حذف شود؟", "حذف", {
        h.update { s -> s.copy(books = s.books.filter { it.id != b.id }, notes = s.notes.filter { it.bookId != b.id }) }
        del = false
    }, { del = false }, danger = true)
}

@Composable
private fun PageDialog(b: Book, onClose: () -> Unit, onOk: (Int) -> Unit) {
    var t by remember { mutableStateOf(b.page.toString()) }
    val v = parseNumber(t)?.roundToInt() ?: -1
    AppDialog("صفحهٔ فعلی", onClose, confirmText = "ثبت", onConfirm = { onOk(v) }, dismissText = "انصراف", confirmEnabled = v >= 0) {
        AppField(t, { t = it }, "شمارهٔ صفحه", keyboard = KeyboardType.Number)
        if (b.totalPages > 0) HintText("با رسیدن به صفحهٔ ${Fa.num(b.totalPages)} کتاب «تمام شد» می‌شود.")
        HintText("برای ثبت زمان مطالعه از تب «امروز» استفاده کن.")
    }
}

@Composable
private fun NoteDialog(onClose: () -> Unit, onOk: (Int, String) -> Unit) {
    var page by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AppDialog("یادداشت یا نقل‌قول", onClose, confirmText = "ثبت", onConfirm = { onOk(parseNumber(page)?.roundToInt() ?: 0, text.trim()) }, dismissText = "انصراف", confirmEnabled = text.isNotBlank()) {
        AppField(text, { text = it.take(600) }, "متن", singleLine = false)
        AppField(page, { page = it }, "صفحه (اختیاری)", keyboard = KeyboardType.Number)
    }
}

@Composable
private fun BookDialog(initial: Book?, onClose: () -> Unit, onOk: (Book) -> Unit) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var author by remember { mutableStateOf(initial?.author.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: 0) }
    var total by remember { mutableStateOf(initial?.totalPages?.takeIf { it > 0 }?.toString().orEmpty()) }
    var page by remember { mutableStateOf(initial?.page?.takeIf { it > 0 }?.toString().orEmpty()) }
    var status by remember { mutableStateOf(initial?.status ?: 1) }
    AppDialog(
        if (initial == null) "کتاب یا درس جدید" else "ویرایش", onClose, confirmText = "ذخیره", dismissText = "انصراف", confirmEnabled = title.isNotBlank(),
        onConfirm = {
            val t = parseNumber(total)?.roundToInt() ?: 0
            var p = parseNumber(page)?.roundToInt() ?: 0
            if (t > 0) p = minOf(p, t)
            onOk(Book(initial?.id ?: 0L, title.trim(), author.trim(), kind, t, p, status, initial?.rating ?: 0, initial?.finishedDay ?: 0))
        }
    ) {
        AppField(title, { title = it.take(80) }, "عنوان")
        AppField(author, { author = it.take(60) }, "نویسنده یا استاد (اختیاری)")
        ChipRow { BOOK_KINDS.forEachIndexed { i, n -> AppChip(n, kind == i, { kind = i }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(total, { total = it }, "کل صفحه‌ها", Modifier.weight(1f), keyboard = KeyboardType.Number)
            AppField(page, { page = it }, "صفحهٔ فعلی", Modifier.weight(1f), keyboard = KeyboardType.Number)
        }
        ChipRow { BOOK_STATUS.forEachIndexed { i, n -> AppChip(n, status == i, { status = i }) } }
    }
}

// ───────────────────────── تب آمار ─────────────────────────

@Composable
private fun StatsTab(h: ReadHolder, today: Int) {
    val st = h.data
    val week = ReadingStats.last7(st.sessions, today)
    val streak = ReadingStats.streak(st.sessions, today)
    val jy = JalaliCalendar.fromLocalDate(LocalDate.ofEpochDay(today.toLong())).year
    val yStart = JalaliDate(jy, 1, 1).toLocalDate().toEpochDay().toInt()
    val yEnd = JalaliDate(jy + 1, 1, 1).toLocalDate().toEpochDay().toInt() - 1
    val finished = ReadingStats.finishedBetween(st.books, yStart, yEnd)
    val totalMin = st.sessions.sumOf { it.minutes }
    val totalPages = st.sessions.sumOf { it.pages }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("دقیقه در ۷ روز اخیر", Fa.num(week.sum()), R.drawable.ic_timer, Modifier.weight(1f))
        StatTile("روز پیاپی", Fa.num(streak), R.drawable.ic_check_circle, Modifier.weight(1f), tint = Pal.green)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("تمام‌شده در ${Fa.num(jy)}", "${Fa.num(finished.size)} از ${Fa.num(st.yearGoalBooks)}", R.drawable.ic_home_reading, Modifier.weight(1f), tint = Pal.coral)
        StatTile("صفحهٔ ثبت‌شده", Fa.num(totalPages), R.drawable.ic_note, Modifier.weight(1f), tint = Pal.amber)
    }

    SettingsGroup("۷ روز اخیر", icon = R.drawable.ic_chart) {
        WeekBars(week, st.goalMin, today)
        HintText("خط سبز هدف روزانه است (${Fa.num(st.goalMin)} دقیقه). مجموع کل مطالعه: ${Fa.num(totalMin / 60)} ساعت و ${Fa.num(totalMin % 60)} دقیقه.")
    }

    SettingsGroup("هدف سالانه", icon = R.drawable.ic_star) {
        Text("چند کتاب یا درس را در سال تمام کنی؟", style = MaterialTheme.typography.bodyMedium)
        ChipRow { listOf(6, 12, 24, 36, 52).forEach { g -> AppChip(Fa.num(g), st.yearGoalBooks == g, { h.update { it.copy(yearGoalBooks = g) } }) } }
        LinearProgressIndicator(progress = { (finished.size.toFloat() / st.yearGoalBooks).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }

    if (finished.isNotEmpty()) {
        SettingsGroup("تمام‌شده‌های امسال", icon = R.drawable.ic_check_circle) {
            finished.sortedByDescending { it.finishedDay }.forEach { b ->
                Row {
                    Text(b.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (if (b.rating > 0) "${Fa.num(b.rating)} از ۵ · " else "") + dayText(b.finishedDay),
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBars(values: List<Int>, goal: Int, today: Int) {
    val bar = MaterialTheme.colorScheme.primary
    val goalColor = Pal.green
    val maxV = maxOf(values.max(), goal, 1).toFloat()
    val letters = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = values.size
        val slot = size.width / n
        val bw = slot * 0.55f
        val hh = size.height
        values.forEachIndexed { i, v ->
            val bh = hh * (v / maxV)
            val x = (n - 1 - i) * slot + (slot - bw) / 2f      // قدیمی‌ترین روز سمت راست (جهت متن فارسی)
            drawRect(bar, Offset(x, hh - bh), Size(bw, bh))
        }
        val gy = hh - hh * (goal / maxV)
        drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), 1.5.dp.toPx())
    }
    Row(Modifier.fillMaxWidth()) {
        values.indices.forEach { i ->
            val dow = LocalDate.ofEpochDay((today - (values.size - 1 - i)).toLong()).dayOfWeek.value
            Text(letters[(dow + 1) % 7], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Pal.muted, textAlign = TextAlign.Center)
        }
    }
}
