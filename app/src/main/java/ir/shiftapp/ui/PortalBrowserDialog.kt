package ir.shiftapp.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.shiftapp.R
import ir.shiftapp.core.PdfFinder
import ir.shiftapp.core.PortalAutofill
import ir.shiftapp.core.RequestLog
import ir.shiftapp.system.PayslipPdf
import ir.shiftapp.system.PortalVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/** آدرس src همهٔ iframe و embed و object صفحه، هر کدام در یک خط. */
internal const val DOM_URLS_JS =
    "(function(){var r=[];document.querySelectorAll('iframe,embed,object')" +
        ".forEach(function(e){r.push(e.src||e.data||'')});return r.join('\\n');})()"

/** متن دیدنی صفحه و فریم‌های هم‌مبدأ و عنوان آن؛ برای تشخیص نوع (حکمی/رفاهی) و ماه فیش. */
internal const val PAGE_TEXT_JS =
    "(function(){var t=[];" +
        "document.querySelectorAll('select option:checked,.active,.selected').forEach(function(e){t.push(e.innerText||'')});" +
        "t.push(document.title||'',(document.body&&document.body.innerText)||'');" +
        "try{document.querySelectorAll('iframe').forEach(function(f){try{t.push(f.contentDocument.body.innerText||'')}catch(e){}})}catch(e){}" +
        "return t.join('\\n').slice(0,60000);})()"

/**
 * مرورگر داخلی برای سامانه‌های کاری (مثل «مس من») با دکمهٔ «دانلود فایل PDF».
 * سایت دکمهٔ دانلود خودش را غیرفعال کرده؛ این‌جا هر درخواست GET صفحه ثبت می‌شود و با دکمه، همان نشست ورود کاربر
 * برای پیدا کردن و ذخیرهٔ فایل اصلی PDF فیش استفاده می‌شود. نام کاربری و رمز هیچ‌جا ذخیره نمی‌شود.
 */
private fun activityOf(c: Context): Activity? {
    var x: Context? = c
    while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
    return null
}

/**
 * سهم هم‌رنگ‌ترین رنگ در تصویر کوچکِ «آنچه واقعاً روی صفحه دیده می‌شود» در ناحیهٔ [view] (PixelCopy)؛ null اگر گرفتن تصویر ممکن نشد.
 * نزدیک ۱ یعنی ناحیه یک‌دست (مثلاً سفید) است؛ یعنی صفحه بارگذاری شده ولی چیزی کشیده نشده.
 */
