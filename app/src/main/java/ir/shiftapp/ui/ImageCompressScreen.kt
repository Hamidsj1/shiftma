package ir.shiftapp.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.CompressSettings
import ir.shiftapp.core.Fa
import ir.shiftapp.core.ImageCompress
import ir.shiftapp.core.ProQuota
import ir.shiftapp.system.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «کاهش حجم عکس»: چند عکس را انتخاب کن، کوچک و فشرده می‌شوند؛ بعد در گالری ذخیره یا ارسال کن. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageCompressSection(onHome: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var maxSide by rememberSaveable { mutableIntStateOf(2048) }
    var quality by rememberSaveable { mutableIntStateOf(75) }
    var targetText by rememberSaveable { mutableStateOf("") }
    var webp by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<ImageCompressor.Result>>(emptyList()) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // چند بار استفادهٔ رایگان، بعد خرید از کافه‌بازار (کارت وضعیت بالای صفحه)
    val billing = LocalBilling.current
    val pro by billing.pro.collectAsState()
    var used by remember { mutableIntStateOf(ProQuota.used(ctx, ProQuota.FEATURE_COMPRESS)) }
    val allowed = ProQuota.canUse(used, pro)
    LaunchedEffect(Unit) { billing.refresh() }      // خرید قبلی (نصب دوباره یا گوشی دیگر) بی‌صدا بازیابی شود

    val target = ImageCompress.parseTargetKb(targetText)
    val targetBad = targetText.isNotBlank() && target == null

    fun run(uris: List<Uri>) {
        if (busy || uris.isEmpty()) return
        if (!ProQuota.canUse(used, pro)) return
        if (!pro) used = ProQuota.addUse(ctx, ProQuota.FEATURE_COMPRESS)      // هر اجرا روی یک دسته عکس = یک بار
        busy = true; done = 0; total = uris.size; failed = 0; results = emptyList()
        val s = CompressSettings(maxSide, quality, target, webp)
        scope.launch {
            val out = ArrayList<ImageCompressor.Result>()
            val names = HashSet<String>()
            var bad = 0
            for (u in uris) {
                val r = withContext(Dispatchers.Default) { runCatching { ImageCompressor.compress(ctx, u, s) }.getOrNull() }
                if (r == null) bad++
                else {
                    val n = ImageCompress.uniqueName(r.name, names)
                    names.add(n)
                    out.add(if (n == r.name) r else r.withName(n))
                }
                done++
            }
            results = out; failed = bad; busy = false
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) { picked = uris; run(uris) }
    }

    ToolScaffold("کاهش حجم عکس", "کم‌کردن حجم برای ارسال و آپلود", onHome) {
        ProStatusCard("کاهش حجم عکس", used)
        SectionLabel("تنظیمات")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("حداکثر اندازهٔ عکس (پیکسل)", style = MaterialTheme.typography.labelMedium, color = Pal.muted)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ImageCompress.SIDES.forEach { p ->
                        AppChip(if (p == ImageCompress.ORIGINAL_CAP) "اصلی" else Fa.num(p), maxSide == p, { maxSide = p })
                    }
                }
                if (maxSide == ImageCompress.ORIGINAL_CAP) HintText("«اصلی»: عکس‌های بزرگ‌تر از ${Fa.num(ImageCompress.ORIGINAL_CAP)} پیکسل برای پر نشدن حافظه کوچک می‌شوند.")
                RowDivider(Modifier.padding(horizontal = 0.dp))
                Text(
                    if (target == null) "کیفیت: ${Fa.num(quality)}٪" else "کیفیت: خودکار (طبق حجم هدف)",
                    style = MaterialTheme.typography.labelMedium, color = Pal.muted
                )
                Slider(
                    value = quality.toFloat(), onValueChange = { quality = it.toInt() },
                    valueRange = ImageCompress.MIN_QUALITY.toFloat()..ImageCompress.MAX_QUALITY.toFloat(), steps = 12,
                    enabled = target == null && !busy
                )
                AppField(
                    targetText, { targetText = it.filter { c -> c.isDigit() }.take(5) }, "حجم هدف (اختیاری)",
                    placeholder = "مثلاً ۲۰۰", keyboard = androidx.compose.ui.text.input.KeyboardType.Number, ltr = true,
                    isError = targetBad,
                    supporting = if (targetBad) "عددی بین ۱۰ تا ۵۰٬۰۰۰ کیلوبایت" else "کیلوبایت؛ برای سایت‌هایی که سقف حجم دارند. با پر شدنش کیفیت خودکار تنظیم می‌شود."
                )
                RowDivider(Modifier.padding(horizontal = 0.dp))
                Text("فرمت خروجی", style = MaterialTheme.typography.labelMedium, color = Pal.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip("JPEG", !webp, { webp = false })
                    AppChip("WebP", webp, { webp = true })
                }
                HintText(if (webp) "WebP کوچک‌تر است ولی بعضی سایت‌ها و برنامه‌ها آن را نمی‌پذیرند." else "JPEG را همه‌جا می‌پذیرند. عکس شفاف (PNG) روی زمینهٔ سفید می‌نشیند.")
            }
        }

        AppButton("انتخاب عکس‌ها", { pick.launch("image/*") }, Modifier.fillMaxWidth(), icon = R.drawable.ic_image, enabled = !busy && !targetBad && allowed)
        if (picked.isNotEmpty() && !busy && results.isNotEmpty()) {
            AppButton("اعمال دوباره با تنظیمات جدید", { run(picked) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_restore, style = BtnStyle.Outline, enabled = !targetBad && allowed)
        }

        if (busy) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("در حال کاهش حجم… ${Fa.num(done)} از ${Fa.num(total)}", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (!busy && results.isNotEmpty()) {
            val before = results.sumOf { it.before }
            val after = results.sumOf { it.after }
            val pct = ImageCompress.savedPercent(before, after)
            SectionLabel("نتیجه")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "از ${ImageCompress.formatSize(before)} به ${ImageCompress.formatSize(after)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        (if (pct > 0) "${Fa.num(pct)}٪ کمتر · " else "") + "${Fa.num(results.size)} عکس",
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                results.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val tb = r.thumb
                        if (tb != null) Image(
                            tb.asImageBitmap(), null,
                            Modifier.size(52.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop
                        ) else IconBadge(R.drawable.ic_image, size = 52.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.name, style = LtrStyle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val p = ImageCompress.savedPercent(r.before, r.after)
                            Text(
                                "از ${ImageCompress.formatSize(r.before)} به ${ImageCompress.formatSize(r.after)}" + if (p > 0) " · ${Fa.num(p)}٪ کمتر" else "",
                                style = MaterialTheme.typography.bodySmall, color = Pal.muted
                            )
                            r.note?.let { HintText(it) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("ذخیره در گالری", {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { results.count { ImageCompressor.saveToGallery(ctx, it) } }
                        Toast.makeText(
                            ctx,
                            when {
                                android.os.Build.VERSION.SDK_INT < 29 -> "در این نسخهٔ اندروید از «اشتراک‌گذاری» استفاده کنید"
                                ok == results.size -> "${Fa.num(ok)} عکس در گالری (پوشهٔ ShiftMa) ذخیره شد"
                                else -> "ذخیره‌سازی کامل نشد"
                            }, Toast.LENGTH_LONG
                        ).show()
                    }
                }, Modifier.weight(1f), icon = R.drawable.ic_download, style = BtnStyle.Tonal)
                AppButton("اشتراک‌گذاری", {
                    if (!ImageCompressor.share(ctx, results)) Toast.makeText(ctx, "ارسال ممکن نشد", Toast.LENGTH_LONG).show()
                }, Modifier.weight(1f), icon = R.drawable.ic_share)
            }
        }
        if (!busy && failed > 0) HintText("${Fa.num(failed)} فایل خوانده نشد (عکس نبود یا خراب بود).", color = MaterialTheme.colorScheme.error)
        HintText("اطلاعات پنهان عکس (مثل مکان GPS) در خروجی نمی‌ماند. فایل اصلی دست‌نخورده می‌ماند.")
    }
}
