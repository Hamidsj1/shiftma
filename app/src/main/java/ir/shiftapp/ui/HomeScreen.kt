package ir.shiftapp.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ir.shiftapp.R
import ir.shiftapp.core.AppSearch
import ir.shiftapp.core.SearchItem
import ir.shiftapp.system.ConfigLoader
import ir.shiftapp.core.DateInfo
import ir.shiftapp.core.DayKind
import ir.shiftapp.core.DayResolver
import ir.shiftapp.core.HomeLayout
import ir.shiftapp.core.JalaliCalendar
import ir.shiftapp.core.Occasions
import ir.shiftapp.core.Fa
import ir.shiftapp.core.PrefKeys
import ir.shiftapp.core.SiteIcon
import ir.shiftapp.core.SiteTile
import ir.shiftapp.core.Strip
import ir.shiftapp.core.StripSites
import ir.shiftapp.docs.DocsVm
import ir.shiftapp.docs.MealComplex
import kotlinx.coroutines.flow.MutableStateFlow
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.ShiftPattern

/**
 * یک بخش از صفحهٔ اول. برای افزودن بخش جدید: docs/ADDING_SECTIONS.md
 * ready = false یعنی کاشی کم‌رنگ است و با لمس آن پیام «به‌زودی» نشان داده می‌شود.
 * secret = true قبلاً یعنی کاشی مخفی با رمز روزانه؛ قفل برداشته شد و همهٔ کاشی‌ها همیشه دیده می‌شوند (شناسهٔ باقی مانده برای سازگاری).
 */
data class HomeSection(val id: String, val title: String, val subtitle: String, val icon: Int, val ready: Boolean, val logo: Boolean = false, val secret: Boolean = false, val logoBytes: ByteArray? = null, val iconKey: String? = null)

/** سامانه‌های صفحهٔ اول پیش‌فرض ندارند؛ از «فایل کاشی‌ها» می‌آیند (core/TilesPack.kt و تنظیمات). */

internal fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (e: Exception) { Toast.makeText(ctx, "بازکردن سایت ممکن نشد", Toast.LENGTH_SHORT).show() }
}

private const val ADD_SITE_ID = "__add_site__"

/** فاصلهٔ استاندارد زیر هر بخش دیدنی صفحهٔ اول. */
private val GAP = 10.dp

/** وقتی DocsVm نیست (مثلاً پیش‌نمایش): فهرست خالی مجتمع‌های برنامه غذایی. */
private val NO_MEALS = MutableStateFlow<List<MealComplex>>(emptyList())

/**
 * پنجرهٔ افزودن یا ویرایش یک سایت؛ [onAdd] متن خطا یا null (موفق) برمی‌گرداند.
 * برای ویرایش، [initialTitle] و [initialUrl] را بدهید؛ [showSubtitle] = false فیلد توضیح را برمی‌دارد (نوارهای ضروری و تلویزیون توضیح ندارند).
 */
