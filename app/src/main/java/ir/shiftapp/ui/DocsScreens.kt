package ir.shiftapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.docs.DocFile
import ir.shiftapp.docs.DocKind
import ir.shiftapp.docs.DocTypes
import ir.shiftapp.docs.DocsVm
import ir.shiftapp.docs.MealComplex
import ir.shiftapp.docs.MealPackItem
import kotlinx.coroutines.launch

private fun sizeText(bytes: Long): String =
    if (bytes >= 1024 * 1024) "${(bytes / 1024 / 1024).toInt().fa()}٫${((bytes / 1024 % 1024) * 10 / 1024).toInt().fa()} مگابایت"
    else "${(bytes / 1024).coerceAtLeast(1).toInt().fa()} کیلوبایت"

private fun kindIcon(k: DocKind) = if (k == DocKind.IMAGE) R.drawable.ic_image else R.drawable.ic_note

/** یک ردیف فایل با آیکن نوع، عنوان و توضیح؛ [actions] دکمه‌های سمت چپ ردیف. */
@Composable
private fun DocRow(f: DocFile, subtitle: String?, onClick: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(kindIcon(f.kind), size = 44.dp, iconSize = 22.dp, modifier = Modifier)
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle ?: "${f.kind.label} · ${sizeText(f.size)}", style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
}

// ───────────────────────── فایل‌های من (عمومی) ─────────────────────────

