package ir.shiftapp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ir.shiftapp.system.AzanService
import ir.shiftapp.system.AzanPlayer
import ir.shiftapp.system.AzanFiles
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** ساعتِ زنده؛ هر ۳۰ ثانیه تازه می‌شود (برای شمارش معکوس اذان بعدی). */
@Composable
internal fun rememberNowMs(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000L); now = System.currentTimeMillis() } }
    return now
}

/** «اذان ظهر ۱۱:۵۱ · ۲ ساعت و ۱۰ دقیقه دیگر»؛ وقتی امکانات مذهبی خاموش است null. */
@Composable
internal fun rememberNextPrayerLine(prefs: Prefs): String? {
    val now = rememberNowMs()
    return remember(prefs.prayerOn, prefs.prayerCity, prefs.prayerLat, prefs.prayerLon, prefs.prayerMethodId, now / 60_000L) {
        if (!prefs.prayerOn) null else {
            val pl = prefs.prayerPlace
            PrayerCalc.next(now, ZoneId.systemDefault(), pl.lat, pl.lon, prefs.prayerMethod)?.let {
                "${it.prayer.title} ${Fa.digits(ShiftTime.fmt(it.minuteOfDay))} · ${PrayerRules.remaining(it.atMs - now)} دیگر"
            }
        }
    }
}

private fun clock(min: Int) = Fa.digits(ShiftTime.fmt(min % 1440))