@Composable
internal fun AddSiteDialog(
    onAdd: (title: String, url: String, subtitle: String) -> String?, onDismiss: () -> Unit,
    heading: String = "افزودن سایت", confirmLabel: String = "افزودن",
    initialTitle: String = "", initialUrl: String = "", showSubtitle: Boolean = true, initialSubtitle: String = ""
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var sub by rememberSaveable { mutableStateOf(initialSubtitle) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("عنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it; error = null }, label = { Text("آدرس (مثل example.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri)
                )
                if (showSubtitle) OutlinedTextField(sub, { sub = it; error = null }, label = { Text("توضیح (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton({ val e = onAdd(title, url, sub); if (e == null) onDismiss() else error = e }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}

/** بخش‌های کم‌استفاده که از شبکهٔ صفحهٔ اول برداشته می‌شوند و داخل کشوی «بخش‌های دیگر» می‌آیند. */
internal val DRAWER_SECTION_IDS = setOf("prayer", "pay", "sport", "reading", "english", "dhikr", "convert", "age", "datecv", "span")

@Composable
fun HomeScreen(vm: MainVm, onOpen: (String) -> Unit, onLink: (String) -> Unit, pbVm: ir.shiftapp.phonebook.PhonebookVm? = null, docsVm: DocsVm? = null) {
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val noteCount = prefs.notes.size
    val remindCount = prefs.notes.count { it.reminderAt != null }
    val notesSub = if (noteCount == 0) "متن، چک‌لیست و یادآور"
        else "${Fa.num(noteCount)} یادداشت" + if (remindCount > 0) " · ${Fa.num(remindCount)} یادآور" else ""
    val eng by vm.english.collectAsState()
    val engDue = remember(eng) { eng.dueIds(java.time.LocalDate.now().toEpochDay().toInt()).size }
    val engSub = if (engDue > 0) "${Fa.num(engDue)} لغت برای مرور" else "لغت روز، فلش‌کارت و آزمون"
    val prayerSub = rememberNextPrayerLine(prefs)
    val special by vm.specialOn.collectAsState()
    // همهٔ بخش‌ها (حتی مخفی‌ها و اوقات شرعی)؛ دیدنی بودن بعد از اعمال ترتیب کاربر فیلتر می‌شود
    val sectionsAllRaw = remember(notesSub, engSub, prayerSub) {
        listOf(
            // ردیف اول: فیش‌های من (دفترچه تلفن به گروه «سامانه‌ها» رفت و مثل برنامه غذایی پیش‌فرض پنهان است)
            HomeSection("payslips", "فیش‌های من", "مشاهده و مقایسهٔ فیش‌های دانلودشده", R.drawable.ic_home_slip, true),
            HomeSection("shift", "جدول شیفت", "تقویم، گروه‌ها، گزارش و مرخصی", R.drawable.ic_tab_calendar, true),
            HomeSection("notes", "یادداشت‌ها", notesSub, R.drawable.ic_home_notes, true),
            HomeSection("files", "فایل‌های من", "عکس، PDF و فایل نمایشی", R.drawable.ic_home_files, true),
            HomeSection("income", "دریافتی‌های ماهانه", "ثبت حقوق، پاداش و سایر دریافتی‌ها", R.drawable.ic_home_income, true),
            HomeSection("prayer", "اوقات شرعی و قبله", prayerSub ?: "اذان، قبله و هشدار", R.drawable.ic_qibla, true),
            // این‌ها از شبکهٔ صفحهٔ اول برداشته شده و فقط در کشوی «بخش‌های دیگر» می‌آیند
            HomeSection("english", "یادگیری انگلیسی", engSub, R.drawable.ic_home_english, true),
            HomeSection("pay", "حقوق و اضافه‌کار", "برآورد اضافه‌کاری، شب‌کاری و جمعه‌کاری", R.drawable.ic_pay, true),
            HomeSection("sport", "ورزش و تغذیه", "حرکات، برنامه تمرینی و رژیم", R.drawable.ic_home_sport, true),
            HomeSection("reading", "مطالعه و کتابخوانی", "کتاب، دانلود، هدف روزانه و تایمر", R.drawable.ic_home_reading, true),
            HomeSection("dhikr", "ذکر، قضا و رمضان", "ذکرشمار، رکعت‌شمار، نماز قضا و افطار", R.drawable.ic_home_dhikr, true)
        )
    }
    // تنظیم بخش‌های این محل کار (گیت‌هاب، config.json): بخش پنهان همه‌جا (شبکه، کشو، جست‌وجو) برداشته می‌شود؛ بخش پولی دیده می‌شود و با لمس صفحهٔ قفل باز می‌شود
    val remote by ConfigLoader.config.collectAsState()
    val sectionsAll = remember(sectionsAllRaw, remote) { sectionsAllRaw.filter { remote.visible(it.id) } }
    // ابزارها از صفحهٔ اول برداشته شده‌اند و فقط داخل کشوی «بخش‌های دیگر» (زیر عنوان «ابزارها») هستند
    val tools = remember {
        listOf(
            HomeSection("convert", "تبدیل واحد", "طول، وزن، دما، حجم و …", R.drawable.ic_swap, true),
            HomeSection("age", "محاسبهٔ سن", "سن دقیق و تولد بعدی", R.drawable.ic_today, true),
            HomeSection("datecv", "مبدل تاریخ", "شمسی، میلادی و قمری", R.drawable.ic_tab_calendar, true),
            HomeSection("span", "محاسبهٔ مدت", "از تاریخ تا تاریخ؛ سال، ماه، روز و سابقه بیمه", R.drawable.ic_today, true),
            HomeSection("imgcompress", "کاهش حجم عکس", "کم‌کردن حجم برای ارسال و آپلود", R.drawable.ic_image, true)
        )
    }
    // تنظیمات و درباره (اشتراک‌گذاری برنامه) در کشوی اصلی؛ همان صفحه‌های کشوی «جدول شیفت»
    val appItems = remember {
        listOf(
            HomeSection("settings", "تنظیمات", "ساعت شیفت، یادآور، تم، پشتیبان", R.drawable.ic_settings, true),
            HomeSection("about", "درباره و اشتراک‌گذاری", "معرفی برنامه و ارسال برای دوستان", R.drawable.ic_info, true)
        )
    }
    // «دفترچه تلفن» و «برنامه غذایی مجتمع» کاشی‌های مخفیِ گروه سامانه‌ها هستند: پیش‌فرض دیده نمی‌شوند و فقط وقتی فایل .shiftma
    // در بخش sections خودشان را بخواهد (یا دسترسی ویژه روشن باشد) نمایان می‌شوند؛ مثل بقیهٔ کاشی‌ها جابه‌جا می‌شوند
    val phonebookTile = remember { HomeSection("phonebook", "دفترچه تلفن", "مخاطبین، جست‌وجو و تماس", R.drawable.ic_home_phone, true, secret = true) }
    val mealsTile = remember { HomeSection("meals", "برنامه غذایی", "منوی ماه مجتمع‌ها", R.drawable.ic_home_meal, true, secret = true) }
    val links = remember(prefs.sitesRaw, phonebookTile, mealsTile) {
        TilesPackFile.decodeSites(prefs.sitesRaw).map { HomeSection(it.url, it.title, it.subtitle, R.drawable.ic_open_new, true, logo = true, logoBytes = it.logo, iconKey = it.key) } + phonebookTile + mealsTile
    }
    val enabledSections by vm.enabledSections.collectAsState()

    // ---- جست‌وجوی سراسری: بخش‌های دیدنی + ابزارها + سامانه‌ها + یادداشت‌ها ----
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchItems = remember(sectionsAll, tools, links, prefs.notes, prefs.prayerOn, special, enabledSections) {
        val sec = sectionsAll
            .filter { (!it.secret || special || it.id in enabledSections) && ((it.id != "prayer" && it.id != "dhikr") || prefs.prayerOn) }
            .map { SearchItem(it.id, it.title, it.subtitle, AppSearch.keywordsFor(it.id), "بخش") }
        val tl = tools.map { SearchItem(it.id, it.title, it.subtitle, AppSearch.keywordsFor(it.id), "ابزار") }
        val sites = links.filter { !it.secret || special || it.id in enabledSections }
            .map { if (it.secret) SearchItem(it.id, it.title, it.subtitle, AppSearch.keywordsFor(it.id), "سامانه") else SearchItem(it.id, it.title, it.subtitle, listOf("سامانه", "سایت", "پورتال"), "سامانه") }
        val notes = prefs.notes.map { n ->
            val title = n.title.ifBlank { n.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { "یادداشت بدون عنوان" }
            SearchItem("notes", title.take(60), n.body.replace('\n', ' ').take(80), emptyList(), "یادداشت")
        }
        sec + tl + sites + notes
    }

    // ---- چیدمان دلخواه: ترتیب کاشی‌ها و گروه‌ها (در پشتیبان هم می‌آید) ----
    var editing by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val layout = remember(prefs.homeLayoutRaw) { HomeLayout.migrate(HomeLayout.parse(prefs.homeLayoutRaw)) }
    fun saveLayout(m: Map<String, List<String>>) = vm.setPref(PrefKeys.HOME_LAYOUT, HomeLayout.encode(m))
    fun ordered(key: String, all: List<HomeSection>, visible: (HomeSection) -> Boolean): List<HomeSection> {
        val byId = all.associateBy { it.id }
        return HomeLayout.order(all.map { it.id }, layout[key]).mapNotNull { byId[it] }.filter(visible)
    }
    fun reorderTiles(key: String, all: List<HomeSection>, newVisible: List<String>) {
        val full = HomeLayout.order(all.map { it.id }, layout[key])
        saveLayout(layout + (key to HomeLayout.reorderVisible(full, newVisible)))
    }
    val sections = ordered("sections", sectionsAll) { (!it.secret || special || it.id in enabledSections) && (it.id != "prayer" || prefs.prayerOn) && it.id !in DRAWER_SECTION_IDS }
    val linkList = ordered("links", links) { !it.secret || special || it.id in enabledSections }
    var addSiteOpen by rememberSaveable { mutableStateOf(false) }
    // نوارهای «سایت‌های ضروری» و «تلویزیون»: فهرست قابل ویرایش (افزودن، ویرایش، جابه‌جایی، حذف)
    val essSites = remember(prefs.essListRaw, prefs.essSitesRaw) { StripSites.resolve(Strip.ESSENTIAL, prefs.essListRaw, prefs.essSitesRaw) }
    val tvSites = remember(prefs.tvListRaw) { StripSites.resolve(Strip.TV, prefs.tvListRaw) }
    val essUi = rememberStripUi()
    val tvUi = rememberStripUi()
    val blocks = HomeLayout.order(HomeLayout.BLOCKS, layout["blocks"])      // گروه «سامانه‌ها» همیشه هست تا کاشی «افزودن سایت» دیده شود
    fun reorderBlocks(newVisible: List<String>) {
        val full = HomeLayout.order(HomeLayout.BLOCKS, layout["blocks"])
        saveLayout(layout + ("blocks" to HomeLayout.reorderVisible(full, newVisible)))
    }
    fun moveBlock(id: String, delta: Int) {
        val full = HomeLayout.order(HomeLayout.BLOCKS, layout["blocks"])
        saveLayout(layout + ("blocks" to HomeLayout.moveVisible(full, blocks, id, delta)))
    }
    val blockDrag = remember { DragReorder() }
    val (shownBlocks, rememberBlockDrop) = rememberShownOrder(blockDrag, blocks)
    AutoScrollWhileDragging(blockDrag, scroll)

    var shareOpen by remember { mutableStateOf(false) }
    val mealList by (docsVm?.complexes ?: NO_MEALS).collectAsState()
    val shareScope = rememberCoroutineScope()
    val sharePatterns by vm.patterns.collectAsState()
    if (shareOpen) {
        SelectiveShareDialog(
            sharePatterns, TilesPackFile.decodeSites(prefs.sitesRaw),
            // مخاطبین فقط وقتی دفترچه تلفن در دسترس است (رمز ویژه یا فایل کاشی‌ها) پیشنهاد می‌شوند
            loadContacts = if (pbVm != null && (special || "phonebook" in enabledSections)) ({ pbVm.allContacts() }) else null,
            // برنامه غذایی هم مثل مخاطبین فقط وقتی بخشش در دسترس است و دست‌کم یک مجتمع فایل دارد پیشنهاد می‌شود
            meals = if (docsVm != null && (special || "meals" in enabledSections)) mealList.filter { it.file != null } else null,
            onSend = { ps, ss, cs, ms -> shareOpen = false; shareScope.launch { shareBundleFile(ctx, ps, ss, cs, pbVm, docsVm?.mealItemsOf(ms).orEmpty(), ir.shiftapp.phonebook.PhoneActions.loadPrefix(ctx)) } },
            onCancel = { shareOpen = false }
        )
    }
    StripDialogs(vm, Strip.ESSENTIAL, essSites, essUi)
    StripDialogs(vm, Strip.TV, tvSites, tvUi)
    if (addSiteOpen) AddSiteDialog(onAdd = { t, u, sub -> vm.addSite(t, u, sub) }, onDismiss = { addSiteOpen = false })
    if (searchOpen) AppSearchDialog(
        searchItems,
        onPick = { r -> searchOpen = false; if (r.id.startsWith("http")) onLink(r.id) else onOpen(r.id) },
        onDismiss = { searchOpen = false }
    )
    RatePromptHost(hasData = vm.patterns.collectAsState().value.isNotEmpty())
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val importShiftma = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openPatternFile) }
    BackHandler(enabled = drawer.isOpen) { drawerScope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !editing,      // موقع چیدن کاشی‌ها کشیدن نباید کشو را باز کند
        drawerContent = {
            ModalDrawerSheet {
              Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("بخش‌های دیگر", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))
                sectionsAll.filter { it.id in DRAWER_SECTION_IDS && (it.id != "dhikr" || prefs.prayerOn) }.forEach { s ->
                    DrawerSectionItem(s) { drawerScope.launch { drawer.close() }; onOpen(s.id) }
                }
                Text("ابزارها", style = MaterialTheme.typography.labelLarge, color = Pal.muted, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 4.dp))
                tools.forEach { t ->
                    DrawerSectionItem(t) { drawerScope.launch { drawer.close() }; onOpen(t.id) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("وارد کردن فایل شیفت‌ما", style = MaterialTheme.typography.titleSmall)
                            Text("فایل .shiftma: الگو، مخاطبین، برنامه غذایی، کاشی‌ها", style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2)
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    selected = false,
                    onClick = { drawerScope.launch { drawer.close() }; importShiftma.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("ارسال انتخابی", style = MaterialTheme.typography.titleSmall)
                            Text("الگوها، سامانه‌ها و مخاطبین؛ هرکدام را خودت انتخاب کن", style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2)
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    selected = false,
                    onClick = { drawerScope.launch { drawer.close() }; shareOpen = true },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Text("برنامه", style = MaterialTheme.typography.labelLarge, color = Pal.muted, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 4.dp))
                appItems.forEach { t ->
                    DrawerSectionItem(t) { drawerScope.launch { drawer.close() }; onOpen(t.id) }
                }
                Spacer(Modifier.height(16.dp))
              }
            }
        }
    ) {
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val callBtn = remember { UiFlags.callButton(ctx) }
    Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)      // فاصله‌ها دستی و فقط برای بخش‌های دیده‌شده؛ spacedBy بین کامپوزبل‌های خالی هم فاصله می‌گذاشت (فضای پرت)
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(R.drawable.ic_menu, "بخش‌های دیگر", { drawerScope.launch { drawer.open() } })
            Text("بخش‌های دیگر", style = MaterialTheme.typography.labelLarge, color = Pal.muted,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { drawerScope.launch { drawer.open() } }.padding(horizontal = 6.dp, vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            SmallIconButton(R.drawable.ic_search, "جست‌وجو", { searchOpen = true })
        }
        RingingBanner(Modifier.padding(bottom = GAP))      // فقط وقتی آلارمی زنگ می‌زند: دکمهٔ بزرگ قطع، حتی اگر اعلان دیده نشود
        val patterns by vm.patterns.collectAsState()
        val patternsLoaded by vm.patternsLoaded.collectAsState()
        // کارت بالا + نوار باریک شیفت در جریان (فقط وقتی شیفتی در جریان است؛ وگرنه هیچ فضایی نمی‌گیرد)
        Column(Modifier.padding(bottom = GAP)) {
            HomeHero(vm) { onOpen("shift") }
            ShiftProgressStrip(vm, Modifier.padding(top = 6.dp))
        }
        // سامانه‌ها: نوار روشن افقی درست زیر کارت بالا و بالای تقویم (در حالت «چیدمان» همان گروه پایین برای جابه‌جایی دیده می‌شود)
        if (!editing && remote.visible("links")) Column(Modifier.padding(bottom = GAP)) {
            if (linkList.isEmpty()) {
                // سامانه‌ای نیست: فقط یک سطر باریک، نه کاشی بزرگ «افزودن»
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("سامانه‌ها", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton({ addSiteOpen = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+ افزودن سامانه", style = MaterialTheme.typography.labelLarge) }
                }
            } else {
                LinksStrip(linkList, onAdd = { addSiteOpen = true }) { s -> if (s.secret) onOpen(s.id) else onLink(s.id) }
            }
        }
        // راهنمای فعال‌سازی مجوزها (اعلان، باتری، قفل‌صفحه): کارت کوچک تا وقتی موردی مانده؛ یک‌بار هم خودکار باز می‌شود
        if (patternsLoaded && patterns.isNotEmpty()) PermissionGuideEntry(vm, prefs, Modifier.padding(bottom = GAP))
        // نصب تازه: به‌جای تقویم خالی، الگوهای پیشنهادی را همین‌جا نشان بده
        if (patternsLoaded && patterns.isEmpty()) QuickStartCard(vm, Modifier.padding(bottom = GAP)) else {
            // تقویم + نوار باریک آلارم/یادآوری‌ها درست زیرش
            Column(Modifier.padding(bottom = GAP)) {
                HomeMonthCard(vm) { onOpen("shift") }
                ReminderStrip(vm, Modifier.padding(top = 6.dp))
            }
        }
        UpcomingNotes(prefs, Modifier.padding(bottom = GAP)) { onOpen("notes") }
        LayoutBar(editing, { editing = true }, { saveLayout(emptyMap()) }, { editing = false })
        ReorderLayout(columns = 1, spacing = 12.dp, dr = blockDrag) {
            shownBlocks.filter { editing || it != "links" }.forEach { id ->
                key(id) {
                    val bi = shownBlocks.indexOf(id)
                    val up = bi > 0
                    val down = bi < shownBlocks.size - 1
                    // کشیدن گروه: لمس طولانی روی عنوانش
                    val handle = if (editing) Modifier.dragHandle(blockDrag, id, { blocks }) { o -> rememberBlockDrop(o); reorderBlocks(o) } else Modifier
                    Column(Modifier.reorderItem(blockDrag, id), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (id) {
                            "sections" -> {
                                BlockHeader("بخش‌ها", editing, up, down, { moveBlock(id, -1) }, { moveBlock(id, 1) }, handle)
                                if (editing) TileGrid(sections, compact = false, editing = true, scroll = scroll, onReorder = { reorderTiles("sections", sectionsAll, it) }) { }
                                else SectionsStrip(sections) { s -> if (s.ready) onOpen(s.id) else Toast.makeText(ctx, "این بخش به‌زودی اضافه می‌شود", Toast.LENGTH_SHORT).show() }
                                if (!editing && !prefs.prayerOn && !prefs.prayerHintHidden) {
                                    // پیشنهاد اختیاری: فقط تا وقتی کاربر فعالش نکرده یا پنهانش نکرده دیده می‌شود
                                    AppCard(Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            IconBadge(R.drawable.ic_qibla, size = 44.dp, iconSize = 24.dp, modifier = Modifier)
                                            Column(Modifier.weight(1f)) {
                                                Text("اوقات شرعی، قبله و اذان", style = MaterialTheme.typography.titleSmall)
                                                Text("اختیاری؛ اگر نیاز ندارید فعال نکنید.", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                                            }
                                            TextButton({ vm.setPref(PrefKeys.PRAYER_ON, "true") }) { Text("فعال‌سازی") }
                                            SmallIconButton(R.drawable.ic_close, "دیگر نشان نده", { vm.setPref(PrefKeys.PRAYER_HINT_HIDDEN, "true") })
                                        }
                                    }
                                }
                            }
                            "essential" -> {
                                BlockHeader("سایت‌های ضروری", editing, up, down, { moveBlock(id, -1) }, { moveBlock(id, 1) }, handle)
                                EssentialSitesStrip(essSites, essUi, onAll = { onOpen("sites") })
                            }
                            "tv" -> {
                                BlockHeader("تلویزیون", editing, up, down, { moveBlock(id, -1) }, { moveBlock(id, 1) }, handle)
                                TvStrip(tvSites, tvUi, onAll = { onOpen("tv") })
                            }
                            "links" -> if (editing) {
                                BlockHeader("سامانه‌ها", editing, up, down, { moveBlock(id, -1) }, { moveBlock(id, 1) }, handle)
                                // کاشی «افزودن سایت» آخر فهرست است و موقع چیدن (جابه‌جایی) دیده نمی‌شود
                                TileGrid(linkList, compact = true, editing = true, scroll = scroll, onReorder = { reorderTiles("links", links, it) }) { }
                            }
                            "widget" -> {
                                BlockHeader("ویجت صفحهٔ اصلی گوشی", editing, up, down, { moveBlock(id, -1) }, { moveBlock(id, 1) }, handle)
                                WidgetButtons()
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    // دکمهٔ دم‌دستِ دفترچه تلفن: پایین-چپ صفحه، فقط وقتی بخش در دسترس است (رمز ویژه یا فایل کاشی‌ها)
    if (callBtn && !editing && (special || "phonebook" in enabledSections) && remote.visible("phonebook")) {
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        androidx.compose.material3.FloatingActionButton(
            onClick = { onOpen("phonebook") },
            modifier = Modifier.align(if (rtl) Alignment.BottomEnd else Alignment.BottomStart).padding(16.dp).size(64.dp)
                .semantics { contentDescription = "تماس و دفترچه تلفن" },
            shape = CircleShape,
            containerColor = TODAY_BG,
            contentColor = Color.White
        ) { Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(32.dp)) }
    }
    }
    if (!landscape) AdPanel(prefs.adFeed, prefs.adPublic)      // پنل ثابت پایین؛ در حالت افقی ارتفاع را می‌خورد و پنهان است
    }
    }
}

/** یک ردیف کشوی «بخش‌های دیگر» (بخش یا ابزار). */
@Composable
private fun DrawerSectionItem(s: HomeSection, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(s.title, style = MaterialTheme.typography.titleSmall)
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1)
            }
        },
        icon = { Icon(painterResource(s.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

// ───────────────────────── چیدمان دلخواه صفحهٔ اول ─────────────────────────

/** دکمهٔ ورود به «چیدمان صفحه»؛ در حالت ویرایش، راهنما با «بازنشانی» و «پایان». */
@Composable
private fun LayoutBar(editing: Boolean, onStart: () -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    if (!editing) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onStart, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Ico(R.drawable.ic_edit, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("چیدمان صفحه")
            }
        }
    } else {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حالت چیدمان", style = MaterialTheme.typography.titleSmall)
                HintText("هر کاشی را نگه دار و به جای دلخواه بکش. عنوان هر گروه را هم می‌شود نگه داشت و کشید؛ ▲ ▼ کنار عنوان هم کل گروه را جابه‌جا می‌کند.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("بازنشانی", onReset, Modifier.weight(1f), style = BtnStyle.Outline)
                    AppButton("پایان", onDone, Modifier.weight(1f))
                }
            }
        }
    }
}

/** عنوان گروه؛ در حالت چیدمان با لمس طولانی روی عنوان (برچسب [dragMod]) کل گروه کشیده می‌شود و ▲ ▼ هم دارد. */
@Composable
private fun BlockHeader(title: String, editing: Boolean, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, dragMod: Modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).then(dragMod).padding(vertical = if (editing) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) Text("⠿", style = MaterialTheme.typography.titleMedium, color = Pal.muted)
            SectionLabel(title)
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MoveChip("▲", "گروه را بالا ببر", canUp, onUp)
                MoveChip("▼", "گروه را پایین ببر", canDown, onDown)
            }
        }
    }
}

@Composable
private fun MoveChip(glyph: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, style = MaterialTheme.typography.titleSmall) }
}