private fun screenUniformRatio(view: View, onResult: (Float?) -> Unit) {
    val act = activityOf(view.context)
    if (act == null || view.width <= 0 || view.height <= 0 || android.os.Build.VERSION.SDK_INT < 26) { onResult(null); return }
    val loc = IntArray(2)
    view.getLocationInWindow(loc)
    val n = 48
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(act.window, Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height), bmp, { code ->
            if (code != PixelCopy.SUCCESS) { onResult(null); return@request }
            val px = IntArray(n * n)
            bmp.getPixels(px, 0, n, 0, 0, n, n)
            val counts = HashMap<Int, Int>()
            px.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            onResult((counts.values.maxOrNull() ?: 0) / px.size.toFloat())
        }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) { onResult(null) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PortalBrowserDialog(title: String, startUrl: String, portalKey: String?, showPdf: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var notFound by remember { mutableStateOf<List<String>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var renderWarn by remember { mutableStateOf<String?>(null) }   // هشدار تشخیص خودکار صفحهٔ سفید؛ فقط یک نوار کوچک است و صفحه را نمی‌پوشاند
    var lastConsole by remember { mutableStateOf("") }      // آخرین خطای جاوااسکریپت صفحه؛ برای تشخیص علت صفحهٔ خالی
    var lastHttp by remember { mutableStateOf("") }
    // «حالت سازگاری»: نمایش نرم‌افزاری WebView؛ روی بعضی گوشی‌ها صفحه با شتاب سخت‌افزاری سفید می‌ماند. انتخاب روی گوشی یادآوری می‌شود.
    val browserPrefs = remember { ctx.getSharedPreferences("shiftma_browser", Context.MODE_PRIVATE) }
    var soft by remember { mutableStateOf(false) }          // فقط برای همین بار؛ پیش‌فرض خاموش (همان WebView ساده‌ای که کار می‌کرد)
    LaunchedEffect(Unit) { browserPrefs.edit().remove("soft").apply() }   // مقدار ذخیره‌شدهٔ نسخه‌های ۱.۸.۳ و ۱.۸.۴ پاک شود
    var started by remember { mutableStateOf(false) }       // onPageStarted حداقل یک بار آمده؟
    var finished by remember { mutableStateOf(false) }      // onPageFinished حداقل یک بار آمده؟
    fun setSoft(on: Boolean) { soft = on; started = false; finished = false; loadError = null; renderWarn = null }
    // اگر بعد از چند ثانیه حتی بارگذاری شروع نشده باشد (صفحهٔ سفید بی‌پیام)، به‌جای سفیدی دلیل و راه‌حل نشان بده
    LaunchedEffect(soft) {
        delay(9000)
        if (!finished && loadError == null) {
            loadError = if (!started) "سایت حتی شروع به بارگذاری نشد. ممکن است موتور نمایش وب گوشی یا اتصال برنامه مشکل داشته باشد."
                        else "بارگذاری سایت تمام نشد. اینترنت یا VPN را بررسی کن."
        }
    }
    var cred by remember { mutableStateOf(portalKey?.let { PortalVault.get(ctx, it) }) }
    var credDialog by remember { mutableStateOf(false) }
    var pendingSlip by remember { mutableStateOf<Pair<ByteArray, List<String>>?>(null) }
    val log = remember { RequestLog(PdfFinder.MAX_SEEN) }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    fun directDownload(url: String, ua: String?, mime: String?, cd: String?) {
        if (!url.startsWith("http")) { toast("این لینک را نمی‌شود مستقیم دانلود کرد؛ از دکمهٔ «دانلود فایل PDF» استفاده کن."); return }
        try { toast("دانلود شروع شد: " + PayslipPdf.enqueue(ctx, url, ua, mime, cd)) }
        catch (e: Exception) { toast("دانلود نشد: ${e.message}") }
    }

    fun findPdf() {
        val w = web ?: return
        if (busy) return
        busy = true
        toast("در حال جست‌وجوی فایل PDF...")
        w.evaluateJavascript(DOM_URLS_JS) { res ->
          val dom = try { (JSONTokener(res).nextValue() as? String).orEmpty() } catch (e: Exception) { "" }
          w.evaluateJavascript(PAGE_TEXT_JS) { textRes ->
            val pageText = try { (JSONTokener(textRes).nextValue() as? String).orEmpty() } catch (e: Exception) { "" }
            val cands = PdfFinder.candidates(w.url ?: startUrl, dom.split('\n'), log.newestFirst())
            val ua = w.settings.userAgentString
            // اولویت تشخیص: متن صفحه، بعد آدرس‌ها (ممکن است نوع یا ماه در آدرس باشد)، بعد متن خود PDF
            val hints = listOf(pageText, runCatching { java.net.URLDecoder.decode(cands.joinToString("\n") { it.url }, "UTF-8") }.getOrDefault(""))
            scope.launch {
                val hit = withContext(Dispatchers.IO) { PayslipPdf.fetchFirst(cands, ua) }
                busy = false
                if (hit == null) { notFound = cands.take(12).map { it.url.take(160) }; return@launch }
                val name = "payslip_${System.currentTimeMillis()}.pdf"
                withContext(Dispatchers.IO) { runCatching { PayslipPdf.save(ctx, hit.bytes, name) } }
                    .onSuccess { toast("فایل PDF اصلی ذخیره شد: $name\n$it"); pendingSlip = hit.bytes to hints }
                    .onFailure { toast("ذخیره نشد: ${it.message}") }
            }
          }
        }
    }

    /** صفحهٔ بارگذاری‌شده بدون هیچ محتوایی (متن، تصویر، فرم، قاب یا بوم) یعنی چیزی خراب است؛ علت احتمالی را نشان می‌دهیم. */
    fun probeBlank(view: WebView, attempt: Int) {
        view.evaluateJavascript(
            "(function(){try{var b=document.body;return JSON.stringify({t:b?b.innerText.trim().length:-1,i:document.images.length," +
                "f:document.querySelectorAll('iframe,canvas,input,video,svg').length,s:document.readyState});}catch(e){return 'err'}})()"
        ) { res ->
            val o = try { org.json.JSONObject(JSONTokener(res).nextValue() as? String ?: return@evaluateJavascript) } catch (e: Exception) { return@evaluateJavascript }
            val empty = o.optInt("t", -1) < 5 && o.optInt("i", 0) == 0 && o.optInt("f", 0) == 0
            if (empty) {
                if (attempt >= 1 && loadError == null) renderWarn = "صفحه محتوایی ندارد؛ شاید هنوز در حال بارگذاری است یا سایت نمایش در برنامه را بسته."
                return@evaluateJavascript
            }
            screenUniformRatio(view) { ratio ->
                if (ratio == null || loadError != null) return@screenUniformRatio
                if (ratio < 0.985f) { renderWarn = null; return@screenUniformRatio }
                // صفحه‌های سنگین (SPA) ثانیه‌های اول سفیدند؛ فقط در بررسی دوم (چند ثانیه بعد) تصمیم می‌گیریم
                if (attempt < 1) return@screenUniformRatio
                if (!soft) { setSoft(true); toast("نمایش روی این گوشی خالی بود؛ حالت سازگاری روشن شد") }
                else renderWarn = "صفحه یک‌دست دیده می‌شود. اگر سایت را می‌بینی این هشدار را ببند؛ وگرنه در مرورگر گوشی باز کن."
            }
        }
    }

    // مرورگر داخل همان پنجرهٔ برنامه کشیده می‌شود نه پنجرهٔ جدا (Dialog)؛ WebView داخل پنجرهٔ Dialog روی بعضی گوشی‌ها سفید می‌ماند
    Dialog(
        onDismissRequest = { if (web?.canGoBack() == true) web?.goBack() else onClose() },   // دکمهٔ بازگشت: اول صفحهٔ قبل
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallIconButton(R.drawable.ic_close, "بستن", onClose)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton({ setSoft(!soft) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (soft) "سازگاری ✓" else "سازگاری") }
                    if (portalKey != null) SmallIconButton(
                        R.drawable.ic_lock, if (cred != null) "نام کاربری و رمز ذخیره‌شده" else "ذخیرهٔ نام کاربری و رمز", { credDialog = true },
                        tint = if (cred != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SmallIconButton(R.drawable.ic_open_new, "باز کردن در مرورگر گوشی", { openUrl(ctx, web?.url ?: startUrl) })
                    SmallIconButton(R.drawable.ic_arrow_back, "صفحهٔ قبل", { web?.takeIf { it.canGoBack() }?.goBack() })
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())

                Box(Modifier.weight(1f).fillMaxWidth()) {
                key(soft) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { w -> w.stopLoading(); w.destroy() },
                    factory = { c ->
                        WebView(c).apply {
                            if (soft) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            // بعضی سایت‌ها WebView را از نشانهٔ «; wv» می‌شناسند و صفحه را خالی یا ناقص می‌فرستند؛ مثل Chrome معمولی معرفی شود
                            if (soft) settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
                            if (soft) setBackgroundColor(android.graphics.Color.WHITE)    // اگر صفحه چیزی نکشید، سفید دیده شود نه شفاف
                            // WebView به‌طور پیش‌فرض هدر X-Requested-With با نام بستهٔ برنامه می‌فرستد؛ بعضی سایت‌ها/فایروال‌ها به‌خاطرش صفحه را خالی می‌دهند
                            if (soft && WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet()) }
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                                    if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                        lastConsole = "${cm.message().take(160)} (${cm.sourceId().orEmpty().substringAfterLast('/').take(40)}:${cm.lineNumber()})"
                                    }
                                    return false
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                // پر کردن خودکار فرم ورود: فقط https و فقط روی همان سایتِ ذخیره‌شده؛ دکمهٔ ورود را خود کاربر می‌زند
                                override fun onPageFinished(view: WebView, url: String?) {
                                    finished = true
                                    // اگر بعد از چند ثانیه صفحه هیچ متن، تصویر یا فرمی نداشت، به‌جای صفحهٔ سفید دلیلش را نشان بده
                                                                        val c = cred ?: return
                                    if (url == null || !PortalAutofill.hostMatches(c.host, url)) return
                                    val js = PortalAutofill.fillScript(c.user, c.pass)
                                    view.evaluateJavascript(js, null)
                                    view.postDelayed({ view.evaluateJavascript(js, null) }, 1200)   // صفحه‌های دیرلود
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { started = true; loadError = null; renderWarn = null; lastConsole = ""; lastHttp = "" }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) loadError = "اتصال به سایت برقرار نشد (${error.description}). اینترنت یا VPN را بررسی کن."
                                }

                                // اگر موتور نمایش وب (پردازهٔ رندر) متوقف شود، صفحه خالی می‌ماند؛ به‌جای بسته شدن برنامه پیام نشان بده
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    loadError = if (detail.didCrash()) "موتور نمایش وب گوشی متوقف شد." else "موتور نمایش وب گوشی به‌خاطر کمبود حافظه بسته شد."
                                    return true
                                }

                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                    if (request.isForMainFrame) lastHttp = "HTTP ${errorResponse.statusCode}"
                                    if (request.isForMainFrame && errorResponse.statusCode >= 400) loadError = "سایت خطای ${errorResponse.statusCode} داد."
                                }

                                // گواهی نامعتبر هرگز پذیرفته نمی‌شود (امنیت رمز)؛ فقط دلیل نشان داده می‌شود
                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                    handler.cancel()
                                    loadError = "گواهی امنیتی سایت از نظر گوشی معتبر نیست. ساعت و تاریخ گوشی را بررسی کن یا در مرورگر گوشی باز کن."
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    val scheme = request.url.scheme?.lowercase()
                                    if (scheme != "http" && scheme != "https") return true           // intent:، tel: و ... را رد کن
                                    if (u.substringBefore('?').endsWith(".pdf", ignoreCase = true)) {   // لینک مستقیم PDF
                                        directDownload(u, view.settings.userAgentString, "application/pdf", null)
                                        return true
                                    }
                                    return false
                                }
                                // همهٔ درخواست‌های صفحه (و فریم‌های داخلش) ثبت می‌شوند؛ همیشه null تا درخواست عادی انجام شود
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val u = request.url.toString()
                                    if (PdfFinder.shouldLog(request.method, u)) log.add(u, request.requestHeaders["Referer"])
                                    return null
                                }
                            }
                            // اگر سایت خودش فایل یا Content-Disposition فرستاد
                            setDownloadListener(DownloadListener { url, ua, cd, mime, _ -> directDownload(url, ua, mime, cd) })
                            web = this
                            loadUrl(startUrl)
                        }
                    }
                )
                }
                loadError?.let { msg ->
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                        val tech = listOf("شروع=$started پایان=$finished پیشرفت=$progress", lastHttp, lastConsole).filter { it.isNotBlank() }.joinToString("\n")
                        if (tech.isNotEmpty()) Text(tech, style = MaterialTheme.typography.bodySmall, color = Pal.muted, modifier = Modifier.fillMaxWidth())
                        AppButton("تلاش دوباره", { loadError = null; web?.reload() }, Modifier.fillMaxWidth())
                        AppButton("باز کردن در مرورگر گوشی", { openUrl(ctx, web?.url ?: startUrl) }, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
                        TextButton({ loadError = null }) { Text("بستن این پیام") }
                    }
                }
                renderWarn?.let { w ->
                    Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), tonalElevation = 6.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(w, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ openUrl(ctx, web?.url ?: startUrl) }) { Text("باز کردن در مرورگر گوشی") }
                                TextButton({ renderWarn = null }) { Text("بستن") }
                            }
                        }
                    }
                }
                }

                if (showPdf) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppButton(if (busy) "در حال جست‌وجو…" else "دانلود فایل PDF", { findPdf() }, Modifier.fillMaxWidth(), icon = R.drawable.ic_download, enabled = !busy)
                        HintText("اول وارد سامانه شو و فیش موردنظر را باز کن، بعد این دکمه را بزن. فایل در پوشهٔ Downloads ذخیره می‌شود.")
                    }
                }
            }
        }
    }

    if (credDialog && portalKey != null) PortalCredDialog(
        title, PortalAutofill.hostOf(startUrl).orEmpty(), cred,
        onSave = { c -> runCatching { PortalVault.put(ctx, portalKey, c) }.onSuccess { cred = c; credDialog = false; toast("ذخیره شد؛ دفعهٔ بعد فرم ورود خودکار پر می‌شود") }.onFailure { toast("ذخیره نشد") } },
        onDelete = { PortalVault.remove(ctx, portalKey); cred = null; credDialog = false; toast("نام کاربری و رمز حذف شد") },
        onClose = { credDialog = false }
    )

    pendingSlip?.let { (b, hints) -> SavePayslipDialog(b, hints, onClose = { pendingSlip = null }) { pendingSlip = null } }

    notFound?.let { urls ->
        AppDialog("فایل PDF پیدا نشد", { notFound = null }, dismissText = "باشه") {
            Text("از این صفحه فایل PDF مستقیم پیدا نشد. فیش را کامل باز کن (تا نمایش داده شود) و دوباره امتحان کن.", style = MaterialTheme.typography.bodyMedium)
            if (urls.isNotEmpty()) {
                HintText("آدرس‌هایی که بررسی شد:")
                urls.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Pal.muted) }
            }
        }
    }
}

