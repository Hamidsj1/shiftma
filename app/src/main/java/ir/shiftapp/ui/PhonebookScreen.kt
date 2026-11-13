package ir.shiftapp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.shiftapp.R
import ir.shiftapp.phonebook.Contact
import ir.shiftapp.phonebook.PhoneActions
import ir.shiftapp.phonebook.PhonebookVm
import ir.shiftapp.phonebook.Social
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AVATAR_COLORS = listOf(
    0xFF5C6BC0, 0xFF2E9E78, 0xFF8E5BC7, 0xFFD6477F, 0xFF1FA3A0, 0xFF8D6E63, 0xFFE07B1F, 0xFF4B6CB7
).map { Color(it) }

private fun avatarColor(name: String) = AVATAR_COLORS[(name.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

/** رنگ‌های شمارهٔ همراه (سبز) و محل/داخلی (آبی/بنفش) از پالت مشترک برنامه. */
@Composable
private fun mobileTone(): Pair<Color, Color> =
    if (LocalDark.current) Color(0xFF1B3A33) to Color(0xFF7FE3C7) else Color(0xFFDDF5EE) to Color(0xFF0B6B5E)

@Composable
private fun landlineTone(): Pair<Color, Color> =
    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

@Composable
fun PhonebookSection(vm: PhonebookVm, siteTiles: List<ir.shiftapp.core.SiteTile> = emptyList(), onHome: () -> Unit) {
    val ctx = LocalContext.current
    val query by vm.query.collectAsState()
    val list by vm.contacts.collectAsState()
    val total by vm.total.collectAsState()
    val prefix by vm.prefix.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }

    var detail by remember { mutableStateOf<Contact?>(null) }
    var editor by remember { mutableStateOf<Contact?>(null) }   // id = 0 یعنی مخاطب جدید
    var showManage by remember { mutableStateOf(false) }
    var importChoice by remember { mutableStateOf(false) }       // انتخاب «افزودن» یا «جایگزینی» قبل از باز شدن فایل
    var importReplace by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showPrefix by remember { mutableStateOf(false) }
    var needPrefixFor by remember { mutableStateOf<Contact?>(null) }   // ذخیره در گوشی با پیش‌شمارهٔ خالی
    var deleting by remember { mutableStateOf<Contact?>(null) }
    var confirmExport by remember { mutableStateOf(false) }
    var confirmReload by remember { mutableStateOf(false) }
    var shareChoice by remember { mutableStateOf(false) }        // انتخاب نوع فایل خروجی (.shiftma)
    val shareScope = rememberCoroutineScope()

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importCsv(uri, importReplace) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.exportAll() else vm.message.value = "بدون اجازه نمی‌توان مخاطبین را منتقل کرد"
    }

    BackHandler { if (query.isNotEmpty()) vm.setQuery("") else onHome() }

    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(bottom = 10.dp)) {
                AppTopBar(
                    title = "دفترچه تلفن", leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome,
                    subtitle = if (query.isEmpty()) "${total.fa()} مخاطب" else "${list.size.fa()} نتیجه از ${total.fa()} مخاطب"
                ) {
                    BarIconButton(R.drawable.ic_share, "ارسال فایل مخاطبین", { shareChoice = true })
                    BarIconButton(R.drawable.ic_settings, "مدیریت دفترچه", { showManage = true })
                }
                // پس‌زمینهٔ جدا برای ناحیهٔ جست‌وجو تا از صفحهٔ اصلی متمایز باشد
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp) {
                    SearchField(query, onChange = { vm.setQuery(it) }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("مخاطب جدید", style = MaterialTheme.typography.labelLarge) },
                icon = { Ico(R.drawable.ic_add, size = 22.dp) },
                onClick = { editor = Contact(name = "") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyState(
                    R.drawable.ic_home_phone,
                    if (total == 0) "هنوز مخاطبی نیست" else "نتیجه‌ای پیدا نشد",
                    Modifier.align(Alignment.Center),
                    text = if (total == 0) "با دکمهٔ «مخاطب جدید» اضافه کنید یا با دکمهٔ مدیریت (بالا) فایل CSV را وارد کنید." else "عبارت دیگری را جست‌وجو کنید."
                )
            } else {
                val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                LazyColumn(
                    Modifier.fillMaxSize().thinScrollbar(listState, barColor),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { c -> ContactCard(vm, c, prefix, onClick = { detail = c }) }
                }
            }
        }
    }

    if (showManage) AppDialog("مدیریت دفترچه", { showManage = false }, confirmText = "بستن", onConfirm = { showManage = false }) {
        ManageRow(R.drawable.ic_download, "وارد کردن از فایل CSV", "با عکس‌ها؛ فایل جدید را اضافه یا جایگزین کنید") { showManage = false; importChoice = true }
        ManageRow(R.drawable.ic_share, "ارسال فایل مخاطبین", "فایل .shiftma برای همکاران؛ با زدن روی آن «شیفت ما» باز می‌شود", enabled = total > 0) { showManage = false; shareChoice = true }
        ManageRow(R.drawable.ic_person_add, "انتقال همه به مخاطبین گوشی", "ساخت مخاطب در دفترچهٔ خود گوشی") { showManage = false; confirmExport = true }
        ManageRow(R.drawable.ic_call, "پیش‌شمارهٔ داخلی", if (prefix.isEmpty()) "تنظیم نشده" else "فعلی: $prefix") { showManage = false; showPrefix = true }
        if (vm.hasAsset()) ManageRow(R.drawable.ic_restore, "بارگذاری مجدد از فایل داخلی", "بازگشت به فهرست اولیهٔ برنامه") { showManage = false; confirmReload = true }
        RowDivider()
        ManageRow(R.drawable.ic_delete, "حذف همهٔ مخاطبین", if (total == 0) "دفترچه خالی است" else "${total.fa()} مخاطب پاک می‌شود", danger = true, enabled = total > 0) {
            showManage = false; confirmDeleteAll = true
        }
    }
    if (shareChoice) AppDialog("ارسال فایل مخاطبین", { shareChoice = false }, dismissText = "انصراف") {
        HintText("فایل با پسوند .shiftma ساخته می‌شود. گیرنده فقط کافی است روی آن بزند؛ گوشی او «شیفت ما» را برای باز کردنش پیشنهاد می‌دهد.")
        ManageRow(R.drawable.ic_share, "همهٔ مخاطبین (${total.fa()})", "با عکس‌ها؛ اگر حجم زیاد شود خودکار بدون عکس می‌شود") {
            shareChoice = false; shareScope.launch { shareContactsFile(ctx, vm, null, true) }
        }
        ManageRow(R.drawable.ic_share, "همهٔ مخاطبین، بدون عکس", "فایل سبک‌تر برای فرستادن سریع") {
            shareChoice = false; shareScope.launch { shareContactsFile(ctx, vm, null, false) }
        }
        if (siteTiles.isNotEmpty()) ManageRow(R.drawable.ic_share, "همهٔ مخاطبین + سامانه‌ها (${siteTiles.size.fa()} سامانه)", "گیرنده هر دو را با یک فایل می‌گیرد؛ نام کاربری و رمز در فایل نیست") {
            shareChoice = false
            shareScope.launch { shareContactsFile(ctx, vm, null, true, ir.shiftapp.core.TilesPackFile.toJsonObject(siteTiles, setOf("phonebook"))) }
        }
        if (query.isNotBlank() && list.isNotEmpty()) ManageRow(R.drawable.ic_search, "فقط نتایج جست‌وجو (${list.size.fa()})", "فقط مخاطبینی که الان در فهرست دیده می‌شوند") {
            shareChoice = false; val sel = list; shareScope.launch { shareContactsFile(ctx, vm, sel, true) }
        }
    }
    if (importChoice) AppDialog("وارد کردن از فایل CSV", { importChoice = false }, dismissText = "انصراف") {
        HintText("با فایل جدید چه کنیم؟")
        ManageRow(R.drawable.ic_add, "افزودن به مخاطبین فعلی", "فقط مخاطبین تازه اضافه می‌شوند؛ تکراری‌ها نادیده گرفته می‌شوند") {
            importChoice = false; importReplace = false; csvPicker.launch(arrayOf("*/*"))
        }
        ManageRow(R.drawable.ic_swap, "جایگزینی همه", "اول همهٔ مخاطبین پاک می‌شوند، بعد فایل وارد می‌شود (قاطی نمی‌شود)", danger = true) {
            importChoice = false; importReplace = true; csvPicker.launch(arrayOf("*/*"))
        }
    }
    if (confirmDeleteAll) ConfirmDialog(
        "همهٔ ${total.fa()} مخاطب برنامه (با عکس‌ها) پاک می‌شود و برگشت ندارد. مخاطبین خود گوشی دست‌نخورده می‌مانند.\n\nمطمئنید؟",
        "حذف همه", title = "حذف همهٔ مخاطبین",
        onOk = { confirmDeleteAll = false; vm.deleteAll() }, onCancel = { confirmDeleteAll = false }, danger = true
    )

    detail?.let { c ->
        DetailDialog(vm, c, prefix,
            onNeedPrefix = { needPrefixFor = c },
            onEdit = { editor = c; detail = null },
            onDelete = { deleting = c; detail = null },
            onClose = { detail = null })
    }
    editor?.let { c -> EditorDialog(vm, c) { editor = null } }

    needPrefixFor?.let { c ->
        AppDialog(
            "پیش‌شمارهٔ داخلی تنظیم نشده", { needPrefixFor = null },
            confirmText = "تنظیم پیش‌شماره", onConfirm = { needPrefixFor = null; showPrefix = true },
            dismissText = "بدون پیش‌شماره", onDismissClick = { needPrefixFor = null; PhoneActions.addToPhone(ctx, c, prefix, vm.photoBytes(c.photo)) }
        ) {
            Text("این مخاطب شمارهٔ ۴ رقمی (داخلی) دارد و بدون پیش‌شماره ذخیره می‌شود. ابتدا پیش‌شماره را تنظیم کنید، یا همین‌طور ذخیره کنید.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (showPrefix) PrefixDialog(prefix, onSave = { vm.setPrefix(it); showPrefix = false }, onClose = { showPrefix = false })

    deleting?.let { c ->
        ConfirmDialog("«${c.name}» حذف شود؟", "حذف", onOk = { vm.delete(c); deleting = null }, onCancel = { deleting = null }, danger = true)
    }
    if (confirmExport) ConfirmDialog(
        "${total.fa()} مخاطب (با امور و واحد) به دفترچهٔ تلفن گوشی اضافه می‌شوند. اگر قبلاً انتقال داده باشید، مخاطبین تکراری ساخته می‌شود." +
            (if (prefix.isBlank()) "\n\nتوجه: پیش‌شمارهٔ داخلی تنظیم نشده و شماره‌های ۴ رقمی بدون پیش‌شماره ذخیره می‌شوند. اول از همین منو پیش‌شماره را تنظیم کنید." else "") + "\n\nادامه؟",
        "ادامه",
        onOk = {
            confirmExport = false
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) vm.exportAll()
            else permission.launch(Manifest.permission.WRITE_CONTACTS)
        },
        onCancel = { confirmExport = false }
    )
    if (confirmReload) ConfirmDialog(
        "همهٔ مخاطبین فعلی (شامل ویرایش‌ها، افزوده‌ها و تصاویر) پاک شود و از فایل داخلی برنامه دوباره خوانده شود؟",
        "بله",
        onOk = { confirmReload = false; vm.reloadFromAsset() },
        onCancel = { confirmReload = false },
        danger = true
    )
}

/** یک ردیف بزرگ و خوانا در پنجرهٔ مدیریت: آیکن، عنوان و توضیح کوتاه. */
@Composable
private fun ManageRow(icon: Int, title: String, subtitle: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tone = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(
            icon, size = 40.dp, iconSize = 20.dp,
            container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            tint = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) tone else Pal.muted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
    }
}

