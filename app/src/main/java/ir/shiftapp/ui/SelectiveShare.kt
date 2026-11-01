package ir.shiftapp.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ir.shiftapp.core.ShiftPattern
import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.data.BundleFile
import ir.shiftapp.data.PatternEntity
import ir.shiftapp.data.PatternFile
import ir.shiftapp.docs.MealComplex
import ir.shiftapp.docs.MealPackItem
import ir.shiftapp.phonebook.Contact
import ir.shiftapp.phonebook.PhonebookVm
import ir.shiftapp.phonebook.matches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ارسال انتخابی (کشوی صفحهٔ اول): کاربر از بین الگوها، کاشی‌های سامانه، برنامه غذایی و مخاطبین هر کدام را که می‌خواهد تیک می‌زند
 * و یک فایل .shiftma (type = bundle) ساخته می‌شود. نام کاربری و رمز سامانه‌ها هرگز در فایل نیست.
 * [loadContacts] = null یعنی دفترچه تلفن در دسترس نیست (بخش مخفی است) و بخش مخاطبین نشان داده نمی‌شود.
 * [meals] = مجتمع‌هایی که فایل برنامه دارند؛ null یا خالی یعنی بخش برنامه غذایی نشان داده نمی‌شود.
 */
@Composable
fun SelectiveShareDialog(
    patterns: List<PatternEntity>,
    sites: List<SiteTile>,
    loadContacts: (suspend () -> List<Contact>)?,
    meals: List<MealComplex>?,
    onSend: (List<PatternEntity>, List<SiteTile>, List<Contact>, List<MealComplex>) -> Unit,
    onCancel: () -> Unit
) {
    val mealList = meals.orEmpty()
    val pSel = remember { mutableStateListOf<Long>() }
    val sSel = remember { mutableStateListOf<String>() }
    val cSel = remember { mutableStateListOf<Long>() }
    val mSel = remember { mutableStateListOf<String>() }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (loadContacts != null) contacts = loadContacts() }
    val total = pSel.size + sSel.size + cSel.size + mSel.size
    AppDialog(
        "ارسال انتخابی", onCancel,
        confirmText = if (total > 0) "ارسال (${total.fa()})" else "ارسال",
        onConfirm = { onSend(patterns.filter { it.id in pSel }, sites.filter { it.key in sSel }, contacts.filter { it.id in cSel }, mealList.filter { it.id in mSel }) },
        dismissText = "انصراف", confirmEnabled = total > 0
    ) {
        if (patterns.isEmpty() && sites.isEmpty() && contacts.isEmpty() && mealList.isEmpty()) {
            Text("هنوز الگو، سامانه، برنامه غذایی یا مخاطبی برای ارسال نیست.", style = MaterialTheme.typography.bodyMedium)
        }
        if (patterns.isNotEmpty()) {
            PickHeader("الگوها", pSel.size == patterns.size) {
                if (pSel.size == patterns.size) pSel.clear() else { pSel.clear(); pSel.addAll(patterns.map { it.id }) }
            }
            patterns.forEach { p ->
                PickRow(
                    p.id in pSel,
                    p.name + if (p.company.isNotBlank()) "  (${p.company})" else "",
                    "چرخه: ${ShiftPattern.parse(p.pattern).cycleLength.fa()} روز"
                ) { if (p.id in pSel) pSel.remove(p.id) else pSel.add(p.id) }
            }
        }
        if (sites.isNotEmpty()) {
            PickHeader("کاشی‌های سامانه", sSel.size == sites.size) {
                if (sSel.size == sites.size) sSel.clear() else { sSel.clear(); sSel.addAll(sites.map { it.key }) }
            }
            sites.forEach { s ->
                PickRow(s.key in sSel, s.title, TilesPackFile.hostOf(s.url)) { if (s.key in sSel) sSel.remove(s.key) else sSel.add(s.key) }
            }
            Text("نام کاربری و رمز سامانه‌ها در فایل نمی‌آید.", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
        if (mealList.isNotEmpty()) {
            PickHeader("برنامه غذایی", mSel.size == mealList.size) {
                if (mSel.size == mealList.size) mSel.clear() else { mSel.clear(); mSel.addAll(mealList.map { it.id }) }
            }
            mealList.forEach { c ->
                PickRow(c.id in mSel, c.name, listOfNotNull(c.label.takeIf { it.isNotBlank() }, c.file?.title).joinToString(" · ")) {
                    if (c.id in mSel) mSel.remove(c.id) else mSel.add(c.id)
                }
            }
            Text("فایل برنامهٔ هر مجتمع همراه می‌رود؛ فایل‌های بزرگ فایل ارسالی را سنگین می‌کنند.", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
        if (contacts.isNotEmpty()) {
            // فهرست مخاطبین ممکن است هزاران ردیف باشد؛ تیک‌زدن تک‌تک در پنجرهٔ جدا (با جست‌وجو) انجام می‌شود
            PickHeader("مخاطبین", cSel.size == contacts.size) {
                if (cSel.size == contacts.size) cSel.clear() else { cSel.clear(); cSel.addAll(contacts.map { it.id }) }
            }
            PickRow(
                cSel.isNotEmpty(),
                if (cSel.isEmpty()) "انتخاب مخاطبین…" else "${cSel.size.fa()} از ${contacts.size.fa()} مخاطب انتخاب شده",
                "جست‌وجو و تیک‌زدن تک‌تک؛ عکس مخاطبین و پیش‌شمارهٔ داخلی (اگر تنظیم شده) هم همراه می‌رود"
            ) { picking = true }
        }
    }
    if (picking) ContactPickDialog(contacts, cSel) { picking = false }
}

/** انتخاب مخاطبین با جست‌وجو؛ تیک‌ها مستقیم در [sel] (شناسهٔ مخاطب) نوشته می‌شوند. */
@Composable
private fun ContactPickDialog(all: List<Contact>, sel: androidx.compose.runtime.snapshots.SnapshotStateList<Long>, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, all) { if (q.isBlank()) all else all.filter { it.matches(q) } }
    val allShownChecked = shown.isNotEmpty() && shown.all { it.id in sel }
    AppDialog("انتخاب مخاطبین", onClose, confirmText = "تأیید (${sel.size.fa()})", onConfirm = onClose) {
        SearchField(q, { q = it })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size.fa()} مخاطب", style = MaterialTheme.typography.bodySmall, color = Pal.muted, modifier = Modifier.weight(1f))
            TextButton({
                if (allShownChecked) sel.removeAll(shown.map { it.id }.toSet())
                else shown.forEach { if (it.id !in sel) sel.add(it.id) }
            }, enabled = shown.isNotEmpty()) { Text(if (allShownChecked) "برداشتن همهٔ نتایج" else "انتخاب همهٔ نتایج") }
        }
        LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { c ->
                PickRow(
                    c.id in sel, c.name,
                    listOf(c.mobile, c.landline, c.dept).filter { it.isNotBlank() }.joinToString(" · ")
                ) { if (c.id in sel) sel.remove(c.id) else sel.add(c.id) }
            }
        }
    }
}

