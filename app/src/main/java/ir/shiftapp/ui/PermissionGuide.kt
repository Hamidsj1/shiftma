package ir.shiftapp.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.system.ReliabilityCheck
import ir.shiftapp.system.ReliabilityItem
import kotlinx.coroutines.delay

/*
 * راهنمای قدم‌به‌قدم فعال‌سازی مجوزها برای کاربر تازه، تا آلارم و اذان مطمئن کار کنند:
 *  - یک‌بار، چند لحظه بعد از اولین باز شدن صفحهٔ اول (بعد از ساخت الگو) خودکار باز می‌شود؛
 *  - تا وقتی موردی مانده، یک کارت کوچک بالای صفحهٔ اول می‌ماند (با ✕ برای «بعداً»)؛
 *  - همان بررسی‌های پنل «اطمینان از آلارم» تنظیمات را استفاده می‌کند و بعد از برگشتن از تنظیمات سیستم خودش به‌روز می‌شود.
 * مورد «آلارم دقیق» اینجا نیست چون فقط برای حالت اعلان لازم است و در پنل تنظیمات مانده.
 */

private const val FLAGS = "shiftma_flags"
private const val KEY_AUTO = "perm_guide_auto_shown"
private const val KEY_HIDDEN = "perm_guide_card_hidden"

private fun flags(ctx: Context) = ctx.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)

private fun guideItems(ctx: Context): List<ReliabilityItem> = ReliabilityCheck.collect(ctx).filter { it.id != "exact" && it.id != "volumekey" && it.id != "restricted" }

/** عنوان ساده و دلیلِ هر مورد به زبان کاربر عادی. */
private val GUIDE_TEXT = mapOf(
    "notif" to ("اجازهٔ اعلان" to "بدون آن هیچ آلارم و یادآوری‌ای نمایش داده نمی‌شود."),
    "channel" to ("صدای آلارم" to "کانال «آلارم شیفت» باید فعال باشد، وگرنه آلارم بی‌صدا می‌ماند."),
    "battery" to ("اجازهٔ کار در پس‌زمینه (باتری)" to "مهم‌ترین مورد: بعضی گوشی‌ها برای صرفه‌جویی برنامه را می‌بندند و آلارم دیر می‌رسد. گزینهٔ «اجازه» را بزنید."),
    "overlay" to ("نمایش آلارم مثل تماس" to "با این مجوز صفحه روشن می‌شود و صفحهٔ بزرگ آلارم (با کشیدن برای قطع) روی هر چیزی باز می‌شود."),
    "fullscreen" to ("نمایش آلارم روی قفل‌صفحه" to "با این مجوز، صفحهٔ بزرگ آلارم (با دکمهٔ توقف) روی قفل‌صفحه باز می‌شود.")
)

@Composable
internal fun PermissionGuideEntry(vm: MainVm, prefs: Prefs) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }       // بعد از برگشتن از تنظیمات سیستم
    val items = remember(tick) { guideItems(ctx) }
    val todo = items.count { !it.ok }
    var open by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(flags(ctx).getBoolean(KEY_HIDDEN, false)) }

    // یک‌بار خودکار؛ کمی تأخیر تا با پنجرهٔ مجوز اعلان سیستم (اندروید ۱۳+) تداخل نکند
    LaunchedEffect(Unit) {
        val sp = flags(ctx)
        if (sp.getBoolean(KEY_AUTO, false)) return@LaunchedEffect
        delay(1500)
        if (guideItems(ctx).any { !it.ok }) { sp.edit().putBoolean(KEY_AUTO, true).apply(); open = true }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) tick++ else ReliabilityCheck.collect(ctx).firstOrNull { it.id == "notif" }?.fix?.let { ReliabilityCheck.open(ctx, it) }
    }
    val fix: (ReliabilityItem) -> Unit = { item ->
        if (item.id == "notif" && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else item.fix?.let { ReliabilityCheck.open(ctx, it) }
    }

    if (todo > 0 && !hidden) {
        AppCard(Modifier.fillMaxWidth(), onClick = { open = true }) {
            Row(
                Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Ico(R.drawable.ic_alarm, size = 22.dp, tint = Pal.amber)
                Column(Modifier.weight(1f)) {
                    Text("آلارم‌ها را مطمئن کنید", style = MaterialTheme.typography.titleSmall)
                    HintText("${Fa.num(todo)} مورد مانده؛ کمتر از یک دقیقه وقت می‌گیرد")
                }
                SmallIconButton(R.drawable.ic_close, "بعداً", { flags(ctx).edit().putBoolean(KEY_HIDDEN, true).apply(); hidden = true })
            }
        }
    }

    if (open) PermissionGuideDialog(vm, prefs, remember(tick) { ReliabilityCheck.collect(ctx).filter { it.id != "exact" } }, fix) { open = false }
}