@Composable
fun FilesSection(vm: DocsVm, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val files by vm.myFiles.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    var viewing by remember { mutableStateOf<DocFile?>(null) }
    var deleting by remember { mutableStateOf<DocFile?>(null) }
    var renaming by remember { mutableStateOf<DocFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addMyFile(uri) }
    BackHandler { onHome() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            AppTopBar(title = "فایل‌های من", leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome, subtitle = "${files.size.fa()} فایل")
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("افزودن فایل", style = MaterialTheme.typography.labelLarge) },
                icon = { Ico(R.drawable.ic_add, size = 22.dp) },
                onClick = { picker.launch(DocTypes.PICKER_MIMES) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (files.isEmpty()) EmptyState(
                R.drawable.ic_home_files, "هنوز فایلی نیست", Modifier.align(Alignment.Center),
                text = "با «افزودن فایل»، عکس، PDF یا فایل نمایشی (پاورپوینت) خودتان را اضافه کنید تا همیشه دم‌دست باشد. فایل‌ها فقط روی همین گوشی می‌مانند."
            ) else LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files, key = { it.id }) { f ->
                    DocRow(f, null, onClick = { viewing = f }) {
                        SmallIconButton(R.drawable.ic_edit, "تغییر نام", { renaming = f })
                        SmallIconButton(R.drawable.ic_share, "ارسال", { shareDocFile(ctx, vm.fileOf(f), f.mime, f.title) })
                        SmallIconButton(R.drawable.ic_delete, "حذف", { deleting = f }, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    viewing?.let { f -> DocViewerDialog(f.title, vm.fileOf(f), f.kind, f.mime) { viewing = null } }
    deleting?.let { f ->
        ConfirmDialog("«${f.title}» از برنامه حذف شود؟ (فایل اصلی در جای دیگر دست‌نخورده می‌ماند)", "حذف", onOk = { vm.deleteMyFile(f); deleting = null }, onCancel = { deleting = null }, danger = true)
    }
    renaming?.let { f ->
        var t by remember(f.id) { mutableStateOf(f.title) }
        AppDialog("تغییر نام", { renaming = null }, confirmText = "ذخیره", onConfirm = { vm.renameMyFile(f, t); renaming = null }, confirmEnabled = t.isNotBlank(), dismissText = "انصراف") {
            AppField(t, { t = it }, "نام فایل")
        }
    }
}

// ───────────────────────── برنامه غذایی (اختصاصی) ─────────────────────────

@Composable
fun MealsSection(vm: DocsVm, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by vm.complexes.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    var viewing by remember { mutableStateOf<MealComplex?>(null) }
    var editing by remember { mutableStateOf<MealComplex?>(null) }      // id خالی = مجتمع تازه
    var deleting by remember { mutableStateOf<MealComplex?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pickFor; pickFor = null
        if (uri != null && id != null) vm.setComplexFile(id, uri)
    }
    BackHandler { onHome() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            AppTopBar(title = "برنامه غذایی", leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome, subtitle = "${list.size.fa()} مجتمع") {
                BarIconButton(R.drawable.ic_share, "ارسال بستهٔ برنامه غذایی", {
                    scope.launch { vm.buildMealPack()?.let { shareMealPack(ctx, it) } }
                })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("افزودن مجتمع", style = MaterialTheme.typography.labelLarge) },
                icon = { Ico(R.drawable.ic_add, size = 22.dp) },
                onClick = { editing = MealComplex("", "", "", null) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (list.isEmpty()) EmptyState(
                R.drawable.ic_home_meal, "هنوز مجتمعی نیست", Modifier.align(Alignment.Center),
                text = "برنامهٔ دریافت‌شده (فایل .shiftma) را باز کنید تا مجتمع‌ها خودکار ساخته شوند، یا با «افزودن مجتمع» خودتان بسازید و فایل برنامهٔ ماه را بگذارید."
            ) else LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { c ->
                    AppCard(Modifier.fillMaxWidth(), onClick = { if (c.file != null) viewing = c else { pickFor = c.id; picker.launch(DocTypes.PICKER_MIMES) } }) {
                        Column(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBadge(R.drawable.ic_home_meal, size = 44.dp, iconSize = 22.dp, modifier = Modifier)
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (c.file == null) "هنوز فایلی نیست؛ برای گذاشتن برنامه بزنید"
                                        else listOf(c.label, c.file.kind.label).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (c.file != null) {
                                // خود برنامه همین‌جا دیده می‌شود (عکس یا صفحهٔ اول PDF)؛ با زدن روی کارت تمام‌صفحه و قابل بزرگ‌نمایی باز می‌شود
                                FilePreview(vm.fileOf(c.file), c.file.kind, Modifier.padding(end = 8.dp, top = 6.dp))
                                if (c.file.kind != DocKind.SLIDES) HintText("برای دیدن کامل برنامه بزنید", Modifier.padding(top = 2.dp))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                SmallIconButton(R.drawable.ic_upload, "گذاشتن یا جایگزینی فایل برنامه", { pickFor = c.id; picker.launch(DocTypes.PICKER_MIMES) })
                                SmallIconButton(R.drawable.ic_edit, "ویرایش نام و ماه", { editing = c })
                                SmallIconButton(R.drawable.ic_delete, "حذف مجتمع", { deleting = c }, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { c -> c.file?.let { f -> DocViewerDialog("${c.name}${if (c.label.isNotBlank()) " · " + c.label else ""}", vm.fileOf(f), f.kind, f.mime) { viewing = null } } }
    deleting?.let { c ->
        ConfirmDialog("مجتمع «${c.name}» و فایل برنامهٔ آن حذف شود؟", "حذف", onOk = { vm.deleteComplex(c); deleting = null }, onCancel = { deleting = null }, danger = true)
    }
    editing?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        var label by remember(c.id) { mutableStateOf(c.label) }
        AppDialog(
            if (c.id.isEmpty()) "افزودن مجتمع" else "ویرایش مجتمع", { editing = null },
            confirmText = "ذخیره", confirmEnabled = name.isNotBlank(),
            onConfirm = { vm.saveComplex(c.id.ifEmpty { null }, name, label); editing = null }, dismissText = "انصراف"
        ) {
            AppField(name, { name = it }, "نام مجتمع")
            AppField(label, { label = it }, "ماه برنامه (اختیاری)", placeholder = "مثلاً مهر ۱۴۰۵")
        }
    }
}

/** تأیید قبل از ذخیرهٔ بستهٔ برنامه غذایی که از بیرون برنامه باز شده است. */
@Composable
fun ImportMealsDialog(items: List<MealPackItem>, onOk: () -> Unit, onCancel: () -> Unit) {
    AppDialog("برنامه غذایی ${items.size.fa()} مجتمع", onCancel, confirmText = "ذخیره", onConfirm = onOk, dismissText = "انصراف") {
        HintText("فایل برنامه غذایی «${AppInfo.APP_NAME}». مجتمع‌های هم‌نام به‌روز و بقیه اضافه می‌شوند.")
        items.forEach { i ->
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(i.name, style = MaterialTheme.typography.titleSmall)
                Text(listOf(i.label, DocTypes.kindOf(i.fileName, null)?.label.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
        }
    }
}