/** کاشی در حالت چیدمان: آیکن و عنوان و نشانهٔ کشیدن؛ با لمس طولانی کشیده می‌شود. */
@Composable
private fun EditTile(s: HomeSection) {
    AppCard(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.logo) SiteLogo(s, 32.dp, 10.dp) else IconBadge(s.icon, size = 32.dp, iconSize = 18.dp, modifier = Modifier)
            Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⠿", style = MaterialTheme.typography.titleMedium, color = Pal.muted)
        }
    }
}

/** کارت بالای صفحهٔ اول: تاریخ امروز و شیفت امروز/فردای الگوی انتخاب‌شده؛ با لمس، بخش جدول شیفت باز می‌شود. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeHero(vm: MainVm, onOpen: () -> Unit) {
    val p by vm.selected.collectAsState()
    val loaded by vm.patternsLoaded.collectAsState()
    val all by vm.entriesAllPatterns.collectAsState()
    val today = remember { JalaliDate.today() }
    val tomorrow = remember { today.plusDays(1) }
    fun codeOn(d: JalaliDate): Int? = p?.let { pat ->
        runCatching {
            DayResolver.resolve(pat.base(), ShiftPattern.parse(pat.pattern), d, all[pat.id]?.get(d.key).orEmpty()).effectiveCode
        }.getOrNull()
    }
    val onHero = Color.White
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Pal.heroBrush)
            .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painterResource(R.drawable.app_logo), contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(AppInfo.APP_NAME, style = MaterialTheme.typography.titleLarge, color = onHero, maxLines = 1)
                        val cur = p
                        if (cur != null) {
                            // عنوان شیفت (الگوی انتخاب‌شده) با امکان تغییر؛ همان انتخابِ داخل بخش جدول شیفت است
                            val all by vm.patterns.collectAsState()
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f))
                                        .clickable(role = Role.Button) { if (all.size > 1) menu = true else onOpen() }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    GroupBadge(cur.name)
                                    Text(groupTitle(cur.name), style = MaterialTheme.typography.labelLarge, color = onHero, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (all.size > 1) Ico(R.drawable.ic_expand_more, size = 16.dp, tint = onHero)
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    all.forEach { pt ->
                                        DropdownMenuItem(
                                            text = { Text(groupTitle(pt.name)) },
                                            leadingIcon = if (pt.id == cur.id) { { Ico(R.drawable.ic_check, size = 20.dp) } } else null,
                                            onClick = { vm.select(pt.id); menu = false }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("مدیریت الگوها") },
                                        leadingIcon = { Ico(R.drawable.ic_patterns, size = 20.dp) },
                                        onClick = { menu = false; onOpen() }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "${WEEK_FULL[today.dayOfWeek]} ${today.day.fa()} ${MONTHS[today.month - 1]} ${today.year.fa()}",
                        style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.82f)
                    )
                    val heroPrefs by vm.prefs.collectAsState()
                    val gregText = remember(today) { DateInfo.gregorianText(today.toLocalDate()) }
                    val hijriText = remember(today, heroPrefs.lunarShift) {
                        DateInfo.hijri(today.toLocalDate(), heroPrefs.lunarShift)?.let { DateInfo.hijriText(it) }
                    }
                    Text(
                        if (hijriText != null) "$gregText · $hijriText" else gregText,
                        style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    rememberNextPrayerLine(heroPrefs)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                run {
                    // آلارم شیفت: همان پنجرهٔ بالای بخش جدول شیفت؛ روشن = دایرهٔ سفید، خاموش = دایرهٔ کم‌رنگ
                    val prefs by vm.prefs.collectAsState()
                    var alarmOpen by remember { mutableStateOf(false) }
                    val alarmOn = prefs.reminderEnabled && prefs.reminderAlarm
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (alarmOn) Color.White else Color.White.copy(alpha = 0.18f))
                            .clickable(role = Role.Button) { alarmOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Ico(
                            if (alarmOn) R.drawable.ic_alarm else R.drawable.ic_alarm_off, size = 22.dp, desc = "آلارم شیفت",
                            tint = if (alarmOn) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    if (alarmOpen) ShiftAlarmDialog(vm, p, prefs) { alarmOpen = false }
                    val shareCtx = androidx.compose.ui.platform.LocalContext.current
                    val shareScope = rememberCoroutineScope()
                    // دکمهٔ اشتراک: خودِ برنامه (APK) را می‌فرستد، نه فقط متن معرفی
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable(role = Role.Button) { shareScope.launch { shareApk(shareCtx) } },
                        contentAlignment = Alignment.Center
                    ) { Ico(R.drawable.ic_share, size = 20.dp, desc = "اشتراک‌گذاری برنامه", tint = Color.White) }
                }
            }
            if (loaded && p != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroShift("امروز", codeOn(today), Modifier.weight(1f))
                    HeroShift("فردا", codeOn(tomorrow), Modifier.weight(1f))
                }
            } else if (loaded) {
                Text(
                    "برای شروع، از کارت زیر الگوی شیفت خود را انتخاب کنید.",
                    style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun HeroShift(label: String, code: Int?, modifier: Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Dot(if (code != null) shiftColor(code) else Color.White.copy(alpha = 0.4f), 10.dp)
        Text(if (code != null) shiftName(code) else "—", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun TileGrid(
    items: List<HomeSection>, compact: Boolean, editing: Boolean = false, scroll: androidx.compose.foundation.ScrollState,
    onReorder: (List<String>) -> Unit = {}, onClick: (HomeSection) -> Unit
) {
    val dr = remember { DragReorder() }
    AutoScrollWhileDragging(dr, scroll)
    val ids = items.map { it.id }
    val byId = items.associateBy { it.id }
    val (shown, rememberDrop) = rememberShownOrder(dr, ids)
    ReorderLayout(columns = 2, spacing = 12.dp, dr = dr) {
        shown.forEach { id ->
            val s = byId[id]
            if (s != null) key(id) {
                val handle = if (editing) Modifier.dragHandle(dr, id, { ids }) { o -> rememberDrop(o); onReorder(o) } else Modifier
                Box(handle.reorderItem(dr, id)) {
                    when {
                        editing -> EditTile(s)
                        compact -> LinkTile(s) { onClick(s) }
                        else -> SectionTile(s) { onClick(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTile(s: HomeSection, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxSize().alpha(if (s.ready) 1f else 0.6f), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(s.icon, size = 50.dp, iconSize = 26.dp, modifier = Modifier)
                Spacer(Modifier.weight(1f))
                if (!s.ready) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text("به‌زودی", style = MaterialTheme.typography.labelSmall, color = Pal.muted)
                    }
                }
            }
            Column {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** سامانه‌ها: نوار افقی اسکرول‌شونده، کاشی سفید با آرم/آیکن و نام زیرش (سبک روشن)؛ «افزودن» آخر نوار است. */
