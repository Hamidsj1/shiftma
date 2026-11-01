package ir.shiftapp.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.shiftapp.R
import ir.shiftapp.core.Fa
import ir.shiftapp.system.AlarmRingService
import ir.shiftapp.system.ReminderScheduler
import kotlinx.coroutines.delay

/*
 * کنترل بلندی صدای آلارم و اذان.
 * آلارم شیفت و اذان (حالت «آلارم») روی کانال «آلارم» گوشی پخش می‌شوند و دکمه‌های صدا موقع زنگ زدن آلارم، آلارم را قطع می‌کنند
 * (نه اینکه صدا را تغییر بدهند)؛ پس بلندی باید از قبل و از همین‌جا تنظیم شود.
 * این نوار همان بلندی «آلارم» خود گوشی را تغییر می‌دهد.
 */

private fun alarmVolume(ctx: Context): Pair<Int, Int> {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0 to 1
    return am.getStreamVolume(AudioManager.STREAM_ALARM) to am.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
}

@Composable
internal fun AlarmVolumeControl() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val (cur, max) = remember(tick) { alarmVolume(ctx) }
    var level by remember(tick) { mutableFloatStateOf(cur.toFloat()) }
    val percent = (level / max * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ic_volume, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("بلندی صدای آلارم و اذان", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${Fa.num(percent)}٪", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
        }
        Slider(
            value = level, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0),
            onValueChange = {
                level = it
                runCatching {
                    // صفر کامل آلارم را بی‌صدا می‌کند؛ حداقل یک پله می‌ماند
                    (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                        .setStreamVolume(AudioManager.STREAM_ALARM, it.toInt().coerceAtLeast(1), 0)
                }
            }
        )
        HintText("همان بلندی «آلارم» گوشی است و روی آلارم شیفت و اذان (در حالت «آلارم») اثر دارد. موقع زنگ زدن، دکمه‌های صدا آلارم را قطع می‌کنند؛ پس بلندی را از اینجا تنظیم کنید و با «آلارم آزمایشی» یا «هشدار آزمایشی» امتحان کنید.")
    }
}

/** کارت قرمز بالای صفحهٔ اول تا وقتی آلارم زنگ می‌زند: راه همیشه‌در‌دسترس قطع آلارم، حتی اگر اعلان دیده نشود. */
@Composable
internal fun RingingBanner() {
    val ctx = LocalContext.current
    var ringing by remember { mutableStateOf(AlarmRingService.ringing) }
    LaunchedEffect(Unit) { while (true) { ringing = AlarmRingService.ringing; delay(700) } }
    if (!ringing) return
    val info = AlarmRingService.current
    AppCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico(R.drawable.ic_alarm, size = 24.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                Column(Modifier.weight(1f)) {
                    Text("آلارم در حال زنگ زدن است", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    info?.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("قطع آلارم", { ReminderScheduler.dismiss(ctx); ringing = false }, Modifier.weight(1f), icon = R.drawable.ic_close)
                AppButton("صفحهٔ آلارم", { AlarmRingService.openStopScreen(ctx) }, Modifier.weight(1f), style = BtnStyle.Outline)
            }
        }
    }
}
