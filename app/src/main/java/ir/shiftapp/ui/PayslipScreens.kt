package ir.shiftapp.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PayslipDetect
import ir.shiftapp.core.PayslipNames
import ir.shiftapp.core.SlipKind
import ir.shiftapp.system.PayslipFiles
import ir.shiftapp.system.PayslipFiles.Slip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_BYTES = 30 * 1024 * 1024
private const val RENDER_WIDTH = 1400

private fun monthLabel(year: Int, month: Int) = "${Fa.MONTHS[month - 1]} ${Fa.num(year)}"
private fun slipLabel(s: Slip) = "${monthLabel(s.year, s.month)} · ${s.kind.label}"

// ───────────────────────── بخش «فیش‌های من» ─────────────────────────

@Composable
fun PayslipsSection(onHome: () -> Unit) {
    val ctx = LocalContext.current
    var slips by remember { mutableStateOf(PayslipFiles.list(ctx)) }
    fun refresh() { slips = PayslipFiles.list(ctx) }
    var view by remember { mutableStateOf<Slip?>(null) }
    var compare by remember { mutableStateOf<Pair<Slip, Slip>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<String>>(emptyList()) }
    var delete by remember { mutableStateOf<Slip?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null }
        when {
            bytes == null -> Toast.makeText(ctx, "فایل خوانده نشد", Toast.LENGTH_SHORT).show()
            bytes.size > MAX_IMPORT_BYTES || !PayslipNames.isPdf(bytes) -> Toast.makeText(ctx, "این فایل PDF معتبر نیست", Toast.LENGTH_SHORT).show()
            else -> importBytes = bytes
        }
    }

    val c = compare
    val v = view
    when {
        c != null -> SlipCompare(c.first, c.second) { compare = null }
        v != null -> SlipViewer(v) { view = null }
        else -> ToolScaffold("فیش‌های من", "مشاهده و مقایسهٔ فیش‌های حقوقی", onHome) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("افزودن PDF", { importer.launch(arrayOf("application/pdf")) }, Modifier.weight(1f), icon = R.drawable.ic_add, style = BtnStyle.Tonal)
                AppButton(
                    if (picking) "انصراف" else "مقایسهٔ دو فیش",
                    { picking = !picking; picked = emptyList() }, Modifier.weight(1f),
                    style = if (picking) BtnStyle.Outline else BtnStyle.Tonal, enabled = slips.size >= 2
                )
            }
            if (picking) {
                HintText("دو فیش را انتخاب کن (${Fa.num(picked.size)} از ۲).")
                AppButton("نمایش مقایسه", {
                    val two = slips.filter { it.file.name in picked }.sortedWith(compareBy<Slip> { it.year }.thenBy { it.month })
                    if (two.size == 2) { compare = two[0] to two[1]; picking = false; picked = emptyList() }
                }, Modifier.fillMaxWidth(), enabled = picked.size == 2)
            }
            if (slips.isEmpty()) {
                EmptyState(
                    R.drawable.ic_home_slip, "هنوز فیشی ذخیره نشده",
                    text = "در «مس من» فیش را باز کن و «دانلود فایل PDF» را بزن؛ بعد می‌توانی آن را به این بخش اضافه کنی. یا یک PDF از گوشی اضافه کن."
                )
            }
            // به تفکیک ماه؛ در هر ماه، نوع فیش (حکمی / رفاهی) جدا نشان داده می‌شود
            slips.groupBy { it.year to it.month }.forEach { (ym, monthSlips) ->
                Text(monthLabel(ym.first, ym.second), style = MaterialTheme.typography.titleSmall, color = Pal.muted, modifier = Modifier.padding(top = 6.dp))
                monthSlips.forEach { s ->
                    val on = s.file.name in picked
                    AppCard(Modifier.fillMaxWidth(), onClick = {
                        if (picking) picked = if (on) picked - s.file.name else if (picked.size < 2) picked + s.file.name else picked
                        else view = s
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(R.drawable.ic_home_slip, size = 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text("فیش ${s.kind.label}", style = MaterialTheme.typography.titleSmall)
                                Text("${Fa.num((s.file.length() / 1024).toInt())} کیلوبایت", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                            }
                            if (picking) { if (on) Ico(R.drawable.ic_check_circle, size = 24.dp, tint = Pal.green, desc = "انتخاب شد") }
                            else {
                                SmallIconButton(R.drawable.ic_share, "اشتراک‌گذاری", { shareSlip(ctx, s) })
                                SmallIconButton(R.drawable.ic_delete, "حذف", { delete = s }, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            HintText("فیش‌ها فقط روی همین گوشی و در پوشهٔ خصوصی برنامه می‌مانند و در فایل پشتیبان نیستند.")
        }
    }

    delete?.let { s ->
        ConfirmDialog("فیش ${slipLabel(s)} از گوشی حذف شود؟", "حذف", { PayslipFiles.delete(s); refresh(); delete = null }, { delete = null }, danger = true)
    }
    importBytes?.let { b ->
        SavePayslipDialog(b, onClose = { importBytes = null }) { refresh(); importBytes = null }
    }
}

private fun shareSlip(ctx: android.content.Context, s: Slip) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, PayslipFiles.shareUri(ctx, s))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "ارسال فیش").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { Toast.makeText(ctx, "ارسال نشد", Toast.LENGTH_SHORT).show() }
}

// ───────────────────────── ذخیره: انتخاب ماه ─────────────────────────

/**
 * ذخیره در «فیش‌های من»: نوع (حکمی/رفاهی) و ماه از متن صفحهٔ سامانه ([hints]) و متن داخل PDF حدس زده و از پیش انتخاب می‌شود؛
 * کاربر می‌تواند تغییر بدهد. هم از مرورگر و هم از «افزودن PDF» استفاده می‌شود.
 */
@Composable
internal fun SavePayslipDialog(bytes: ByteArray, hints: List<String> = emptyList(), onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val today = remember { JalaliDate.today() }
    val prev = remember { PayslipNames.previous(today.year, today.month) }    // فیش معمولاً مال ماه گذشته است
    var year by remember { mutableStateOf(prev.first.toString()) }
    var month by remember { mutableStateOf(prev.second) }
    var kind by remember { mutableStateOf(SlipKind.HOKMI) }
    var touched by remember { mutableStateOf(false) }
    val det by produceState<PayslipDetect.Result?>(null, bytes) {
        value = withContext(Dispatchers.Default) { PayslipDetect.detect(hints + PayslipDetect.pdfText(bytes)) }
    }
    LaunchedEffect(det) {
        val d = det ?: return@LaunchedEffect
        if (touched) return@LaunchedEffect
        d.kind?.let { kind = it }
        d.month?.let { month = it }
        d.year?.let { year = it.toString() }
    }
    val y = year.trim().toIntOrNull() ?: 0
    val valid = y in 1300..1600
    val replacing = valid && PayslipFiles.exists(ctx, y, month, kind)
    AppDialog(
        "ذخیره در «فیش‌های من»", onClose, confirmText = "ذخیره", dismissText = "فعلاً نه", confirmEnabled = valid,
        onConfirm = {
            try {
                PayslipFiles.save(ctx, bytes, y, month, kind)
                Toast.makeText(ctx, "فیش ${kind.label} ${monthLabel(y, month)} ذخیره شد", Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: Exception) { Toast.makeText(ctx, "ذخیره نشد: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    ) {
        val d = det
        when {
            d == null -> HintText("در حال تشخیص نوع و ماه فیش…")
            d.isEmpty -> HintText("نوع و ماه از فایل تشخیص داده نشد؛ خودت انتخاب کن.", color = Pal.amber)
            else -> HintText("تشخیص خودکار: " + listOfNotNull(d.kind?.let { "فیش ${it.label}" }, d.month?.let { Fa.MONTHS[it - 1] }).joinToString(" · ") +
                (if (d.kind == null || d.month == null) " (بقیه را خودت انتخاب کن)" else "") + ". اگر درست نیست، تغییر بده.")
        }
        Text("نوع فیش", style = MaterialTheme.typography.bodyMedium)
        ChipRow { SlipKind.values().forEach { k -> AppChip(k.label, kind == k, { kind = k; touched = true }) } }
        Text("این فیش مال کدام ماه است؟", style = MaterialTheme.typography.bodyMedium)
        ChipRow { Fa.MONTHS.forEachIndexed { i, n -> AppChip(n, month == i + 1, { month = i + 1; touched = true }) } }
        AppField(year, { year = it.take(4); touched = true }, "سال (شمسی)", keyboard = KeyboardType.Number)
        if (replacing) HintText("فیش ${kind.label} این ماه قبلاً ذخیره شده؛ با ذخیره، جایگزین می‌شود.", color = Pal.amber)
    }
}

// ───────────────────────── نمایش و زوم ─────────────────────────

private class ZoomState {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    fun reset() { scale = 1f; offset = Offset.Zero }
}

@Composable
private fun rememberSlipBitmap(slip: Slip, page: Int): State<Bitmap?> =
    produceState<Bitmap?>(null, slip.file.path, page) {
        value = withContext(Dispatchers.IO) { PayslipFiles.render(slip.file, page, RENDER_WIDTH) }
    }

/** صفحهٔ PDF با زوم دو انگشتی و جابه‌جایی؛ دو بار لمس: زوم/بازنشانی. وضعیت زوم می‌تواند بین چند قاب مشترک باشد. */
@Composable
private fun ZoomPane(bitmap: Bitmap?, zoom: ZoomState, modifier: Modifier, label: String? = null) {
    Box(
        modifier.clip(MaterialTheme.shapes.small).background(Color.White)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ ->
                    val ns = (zoom.scale * z).coerceIn(1f, 6f)
                    val maxX = size.width * (ns - 1f) / 2f
                    val maxY = size.height * (ns - 1f) / 2f
                    zoom.scale = ns
                    zoom.offset = Offset((zoom.offset.x + pan.x).coerceIn(-maxX, maxX), (zoom.offset.y + pan.y).coerceIn(-maxY, maxY))
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (zoom.scale > 1f) zoom.reset() else zoom.scale = 2.5f }) }
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.offset.x; translationY = zoom.offset.y
                }
            )
        } else CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
        if (label != null) {
            Text(
                label, Modifier.align(Alignment.TopStart).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium, color = Color.White
            )
        }
    }
}

