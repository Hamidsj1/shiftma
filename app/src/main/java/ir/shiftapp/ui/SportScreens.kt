package ir.shiftapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.Exercise
import ir.shiftapp.core.Fa
import ir.shiftapp.core.PrefKeys
import ir.shiftapp.core.JalaliCalendar
import ir.shiftapp.core.SPORT_EQUIPMENT
import ir.shiftapp.core.SPORT_LEVELS
import ir.shiftapp.core.SPORT_MUSCLES
import ir.shiftapp.core.SportCalc
import ir.shiftapp.core.SportData
import ir.shiftapp.core.PlanStep
import ir.shiftapp.core.WorkoutPlan
import ir.shiftapp.sport.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private val SPORT_TABS = listOf("تمرین", "حرکات", "رژیم", "پیشرفت")

private class TrainSession(val title: String, val steps: List<PlanStep>, val programId: String?, val dayIdx: Int)

private typealias SportHolder = RawHolder<SportState>

// ───────────────────────── کمکی‌ها ─────────────────────────

internal fun dayText(epoch: Int): String {
    val j = JalaliCalendar.fromLocalDate(LocalDate.ofEpochDay(epoch.toLong()))
    return "${Fa.num(j.day)} ${Fa.MONTHS[j.month - 1]}"
}

internal fun kgText(v: Double): String =
    if (v == Math.floor(v)) Fa.num(v.toInt()) else Fa.digits(String.format(Locale.US, "%.1f", v)).replace('.', '٫')

private fun setText(s: SetLog, unit: Int): String = when {
    unit == WorkoutPlan.UNIT_MIN -> "${Fa.num(s.reps)} دقیقه"
    unit == WorkoutPlan.UNIT_SEC -> "${Fa.num(s.reps)} ثانیه"
    s.kg > 0 -> "${Fa.num(s.reps)} × ${kgText(s.kg)} کیلو"
    else -> "${Fa.num(s.reps)} تکرار"
}

private fun SportState.addExercises(day: Int, ids: List<String>): SportState {
    val w = workoutOn(day)
    val have = w.items.map { it.exerciseId }.toSet()
    val add = ids.filter { it !in have }.distinct().map { ExerciseLog(it) }
    return withWorkout(w.copy(items = w.items + add))
}

private fun SportState.addSet(day: Int, id: String, set: SetLog): SportState {
    val s2 = addExercises(day, listOf(id))
    val w = s2.workoutOn(day)
    return s2.withWorkout(w.copy(items = w.items.map { if (it.exerciseId == id) it.copy(sets = it.sets + set) else it }))
}

/** آخرین وزنهٔ ثبت‌شده برای یک حرکت (صفر اگر تا حالا وزنه‌ای ثبت نشده). */
private fun lastKg(d: SportState, id: String): Double =
    d.workouts.asReversed().firstNotNullOfOrNull { w ->
        w.items.firstOrNull { it.exerciseId == id }?.sets?.lastOrNull { it.kg > 0 }?.kg
    } ?: 0.0

private fun muscleName(i: Int) = SPORT_MUSCLES.getOrElse(i) { "" }

@Composable
private fun NumInput(label: String, initial: String, modifier: Modifier = Modifier.fillMaxWidth(), onValue: (Double) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AppField(
        t, { text -> t = text; val v = parseNumber(text); if (v != null && v > 0) onValue(v) },
        label, modifier, keyboard = KeyboardType.Decimal
    )
}

// ───────────────────────── صفحهٔ اصلی بخش ─────────────────────────