@Composable
private fun PickHeader(title: String, allChecked: Boolean, onToggleAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onToggleAll) { Text(if (allChecked) "هیچ‌کدام" else "همه") }
    }
}

@Composable
private fun PickRow(checked: Boolean, title: String, sub: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Checkbox, onClick = onToggle).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked, onCheckedChange = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
    }
}

/** فایل ارسال انتخابی را می‌سازد و پنجرهٔ اشتراک‌گذاری سیستم را باز می‌کند. [pbVm] برای عکس مخاطبین است. */
suspend fun shareBundleFile(
    ctx: Context, patterns: List<PatternEntity>, sites: List<SiteTile>, contacts: List<Contact> = emptyList(),
    pbVm: PhonebookVm? = null, meals: List<MealPackItem> = emptyList(), prefix: String = ""
) {
    if (patterns.isEmpty() && sites.isEmpty() && contacts.isEmpty() && meals.isEmpty()) return
    var tooBig = false
    var photosDropped = false
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "ShiftMa-selected.${PatternFile.EXT}")
            var json = if (pbVm != null && contacts.isNotEmpty())
                BundleFile.toJson(patterns, sites, contacts, { pbVm.photoBytes(it.photo) }, meals, prefix)
            else BundleFile.toJson(patterns, sites, contacts, meals = meals, prefix = prefix)
            if (json.length > BundleFile.MAX_CHARS && contacts.isNotEmpty()) {      // عکس‌ها فایل را از سقف می‌برد: بدون عکس بساز
                json = BundleFile.toJson(patterns, sites, contacts, meals = meals, prefix = prefix); photosDropped = true
            }
            if (json.length > BundleFile.MAX_CHARS) { tooBig = true; null }      // گیرنده فایل بزرگ‌تر از سقف را نمی‌پذیرد
            else {
                f.writeText(json, Charsets.UTF_8)
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            }
        } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, if (tooBig) "حجم موارد انتخاب‌شده زیاد است؛ تعداد کمتری انتخاب کنید" else "ساخت فایل ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    if (photosDropped) Toast.makeText(ctx, "حجم عکس‌ها زیاد بود؛ فایل بدون عکس ساخته شد", Toast.LENGTH_LONG).show()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PatternFile.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        putExtra(Intent.EXTRA_SUBJECT, "ارسال انتخابی (${AppInfo.APP_NAME})")
        clipData = ClipData.newRawUri(AppInfo.APP_NAME, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, "ارسال فایل انتخابی"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "برنامه‌ای برای ارسال فایل پیدا نشد", Toast.LENGTH_LONG).show()
    }
}
