package ir.shiftapp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.shiftapp.R
import ir.shiftapp.core.AzanSounds
import ir.shiftapp.system.AzanFiles
import kotlinx.coroutines.launch

private data class FoundAudio(val url: String, val name: String)

/**
 * مرورگر داخل برنامه برای پیدا کردن اذان در گوگل و سایت‌های فارسی. کاربر خودش می‌گردد؛ برنامه چیزی را خودکار نمی‌خواند.
 * هر فایل صوتی که صفحه بارگیری یا دانلود کند (لینک mp3، دکمهٔ دانلود، پخش‌کنندهٔ صفحه) پیشنهاد «ذخیره در برنامه» می‌شود
 * و با همان کوکی و صفحهٔ مبدأ در پوشهٔ خصوصی برنامه ذخیره می‌شود.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AzanBrowserDialog(onSaved: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("اذان موذن زاده mp3") }
    var engine by remember { mutableStateOf("google") }
    var progress by remember { mutableStateOf(0) }
    var pageUrl by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundAudio>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { web?.apply { stopLoading(); destroy() } } }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    fun addFound(url: String, hint: String?) {
        if (found.any { it.url == url }) return
        val name = AzanSounds.sanitize(hint ?: AzanSounds.nameFromUrl(url))
        found = (listOf(FoundAudio(url, name)) + found).take(4)
    }
    fun go() { web?.loadUrl(AzanSounds.browserTarget(input, engine)) }
    fun save(f: FoundAudio) {
        if (saving) return
        if (!AzanSounds.isHttps(f.url)) { toast("این پیوند http و ناامن است؛ نسخهٔ https همان سایت را باز کنید"); return }
        saving = true
        val cookie = CookieManager.getInstance().getCookie(f.url)
        val ua = web?.settings?.userAgentString
        scope.launch {
            val r = AzanFiles.download(ctx, f.url, referer = pageUrl.ifBlank { null }, cookie = cookie, userAgent = ua, nameHint = f.name)
            saving = false
            r.onSuccess { toast("ذخیره شد و به‌عنوان صدای اذان انتخاب شد"); onSaved(it) }.onFailure { toast(errorText(it)) }
        }
    }

    Dialog(
        onDismissRequest = { if (web?.canGoBack() == true) web?.goBack() else onClose() },   // دکمهٔ بازگشت: اول صفحهٔ قبل
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmallIconButton(R.drawable.ic_close, "بستن مرورگر", onClose)
                    Box(Modifier.weight(1f)) { AppField(input, { input = it }, "جست‌وجو یا نشانی سایت") }
                    SmallIconButton(R.drawable.ic_search, "برو", { go() })
                }
                ChipRow {
                    AppChip("گوگل", engine == "google", { engine = "google"; go() })
                    AppChip("بینگ", engine == "bing", { engine = "bing"; go() })
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())

                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { pageUrl = url ?: ""; found = emptyList() }
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    val scheme = request.url.scheme?.lowercase()
                                    if (scheme != "http" && scheme != "https") return true      // intent:، market: و ... را رد کن
                                    if (AzanSounds.looksLikeAudioUrl(u)) { addFound(u, null); return true }
                                    return false
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val u = request.url.toString()
                                    if (AzanSounds.looksLikeAudioUrl(u)) view.post { addFound(u, null) }
                                    return null
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                            }
                            setDownloadListener(DownloadListener { url, _, cd, mime, _ ->
                                if (!url.isNullOrBlank()) addFound(url, URLUtil.guessFileName(url, cd, mime))
                            })
                            web = this
                            loadUrl(AzanSounds.browserTarget(input, engine))
                        }
                    }
                )

                if (found.isEmpty()) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        HintText("روی لینک دانلود فایل mp3 بزنید یا دکمهٔ پخش خود صفحه را بزنید؛ وقتی فایل صوتی پیدا شد، اینجا «ذخیره» می‌آید.")
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("فایل صوتی پیدا شد", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                        found.forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(AzanSounds.display(f.name), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                AppButton(if (saving) "…" else "ذخیره در برنامه", { save(f) }, icon = R.drawable.ic_download, enabled = !saving)
                            }
                        }
                    }
                }
            }
        }
    }
}