@Composable
fun SportSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val holder = remember { RawHolder(SportState(), SportCodec::encodeStr, SportCodec::decodeStr) { vm.setPref(PrefKeys.SPORT, it) } }
    LaunchedEffect(prefs.sportRaw) { holder.sync(prefs.sportRaw) }
    val today = remember { LocalDate.now().toEpochDay().toInt() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var session by remember { mutableStateOf<TrainSession?>(null) }
    val s = session

    if (s != null) {
        WorkoutPlayer(
            title = s.title, steps = s.steps,
            startKg = { id -> lastKg(holder.data, id) },
            onSet = { id, set -> holder.update { it.addSet(today, id, set) } },
            onFinish = { completed ->
                val pid = s.programId
                if (completed && pid != null && s.dayIdx >= 0) {
                    val n = SportData.program(pid)?.days?.size ?: 1
                    holder.update { it.copy(programDay = (s.dayIdx + 1) % n) }
                }
                session = null
            }
        )
    } else {
        ToolScaffold("ورزش و تغذیه", "تمرین راهنما، حرکات، رژیم و پیشرفت", onHome) {
            Segmented(SPORT_TABS, tab) { tab = it }
            when (tab) {
                0 -> TrainTab(vm, holder, today) { session = it }
                1 -> ExercisesTab(holder, today) { tab = 0 }
                2 -> DietTab(holder, today)
                else -> ProgressTab(holder, today)
            }
            HintText("این بخش فقط راهنمای عمومی است و جایگزین مشورت با پزشک یا مربی نیست. در صورت درد یا بیماری، قبل از شروع با پزشک صحبت کنید.")
        }
    }
}

// ───────────────────────── تب حرکات ─────────────────────────

@Composable
private fun ExercisesTab(h: SportHolder, today: Int, goLog: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf(-1) }
    var equip by rememberSaveable { mutableStateOf(-1) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    AppField(q, { q = it }, "جست‌وجوی حرکت")
    ChipRow {
        AppChip("همه", muscle == -1, { muscle = -1 })
        SPORT_MUSCLES.forEachIndexed { i, n -> AppChip(n, muscle == i, { muscle = i }) }
    }
    ChipRow {
        AppChip("هر وسیله", equip == -1, { equip = -1 })
        SPORT_EQUIPMENT.forEachIndexed { i, n -> AppChip(n, equip == i, { equip = i }) }
    }
    val list = remember(q, muscle, equip) {
        SportData.EXERCISES.filter {
            (muscle == -1 || it.muscle == muscle) && (equip == -1 || it.equipment == equip) &&
                (q.isBlank() || it.name.contains(q.trim()))
        }
    }
    HintText("${Fa.num(list.size)} حرکت")
    if (list.isEmpty()) EmptyState(R.drawable.ic_search, "حرکتی پیدا نشد", text = "فیلترها را تغییر دهید.")
    list.forEach { e ->
        ExerciseCard(
            e, open == e.id, onToggle = { open = if (open == e.id) null else e.id },
            onAdd = { h.update { it.addExercises(today, listOf(e.id)) }; goLog() }
        )
    }
}

@Composable
private fun ExerciseCard(e: Exercise, expanded: Boolean, onToggle: () -> Unit, onAdd: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onToggle) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${muscleName(e.muscle)} · ${SPORT_EQUIPMENT[e.equipment]} · ${SPORT_LEVELS[e.level]}",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
                Ico(R.drawable.ic_expand_more, size = 22.dp, tint = Pal.muted)
            }
            if (expanded) {
                ExerciseAnimOrNothing(e.id, e.name)
                Text(e.steps, style = MaterialTheme.typography.bodyMedium)
                Text("نکته: ${e.tip}", style = MaterialTheme.typography.bodySmall, color = Pal.amber)
                Text("پیشنهاد: ${e.sets}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                AppButton("افزودن به تمرین امروز", onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
            }
        }
    }
}

// ───────────────────────── تب تمرین (صفحهٔ اصلی) ─────────────────────────

private fun stepLabel(st: PlanStep): String {
    val per = when {
        !st.timed -> if (st.sets > 1) Fa.num(st.reps) else "${Fa.num(st.reps)} تکرار"
        st.seconds >= 60 && st.seconds % 60 == 0 -> "${Fa.num(st.seconds / 60)} دقیقه"
        else -> "${Fa.num(st.seconds)} ثانیه"
    }
    return if (st.sets > 1) "${Fa.num(st.sets)}×$per" else per
}