private class GuideStep(val title: String, val intro: String, val ids: List<String>)

/** گام‌های راهنما به ترتیب؛ گامی که هیچ‌کدام از مورد‌هایش روی این گوشی نباشد (مثلاً نسخهٔ اندروید پایین) حذف می‌شود. */
private val GUIDE_STEPS = listOf(
    GuideStep("اعلان‌ها", "برای نمایش آلارم، یادآور و اذان، اعلان‌های برنامه باید روشن باشند.", listOf("notif", "channel")),
    GuideStep("کار در پس‌زمینه", "بعضی گوشی‌ها برای صرفه‌جویی برنامه را می‌بندند و آلارم دیر می‌رسد یا اصلاً نمی‌رسد.", listOf("battery")),
    GuideStep("باز شدن آلارم مثل تماس", "با این تنظیم‌ها وقتی آلارم زنگ می‌زند صفحه روشن می‌شود و صفحهٔ بزرگ قطع آلارم روی قفل‌صفحه باز می‌شود.", listOf("fullscreen", "overlay")),
    GuideStep("قطع با دکمهٔ صدا", "با این تنظیم، دکمهٔ صدا در هر حالتی (صفحه خاموش، برنامه بسته) آلارم و اذان را قطع می‌کند. مرحله‌ها را به ترتیب انجام دهید.", listOf("restricted", "volumekey")),
    GuideStep("امتحان", "یک آلارم آزمایشی بسازید و گوشی را قفل کنید.", emptyList())
)

@Composable
private fun PermissionGuideDialog(vm: MainVm, prefs: Prefs, items: List<ReliabilityItem>, onFix: (ReliabilityItem) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val steps = remember(items.map { it.id }) {
        GUIDE_STEPS.filter { st -> st.ids.isEmpty() || st.ids.any { id -> items.any { it.id == id } } }
    }
    var step by remember { mutableIntStateOf(0) }
    var later by remember { mutableStateOf(false) }
    val idx = step.coerceIn(0, steps.lastIndex)
    val st = steps[idx]
    val last = idx >= steps.lastIndex
    val close = {
        if (later) flags(ctx).edit().putBoolean(KEY_HIDDEN, true).apply()
        onClose()
    }
    AppDialog(title = null, onDismiss = close) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "گام ${Fa.num(idx + 1)}: ${st.title}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Text(
                "${Fa.num(idx + 1)}/${Fa.num(steps.size)}", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
            )
        }
        Text(st.intro, style = MaterialTheme.typography.bodyMedium)

        st.ids.mapNotNull { id -> items.firstOrNull { it.id == id } }.forEach { item ->
            val (title, why) = GUIDE_TEXT[item.id] ?: (item.title to item.detail)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(
                    if (item.ok) R.drawable.ic_check_circle else R.drawable.ic_info, size = 26.dp,
                    tint = if (item.ok) Pal.green else if (item.required) Pal.coral else Pal.amber
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    HintText(if (item.ok) "روشن است." else why)
                    if (!item.ok && item.fix != null) {
                        AppButton("رفتن به تنظیمات", { onFix(item) }, Modifier.fillMaxWidth(), style = if (item.required) BtnStyle.Filled else BtnStyle.Tonal)
                    }
                }
            }
        }

        // تنظیم مخصوص برند گوشی (مثلاً شیائومی: شروع خودکار و نمایش در قفل‌صفحه)
        val brand = Build.MANUFACTURER.orEmpty()
        if (idx == 1 && OemGuide.hasBrandTips(brand)) {
            Text(
                "یک قدم مخصوص گوشی شما (${brand.replaceFirstChar { it.uppercase() }})",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
            )
            OemGuide.tips(brand).forEach { HintText("• $it") }
        }

        if (last) {
            AppButton("آلارم آزمایشی (یک دقیقه بعد)", { scheduleTestAlarm(ctx, vm, prefs) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_alarm, style = BtnStyle.Filled)
            HintText("بعد از ساخت، گوشی را قفل کنید. اگر آلارم سر وقت زنگ زد و صفحه روشن شد، همه‌چیز درست است. گزارش کامل در تنظیمات ← «اطمینان از آلارم و اذان» است.")
        }

        Row(
            Modifier.fillMaxWidth().clickable { later = !later },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = later, onCheckedChange = { later = it })
            Text("فعلاً نمی‌خواهم تنظیمات را انجام دهم.", style = MaterialTheme.typography.bodySmall)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (idx > 0) AppButton("مرحله قبل", { step = idx - 1 }, Modifier.weight(1f), style = BtnStyle.Outline)
            AppButton(
                if (last) "تمام" else "مرحله بعد",
                { if (last) close() else step = idx + 1 },
                Modifier.weight(1f)
            )
        }
    }
}
