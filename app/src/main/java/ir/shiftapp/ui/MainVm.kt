package ir.shiftapp.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.shiftapp.core.*
import ir.shiftapp.data.*
import ir.shiftapp.docs.MealPackFile
import ir.shiftapp.docs.MealPackItem
import ir.shiftapp.phonebook.Contact
import ir.shiftapp.phonebook.ContactsFile
import ir.shiftapp.system.DefaultTiles
import ir.shiftapp.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal fun PatternEntity.base() = JalaliDate(baseYear, baseMonth, baseDay)

enum class ExportKind { CSV, PDF, XLSX, PNG_FOUR }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val SEL = "selected_pattern"

    /** تا اولین خواندن از دیتابیس false است؛ برای اینکه صفحهٔ خوش‌آمد لحظه‌ای نمایش داده نشود. */
    val patternsLoaded = MutableStateFlow(false)
    val patterns = dao.patterns().onEach { patternsLoaded.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** الگوهای خوانده‌شده از فایل .shiftma که منتظر تأیید کاربرند. */
    val pendingImport = MutableStateFlow<List<PatternEntity>?>(null)
    /** مخاطبین خوانده‌شده از فایل .shiftma (type = contacts) که منتظر تأیید کاربرند. */
    val pendingContacts = MutableStateFlow<List<Contact>?>(null)
    /** پیش‌شمارهٔ داخلی همراه فایل مخاطبین در انتظار تأیید (خالی = ندارد). */
    val pendingPrefix = MutableStateFlow("")
    /** بستهٔ برنامه غذایی خوانده‌شده از فایل .shiftma (type = meals) که منتظر تأیید کاربر است. */
    val pendingMeals = MutableStateFlow<List<MealPackItem>?>(null)
    val employees = dao.employees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val month = MutableStateFlow(JalaliDate.today().let { JalaliDate(it.year, it.month, 1) })
    private val selectedId = MutableStateFlow(0L)
    val message = MutableStateFlow<String?>(null)

    /** الگوی انتخاب‌شده؛ اگر انتخاب معتبر نباشد اولین الگو. */
    val selected: StateFlow<PatternEntity?> = combine(patterns, selectedId) { l, id -> l.firstOrNull { it.id == id } ?: l.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entriesByDate: StateFlow<Map<String, List<Entry>>> = combine(selected, month) { p, m -> p to m }
        .flatMapLatest { (p, m) ->
            if (p == null) flowOf(emptyList<DayEntry>()) else
                dao.entries(p.id, JalaliDate(m.year, m.month, 1).key, JalaliDate(m.year, m.month, JalaliCalendar.daysInMonth(m.year, m.month)).key)
        }.map { l -> l.groupBy({ it.dateKey }, { Entry(it.type, it.shiftCode, it.minutes, it.text) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** برای حذف ثبت‌ها لازم است شناسهٔ ردیف‌ها را هم داشته باشیم. */
    val rawEntries: StateFlow<List<DayEntry>> = combine(selected, month) { p, m -> p to m }
        .flatMapLatest { (p, m) ->
            if (p == null) flowOf(emptyList<DayEntry>()) else
                dao.entries(p.id, JalaliDate(m.year, m.month, 1).key, JalaliDate(m.year, m.month, JalaliCalendar.daysInMonth(m.year, m.month)).key)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- تب چهارشیفت: ۴ ستون، هر ستون شناسهٔ یک الگو (۰ = خالی) ----
    val slots = MutableStateFlow(List(FourShiftTable.SLOTS) { 0L })
    private fun slotKey(i: Int) = "four_slot_$i"

    /** ثبت‌های ماه جاری برای همهٔ الگوها: patternId -> (dateKey -> ثبت‌ها). */
    val entriesAllPatterns: StateFlow<Map<Long, Map<String, List<Entry>>>> = month.flatMapLatest { m ->
        dao.entriesInRange(JalaliDate(m.year, m.month, 1).key, JalaliDate(m.year, m.month, JalaliCalendar.daysInMonth(m.year, m.month)).key)
    }.map { l ->
        l.groupBy { it.patternId }.mapValues { (_, v) -> v.groupBy({ it.dateKey }, { Entry(it.type, it.shiftCode, it.minutes, it.text) }) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ---- تنظیمات، تعطیلات ----
    val prefs: StateFlow<Prefs> = dao.settings().map { l -> Prefs.from(l.associate { it.key to it.value }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())
    val holidays: StateFlow<List<Holiday>> = dao.holidays().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val holidayCalendar: StateFlow<HolidayCalendar> = combine(holidays, prefs) { h, p -> HolidayCalendar(h.associate { it.dateKey to it.title }, p.fridayOff) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HolidayCalendar(emptyMap(), true))

    fun setPref(key: String, value: String) { viewModelScope.launch { dao.put(Setting(key, value)) } }

    // ---- کاشی‌های مخفی (دفترچه تلفن، برنامه غذایی): پیش‌فرض پنهان‌اند و فقط با «فایل کاشی‌ها» (sections) یا دسترسی ویژه نمایان می‌شوند ----
    val specialOn = MutableStateFlow(SpecialAccess.isOn(app))

    // ---- بستهٔ کاشی‌ها (فایل .shiftma با type = tiles): سامانه‌ها و بخش‌های مخفی؛ پیش‌فرض‌ها در assets/default_tiles.shiftma (system/DefaultTiles.kt) ----
    val enabledSections = MutableStateFlow(SpecialAccess.sections(app) + DefaultTiles.sections(app))

    private fun currentSections(): Set<String> =
        SpecialAccess.sections(getApplication<Application>()) + DefaultTiles.sections(getApplication<Application>())

    /** اولین اجرا: سامانه‌های پیش‌فرض را (بدون دست زدن به سامانه‌های دیگر) اضافه می‌کند. فقط یک‌بار؛ حذف کاربر برنمی‌گردد. */
    private suspend fun seedDefaultTiles() {
        val app = getApplication<Application>()
        if (DefaultTiles.seeded(app)) return
        val pack = DefaultTiles.pack(app) ?: return
        if (pack.sites.isNotEmpty()) {
            val old = TilesPackFile.decodeSites(dao.setting(PrefKeys.SITES).orEmpty())
            val keys = pack.sites.map { it.key }.toSet()
            val merged = old.filter { it.key !in keys } + pack.sites
            dao.put(Setting(PrefKeys.SITES, TilesPackFile.encodeSites(merged.takeLast(TilesPackFile.MAX_SITES))))
        }
        DefaultTiles.markSeeded(app)
    }
    init { viewModelScope.launch { seedDefaultTiles() } }
    // به‌روزرسانی آدرس سایت‌ها از config.json (فقط سایت‌های موجود و ویرایش‌نشدهٔ کاربر)؛ بدون تغییر = بدون نوشتن
    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(ir.shiftapp.system.ConfigLoader.config, prefs.map { it.sitesRaw }.distinctUntilChanged()) { c, raw -> c.siteUpdates to raw }
                .collect { (updates, raw) ->
                    val old = TilesPackFile.decodeSites(raw)
                    val fresh = SiteUpdates.apply(old, updates) ?: return@collect
                    fresh.filter { n -> old.firstOrNull { it.key == n.key }?.url != n.url }.forEach { SiteIcon.forget(getApplication<Application>(), it.key) }
                    ir.shiftapp.system.UpdateLog.addChanges(getApplication<Application>(), SiteUpdates.changes(old, fresh))
                    setPref(PrefKeys.SITES, TilesPackFile.encodeSites(fresh))
                }
        }
    }
    val pendingTiles = MutableStateFlow<TilesPack?>(null)

    /** مخاطبینِ یک فایل «مخاطبین + سامانه‌ها» که تا تمام شدن پنجرهٔ کاشی‌ها منتظر می‌مانند. */
    private var heldContacts: List<Contact>? = null
    private var heldPrefix = ""

    private fun showContacts(list: List<Contact>, prefix: String = "") {
        pendingPrefix.value = prefix; pendingContacts.value = list      // نمایان شدن کاشی فقط با کلید sections فایل کاشی‌هاست؛ وارد کردن مخاطبین به‌تنهایی کاشی را نمایان نمی‌کند
    }

    private fun releaseHeldContacts() {
        val l = heldContacts ?: return
        heldContacts = null
        val pre = heldPrefix
        heldPrefix = ""
        showContacts(l, pre)
    }

    /**
     * برنامه غذاییِ یک فایل «ارسال انتخابی» که تا تمام شدن پنجره‌های الگو و کاشی‌ها منتظر می‌ماند؛
     * ترتیب پنجره‌ها: الگوها ← کاشی‌ها ← برنامه غذایی ← مخاطبین.
     */
    private var heldMeals: List<MealPackItem>? = null

    private fun releaseHeldMeals() {
        val m = heldMeals
        if (m == null) { releaseHeldContacts(); return }
        heldMeals = null
        pendingMeals.value = m      // با بسته‌شدن پنجرهٔ تأیید، mealsDone() مرحلهٔ بعد (مخاطبین) را آزاد می‌کند؛ نمایان شدن کاشی فقط با کلید sections فایل است
    }

    /** پنجرهٔ تأیید برنامه غذایی بسته شد (تأیید یا انصراف): مرحلهٔ بعدی فایل ارسال انتخابی. */
    fun mealsDone() { pendingMeals.value = null; releaseHeldContacts() }

    fun cancelTiles() { pendingTiles.value = null; releaseHeldMeals() }

    fun clearEnabledSections() {
        SpecialAccess.clearSections(getApplication<Application>()); enabledSections.value = currentSections()
    }

    /** [keys] = شناسهٔ سامانه‌هایی که کاربر تیک زده؛ null یعنی همه. بخش‌های مخفی فایل همیشه اعمال می‌شوند. */
    fun confirmTiles(replace: Boolean, keys: Set<String>? = null) {
        val pack = pendingTiles.value ?: return
        pendingTiles.value = null
        val sites = if (keys == null) pack.sites else pack.sites.filter { it.key in keys }
        if (sites.isNotEmpty()) {
            val old = TilesPackFile.decodeSites(prefs.value.sitesRaw)
            val newKeys = sites.map { it.key }.toSet()
            val merged = (if (replace) emptyList() else old.filter { it.key !in newKeys }) + sites
            setPref(PrefKeys.SITES, TilesPackFile.encodeSites(merged.takeLast(TilesPackFile.MAX_SITES)))
        }
        if (pack.sections.isNotEmpty()) {
            SpecialAccess.addSections(getApplication<Application>(), pack.sections)
            enabledSections.value = currentSections()
        }
        applyFeeds(pack)
        val parts = ArrayList<String>()
        if (sites.isNotEmpty()) parts.add("${Fa.num(sites.size)} سامانه")
        if (pack.sections.isNotEmpty()) parts.add("${Fa.num(pack.sections.size)} بخش")
        if (pack.mealsFeed.isNotEmpty()) parts.add("دریافت خودکار برنامه غذایی")
        if (pack.noticeFeed.isNotEmpty()) parts.add("اعلان‌های محل کار")
        if (parts.isNotEmpty()) message.value = parts.joinToString(" و ") + " اضافه شد"
        releaseHeldMeals()
    }

    /**
     * بستهٔ «فقط‌خوراک» (بدون سامانه و بخش؛ مثلاً آدرس‌های منطقه که همراه فایل الگو/مخاطبین می‌آید) پنجرهٔ تأیید ندارد:
     * بی‌صدا اعمال می‌شود و کاربر چیزی از آن نمی‌بیند. برای بستهٔ دارای سامانه/بخش خودش را برمی‌گرداند تا پنجرهٔ تأیید بیاید.
     */
    private fun silentFeedsOnly(t: TilesPack?): TilesPack? {
        if (t == null || t.sites.isNotEmpty() || t.sections.isNotEmpty()) return t
        applyFeeds(t)
        return null
    }

    /** آدرس‌های خوراک (غذا، تبلیغ، اعلان، تنظیمات) را ذخیره می‌کند؛ فقط غیرخالی‌ها؛ نبودن کلید یعنی دست نزن. */
    private fun applyFeeds(pack: TilesPack) {
        // آدرس خوراک برنامه غذایی شرکت: فقط با تأیید همین فایل کاشی‌ها ذخیره می‌شود؛ بدونش هیچ درخواستی به اینترنت نمی‌رود
        if (pack.mealsFeed.isNotEmpty()) setPref(PrefKeys.MEAL_FEED, pack.mealsFeed)
        if (pack.adsFeed.isNotEmpty()) {      // تبلیغ همین محل؛ تبلیغ عمومی فقط اگر فایل بخواهد کنارش بماند
            setPref(PrefKeys.AD_FEED, pack.adsFeed)
            setPref(PrefKeys.AD_PUBLIC, pack.publicAds.toString())
        }
        if (pack.noticeFeed.isNotEmpty()) {   // اعلان محلی همین محل؛ اعلان عمومی به‌طور پیش‌فرض کنارش می‌ماند
            setPref(PrefKeys.NOTICE_FEED, pack.noticeFeed)
            setPref(PrefKeys.NOTICE_PUBLIC, pack.publicNotices.toString())
            NoticeLoader.setLocal(getApplication<Application>(), pack.noticeFeed, pack.publicNotices)
        }
        if (pack.configFeed.isNotEmpty()) {   // تنظیم بخش‌های همین محل (نمایش/پنهان/پولی)
            setPref(PrefKeys.CONFIG_FEED, pack.configFeed)
            ConfigLoader.setFeed(getApplication<Application>(), pack.configFeed)
        }
    }

    /**
     * افزودن دستی یک سامانه/سایت به کاشی‌های «سامانه‌ها». خطا را به‌صورت متن برمی‌گرداند (null = اضافه شد).
     * اعتبارسنجی همان قواعد فایل کاشی‌هاست؛ چون یک مورد نامعتبر باعث می‌شود کل فهرست خوانده نشود.
     */
    fun addSite(title: String, url: String, subtitle: String = ""): String? {
        val t = title.trim()
        val sub = subtitle.trim()
        var u = url.trim()
        if (u.isNotEmpty() && !u.contains("://")) u = "https://$u"
        if (t.isEmpty()) return "عنوان را بنویس"
        if (t.length > TilesPackFile.MAX_TITLE) return "عنوان حداکثر ${Fa.num(TilesPackFile.MAX_TITLE)} حرف باشد"
        if (sub.length > TilesPackFile.MAX_SUB) return "توضیح حداکثر ${Fa.num(TilesPackFile.MAX_SUB)} حرف باشد"
        if (u.startsWith("http://")) return "فقط آدرس امن (https) پذیرفته می‌شود"
        if (!u.startsWith("https://") || TilesPackFile.hostOf(u).isEmpty() || u.length > TilesPackFile.MAX_URL) return "آدرس درست نیست (مثل example.com)"
        val old = TilesPackFile.decodeSites(prefs.value.sitesRaw)
        if (old.any { it.url.equals(u, ignoreCase = true) }) return "این آدرس قبلاً اضافه شده"
        if (old.size >= TilesPackFile.MAX_SITES) return "حداکثر ${Fa.num(TilesPackFile.MAX_SITES)} سامانه می‌شود؛ یکی را از تنظیمات حذف کن"
        var key = TilesPackFile.keyFor(u).ifEmpty { "site" }
        while (old.any { it.key == key }) key = (key + "x").takeLast(30)
        setPref(PrefKeys.SITES, TilesPackFile.encodeSites(old + SiteTile(key, t, sub, u, false, null)))
        message.value = "«$t» اضافه شد"
        return null
    }

    // ---- نوارهای «سایت‌های ضروری» و «تلویزیون» صفحهٔ اول: افزودن، ویرایش، جابه‌جایی، حذف (core/StripSites.kt) ----
    private fun stripSites(strip: Strip): List<StripSite> {
        val p = prefs.value
        return if (strip == Strip.ESSENTIAL) StripSites.resolve(strip, p.essListRaw, p.essSitesRaw) else StripSites.resolve(strip, p.tvListRaw)
    }

    private fun saveStrip(strip: Strip, list: List<StripSite>) = setPref(strip.listKey, StripSites.encode(list))

    /** متن خطا یا null (اضافه شد). */
    fun addStripSite(strip: Strip, title: String, url: String): String? {
        val r = StripSites.add(stripSites(strip), title, url)
        val list = r.list ?: return r.error
        saveStrip(strip, list)
        message.value = "«${title.trim()}» اضافه شد"
        return null
    }

    /** متن خطا یا null (ذخیره شد). اگر آدرس عوض شود آیکن ذخیره‌شدهٔ قبلی کنار می‌رود تا آیکن سایت تازه خوانده شود. */
    fun editStripSite(strip: Strip, key: String, title: String, url: String): String? {
        val old = stripSites(strip)
        val r = StripSites.update(old, key, title, url)
        val list = r.list ?: return r.error
        val before = old.firstOrNull { it.key == key }?.url.orEmpty()
        if (!before.equals(StripSites.normalizeUrl(url), ignoreCase = true)) SiteIcon.forget(getApplication<Application>(), strip.iconPrefix + key)
        saveStrip(strip, list)
        return null
    }

    fun moveStripSite(strip: Strip, key: String, delta: Int) {
        val old = stripSites(strip)
        val moved = StripSites.move(old, key, delta)
        if (moved.map { it.key } != old.map { it.key }) saveStrip(strip, moved)
    }

    fun removeStripSite(strip: Strip, key: String) {
        SiteIcon.forget(getApplication<Application>(), strip.iconPrefix + key)
        saveStrip(strip, StripSites.remove(stripSites(strip), key))
    }

    /** نوار را به فهرست پیش‌فرض برمی‌گرداند (سایت‌های افزودهٔ کاربر هم پاک می‌شوند). */
    fun resetStrip(strip: Strip) {
        setPref(strip.listKey, "")
        if (strip == Strip.ESSENTIAL) setPref(PrefKeys.ESS_SITES, "")
    }

    /** ویرایش عنوان/آدرس/توضیح یک سایت (کلید و پس‌نشان و رمز ذخیره‌شده می‌مانند). بعدش به‌روزرسانی راه‌دور به آن دست نمی‌زند. */
    fun editSite(key: String, title: String, url: String, subtitle: String): String? {
        val t = title.trim(); val sub = subtitle.trim()
        var u = url.trim()
        if (u.isNotEmpty() && !u.contains("://")) u = "https://$u"
        if (t.isEmpty()) return "عنوان را بنویس"
        if (t.length > TilesPackFile.MAX_TITLE) return "عنوان حداکثر ${Fa.num(TilesPackFile.MAX_TITLE)} حرف باشد"
        if (sub.length > TilesPackFile.MAX_SUB) return "توضیح حداکثر ${Fa.num(TilesPackFile.MAX_SUB)} حرف باشد"
        if (u.startsWith("http://")) return "فقط آدرس امن (https) پذیرفته می‌شود"
        if (!u.startsWith("https://") || TilesPackFile.hostOf(u).isEmpty() || u.length > TilesPackFile.MAX_URL) return "آدرس درست نیست (مثل example.com)"
        val old = TilesPackFile.decodeSites(prefs.value.sitesRaw)
        val cur = old.firstOrNull { it.key == key } ?: return "این سایت دیگر نیست"
        if (old.any { it.key != key && it.url.equals(u, ignoreCase = true) }) return "این آدرس قبلاً اضافه شده"
        if (!cur.url.equals(u, ignoreCase = true)) SiteIcon.forget(getApplication<Application>(), key)
        setPref(PrefKeys.SITES, TilesPackFile.encodeSites(old.map { if (it.key == key) SiteTile(key, t, sub, u, cur.pdf, cur.logo, edited = true) else it }))
        message.value = "«$t» ویرایش شد"
        return null
    }

    fun removeSite(key: String) {
        SiteIcon.forget(getApplication<Application>(), key)
        val left = TilesPackFile.decodeSites(prefs.value.sitesRaw).filter { it.key != key }
        setPref(PrefKeys.SITES, TilesPackFile.encodeSites(left))
    }

    // ---- یادگیری انگلیسی ----
    /** تا وقتی نوشتن در دیتابیس به prefs نرسیده، آخرین مقدار محلی را نگه می‌دارد (پاسخ‌های پشت‌سرهم گم نشوند). */
    private val englishLocal = MutableStateFlow<EnglishProgress?>(null)
    val english: StateFlow<EnglishProgress> = combine(prefs, englishLocal) { p, l -> l ?: p.english }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EnglishProgress())

    fun updateEnglish(f: (EnglishProgress) -> EnglishProgress) {
        val next = f(english.value)
        englishLocal.value = next
        setPref(PrefKeys.ENGLISH, next.encode())
    }

    init {
        viewModelScope.launch {
            prefs.map { it.english }.distinctUntilChanged().collect { e -> if (englishLocal.value == e) englishLocal.value = null }
        }
    }

    fun addHoliday(dateText: String, title: String): String? {
        val d = DateText.parse(dateText) ?: return "تاریخ نامعتبر است (مثال: ۱۴۰۵/۰۷/۲۰)"
        if (title.isBlank()) return "عنوان تعطیلی را وارد کنید"
        viewModelScope.launch { dao.put(Holiday(d.key, title.trim())) }
        return null
    }
    fun deleteHoliday(key: String) { viewModelScope.launch { dao.deleteHoliday(key) } }

    // ---- مرخصی سالانه ----
    val leaveYear = MutableStateFlow(JalaliDate.today().year)
    val leaveEntries: StateFlow<List<DayEntry>> = combine(selected, leaveYear) { p, y -> p to y }
        .flatMapLatest { (p, y) ->
            if (p == null) flowOf(emptyList<DayEntry>())
            else dao.entries(p.id, JalaliDate(y, 1, 1).key, JalaliDate(y, 12, JalaliCalendar.daysInMonth(y, 12)).key)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun leaveBalance(): LeaveBalance? {
        val p = selected.value ?: return null
        val byDate = leaveEntries.value.groupBy({ it.dateKey }, { Entry(it.type, it.shiftCode, it.minutes, it.text) })
        return runCatching {
            LeaveCalculator.year(leaveYear.value, prefs.value.leaveCapDays, p.base(), ShiftPattern.parse(p.pattern), byDate, prefs.value.leaveCountsRestDays)
        }.getOrNull()
    }

    fun hours(): HoursReport? {
        val p = selected.value ?: return null; val m = month.value
        return runCatching {
            HoursCalculator.month(m.year, m.month, p.base(), ShiftPattern.parse(p.pattern), entriesByDate.value, prefs.value.shiftTimes, holidayCalendar.value)
        }.getOrNull()
    }

    /** ستون‌های جدول چهارشیفتِ ماه جاری (null = ستون خالی). */
    fun fourColumns(): List<FourShiftColumn?> = slots.value.map { id ->
        patterns.value.firstOrNull { it.id == id }?.let { p ->
            FourShiftColumn(p.id, p.name, p.company, p.base(), ShiftPattern.parse(p.pattern), entriesAllPatterns.value[p.id].orEmpty())
        }
    }

    init {
        reloadSettings()
        // هر تغییر داده/تنظیمات: یادآور بعدی، ویجت و پشتیبان خودکار را همگام کن
        viewModelScope.launch {
            combine(dao.patterns(), dao.settings(), dao.allEntriesFlow(), dao.holidays()) { _, _, _, _ -> Unit }
                .debounce(800)
                .collect {
                    val app = getApplication<Application>()
                    runCatching { ReminderScheduler.reschedule(app) }
                    runCatching { ShiftWidget.refresh(app) }
                    runCatching { NextReminderWidget.refresh(app) }
                    runCatching { PrayerWidget.refresh(app) }
                    runCatching { DateBar.refresh(app) }      // اعلان همیشگی تاریخ و اطلاعات (یا برداشتنش اگر در تنظیمات خاموش شد)
                    runCatching { AutoBackup.apply(app, prefs.value.autoBackup) }
                }
        }
    }

    private fun reloadSettings() = viewModelScope.launch {
        dao.setting(SEL)?.toLongOrNull()?.let { selectedId.value = it }
        slots.value = List(FourShiftTable.SLOTS) { i -> dao.setting(slotKey(i))?.toLongOrNull() ?: 0L }
    }

    fun setSlot(index: Int, patternId: Long) {
        if (index !in 0 until FourShiftTable.SLOTS) return
        slots.value = slots.value.toMutableList().also { it[index] = patternId }
        viewModelScope.launch { dao.put(Setting(slotKey(index), patternId.toString())) }
    }
    fun clearSlot(index: Int) = setSlot(index, 0L)

    fun select(id: Long) { selectedId.value = id; viewModelScope.launch { dao.put(Setting(SEL, id.toString())) } }

    fun shiftMonth(delta: Int) {
        val m = month.value; val t = (m.year * 12 + (m.month - 1)) + delta
        val y = Math.floorDiv(t, 12); val mo = Math.floorMod(t, 12) + 1
        if (y in JalaliCalendar.MIN_YEAR..JalaliCalendar.MAX_YEAR) month.value = JalaliDate(y, mo, 1)
    }
    fun goToday() { JalaliDate.today().let { month.value = JalaliDate(it.year, it.month, 1) } }

    fun savePattern(id: Long, name: String, company: String, y: Int, m: Int, d: Int, codes: List<Int>, assignSlot: Int? = null, selectNew: Boolean = false,
                    supervisor: String = "", headSupervisor: String = "", section: String = "", setName: String? = null): String? = try {
        require(name.isNotBlank()) { "نام الگو را وارد کنید" }
        require(JalaliDate(y, m, d).isValid) { "تاریخ مبنا نامعتبر است" }
        val pat = ShiftPattern.of(codes).serialize()
        viewModelScope.launch {
            if (id == 0L) { val nid = dao.insert(PatternEntity(0, name.trim(), company.trim(), y, m, d, pat, supervisor.trim(), headSupervisor.trim(), section.trim(), setName.orEmpty().trim())); if (selectNew || selected.value == null) select(nid); assignSlot?.let { setSlot(it, nid) } }
            else dao.update(PatternEntity(id, name.trim(), company.trim(), y, m, d, pat, supervisor.trim(), headSupervisor.trim(), section.trim(), setName ?: patterns.value.firstOrNull { it.id == id }?.setName.orEmpty()))
        }; null
    } catch (e: IllegalArgumentException) { e.message }

    // ---------- فایل الگو (.shiftma) ----------

    private sealed interface Incoming {
        class Patterns(val list: List<PatternEntity>) : Incoming
        class Contacts(val list: List<Contact>, val tiles: TilesPack? = null, val prefix: String = "") : Incoming
        class Meals(val list: List<MealPackItem>) : Incoming
        class Tiles(val pack: TilesPack) : Incoming
        class Bundle(val bundle: BundleFile.Bundle) : Incoming
    }

    /** فایل .shiftma را باز می‌کند؛ بر اساس فیلد type یا الگوهاست، یا مخاطبین، یا برنامه غذایی. */
    fun openPatternFile(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val incoming: Incoming = when (ContactsFile.typeOf(text)) {
                        ContactsFile.TYPE -> {
                            require(text.length <= ContactsFile.MAX_CHARS) { "فایل بیش از حد بزرگ است" }
                            Incoming.Contacts(ContactsFile.fromJson(text), ContactsFile.tilesOf(text), ContactsFile.prefixOf(text))
                        }
                        TilesPackFile.TYPE -> {
                            require(text.length <= TilesPackFile.MAX_CHARS) { "فایل بیش از حد بزرگ است" }
                            Incoming.Tiles(TilesPackFile.fromJson(text))
                        }
                        BundleFile.TYPE -> {
                            require(text.length <= BundleFile.MAX_CHARS) { "فایل بیش از حد بزرگ است" }
                            Incoming.Bundle(BundleFile.fromJson(text))
                        }
                        MealPackFile.TYPE -> {
                            require(text.length <= MealPackFile.MAX_CHARS) { "فایل بیش از حد بزرگ است" }
                            Incoming.Meals(MealPackFile.fromJson(text))
                        }
                        else -> {
                            require(text.length <= 1_000_000) { "فایل بیش از حد بزرگ است" }
                            Incoming.Patterns(PatternFile.fromJson(text))
                        }
                    }
                    incoming
                }
            }
            r.onSuccess { inc ->
                when (inc) {
                    is Incoming.Patterns -> pendingImport.value = inc.list
                    is Incoming.Contacts -> {
                        val t = silentFeedsOnly(inc.tiles)
                        if (t != null) { heldContacts = inc.list; heldPrefix = inc.prefix; pendingTiles.value = t }      // فایل مخاطبین + سامانه‌ها: اول کاشی‌ها، بعد مخاطبین
                        else showContacts(inc.list, inc.prefix)
                    }
                    is Incoming.Meals -> pendingMeals.value = inc.list
                    is Incoming.Tiles -> pendingTiles.value = inc.pack
                    is Incoming.Bundle -> {
                        val b = inc.bundle
                        val bTiles = silentFeedsOnly(b.tiles)      // آدرس‌های منطقهٔ فرستنده بی‌صدا اعمال می‌شود
                        // فایل ارسال انتخابی: اول الگوها، بعد کاشی‌ها، بعد برنامه غذایی، آخر مخاطبین
                        heldContacts = b.contacts.takeIf { it.isNotEmpty() }
                        heldPrefix = b.prefix
                        heldMeals = b.meals.takeIf { it.isNotEmpty() }
                        if (b.patterns.isNotEmpty()) { heldTiles = bTiles; pendingImport.value = b.patterns }
                        else if (bTiles != null) pendingTiles.value = bTiles
                        else releaseHeldMeals()
                    }
                }
            }.onFailure {
                message.value = if (it is OutOfMemoryError) "فایل بیش از حد بزرگ است" else (it as? IllegalArgumentException)?.message ?: "فایل خوانده نشد"
            }
        }
    }

    /** کاشی‌های یک فایل ارسال انتخابی که تا تمام شدن پنجرهٔ الگوها منتظر می‌مانند. */
    private var heldTiles: TilesPack? = null

    private fun releaseHeldTiles() {
        val t = heldTiles
        heldTiles = null
        if (t == null) releaseHeldMeals() else pendingTiles.value = t
    }

    fun cancelImport() { pendingImport.value = null; releaseHeldTiles() }

    /** الگوهای فایل را اضافه می‌کند و در جاهای خالی تب چهارشیفت می‌گذارد. */
    fun confirmImport(chosen: List<PatternEntity>? = null) {
        val all = pendingImport.value ?: return
        pendingImport.value = null
        val list = chosen ?: all
        releaseHeldTiles()
        if (list.isEmpty()) return
        viewModelScope.launch {
            val free = slots.value.withIndex().filter { it.value == 0L }.map { it.index }.toMutableList()
            val needSelect = selected.value == null
            list.forEachIndexed { i, p ->
                val nid = dao.insert(p.copy(id = 0))
                if (needSelect && i == 0) select(nid)
                if (free.isNotEmpty()) setSlot(free.removeAt(0), nid)
            }
            message.value = "${list.size} الگو اضافه شد"
        }
    }

    /** ساخت همهٔ گروه‌های یک الگوی آماده (A, B, ...) با فاصلهٔ شروع مشخص؛ گروه‌ها در تب چهارشیفت هم قرار می‌گیرند. */
    fun createCrews(preset: PatternPreset, company: String, start: JalaliDate, headSupervisor: String = "", section: String = "", mine: Int = 0): String? {
        for (i in 0 until preset.crews) {
            val d = start.plusDays(preset.slotOf(i) * preset.offsetDays)
            val err = savePattern(0L, ('A' + i).toString(), company, d.year, d.month, d.day, preset.codes,
                if (i < FourShiftTable.SLOTS) i else null, i == mine, "", headSupervisor, section, setName = preset.title)
            if (err != null) return err
        }
        return null
    }

    fun deletePattern(id: Long) = viewModelScope.launch {
        dao.deleteEntriesOfPattern(id); dao.deletePattern(id)
        slots.value.forEachIndexed { i, s -> if (s == id) setSlot(i, 0L) }
    }

    fun addEntry(date: JalaliDate, type: String, shiftCode: Int? = null, minutes: Int? = null, text: String = "", employeeId: Long? = null): String? {
        val p = selected.value ?: return "ابتدا یک الگوی شیفت بسازید"
        if (!date.isValid) return "تاریخ نامعتبر"
        if ((type == EntryType.MANUAL_SHIFT || type == EntryType.EXCHANGE) && shiftCode == null) return "شیفت را انتخاب کنید"
        if (type == EntryType.OVERTIME && (minutes == null || minutes <= 0)) return "مدت اضافه‌کاری را به دقیقه وارد کنید"
        viewModelScope.launch { dao.insert(DayEntry(0, p.id, date.key, type, shiftCode, minutes, text, employeeId)) }
        return null
    }
    fun deleteEntry(id: Long) = viewModelScope.launch { dao.deleteEntry(id) }
    fun addEmployee(name: String) { if (name.isNotBlank()) viewModelScope.launch { dao.insert(Employee(0, name.trim())) } }
    fun deleteEmployee(id: Long) = viewModelScope.launch { dao.deleteEmployee(id) }

    fun report(): MonthReport? {
        val p = selected.value ?: return null; val m = month.value
        return ReportCalculator.month(m.year, m.month, JalaliDate(p.baseYear, p.baseMonth, p.baseDay), ShiftPattern.parse(p.pattern), entriesByDate.value)
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) =
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }

    private fun renderBytes(kind: ExportKind): ByteArray {
        val p = selected.value; val m = month.value
        val cal = holidayCalendar.value
        return when (kind) {
            ExportKind.CSV -> {
                p ?: error("الگویی انتخاب نشده")
                CsvExport.month(m.year, m.month, p.base(), ShiftPattern.parse(p.pattern), entriesByDate.value).toByteArray(Charsets.UTF_8)
            }
            ExportKind.PDF, ExportKind.XLSX -> {
                p ?: error("الگویی انتخاب نشده")
                val t = ExportModels.cardex(m.year, m.month, p.name, p.base(), ShiftPattern.parse(p.pattern), entriesByDate.value, cal, prefs.value.shiftTimes)
                if (kind == ExportKind.PDF) TableRenderer.toPdf(t)
                else ByteArrayOutputStream().also { XlsxWriter.write(t, it) }.toByteArray()
            }
            ExportKind.PNG_FOUR -> {
                val cols = fourColumns()
                if (cols.all { it == null }) error("هیچ ستونی در جدول چهارشیفت انتخاب نشده")
                TableRenderer.toPng(ExportModels.fourShift(m.year, m.month, cols, cal))
            }
        }
    }

    private fun extOf(k: ExportKind) = when (k) { ExportKind.CSV -> "csv"; ExportKind.PDF -> "pdf"; ExportKind.XLSX -> "xlsx"; ExportKind.PNG_FOUR -> "png" }
    private fun mimeOf(k: ExportKind) = when (k) {
        ExportKind.CSV -> "text/csv"; ExportKind.PDF -> "application/pdf"; ExportKind.PNG_FOUR -> "image/png"
        ExportKind.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
    fun mime(k: ExportKind) = mimeOf(k)
    fun suggestedName(k: ExportKind): String { val m = month.value; return "shift-${m.year}-${"%02d".format(m.month)}.${extOf(k)}" }

    fun export(kind: ExportKind, uri: Uri) = viewModelScope.launch {
        runCatching { writeBytes(uri, withContext(Dispatchers.Default) { renderBytes(kind) }) }
            .onSuccess { message.value = "فایل ذخیره شد" }.onFailure { message.value = "خطا در ذخیره: ${it.message}" }
    }

    fun share(kind: ExportKind) = viewModelScope.launch {
        runCatching {
            val bytes = withContext(Dispatchers.Default) { renderBytes(kind) }
            ShareUtil.shareBytes(getApplication(), bytes, suggestedName(kind), mimeOf(kind))
        }.onFailure { message.value = "خطا در اشتراک‌گذاری: ${it.message}" }
    }

    fun backupNowToAppFolder() { AutoBackup.runNow(getApplication()); message.value = "پشتیبان‌گیری شروع شد" }

    fun backup(uri: Uri) = viewModelScope.launch {
        runCatching { writeBytes(uri, Backup.toJson(Snapshot(dao.patternsOnce(), dao.allEntries(), dao.employeesOnce(), dao.settingsOnce(), dao.holidaysOnce())).toByteArray(Charsets.UTF_8)) }
            .onSuccess { message.value = "پشتیبان ذخیره شد" }.onFailure { message.value = "خطا در پشتیبان‌گیری: ${it.message}" }
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            dao.replaceAll(Backup.fromJson(text))   // اعتبارسنجی کامل قبل از هر تغییر؛ اعمال اتمی
        }.onSuccess { reloadSettings(); message.value = "بازیابی انجام شد" }.onFailure { message.value = "بازیابی ناموفق (داده‌ها تغییر نکردند): ${it.message}" }
    }
}