/** ذخیرهٔ اختیاری نام کاربری و رمز این سامانه؛ فقط روی همین گوشی و رمزنگاری‌شده با Keystore. */
@Composable
private fun PortalCredDialog(title: String, host: String, existing: PortalVault.Cred?, onSave: (PortalVault.Cred) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var user by remember { mutableStateOf(existing?.user.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.pass.orEmpty()) }
    var show by remember { mutableStateOf(false) }
    AppDialog(
        "ورود خودکار به $title", onClose, confirmText = "ذخیره", dismissText = "بستن",
        confirmEnabled = user.isNotBlank() && pass.isNotEmpty() && host.isNotEmpty(),
        onConfirm = { onSave(PortalVault.Cred(user.trim(), pass, host)) }
    ) {
        HintText("نام کاربری و رمز فقط روی همین گوشی و رمزنگاری‌شده (Android Keystore) نگه داشته می‌شود؛ در پشتیبان و اشتراک‌گذاری برنامه نمی‌آید. فقط روی سایت همین سامانه و با https پر می‌شود و دکمهٔ ورود را خودت می‌زنی.")
        AppField(user, { user = it }, "نام کاربری", ltr = true)
        AppField(
            pass, { pass = it }, "رمز", ltr = true, keyboard = KeyboardType.Password,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
        )
        TextButton({ show = !show }) { Text(if (show) "پنهان کردن رمز" else "نمایش رمز") }
        if (existing != null) AppButton("حذف نام کاربری و رمز", onDelete, Modifier.fillMaxWidth(), style = BtnStyle.Outline)
    }
}
