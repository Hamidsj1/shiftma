package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliCalendar
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PrefKeys
import ir.shiftapp.income.*
import java.time.LocalDate

private const val UNIT = "تومان"
private val WEEK_LETTERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

private fun incomeMoney(a: Long) = "${IncomeStats.format(a)} $UNIT"
private fun dayLabel(epoch: Int): String {
    val j = IncomeStats.jalali(epoch)
    return "${Fa.WEEK_FULL[j.dayOfWeek]} ${Fa.num(j.day)} ${Fa.MONTHS[j.month - 1]}"
}

/** دریافتی‌های ماهانه: ثبت تاریخ، عنوان، مبلغ، دسته و یادداشت؛ جمع ماه، سهم هر دسته و نمودار سال. */
@Composable
fun IncomeSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val holder = remember { RawHolder(IncomeState(), IncomeCodec::encode, IncomeCodec::decode) { vm.setPref(PrefKeys.INCOME, it) } }
    LaunchedEffect(prefs.incomeRaw) { holder.sync(prefs.incomeRaw) }
    val todayJ = remember { JalaliDate.today() }
    var ym by rememberSaveable { mutableStateOf(todayJ.year * 100 + todayJ.month) }
    val y = ym / 100
    val m = ym % 100
    fun go(delta: Int) {
        var yy = y; var mm = m + delta
        if (mm < 1) { mm = 12; yy-- } else if (mm > 12) { mm = 1; yy++ }
        ym = yy * 100 + mm
    }

    var dialogFor by remember { mutableStateOf<IncomeEntry?>(null) }     // ویرایش
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<IncomeEntry?>(null) }

    val all = holder.data.entries
    val list = remember(all, ym) { IncomeStats.inMonth(all, y, m) }
    val total = IncomeStats.total(list)
    val prevTotal = remember(all, ym) { val py = if (m == 1) y - 1 else y; val pm = if (m == 1) 12 else m - 1; IncomeStats.total(IncomeStats.inMonth(all, py, pm)) }
    val yearTotals = remember(all, y) { IncomeStats.yearTotals(all, y) }

    ToolScaffold("دریافتی‌های ماهانه", "حقوق، اضافه‌کار، پاداش و سایر دریافتی‌ها", onHome) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(R.drawable.ic_chevron_left, "ماه قبل", { go(-1) })
            Text("${Fa.MONTHS[m - 1]} ${Fa.num(y)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            SmallIconButton(R.drawable.ic_chevron_right, "ماه بعد", { go(1) })
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText("جمع دریافتی این ماه")
                Text(incomeMoney(total), style = MaterialTheme.typography.headlineSmall, color = Pal.green, fontWeight = FontWeight.Bold)
                val parts = buildList {
                    add("${Fa.num(list.size)} مورد")
                    val pts = list.sumOf { it.points }
                    if (pts > 0) add("${IncomeStats.formatPoints(pts)} امتیاز راندمان")
                    if (prevTotal > 0 && total > 0) {
                        val pct = ((total - prevTotal) * 100.0 / prevTotal).toInt()
                        add("نسبت به ماه قبل ${if (pct >= 0) "+" else "−"}${Fa.num(kotlin.math.abs(pct))}٪")
                    }
                }
                HintText(parts.joinToString(" · "))
            }
        }

        AppButton("ثبت دریافتی", { adding = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)

        val kinds = IncomeStats.byKind(list)
        if (kinds.size > 1) {
            SectionLabel("سهم هر دسته")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    kinds.forEach { (k, sum) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(INCOME_KINDS[k], Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(incomeMoney(sum), style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                            }
                            LinearProgressIndicator(progress = { (sum.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }

        SectionLabel("دریافتی‌های ${Fa.MONTHS[m - 1]}")
        if (list.isEmpty()) EmptyState(
            R.drawable.ic_home_income, "در این ماه چیزی ثبت نشده",
            text = "با «ثبت دریافتی» تاریخ، عنوان و مبلغ هر دریافتی را بنویسید. داده‌ها فقط روی همین گوشی می‌مانند و با پشتیبان‌گیری برنامه ذخیره می‌شوند."
        )
        list.forEach { e ->
            AppCard(Modifier.fillMaxWidth(), onClick = { dialogFor = e }) {
                Row(
                    Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HintText("${dayLabel(e.day)} · ${INCOME_KINDS[e.kind.coerceIn(0, INCOME_KINDS.size - 1)]}")
                        if (e.points > 0 && e.rialPerPoint > 0) HintText("${IncomeStats.formatPoints(e.points)} امتیاز × ${IncomeStats.formatPoints(e.rialPerPoint / 10.0)} $UNIT")
                        if (e.note.isNotBlank()) HintText(e.note, color = Pal.muted)
                    }
                    Text(IncomeStats.format(e.amount), style = MaterialTheme.typography.titleSmall, color = Pal.green, fontWeight = FontWeight.Bold)
                    SmallIconButton(R.drawable.ic_delete, "حذف", { deleting = e }, tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        SectionLabel("سال ${Fa.num(y)}")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val maxV = (yearTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    yearTotals.forEachIndexed { i, v ->
                        val sel = i + 1 == m
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = Fa.MONTHS[i]) { ym = y * 100 + i + 1 },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                Modifier.fillMaxWidth(0.7f).height((78 * v.toFloat() / maxV).coerceAtLeast(if (v > 0) 4f else 1.5f).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = if (v > 0) 0.35f else 0.12f))
                            )
                            Text(Fa.num(i + 1), style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.primary else Pal.muted)
                        }
                    }
                }
                HintText("جمع سال: ${incomeMoney(yearTotals.sum())} · با لمس هر ستون به همان ماه بروید")
            }
        }
    }

    // ارزش امتیاز را از آخرین راندمان ثبت‌شده برمی‌داریم تا هر بار دوباره تایپ نشود
    val lastRial = all.filter { it.kind == KIND_POINTS && it.rialPerPoint > 0 }.maxByOrNull { it.day }?.rialPerPoint ?: DEFAULT_RIAL_PER_POINT
    if (adding) IncomeDialog(null, defaultDay(todayJ, y, m), lastRial, onSave = { d -> holder.update { st -> st.copy(entries = st.entries + d.copy(id = st.nextId())) }; adding = false }, onClose = { adding = false })
    dialogFor?.let { e ->
        IncomeDialog(e, e.day, lastRial, onSave = { d -> holder.update { st -> st.copy(entries = st.entries.map { if (it.id == e.id) d.copy(id = e.id) else it }) }; dialogFor = null }, onClose = { dialogFor = null })
    }
    deleting?.let { e ->
        ConfirmDialog("«${e.title}» (${incomeMoney(e.amount)}) حذف شود؟", "حذف", onOk = { holder.update { st -> st.copy(entries = st.entries.filter { it.id != e.id }) }; deleting = null }, onCancel = { deleting = null }, danger = true)
    }
}

/** روز پیش‌فرض ثبت: اگر ماه جاری دیده می‌شود امروز، وگرنه اول همان ماه. */
private fun defaultDay(today: JalaliDate, y: Int, m: Int): Int =
    (if (today.year == y && today.month == m) today else JalaliDate(y, m, 1)).toLocalDate().toEpochDay().toInt()

@Composable
private fun IncomeDialog(initial: IncomeEntry?, startDay: Int, defaultRial: Long, onSave: (IncomeEntry) -> Unit, onClose: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var amountText by remember { mutableStateOf(initial?.amount?.let { IncomeStats.format(it) }.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: 0) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var day by remember { mutableStateOf(IncomeStats.jalali(startDay)) }
    var month by remember { mutableStateOf(JalaliDate(day.year, day.month, 1)) }
    var pickDate by remember { mutableStateOf(false) }
    // راندمان: ۰ = امتیاز را می‌دهم و مبلغ حساب شود، ۱ = مبلغ واریزی را می‌دهم و تعداد امتیاز حساب شود.
    // ویرایش یک راندمان از «مبلغ» شروع می‌شود چون مبلغ واریزی همان عددِ قطعی است.
    var mode by remember { mutableStateOf(if ((initial?.points ?: 0.0) > 0) 1 else 0) }
    var pointsText by remember { mutableStateOf("") }
    var rialText by remember { mutableStateOf(IncomeStats.format(initial?.rialPerPoint?.takeIf { it > 0 } ?: defaultRial)) }

    val isPts = kind == KIND_POINTS
    val rial = IncomeStats.parse(rialText) ?: 0L
    val ptsTyped = IncomeStats.parseDecimal(pointsText)
    val amountTyped = IncomeStats.parse(amountText)
    val fromPoints = isPts && mode == 0
    val finalAmount: Long? = if (fromPoints) ptsTyped?.takeIf { rial > 0 }?.let { IncomeStats.amountFromPoints(it, rial) } else amountTyped
    val finalPoints: Double = when {
        !isPts -> 0.0
        fromPoints -> ptsTyped ?: 0.0
        amountTyped != null && rial > 0 -> IncomeStats.pointsFromAmount(amountTyped, rial)
        else -> 0.0
    }
    val valid = title.isNotBlank() && finalAmount != null && finalAmount > 0 && (!isPts || rial > 0)

    AppDialog(
        if (initial == null) "ثبت دریافتی" else "ویرایش دریافتی", onClose,
        confirmText = "ذخیره", dismissText = "انصراف", confirmEnabled = valid,
        onConfirm = {
            if (finalAmount != null) onSave(
                IncomeEntry(
                    0, day.toLocalDate().toEpochDay().toInt(), title.trim().take(60), finalAmount.coerceAtMost(IncomeStats.MAX_AMOUNT), kind, note.trim().take(120),
                    if (isPts) Math.round(finalPoints * 10_000) / 10_000.0 else 0.0, if (isPts) rial else 0L
                )
            )
        }
    ) {
        ChipRow {
            INCOME_KINDS.forEachIndexed { i, k ->
                AppChip(k, kind == i, { kind = i; if (title.isBlank() || title in INCOME_KINDS) title = k })
            }
        }
        AppField(title, { title = it.take(60) }, "عنوان", placeholder = "مثلاً حقوق مهر")
        if (isPts) {
            Segmented(listOf("از روی امتیاز", "از روی مبلغ واریزی"), mode) { mode = it }
            AppField(
                rialText, { t -> rialText = IncomeStats.parse(t)?.let { IncomeStats.format(it) } ?: "" }, "ارزش هر امتیاز (ریال)",
                keyboard = KeyboardType.Number, ltr = true,
                supporting = if (rial > 0) "هر امتیاز = ${IncomeStats.formatPoints(rial / 10.0)} $UNIT" else null
            )
            if (mode == 0) AppField(
                pointsText, { t -> pointsText = t.filter { c -> c in '0'..'9' || c in '۰'..'۹' || c in ".٫/,٬" }.take(14) }, "تعداد امتیاز",
                keyboard = KeyboardType.Decimal, ltr = true, placeholder = "مثلاً ۵۰۰۰۰"
            )
        }
        if (!fromPoints) AppField(
            amountText, { t -> amountText = IncomeStats.parse(t)?.let { IncomeStats.format(it) } ?: "" },
            if (isPts) "مبلغ واریزی ($UNIT)" else "مبلغ ($UNIT)", keyboard = KeyboardType.Number, ltr = true
        )
        if (isPts) {
            val line = if (fromPoints) {
                if (finalAmount != null) "مبلغ: ${incomeMoney(finalAmount)}" else "تعداد امتیاز را بنویسید تا مبلغ حساب شود."
            } else {
                if (finalPoints > 0) "تعداد امتیاز: ${IncomeStats.formatPoints(finalPoints)}" else "مبلغ واریزی را بنویسید تا تعداد امتیاز حساب شود."
            }
            Text(line, style = MaterialTheme.typography.titleSmall, color = Pal.green)
        }
        AppButton("تاریخ: ${dayLabel(day.toLocalDate().toEpochDay().toInt())}", { pickDate = !pickDate }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
        if (pickDate) IncomeDayGrid(month, day, onPrev = { month = prevMonth(month) }, onNext = { month = nextMonth(month) }, onSelect = { day = it; pickDate = false })
        AppField(note, { note = it.take(120) }, "یادداشت (اختیاری)", singleLine = false)
    }
}

private fun prevMonth(m: JalaliDate) = if (m.month == 1) JalaliDate(m.year - 1, 12, 1) else JalaliDate(m.year, m.month - 1, 1)
private fun nextMonth(m: JalaliDate) = if (m.month == 12) JalaliDate(m.year + 1, 1, 1) else JalaliDate(m.year, m.month + 1, 1)

/** انتخاب روز با هر تاریخی (برخلاف آلارم، روزهای گذشته هم مجازند). */
@Composable
private fun IncomeDayGrid(month: JalaliDate, selected: JalaliDate, onPrev: () -> Unit, onNext: () -> Unit, onSelect: (JalaliDate) -> Unit) {
    val days = JalaliCalendar.daysInMonth(month.year, month.month)
    val first = month.dayOfWeek
    val cells: List<Int?> = List(first) { null } + (1..days).map { it } + List((7 - (first + days) % 7) % 7) { null }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(R.drawable.ic_chevron_left, "ماه قبل", onPrev)
            Text("${Fa.MONTHS[month.month - 1]} ${Fa.num(month.year)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            SmallIconButton(R.drawable.ic_chevron_right, "ماه بعد", onNext)
        }
        Row(Modifier.fillMaxWidth()) {
            WEEK_LETTERS.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Pal.muted, textAlign = TextAlign.Center) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    if (d == null) Spacer(Modifier.weight(1f).height(36.dp)) else {
                        val date = JalaliDate(month.year, month.month, d)
                        val sel = date == selected
                        Box(
                            Modifier.weight(1f).height(36.dp).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(role = Role.Button) { onSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Fa.num(d), style = MaterialTheme.typography.bodySmall,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
