package ir.shiftapp.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.phonebook.fixDigits
import java.time.LocalDate

/** ابزارهای جانبیِ صفحهٔ اول: تبدیل واحد، محاسبهٔ سن، سایت‌های ضروری و تلویزیون. */

private fun faS(s: String): String = s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

@Composable
internal fun ToolScaffold(title: String, subtitle: String, onHome: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onHome() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome, subtitle = subtitle) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

// ───────────────────────── تبدیل واحد ─────────────────────────

@Composable
private fun UnitPicker(label: String, units: List<ConvUnit>, selected: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button) { open = true }.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(units[selected].name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Ico(R.drawable.ic_expand_more, size = 18.dp, tint = Pal.muted)
            }
        }
        DropdownMenu(open, { open = false }) {
            units.forEachIndexed { i, u -> DropdownMenuItem(text = { Text(u.name) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConvertSection(onHome: () -> Unit) {
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var from by rememberSaveable { mutableIntStateOf(0) }
    var to by rememberSaveable { mutableIntStateOf(1) }
    var text by rememberSaveable { mutableStateOf("1") }
    val c = UnitConverter.CATEGORIES[cat]
    val fi = from.coerceIn(0, c.units.lastIndex)
    val ti = to.coerceIn(0, c.units.lastIndex)
    val f = c.units[fi]; val t = c.units[ti]
    val v = UnitConverter.parse(text)
    ToolScaffold("تبدیل واحد", "طول، وزن، دما، حجم و …", onHome) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UnitConverter.CATEGORIES.forEachIndexed { i, k -> AppChip(k.title, cat == i, { cat = i; from = 0; to = 1 }) }
        }
        AppField(text, { text = it }, "مقدار", keyboard = KeyboardType.Decimal, ltr = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitPicker("از", c.units, fi, Modifier.weight(1f)) { from = it }
            IconButton({ val x = from; from = to; to = x }) { Ico(R.drawable.ic_swap, desc = "جابه‌جایی") }
            UnitPicker("به", c.units, ti, Modifier.weight(1f)) { to = it }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (v == null) "—" else faS(UnitConverter.format(UnitConverter.convert(f, t, v))) + " " + t.name,
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary
                )
                if (v != null) Text(faS(text.trim()) + " " + f.name, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
        }
        if (v != null) {
            SectionLabel("همهٔ واحدهای ${c.title}")
            AppCard(Modifier.fillMaxWidth()) {
                c.units.forEachIndexed { i, u ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(u.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(faS(UnitConverter.format(UnitConverter.convert(f, u, v))), style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
                    }
                }
            }
        }
    }
}

// ───────────────────────── محاسبهٔ سن ─────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pal.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AgeSection(onHome: () -> Unit) {
    val today = remember { JalaliDate.today() }
    var y by rememberSaveable { mutableStateOf("") }
    var m by rememberSaveable { mutableStateOf("") }
    var d by rememberSaveable { mutableStateOf("") }
    val yy = fixDigits(y).toIntOrNull(); val mm = DateText.parseMonth(0, m); val dd = fixDigits(d).toIntOrNull()
    val filled = y.isNotBlank() && m.isNotBlank() && d.isNotBlank()
    val birth = if (yy != null && mm != null && dd != null && yy in 1..3000 && JalaliCalendar.isValid(yy, mm, dd)) JalaliDate(yy, mm, dd) else null
    val r = birth?.let { AgeCalc.compute(it, today) }
    ToolScaffold("محاسبهٔ سن", "تاریخ تولد شمسی را وارد کنید", onHome) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(y, { y = it.take(4) }, "سال", Modifier.weight(1f), placeholder = "مثلا : ۱۳۷۵", keyboard = KeyboardType.Number, ltr = true)
            AppField(m, { m = it.take(14) }, "ماه", Modifier.weight(1.3f), placeholder = "عدد یا نام")
            AppField(d, { d = it.take(2) }, "روز", Modifier.weight(1f), keyboard = KeyboardType.Number, ltr = true)
        }
        if (filled && birth == null) HintText("این تاریخ معتبر نیست.")
        else if (birth != null && r == null) HintText("تاریخ تولد در آینده است.")
        if (r != null) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("سن شما", style = MaterialTheme.typography.labelMedium, color = Pal.muted)
                    Text(
                        "${r.years.fa()} سال و ${r.months.fa()} ماه و ${r.days.fa()} روز",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                InfoRow("مجموع روزهای زندگی", r.totalDays.fa())
                RowDivider()
                InfoRow("روز هفتهٔ تولد", WEEK_FULL[r.weekday])
                RowDivider()
                InfoRow("تاریخ تولد میلادی", faS(r.gregorian.toString()))
                RowDivider()
                InfoRow(
                    "تولد بعدی",
                    if (r.daysToBirthday == 0) "امروز!" else "${r.nextBirthday.day.fa()} ${MONTHS[r.nextBirthday.month - 1]} · ${r.daysToBirthday.fa()} روز دیگر"
                )
            }
        }
    }
}