@Composable
fun PrayerSection(vm: MainVm, onHome: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val now = rememberNowMs()
    val today = JalaliDate.today()
    ToolScaffold("اوقات شرعی و قبله", "${prefs.prayerPlace.name} · ${WEEK_FULL[today.dayOfWeek]} ${today.day.fa()} ${MONTHS[today.month - 1]}", onHome) {
        if (!prefs.prayerOn) {
            EmptyState(R.drawable.ic_qibla, "امکانات مذهبی خاموش است", text = "اوقات شرعی، قبله و اذان اختیاری‌اند؛ هر وقت خواستید روشن کنید.", action = {
                AppButton("فعال‌سازی", { vm.setPref(PrefKeys.PRAYER_ON, "true") })
            })
            return@ToolScaffold
        }
        val place = prefs.prayerPlace
        val method = prefs.prayerMethod
        val date = remember(now / 3_600_000L, zone) { LocalDate.now(zone) }
        val times = remember(date, place, method) { PrayerCalc.times(date, place.lat, place.lon, zone, method) }
        val next = remember(now / 60_000L, place, method) { PrayerCalc.next(now, zone, place.lat, place.lon, method) }

        NextCard(next, now)
        TimesCard(vm, prefs, times, next?.prayer)
        PlaceCard(vm, prefs)
        QiblaCard(place)
        AlertCard(vm, prefs)

        var confirmOff by remember { mutableStateOf(false) }
        AppButton("خاموش کردن امکانات مذهبی", { confirmOff = true }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
        if (confirmOff) ConfirmDialog(
            "بخش اوقات شرعی از صفحهٔ اول برداشته می‌شود و همهٔ هشدارهای اذان متوقف می‌شود. تنظیمات شما نگه داشته می‌شود.",
            "خاموش کن", { vm.setPref(PrefKeys.PRAYER_ON, "false"); confirmOff = false; onHome() }, { confirmOff = false }
        )
    }
}

@Composable
private fun NextCard(next: NextPrayer?, now: Long) {
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Pal.heroBrush).padding(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("وقت بعدی", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
            if (next == null) Text("—", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            else {
                Text("${next.prayer.title}  ${clock(next.minuteOfDay)}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("${PrayerRules.remaining(next.atMs - now)} دیگر", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun TimesCard(vm: MainVm, prefs: Prefs, times: DayTimes, next: Prayer?) {
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val p by vm.selected.collectAsState()
    val all by vm.entriesAllPatterns.collectAsState()
    val todayJ = remember { JalaliDate.today() }
    fun shiftOn(d: JalaliDate): ShiftTime? = p?.let { pat ->
        runCatching {
            val r = DayResolver.resolve(pat.base(), ShiftPattern.parse(pat.pattern), d, all[pat.id]?.get(d.key).orEmpty())
            if (r.onLeave) null else prefs.shiftTimes[r.effectiveCode]
        }.getOrNull()
    }
    val todayShift = remember(p, all, prefs.shiftTimes) { shiftOn(todayJ) }
    val yesterdayShift = remember(p, all, prefs.shiftTimes) { shiftOn(todayJ.plusDays(-1)) }
    val method = prefs.prayerMethod

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("اوقات امروز")
        AppCard(Modifier.fillMaxWidth()) {
            method.shown.forEachIndexed { i, pr ->
                if (i > 0) RowDivider()
                val min = times.minutes[pr]
                val inShift = min != null && PrayerRules.inShift(min % 1440, todayShift, yesterdayShift)
                val alertOn = pr.id in prefs.prayerAlerts
                Row(
                    Modifier.fillMaxWidth().background(if (pr == next) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pr.title, style = MaterialTheme.typography.titleSmall)
                        if (inShift) Text("در ساعت کار شما", style = MaterialTheme.typography.labelSmall, color = Pal.amber)
                    }
                    Text(if (min != null) clock(min) else "—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    SmallIconButton(
                        if (alertOn) R.drawable.ic_alarm else R.drawable.ic_alarm_off, if (alertOn) "حذف هشدار ${pr.title}" else "هشدار برای ${pr.title}",
                        {
                            val n = if (alertOn) prefs.prayerAlerts - pr.id else prefs.prayerAlerts + pr.id
                            vm.setPref(PrefKeys.PRAYER_ALERTS, n.sorted().joinToString(","))
                            if (!alertOn && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        tint = if (alertOn) MaterialTheme.colorScheme.primary else Pal.muted
                    )
                }
            }
        }
        HintText("زنگ کنار هر وقت = هشدار روشن/خاموش. محاسبه کاملاً آفلاین است و ممکن است یکی‌دو دقیقه با تقویم رسمی فرق کند.")
    }
}

@Composable
private fun PlaceCard(vm: MainVm, prefs: Prefs) {
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf(false) }

    fun useLastLocation() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = if (lm == null) null else runCatching {
            lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        }.getOrNull()
        if (loc == null) {
            Toast.makeText(ctx, "موقعیت پیدا نشد؛ شهر را از فهرست انتخاب کنید", Toast.LENGTH_LONG).show()
            return
        }
        vm.setPref(PrefKeys.PRAYER_LAT, loc.latitude.toString())
        vm.setPref(PrefKeys.PRAYER_LON, loc.longitude.toString())
        vm.setPref(PrefKeys.PRAYER_LOCNAME, "موقعیت من")
        vm.setPref(PrefKeys.PRAYER_CITY, PrayerCities.GPS)
        pick = false
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useLastLocation() else Toast.makeText(ctx, "بدون مجوز موقعیت، شهر را از فهرست انتخاب کنید", Toast.LENGTH_LONG).show()
    }

    SettingsGroup("مکان و روش محاسبه", icon = R.drawable.ic_event) {
        SettingRow("شهر / موقعیت", subtitle = prefs.prayerPlace.name, onClick = { pick = true }, modifier = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer))
        Segmented(PrayerMethod.ALL.map { it.title }, PrayerMethod.ALL.indexOfFirst { it.id == prefs.prayerMethodId }.coerceAtLeast(0)) {
            vm.setPref(PrefKeys.PRAYER_METHOD, PrayerMethod.ALL[it].id)
        }
        HintText(
            if (prefs.prayerMethod.id == "shia") "شیعه: روش مؤسسهٔ ژئوفیزیک دانشگاه تهران؛ نیمه‌شب شرعی از غروب تا اذان صبح روز بعد."
            else "اهل سنت: روش رابطهٔ جهان اسلام؛ عصر با سایهٔ هم‌اندازه و مغرب هم‌زمان با غروب."
        )
    }
    if (pick) {
        var q by remember { mutableStateOf("") }
        val list = remember(q) { PrayerCities.ALL.filter { q.isBlank() || it.name.contains(q.trim()) } }
        AppDialog("انتخاب شهر", { pick = false }, dismissText = "بستن") {
            AppButton("استفاده از موقعیت من", {
                val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) useLastLocation() else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }, Modifier.fillMaxWidth(), icon = R.drawable.ic_qibla, style = BtnStyle.Tonal)
            AppField(q, { q = it }, "جست‌وجوی شهر")
            list.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.Button) {
                        vm.setPref(PrefKeys.PRAYER_CITY, c.id); pick = false
                    }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (prefs.prayerCity == c.id) Ico(R.drawable.ic_check, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (list.isEmpty()) HintText("شهری پیدا نشد؛ از «موقعیت من» استفاده کنید.")
        }
    }
}

// ───────────────────────── قبله ─────────────────────────

/** جهت گوشی نسبت به شمال حقیقی (درجه)؛ null تا رسیدن اولین داده یا اگر حسگر نیست. */
@Composable
private fun rememberAzimuth(lat: Double, lon: Double): State<Float?> {
    val ctx = LocalContext.current
    val az = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(lat, lon) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val decl = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        var smooth: Float? = null
        val rot = FloatArray(9); val ori = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = ((Math.toDegrees(ori[0].toDouble()).toFloat() + decl) + 360f) % 360f
                val prev = smooth
                smooth = if (prev == null) deg else {
                    var d = deg - prev
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    (prev + 0.15f * d + 360f) % 360f
                }
                az.value = smooth
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sm != null && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    return az
}

@Composable
private fun QiblaCard(place: PrayerPlace) {
    val ctx = LocalContext.current
    val bearing = remember(place) { Qibla.bearing(place.lat, place.lon) }
    val km = remember(place) { Qibla.distanceKm(place.lat, place.lon) }
    val hasSensor = remember { (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null }
    val azimuth by rememberAzimuth(place.lat, place.lon)
    val heading = azimuth
    val aligned = heading != null && abs(Qibla.diff(bearing, heading.toDouble())) < 4.0

    SettingsGroup("جهت قبله", icon = R.drawable.ic_qibla) {
        Text(
            "${Fa.num(Math.round(bearing).toInt())}° از شمال (${Qibla.cardinal(bearing)}) · ${Fa.num(Math.round(km).toInt())} کیلومتر تا کعبه",
            style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        if (hasSensor) {
            val ring = Pal.muted
            val north = Pal.coral
            val arrow = if (aligned) Pal.green else MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    val c = center
                    val r = size.minDimension / 2f - 6.dp.toPx()
                    drawCircle(ring.copy(alpha = 0.5f), radius = r, style = Stroke(2.dp.toPx()))
                    val h = heading ?: 0f
                    rotate(-h, c) {
                        for (i in 0 until 12) {
                            val len = if (i % 3 == 0) 14.dp.toPx() else 7.dp.toPx()
                            rotate(i * 30f, c) { drawLine(ring, Offset(c.x, c.y - r), Offset(c.x, c.y - r + len), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round) }
                        }
                        // مثلث قرمز = شمال
                        val tri = Path().apply {
                            moveTo(c.x, c.y - r + 8.dp.toPx()); lineTo(c.x - 9.dp.toPx(), c.y - r + 30.dp.toPx()); lineTo(c.x + 9.dp.toPx(), c.y - r + 30.dp.toPx()); close()
                        }
                        drawPath(tri, north)
                    }
                    // پیکان قبله (نسبت به شمال صفحه: زاویهٔ قبله − جهت گوشی)
                    rotate(bearing.toFloat() - h, c) {
                        drawLine(arrow, Offset(c.x, c.y + 18.dp.toPx()), Offset(c.x, c.y - r + 44.dp.toPx()), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                        val head = Path().apply {
                            moveTo(c.x, c.y - r + 36.dp.toPx()); lineTo(c.x - 14.dp.toPx(), c.y - r + 62.dp.toPx()); lineTo(c.x + 14.dp.toPx(), c.y - r + 62.dp.toPx()); close()
                        }
                        drawPath(head, arrow)
                    }
                    drawCircle(arrow, radius = 7.dp.toPx(), center = c)
                }
            }
            Text(
                when {
                    heading == null -> "در حال خواندن حسگر..."
                    aligned -> "رو به قبله هستید"
                    else -> "پیکان را بالا بیاورید تا روی قبله بیفتد"
                },
                style = MaterialTheme.typography.titleSmall, color = if (aligned) Pal.green else Pal.muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            HintText("گوشی را صاف و دور از آهنربا و فلز نگه دارید. اگر پیکان می‌لرزد یا غلط نشان می‌دهد، گوشی را چند بار به شکل عدد ۸ بچرخانید. مثلث قرمز = شمال.")
        } else {
            HintText("این گوشی حسگر قطب‌نما ندارد. از روی زاویهٔ بالا و یک قطب‌نمای معمولی جهت قبله را پیدا کنید.")
        }
    }
}

// ───────────────────────── هشدار اذان ─────────────────────────

private fun soundTitle(ctx: Context, token: String): String = when (AzanSounds.kind(token)) {
    AzanSounds.Kind.DEFAULT -> "صدای آلارم گوشی (نه اذان)"
    AzanSounds.Kind.BUILTIN -> AzanSounds.BUILTIN_TITLE + " (همراه برنامه)"
    AzanSounds.Kind.FILE -> AzanSounds.fileName(token)?.let { AzanSounds.display(it) } ?: "فایل نامعتبر"
    AzanSounds.Kind.SYSTEM -> runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(token)).getTitle(ctx) }.getOrNull()?.takeIf { it.isNotBlank() } ?: "صدای انتخاب‌شده"
}

@Composable
private fun AlertCard(vm: MainVm, prefs: Prefs) {
    val ctx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var soundDialog by remember { mutableStateOf(false) }
    SettingsGroup("هشدار اذان", icon = R.drawable.ic_notifications) {
        if (prefs.prayerAlerts.isEmpty()) HintText("هنوز برای هیچ وقتی هشدار نگذاشته‌اید؛ با زنگ کنار هر وقت (در فهرست بالا) فعالش کنید.")
        else Text("هشدار روشن: " + prefs.prayerMethod.shown.filter { it.id in prefs.prayerAlerts }.joinToString("، ") { it.title }, style = MaterialTheme.typography.bodyMedium)

        Text("چند دقیقه قبل از وقت؟", style = MaterialTheme.typography.titleSmall)
        ChipRow {
            listOf(0, 5, 10, 15, 30, 60).forEach { m ->
                AppChip(if (m == 0) "سر وقت" else "${Fa.num(m)} دقیقه", prefs.prayerLeadMin == m, { vm.setPref(PrefKeys.PRAYER_LEAD, m.toString()) })
            }
        }
        Text("نوع هشدار", style = MaterialTheme.typography.titleSmall)
        Segmented(listOf("اعلان", "آلارم"), if (prefs.prayerAlarm) 1 else 0) { vm.setPref(PrefKeys.PRAYER_ALARM, (it == 1).toString()) }
        HintText(if (prefs.prayerAlarm) "آلارم: با صدای آلارم و لرزش، دقیق و حتی در حالت بی‌صدا." else "اعلان: اعلان معمولی؛ در حالت بی‌صدا بی‌صدا می‌آید.")
        AlarmVolumeControl()

        SettingRow(
            "صدای هشدار", subtitle = soundTitle(ctx, prefs.prayerSound),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
            onClick = { soundDialog = true }
        )
        HintText("اذان پیش‌فرض همراه برنامه است و بدون اینترنت پخش می‌شود. اذان‌های دیگر را می‌توانید از اینترنت دانلود یا از گوشی اضافه کنید.")

        SwitchRow(
            "ساعت سکوت", prefs.prayerQuietOn, { vm.setPref(PrefKeys.PRAYER_QUIET_ON, it.toString()) },
            Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
            subtitle = "هشدارهایی که در این بازه می‌افتند زده نمی‌شوند؛ مناسب خواب بعد از شبکاری"
        )
        if (prefs.prayerQuietOn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockField("از ساعت", prefs.prayerQuietStart, { vm.setPref(PrefKeys.PRAYER_QUIET, "$it-${prefs.prayerQuietEnd}") }, Modifier.weight(1f))
                ClockField("تا ساعت", prefs.prayerQuietEnd, { vm.setPref(PrefKeys.PRAYER_QUIET, "${prefs.prayerQuietStart}-$it") }, Modifier.weight(1f))
            }
        }
        AppButton("ارسال هشدار آزمایشی", {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else AzanService.trigger(ctx, "اذان ظهر", "این یک هشدار آزمایشی است", prefs.prayerAlarm, prefs.prayerSound)
        }, Modifier.fillMaxWidth(), icon = R.drawable.ic_notifications, style = BtnStyle.Tonal)
    }
    if (soundDialog) AzanSoundDialog(vm, prefs) { soundDialog = false }
}