@Composable
private fun SlipViewer(slip: Slip, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    var page by remember { mutableStateOf(0) }
    val pages = remember(slip.file.path) { PayslipFiles.pageCount(slip.file) }
    val zoom = remember { ZoomState() }
    val bmp by rememberSlipBitmap(slip, page)
    LaunchedEffect(page) { zoom.reset() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(slipLabel(slip), R.drawable.ic_arrow_back, "بازگشت", onBack, subtitle = "دو انگشت: زوم · دو بار لمس: زوم/بازنشانی") {
                SmallIconButton(R.drawable.ic_share, "اشتراک‌گذاری", { shareSlip(ctx, slip) })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pages > 1) ChipRow { (0 until pages).forEach { i -> AppChip("صفحهٔ ${Fa.num(i + 1)}", page == i, { page = i }) } }
            if (pages == 0) HintText("این فایل قابل نمایش نیست.", color = MaterialTheme.colorScheme.error)
            ZoomPane(bmp, zoom, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

// ───────────────────────── مقایسه ─────────────────────────

@Composable
private fun SlipCompare(a: Slip, b: Slip, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var first by remember { mutableStateOf(a) }
    var second by remember { mutableStateOf(b) }
    var stacked by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }      // زوم و جابه‌جایی بین دو فیش مشترک است
    val bmpA by rememberSlipBitmap(first, 0)
    val bmpB by rememberSlipBitmap(second, 0)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("مقایسهٔ فیش‌ها", R.drawable.ic_arrow_back, "بازگشت", onBack, subtitle = "${slipLabel(first)} و ${slipLabel(second)}") }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Segmented(listOf("کنار هم", "بالا و پایین"), if (stacked) 1 else 0) { stacked = it == 1; zoom.reset() } }
                AppButton("جابه‌جایی", { val t = first; first = second; second = t }, style = BtnStyle.Outline)
            }
            if (stacked) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoomPane(bmpA, zoom, Modifier.weight(1f).fillMaxWidth(), slipLabel(first))
                    ZoomPane(bmpB, zoom, Modifier.weight(1f).fillMaxWidth(), slipLabel(second))
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoomPane(bmpA, zoom, Modifier.weight(1f).fillMaxHeight(), slipLabel(first))
                    ZoomPane(bmpB, zoom, Modifier.weight(1f).fillMaxHeight(), slipLabel(second))
                }
            }
            HintText("زوم و جابه‌جایی هر دو فیش را هم‌زمان حرکت می‌دهد تا یک ردیف را کنار هم ببینی. دو بار لمس: زوم/بازنشانی.")
        }
    }
}
