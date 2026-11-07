package ir.shiftapp.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import kotlinx.coroutines.launch
import ir.shiftapp.system.PortalVault
import ir.shiftapp.core.*
import ir.shiftapp.system.AutoBackup
import ir.shiftapp.system.NoticeLoader
import ir.shiftapp.system.DefaultTiles
import ir.shiftapp.system.EnglishWidget
import ir.shiftapp.system.ShiftWidget
import ir.shiftapp.system.WidgetPin
import ir.shiftapp.system.ReminderScheduler

/** نمایش مدت: ۰ → «هم‌زمان با شروع»، ۹۰ → «۱ ساعت و ۳۰ دقیقه» */
internal fun leadLabel(min: Int): String = when {
    min <= 0 -> "هم‌زمان با شروع"
    min < 60 -> "${Fa.num(min)} دقیقه"
    min % 60 == 0 -> "${Fa.num(min / 60)} ساعت"
    else -> "${Fa.num(min / 60)} ساعت و ${Fa.num(min % 60)} دقیقه"
}

/** ارقامِ خام را هنگام نمایش به «ساعت:دقیقه» تبدیل می‌کند (۱۷۳۰ ← ۱۷:۳۰)؛ متن واقعی فیلد فقط رقم می‌ماند. */
private object ClockVisual : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        if (raw.length <= 2) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val k = raw.length - 2
        val shown = raw.substring(0, k) + ":" + raw.substring(k)
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= k) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= k) offset else (offset - 1).coerceAtLeast(k)
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(shown), map)
    }
}

/**
 * ورود ساعت بدون زحمت: فقط رقم بزنید، «:» خودکار گذاشته می‌شود (۵۳۰ ← ۵:۳۰، ۱۷۳۰ ← ۱۷:۳۰، ۰۶ ← ۰۶:۰۰).
 * اگر کاربر «:» یا «.» هم بزند نادیده گرفته می‌شود. فقط ساعت (مثلاً ۱۷) هم کافی است.
 */
@Composable
internal fun ClockField(label: String, initialMin: Int?, onValid: (Int) -> Unit, modifier: Modifier = Modifier, onInvalid: (() -> Unit)? = null) {
    var text by remember { mutableStateOf(initialMin?.let { Fa.digits(ShiftTime.fmt(it).replace(":", "")) } ?: "") }
    val parsed = ShiftTime.parseClock(text)
    AppField(
        text, { raw ->
            text = Fa.digits(Fa.latin(raw).filter { c -> c.isDigit() }.take(4))
            val v = ShiftTime.parseClock(text)
            if (v != null) onValid(v) else onInvalid?.invoke()
        },
        label, modifier, placeholder = "مثلاً ۰۵۳۰", keyboard = KeyboardType.Number, ltr = true,
        isError = text.isNotEmpty() && parsed == null, visualTransformation = ClockVisual
    )
}

@Composable
internal fun ShiftTimeRow(code: Int, prefs: Prefs, vm: MainVm) {
    val t = prefs.shiftTimes[code]
    var start by remember { mutableStateOf(t?.startMin) }
    var end by remember { mutableStateOf(t?.endMin) }
    fun save() { val s = start; val e = end; if (s != null && e != null && (s != e || code == 5)) vm.setPref(PrefKeys.time(code), "$s-$e") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShiftChip(code, Modifier.width(96.dp), compact = true)
            if (t != null) Text(
                "${Fa.num(t.durationMin / 60)} ساعت" + if (t.durationMin % 60 != 0) " و ${Fa.num(t.durationMin % 60)} دقیقه" else "",
                style = MaterialTheme.typography.bodySmall, color = Pal.muted
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockField("شروع", t?.startMin, { start = it; save() }, Modifier.weight(1f))
            ClockField("پایان", t?.endMin, { end = it; save() }, Modifier.weight(1f))
        }
    }
}

/** ردیف کلید/سوئیچ داخل کارت تنظیمات (قرص ملایم). */
@Composable
private fun GroupSwitch(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    SwitchRow(title, checked, onChange, Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer), subtitle)
}

