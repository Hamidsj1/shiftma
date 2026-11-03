package ir.shiftapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.PaidAccess
import ir.shiftapp.core.TilesPackFile
import ir.shiftapp.docs.DocsVm
import ir.shiftapp.phonebook.PhonebookVm
import ir.shiftapp.system.BazaarBilling
import ir.shiftapp.system.DateBar
import ir.shiftapp.system.ConfigLoader
import ir.shiftapp.system.NoticeLoader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    private val pbVm: PhonebookVm by viewModels()
    private val docsVm: DocsVm by viewModels()
    /** خرید درون‌برنامه‌ای کافه‌بازار (کاهش حجم عکس بعد از سهمیهٔ رایگان)؛ اتصال فقط هنگام نیاز برقرار می‌شود. */
    private val billing by lazy(LazyThreadSafetyMode.NONE) { BazaarBilling(this) }
    /** بازکردن فایل .shiftma از بیرون برنامه (مثلاً از تلگرام یا مدیر فایل). */
    /** بخشی که ویجت خواسته باز شود (extra «section»)؛ App آن را مصرف می‌کند. */
    private var launchSection by mutableStateOf<String?>(null)

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): android.net.Uri? = i.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri

    private fun handleIncoming(i: Intent?) {
        i?.getStringExtra(EXTRA_SECTION)?.takeIf { it in SECTION_IDS }?.let { launchSection = it }
        val uri = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            // «اشتراک‌گذاری ← شیفت ما»: فایل در clipData یا EXTRA_STREAM می‌آید
            Intent.ACTION_SEND -> i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: streamUri(i)
            else -> null
        }
        if (uri != null) vm.openPatternFile(uri)
    }

    /** مجوز اعلان (اندروید ۱۳+) برای اعلان همیشگی تاریخ؛ بعد از دادن مجوز اعلان بلافاصله گذاشته می‌شود. */
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) DateBar.refreshAsync(this) }

    /** فقط یک‌بار از کاربر مجوز می‌خواهد (اگر رد کرد، دوباره مزاحم نمی‌شود؛ از تنظیمات قابل‌روشن‌کردن است). */
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val sp = getSharedPreferences("shiftma_flags", MODE_PRIVATE)
        if (sp.getBoolean("notif_asked", false)) return
        sp.edit().putBoolean("notif_asked", true).apply()
        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        ir.shiftapp.system.AlarmRingService.openStopScreenOnce(this)      // آلارم هنوز زنگ می‌زند: صفحهٔ قطع باز شود
        DateBar.refreshAsync(this)      // اعلان تاریخ و اطلاعات هر بار که برنامه باز می‌شود تازه می‌شود
        NoticeLoader.apply(this); NoticeLoader.checkNow(this)      // پنل نوتیفیکیشن: زمان‌بندی دوره‌ای + یک چک فوری
        ConfigLoader.apply(this); ConfigLoader.checkNow(this)      // پنل تنظیمات بخش‌ها (نمایش/پنهان/پولی): همان الگو
    }

    override fun onDestroy() {
        billing.close()      // اتصال به سرویس بازار رها شود (اگر هیچ‌وقت وصل نشده، بی‌اثر است)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigLoader.init(this)      // آخرین تنظیم بخش‌ها (آفلاین) پیش از اولین نمایش آماده باشد
        if (savedInstanceState == null) { handleIncoming(intent); askNotificationsOnce() }
        setContent {
            val prefs by vm.prefs.collectAsState()
            val dark = when (prefs.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDark provides dark, LocalBilling provides billing) {
                AppTheme(dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App(vm, pbVm, docsVm, launchSection) { launchSection = null }
                    }
                }
            }
        }
    }
}

const val EXTRA_SECTION = "section"
private val SECTION_IDS = setOf("shift", "phonebook", "notes", "english", "prayer", "pay", "sport", "reading", "payslips", "books", "income", "planner", "home")
/** بخش‌هایی که فقط با دسترسی ویژه (رمز روزانه) باز می‌شوند. */
private val SPECIAL_SECTIONS = setOf("phonebook", "meals")