// ───────────────────────── انتخاب صدای اذان ─────────────────────────

@Composable
private fun AzanSoundDialog(vm: MainVm, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(AzanFiles.list(ctx)) }
    var playing by remember { mutableStateOf<String?>(null) }   // توکن یا "url"
    var busy by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { AzanPlayer.stop() } }

    fun select(token: String) { vm.setPref(PrefKeys.PRAYER_SOUND, token) }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    fun preview(key: String, token: String) {
        if (playing == key) { AzanPlayer.stop(); playing = null; return }
        val ok = AzanPlayer.playToken(ctx, token, prefs.prayerAlarm) { playing = null }
        if (ok) playing = key else toast("پخش نشد؛ فایل پیدا نشد یا خراب است")
    }
    fun failure(e: Throwable?) = toast(if (e is IllegalStateException || e is IllegalArgumentException) e.message ?: "انجام نشد" else "انجام نشد؛ اینترنت یا پیوند را بررسی کنید")

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val r = AzanFiles.import(ctx, uri)
            busy = false
            r.onSuccess { files = AzanFiles.list(ctx); select(AzanSounds.fileToken(it)) }.onFailure { failure(it) }
        }
    }

    @Composable
    fun SoundRow(title: String, sub: String?, token: String, key: String, deletable: String? = null) {
        val selected = prefs.prayerSound == token
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.RadioButton) { select(token) }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Ico(R.drawable.ic_check_circle, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
            SmallIconButton(if (playing == key) R.drawable.ic_close else R.drawable.ic_volume, if (playing == key) "توقف پخش" else "پخش نمونه", { preview(key, token) })
            if (deletable != null) SmallIconButton(R.drawable.ic_delete, "حذف فایل", {
                if (playing == key) { AzanPlayer.stop(); playing = null }
                AzanFiles.delete(ctx, deletable)
                files = AzanFiles.list(ctx)
                if (selected) select(AzanSounds.BUILTIN)
            })
        }
    }

    AppDialog("صدای هشدار اذان", onClose, dismissText = "بستن") {
        SoundRow("صدای آلارم گوشی (نه اذان)", null, "", "default")
        SoundRow(AzanSounds.BUILTIN_TITLE, "همراه برنامه · بدون اینترنت", AzanSounds.BUILTIN, "builtin")
        files.forEach { f -> SoundRow(AzanSounds.display(f.name), "پوشهٔ برنامه · ${Fa.num((f.length() / 1024).toInt())} کیلوبایت", AzanSounds.fileToken(f.name), f.name, deletable = f.name) }

        AppButton("افزودن فایل از گوشی", { importer.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_add, style = BtnStyle.Tonal, enabled = !busy)

        AppButton("جست‌وجو و دانلود از اینترنت", { AzanPlayer.stop(); playing = null; searchDialog = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_search, style = BtnStyle.Tonal)
        HintText("فقط وقتی جست‌وجو یا دانلود را بزنید به اینترنت وصل می‌شود. فایل دانلودشده در پوشهٔ خصوصی برنامه می‌ماند و بعدش بدون اینترنت پخش می‌شود.")
    }
    if (searchDialog) AzanSearchDialog(
        prefs, { name -> files = AzanFiles.list(ctx); select(AzanSounds.fileToken(name)) }, { searchDialog = false }
    )
}
