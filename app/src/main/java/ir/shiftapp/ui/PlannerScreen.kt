package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import java.time.LocalDate

/**
 * برنامهٔ روزانه: سه اولویت (ضرب‌الاجل/اثر، پیش‌بینی و واقعی)، برنامهٔ ساعتی با بافر ثابت، مرور شبانه و ارزیابی هفتگی.
 * داده JSON در تنظیمات است (با پشتیبان می‌آید)؛ آلارم‌ها در system/PlannerAlarms.kt و مستقل از یادآور شیفت‌اند.
 */
@Composable
fun PlannerSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val holder = remember { RawHolder(PlannerState(), Planner::encode, Planner::decode) { vm.setPref(PrefKeys.PLANNER, it) } }
    LaunchedEffect(prefs.plannerRaw) { holder.sync(prefs.plannerRaw) }
    val todayEpoch = remember { LocalDate.now().toEpochDay().toInt() }
    var epoch by rememberSaveable { mutableStateOf(todayEpoch) }
    val st = holder.data
    val day = st.day(epoch)
    val requestNotif = rememberNotifRequest()
    fun setDay(f: (PlanDay) -> PlanDay) = holder.update { it.withDay(f(it.day(epoch))) }
    val jd = JalaliCalendar.fromLocalDate(LocalDate.ofEpochDay(epoch.toLong()))

    ToolScaffold("برنامهٔ روزانه", "اولویت‌ها، برنامهٔ ساعتی و مرور شبانه", onHome) {
        // ── روز ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ epoch -= 1 }) { Text("روز قبل") }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(role = Role.Button) { epoch = todayEpoch }) {
                Text("${Fa.WEEK_FULL[jd.dayOfWeek]} ${Fa.num(jd.day)} ${Fa.MONTHS[jd.month - 1]}", style = MaterialTheme.typography.titleMedium)
                HintText(if (epoch == todayEpoch) "امروز" else "برای برگشت به امروز بزنید")
            }
            TextButton({ epoch += 1 }) { Text("روز بعد") }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نوع روز", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Planner.DAY_TYPES.forEachIndexed { i, n -> AppChip(n, day.dayType == i, { setDay { it.copy(dayType = i) } }) }
                }
                key(epoch, holder.epoch) {
                    ClockField("ساعت برنامه‌ریزی‌شده (مدت، مثلاً ۰۸۰۰)", day.plannedMin.takeIf { it > 0 }, { v -> setDay { it.copy(plannedMin = v) } },
                        onInvalid = { setDay { it.copy(plannedMin = 0) } })
                }
            }
        }
        val carried = remember(st, epoch) { Planner.carriedFrom(st.day(epoch - 1)) }
        if (carried.isNotEmpty() && day.priorities.all { it.isEmpty }) {
            AppButton("افزودن ${Fa.num(carried.size)} مورد «فردا»ی مرور دیروز به اولویت‌ها", {
                setDay { d -> d.copy(priorities = List(3) { i -> carried.getOrNull(i)?.let { PlanPriority(text = it) } ?: d.priorities[i] }) }
            }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
        }

        // ── ۱) اولویت‌ها ──
        SectionLabel("۱) سه اولویت امروز")
        HintText("معیار انتخاب: ضرب‌الاجل دارد؟ یا بیشترین اثر را دارد؟ کاری که یکی از این دو را داشت، کاندید اولویت است.")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                day.priorities.forEachIndexed { i, p ->
                    key(epoch, i, holder.epoch) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CheckDot(p.done) { setDay { d -> d.copy(priorities = d.priorities.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }) } }
                                AppField(p.text, { v -> setDay { d -> d.copy(priorities = d.priorities.mapIndexed { j, x -> if (j == i) x.copy(text = v.take(200)) else x }) } },
                                    "اولویت ${Fa.num(i + 1)}", Modifier.weight(1f))
                            }
                            AppField(p.impact, { v -> setDay { d -> d.copy(priorities = d.priorities.mapIndexed { j, x -> if (j == i) x.copy(impact = v.take(120)) else x }) } },
                                "ضرب‌الاجل / اثر")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MinField("پیش‌بینی (دقیقه)", p.predictMin, Modifier.weight(1f)) { v -> setDay { d -> d.copy(priorities = d.priorities.mapIndexed { j, x -> if (j == i) x.copy(predictMin = v) else x }) } }
                                MinField("واقعی (دقیقه)", p.actualMin, Modifier.weight(1f)) { v -> setDay { d -> d.copy(priorities = d.priorities.mapIndexed { j, x -> if (j == i) x.copy(actualMin = v) else x }) } }
                            }
                        }
                    }
                    if (i < day.priorities.lastIndex) HorizontalDivider(color = Pal.hairline)
                }
            }
        }

        // ── ۲) برنامهٔ ساعتی ──
        SectionLabel("۲) برنامهٔ ساعتی")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Planner.sortedRows(day.rows).forEach { r ->
                    key(epoch, r.id, holder.epoch) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (r.buffer) HintText("بافر ثابت — فقط برای کارهای ناخواسته یا عقب‌افتاده")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CheckDot(r.done) { setDay { d -> d.copy(rows = d.rows.map { if (it.id == r.id) it.copy(done = !it.done) else it }) } }
                                ClockField("ساعت", r.minute.takeIf { it >= 0 }, { v -> setDay { d -> d.copy(rows = d.rows.map { if (it.id == r.id) it.copy(minute = v) else it }) } },
                                    Modifier.width(96.dp), onInvalid = { setDay { d -> d.copy(rows = d.rows.map { if (it.id == r.id) it.copy(minute = -1, alarm = false) else it }) } })
                                if (!r.buffer) AppField(r.text, { v -> setDay { d -> d.copy(rows = d.rows.map { if (it.id == r.id) it.copy(text = v.take(200)) else it }) } }, "کار", Modifier.weight(1f))
                                else Spacer(Modifier.weight(1f))
                                SmallIconButton(if (r.alarm) R.drawable.ic_alarm else R.drawable.ic_alarm_off, if (r.alarm) "خاموش کردن آلارم" else "آلارم این ساعت", {
                                    if (r.minute < 0) return@SmallIconButton
                                    if (!r.alarm) requestNotif()
                                    setDay { d -> d.copy(rows = d.rows.map { if (it.id == r.id) it.copy(alarm = !it.alarm) else it }) }
                                }, tint = if (r.alarm) MaterialTheme.colorScheme.primary else Pal.muted)
                                if (!r.buffer) SmallIconButton(R.drawable.ic_close, "حذف ردیف", { setDay { d -> d.copy(rows = d.rows.filter { it.id != r.id }) } })
                            }
                        }
                    }
                }
                if (day.rows.size < Planner.MAX_ROWS) AppButton("ردیف جدید", {
                    setDay { d -> d.copy(rows = d.rows + PlanRow(Planner.nextRowId(d.rows))) }
                }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Outline)
                HintText("برای آلارم، ساعت را بنویسید و آیکن زنگ را بزنید؛ کار انجام‌شده دیگر زنگ نمی‌زند.")
            }
        }

        // ── ۳) مرور شبانه ──
        SectionLabel("۳) مرور شبانه (۵ دقیقه)")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (day.review.all { it.text.isBlank() } && day.priorities.any { !it.isEmpty }) {
                    AppButton("برداشتن کارها از اولویت‌ها", {
                        setDay { d -> d.copy(review = List(3) { i -> d.priorities.getOrNull(i)?.takeIf { !it.isEmpty }?.let { PlanReview(it.text, if (it.done) 1 else 0) } ?: d.review[i] }) }
                    }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
                }
                day.review.forEachIndexed { i, rv ->
                    key(epoch, i, holder.epoch) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppField(rv.text, { v -> setDay { d -> d.copy(review = d.review.mapIndexed { j, x -> if (j == i) x.copy(text = v.take(200)) else x }) } }, "کار")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("انجام شد", "نشد", "فردا").forEachIndexed { k, n ->
                                    AppChip(n, rv.status == k + 1, { setDay { d -> d.copy(review = d.review.mapIndexed { j, x -> if (j == i) x.copy(status = if (x.status == k + 1) 0 else k + 1) else x }) } })
                                }
                            }
                            if (rv.status == 2 || rv.status == 3) AppField(rv.reason, { v -> setDay { d -> d.copy(review = d.review.mapIndexed { j, x -> if (j == i) x.copy(reason = v.take(200)) else x }) } }, "دلیل (یک خط)")
                        }
                    }
                }
                HintText("موردهای «فردا» فردا به‌عنوان پیشنهاد اولویت نشان داده می‌شوند.")
            }
        }

        // ── ارزیابی هفته ──
        SectionLabel("ارزیابی پایان هفته")
        val ev = Planner.weekEval(st, epoch)
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("روزهایی که هر سه اولویت انجام شد: ${Fa.num(ev.allDoneDays)} از ۷", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "میانگین فاصلهٔ زمان پیش‌بینی با واقعی: " + (ev.avgGapMin?.let { "${Fa.num(it)} دقیقه" } ?: "هنوز دادهٔ کافی نیست"),
                    style = MaterialTheme.typography.bodyMedium
                )
                HintText("هفت روز منتهی به روزی که باز است؛ فقط اولویت‌هایی که هم پیش‌بینی و هم زمان واقعی دارند حساب می‌شوند.")
            }
        }

        // ── آلارم‌ها ──
        SectionLabel("آلارم‌های برنامه")
        val card = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
        SwitchRow("آلارم ردیف‌های ساعتی", st.alarmsOn, { v -> holder.update { it.copy(alarmsOn = v) }; if (v) requestNotif() }, card,
            subtitle = "مستقل از یادآور شیفت و یادداشت‌ها؛ با صدای آلارم و دکمهٔ چرت")
        SwitchRow("یادآور مرور شبانه", st.reviewMin >= 0, { v -> holder.update { it.copy(reviewMin = if (v) 21 * 60 + 30 else -1) }; if (v) requestNotif() }, card,
            subtitle = if (st.reviewMin >= 0) "هر شب ساعت ${Fa.digits(ShiftTime.fmt(st.reviewMin))}" else "خاموش")
        if (st.reviewMin >= 0) key(holder.epoch) {
            ClockField("ساعت مرور شبانه", st.reviewMin, { v -> holder.update { it.copy(reviewMin = v) } })
        }
    }
}

@Composable
private fun CheckDot(done: Boolean, onToggle: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onToggle), contentAlignment = Alignment.Center) {
        if (done) Ico(R.drawable.ic_check_circle, size = 24.dp, tint = MaterialTheme.colorScheme.primary)
        else Box(Modifier.size(24.dp).border(1.5.dp, Pal.muted, CircleShape))
    }
}

/** ورودی عدد دقیقه؛ خالی = ۰. */
@Composable
private fun MinField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) Fa.num(value) else "") }
    AppField(text, { raw ->
        text = Fa.digits(Fa.latin(raw).filter { it.isDigit() }.take(4))
        onChange(Fa.latin(text).toIntOrNull()?.coerceIn(0, 1440) ?: 0)
    }, label, modifier, keyboard = KeyboardType.Number, ltr = true)
}
