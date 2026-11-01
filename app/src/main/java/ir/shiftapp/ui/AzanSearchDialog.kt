package ir.shiftapp.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.system.AzanFiles
import ir.shiftapp.system.AzanPlayer
import ir.shiftapp.system.AzanSearch
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

private fun sizeLabel(b: Long): String = when {
    b <= 0 -> ""
    b < 1_048_576 -> "${Fa.num((b / 1024).toInt())} کیلوبایت"
    else -> Fa.digits(String.format(Locale.US, "%.1f", b / 1_048_576.0)).replace('.', '٫') + " مگابایت"
}

internal fun errorText(e: Throwable?): String =
    if (e is IllegalStateException || e is IllegalArgumentException) e.message ?: "انجام نشد"
    else "انجام نشد؛ اینترنت یا پیوند را بررسی کنید"

/**
 * جست‌وجوی اذان در اینترنت، پیش‌شنیدن (استریم) و دانلود در پوشهٔ برنامه.
 * [onDownloaded] نام فایل ذخیره‌شده را برمی‌گرداند.
 */
@Composable
internal fun AzanSearchDialog(prefs: Prefs, onDownloaded: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("اذان") }
    var loading by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<AzanHit>>(emptyList()) }
    var searchHits by remember { mutableStateOf<List<AzanHit>>(emptyList()) }
    var heading by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var browser by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { AzanPlayer.stop() } }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    fun stopPlay() { AzanPlayer.stop(); playing = null }

    fun search(q: String) {
        if (loading) return
        stopPlay(); loading = true; message = null; heading = null
        scope.launch {
            val r = AzanSearch.search(q)
            loading = false
            r.onSuccess {
                hits = it; searchHits = it
                if (it.isEmpty()) message = "نتیجه‌ای پیدا نشد؛ عبارت دیگری (مثلاً adhan یا azan) را امتحان کنید"
            }.onFailure { hits = emptyList(); message = errorText(it) }
        }
    }

    fun openItem(h: AzanHit) {
        if (loading) return
        stopPlay(); loading = true; message = null
        scope.launch {
            val r = AzanSearch.files(h)
            loading = false
            r.onSuccess {
                hits = it; heading = h.title
                if (it.isEmpty()) message = "این مجموعه فایل صوتی مناسبی (زیر ۲۰ مگابایت) ندارد"
            }.onFailure { message = errorText(it) }
        }
    }

    fun preview(url: String) {
        if (playing == url) { stopPlay(); return }
        if (AzanPlayer.playUrl(url, prefs.prayerAlarm) { ok -> playing = null; if (!ok) toast("پخش آنلاین نشد؛ پیوند یا اینترنت را بررسی کنید") }) playing = url
        else toast("پخش آنلاین نشد")
    }

    fun download(url: String) {
        if (loading) return
        if (!AzanSounds.isHttps(url)) { toast("پیوند باید با https شروع شود"); return }
        stopPlay(); loading = true
        scope.launch {
            val r = AzanFiles.download(ctx, url)
            loading = false
            r.onSuccess { onDownloaded(it); toast("دانلود شد و به‌عنوان صدای اذان انتخاب شد"); onClose() }.onFailure { toast(errorText(it)) }
        }
    }

    if (browser) AzanBrowserDialog({ name -> onDownloaded(name); onClose() }, { browser = false })

    AppDialog("جست‌وجو و دانلود اذان", onClose, dismissText = "بستن") {
        AppButton("جست‌وجو در اینترنت (گوگل و سایت‌های فارسی)", { stopPlay(); browser = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_search)
        HintText("مرورگر داخل برنامه باز می‌شود؛ هر سایتی را که خواستید جست‌وجو کنید و روی فایل صوتی بزنید، «ذخیره در برنامه» می‌آید.")

        SectionLabel("منابع آزاد (بیشتر عربی و انگلیسی)")
        AppField(query, { query = it }, "مثلاً adhan، azan")
        AppButton(if (loading) "کمی صبر کنید…" else "جست‌وجو", { search(query) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_search, enabled = !loading)
        ChipRow {
            listOf("اذان", "اذان صبح", "adhan", "azan").forEach { s -> AppChip(s, query == s, { query = s; search(s) }) }
        }

        if (heading != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallIconButton(R.drawable.ic_arrow_back, "بازگشت به نتایج", { stopPlay(); heading = null; hits = searchHits; message = null })
                Text(heading ?: "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2)
            }
        }
        message?.let { HintText(it) }

        hits.forEach { h ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        listOfNotNull(h.source, h.license, sizeLabel(h.sizeBytes).ifBlank { null }, if (h.isItem) "مجموعه" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Pal.muted
                    )
                }
                if (h.isItem) SmallIconButton(R.drawable.ic_chevron_left, "نمایش فایل‌ها", { openItem(h) })
                else {
                    SmallIconButton(if (playing == h.url) R.drawable.ic_close else R.drawable.ic_volume, if (playing == h.url) "توقف پخش" else "پخش آنلاین", { preview(h.url) })
                    SmallIconButton(R.drawable.ic_download, "دانلود", { download(h.url) })
                }
            }
        }

        SectionLabel("پیوند دارید؟")
        AppField(link, { link = it }, "پیوند مستقیم فایل صوتی (https)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("چسباندن", {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                if (t.isNotBlank()) link = t else toast("چیزی برای چسباندن نیست")
            }, Modifier.weight(1f), icon = R.drawable.ic_copy, style = BtnStyle.Tonal)
            AppButton("پخش", { if (AzanSounds.isHttps(link)) preview(link.trim()) else toast("پیوند باید با https شروع شود") }, Modifier.weight(1f), icon = R.drawable.ic_volume, style = BtnStyle.Tonal)
            AppButton("دانلود", { download(link.trim()) }, Modifier.weight(1f), icon = R.drawable.ic_download, enabled = !loading)
        }
        HintText("«منابع آزاد» در ویکی‌انبار و Internet Archive جست‌وجو می‌کند؛ فایل‌های ویکی‌انبار پروانهٔ مشخص دارند و حقوق فایل‌های دیگر با صاحب آن‌هاست. فایل دانلودشده در پوشهٔ خصوصی برنامه می‌ماند و بعدش بدون اینترنت پخش می‌شود (حداکثر ۲۰ مگابایت).")
    }
}