private val TABS = listOf("چهارشیفت", "کاردکس", "تقویم", "گرافیکی")
private val TAB_ICONS = listOf(R.drawable.ic_tab_four, R.drawable.ic_tab_cardex, R.drawable.ic_tab_calendar, R.drawable.ic_tab_graphic)

/** صفحهٔ اول: انتخاب بخش. بخش‌های تازه را در HomeScreen.kt و همین‌جا اضافه کنید (docs/ADDING_SECTIONS.md). */
@Composable
fun App(vm: MainVm, pbVm: PhonebookVm, docsVm: DocsVm, launch: String? = null, onLaunchConsumed: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var portal by rememberSaveable { mutableStateOf<String?>(null) }      // سامانهٔ کاری باز در مرورگر داخلی (مس من)
    val ctx = LocalContext.current
    val special by vm.specialOn.collectAsState()
    val prefs by vm.prefs.collectAsState()
    LaunchedEffect(launch) { if (launch != null) { section = if (launch == "home") null else launch; onLaunchConsumed() } }
    // بخش‌های مخفی تا وقتی دسترسی ویژه فعال نشده باز نمی‌شوند (مثلاً از میانبر ویجت)
    val enabledSections by vm.enabledSections.collectAsState()
    val sites = remember(prefs.sitesRaw) { TilesPackFile.decodeSites(prefs.sitesRaw) }
    // خوراک اعلان محلی را برای کارگر پس‌زمینه هم نگه دار (بعد از بازیابی پشتیبان یا وارد کردن فایل کاشی‌ها)
    LaunchedEffect(prefs.noticeFeed, prefs.noticePublic) { NoticeLoader.setLocal(ctx, prefs.noticeFeed, prefs.noticePublic) }
    // آدرس تنظیمات بخش‌ها (نمایش/پنهان/پولی) را هم برای کارگر پس‌زمینه نگه دار
    LaunchedEffect(prefs.configFeed) { ConfigLoader.setFeed(ctx, prefs.configFeed) }
    val remote by ConfigLoader.config.collectAsState()
    var paidTick by remember { mutableIntStateOf(0) }      // بعد از باز شدن قفل، وضعیت دوباره خوانده شود
    // برنامه غذایی شرکت: فقط وقتی آدرس خوراک از فایل کاشی‌ها ذخیره شده و بخش در دسترس است؛ کاربر عمومی هیچ درخواستی نمی‌فرستد
    val mealsReady = special || "meals" in enabledSections
    LaunchedEffect(prefs.mealFeed, mealsReady, section == "meals") {
        if (mealsReady && prefs.mealFeed.isNotEmpty()) docsVm.syncMealFeed(prefs.mealFeed, force = section == "meals")
    }
    val shownBase = if (section in SPECIAL_SECTIONS && !(special || section in enabledSections)) null else section
    // شناسهٔ قدیمی «books» همان بخش «reading» است
    val remoteId = if (shownBase == "books") "reading" else shownBase
    val shown = if (shownBase != null && !remote.visible(remoteId!!)) null else shownBase   // بخش پنهان باز نمی‌شود (میانبر ویجت هم)
    val locked = shown != null && remote.needsPayment(remoteId!!) && paidTick >= 0 && !PaidAccess.isUnlocked(ctx, remoteId)
    Box(Modifier.fillMaxSize()) {
        if (locked) PaidGate(remoteId!!, "", remote.payLink, onUnlocked = { paidTick++ }, onBack = { section = null })
        else when (shown) {
            "shift" -> ShiftSection(vm) { section = null }
            "phonebook" -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {      // تبلیغ انحصاری کاشی (tile = phonebook) پایین همان بخش
                Box(Modifier.weight(1f)) { PhonebookSection(pbVm, sites) { section = null } }
                TileAd("phonebook", prefs.adFeed, prefs.adPublic)
            }
            "meals" -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {      // تبلیغ انحصاری کاشی (tile = meals)
                Box(Modifier.weight(1f)) { MealsSection(docsVm) { section = null } }
                TileAd("meals", prefs.adFeed, prefs.adPublic)
            }
            "files" -> FilesSection(docsVm) { section = null }
            "books" -> ReadingSection(vm, initialTab = 1, initialLibFiles = true) { section = null }   // کاشی کتاب‌ها با مطالعه یکی شد؛ شناسهٔ قدیمی (میانبر) هنوز کار می‌کند
            "notes" -> NotesSection(vm) { section = null }
            "planner" -> PlannerSection(vm) { section = null }
            "english" -> EnglishSection(vm) { section = null }
            "prayer" -> PrayerSection(vm) { section = null }
            "dhikr" -> DhikrSection(vm) { section = null }
            "pay" -> PaySection(vm) { section = null }
            "sport" -> SportSection(vm) { section = null }
            "reading" -> ReadingSection(vm) { section = null }
            "income" -> IncomeSection(vm) { section = null }
            "payslips" -> PayslipsSection { section = null }
            "convert" -> ConvertSection { section = null }
            "age" -> AgeSection { section = null }
            "datecv" -> DateConvertSection(vm) { section = null }
            "span" -> DateSpanSection { section = null }
            "imgcompress" -> ImageCompressSection { section = null }
            "sites" -> SitesSection(vm) { section = null }
            "tv" -> TvSection(vm) { section = null }
            "settings" -> AppSettingsSection(vm) { section = null }
            "about" -> AppAboutSection(vm) { section = null }
            else -> HomeScreen(vm, onOpen = { id -> section = id }, onLink = { url ->
                // سامانه‌های دارای مرورگر داخلی به‌صورت پیش‌فرض داخلی باز می‌شوند، مگر در تنظیمات بیرونی شده باشند
                val key = sites.firstOrNull { it.url == url }?.key
                if (key != null && key !in prefs.externalPortals) portal = url else openUrl(ctx, url)
            }, pbVm = pbVm, docsVm = docsVm)
        }
        portal?.let { u ->
            val link = sites.firstOrNull { it.url == u }
            // مرورگر در Activity جدا با WebView ساده باز می‌شود (نه داخل Compose/Dialog) تا صفحه سفید نماند
            LaunchedEffect(u) {
                if (PortalWebActivity.opensInChromeTab(u)) {
                    // سایت‌هایی مثل رسا WebView را رد می‌کنند؛ با Chrome Custom Tab باز می‌شوند
                    PortalWebActivity.launchChromeTab(ctx, u)
                } else {
                    ctx.startActivity(
                        Intent(ctx, PortalWebActivity::class.java)
                            .putExtra(PortalWebActivity.EXTRA_TITLE, link?.title ?: "سامانه")
                            .putExtra(PortalWebActivity.EXTRA_URL, u)
                            .putExtra(PortalWebActivity.EXTRA_KEY, link?.key)
                            .putExtra(PortalWebActivity.EXTRA_PDF, link?.pdf ?: false)
                    )
                }
                portal = null
            }
        }
    }
    val pending by vm.pendingImport.collectAsState()
    pending?.let { list ->
        ImportPatternsDialog(list, onOk = { sel -> vm.confirmImport(sel); section = "shift" }, onCancel = { vm.cancelImport() })
    }
    val pendingMeals by vm.pendingMeals.collectAsState()
    pendingMeals?.let { list ->
        ImportMealsDialog(
            list,
            onOk = { docsVm.importMealPack(list); section = "meals"; vm.mealsDone() },
            onCancel = { vm.mealsDone() }
        )
    }
    val pendingTiles by vm.pendingTiles.collectAsState()
    pendingTiles?.let { pack ->
        ImportTilesDialog(
            pack,
            onAdd = { keys -> vm.confirmTiles(false, keys); section = null },
            onReplace = { keys -> vm.confirmTiles(true, keys); section = null },
            onCancel = { vm.cancelTiles() }
        )
    }
    val pendingContacts by vm.pendingContacts.collectAsState()
    val pendingPrefix by vm.pendingPrefix.collectAsState()
    val pbPrefix by pbVm.prefix.collectAsState()
    pendingContacts?.let { list ->
        ImportContactsDialog(
            list,
            onAdd = { withPre -> vm.pendingContacts.value = null; if (withPre) pbVm.setPrefix(pendingPrefix); pbVm.importContacts(list, false); section = "phonebook" },
            onReplace = { withPre -> vm.pendingContacts.value = null; if (withPre) pbVm.setPrefix(pendingPrefix); pbVm.importContacts(list, true); section = "phonebook" },
            onCancel = { vm.pendingContacts.value = null },
            prefix = pendingPrefix, currentPrefix = pbPrefix
        )
    }
}

