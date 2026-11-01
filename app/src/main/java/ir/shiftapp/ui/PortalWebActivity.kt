package ir.shiftapp.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.URLUtil
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.core.PayslipDetect
import ir.shiftapp.core.PayslipNames
import ir.shiftapp.core.SlipKind
import ir.shiftapp.core.PdfFinder
import ir.shiftapp.core.PortalAutofill
import ir.shiftapp.core.RequestLog
import ir.shiftapp.system.PayslipFiles
import ir.shiftapp.system.PayslipPdf
import ir.shiftapp.system.PortalVault
import org.json.JSONTokener

/**
 * مرورگر سامانه‌های کاری (مس من و ...) در یک Activity جدا با WebView ساده؛
 * دقیقاً همان ساختاری که در برنامهٔ آزمایشی FishApp سایت را درست نشان داد
 * (نه داخل Compose یا Dialog). نوار بالا: بستن، عنوان، بارگذاری مجدد و منوی ⋮؛ پایین: دانلود فایل اصلی PDF و چاپ به PDF.
 */
class PortalWebActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var bar: ProgressBar
    private var cred: PortalVault.Cred? = null
    private var portalKey: String? = null
    private var startUrl = ""
    private var busy = false
    private lateinit var status: TextView
    private var defaultUa = ""
    private var compat = false
    private var desktop = false
    private var lastConsole = ""
    private var lastHttp = ""
    private val log = RequestLog(PdfFinder.MAX_SEEN)   // آدرس درخواست‌های صفحه؛ برای پیدا کردن فایل اصلی PDF

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "سامانه"
        startUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        portalKey = intent.getStringExtra(EXTRA_KEY)
        val showPdf = intent.getBooleanExtra(EXTRA_PDF, false)
        cred = portalKey?.let { runCatching { PortalVault.get(this, it) }.getOrNull() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // نوار بالا: بستن | عنوان | بارگذاری مجدد | منو (بقیهٔ امکانات داخل منو)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        }
        val menuBtn = iconBtn("⋮", "منو") { }
        top.addView(iconBtn("✕", "بستن") { finish() })
        top.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(INK); typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        })
        top.addView(iconBtn("↻", "بارگذاری مجدد") { web.reload() })
        top.addView(menuBtn)
        menuBtn.setOnClickListener { showMenu(menuBtn) }

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(BLUE)
            visibility = View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        // خط وضعیت فقط در «حالت پیشرفته» دیده می‌شود
        status = TextView(this).apply {
            textSize = 11f; setTextColor(MUTED); setPadding(dp(12), dp(2), dp(12), dp(2)); visibility = View.GONE
            setBackgroundColor(0xFFF3F4F6.toInt())
        }
        web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        root.addView(top)
        root.addView(bar)
        root.addView(status)
        root.addView(web)
        if (showPdf) {
            root.addView(View(this).apply {
                setBackgroundColor(LINE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            fun bottomBtn(text: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
                this.text = text; textSize = 14f; isAllCaps = false; stateListAnimator = null
                setTextColor(if (primary) Color.WHITE else BLUE)
                background = pill(if (primary) BLUE else Color.WHITE, BLUE)
                minHeight = dp(48); minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                }
                setOnClickListener { onClick() }
            }
            row.addView(bottomBtn("دانلود فایل اصلی PDF", true) { findPdf() })
            row.addView(bottomBtn("چاپ صفحه به PDF", false) { printToPdf() })
            root.addView(row)
        }
        setContentView(root)

        // فاصله از نوار وضعیت و ناوبری
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        // همان تنظیمات FishApp، بدون هیچ دستکاری دیگر
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        defaultUa = web.settings.userAgentString
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        setClients(simple = true)      // پیش‌فرض: دقیقاً مثل FishApp
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, mimeType, contentDisposition)
        }

        if (savedInstanceState == null) web.loadUrl(startUrl) else web.restoreState(savedInstanceState)
    }

    /**
     * دانلود فایل اصلی PDF: آدرس‌های صفحه و درخواست‌های ثبت‌شده را با کوکی ورود امتحان می‌کند، اولین پاسخ «%PDF-» را
     * در Downloads ذخیره می‌کند و همان را (با تشخیص نوع و ماه) در «فیش‌های من» هم می‌گذارد. نتیجه با پنجره گزارش می‌شود.
     */
    private fun findPdf() {
        if (busy) return
        busy = true
        toast("در حال جست‌وجوی فایل PDF...")
        web.evaluateJavascript(DOM_URLS_JS) { res ->
            val dom = try { (JSONTokener(res).nextValue() as? String).orEmpty() } catch (e: Exception) { "" }
            web.evaluateJavascript(PAGE_TEXT_JS) { textRes ->
                val pageText = try { (JSONTokener(textRes).nextValue() as? String).orEmpty() } catch (e: Exception) { "" }
                val cands = PdfFinder.candidates(web.url ?: startUrl, dom.split('\n'), log.newestFirst())
                val ua = web.settings.userAgentString
                // اولویت تشخیص: متن صفحه، بعد آدرس‌ها، بعد متن خود PDF
                val hints = listOf(pageText, runCatching { java.net.URLDecoder.decode(cands.joinToString("\n") { it.url }, "UTF-8") }.getOrDefault(""))
                Thread {
                    val hit = runCatching { PayslipPdf.fetchFirst(cands, ua) }.getOrNull()
                    if (hit == null) {
                        runOnUiThread {
                            busy = false
                            AlertDialog.Builder(this)
                                .setTitle("فایل PDF پیدا نشد")
                                .setMessage("اول وارد سامانه شو و خود فیش را باز کن، بعد دوباره بزن؛ یا از «چاپ صفحه به PDF» استفاده کن.\n\nآدرس‌های بررسی‌شده:\n" +
                                    cands.take(12).joinToString("\n") { it.url.take(120) })
                                .setPositiveButton("باشه", null)
                                .show()
                        }
                        return@Thread
                    }
                    val name = "payslip_${System.currentTimeMillis()}.pdf"
                    val saved = runCatching { PayslipPdf.save(this, hit.bytes, name) }
                    val det = runCatching { PayslipDetect.detect(hints + PayslipDetect.pdfText(hit.bytes)) }.getOrNull()
                    runOnUiThread {
                        busy = false
                        onSlipDownloaded(hit.bytes, name, saved.getOrNull(), saved.exceptionOrNull()?.message, det)
                    }
                }.start()
            }
        }
    }

    private fun slipLabel(year: Int, month: Int) = "${Fa.MONTHS[month - 1]} ${Fa.num(year)}"

    /** بعد از دانلود: اگر نوع، ماه و سال همه تشخیص داده شد خودکار در «فیش‌های من» ذخیره می‌شود؛ وگرنه کاربر تأیید/اصلاح می‌کند. */
    private fun onSlipDownloaded(bytes: ByteArray, fileName: String, savedWhere: String?, saveError: String?, det: PayslipDetect.Result?) {
        val downloadsNote = if (savedWhere != null) "فایل اصلی هم در $savedWhere ذخیره شد ($fileName)." else "ذخیره در Downloads انجام نشد: ${saveError.orEmpty()}"
        val kind = det?.kind
        val month = det?.month
        val year = det?.year
        if (kind != null && month != null && year != null) {
            saveSlip(bytes, year, month, kind, downloadsNote)
        } else {
            chooseSlipDialog(bytes, det, downloadsNote)
        }
    }

    private fun saveSlip(bytes: ByteArray, year: Int, month: Int, kind: SlipKind, downloadsNote: String) {
        val replaced = PayslipFiles.exists(this, year, month, kind)
        runCatching { PayslipFiles.save(this, bytes, year, month, kind) }
            .onSuccess {
                AlertDialog.Builder(this)
                    .setTitle("فیش ذخیره شد ✓")
                    .setMessage("فیش ${kind.label} ${slipLabel(year, month)} در «فیش‌های من» ذخیره شد." +
                        (if (replaced) "\n(جایگزین فیش قبلیِ همین ماه و نوع شد.)" else "") + "\n\n" + downloadsNote)
                    .setPositiveButton("باز کردن فیش‌های من") { _, _ -> openPayslips() }
                    .setNegativeButton("بستن", null)
                    .show()
            }
            .onFailure {
                AlertDialog.Builder(this)
                    .setTitle("ذخیره در فیش‌های من نشد")
                    .setMessage((it.message ?: "خطای نامشخص") + "\n\n" + downloadsNote)
                    .setPositiveButton("باشه", null)
                    .show()
            }
    }

    /** وقتی تشخیص خودکار کامل نبود: نوع، ماه و سال را با مقدار پیشنهادی نشان می‌دهد تا کاربر تأیید یا اصلاح کند. */
    private fun chooseSlipDialog(bytes: ByteArray, det: PayslipDetect.Result?, downloadsNote: String) {
        val today = JalaliDate.today()
        val prev = PayslipNames.previous(today.year, today.month)     // فیش معمولاً مال ماه گذشته است
        val month0 = det?.month ?: prev.second
        val year0 = det?.year ?: if (det?.month != null) (if (month0 <= today.month) today.year else today.year - 1) else prev.first

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        col.addView(TextView(this).apply {
            textSize = 13f; setTextColor(MUTED)
            text = if (det == null || det.isEmpty) "نوع و ماه از فایل تشخیص داده نشد؛ خودت انتخاب کن."
            else "تشخیص خودکار: " + listOfNotNull(det.kind?.let { "فیش ${it.label}" }, det.month?.let { Fa.MONTHS[it - 1] }).joinToString(" · ") + ". اگر درست نیست تغییر بده."
        })
        val group = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rbs = SlipKind.values().map { k -> RadioButton(this).apply { text = k.label; id = View.generateViewId(); tag = k } }
        rbs.forEach { group.addView(it) }
        (rbs.firstOrNull { it.tag == (det?.kind ?: SlipKind.HOKMI) } ?: rbs[0]).isChecked = true
        col.addView(group)
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(this@PortalWebActivity, android.R.layout.simple_spinner_dropdown_item, Fa.MONTHS)
            setSelection(month0 - 1)
        }
        col.addView(spin)
        val yearEt = EditText(this).apply { hint = "سال (شمسی)"; setText(year0.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        col.addView(yearEt)

        val dlg = AlertDialog.Builder(this)
            .setTitle("ذخیره در «فیش‌های من»")
            .setView(col)
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("فعلاً نه") { _, _ -> toast(downloadsNote) }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val y = PayslipDetect.normalize(yearEt.text.toString()).trim().toIntOrNull() ?: 0
                if (y !in 1300..1600) { toast("سال را درست بنویس (مثل 1405)"); return@setOnClickListener }
                val kind = (rbs.firstOrNull { it.isChecked }?.tag as? SlipKind) ?: SlipKind.HOKMI
                dlg.dismiss()
                saveSlip(bytes, y, spin.selectedItemPosition + 1, kind, downloadsNote)
            }
        }
        dlg.show()
    }

    /** صفحهٔ «فیش‌های من» را در برنامهٔ اصلی باز می‌کند و مرورگر را می‌بندد. */
    private fun openPayslips() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SECTION, "payslips")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private var simple = true

    /**
     * حالت ساده: دقیقاً مثل FishApp (WebChromeClient پیش‌فرض و WebViewClient فقط با shouldOverrideUrlLoading).
     * حالت پیشرفته: ثبت درخواست‌ها برای «دانلود فایل اصلی PDF»، پر کردن خودکار فرم ورود، خط وضعیت و خطاها.
     */
    private fun setClients(simple: Boolean) {
        this.simple = simple
        if (simple) {
            if (::status.isInitialized) status.visibility = View.GONE
            web.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = updateProgress(newProgress)
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.substringBefore('?').endsWith(".pdf", ignoreCase = true)) {
                        download(url, null, "application/pdf")
                        return true
                    }
                    return false
                }
            }
        } else {
            if (::status.isInitialized) { status.visibility = View.VISIBLE; status.text = "حالت پیشرفته" }
            web.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        lastConsole = "${cm.message().take(90)} (${cm.sourceId().orEmpty().substringAfterLast('/').take(30)}:${cm.lineNumber()})"
                        refreshStatus()
                    }
                    return false
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) = updateProgress(newProgress)
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https") return true      // intent:، tel: و ... رد شود
                    if (url.substringBefore('?').endsWith(".pdf", ignoreCase = true)) {
                        download(url, null, "application/pdf")
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

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    lastConsole = ""; lastHttp = ""
                    status.text = "در حال بارگذاری: ${url.orEmpty().take(70)}"
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame) { lastHttp = "HTTP ${errorResponse.statusCode}"; refreshStatus() }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()      // گواهی نامعتبر هرگز پذیرفته نمی‌شود
                    lastHttp = "گواهی امنیتی نامعتبر (ساعت و تاریخ گوشی را چک کن)"; refreshStatus()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    refreshStatus(done = true)
                    // پر کردن خودکار فرم ورود: فقط https و فقط همان سایتِ ذخیره‌شده؛ دکمهٔ ورود را خود کاربر می‌زند
                    val c = cred ?: return
                    if (url == null || !PortalAutofill.hostMatches(c.host, url)) return
                    val js = PortalAutofill.fillScript(c.user, c.pass)
                    view.evaluateJavascript(js, null)
                    view.postDelayed({ view.evaluateJavascript(js, null) }, 1200)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        Toast.makeText(this@PortalWebActivity, "باز نشد: ${error.description}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun refreshStatus(done: Boolean = false) {
        val parts = listOfNotNull(
            if (done) "پایان بارگذاری" else null,
            lastHttp.ifBlank { null },
            lastConsole.ifBlank { null }?.let { "JS: $it" },
            if (compat) "حالت سازگاری" else null
        )
        if (parts.isNotEmpty()) status.text = parts.joinToString(" | ")
    }

    /** حالت سازگاری: معرفی مثل Chrome معمولی (بدون «; wv») و حذف هدر X-Requested-With؛ بعضی سایت‌ها WebView را می‌شناسند و صفحه را خالی می‌دهند. */
    private fun applyCompat(on: Boolean) {
        compat = on
        applyUa()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(web.settings, if (on) emptySet() else setOf("*")) }
        }
        web.reload()
    }

    // ───────────── ظاهر: رنگ‌ها و ابزارهای ساخت دکمه ─────────────
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** دکمهٔ گرد با حاشیه (و ریپل لمس). [fill] رنگ داخل، [stroke] رنگ حاشیه. */
    private fun pill(fill: Int, stroke: Int): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat(); setColor(fill); setStroke(dp(1).coerceAtLeast(1), stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null)
    }

    /** دکمهٔ آیکونی ۴۸dp بدون پس‌زمینه. */
    private fun iconBtn(glyph: String, desc: String, onClick: () -> Unit) = TextView(this).apply {
        text = glyph; textSize = 22f; setTextColor(INK); gravity = Gravity.CENTER
        contentDescription = desc
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun updateProgress(p: Int) {
        bar.progress = p
        bar.visibility = if (p in 1..99) View.VISIBLE else View.INVISIBLE
    }

    /** منوی ⋮ : همهٔ امکانات کم‌کاربرد اینجاست تا نوار بالا شلوغ نشود. */
    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        fun add(id: Int, t: String) = m.menu.add(0, id, id, t)
        add(1, "صفحهٔ قبلی"); add(2, "صفحهٔ بعدی"); add(3, "کپی لینک")
        add(4, "باز کردن در Chrome (درون‌برنامه)"); add(5, "باز کردن در مرورگر گوشی")
        add(6, (if (desktop) "✓ " else "") + "نسخهٔ دسکتاپ سایت")
        add(7, (if (compat) "✓ " else "") + "حالت سازگاری")
        add(8, (if (!simple) "✓ " else "") + "حالت پیشرفته (خط وضعیت)")
        if (portalKey != null) add(9, "نام کاربری و رمز")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (web.canGoBack()) web.goBack()
                2 -> if (web.canGoForward()) web.goForward()
                3 -> copyLink()
                4 -> openChromeTab()
                5 -> openOutside()
                6 -> toggleDesktop()
                7 -> applyCompat(!compat)
                8 -> { setClients(!simple); web.reload() }
                9 -> credDialog()
            }
            true
        }
        m.show()
    }

    /** User-Agent نهایی از ترکیب «سازگاری» (بدون ; wv) و «دسکتاپ» (معرفی مثل Chrome روی لینوکس). */
    private fun computeUa(): String {
        var ua = defaultUa
        if (compat) ua = ua.replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
        if (desktop) {
            val chrome = Regex("Chrome/[\\d.]+").find(defaultUa)?.value ?: "Chrome/120.0.0.0"
            ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        }
        return ua
    }

    private fun applyUa() { web.settings.userAgentString = computeUa() }

    private fun toggleDesktop() {
        desktop = !desktop
        applyUa()
        web.reload()
    }

    private fun copyLink() {
        val u = web.url ?: startUrl
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", u))
        toast("لینک کپی شد")
    }

    /** باز کردن همین صفحه در Chrome Custom Tab: خود Chrome صفحه را می‌کشد ولی داخل برنامه می‌ماند. */
    private fun openChromeTab() = launchChromeTab(this, web.url ?: startUrl)

    private fun openOutside() {
        val u = web.url ?: startUrl
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        catch (e: Exception) { Toast.makeText(this, "مرورگری پیدا نشد", Toast.LENGTH_SHORT).show() }
    }

    /** ذخیرهٔ صفحهٔ فعلی (فیش) به‌صورت PDF از طریق چاپ سیستم */
    private fun printToPdf() {
        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val name = "payslip_" + System.currentTimeMillis()
        val adapter = web.createPrintDocumentAdapter(name)
        pm.print(name, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        Toast.makeText(this, "در پنجرهٔ بازشده «Save as PDF» را انتخاب کنید", Toast.LENGTH_LONG).show()
    }

    /** دانلود فایل با همان کوکی‌های ورود */
    private fun download(url: String, userAgent: String?, mime: String?, disposition: String? = null) {
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            val cookie = CookieManager.getInstance().getCookie(url)
            if (cookie != null) req.addRequestHeader("Cookie", cookie)
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent)
            req.setMimeType(mime)
            val fileName = URLUtil.guessFileName(url, disposition, mime)
            req.setTitle(fileName)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "دانلود شروع شد: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "دانلود نشد: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** ذخیره/حذف اختیاری نام کاربری و رمز (رمزنگاری‌شده در PortalVault)؛ دفعهٔ بعد فرم ورود خودکار پر می‌شود. */
    private fun credDialog() {
        val key = portalKey ?: return
        val host = PortalAutofill.hostOf(startUrl).orEmpty()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val user = EditText(this).apply { hint = "نام کاربری"; setText(cred?.user.orEmpty()); inputType = InputType.TYPE_CLASS_TEXT }
        val pass = EditText(this).apply { hint = "رمز"; setText(cred?.pass.orEmpty()); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        col.addView(user); col.addView(pass)
        val b = AlertDialog.Builder(this)
            .setTitle("نام کاربری و رمز ($host)")
            .setView(col)
            .setPositiveButton("ذخیره") { _, _ ->
                val c = PortalVault.Cred(user.text.toString().trim(), pass.text.toString(), host)
                runCatching { PortalVault.put(this, key, c) }
                    .onSuccess { cred = c; Toast.makeText(this, "ذخیره شد؛ دفعهٔ بعد فرم ورود خودکار پر می‌شود", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(this, "ذخیره نشد", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("انصراف", null)
        if (cred != null) b.setNeutralButton("حذف") { _, _ ->
            PortalVault.remove(this, key); cred = null
            Toast.makeText(this, "نام کاربری و رمز حذف شد", Toast.LENGTH_SHORT).show()
        }
        b.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_KEY = "key"
        const val EXTRA_PDF = "pdf"

        private val BLUE = 0xFF1E5AA8.toInt()
        private val INK = 0xFF1F2937.toInt()
        private val MUTED = 0xFF6B7280.toInt()
        private val LINE = 0xFFE5E7EB.toInt()

        /** سایت‌هایی که WebView را رد می‌کنند و باید با Chrome Custom Tab باز شوند (مثلاً «رسا سلامت»). */
        val CHROME_TAB_HOSTS = setOf("rasatpa.ir")

        fun opensInChromeTab(url: String): Boolean {
            val h = Uri.parse(url).host?.lowercase() ?: return false
            return CHROME_TAB_HOSTS.any { h == it || h.endsWith(".$it") }
        }

        fun launchChromeTab(ctx: Context, url: String) {
            try {
                androidx.browser.customtabs.CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .setDefaultColorSchemeParams(
                        androidx.browser.customtabs.CustomTabColorSchemeParams.Builder().setToolbarColor(BLUE).build()
                    )
                    .build().launchUrl(ctx, Uri.parse(url))
            } catch (e: Exception) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e2: Exception) { Toast.makeText(ctx, "مرورگری پیدا نشد", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