@Composable
private fun ContactAvatar(vm: PhonebookVm, c: Contact, size: Dp) {
    val bmp by produceState<ImageBitmap?>(null, c.photo) {
        value = if (c.photo.isEmpty()) null else withContext(Dispatchers.IO) { vm.thumb(c.photo)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(avatarColor(c.name)), contentAlignment = Alignment.Center) {
            Text(c.name.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

/** فیلد جست‌وجو: همان زبان بصری فیلدهای برنامه (کارت گرد با حاشیهٔ ملایم). */
@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(Pal.card).border(1.dp, Pal.hairline, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Ico(R.drawable.ic_search, size = 22.dp, tint = Pal.muted)
        BasicTextField(
            value = query, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("جست‌وجو: نام، شماره، امور یا واحد", style = MaterialTheme.typography.bodyMedium, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) SmallIconButton(R.drawable.ic_close, "پاک کردن", { onChange("") })
    }
}

/** نوار پیمایش باریک کنار لیست. */
private fun Modifier.thinScrollbar(state: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (total == 0 || visible.isEmpty() || visible.size >= total) return@drawWithContent
    val h = size.height
    val thumbH = (h * visible.size / total).coerceAtLeast(48.dp.toPx())
    val first = visible.first()
    val frac = (first.index + (-first.offset).toFloat() / first.size.coerceAtLeast(1)) / (total - visible.size).coerceAtLeast(1)
    val top = ((h - thumbH) * frac.coerceIn(0f, 1f))
    drawRoundRect(color, Offset(0f, top), Size(4.dp.toPx(), thumbH), CornerRadius(2.dp.toPx()))
}

@Composable
private fun NumberChip(icon: Int, number: String, tone: Pair<Color, Color>, prefix: String, modifier: Modifier, onUse: () -> Unit = {}) {
    val ctx = LocalContext.current
    Row(
        modifier.height(42.dp).clip(CircleShape).background(tone.first)
            .clickable(role = Role.Button) { onUse(); PhoneActions.dial(ctx, number, prefix) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Ico(icon, size = 18.dp, tint = tone.second)
        Spacer(Modifier.width(8.dp))
        Text(number, maxLines = 1, style = LtrStyle.copy(color = tone.second, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ContactCard(vm: PhonebookVm, c: Contact, prefix: String, onClick: () -> Unit) {
    val mobile = mobileTone()
    val landline = landlineTone()
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactAvatar(vm, c, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOf("امور" to c.dept, "واحد" to c.unit).filter { it.second.isNotBlank() }
                    meta.forEach { (k, v) ->
                        Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // دو خانه: همراه و محل/داخلی؛ اگر فقط یکی باشد در خانهٔ اول می‌نشیند.
            if (c.mobile.isNotBlank() || c.landline.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = buildList {
                        if (c.mobile.isNotBlank()) add(Triple(R.drawable.ic_phone_mobile, c.mobile, mobile))
                        if (c.landline.isNotBlank()) add(Triple(R.drawable.ic_call, c.landline, landline))
                    }
                    chips.forEach { (icon, num, tone) -> NumberChip(icon, num, tone, prefix, Modifier.weight(1f), onUse = { vm.recordUse(c, num) }) }
                    if (chips.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NumberCard(title: String, number: String, prefix: String, tone: Pair<Color, Color>, icon: Int, onUse: () -> Unit = {}) {
    if (number.isBlank()) return
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Pal.muted)
            Text(number, modifier = Modifier.fillMaxWidth(), style = LtrStyle.copy(color = tone.second, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }
        SmallIconButton(R.drawable.ic_copy, "کپی", { PhoneActions.copy(ctx, number) })
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(tone.first).clickable(role = Role.Button) { onUse(); PhoneActions.dial(ctx, number, prefix) },
            contentAlignment = Alignment.Center
        ) { Ico(icon, size = 22.dp, tint = tone.second, desc = "تماس") }
    }
}

@Composable
private fun ActionItem(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Ico(icon, size = 24.dp, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailDialog(vm: PhonebookVm, c: Contact, prefix: String, onNeedPrefix: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val mobile = mobileTone()
    val landline = landlineTone()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.extraLarge, color = Pal.dialog, tonalElevation = 0.dp) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ContactAvatar(vm, c, 88.dp)
                Text(c.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (c.dept.isNotBlank() || c.unit.isNotBlank()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (c.dept.isNotBlank()) Text("امور: ${c.dept}", style = MaterialTheme.typography.bodyMedium, color = Pal.muted, textAlign = TextAlign.Center)
                    if (c.unit.isNotBlank()) Text("واحد: ${c.unit}", style = MaterialTheme.typography.bodyMedium, color = Pal.muted, textAlign = TextAlign.Center)
                }
                NumberCard("همراه", c.mobile, prefix, mobile, R.drawable.ic_call, onUse = { vm.recordUse(c, c.mobile) })
                NumberCard("محل / داخلی", c.landline, prefix, landline, R.drawable.ic_call, onUse = { vm.recordUse(c, c.landline) })
                val links = remember(c) { Social.links(c) }
                if (c.mobile.isNotBlank() || links.isNotEmpty()) {
                    var more by remember { mutableStateOf(false) }
                    val direct = links.filter { it.first == "واتساپ" || it.first == "تلگرام" }
                    val others = links.filter { it.first != "واتساپ" && it.first != "تلگرام" }
                    val searchApps = Social.searchOnly(c)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        if (c.mobile.isNotBlank()) AppChip("پیامک", false, { PhoneActions.sms(ctx, PhoneActions.dialable(c.mobile, prefix)) })
                        direct.forEach { (label, url) -> AppChip(label, false, { PhoneActions.openUrl(ctx, url) }) }
                        if (others.isNotEmpty() || searchApps.isNotEmpty()) Box {
                            AppChip("بیشتر", false, { more = true })
                            DropdownMenu(more, { more = false }) {
                                others.forEach { (label, url) -> DropdownMenuItem(text = { Text(label) }, onClick = { more = false; PhoneActions.openUrl(ctx, url) }) }
                                searchApps.forEach { m -> DropdownMenuItem(text = { Text("${m.label} (جست‌وجوی شماره)") }, onClick = { more = false; PhoneActions.openApp(ctx, m, c.mobile) }) }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Pal.hairline)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                    val blue = MaterialTheme.colorScheme.primary
                    ActionItem(R.drawable.ic_share, "اشتراک", blue) { PhoneActions.share(ctx, c) }
                    ActionItem(R.drawable.ic_person_add, "ذخیره در گوشی", blue) {
                        if (prefix.isBlank() && PhoneActions.hasExtension(c)) onNeedPrefix()
                        else PhoneActions.addToPhone(ctx, c, prefix, vm.photoBytes(c.photo))
                    }
                    ActionItem(R.drawable.ic_edit, "ویرایش", blue, onEdit)
                    ActionItem(R.drawable.ic_delete, "حذف", MaterialTheme.colorScheme.error, onDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorDialog(vm: PhonebookVm, initial: Contact, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var mobile by remember { mutableStateOf(initial.mobile) }
    var landline by remember { mutableStateOf(initial.landline) }
    var dept by remember { mutableStateOf(initial.dept) }
    var unit by remember { mutableStateOf(initial.unit) }
    var photo by remember { mutableStateOf(initial.photo) }
    var error by remember { mutableStateOf<String?>(null) }
    val sm = remember { Social.parse(initial.social) }
    var tgId by remember { mutableStateOf(sm["tg"]?.takeIf { it != "1" } ?: "") }
    var eitaa by remember { mutableStateOf(sm["eitaa"] ?: "") }
    var bale by remember { mutableStateOf(sm["bale"] ?: "") }
    var rubika by remember { mutableStateOf(sm["rubika"] ?: "") }

    fun cancel() {
        if (photo != initial.photo) vm.deletePhotoFile(photo)   // عکس تازه‌ای که ذخیره نشد
        onClose()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val p = vm.savePickedPhoto(uri)
            if (p == null) vm.message.value = "تصویر خوانده نشد"
            else {
                if (photo != initial.photo) vm.deletePhotoFile(photo)
                photo = p
            }
        }
    }

    AppDialog(
        if (initial.id == 0L) "مخاطب جدید" else "ویرایش مخاطب", { cancel() },
        confirmText = "ذخیره", dismissText = "انصراف", onDismissClick = { cancel() },
        onConfirm = {
            if (name.isBlank()) error = "نام را وارد کنید"
            else {
                vm.save(if (initial.id == 0L) null else initial, initial.copy(name = name, mobile = mobile, landline = landline, dept = dept, unit = unit, photo = photo, social = Social.encode(buildMap {
                    val tg = Social.cleanId(tgId)
                    if (tg.isNotEmpty()) put("tg", tg)
                    Social.cleanId(eitaa).takeIf { it.isNotEmpty() }?.let { put("eitaa", it) }
                    Social.cleanId(bale).takeIf { it.isNotEmpty() }?.let { put("bale", it) }
                    Social.cleanId(rubika).takeIf { it.isNotEmpty() }?.let { put("rubika", it) }
                })))
                onClose()
            }
        }
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ContactAvatar(vm, initial.copy(name = name.ifBlank { "؟" }, photo = photo), 76.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ picker.launch("image/*") }) { Text(if (photo.isEmpty()) "انتخاب تصویر" else "تغییر تصویر") }
                if (photo.isNotEmpty()) TextButton({
                    if (photo != initial.photo) vm.deletePhotoFile(photo)
                    photo = ""
                }) { Text("حذف تصویر", color = MaterialTheme.colorScheme.error) }
            }
        }
        AppField(name, { name = it; error = null }, "نام و نام خانوادگی", isError = error != null, supporting = error)
        AppField(mobile, { mobile = it }, "همراه", keyboard = KeyboardType.Phone, ltr = true)
        AppField(landline, { landline = it }, "محل (داخلی یا ثابت)", keyboard = KeyboardType.Phone, ltr = true)
        AppField(dept, { dept = it }, "امور")
        AppField(unit, { unit = it }, "واحد")
        HorizontalDivider(color = Pal.hairline)
        var showIds by remember { mutableStateOf(tgId.isNotEmpty() || eitaa.isNotEmpty() || bale.isNotEmpty() || rubika.isNotEmpty()) }
        TextButton({ showIds = !showIds }) { Text(if (showIds) "بستن آیدی پیام‌رسان‌ها" else "آیدی پیام‌رسان‌ها (اختیاری)") }
        if (showIds) {
            HintText("واتساپ و تلگرام با شماره کار می‌کنند. آیدی فقط برای ایتا، بله و روبیکا (و در صورت نیاز تلگرام) لازم است.")
            AppField(tgId, { tgId = it }, "آیدی تلگرام", placeholder = "مثلا : ali_123", ltr = true)
            AppField(eitaa, { eitaa = it }, "آیدی ایتا", ltr = true)
            AppField(bale, { bale = it }, "آیدی بله", ltr = true)
            AppField(rubika, { rubika = it }, "آیدی روبیکا", ltr = true)
        }
    }
}

@Composable
private fun PrefixDialog(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AppDialog("پیش‌شمارهٔ داخلی", onClose, confirmText = "ذخیره", onConfirm = { onSave(text) }, dismissText = "انصراف") {
        HintText(
            "این پیش‌شماره قبل از شماره‌های ۴ رقمی (داخلی) اضافه می‌شود. بقیهٔ شماره‌ها (موبایل، شمارهٔ کامل، کدهای ۳ رقمی) تغییری نمی‌کنند. " +
                "اگر بعد از وصل‌شدن باید چند ثانیه صبر شود تا داخلی گرفته شود، آخر پیش‌شماره یک ویرگول (,) بگذارید. خالی = بدون پیش‌شماره."
        )
        AppField(text, { text = it }, "پیش‌شماره", placeholder = "مثلاً +98211234", ltr = true)
    }
}


/** تأیید قبل از وارد کردن مخاطبین فایل .shiftma که از بیرون برنامه باز شده است. */
@Composable
fun ImportContactsDialog(
    list: List<Contact>, onAdd: (Boolean) -> Unit, onReplace: (Boolean) -> Unit, onCancel: () -> Unit,
    prefix: String = "", currentPrefix: String = ""
) {
    val photos = list.count { it.photoData.isNotEmpty() }
    // پیش‌شمارهٔ فرستنده فقط وقتی پیشنهاد می‌شود که با پیش‌شمارهٔ فعلی فرق داشته باشد
    val offerPrefix = prefix.isNotEmpty() && prefix != currentPrefix
    var applyPrefix by remember { mutableStateOf(true) }
    AppDialog("${list.size.fa()} مخاطب در این فایل است", onCancel, dismissText = "انصراف") {
        HintText("فایل مخاطبین «${AppInfo.APP_NAME}»" + if (photos > 0) " (با ${photos.fa()} عکس)" else "")
        list.take(3).forEach { c ->
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp)
            ) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOf(c.dept, c.unit, c.mobile, c.landline).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (list.size > 3) HintText("و ${(list.size - 3).fa()} مخاطب دیگر")
        if (offerPrefix) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(role = Role.Checkbox) { applyPrefix = !applyPrefix }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(applyPrefix, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    Text("تنظیم پیش‌شمارهٔ داخلی: $prefix", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (currentPrefix.isEmpty()) "هم‌اکنون پیش‌شماره تنظیم نشده" else "جایگزین پیش‌شمارهٔ فعلی ($currentPrefix) می‌شود",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
            }
        }
        ManageRow(R.drawable.ic_add, "افزودن به مخاطبین فعلی", "فقط مخاطبین تازه اضافه می‌شوند؛ تکراری‌ها نادیده گرفته می‌شوند", onClick = { onAdd(offerPrefix && applyPrefix) })
        ManageRow(R.drawable.ic_swap, "جایگزینی همه", "اول همهٔ مخاطبین فعلی پاک می‌شوند، بعد این فایل وارد می‌شود", danger = true, onClick = { onReplace(offerPrefix && applyPrefix) })
    }
}
