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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.shiftapp.R
import ir.shiftapp.core.AzanSounds
import ir.shiftapp.core.Books
import ir.shiftapp.core.Fa
import ir.shiftapp.docs.DocKind
import ir.shiftapp.system.BookFiles
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/*
 * بخش «کتاب‌ها»: کتابخانهٔ شخصی روی همین گوشی + مرورگر داخل برنامه برای پیدا کردن و دانلود کتاب،
 * همان الگوی بخش اذان: هر فایل کتاب که صفحه بدهد پیشنهاد «ذخیره در برنامه» می‌شود؛ ذخیره‌شده‌ها را می‌شود خواند، فرستاد و حذف کرد.
 */

private fun bookSize(b: Long): String = when {
    b < 1_048_576 -> "${Fa.num((b / 1024).coerceAtLeast(1).toInt())} کیلوبایت"
    else -> Fa.digits(String.format(Locale.US, "%.1f", b / 1_048_576.0)).replace('.', '٫') + " مگابایت"
}

/** فایل کتاب‌های روی گوشی (PDF/EPUB/…): جست‌وجو و دانلود، افزودن از حافظه، مطالعه. تبی در بخش «مطالعه و کتابخوانی». */
@Composable
internal fun BooksTab(openBrowser: Boolean = false, onBrowserShown: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf(BookFiles.list(ctx)) }
    fun refresh() { books = BookFiles.list(ctx) }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    var browser by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(if (openBrowser) 1 else 0) }
    LaunchedEffect(openBrowser) { if (openBrowser) { browser = true; onBrowserShown() } }
    var reading by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            BookFiles.import(ctx, uri)
                .onSuccess { refresh(); toast("کتاب اضافه شد") }
                .onFailure { toast(errorText(it)) }
        }
    }

    Segmented(listOf("کتابخانه", "دانلود"), tab) { tab = it }
    if (tab == 1) {
        AppButton("جست‌وجو و دانلود کتاب", { browser = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_search)
        HintText("در مرورگر جست‌وجو کن؛ فایل PDF یا EPUB را ذخیره کن تا مستقیم به کتابخانه اضافه شود.")
        AppButton(
            "افزودن فایل از گوشی",
            { picker.launch(arrayOf("application/pdf", "application/epub+zip", "application/octet-stream", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
            Modifier.fillMaxWidth(), icon = R.drawable.ic_upload, style = BtnStyle.Tonal
        )
    } else {
        HintText("${books.size.fa()} فایل کتاب")
        if (books.isEmpty()) EmptyState(
            R.drawable.ic_home_books, "کتابخانه خالی است",
            text = "از تب «دانلود» کتاب بگیر یا فایل PDF و EPUB را از گوشی اضافه کن."
        )
        books.forEach { f ->
            AppCard(Modifier.fillMaxWidth(), onClick = { reading = f }) {
                Row(
                    Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconBadge(R.drawable.ic_home_books, size = 44.dp, iconSize = 22.dp, modifier = Modifier)
                    Column(Modifier.weight(1f)) {
                        Text(Books.display(f.name), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${Books.extOf(f.name).uppercase()} · ${bookSize(f.length())}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                    }
                    SmallIconButton(R.drawable.ic_edit, "تغییر نام", { renaming = f })
                    SmallIconButton(R.drawable.ic_share, "ارسال", { shareDocFile(ctx, f, Books.mimeOf(f.name), Books.display(f.name)) })
                    SmallIconButton(R.drawable.ic_delete, "حذف", { deleting = f }, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    reading?.let { f ->
        if (Books.isPdf(f.name)) DocViewerDialog(Books.display(f.name), f, DocKind.PDF, "application/pdf") { reading = null }
        else { LaunchedEffect(f) { openDocExternally(ctx, f, Books.mimeOf(f.name)); reading = null } }
    }
    deleting?.let { f ->
        ConfirmDialog("«${Books.display(f.name)}» از برنامه حذف شود؟", "حذف", onOk = { BookFiles.delete(ctx, f.name); deleting = null; refresh() }, onCancel = { deleting = null }, danger = true)
    }
    renaming?.let { f ->
        var t by remember(f.name) { mutableStateOf(Books.display(f.name)) }
        AppDialog("تغییر نام", { renaming = null }, confirmText = "ذخیره", dismissText = "انصراف", confirmEnabled = t.isNotBlank(),
            onConfirm = { BookFiles.rename(ctx, f.name, t); renaming = null; refresh() }) {
            AppField(t, { t = it.take(70) }, "نام کتاب")
        }
    }
    if (browser) BookBrowserDialog(onSaved = { refresh() }, onClose = { browser = false; refresh() })
}

private data class FoundBook(val url: String, val name: String)

/**
 * مرورگر داخل برنامه برای پیدا کردن کتاب. کاربر خودش می‌گردد؛ برنامه چیزی را خودکار نمی‌خواند.
 * هر فایل کتابی که صفحه بدهد (لینک PDF/EPUB یا دکمهٔ دانلود) پیشنهاد «ذخیره در برنامه» می‌شود.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BookBrowserDialog(onSaved: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("دانلود کتاب pdf") }
    var engine by remember { mutableStateOf("google") }
    var progress by remember { mutableStateOf(0) }
    var pageUrl by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundBook>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { web?.apply { stopLoading(); destroy() } } }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    fun addFound(url: String, hint: String?) {
        if (found.any { it.url == url }) return
        val name = Books.sanitize(hint ?: Books.nameFromUrl(url))
        found = (listOf(FoundBook(url, name)) + found).take(4)
    }
    fun go() { web?.loadUrl(AzanSounds.browserTarget(input, engine)) }
    fun save(f: FoundBook) {
        if (saving) return
        if (!AzanSounds.isHttps(f.url)) { toast("این پیوند http و ناامن است؛ نسخهٔ https همان سایت را باز کنید"); return }
        saving = true
        val cookie = CookieManager.getInstance().getCookie(f.url)
        val ua = web?.settings?.userAgentString
        scope.launch {
            val r = BookFiles.download(ctx, f.url, referer = pageUrl.ifBlank { null }, cookie = cookie, userAgent = ua, nameHint = f.name)
            saving = false
            r.onSuccess { name -> toast("در کتاب‌ها ذخیره شد"); found = found.filter { it.url != f.url }; onSaved(name) }.onFailure { toast(errorText(it)) }
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
                                    if (Books.looksLikeBookUrl(u)) { addFound(u, null); return true }
                                    return false
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val u = request.url.toString()
                                    if (Books.looksLikeBookUrl(u)) view.post { addFound(u, null) }
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
                        HintText("روی لینک دانلود کتاب (PDF یا EPUB) بزنید؛ وقتی فایل پیدا شد، اینجا «ذخیره در برنامه» می‌آید.")
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("فایل کتاب پیدا شد", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                        found.forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(Books.display(f.name), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                AppButton(if (saving) "…" else "ذخیره در برنامه", { save(f) }, icon = R.drawable.ic_download, enabled = !saving)
                            }
                        }
                    }
                }
            }
        }
    }
}