@Composable
private fun TrainTab(vm: MainVm, h: SportHolder, today: Int, start: (TrainSession) -> Unit) {
    val data = h.data
    val prog = data.activeProgram?.let { SportData.program(it) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var dayPick by rememberSaveable { mutableStateOf(-1) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var remind by remember { mutableStateOf(false) }
    val w = data.workoutOn(today)
    val extras = w.items.filter { it.sets.isEmpty() }.map { it.exerciseId }
    val done = w.items.filter { it.sets.isNotEmpty() }

    if (prog == null || choosing) {
        ProgramPicker(
            current = prog?.id,
            onPick = { p ->
                h.update { it.copy(activeProgram = p.id, programDay = 0) }
                dayPick = -1
                choosing = false
            },
            onCancel = if (prog != null) ({ choosing = false }) else null
        )
    } else {
        val dayIdx = (if (dayPick in prog.days.indices) dayPick else data.programDay) % prog.days.size
        val day = prog.days[dayIdx]
        val steps = remember(prog.id, dayIdx) { WorkoutPlan.fromDay(day) }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تمرین امروز", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                Text(day.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${prog.name} · ${Fa.num(steps.size)} حرکت · حدود ${Fa.num(WorkoutPlan.estimateMinutes(steps))} دقیقه",
                    style = MaterialTheme.typography.bodyMedium, color = Pal.muted
                )
                if (prog.days.size > 1) {
                    ChipRow { prog.days.forEachIndexed { i, d -> AppChip(d.title, i == dayIdx, { dayPick = i }) } }
                }
                steps.forEach { st ->
                    Text("• ${st.name} — ${stepLabel(st)}", style = MaterialTheme.typography.bodySmall)
                }
                AppButton(
                    "شروع تمرین", { start(TrainSession(day.title, steps, prog.id, dayIdx)) },
                    Modifier.fillMaxWidth(), icon = R.drawable.ic_play
                )
            }
        }
        TextButton({ choosing = true }) { Text("تغییر برنامه") }
    }

    if (extras.isNotEmpty()) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حرکات انتخابی امروز", style = MaterialTheme.typography.titleSmall)
                extras.forEach { id ->
                    Text("• ${SportData.exercise(id)?.name ?: id}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        "شروع این حرکات",
                        { start(TrainSession("حرکات امروز", WorkoutPlan.fromExercises(extras), null, -1)) },
                        Modifier.weight(1f), icon = R.drawable.ic_play, style = BtnStyle.Tonal
                    )
                    AppButton("پاک کردن", {
                        h.update { d ->
                            val cur = d.workoutOn(today)
                            d.withWorkout(cur.copy(items = cur.items.filter { it.sets.isNotEmpty() }))
                        }
                    }, style = BtnStyle.Outline)
                }
            }
        }
    }

    if (done.isNotEmpty()) {
        val n = done.sumOf { it.sets.size }
        HintText(
            "امروز ${Fa.num(n)} ست انجام شده: " + done.joinToString("، ") { SportData.exercise(it.exerciseId)?.name ?: it.exerciseId },
            color = Pal.green
        )
    }

    ShiftWeekStrip(
        vm, restTip = "امروز روز استراحتی؛ بهترین وقت برای تمرین کامل است.",
        workTip = "امروز شیفت داری؛ تمرین سبک‌تر یا کوتاه‌تر بزن و بعد از شیفت شب تمرین سنگین نکن.",
        compact = true
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("یادآور تمرین", { remind = true }, Modifier.weight(1f), icon = R.drawable.ic_alarm, style = BtnStyle.Outline)
        AppButton(if (manual) "بستن ثبت دستی" else "ثبت دستی", { manual = !manual }, Modifier.weight(1f), style = BtnStyle.Outline)
    }
    if (manual) ManualLog(h, today)
    if (remind) ReminderDialog(vm, "وقت تمرین", onClose = { remind = false })
}

@Composable
private fun ProgramPicker(current: String?, onPick: (SportData.Program) -> Unit, onCancel: (() -> Unit)?) {
    Text(if (current == null) "یک برنامه انتخاب کن" else "تغییر برنامه", style = MaterialTheme.typography.titleMedium)
    HintText("بعد از انتخاب، هر روز فقط «شروع تمرین» را می‌زنی و برنامه قدم‌به‌قدم، با شمارش ست و تایمر استراحت همراهت است.")
    SportData.PROGRAMS.forEach { p ->
        AppCard(Modifier.fillMaxWidth(), onClick = { onPick(p) }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(R.drawable.ic_home_sport, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${SPORT_LEVELS[p.level]} · ${Fa.num(p.daysPerWeek)} روز در هفته · ${p.where}",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                    Text(p.about, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (p.id == current) Ico(R.drawable.ic_check_circle, size = 22.dp, tint = Pal.green, desc = "برنامهٔ فعال")
            }
        }
    }
    if (onCancel != null) TextButton(onCancel) { Text("بازگشت") }
}