@Composable
fun ShiftSection(vm: MainVm, onHome: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var more by rememberSaveable { mutableStateOf<String?>(null) }     // زیرصفحه‌ای که از کشو باز شده
    val msg by vm.message.collectAsState()
    val patterns by vm.patterns.collectAsState()
    val loaded by vm.patternsLoaded.collectAsState()
    val snack = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    val inSub = more != null
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    BackHandler { if (inSub) more = null else onHome() }
    BackHandler(enabled = drawer.isOpen) { drawerScope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,      // باز شدن فقط با دکمهٔ منو؛ کشیدن روی تقویم و جدول‌ها با کشو تداخل نکند
        drawerContent = {
            MoreDrawerSheet(selected = more) { key ->
                drawerScope.launch { drawer.close() }
                more = key
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                ShiftTopBar(
                    vm = vm,
                    title = if (inSub) moreTitle(more.orEmpty()) else "جدول شیفت",
                    leadingIcon = if (inSub) R.drawable.ic_arrow_back else R.drawable.ic_home,
                    leadingDesc = if (inSub) "بازگشت" else "صفحهٔ اول",
                    onLeading = { if (inSub) more = null else onHome() },
                    showTools = loaded && patterns.isNotEmpty(),
                    onMenu = { drawerScope.launch { drawer.open() } }
                )
            },
            bottomBar = { if (!landscape) ShiftNavBar(if (inSub) -1 else tab) { i -> tab = i; more = null } }
        ) { pad ->
            Row(Modifier.padding(pad).fillMaxSize()) {
            if (landscape) ShiftNavRail(if (inSub) -1 else tab) { i -> tab = i; more = null }      // افقی: ستون کناری به‌جای نوار پایین تا ارتفاع نخورد
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp)) {
                when {
                    !loaded -> Unit
                    inSub -> MoreScreen(vm, more) { more = it }
                    patterns.isEmpty() -> WelcomeScreen(vm)
                    else -> when (tab) {
                        0 -> FourShiftScreen(vm)
                        1 -> CardexScreen(vm)
                        2 -> CalendarScreen(vm)
                        else -> GraphicScreen(vm)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ShiftNavRail(selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.weight(1f))
        TABS.forEachIndexed { i, t ->
            NavigationRailItem(
                selected = selected == i, onClick = { onSelect(i) },
                icon = { Icon(painterResource(TAB_ICONS[i]), contentDescription = null) },
                label = { Text(t, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                alwaysShowLabel = true
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ShiftNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = Pal.hairline)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            TABS.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = selected == i, onClick = { onSelect(i) },
                    icon = { Icon(painterResource(TAB_ICONS[i]), contentDescription = null) },
                    label = { Text(t, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = Pal.muted, unselectedTextColor = Pal.muted
                    )
                )
            }
        }
    }
}