// ───────────────────────── محاسبهٔ مدت (از تاریخ تا تاریخ) ─────────────────────────

@Composable
private fun SpanDateFields(label: String, y: String, m: String, d: String, onY: (String) -> Unit, onM: (String) -> Unit, onD: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Pal.muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppField(y, { onY(it.take(4)) }, "سال", Modifier.weight(1f), placeholder = "مثلا : ۱۳۹۰", keyboard = KeyboardType.Number, ltr = true)
        AppField(m, { onM(it.take(14)) }, "ماه", Modifier.weight(1.3f), placeholder = "عدد یا نام")
        AppField(d, { onD(it.take(2)) }, "روز", Modifier.weight(1f), keyboard = KeyboardType.Number, ltr = true)
    }
}

private fun spanDate(y: String, m: String, d: String): JalaliDate? {
    val yy = fixDigits(y).toIntOrNull(); val mm = DateText.parseMonth(0, m); val dd = fixDigits(d).toIntOrNull()
    return if (yy != null && mm != null && dd != null && yy in 1..3000 && JalaliCalendar.isValid(yy, mm, dd)) JalaliDate(yy, mm, dd) else null
}

@Composable
fun DateSpanSection(onHome: () -> Unit) {
    val today = remember { JalaliDate.today() }
    var fy by rememberSaveable { mutableStateOf("") }
    var fm by rememberSaveable { mutableStateOf("") }
    var fd by rememberSaveable { mutableStateOf("") }
    var ty by rememberSaveable { mutableStateOf("") }
    var tm by rememberSaveable { mutableStateOf("") }
    var td by rememberSaveable { mutableStateOf("") }
    var inclusive by rememberSaveable { mutableStateOf(true) }
    val fromFilled = fy.isNotBlank() && fm.isNotBlank() && fd.isNotBlank()
    val toFilled = ty.isNotBlank() && tm.isNotBlank() && td.isNotBlank()
    val toEmpty = ty.isBlank() && tm.isBlank() && td.isBlank()
    val from = spanDate(fy, fm, fd)
    val to = if (toEmpty) today else spanDate(ty, tm, td)      // پایانِ خالی = امروز
    val r = if (from != null && to != null) DateSpan.compute(from, to, inclusive) else null
    ToolScaffold("محاسبهٔ مدت", "از تاریخ تا تاریخ؛ سال، ماه و روز", onHome) {
        SpanDateFields("از تاریخ (شمسی)", fy, fm, fd, { fy = it }, { fm = it }, { fd = it })
        SpanDateFields("تا تاریخ (خالی = امروز)", ty, tm, td, { ty = it }, { tm = it }, { td = it })
        Row(
            Modifier.fillMaxWidth().clickable { inclusive = !inclusive },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("روز شروع و پایان هر دو حساب شود", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(inclusive, { inclusive = it })
        }
        if (fromFilled && from == null) HintText("تاریخ شروع معتبر نیست.")
        else if (toFilled && to == null) HintText("تاریخ پایان معتبر نیست.")
        else if (from != null && to != null && r == null) HintText("تاریخ پایان قبل از شروع است.")
        if (r != null) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("مدت", style = MaterialTheme.typography.labelMedium, color = Pal.muted)
                    Text(
                        "${r.years.fa()} سال و ${r.months.fa()} ماه و ${r.days.fa()} روز",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                InfoRow("مجموع روزها", r.totalDays.fa())
                RowDivider()
                InfoRow("به شیوهٔ بیمه (ماه ۳۰ روز)", "${r.insYears.fa()} سال و ${r.insMonths.fa()} ماه و ${r.insDays.fa()} روز")
            }
        }
    }
}

