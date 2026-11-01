package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.*
import java.time.LocalDate
import java.time.ZoneId

/*
 * ذکرشمار، رکعت‌شمار، نماز و روزهٔ قضا و کارت رمضان.
 * فقط وقتی «امکانات مذهبی» روشن است در کشوی «بخش‌های دیگر» دیده می‌شود. همه‌چیز آفلاین و روی همین گوشی است.
 * وضعیت (ذکرها، قضا) در کلید pref_dhikr ذخیره می‌شود؛ رکعت‌شمار موقتی است و ذخیره نمی‌شود.
 */

@Composable
fun DhikrSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val holder = remember { RawHolder(DhikrState(), DhikrCodec::encode, DhikrCodec::decode) { vm.setPref(PrefKeys.DHIKR, it) } }
    LaunchedEffect(prefs.dhikrRaw) { holder.sync(prefs.dhikrRaw) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ToolScaffold("ذکر، قضا و رمضان", "شمارنده‌ها و حساب شخصی؛ آفلاین", onHome) {
        RamadanCard(prefs)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ذکرشمار", "رکعت‌شمار", "قضا").forEachIndexed { i, label ->
                AppButton(label, { tab = i }, Modifier.weight(1f), style = if (tab == i) BtnStyle.Filled else BtnStyle.Outline)
            }
        }
        when (tab) {
            0 -> DhikrTab(holder)
            1 -> RakatTab()
            else -> QazaTab(holder)
        }
    }
}

// ───────────────────────── رمضان ─────────────────────────

