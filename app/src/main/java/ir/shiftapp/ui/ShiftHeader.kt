package ir.shiftapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import java.time.ZoneId

/**
 * نوار بالای کل بخش «جدول شیفت» (همهٔ تب‌ها و زیرصفحه‌ها): دکمهٔ خانه/برگشت | عنوان + «شیفت امروز · تاریخ» | آلارم | الگوها.
 * با ضربه روی عنوان یا دکمهٔ آلارم، پنجرهٔ تنظیم آلارم یادآور شیفت و ساعت آغاز/پایان شیفت‌ها باز می‌شود.
 */
@Composable
fun ShiftTopBar(
    vm: MainVm, title: String, leadingIcon: Int, leadingDesc: String, onLeading: () -> Unit,
    showTools: Boolean, onMenu: () -> Unit
) {
    val p by vm.selected.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val allEntries by vm.entriesAllPatterns.collectAsState()
    val today = remember { JalaliDate.today() }
    var open by remember { mutableStateOf(false) }

    val code = p?.let {
        runCatching { DayResolver.resolve(it.base(), ShiftPattern.parse(it.pattern), today, allEntries[it.id]?.get(today.key).orEmpty()).effectiveCode }.getOrNull()
    }
    val alarmOn = prefs.reminderEnabled && prefs.reminderAlarm
    val dateText = "${WEEK_FULL[today.dayOfWeek]} ${today.day.fa()} ${MONTHS[today.month - 1]}"
    val subtitle = if (showTools && p != null) {
        groupTitle(p!!.name) + "  ·  " + dateText
    } else dateText

    AppTopBar(
        title = title, leadingIcon = leadingIcon, leadingDesc = leadingDesc, onLeading = onLeading,
        subtitle = subtitle, subtitleDot = if (showTools && code != null) shiftColor(code) else null,
        subtitleLead = if (showTools && p != null && groupLetter(p!!.name) != null) { { GroupBadge(p!!.name, 16.dp) } } else null,
        onTitleClick = if (showTools) { { open = true } } else null
    ) {
        // آلارم: روشن = دایرهٔ پر با رنگ اصلی، خاموش = دایرهٔ کم‌رنگ؛ در همهٔ صفحه‌ها (حتی صفحهٔ انتخاب الگو) دیده می‌شود
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (alarmOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center
        ) {
            Ico(
                if (alarmOn) R.drawable.ic_alarm else R.drawable.ic_alarm_off, size = 22.dp, desc = "آلارم شیفت",
                tint = if (alarmOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        BarIconButton(R.drawable.ic_menu, "منو، تنظیمات و گزینه‌های بیشتر", onMenu)
    }
    if (open) ShiftAlarmDialog(vm, p, prefs) { open = false }
}

@Composable
private fun AlarmTile(
    icon: Int, title: String, subtitle: String, active: Boolean, modifier: Modifier = Modifier,
    badge: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit
) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(bg).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Ico(icon, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
            if (badge != null) Text(
                badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            trailing?.invoke()
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2)
    }
}

@Composable
internal fun ShiftAlarmDialog(vm: MainVm, p: ir.shiftapp.data.PatternEntity?, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val notifPerm = rememberNotifRequest()
    val zone = remember { ZoneId.systemDefault() }
    val codes = remember(p) { p?.let { ShiftPattern.parse(it.pattern).codes.distinct().filter { c -> ShiftType.byCode(c)?.isRest != true } }.orEmpty() }
    val on = prefs.reminderEnabled && prefs.reminderAlarm
    var showSingles by remember { mutableStateOf(false) }
    var showSub by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val singles = prefs.alarms.count { !it.isSub && it.enabled && it.triggerAtMs > now }
    val subActive = SubNight.active(prefs.alarms, now)

    AppDialog("آلارم شیفت", onClose, confirmText = "تأیید", onConfirm = onClose) {
        SwitchRow("یادآور شیفت با آلارم", on, { v ->
            if (v) {
                vm.setPref(PrefKeys.REM_ON, "true")
                vm.setPref(PrefKeys.REM_ALARM, "true")
                notifPerm()
            } else {
                vm.setPref(PrefKeys.REM_ON, "false")
            }
        }, Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer))
        if (on) {
            LeadPicker(prefs.reminderLeadMin) { vm.setPref(PrefKeys.REM_LEAD, it.toString()) }
            val parts = codes.mapNotNull { c ->
                prefs.shiftTimes[c]?.let { "${shiftName(c)} ${Fa.digits(ShiftTime.fmt(Math.floorMod(it.startMin - prefs.reminderLeadMin, 1440)))}" }
            }
            if (parts.isNotEmpty()) HintText("← " + parts.joinToString("  ·  "))
        }
        if (codes.isNotEmpty()) {
            Text("ساعت شیفت‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            codes.forEach { ShiftTimeLine(it, prefs, vm) }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlarmTile(
                R.drawable.ic_alarm, "آلارم‌های تکی", "روز و ساعت دلخواه", active = false,
                modifier = Modifier.weight(1f).fillMaxHeight(), badge = if (singles > 0) singles.fa() else null
            ) { showSingles = true }
            AlarmTile(
                R.drawable.ic_schedule, "جانشین شب",
                if (subActive.isEmpty()) "ثبت خروج و ورود نیمه‌شب" else "فعال · " + subActive.joinToString(" و ") { a ->
                    val t = java.time.Instant.ofEpochMilli(a.triggerAtMs).atZone(zone)
                    Fa.digits(ShiftTime.fmt(t.hour * 60 + t.minute))
                },
                active = subActive.isNotEmpty(), modifier = Modifier.weight(1f).fillMaxHeight(),
                trailing = {
                    Switch(checked = subActive.isNotEmpty(), onCheckedChange = { v ->
                        if (!v) vm.setAlarms(prefs.alarms.filterNot { it.isSub })
                        else {
                            val rest = prefs.alarms.filterNot { it.isSub }
                            val plan = SubNight.plan(now, zone, java.time.LocalDate.now(zone), prefs.subOutMin, prefs.subInMin, SingleAlarm.nextId(rest))
                            if (plan.isEmpty()) Toast.makeText(ctx, "زمان امشب گذشته است؛ برای شب دیگر روی کاشی بزنید", Toast.LENGTH_LONG).show()
                            else { vm.setAlarms(rest + plan); notifPerm() }
                        }
                    })
                }
            ) { showSub = true }
        }
        HintText("گزینه‌های بیشتر در «بیشتر ← تنظیمات».")
    }
    if (showSingles) SingleAlarmsDialog(vm, p, prefs) { showSingles = false }
    if (showSub) SubNightDialog(vm, prefs) { showSub = false }
}