// ───────────────────────── مبدل تاریخ ─────────────────────────

private val DATE_KINDS = listOf("شمسی", "میلادی", "قمری")

@Composable
fun DateConvertSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val shift = prefs.lunarShift
    var kind by rememberSaveable { mutableStateOf(0) }
    var y by rememberSaveable { mutableStateOf("") }
    var m by rememberSaveable { mutableStateOf("") }
    var d by rememberSaveable { mutableStateOf("") }
    val yy = fixDigits(y).toIntOrNull(); val mm = DateText.parseMonth(kind, m); val dd = fixDigits(d).toIntOrNull()
    val filled = y.isNotBlank() && m.isNotBlank() && d.isNotBlank()
    val date: LocalDate? = if (yy != null && mm != null && dd != null) when (kind) {
        0 -> if (yy in 1..3000 && JalaliCalendar.isValid(yy, mm, dd)) JalaliDate(yy, mm, dd).toLocalDate() else null
        1 -> runCatching { LocalDate.of(yy, mm, dd) }.getOrNull()
        else -> DateInfo.fromHijri(yy, mm, dd, shift)
    } else null
    val hint = when (kind) { 0 -> "مثلا : ۱۴۰۵"; 1 -> "مثلا : ۲۰۲۶"; else -> "مثلا : ۱۴۴۸" }

    ToolScaffold("مبدل تاریخ", "تبدیل تاریخ شمسی، میلادی و قمری", onHome) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DATE_KINDS.forEachIndexed { i, label ->
                AppChip(label, kind == i, { kind = i; y = ""; m = ""; d = "" }, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(y, { y = it.take(4) }, "سال", Modifier.weight(1f), placeholder = hint, keyboard = KeyboardType.Number, ltr = true)
            AppField(m, { m = it.take(14) }, "ماه", Modifier.weight(1.3f), placeholder = "عدد یا نام")
            AppField(d, { d = it.take(2) }, "روز", Modifier.weight(1f), keyboard = KeyboardType.Number, ltr = true)
        }
        if (m.isNotBlank()) {
            if (mm != null) HintText("ماه: ${DateText.monthName(kind, mm) ?: ""} (${mm.fa()})")
            else HintText("ماه را با عدد ۱ تا ۱۲ یا با نام آن بنویسید.")
        }
        AppButton("امروز", {
            val now = LocalDate.now()
            val parts: Triple<Int, Int, Int>? = when (kind) {
                0 -> JalaliCalendar.fromLocalDate(now).let { Triple(it.year, it.month, it.day) }
                1 -> Triple(now.year, now.monthValue, now.dayOfMonth)
                else -> DateInfo.hijri(now, shift)?.let { Triple(it.year, it.month, it.day) }
            }
            if (parts != null) { y = parts.first.toString(); m = parts.second.toString(); d = parts.third.toString() }
        }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
        if (filled && date == null) HintText("این تاریخ معتبر نیست.")
        if (date != null) {
            val jd = JalaliCalendar.fromLocalDate(date)
            val hj = DateInfo.hijri(date, shift)
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    InfoRow("روز هفته", WEEK_FULL[jd.dayOfWeek])
                    RowDivider()
                    InfoRow("شمسی", "${jd.day.fa()} ${MONTHS[jd.month - 1]} ${jd.year.fa()}")
                    RowDivider()
                    InfoRow("میلادی", DateInfo.gregorianText(date))
                    RowDivider()
                    InfoRow("قمری", if (hj != null) DateInfo.hijriText(hj) else "—")
                }
            }
            HintText("تاریخ قمری بر پایهٔ تقویم ام‌القری است و با رؤیت ماه در ایران ممکن است ۱ تا ۲ روز فرق کند؛ در تنظیمات می‌توانید آن را تطبیق دهید.")
        }
    }
}

// ───────────────────────── سایت‌های ضروری و عمومی ─────────────────────────