@Composable
private fun RamadanCard(prefs: Prefs) {
    val now = rememberNowMs()
    val zone = remember { ZoneId.systemDefault() }
    val pl = prefs.prayerPlace
    val status = remember(now / 60_000L, prefs.prayerCity, prefs.prayerLat, prefs.prayerLon, prefs.prayerMethodId, prefs.lunarShift) {
        Ramadan.status(now, zone, pl.lat, pl.lon, prefs.prayerMethod, prefs.lunarShift)
    }
    if (status != null) {
        val today = LocalDate.now(zone)
        val times = remember(today, pl.lat, pl.lon, prefs.prayerMethodId) { PrayerCalc.times(today, pl.lat, pl.lon, zone, prefs.prayerMethod) }
        val sahar = times.minutes[Prayer.FAJR]?.let { Fa.digits(ShiftTime.fmt(it % 1440)) }
        val iftar = times.minutes[Prayer.MAGHRIB]?.let { Fa.digits(ShiftTime.fmt(it % 1440)) }
        AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ماه رمضان — روز ${Fa.num(status.day)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Ramadan.title(status), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                status.targetMs?.let {
                    Text(
                        PrayerRules.remaining(it - now), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (sahar != null && iftar != null) {
                    Text("اذان صبح (پایان سحر) $sahar · اذان مغرب (افطار) $iftar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        HintText("وقت‌ها از همان محاسبهٔ آفلاین اوقات شرعی (${pl.name}) می‌آیند و ممکن است یکی‌دو دقیقه با تقویم رسمی فرق کنند. روز ماه به تنظیم تقویم قمری شما بستگی دارد.")
    } else {
        val d = remember { Ramadan.daysUntil(LocalDate.now(zone), prefs.lunarShift) }
        if (d != null) AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("${Fa.num(d)} روز تا ماه رمضان", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

// ───────────────────────── ذکرشمار ─────────────────────────

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DhikrTab(h: RawHolder<DhikrState>) {
    val s = h.data
    val epoch = LocalDate.now().toEpochDay().toInt()
    val c = s.current()
    val (phaseIdx, inPhase) = c.position
    val phase = c.phases[phaseIdx]
    val haptic = LocalHapticFeedback.current
    var addOpen by remember { mutableStateOf(false) }
    var delAsk by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        s.counters().forEach { k -> Pill(k.title, k.id == c.id) { h.update { it.select(k.id) } } }
        Pill("+ ذکر دلخواه", false) { addOpen = true }
    }

    AppCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(c.title, style = MaterialTheme.typography.labelLarge, color = Pal.muted)
            Text(phase.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (c.phases.size > 1) HintText("مرحلهٔ ${Fa.num(phaseIdx + 1)} از ${Fa.num(c.phases.size)}")

            Box(
                Modifier.size(200.dp).clip(CircleShape)
                    .background(if (c.done) Pal.green else MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClickLabel = "یک شمارش") {
                        val before = h.data.current()
                        h.update { it.tap(epoch) }
                        val after = h.data.current()
                        if (!before.done) {
                            val phaseEnded = after.position.first != before.position.first
                            haptic.performHapticFeedback(
                                if (after.done || phaseEnded) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Fa.num(if (c.done) phase.target else inPhase), style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("از ${Fa.num(phase.target)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            LinearProgressIndicator(progress = { c.count.toFloat() / c.total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(
                if (c.done) "ذکر کامل شد" else "${Fa.num(c.count)} از ${Fa.num(c.total)}",
                style = MaterialTheme.typography.titleSmall, color = if (c.done) Pal.green else MaterialTheme.colorScheme.onSurface
            )
            HintText("امروز ${Fa.num(s.todayCount(epoch))} شمارش · مجموع ${Fa.digits(s.lifetime.toString())}")
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("یکی برگرد", { h.update { it.undo(epoch) } }, Modifier.weight(1f), style = BtnStyle.Outline, enabled = c.count > 0)
        AppButton(if (c.done) "دوباره شروع کن" else "صفر کردن", { h.update { it.reset(c.id) } }, Modifier.weight(1f), style = BtnStyle.Outline)
    }
    if (c.custom) AppButton("حذف این ذکر", { delAsk = true }, Modifier.fillMaxWidth(), style = BtnStyle.Danger)
    HintText("روی دایره بزنید. در پایان هر مرحله و کل ذکر، گوشی کمی لرزش می‌دهد.")

    if (addOpen) AddDhikrDialog({ addOpen = false }) { title, target -> h.update { it.addCustom(title, target) }; addOpen = false }
    if (delAsk) ConfirmDialog(
        "«${c.title}» حذف شود؟", "حذف", { h.update { it.removeCustom(c.id) }; delAsk = false }, { delAsk = false }, danger = true
    )
}

@Composable
private fun AddDhikrDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("100") }
    val n = Fa.latin(target).trim().toIntOrNull()
    val valid = title.trim().isNotEmpty() && n != null && n in 1..DhikrPresets.MAX_TARGET
    AppDialog(
        "ذکر دلخواه", onDismiss, confirmText = "افزودن", onConfirm = { if (valid && n != null) onAdd(title, n) },
        dismissText = "انصراف", onDismissClick = onDismiss, confirmEnabled = valid
    ) {
        AppField(title, { title = it.take(40) }, "متن ذکر")
        AppField(target, { target = it.take(6) }, "تعداد (۱ تا ${Fa.num(DhikrPresets.MAX_TARGET)})", keyboard = KeyboardType.Number)
    }
}

// ───────────────────────── رکعت‌شمار ─────────────────────────

@Composable
private fun RakatTab() {
    var total by rememberSaveable { mutableIntStateOf(2) }
    var done by rememberSaveable { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(2, 3, 4).forEach { n ->
            AppButton("${Fa.num(n)} رکعتی", { total = n; done = 0 }, Modifier.weight(1f), style = if (total == n) BtnStyle.Tonal else BtnStyle.Outline)
        }
    }
    AppCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(rakatHint(done, total), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Box(
                Modifier.size(200.dp).clip(CircleShape)
                    .background(if (done >= total) Pal.green else MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClickLabel = "رکعت بعد") {
                        if (done < total) {
                            done++
                            haptic.performHapticFeedback(if (done >= total) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fa.num(done), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text("از ${Fa.num(total)} رکعت", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("یکی برگرد", { if (done > 0) done-- }, Modifier.weight(1f), style = BtnStyle.Outline, enabled = done > 0)
        AppButton("از اول", { done = 0 }, Modifier.weight(1f), style = BtnStyle.Outline)
    }
    HintText("بعد از سجدهٔ دوم هر رکعت، یک بار روی دایره بزنید. این شمارنده فقط کمک حافظه است و ذخیره نمی‌شود.")
}

private fun rakatHint(done: Int, total: Int): String = when {
    done <= 0 -> "نیت و تکبیرة‌الاحرام؛ بعد از هر رکعت یک بار بزنید"
    done >= total -> "تمام شد: تشهد، سلام و تعقیبات"
    done == 2 -> "رکعت دوم تمام شد: تشهد و برخاستن برای رکعت ${Fa.num(done + 1)}"
    else -> "رکعت ${Fa.num(done)} تمام شد؛ برخیزید برای رکعت ${Fa.num(done + 1)}"
}

// ───────────────────────── قضا ─────────────────────────

@Composable
private fun StepBox(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else Pal.muted
        )
    }
}

@Composable
private fun QazaTab(h: RawHolder<DhikrState>) {
    val s = h.data
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (s.qazaPrayerTotal == 0) "نمازِ قضای ثبت‌شده‌ای ندارید" else "${Fa.num(s.qazaPrayerTotal)} نماز قضا مانده است",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            Qaza.KEYS.forEach { k ->
                val n = s.qazaOf(k)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Qaza.title(k), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    StepBox("−", n > 0) { h.update { it.qazaAdd(k, -1) } }
                    Text(Fa.num(n), Modifier.width(64.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    StepBox("+", n < Qaza.MAX) { h.update { it.qazaAdd(k, 1) } }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton("یک روز کامل اضافه", { h.update { it.qazaDay(1) } }, Modifier.weight(1f), style = BtnStyle.Tonal)
        AppButton("یک روز کامل ادا شد", { h.update { it.qazaDay(-1) } }, Modifier.weight(1f), style = BtnStyle.Outline, enabled = s.qazaPrayerTotal > 0)
    }
    HintText("«یک روز کامل» پنج نماز را با هم کم یا زیاد می‌کند. این فقط یادداشت شخصی شماست؛ احکام و تعداد دقیق را از مرجع تقلید خود بپرسید.")
}
