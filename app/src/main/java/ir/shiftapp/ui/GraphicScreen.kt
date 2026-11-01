package ir.shiftapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.shiftapp.R
import ir.shiftapp.core.*

/**
 * تب گرافیکی: ۱) نوار رنگی ماه برای هر ستون چهارشیفت (یا الگوی انتخاب‌شده)، ۲) نمودار حلقه‌ای سهم شیفت‌ها، ۳) میله‌های ساعت کار.
 */
@Composable
fun GraphicScreen(vm: MainVm) {
    val month by vm.month.collectAsState()
    val sel by vm.selected.collectAsState()
    val slots by vm.slots.collectAsState()
    val patterns by vm.patterns.collectAsState()
    val all by vm.entriesAllPatterns.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    val today = remember { JalaliDate.today() }
    val days = JalaliCalendar.daysInMonth(month.year, month.month)

    val strips = remember(patterns, slots, sel, all, month) {
        val cols = slots.mapNotNull { id -> patterns.firstOrNull { it.id == id } }
        (if (cols.isEmpty()) listOfNotNull(sel) else cols).map { p ->
            val base = p.base()
            val pat = ShiftPattern.parse(p.pattern)
            val patternEntries = all[p.id].orEmpty()
            p.name to (1..days).map { d ->
                val date = JalaliDate(month.year, month.month, d)
                DayResolver.resolve(base, pat, date, patternEntries[date.key].orEmpty())
            }
        }
    }
    val rep = remember(month, sel, entries) { vm.report() }
    val hours = remember(month, sel, entries, prefs, cal) { vm.hours() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonthSwitcher(vm, month)
        if (strips.isEmpty()) {
            EmptyState(R.drawable.ic_tab_graphic, "الگویی تعریف نشده", text = "ابتدا در «بیشتر ← الگوهای شیفت» یک الگو بسازید.")
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("نمای ماه")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    strips.forEach { (name, list) ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(groupTitle(name), style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp))) {
                                list.forEachIndexed { i, e ->
                                    val date = JalaliDate(month.year, month.month, i + 1)
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight().background(shiftColor(e.effectiveCode))
                                            .then(if (date == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)) {
                                for (d in 1..days) Box(
                                    Modifier.weight(1f).fillMaxHeight().background(
                                        if (cal.kind(JalaliDate(month.year, month.month, d)) != DayKind.NORMAL) Pal.holiday else Color.Transparent
                                    )
                                )
                            }
                            Row(Modifier.fillMaxWidth()) {
                                for (d in 1..days) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (d == 1 || d % 5 == 0) Text(
                                        d.fa(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Pal.muted,
                                        maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true)
                                    )
                                }
                            }
                        }
                    }
                    ShiftLegend()
                    HintText("نوار قرمز زیر هر ردیف = تعطیل رسمی/جمعه  ·  قاب رنگی = امروز")
                }
            }
        }

        if (rep != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("سهم شیفت‌ها (${sel?.name ?: ""})")
                AppCard(Modifier.fillMaxWidth()) {
                    val total = rep.daysByShift.values.sum().coerceAtLeast(1)
                    val trackColor = MaterialTheme.colorScheme.surfaceContainer
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 22.dp.toPx()
                                val tl = Offset(stroke / 2, stroke / 2)
                                val sz = Size(size.width - stroke, size.height - stroke)
                                drawArc(trackColor, 0f, 360f, false, tl, sz, style = Stroke(stroke))
                                var start = -90f
                                rep.daysByShift.forEach { (c, n) ->
                                    val sweep = 360f * n / total
                                    if (sweep > 0f) drawArc(shiftColor(c), start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, tl, sz, style = Stroke(stroke))
                                    start += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(days.fa(), style = MaterialTheme.typography.titleLarge)
                                Text("روز ماه", style = MaterialTheme.typography.labelSmall, color = Pal.muted)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rep.daysByShift.forEach { (c, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(shiftColor(c)))
                                    Text(shiftName(c), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${n.fa()} روز", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hours != null && hours.totalMinutes > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ساعت کار ماه (جمع ${Fa.hm(hours.totalMinutes)})")
                AppCard(Modifier.fillMaxWidth()) {
                    val max = hours.minutesByShift.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hours.minutesByShift.forEach { (c, m) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(shiftName(c), Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Box(Modifier.weight(1f).height(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                                    Box(Modifier.fillMaxWidth((m.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(shiftColor(c)))
                                }
                                Text(Fa.hm(m), Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }
}