// ───────────────────────── ثبت دستی تمرین امروز ─────────────────────────

@Composable
private fun ManualLog(h: SportHolder, today: Int) {
    val w = h.data.workoutOn(today)
    var picker by remember { mutableStateOf(false) }
    var setFor by remember { mutableStateOf<String?>(null) }
    var removeEx by remember { mutableStateOf<String?>(null) }
    Text("تمرین امروز — ${dayText(today)}", style = MaterialTheme.typography.titleMedium)
    RestTimer()

    if (w.items.isEmpty()) {
        EmptyState(
            R.drawable.ic_home_sport, "تمرین امروز خالی است",
            text = "حرکت را از تب «حرکات» یا یک روز از تب «برنامه» اضافه کنید."
        )
    }
    w.items.forEach { item ->
        val ex = SportData.exercise(item.exerciseId)
        val unit = WorkoutPlan.unitOf(ex)
        val last = remember(h.data.workouts, item.exerciseId) {
            h.data.workouts.lastOrNull { it.dayEpoch < today && it.items.any { x -> x.exerciseId == item.exerciseId && x.sets.isNotEmpty() } }
                ?.let { day -> day.dayEpoch to day.items.first { x -> x.exerciseId == item.exerciseId }.sets }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ex?.name ?: item.exerciseId, style = MaterialTheme.typography.titleSmall)
                        if (ex != null) Text("پیشنهاد: ${ex.sets}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    }
                    SmallIconButton(R.drawable.ic_delete, "حذف حرکت", { removeEx = item.exerciseId }, tint = MaterialTheme.colorScheme.error)
                }
                if (last != null) {
                    Text(
                        "دفعهٔ قبل (${dayText(last.first)}): " + last.second.joinToString(" ، ") { setText(it, unit) },
                        style = MaterialTheme.typography.bodySmall, color = Pal.amber
                    )
                }
                item.sets.forEachIndexed { i, s ->
                    Text("ست ${Fa.num(i + 1)}: ${setText(s, unit)}", style = MaterialTheme.typography.bodyMedium)
                }
                val best = item.sets.filter { it.kg > 0 }.maxOfOrNull { SportCalc.oneRepMax(it.kg, it.reps) }
                if (best != null) HintText("حداکثر تخمینی یک‌تکرار: ${kgText((best * 10).roundToInt() / 10.0)} کیلو")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(if (unit != WorkoutPlan.UNIT_REPS) "ثبت زمان" else "ثبت ست", { setFor = item.exerciseId }, Modifier.weight(1f), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
                    if (item.sets.isNotEmpty()) AppButton("حذف آخرین", {
                        h.update { d -> d.withWorkout(w.copy(items = w.items.map { x -> if (x.exerciseId == item.exerciseId) x.copy(sets = x.sets.dropLast(1)) else x })) }
                    }, style = BtnStyle.Outline)
                }
            }
        }
    }
    AppButton("افزودن حرکت", { picker = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)

    if (picker) ExercisePickerDialog(
        onPick = { id -> h.update { it.addExercises(today, listOf(id)) }; picker = false },
        onClose = { picker = false }
    )
    setFor?.let { id ->
        val ex = SportData.exercise(id)
        val unit = WorkoutPlan.unitOf(ex)
        val prev = w.items.firstOrNull { it.exerciseId == id }?.sets?.lastOrNull()
        SetDialog(unit, prev, onClose = { setFor = null }) { reps, kg ->
            h.update { d -> d.withWorkout(w.copy(items = w.items.map { x -> if (x.exerciseId == id) x.copy(sets = x.sets + SetLog(reps, kg)) else x })) }
            setFor = null
        }
    }
    removeEx?.let { id ->
        ConfirmDialog("این حرکت و ستهای ثبت‌شدهٔ آن از تمرین امروز حذف شود؟", "حذف", {
            h.update { d -> d.withWorkout(w.copy(items = w.items.filter { it.exerciseId != id })) }
            removeEx = null
        }, { removeEx = null }, danger = true)
    }
}

