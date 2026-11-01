package ir.shiftapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ir.shiftapp.core.ShiftPattern
import ir.shiftapp.data.PatternEntity
import ir.shiftapp.data.PatternFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** الگوها را به‌صورت فایل .shiftma می‌سازد و پنجرهٔ اشتراک‌گذاری سیستم را باز می‌کند. */
suspend fun sharePatternFile(ctx: Context, patterns: List<PatternEntity>) {
    if (patterns.isEmpty()) return
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "ShiftMa-patterns.${PatternFile.EXT}")
            f.writeText(PatternFile.toJson(patterns), Charsets.UTF_8)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        } catch (e: Exception) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, "ساخت فایل الگو ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PatternFile.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        putExtra(Intent.EXTRA_SUBJECT, "الگوهای شیفت (${AppInfo.APP_NAME})")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "ارسال فایل الگو"))
}

/** تأیید قبل از افزودن الگوهای فایل .shiftma؛ کاربر می‌تواند فقط بعضی را تیک بزند. */
@Composable
fun ImportPatternsDialog(list: List<PatternEntity>, onOk: (List<PatternEntity>) -> Unit, onCancel: () -> Unit) {
    val checked = remember { mutableStateListOf<Boolean>().apply { repeat(list.size) { add(true) } } }
    val chosen = list.filterIndexed { i, _ -> checked.getOrElse(i) { false } }
    AppDialog(
        if (list.size == 1) "افزودن الگو از فایل؟" else "افزودن ${chosen.size.fa()} از ${list.size.fa()} الگو؟",
        onCancel, confirmText = "افزودن", onConfirm = { onOk(chosen) }, dismissText = "انصراف", confirmEnabled = chosen.isNotEmpty()
    ) {
        list.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Checkbox) { checked[i] = !checked[i] }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked[i], onCheckedChange = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name + if (p.company.isNotBlank()) "  (${p.company})" else "", style = MaterialTheme.typography.titleSmall)
                    Text("چرخه: ${ShiftPattern.parse(p.pattern).cycleLength.fa()} روز، شروع: ${p.baseYear.fa()}/${p.baseMonth.fa()}/${p.baseDay.fa()}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    val extra = listOf("مسئول شیفت" to p.supervisor, "سرشیفت کل" to p.headSupervisor, "بخش" to p.section).filter { it.second.isNotBlank() }
                    if (extra.isNotEmpty()) Text(extra.joinToString("  |  ") { "${it.first}: ${it.second}" }, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
            }
        }
    }
}