/** پوستهٔ مشترک نوارهای افقی صفحهٔ اول (سایت‌های ضروری، تلویزیون): پس‌زمینهٔ نیلیِ در حال تغییر و حرکت خودکار تا اولین لمس. */
@Composable
internal fun StripShell(content: @Composable RowScope.() -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    // همان رنگ‌های کارت بالای صفحه (Pal.heroBrush)؛ بین دو سایهٔ نیلی به‌آرامی می‌رود‌وبرمی‌گردد
    val base = if (dark) Color(0xFF3A34B8) else Color(0xFF5348E0)
    val blue = if (dark) Color(0xFF211E73) else Color(0xFF3027A8)
    val t by rememberInfiniteTransition(label = "essBg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t"
    )
    val shape = RoundedCornerShape(18.dp)
    val scroll = rememberScrollState()
    var touched by remember { mutableStateOf(false) }
    // حرکت خودکار و ملایم رفت‌وبرگشتی؛ با اولین لمس کاربر برای همیشه (تا بازگشت به این صفحه) متوقف می‌شود
    LaunchedEffect(touched) {
        if (touched) return@LaunchedEffect
        delay(1500)
        var toEnd = true
        while (true) {
            val max = scroll.maxValue
            if (max <= 0) { delay(1000); continue }
            val target = if (toEnd) max else 0
            val dist = kotlin.math.abs(target - scroll.value)
            scroll.animateScrollTo(target, tween((dist * 12).coerceIn(600, 30000), easing = LinearEasing))
            delay(1500)
            toEnd = !toEnd
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(lerp(base, blue, t), lerp(blue, base, t)))).padding(vertical = 4.dp, horizontal = 8.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touched = true
                    }
                }
            }
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top,
            content = content
        )
    }
}

/** کاشی دایره‌ای/گرد «افزودن» یا «همه» در انتهای نوارها. */
@Composable
internal fun StripAction(label: String, icon: Int, outlined: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val box = Modifier.size(40.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.14f))
        Box(
            if (outlined) box.border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(15.dp)) else box,
            contentAlignment = Alignment.Center
        ) { Ico(icon, size = 20.dp, tint = Color.White) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1, textAlign = TextAlign.Center)
    }
}

// ───────────────────────── نوارهای قابل ویرایش (سایت‌های ضروری، تلویزیون) ─────────────────────────

/** وضعیت پنجره‌های مدیریتِ یک نوار (افزودن، منوی ویرایش/جابه‌جایی/حذف، بازنشانی)؛ پنجره‌ها را [StripDialogs] نشان می‌دهد. */
@Stable
class StripUi {
    var adding by mutableStateOf(false)
    var managing by mutableStateOf<String?>(null)     // کلید سایتی که منوی مدیریتش باز است
    var editing by mutableStateOf<String?>(null)
    var deleting by mutableStateOf<String?>(null)
    var resetting by mutableStateOf(false)
}

@Composable
fun rememberStripUi(): StripUi = remember { StripUi() }

/** نوار افقیِ سایت‌های ضروری در صفحهٔ اول. لمس طولانیِ هر سایت منوی ویرایش، جابه‌جایی و حذف را باز می‌کند. */
@Composable
fun EssentialSitesStrip(sites: List<StripSite>, ui: StripUi, onAll: () -> Unit) {
    val ctx = LocalContext.current
    StripShell {
        sites.forEach { s ->
            key(s.key) { EssentialSiteItem(s.title, s.url, Strip.ESSENTIAL.iconPrefix + s.key, { openUrl(ctx, s.url) }, { ui.managing = s.key }) }
        }
        StripAction("افزودن", R.drawable.ic_add, true) { ui.adding = true }
        StripAction("همه", R.drawable.ic_open_new, false, onAll)
    }
}