@Composable
private fun SetDialog(unit: Int, prev: SetLog?, onClose: () -> Unit, onOk: (Int, Double) -> Unit) {
    var reps by remember { mutableStateOf(prev?.reps?.toString() ?: "") }
    var kg by remember { mutableStateOf(if (prev != null && prev.kg > 0) kgText(prev.kg).let { Fa.latin(it).replace('٫', '.') } else "") }
    val r = parseNumber(reps)?.roundToInt() ?: 0
    val k = parseNumber(kg) ?: 0.0
    AppDialog(
        if (unit != WorkoutPlan.UNIT_REPS) "ثبت زمان" else "ثبت ست", onClose, confirmText = "ثبت", onConfirm = { onOk(r, k) },
        dismissText = "انصراف", confirmEnabled = r > 0
    ) {
        AppField(reps, { reps = it }, when (unit) { WorkoutPlan.UNIT_MIN -> "مدت (دقیقه)"; WorkoutPlan.UNIT_SEC -> "مدت (ثانیه)"; else -> "تکرار" }, keyboard = KeyboardType.Number)
        if (unit == WorkoutPlan.UNIT_REPS) AppField(kg, { kg = it }, "وزنه (کیلوگرم) — برای حرکات بدون وزنه خالی بگذارید", keyboard = KeyboardType.Decimal)
    }
}

@Composable
private fun ExercisePickerDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(-1) }
    val list = remember(q, muscle) {
        SportData.EXERCISES.filter { (muscle == -1 || it.muscle == muscle) && (q.isBlank() || it.name.contains(q.trim())) }
    }
    AppDialog("انتخاب حرکت", onClose, dismissText = "بستن") {
        AppField(q, { q = it }, "جست‌وجو")
        ChipRow {
            AppChip("همه", muscle == -1, { muscle = -1 })
            SPORT_MUSCLES.forEachIndexed { i, n -> AppChip(n, muscle == i, { muscle = i }) }
        }
        list.forEach { e ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onPick(e.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                    Text("${muscleName(e.muscle)} · ${SPORT_EQUIPMENT[e.equipment]}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
            }
        }
    }
}

/** تایمر استراحت بین ست‌ها؛ با پایان زمان یک لرزش کوتاه می‌دهد. */
@Composable
private fun RestTimer() {
    var left by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(running) {
        if (running) {
            while (left > 0) { delay(1000); left-- }
            running = false
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(R.drawable.ic_timer, size = 36.dp, iconSize = 20.dp, circle = true)
                Text("تایمر استراحت", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (running || left > 0) Fa.digits("%d:%02d".format(left / 60, left % 60)) else "آماده",
                    style = MaterialTheme.typography.titleMedium, color = if (running) MaterialTheme.colorScheme.primary else Pal.muted
                )
            }
            ChipRow {
                listOf(45, 60, 90, 120, 180).forEach { s ->
                    AppChip(Fa.num(s) + " ثانیه", false, { left = s; running = true })
                }
                if (running) AppChip("توقف", false, { running = false; left = 0 })
            }
        }
    }
}

// ───────────────────────── تب رژیم ─────────────────────────

