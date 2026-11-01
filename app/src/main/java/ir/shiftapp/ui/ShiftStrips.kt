package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import java.time.Instant
import java.time.ZoneId

/*
 * دو نوار باریک و کم‌ارتفاع برای تب تقویم:
 *  - ShiftProgressStrip (۴۰dp) بالای تقویم: شیفت در جریان، نوار گذشته از شیفت و باقی‌ماندهٔ عددی؛ اگر الان شیفتی در جریان نیست نشان داده نمی‌شود (فضا اشغال نمی‌کند).
 *  - ReminderStrip (۳۶dp) زیر تقویم: نزدیک‌ترین آلارم‌ها و یادآور یادداشت‌ها به‌صورت چیپ‌های افقی؛ ضربه روی آلارم، پنجرهٔ آلارم شیفت را باز می‌کند.
 * هر دو با rememberNowMs (PrayerScreen.kt) هر ۳۰ ثانیه تازه می‌شوند.
 */

@Composable
fun ShiftProgressStrip(vm: MainVm, modifier: Modifier = Modifier) {
    val p by vm.selected.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val allEntries by vm.entriesAllPatterns.collectAsState()
    val now = rememberNowMs()
    val pat = p ?: return
    val info = remember(now, pat, prefs.shiftTimes, allEntries) {
        val today = JalaliDate.today()
        val base = pat.base()
        val pattern = ShiftPattern.parse(pat.pattern)
        fun slot(d: JalaliDate): Pair<Int, ShiftTime>? {
            val r = runCatching { DayResolver.resolve(base, pattern, d, allEntries[pat.id]?.get(d.key).orEmpty()) }.getOrNull() ?: return null
            if (r.onLeave || ShiftType.byCode(r.effectiveCode)?.isRest == true) return null
            val t = prefs.shiftTimes[r.effectiveCode] ?: return null
            return r.effectiveCode to t
        }
        val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        ShiftProgress.compute(t.hour * 60 + t.minute, slot(today.plusDays(-1)), slot(today))
    } ?: return

    val remain = ShiftProgress.clock(info.remainingMin)
    val percent = (info.fraction * 100).toInt()
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp)
            .semantics { contentDescription = "${shiftName(info.code)}؛ ${Fa.num(percent)} درصد گذشته؛ $remain مانده تا پایان شیفت" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Dot(shiftColor(info.code), 8.dp)
        Text(shiftName(info.code), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth(info.fraction).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(remain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("مانده", style = MaterialTheme.typography.labelMedium, color = Pal.muted, maxLines = 1)
        }
    }
}

private class ReminderChip(val atMs: Long, val text: String, val isNote: Boolean)

@Composable
fun ReminderStrip(vm: MainVm, modifier: Modifier = Modifier) {
    val p by vm.selected.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val now = rememberNowMs()
    val zone = remember { ZoneId.systemDefault() }
    var open by remember { mutableStateOf(false) }
    val chips = remember(prefs.alarms, prefs.notes, now) {
        val alarms = prefs.alarms.filter { it.enabled && it.triggerAtMs > now }.map { ReminderChip(it.triggerAtMs, it.title, false) }
        val notes = prefs.notes.filter { (it.reminderAt ?: 0L) > now }.map { ReminderChip(it.reminderAt ?: 0L, it.displayTitle, true) }
        (alarms + notes).sortedBy { it.atMs }.take(5)
    }
    Row(
        modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (chips.isEmpty()) {
            StripChip(R.drawable.ic_alarm, "یادآوری تنظیم نشده · افزودن", first = false) { open = true }
        } else chips.forEachIndexed { i, c ->
            val time = WidgetText.dayAndTime(now, c.atMs, zone).removePrefix("امروز ")
            StripChip(if (c.isNote) R.drawable.ic_note else R.drawable.ic_alarm, "${c.text} · $time", first = i == 0) { if (!c.isNote) open = true }
        }
    }
    if (open) ShiftAlarmDialog(vm, p, prefs) { open = false }
}

@Composable
private fun StripChip(icon: Int, text: String, first: Boolean, onClick: () -> Unit) {
    val bg = if (first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Ico(icon, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
    }
}