/** نوار تلویزیونِ صفحهٔ اول (زیر سایت‌های ضروری، همان ظاهر و همان امکانات ویرایش) و «همه» برای صفحهٔ کامل. */
@Composable
fun TvStrip(sites: List<StripSite>, ui: StripUi, onAll: () -> Unit) {
    val ctx = LocalContext.current
    StripShell {
        sites.forEach { s ->
            key(s.key) { EssentialSiteItem(s.title, s.url, Strip.TV.iconPrefix + s.key, { openStripSite(ctx, s) }, { ui.managing = s.key }) }
        }
        StripAction("افزودن", R.drawable.ic_add, true) { ui.adding = true }
        StripAction("همه", R.drawable.ic_tool_tv, false, onAll)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EssentialSiteItem(title: String, url: String, key: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val ctx = LocalContext.current
    var bytes by remember(key) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(key) { bytes = SiteIcon.load(ctx, key, url) }
    val bmp = remember(bytes) {
        bytes?.let { b -> runCatching { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
    }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) "ویرایش و جابه‌جایی" else null)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
            else Text(title.firstOrNull()?.toString().orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color(0xFF4338CA))
        }
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionRow(label: String, icon: Int, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Ico(icon, size = 22.dp, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/**
 * پنجره‌های مدیریت یک نوار: افزودن، منوی هر سایت (ویرایش، جابه‌جایی، حذف)، تأیید حذف و بازگشت به پیش‌فرض.
 * [vertical] = جابه‌جایی در فهرست عمودی (بالا/پایین) به‌جای نوار افقی (راست/چپ)؛ [allowMove] = false یعنی گزینهٔ جابه‌جایی نباشد.
 */
@Composable
fun StripDialogs(vm: MainVm, strip: Strip, sites: List<StripSite>, ui: StripUi, vertical: Boolean = false, allowMove: Boolean = true) {
    if (ui.adding) AddSiteDialog(onAdd = { t, u, _ -> vm.addStripSite(strip, t, u) }, onDismiss = { ui.adding = false }, showSubtitle = false)

    val managed = sites.firstOrNull { it.key == ui.managing }
    if (managed != null) {
        val i = sites.indexOf(managed)
        AppDialog(managed.title, { ui.managing = null }, dismissText = "بستن") {
            Text(TilesPackFile.hostOf(managed.url), style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            ActionRow("ویرایش", R.drawable.ic_edit) { ui.editing = managed.key; ui.managing = null }
            if (allowMove) {
                ActionRow(if (vertical) "جابه‌جایی به بالا" else "جابه‌جایی به راست", R.drawable.ic_swap, enabled = i > 0) { vm.moveStripSite(strip, managed.key, -1) }
                ActionRow(if (vertical) "جابه‌جایی به پایین" else "جابه‌جایی به چپ", R.drawable.ic_swap, enabled = i < sites.lastIndex) { vm.moveStripSite(strip, managed.key, 1) }
            }
            ActionRow("حذف", R.drawable.ic_delete, danger = true) { ui.deleting = managed.key; ui.managing = null }
        }
    }

    val edited = sites.firstOrNull { it.key == ui.editing }
    if (edited != null) AddSiteDialog(
        onAdd = { t, u, _ -> vm.editStripSite(strip, edited.key, t, u) }, onDismiss = { ui.editing = null },
        heading = "ویرایش سایت", confirmLabel = "ذخیره", initialTitle = edited.title, initialUrl = edited.url, showSubtitle = false
    )

    val doomed = sites.firstOrNull { it.key == ui.deleting }
    if (doomed != null) ConfirmDialog(
        "«${doomed.title}» از این نوار حذف شود؟", "حذف",
        { vm.removeStripSite(strip, doomed.key); ui.deleting = null }, { ui.deleting = null }, danger = true
    )

    if (ui.resetting) ConfirmDialog(
        "فهرست این نوار به حالت اولِ برنامه برمی‌گردد و سایت‌هایی که افزوده یا تغییر داده‌اید از بین می‌رود. ادامه می‌دهید؟", "بازگشت به پیش‌فرض",
        { vm.resetStrip(strip); ui.resetting = false }, { ui.resetting = false }, title = "بازگشت به پیش‌فرض", danger = true
    )
}

/** تلوبیون اگر روی گوشی نصب باشد با خود برنامه باز می‌شود؛ بقیهٔ سایت‌ها در مرورگر. */
private fun openStripSite(ctx: Context, s: StripSite) {
    if (TilesPackFile.hostOf(s.url).contains("telewebion")) {
        val i = ctx.packageManager.getLaunchIntentForPackage("net.telewebion")
        if (i != null) {
            try { ctx.startActivity(i); return } catch (_: Exception) { }
        }
    }
    openUrl(ctx, s.url)
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit, onManage: (() -> Unit)? = null) {
    val v = if (onManage != null) 6.dp else 12.dp
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = if (onManage != null) 6.dp else 16.dp, top = v, bottom = v),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ico(R.drawable.ic_open_new, size = 20.dp, tint = Pal.muted)
            if (onManage != null) SmallIconButton(R.drawable.ic_more_vert, "ویرایش و جابه‌جایی", onManage)
        }
    }
}

@Composable
fun SitesSection(vm: MainVm, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val sites = remember(prefs.essListRaw, prefs.essSitesRaw) { StripSites.resolve(Strip.ESSENTIAL, prefs.essListRaw, prefs.essSitesRaw) }
    val ui = rememberStripUi()
    val catalogUrls = remember { SiteCatalog.ALL.map { it.url.lowercase() }.toSet() }
    val mine = sites.filter { it.url.lowercase() !in catalogUrls }
    ToolScaffold("سایت‌های ضروری", "خدمات دولتی، اخبار و سایت‌های عمومی", onHome) {
        SiteCatalog.GROUPS.forEach { (group, items) ->
            SectionLabel(group)
            items.forEach { s -> LinkRow(s.title, s.subtitle, { openUrl(ctx, s.url) }) }
        }
        if (mine.isNotEmpty()) {
            SectionLabel("سایت‌های افزودهٔ من")
            mine.forEach { s -> LinkRow(s.title, TilesPackFile.hostOf(s.url), { openUrl(ctx, s.url) }, { ui.managing = s.key }) }
        }
        AppButton("افزودن سایت به نوار صفحهٔ اول", { ui.adding = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
        AppButton("بازگشت نوار به پیش‌فرض", { ui.resetting = true }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
        HintText("برای ویرایش یا جابه‌جایی سایت‌ها، روی سایت در نوار «سایت‌های ضروری» صفحهٔ اول انگشت را نگه دارید. لینک‌ها در مرورگر یا برنامهٔ مربوطه باز می‌شوند. سامانه‌های کاری (مس من، کسرا و …) در صفحهٔ اول هستند.")
    }
    StripDialogs(vm, Strip.ESSENTIAL, sites, ui, allowMove = false)
}

// ───────────────────────── تلویزیون ─────────────────────────

@Composable
fun TvSection(vm: MainVm, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val sites = remember(prefs.tvListRaw) { StripSites.resolve(Strip.TV, prefs.tvListRaw) }
    val ui = rememberStripUi()
    ToolScaffold("تلویزیون", "پخش زنده و تماشای آنلاین", onHome) {
        if (sites.isEmpty()) HintText("فهرست خالی است؛ با دکمهٔ زیر سایت اضافه کنید.")
        sites.forEach { s -> LinkRow(s.title, TilesPackFile.hostOf(s.url), { openStripSite(ctx, s) }, { ui.managing = s.key }) }
        AppButton("افزودن سایت", { ui.adding = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
        AppButton("بازگشت به پیش‌فرض", { ui.resetting = true }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
        HintText("تلوبیون اگر روی گوشی نصب باشد با خود برنامه باز می‌شود. هر سایت را می‌توانید ویرایش، جابه‌جا یا حذف کنید؛ روی نوار صفحهٔ اول هم با نگه‌داشتن انگشت روی سایت همین کار ممکن است.")
    }
    StripDialogs(vm, Strip.TV, sites, ui, vertical = true)
}

/** نوار ترکیبیِ کم‌ارتفاع: دو دکمهٔ کوچک برای انتخاب «سایت‌های ضروری» یا «تلویزیون» و زیرش همان نوار انتخاب‌شده. */
@Composable
fun CombinedStrip(
    tvSelected: Boolean, onSelect: (Boolean) -> Unit,
    ess: List<StripSite>, tv: List<StripSite>, essUi: StripUi, tvUi: StripUi,
    onAllEss: () -> Unit, onAllTv: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to "سایت‌های ضروری", true to "تلویزیون").forEach { (isTv, label) ->
                val sel = tvSelected == isTv
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Tab, onClickLabel = label) { onSelect(isTv) }
                        .semantics { selected = sel }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }
        if (tvSelected) TvStrip(tv, tvUi, onAllTv) else EssentialSitesStrip(ess, essUi, onAllEss)
    }
}