@Composable
private fun DietTab(h: SportHolder, today: Int) {
    val p = h.data.profile
    fun setProfile(f: (SportProfile) -> SportProfile) = h.update { it.copy(profile = f(it.profile), profileSet = true) }

    key(h.epoch) {
        SettingsGroup("مشخصات من", icon = R.drawable.ic_people) {
            Segmented(listOf("مرد", "زن"), p.sex) { v -> setProfile { it.copy(sex = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumInput("سن", p.age.toString(), Modifier.weight(1f)) { v -> if (v in 10.0..100.0) setProfile { it.copy(age = v.roundToInt()) } }
                NumInput("قد (سانتی‌متر)", p.heightCm.toString(), Modifier.weight(1f)) { v -> if (v in 100.0..230.0) setProfile { it.copy(heightCm = v.roundToInt()) } }
            }
            NumInput("وزن (کیلوگرم)", kgText(p.weightKg).let { Fa.latin(it).replace('٫', '.') }) { v -> if (v in 25.0..300.0) setProfile { it.copy(weightKg = v) } }
            Text("سطح فعالیت روزانه", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
            ChipRow { SportCalc.ACTIVITY_NAMES.forEachIndexed { i, n -> AppChip(n, p.activity == i, { setProfile { it.copy(activity = i) } }) } }
            Text("هدف", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
            Segmented(SportCalc.GOAL_NAMES, p.goal) { v -> setProfile { it.copy(goal = v) } }
        }
    }

    val bmi = SportCalc.bmi(p.weightKg, p.heightCm.toDouble())
    val bmr = SportCalc.bmr(p.sex, p.age, p.heightCm.toDouble(), p.weightKg)
    val tdee = SportCalc.tdee(bmr, p.activity)
    val target = SportCalc.targetCalories(tdee, p.goal, p.sex)
    val macros = SportCalc.macros(target, p.weightKg, p.goal)
    val range = SportCalc.healthyRange(p.heightCm.toDouble())
    val waterGlasses = (SportCalc.waterMl(p.weightKg) / 250.0).roundToInt()

    if (!h.data.profileSet) HintText("مقادیر پیش‌فرض است؛ مشخصات خودتان را وارد کنید تا اعداد دقیق‌تر شوند.", color = Pal.amber)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("BMI (${SportCalc.bmiLabel(bmi)})", Fa.digits(String.format(Locale.US, "%.1f", bmi)), R.drawable.ic_chart, Modifier.weight(1f))
        StatTile("کالری هدف روزانه", Fa.num(target), R.drawable.ic_pie, Modifier.weight(1f), tint = Pal.coral)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("متابولیسم پایه", Fa.num(bmr.roundToInt()), R.drawable.ic_today, Modifier.weight(1f), tint = Pal.green)
        StatTile("کالری نگه‌داشتن وزن", Fa.num(tdee.roundToInt()), R.drawable.ic_timer, Modifier.weight(1f), tint = Pal.amber)
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("سهم پیشنهادی درشت‌مغذی‌ها", style = MaterialTheme.typography.titleSmall)
            Text("پروتئین ${Fa.num(macros.protein)} گرم · کربوهیدرات ${Fa.num(macros.carb)} گرم · چربی ${Fa.num(macros.fat)} گرم", style = MaterialTheme.typography.bodyMedium)
            HintText("بازهٔ وزن سالم برای قد شما حدود ${Fa.num(range.first)} تا ${Fa.num(range.second)} کیلوگرم است.")
            if (p.goal == 0) HintText("کاهش وزن ایمن حدود نیم تا یک کیلو در هفته است؛ کالری هدف هرگز کمتر از حداقل ایمن محاسبه نمی‌شود.")
        }
    }

    // مصرف امروز
    val meals = h.data.mealsOn(today)
    val totals = meals.fold(doubleArrayOf(0.0, 0.0, 0.0, 0.0)) { a, m ->
        SportData.food(m.foodId)?.let { f ->
            val r = m.grams / 100.0
            a[0] += f.kcal * r; a[1] += f.protein * r; a[2] += f.carb * r; a[3] += f.fat * r
        }
        a
    }
    var addMeal by remember { mutableStateOf(false) }
    SettingsGroup("مصرف امروز — ${dayText(today)}", icon = R.drawable.ic_home_meal) {
        val ratio = if (target > 0) (totals[0] / target).toFloat().coerceIn(0f, 1f) else 0f
        Text("${Fa.num(totals[0].roundToInt())} از ${Fa.num(target)} کیلوکالری", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
        HintText("پروتئین ${Fa.num(totals[1].roundToInt())} از ${Fa.num(macros.protein)} · کربوهیدرات ${Fa.num(totals[2].roundToInt())} از ${Fa.num(macros.carb)} · چربی ${Fa.num(totals[3].roundToInt())} از ${Fa.num(macros.fat)} گرم")
        meals.forEach { m ->
            val f = SportData.food(m.foodId) ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyMedium)
                    Text("${Fa.num(m.grams)} گرم · ${Fa.num((f.kcal * m.grams / 100.0).roundToInt())} کیلوکالری", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
                SmallIconButton(R.drawable.ic_delete, "حذف", { h.update { d -> d.copy(meals = d.meals - m) } }, tint = MaterialTheme.colorScheme.error)
            }
        }
        AppButton("افزودن خوراکی", { addMeal = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    }
    if (addMeal) FoodDialog(onClose = { addMeal = false }) { id, g ->
        h.update { d -> d.copy(meals = d.meals + MealLog(today, id, g)) }
        addMeal = false
    }

    // آب
    val glasses = h.data.waterOn(today)
    SettingsGroup("آب امروز", icon = R.drawable.ic_sun) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallIconButton(R.drawable.ic_remove, "یک لیوان کمتر", { if (glasses > 0) h.update { d -> d.copy(water = d.water + (today to glasses - 1)) } })
            Text(
                "${Fa.num(glasses)} از ${Fa.num(waterGlasses)} لیوان", Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center
            )
            SmallIconButton(R.drawable.ic_add, "یک لیوان بیشتر", { h.update { d -> d.copy(water = d.water + (today to glasses + 1)) } }, tint = MaterialTheme.colorScheme.primary)
        }
        HintText("هر لیوان ۲۵۰ میلی‌لیتر؛ هدف پیشنهادی حدود ${Fa.num(SportCalc.waterMl(p.weightKg))} میلی‌لیتر در روز.")
    }
    HintText("ارزش غذایی خوراکی‌ها تقریبی است و به نوع پخت و مارک بستگی دارد.")
}

@Composable
private fun FoodDialog(onClose: () -> Unit, onOk: (String, Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<SportData.Food?>(null) }
    var grams by remember { mutableStateOf("") }
    val f = picked
    if (f == null) {
        val list = remember(q) { SportData.FOODS.filter { q.isBlank() || it.name.contains(q.trim()) } }
        AppDialog("انتخاب خوراکی", onClose, dismissText = "بستن") {
            AppField(q, { q = it }, "جست‌وجو")
            list.forEach { x ->
                AppCard(Modifier.fillMaxWidth(), onClick = { picked = x; grams = x.servingG.toString() }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(x.name, style = MaterialTheme.typography.titleSmall)
                        Text("هر ۱۰۰ گرم: ${Fa.num(x.kcal)} کیلوکالری · پروتئین ${kgText(x.protein)}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    }
                }
            }
        }
    } else {
        val g = parseNumber(grams)?.roundToInt() ?: 0
        AppDialog(
            f.name, { picked = null }, confirmText = "افزودن", onConfirm = { onOk(f.id, g) },
            dismissText = "برگشت", confirmEnabled = g in 1..3000
        ) {
            AppField(grams, { grams = it }, "مقدار (گرم)", keyboard = KeyboardType.Number)
            HintText("یک وعده: ${f.servingName} ≈ ${Fa.num(f.servingG)} گرم")
            if (g > 0) Text("≈ ${Fa.num((f.kcal * g / 100.0).roundToInt())} کیلوکالری", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ───────────────────────── تب پیشرفت ─────────────────────────

@Composable
private fun ProgressTab(h: SportHolder, today: Int) {
    val d = h.data
    val weekAgo = today - 6
    val sessions = d.workouts.filter { w -> w.items.any { it.sets.isNotEmpty() } }
    val week = sessions.filter { it.dayEpoch >= weekAgo }
    fun volume(w: WorkoutLog) = w.items.sumOf { x -> x.sets.sumOf { s -> SportCalc.volume(s.kg, s.reps) } }
    val weekVolume = week.sumOf { volume(it) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("جلسه در ۷ روز اخیر", Fa.num(week.size), R.drawable.ic_home_sport, Modifier.weight(1f))
        StatTile("کل جلسه‌ها", Fa.num(sessions.size), R.drawable.ic_check_circle, Modifier.weight(1f), tint = Pal.green)
    }
    StatTile("حجم تمرین این هفته (کیلوگرم)", Fa.num(weekVolume.roundToInt()), R.drawable.ic_chart, Modifier.fillMaxWidth(), tint = Pal.coral)

    // وزن بدن
    SettingsGroup("وزن بدن", icon = R.drawable.ic_chart) {
        var w by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(w, { w = it }, "وزن امروز (کیلوگرم)", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            val v = parseNumber(w) ?: 0.0
            AppButton("ثبت", {
                h.update { s -> s.copy(weights = (s.weights.filter { it.dayEpoch != today } + WeightLog(today, v)).sortedBy { it.dayEpoch }, profile = s.profile.copy(weightKg = v)) }
                w = ""
            }, enabled = v in 25.0..300.0)
        }
        val pts = d.weights.takeLast(30)
        if (pts.size >= 2) {
            WeightChart(pts.map { it.kg })
            val diff = pts.last().kg - pts.first().kg
            val sign = if (diff > 0) "+" else if (diff < 0) "−" else ""
            Text("تغییر از ${dayText(pts.first().dayEpoch)}: $sign${kgText(Math.round(Math.abs(diff) * 10) / 10.0)} کیلوگرم", style = MaterialTheme.typography.bodyMedium)
        } else if (pts.size == 1) {
            Text("آخرین وزن: ${kgText(pts[0].kg)} کیلوگرم (${dayText(pts[0].dayEpoch)})", style = MaterialTheme.typography.bodyMedium)
            HintText("برای رسم نمودار حداقل دو بار وزن ثبت کنید.")
        } else HintText("هنوز وزنی ثبت نشده است. بهتر است هفته‌ای یک یا دو بار و صبح ناشتا وزن کنید.")
    }

    // رکوردها
    val records = remember(d.workouts) {
        val best = HashMap<String, Pair<Double, SetLog>>()
        d.workouts.forEach { wk -> wk.items.forEach { x -> x.sets.filter { it.kg > 0 }.forEach { s ->
            val e = SportCalc.oneRepMax(s.kg, s.reps)
            if (e > (best[x.exerciseId]?.first ?: 0.0)) best[x.exerciseId] = e to s
        } } }
        best.entries.sortedByDescending { it.value.first }.take(5)
    }
    if (records.isNotEmpty()) {
        SettingsGroup("رکوردهای من", icon = R.drawable.ic_star) {
            records.forEach { (id, v) ->
                Row {
                    Text(SportData.exercise(id)?.name ?: id, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${setText(v.second, WorkoutPlan.UNIT_REPS)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // تاریخچه
    SettingsGroup("جلسه‌های اخیر", icon = R.drawable.ic_event) {
        if (sessions.isEmpty()) HintText("هنوز تمرینی ثبت نشده است.")
        sessions.takeLast(8).reversed().forEach { s ->
            val names = s.items.filter { it.sets.isNotEmpty() }.joinToString("، ") { SportData.exercise(it.exerciseId)?.name ?: it.exerciseId }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${dayText(s.dayEpoch)} · حجم ${Fa.num(volume(s).roundToInt())} کیلو", style = MaterialTheme.typography.titleSmall)
                Text(names, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
        }
    }
}

@Composable
private fun WeightChart(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = Pal.hairline
    val lo = values.min()
    val hi = values.max()
    val span = if (hi - lo < 0.5) 0.5 else hi - lo
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val padV = 12.dp.toPx()
        val w = size.width
        val h = size.height - 2 * padV
        drawLine(grid, Offset(0f, size.height - padV), Offset(w, size.height - padV), 1.dp.toPx())
        drawLine(grid, Offset(0f, padV), Offset(w, padV), 1.dp.toPx())
        val step = if (values.size > 1) w / (values.size - 1) else 0f
        val pts = values.mapIndexed { i, v -> Offset(i * step, padV + h * (1f - ((v - lo) / span).toFloat())) }
        for (i in 0 until pts.size - 1) drawLine(line, pts[i], pts[i + 1], 3.dp.toPx())
        pts.forEach { drawCircle(line, 4.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("کمترین ${kgText(lo)}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        Spacer(Modifier.weight(1f))
        Text("بیشترین ${kgText(hi)}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
    }
}
