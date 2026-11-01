package ir.shiftapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PatternPreset

/** صفحهٔ شروع برای کاربر تازه: تا وقتی الگویی نیست، به‌جای تب‌های خالی نمایش داده می‌شود. */
@Composable
fun WelcomeScreen(vm: MainVm) {
    var creating by remember { mutableStateOf(false) }
    var presetCodes by remember { mutableStateOf<List<Int>?>(null) }
    var presetDay by remember { mutableStateOf<JalaliDate?>(null) }
    var crewPreset by remember { mutableStateOf<PatternPreset?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openPatternFile(uri) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)))
            Text("به ${AppInfo.APP_NAME} خوش آمدید", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            Text("جدول شیفت ماهانه را خودکار از روی الگوی چرخشی شما می‌سازد.", style = MaterialTheme.typography.bodyMedium, color = Pal.muted, textAlign = TextAlign.Center)
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("برای شروع، سه قدم:", style = MaterialTheme.typography.titleSmall)
                listOf(
                    "الگوی شیفت خود را از فهرست زیر انتخاب کنید (یا الگوی دلخواه بسازید).",
                    "تاریخ شروع دورهٔ شیفت را وارد کنید.",
                    "جدول شیفت ماه، تقویم و گزارش‌ها خودکار ساخته می‌شود."
                ).forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text((i + 1).fa(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("ساخت الگوی دلخواه", { presetCodes = null; presetDay = null; creating = true }, Modifier.weight(1f), icon = R.drawable.ic_add)
            AppButton("ورود از فایل الگو", { filePicker.launch(arrayOf("*/*")) }, Modifier.weight(1f), icon = R.drawable.ic_download, style = BtnStyle.Outline)
        }
        SectionLabel("الگوهای آماده")
        PresetCards(
            onPick = { presetCodes = it.codes; presetDay = it.defaultStart(); creating = true },
            onCrews = { crewPreset = it }
        )
    }

    if (creating) PatternDialog(vm, null, presetCodes = presetCodes, startDay = presetDay) { creating = false; presetCodes = null; presetDay = null }
    crewPreset?.let { CrewsDialog(vm, it) { crewPreset = null } }
}
