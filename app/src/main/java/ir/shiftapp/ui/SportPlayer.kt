package ir.shiftapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.ExerciseAnims
import ir.shiftapp.core.Fa
import ir.shiftapp.core.PhaseKind
import ir.shiftapp.core.PlanStep
import ir.shiftapp.core.SportCalc
import ir.shiftapp.core.SportData
import ir.shiftapp.core.WorkoutPlan
import ir.shiftapp.sport.SetLog
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** بوق کوتاه برای ثانیه‌های آخر و پایان هر مرحله؛ اگر دستگاه اجازه ندهد بی‌صدا می‌ماند. */
private class Beeper {
    private val tg: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (e: Exception) { null }
    fun tick() { try { tg?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) } catch (e: Exception) { } }
    fun end() { try { tg?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) } catch (e: Exception) { } }
    fun release() { try { tg?.release() } catch (e: Exception) { } }
}

private fun clock(s: Int): String = Fa.digits(String.format(Locale.US, "%d:%02d", s / 60, s % 60))

private fun targetText(st: PlanStep): String {
    val base = when {
        st.timed && st.seconds >= 60 && st.seconds % 60 == 0 -> "${Fa.num(st.seconds / 60)} دقیقه"
        st.timed -> "${Fa.num(st.seconds)} ثانیه"
        else -> "${Fa.num(st.reps)} تکرار"
    }
    return if (st.perSide) "$base برای هر طرف" else base
}

/**
 * تمرین راهنما: مثل اپ‌های ورزشی حرکت‌ها را یکی‌یکی نشان می‌دهد، ستها را می‌شمارد،
 * استراحت را با تایمر و بوق مدیریت می‌کند و هر ست انجام‌شده را همان لحظه ذخیره می‌کند.
 */
