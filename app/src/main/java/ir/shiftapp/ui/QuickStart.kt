package ir.shiftapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PatternPreset
import ir.shiftapp.core.PatternPresets

/**
 * شروع سریع در صفحهٔ اول، تا وقتی هنوز هیچ الگویی ساخته نشده: چند الگوی پرکاربرد، «همهٔ الگوها»، ساخت دلخواه و ورود از فایل.
 * همان دیالوگ‌های صفحهٔ خوش‌آمد بخش جدول شیفت را باز می‌کند؛ با ساخته شدن اولین الگو این کارت از صفحهٔ اول برمی‌دارد و تقویم ماه می‌آید.
 */
@Composable
internal fun QuickStartCard(vm: MainVm, modifier: Modifier = Modifier) {
    var creating by remember { mutableStateOf(false) }
    var presetCodes by remember { mutableStateOf<List<Int>?>(null) }
    var presetDay by remember { mutableStateOf<JalaliDate?>(null) }
    var crewPreset by remember { mutableStateOf<PatternPreset?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openPatternFile(uri) }
    val pick: (PatternPreset) -> Unit = { presetCodes = it.codes; presetDay = it.defaultStart(); creating = true }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("از اینجا شروع کنید", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HintText("الگوی شیفت خودتان را انتخاب کنید و تاریخ شروع دوره را بدهید؛ جدول شیفت، تقویم و گزارش‌ها خودکار ساخته می‌شود. بعداً هم می‌شود عوضش کرد.")
        }
        PatternPresets.FEATURED.forEach { PresetCard(it, pick) { c -> crewPreset = c } }
        AppButton("همهٔ الگوهای آماده", { showAll = true }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("ساخت الگوی دلخواه", { presetCodes = null; presetDay = null; creating = true }, Modifier.weight(1f), icon = R.drawable.ic_add)
            AppButton("ورود از فایل الگو", { filePicker.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = R.drawable.ic_download, style = BtnStyle.Outline)
        }
    }

    if (showAll) PresetsDialog(
        onPick = { pick(it); showAll = false },
        onCrews = { crewPreset = it; showAll = false },
        onClose = { showAll = false }
    )
    if (creating) PatternDialog(vm, null, presetCodes = presetCodes, startDay = presetDay) { creating = false; presetCodes = null; presetDay = null }
    crewPreset?.let { CrewsDialog(vm, it) { crewPreset = null } }
}