@Composable
private fun LinksStrip(items: List<HomeSection>, onAdd: () -> Unit, onClick: (HomeSection) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { s ->
            key(s.id) {
                Column(
                    Modifier.width(60.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = { onClick(s) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { SiteLogo(s, 32.dp, 8.dp) }
                    Text(s.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Column(
            Modifier.width(60.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "افزودن سایت", onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_add, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            }
            Text("افزودن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

/** بخش‌ها: نوار افقی اسکرول‌شونده با آیکن و نام (تصمیم مورد ۷: هر گروه یک نوار افقی)؛ کم‌ارتفاع و بدون کارت‌های بلند. */
@Composable
private fun SectionsStrip(items: List<HomeSection>, onClick: (HomeSection) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { s ->
            key(s.id) {
                Column(
                    Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).alpha(if (s.ready) 1f else 0.6f).clickable(role = Role.Button, onClick = { onClick(s) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconBadge(s.icon, size = 44.dp, iconSize = 24.dp, modifier = Modifier)
                    Text(s.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** آرم کاشی سامانه (تصویر داخل فایل کاشی‌ها)؛ اگر آرم نبود یا خراب بود، آیکن عمومی. */
@Composable
private fun SiteLogo(s: HomeSection, size: Dp, radius: Dp) {
    val ctx = LocalContext.current
    var fetched by remember(s.id) { mutableStateOf<ByteArray?>(null) }
    // آرم در فایل نبود: آیکن خود سایت یک بار خوانده و روی گوشی نگه داشته می‌شود
    if (s.logoBytes == null && s.iconKey != null) LaunchedEffect(s.id) { fetched = SiteIcon.load(ctx, s.iconKey, s.id) }
    val bytes = s.logoBytes ?: fetched
    val bmp = remember(bytes) {
        bytes?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(radius)))
    else IconBadge(s.icon, size = size, iconSize = size / 2, modifier = Modifier)
}

@Composable
private fun LinkTile(s: HomeSection, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxSize(), onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SiteLogo(s, 44.dp, 13.dp)
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** نمای ماهانهٔ تقویم در صفحهٔ اول: رنگ شیفت هر روز، تعطیلی‌ها، امروز و مناسبت‌های همان ماه؛ با لمس، بخش جدول شیفت باز می‌شود. */
@Composable
private fun HomeMonthCard(vm: MainVm, onOpen: () -> Unit) {
    val p by vm.selected.collectAsState()
    val all by vm.entriesAllPatterns.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    val lunarShift = vm.prefs.collectAsState().value.lunarShift
    val today = remember { JalaliDate.today() }
    var ym by rememberSaveable { mutableIntStateOf(today.year * 12 + today.month - 1) }
    val year = ym / 12
    val mon = ym % 12 + 1
    val days = JalaliCalendar.daysInMonth(year, mon)
    val offset = JalaliDate(year, mon, 1).dayOfWeek
    val pat = remember(p?.pattern) { p?.let { runCatching { ShiftPattern.parse(it.pattern) }.getOrNull() } }
    val occasions = remember(cal, lunarShift, year, mon) {
        (1..days).flatMap { d ->
            val date = JalaliDate(year, mon, d)
            val h = cal.title(date)
            buildList {
                if (h != null) add(Triple(d, h, true))
                Occasions.all(date, lunarShift).forEach { (t, k) -> if (t != h) add(Triple(d, t + kindTag(k), false)) }
            }
        }
    }
    var alarmDay by remember { mutableStateOf<JalaliDate?>(null) }
    val prefsNow by vm.prefs.collectAsState()
    alarmDay?.let { d -> NewAlarmDialog(vm, p, prefsNow, d) { alarmDay = null } }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ ym -= 1 }) { Ico(R.drawable.ic_chevron_right, desc = "ماه قبل") }
                Text(
                    "${MONTHS[mon - 1]} ${year.fa()}", Modifier.weight(1f).clickable { ym = today.year * 12 + today.month - 1 },
                    style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton({ ym += 1 }) { Ico(R.drawable.ic_chevron_left, desc = "ماه بعد") }
            }
            Row(Modifier.fillMaxWidth()) {
                WEEK_SHORT.forEachIndexed { i, w ->
                    Text(
                        w, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = if (i == 6) Pal.holiday else Pal.muted
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (List<Int?>(offset) { null } + (1..days).toList()).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            if (d == null) Spacer(Modifier.weight(1f).height(34.dp)) else {
                                val date = JalaliDate(year, mon, d)
                                val code = p?.let { cur ->
                                    pat?.let { runCatching { DayResolver.resolve(cur.base(), it, date, all[cur.id]?.get(date.key).orEmpty()).effectiveCode }.getOrNull() }
                                }
                                MiniDay(d, code, cal.kind(date) != DayKind.NORMAL, date == today, Modifier.weight(1f)) { alarmDay = date }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText("با لمس هر روز، آلارم همان روز را بسازید", Modifier.weight(1f))
                AppButton("جدول شیفت", onOpen, style = BtnStyle.Tonal)
            }
            if (occasions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                    occasions.take(4).forEach { (d, t, holiday) ->
                        Text("${d.fa()} ${MONTHS[mon - 1]} · $t", style = MaterialTheme.typography.bodySmall, color = if (holiday) Pal.holiday else Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (occasions.size > 4) Text("و ${(occasions.size - 4).fa()} مورد دیگر در تقویم", style = MaterialTheme.typography.labelSmall, color = Pal.muted)
                }
            }
        }
    }
}

@Composable
private fun MiniDay(day: Int, code: Int?, holiday: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.height(34.dp).then(if (isToday) Modifier.shadow(4.dp, shape) else Modifier).clip(shape)
            .background(if (isToday) TODAY_BG else if (code != null) shiftColor(code) else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = "ساخت آلارم برای این روز", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.fa(), style = MaterialTheme.typography.labelLarge.let { if (isToday) it.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else it },
            color = if (isToday) Color.White else if (holiday) Color(0xFFB71C1C) else if (code != null) Color(0xFF1B1D2A) else MaterialTheme.colorScheme.onSurface
        )
        if (isToday && code != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(shiftColor(code)))
    }
}