@Composable
internal fun WorkoutPlayer(
    title: String,
    steps: List<PlanStep>,
    startKg: (String) -> Double,
    onSet: (String, SetLog) -> Unit,
    onFinish: (Boolean) -> Unit
) {
    val phases = remember(steps) { WorkoutPlan.phases(steps) }
    var idx by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var confirmExit by remember { mutableStateOf(false) }
    var bump by remember { mutableIntStateOf(0) }
    var doneSets by remember { mutableIntStateOf(0) }
    var volume by remember { mutableDoubleStateOf(0.0) }
    val startedAt = remember { System.currentTimeMillis() }
    val kgMap = remember { mutableStateMapOf<String, Double>() }
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val beeper = remember { Beeper() }

    DisposableEffect(Unit) {
        val win = ctx.findActivity()?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            beeper.release()
        }
    }

    val finished = idx >= phases.size
    val phase = phases.getOrNull(idx)
    var remaining by remember(idx) { mutableIntStateOf(phase?.duration ?: 0) }

    BackHandler { if (finished) onFinish(true) else confirmExit = true }

    fun complete(p: ir.shiftapp.core.Phase, elapsed: Int) {
        if (p.kind == PhaseKind.WORK) {
            val st = steps[p.step]
            val ex = SportData.exercise(st.exerciseId)
            val value = WorkoutPlan.logValue(st, ex, elapsed)
            val kg = if (st.timed) 0.0 else (kgMap[st.exerciseId] ?: startKg(st.exerciseId))
            onSet(st.exerciseId, SetLog(value, kg))
            doneSets += 1
            volume += SportCalc.volume(kg, value)
        }
        if (sound) beeper.end()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        paused = false
        idx += 1
    }

    fun skipExercise() {
        val cur = phase?.step ?: return
        var j = idx
        while (j < phases.size && !(phases[j].kind == PhaseKind.WORK && phases[j].step > cur)) j++
        paused = false
        idx = j
    }

    LaunchedEffect(idx, paused, bump) {
        val p = phases.getOrNull(idx) ?: return@LaunchedEffect
        if (p.duration <= 0 || paused) return@LaunchedEffect
        val endAt = SystemClock.elapsedRealtime() + remaining * 1000L
        var shown = remaining
        while (true) {
            val left = ((endAt - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
            if (left != shown) {
                shown = left
                remaining = left
                if (left in 1..3 && sound) beeper.tick()
            }
            if (left <= 0) break
            delay(100)
        }
        complete(p, p.duration)
    }

    val subtitle = when {
        finished -> "پایان تمرین"
        phase?.kind == PhaseKind.READY -> "آماده‌باش"
        else -> "حرکت ${Fa.num((phase?.step ?: 0) + 1)} از ${Fa.num(steps.size)}"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = title, leadingIcon = R.drawable.ic_close, leadingDesc = "خروج از تمرین",
                onLeading = { if (finished) onFinish(true) else confirmExit = true },
                subtitle = subtitle,
                actions = { AppChip(if (sound) "صدا روشن" else "صدا خاموش", sound, { sound = !sound }) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(
                progress = { if (phases.isEmpty()) 1f else (idx.toFloat() / phases.size).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (finished || phase == null) {
                    FinishedView(doneSets, volume, startedAt)
                } else when (phase.kind) {
                    PhaseKind.READY -> {
                        Text("آماده شو", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        CountdownRing(remaining, phase.duration, Fa.num(remaining), MaterialTheme.colorScheme.primary)
                        Text("حرکت اول", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                        ExerciseBlock(steps[0], showAnim = true)
                    }
                    PhaseKind.WORK -> {
                        val st = steps[phase.step]
                        val ex = SportData.exercise(st.exerciseId)
                        Text(
                            "ست ${Fa.num(phase.set + 1)} از ${Fa.num(st.sets)}",
                            style = MaterialTheme.typography.titleMedium, color = Pal.muted
                        )
                        SetDots(st.sets, phase.set)
                        ExerciseBlock(st, showAnim = true)
                        if (st.timed) {
                            CountdownRing(
                                remaining, phase.duration, clock(remaining), Pal.coral,
                                sub = if (paused) "متوقف" else null
                            )
                        } else {
                            Text(
                                targetText(st), style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center
                            )
                            if (ex != null && ex.equipment != 0) {
                                val kg = kgMap[st.exerciseId] ?: startKg(st.exerciseId)
                                WeightStepper(kg) { kgMap[st.exerciseId] = it }
                            }
                        }
                        if (ex != null && phase.set == 0) HintText("نکته: ${ex.tip}", color = Pal.amber)
                    }
                    PhaseKind.REST -> {
                        val next = phases.getOrNull(idx + 1)
                        Text("استراحت", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        CountdownRing(remaining, phase.duration.coerceAtLeast(remaining), clock(remaining), Pal.green)
                        if (next != null) {
                            val ns = steps[next.step]
                            Text("بعدی", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                            Text(
                                "${ns.name} · ست ${Fa.num(next.set + 1)} از ${Fa.num(ns.sets)}",
                                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                            )
                            Text(targetText(ns), style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
                            if (next.set == 0) ExerciseBlock(ns, showAnim = true, showName = false)
                        }
                    }
                }
            }
            if (!finished && phase != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (phase.kind) {
                        PhaseKind.READY -> AppButton("همین حالا شروع کن", { idx += 1 }, Modifier.fillMaxWidth(), icon = R.drawable.ic_play)
                        PhaseKind.WORK -> {
                            val st = steps[phase.step]
                            if (st.timed) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppButton(
                                        if (paused) "ادامه" else "توقف", { paused = !paused }, Modifier.weight(1f),
                                        icon = if (paused) R.drawable.ic_play else R.drawable.ic_pause, style = BtnStyle.Tonal
                                    )
                                    AppButton("پایان ست", { complete(phase, phase.duration - remaining) }, Modifier.weight(1f), style = BtnStyle.Outline)
                                }
                            } else {
                                AppButton("ست انجام شد", { complete(phase, 0) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_check)
                            }
                            TextButton({ skipExercise() }, Modifier.align(Alignment.CenterHorizontally)) { Text("رد شدن از این حرکت") }
                        }
                        PhaseKind.REST -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton("۱۵ ثانیه بیشتر", { remaining += 15; bump += 1 }, Modifier.weight(1f), style = BtnStyle.Tonal)
                            AppButton("رد کردن استراحت", { paused = false; idx += 1 }, Modifier.weight(1f), icon = R.drawable.ic_skip_next)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AppButton("بستن", { onFinish(true) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_check)
                }
            }
        }
    }

    if (confirmExit) {
        AppDialog(
            "خروج از تمرین؟", { confirmExit = false }, confirmText = "خروج", onConfirm = { confirmExit = false; onFinish(false) },
            dismissText = "ادامهٔ تمرین"
        ) {
            Text("ستهای انجام‌شده تا اینجا ذخیره می‌مانند.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExerciseBlock(st: PlanStep, showAnim: Boolean, showName: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showName) {
            Text(
                st.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (showAnim) {
            ExerciseAnims.get(st.exerciseId)?.let { ExerciseAnimView(it, st.name, Modifier.fillMaxWidth(0.75f)) }
        }
    }
}

@Composable
private fun SetDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until total) {
            val c = when {
                i < current -> Pal.green
                i == current -> MaterialTheme.colorScheme.primary
                else -> Pal.hairline
            }
            Dot(c, 10.dp)
        }
    }
}

@Composable
private fun WeightStepper(kg: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SmallIconButton(R.drawable.ic_remove, "وزنه کمتر", { onChange((kg - 2.5).coerceAtLeast(0.0)) })
        Text(
            if (kg > 0) "${kgText(kg)} کیلو" else "بدون ثبت وزنه",
            style = MaterialTheme.typography.titleMedium
        )
        SmallIconButton(R.drawable.ic_add, "وزنه بیشتر", { onChange(kg + 2.5) }, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CountdownRing(remaining: Int, total: Int, label: String, color: Color, sub: String? = null) {
    val track = Pal.hairline
    val frac by animateFloatAsState(
        if (total > 0) (remaining.toFloat() / total).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(900, easing = LinearEasing), label = "ring"
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
        }
    }
}

@Composable
private fun FinishedView(doneSets: Int, volume: Double, startedAt: Long) {
    val minutes = remember { ((System.currentTimeMillis() - startedAt) / 60000L).toInt().coerceAtLeast(1) }
    Spacer(Modifier.height(16.dp))
    IconBadge(R.drawable.ic_check_circle, size = 72.dp, iconSize = 40.dp, circle = true)
    Text("آفرین! تمرین تمام شد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    HintText("ستهای انجام‌شده در «پیشرفت» ذخیره شد. یادت نره آب بخوری و کمی کشش بدهی.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("دقیقه", Fa.num(minutes), R.drawable.ic_timer, Modifier.weight(1f))
        StatTile("ست انجام‌شده", Fa.num(doneSets), R.drawable.ic_check_circle, Modifier.weight(1f), tint = Pal.green)
    }
    if (volume > 0) {
        StatTile("حجم تمرین (کیلوگرم)", Fa.num(volume.roundToInt()), R.drawable.ic_chart, Modifier.fillMaxWidth(), tint = Pal.coral)
    }
}
