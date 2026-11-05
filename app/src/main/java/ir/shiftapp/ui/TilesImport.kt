package ir.shiftapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ir.shiftapp.core.Fa
import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.TilesPack
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.data.PatternFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun sectionName(id: String) = when (id) {
    "phonebook" -> "دفترچه تلفن"
    "meals" -> "برنامه غذایی"
    else -> id
}

/** تأیید قبل از افزودن کاشی‌های فایل .shiftma (سامانه‌ها و بخش‌های مخفی)؛ کاربر می‌تواند فقط بعضی سامانه‌ها را تیک بزند. */
@Composable
fun ImportTilesDialog(pack: TilesPack, onAdd: (Set<String>) -> Unit, onReplace: (Set<String>) -> Unit, onCancel: () -> Unit) {
    val sel = remember { mutableStateListOf<String>().apply { addAll(pack.sites.map { it.key }) } }
    val chosen = sel.toSet()
    AppDialog(
        "افزودن کاشی‌ها از فایل؟", onCancel, confirmText = "افزودن", onConfirm = { onAdd(chosen) }, dismissText = "انصراف",
        confirmEnabled = pack.sites.isEmpty() || chosen.isNotEmpty()
    ) {
        pack.sites.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Checkbox) { if (s.key in sel) sel.remove(s.key) else sel.add(s.key) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(s.key in sel, onCheckedChange = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall)
                    Text(TilesPackFile.hostOf(s.url), style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                }
            }
        }
        if (pack.sections.isNotEmpty()) {
            Text(
                "بخش‌هایی که نمایان می‌شوند: " + pack.sections.joinToString("، ") { sectionName(it) },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (pack.adsFeed.isNotEmpty()) {
            Text(if (pack.publicAds) "پنل اطلاع‌رسانی محل کار شما و پنل عمومی برنامه نشان داده می‌شود." else "پنل اطلاع‌رسانی پایین صفحهٔ اول مخصوص محل کار شما خواهد شد.", style = MaterialTheme.typography.bodyMedium)
        }
        if (pack.noticeFeed.isNotEmpty()) {
            Text(if (pack.publicNotices) "اعلان‌های محل کار شما و اعلان‌های عمومی برنامه به‌صورت اعلان گوشی می‌رسد." else "فقط اعلان‌های مخصوص محل کار شما به‌صورت اعلان گوشی می‌رسد.", style = MaterialTheme.typography.bodyMedium)
        }
        if (pack.mealsFeed.isNotEmpty()) {
            Text("برنامهٔ غذایی مجتمع‌ها از اینترنت (گیت‌هاب) خودکار دریافت می‌شود.", style = MaterialTheme.typography.bodyMedium)
        }
        if (pack.sites.isNotEmpty()) {
            Text("نام کاربری و رمز در فایل نیست؛ بعد از افزودن، داخل هر سامانه با دکمهٔ قفل ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            TextButton({ onReplace(chosen) }, enabled = chosen.isNotEmpty()) { Text("جایگزینی همهٔ سامانه‌های فعلی (${Fa.num(chosen.size)} مورد)") }
        }
    }
}

/** کاشی‌های فعلی را به‌صورت فایل .shiftma می‌سازد و پنجرهٔ اشتراک‌گذاری سیستم را باز می‌کند (بدون نام کاربری و رمز). */
suspend fun shareTilesFile(ctx: Context, sites: List<SiteTile>, sections: Set<String>, mealsFeed: String = "", adsFeed: String = "", publicAds: Boolean = false, noticeFeed: String = "", publicNotices: Boolean = true, configFeed: String = "") {
    if (sites.isEmpty() && sections.isEmpty() && mealsFeed.isEmpty() && adsFeed.isEmpty() && noticeFeed.isEmpty() && configFeed.isEmpty()) return
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "ShiftMa-tiles.${PatternFile.EXT}")
            f.writeText(TilesPackFile.toJson(sites, sections, mealsFeed, adsFeed, publicAds, noticeFeed, publicNotices, configFeed, ir.shiftapp.system.RegionLoader.regionTag(ctx)), Charsets.UTF_8)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        } catch (e: Exception) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, "ساخت فایل کاشی‌ها ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PatternFile.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "ارسال فایل کاشی‌ها"))
}
