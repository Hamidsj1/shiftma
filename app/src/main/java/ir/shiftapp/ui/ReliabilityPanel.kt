package ir.shiftapp.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.system.ReliabilityItem
import ir.shiftapp.system.ReliabilityCheck
import ir.shiftapp.system.ReliabilityStore
import java.time.Instant
import java.time.ZoneId

/*
 * پنل «اطمینان از آلارم و اذان» داخل تنظیمات:
 * ۱) وضعیت مجوزها و محدودیت‌های سیستم (با دکمهٔ رفتن به صفحهٔ تنظیم هر کدام)،
 * ۲) آلارم آزمایشی یک‌دقیقه‌ای که از مسیر واقعی آلارم می‌گذرد،
 * ۳) گزارش پخش (زمان برنامه‌ریزی‌شده در برابر زمان واقعی رسیدن) و دکمهٔ اشتراک آن،
 * ۴) راهنمای تنظیمات برند گوشی.
 */

private const val TEST_ALARM_LABEL = "آلارم آزمایشی"

private fun clockText(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return Fa.digits("%02d:%02d:%02d".format(t.hour, t.minute, t.second))
}

/** آلارم آزمایشی یک‌دقیقه‌ای از مسیر واقعی آلارم‌های تکی؛ هم پنل تنظیمات و هم راهنمای فعال‌سازی از آن استفاده می‌کنند. */
internal fun scheduleTestAlarm(ctx: android.content.Context, vm: MainVm, prefs: Prefs) {
    val now = System.currentTimeMillis()
    val kept = prefs.alarms.filter { it.label != TEST_ALARM_LABEL }
    vm.setAlarms(kept + SingleAlarm(SingleAlarm.nextId(kept), now + 60_000L, TEST_ALARM_LABEL))
    android.widget.Toast.makeText(ctx, "آلارم آزمایشی ساخته شد؛ صفحه را قفل کنید و منتظر بمانید.", android.widget.Toast.LENGTH_LONG).show()
}

@Composable
internal fun ReliabilityPanel(vm: MainVm, prefs: Prefs) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // بعد از برگشتن از صفحهٔ تنظیمات سیستم (یا شلیک آلارم آزمایشی) وضعیت دوباره خوانده می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val items = remember(tick) { ReliabilityCheck.collect(ctx) }
    val log = remember(tick) { ReliabilityStore.load(ctx) }
    val problems = ReliabilityCheck.problems(items)
    val notifRequest = rememberNotifRequest()

    Text(
        if (problems == 0) "همه‌چیز درست است؛ آلارم و اذان باید به‌موقع برسند."
        else "${Fa.num(problems)} مورد نیازمند اقدام است.",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (problems == 0) Pal.green else Pal.amber
    )

    items.forEach { item -> CheckRow(item, onFix = {
        if (item.id == "notif" && Build.VERSION.SDK_INT >= 33) notifRequest()
        item.fix?.let { ReliabilityCheck.open(ctx, it) }
    }) }

    HintText("«پیشنهادی» یعنی برنامه بدون آن هم کار می‌کند، ولی روی بعضی گوشی‌ها ممکن است هشدار دیر برسد.")

    AlarmVolumeControl()

    // آلارم آزمایشی: یک دقیقه بعد، از همان مسیر آلارم‌های تکی
    AppButton(
        "آلارم آزمایشی (یک دقیقه بعد)",
        { scheduleTestAlarm(ctx, vm, prefs) },
        Modifier.fillMaxWidth(), icon = R.drawable.ic_alarm, style = BtnStyle.Tonal
    )
    HintText("بعد از ساخت، گوشی را قفل کنید. اگر آلارم به‌موقع زنگ زد و در گزارش زیر «به‌موقع» نوشته شد، تنظیمات شما درست است.")

    // گزارش پخش
    val s = FireLog.summary(log)
    Text(
        if (log.isEmpty()) "گزارش پخش هنوز خالی است."
        else "گزارش پخش: ${Fa.num(s.total)} هشدار، ${Fa.num(s.late)} مورد دیررسیده",
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
    )
    log.takeLast(8).reversed().forEach { r ->
        val late = FireLog.isLate(r)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dot(if (late) Pal.coral else Pal.green)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("${FireLog.kindTitle(r.kind)} — ${clockText(r.dueMs)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    FireLog.delayLabel(r.delayMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (late) Pal.coral else Pal.muted
                )
            }
        }
    }
    if (log.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                "اشتراک گزارش",
                {
                    val text = FireLog.report(log, Build.MANUFACTURER.orEmpty(), Build.VERSION.SDK_INT, ::clockText)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(send, "اشتراک گزارش پخش"))
                },
                Modifier.weight(1f), icon = R.drawable.ic_share, style = BtnStyle.Outline
            )
            AppButton(
                "پاک کردن گزارش",
                { ReliabilityStore.clear(ctx); tick++ },
                Modifier.weight(1f), icon = R.drawable.ic_delete, style = BtnStyle.Outline
            )
        }
    }

    // راهنمای برند
    val brand = Build.MANUFACTURER.orEmpty()
    Text(
        if (OemGuide.hasBrandTips(brand)) "تنظیمات پیشنهادی برای گوشی شما (${brand.replaceFirstChar { it.uppercase() }})"
        else "پیشنهادهای عمومی برای رسیدن به‌موقع هشدار",
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
    )
    OemGuide.tips(brand).forEach { tip -> HintText("• $tip") }
}

@Composable
private fun CheckRow(item: ReliabilityItem, onFix: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(
                if (item.ok) R.drawable.ic_check_circle else R.drawable.ic_info,
                size = 22.dp, tint = if (item.ok) Pal.green else if (item.required) Pal.coral else Pal.amber
            )
            Spacer(Modifier.width(8.dp))
            Text(
                item.title + if (!item.ok && !item.required) " (پیشنهادی)" else "",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium
            )
        }
        HintText(item.detail)
        if (!item.ok && item.fix != null) {
            AppButton("رفتن به تنظیمات", onFix, Modifier.fillMaxWidth(), style = if (item.required) BtnStyle.Filled else BtnStyle.Tonal)
        }
    }
}