@Composable
fun SettingsScreen(vm: MainVm) {
    val prefs by vm.prefs.collectAsState()
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val themeKeys = listOf("system", "light", "dark")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsGroup("ظاهر برنامه", icon = R.drawable.ic_palette) {
            Segmented(listOf("مثل سیستم", "روشن", "تاریک"), themeKeys.indexOf(prefs.theme).coerceAtLeast(0)) { vm.setPref(PrefKeys.THEME, themeKeys[it]) }
            val appCtx = LocalContext.current
            var callOn by remember { mutableStateOf(UiFlags.callButton(appCtx)) }
            GroupSwitch("دکمهٔ تماس روی صفحهٔ اول", callOn, "دکمهٔ گرد دفترچه تلفن؛ با خاموش کردن (از نوبت بعدی باز شدن صفحهٔ اول) پنهان می‌شود.") { callOn = it; UiFlags.setCallButton(appCtx, it) }
        }

        SettingsGroup("ساعت شروع و پایان شیفت‌ها", icon = R.drawable.ic_schedule) {
            HintText("قوانین هر شرکت فرق دارد؛ مقدار پیش‌فرض (روزکار ۰۷–۱۹، عصرکار ۱۴–۲۲، شبکار ۱۹–۰۷) فقط نقطهٔ شروع است. اگر پایان از شروع کوچک‌تر باشد، شیفت از نیمه‌شب می‌گذرد.")
            listOf(1, 2, 3, 5, 6).forEach { ShiftTimeRow(it, prefs, vm) }
        }

        SettingsGroup("یادآور شیفت", icon = R.drawable.ic_notifications) {
            GroupSwitch("یادآور فعال باشد", prefs.reminderEnabled) { on ->
                vm.setPref(PrefKeys.REM_ON, on.toString())
                if (on && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (prefs.reminderEnabled) {
                val showLead = true
                if (showLead) {
                    Text("چه مدت قبل از شروع شیفت؟", style = MaterialTheme.typography.titleSmall)
                    var tick by remember { mutableStateOf(0) }
                    ChipRow {
                        listOf(0, 15, 30, 60, 120, 180, 240, 360, 480, 720).forEach { m ->
                            AppChip(leadLabel(m), prefs.reminderLeadMin == m, { vm.setPref(PrefKeys.REM_LEAD, m.toString()); tick++ })
                        }
                    }
                    HintText("یا مقدار دلخواه را بنویسید:")
                    var hh by remember(tick) { mutableStateOf((prefs.reminderLeadMin / 60).toString().let { Fa.digits(it) }) }
                    var mm by remember(tick) { mutableStateOf((prefs.reminderLeadMin % 60).toString().let { Fa.digits(it) }) }
                    fun commit() {
                        val h = Fa.latin(hh).toIntOrNull() ?: 0
                        val m = Fa.latin(mm).toIntOrNull() ?: 0
                        if (m in 0..59 && h in 0..72 && h * 60 + m <= 72 * 60) vm.setPref(PrefKeys.REM_LEAD, (h * 60 + m).toString())
                    }
                    val mmBad = (Fa.latin(mm).toIntOrNull() ?: 0) > 59
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(hh, { hh = it.filter { c -> c.isDigit() }.take(2); commit() }, "ساعت", Modifier.weight(1f), keyboard = KeyboardType.Number)
                        AppField(mm, { mm = it.filter { c -> c.isDigit() }.take(2); commit() }, "دقیقه", Modifier.weight(1f), keyboard = KeyboardType.Number, isError = mmBad, supporting = if (mmBad) "حداکثر ۵۹" else null)
                    }
                    Text("مقدار فعلی: ${leadLabel(prefs.reminderLeadMin)}" + if (prefs.reminderLeadMin > 0) " قبل از شروع" else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val sample = listOf(1, 2, 3, 5, 6).filter { it in prefs.reminderCodes }.firstNotNullOfOrNull { c -> prefs.shiftTimes[c]?.let { c to it } }
                    if (sample == null) {
                        Text("برای شیفت‌های انتخاب‌شده ساعت شروع تنظیم نشده؛ از بخش «ساعت شروع و پایان شیفت‌ها» بالا تنظیم کنید، وگرنه یادآور ساخته نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        val (c, t) = sample
                        val raw = t.startMin - prefs.reminderLeadMin
                        val at = Math.floorMod(raw, 1440)
                        val dayNote = when { raw < 0 && raw >= -1440 -> " (روز قبل)"; raw < -1440 -> " (چند روز قبل)"; else -> "" }
                        HintText("مثال: ${shiftName(c)} ساعت ${Fa.digits(ShiftTime.fmt(t.startMin))} شروع می‌شود ← یادآور ساعت ${Fa.digits(ShiftTime.fmt(at))}$dayNote")
                    }
                }
                Text("نوع هشدار", style = MaterialTheme.typography.titleSmall)
                Segmented(listOf("اعلان", "آلارم"), if (prefs.reminderAlarm) 1 else 0) { vm.setPref(PrefKeys.REM_ALARM, (it == 1).toString()) }
                HintText(if (prefs.reminderAlarm) "آلارم: با صدای آلارم و لرزش، دقیق در زمان تعیین‌شده؛ مناسب بیدار شدن برای شیفت." else "اعلان: اعلان معمولی با صدای اعلان گوشی؛ در حالت بی‌صدا بی‌صدا می‌آید.")
                Text("برای کدام شیفت‌ها یادآوری شود", style = MaterialTheme.typography.titleSmall)
                ChipRow {
                    listOf(1, 2, 3, 5, 6).forEach { c ->
                        val on = c in prefs.reminderCodes
                        AppChip(shiftName(c), on, {
                            val n = if (on) prefs.reminderCodes - c else prefs.reminderCodes + c
                            vm.setPref(PrefKeys.REM_CODES, n.sorted().joinToString(","))
                        }, dotColor = shiftColor(c))
                    }
                }
                HintText("روزهای مرخصی و روزهای استراحت یادآور ندارند. یادآور فقط برای الگوی انتخاب‌شدهٔ فعلی ساخته می‌شود.")
                if (Build.VERSION.SDK_INT >= 31 && !prefs.reminderAlarm) {
                    val am = ctx.getSystemService(AlarmManager::class.java)
                    if (am != null && !am.canScheduleExactAlarms()) {
                        Text("مجوز «زنگ و یادآور دقیق» داده نشده؛ اعلان ممکن است چند دقیقه دیرتر برسد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        AppButton("دادن مجوز", { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
                    }
                }
                AppButton("ارسال اعلان آزمایشی", { ReminderScheduler.show(ctx, "یادآور شیفت", "این یک اعلان آزمایشی است", prefs.reminderAlarm) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_notifications, style = BtnStyle.Tonal)
            }
        }

        SettingsGroup("اطمینان از آلارم و اذان", icon = R.drawable.ic_alarm) {
            ReliabilityPanel(vm, prefs)
        }

        SettingsGroup("اعلان تاریخ و اطلاعات", icon = R.drawable.ic_today) {
            GroupSwitch(
                "نمایش همیشگی در نوار اعلان‌ها", prefs.dateBar,
                "تاریخ شمسی، میلادی و قمری، شیفت امروز و فردا و (اگر روشن باشد) اذان بعدی؛ بی‌صدا و همیشه بالای اعلان‌ها."
            ) { on ->
                vm.setPref(PrefKeys.DATE_BAR, on.toString())
                if (on && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (prefs.dateBar) HintText("اگر اعلان نمایش داده نمی‌شود، مجوز اعلان برنامه را در تنظیمات گوشی بررسی کنید. اگر آن را کنار بزنید، خودکار دوباره گذاشته می‌شود؛ برای برداشتن کامل، همین کلید را خاموش کنید.")
        }

        SettingsGroup("امکانات مذهبی (اختیاری)", icon = R.drawable.ic_qibla) {
            GroupSwitch("اوقات شرعی، قبله و اذان", prefs.prayerOn, "پیش‌فرض خاموش است؛ با روشن کردن، بخش «اوقات شرعی و قبله» به صفحهٔ اول اضافه می‌شود.") {
                vm.setPref(PrefKeys.PRAYER_ON, it.toString())
            }
            if (prefs.prayerOn) HintText("شهر، روش محاسبه، هشدار اذان و صدا را از خود بخش «اوقات شرعی و قبله» در صفحهٔ اول تنظیم کنید. با خاموش کردن، همهٔ هشدارهای اذان هم متوقف می‌شود.")
        }

        SettingsGroup("کاشی‌ها (سامانه‌ها و بخش‌ها)", icon = R.drawable.ic_open_new) {
            val sites = remember(prefs.sitesRaw) { TilesPackFile.decodeSites(prefs.sitesRaw) }
            val special by vm.specialOn.collectAsState()
            val enabled by vm.enabledSections.collectAsState()
            val usable = TilesPackFile.SECTIONS.filter { special || it in enabled }.toSet()
            val scope = rememberCoroutineScope()
            val tilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openPatternFile(uri) }
            var del by remember { mutableStateOf<SiteTile?>(null) }
            var edit by remember { mutableStateOf<SiteTile?>(null) }
            Text(if (sites.isEmpty()) "هنوز سامانه‌ای اضافه نشده" else "${sites.size.fa()} سامانه", style = MaterialTheme.typography.bodyMedium)
            sites.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ scope.launch { shareTilesFile(ctx, listOf(s), emptySet(), prefs.mealFeed, prefs.adFeed, prefs.adPublic, prefs.noticeFeed, prefs.noticePublic, prefs.configFeed) } }) { Text("ارسال") }
                    TextButton({ edit = s }) { Text("ویرایش") }
                    TextButton({ del = s }) { Text("حذف") }
                }
            }
            SiteUpdateBox(prefs.configFeed, sites)
            AppButton("وارد کردن فایل کاشی‌ها", { tilesPicker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_download)
            if (sites.isNotEmpty() || usable.isNotEmpty()) {
                AppButton("ارسال فایل کاشی‌ها", { scope.launch { shareTilesFile(ctx, sites, usable, prefs.mealFeed, prefs.adFeed, prefs.adPublic, prefs.noticeFeed, prefs.noticePublic, prefs.configFeed) } }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
            }
            HintText("برنامه هیچ سامانه‌ای به‌صورت پیش‌فرض ندارد؛ دفترچه تلفن و برنامه غذایی هم پنهان‌اند. سامانه‌ها و بخش‌های مخفی فقط با وارد کردن فایل کاشی‌ها (.shiftma) اضافه یا نمایان می‌شوند. فایل کاشی‌های دیگر را هم می‌شود وارد کرد یا با زدن روی خود فایل باز کرد. نام کاربری و رمز هرگز داخل فایل نمی‌آید.")
            edit?.let { e ->
                AddSiteDialog(
                    onAdd = { t, u, sb -> vm.editSite(e.key, t, u, sb) }, onDismiss = { edit = null },
                    heading = "ویرایش سامانه", confirmLabel = "ذخیره", initialTitle = e.title, initialUrl = e.url, initialSubtitle = e.subtitle
                )
            }
            del?.let { d ->
                ConfirmDialog("سامانهٔ «${d.title}» از صفحهٔ اول حذف شود؟", "حذف", { vm.removeSite(d.key); del = null }, { del = null }, danger = true)
            }
        }

        val portalSites = remember(prefs.sitesRaw) { TilesPackFile.decodeSites(prefs.sitesRaw) }
        if (portalSites.isNotEmpty()) SettingsGroup("مرورگر سامانه‌های کاری", icon = R.drawable.ic_open_new) {
            portalSites.forEach { l ->
                val key = l.key
                GroupSwitch("${l.title} در مرورگر داخلی باز شود", key !in prefs.externalPortals, "خاموش: مثل قبل در مرورگر گوشی باز می‌شود") { inApp ->
                    val next = if (inApp) prefs.externalPortals - key else prefs.externalPortals + key
                    vm.setPref(PrefKeys.PORTAL_EXTERNAL, next.sorted().joinToString(","))
                }
            }
            HintText("مرورگر داخلی دکمهٔ «دانلود فایل PDF» دارد. داخل آن هم دکمهٔ باز کردن صفحه در مرورگر گوشی هست.")
        }

        SettingsGroup("نام کاربری و رمز سامانه‌ها", icon = R.drawable.ic_lock) {
            var saved by remember { mutableStateOf(PortalVault.count(ctx)) }
            var confirm by remember { mutableStateOf(false) }
            Text(if (saved == 0) "هنوز چیزی ذخیره نشده" else "${saved.fa()} سامانه ذخیره شده", style = MaterialTheme.typography.bodyMedium)
            HintText("برای ذخیره، سامانه را باز کن و دکمهٔ قفل بالای مرورگر را بزن. اطلاعات فقط روی همین گوشی و رمزنگاری‌شده می‌ماند و در پشتیبان نیست.")
            if (saved > 0) AppButton("حذف همهٔ نام‌های کاربری و رمزها", { confirm = true }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
            if (confirm) ConfirmDialog("همهٔ نام‌های کاربری و رمزهای ذخیره‌شده از این گوشی حذف شود؟", "حذف", { PortalVault.clear(ctx); saved = 0; confirm = false }, { confirm = false }, danger = true)
        }

        SettingsGroup("ویجت صفحهٔ اصلی گوشی", icon = R.drawable.ic_home) { WidgetButtons() }

        SettingsGroup("پشتیبان‌گیری خودکار", icon = R.drawable.ic_backup) {
            GroupSwitch("هر روز خودکار پشتیبان بگیر", prefs.autoBackup) { vm.setPref(PrefKeys.AUTO_BACKUP, it.toString()) }
            var keep by remember { mutableStateOf(prefs.autoBackupKeep.toString()) }
            AppField(keep, { v ->
                keep = v.filter(Char::isDigit).take(2)
                keep.toIntOrNull()?.takeIf { it in 1..60 }?.let { vm.setPref(PrefKeys.AUTO_KEEP, it.toString()) }
            }, "تعداد نسخه‌های نگه‌داشته‌شده", keyboard = KeyboardType.Number)
            val files = remember(prefs.autoBackup, prefs.autoBackupKeep) { AutoBackup.files(ctx) }
            Text(if (files.isEmpty()) "هنوز نسخه‌ای ساخته نشده" else "${files.size.fa()} نسخه؛ آخرین: ${files.first().name}", style = MaterialTheme.typography.bodyMedium)
            HintText("پوشه: ${AutoBackup.dir(ctx).absolutePath}")
            AppButton("همین حالا پشتیبان بگیر", { vm.backupNowToAppFolder() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_backup, style = BtnStyle.Tonal)
            HintText("این پوشه با حذف برنامه پاک می‌شود؛ برای نگهداری بیرونی از «پشتیبان‌گیری (Backup)» در بخش گزارش استفاده کنید.")
        }
    }
}

/** دکمه‌های افزودن ویجت از داخل برنامه (وقتی ویجت در فهرست ابزارک‌های گوشی پیدا نمی‌شود). */
@Composable
internal fun WidgetButtons() {
    val ctx = LocalContext.current
    fun add(cls: Class<*>) {
        val ok = WidgetPin.supported(ctx) && WidgetPin.pin(ctx, cls)
        android.widget.Toast.makeText(
            ctx,
            if (ok) "پنجرهٔ تأیید لانچر را بپذیرید" else "لانچر گوشی افزودن مستقیم را پشتیبانی نمی‌کند؛ از فهرست ابزارک‌ها اضافه کنید",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    AppButton("افزودن ویجت شیفت (نوار شیفت، هفته و آلارم‌ها)", { add(ShiftWidget::class.java) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
    AppButton("افزودن ویجت لغت روز انگلیسی", { add(EnglishWidget::class.java) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
    HintText("در شیائومی اگر پنجرهٔ تأیید نیامد: تنظیمات ← برنامه‌ها ← مدیریت برنامه‌ها ← شیفت ما ← مجوزهای دیگر ← «میانبرهای صفحهٔ اصلی» را روی مجاز بگذارید و دوباره امتحان کنید.")
}


private fun fmtStamp(ts: Long): String {
    if (ts <= 0L) return "هنوز انجام نشده"
    val dt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault())
    val j = JalaliCalendar.fromLocalDate(dt.toLocalDate())
    return Fa.digits("${j.year}/${"%02d".format(j.month)}/${"%02d".format(j.day)}  ${"%02d".format(dt.hour)}:${"%02d".format(dt.minute)}")
}

/** وضعیت به‌روزرسانی راه‌دور سامانه‌ها: آخرین بررسی، دکمهٔ بررسی دستی، آخرین تغییرها و علت اعمال‌نشدن. فقط در تنظیمات (جلوی چشم نیست). */
@Composable
private fun SiteUpdateBox(configFeed: String, sites: List<SiteTile>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by ir.shiftapp.system.ConfigLoader.tick.collectAsState()
    val remote by ir.shiftapp.system.ConfigLoader.config.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val hasFeed = ir.shiftapp.system.ConfigLoader.feedUrl(ctx).isNotEmpty()
    val log = remember(tick, msg) { ir.shiftapp.system.UpdateLog.entries(ctx) }
    val skipped = remember(remote, sites) { SiteUpdates.skippedEdited(sites, remote.siteUpdates) }
    // کاربرِ بدون منطقه چیزی از این قابلیت نمی‌بیند (نه کادر، نه توضیحی دربارهٔ آدرس)
    if (hasFeed) {
        Text("به‌روزرسانی آنلاین سامانه‌ها", style = MaterialTheme.typography.titleSmall)
        val ok = remember(tick, msg) { ir.shiftapp.system.UpdateLog.lastOk(ctx) }
        Text("آخرین بررسی: ${fmtStamp(remember(tick, msg) { ir.shiftapp.system.UpdateLog.lastCheck(ctx) })}" + if (ok) "" else " (ناموفق)", style = MaterialTheme.typography.bodySmall)
        if (!ok) HintText(ir.shiftapp.system.UpdateLog.lastError(ctx).ifEmpty { "بررسی ناموفق بود" })
        Text("تغییر راه‌دور شناخته‌شده: ${remote.siteUpdates.size.fa()} سامانه", style = MaterialTheme.typography.bodySmall)
        AppButton(if (busy) "در حال بررسی…" else "بررسی و به‌روزرسانی الان", {
            if (!busy) { busy = true; scope.launch { val r = ir.shiftapp.system.ConfigLoader.refreshNow(ctx); msg = if (r) "بررسی انجام شد" else "بررسی ناموفق بود"; busy = false } }
        }, Modifier.fillMaxWidth(), icon = R.drawable.ic_download, enabled = !busy)
        if (msg.isNotEmpty()) HintText(msg)
    }
    if (skipped.isNotEmpty()) HintText("چون خودتان ویرایش کرده‌اید اعمال نشد: " + skipped.joinToString("، ") { it.title } + ". برای دریافت آدرس آنلاین، سامانه را حذف و دوباره از فایل کاشی‌ها وارد کنید.")
    if (log.isNotEmpty()) {
        Text("آخرین تغییرها", style = MaterialTheme.typography.bodySmall)
        log.take(5).forEach { Text("${fmtStamp(it.ts)} — ${it.text}", style = MaterialTheme.typography.bodySmall) }
    } else if (hasFeed) HintText("هنوز تغییری اعمال نشده است. بررسی خودکار هر ۳ ساعت و هنگام باز شدن برنامه انجام می‌شود.")
}
