package ir.shiftapp.ui

import ir.shiftapp.system.AdLoader
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.shiftapp.R

private fun versionOf(ctx: Context): String = try {
    @Suppress("DEPRECATION")
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) { "" }

private fun shareText(): String {
    val base = "${AppInfo.APP_NAME}؛ برنامهٔ مدیریت شیفت کاری: تقویم شمسی، الگوهای چرخشی، گزارش و مرخصی، یادآور و ویجت."
    return if (AppInfo.STORE_URL.isNotBlank()) base + "\n" + AppInfo.STORE_URL else base
}

internal fun shareLink(ctx: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, AppInfo.APP_NAME)
        putExtra(Intent.EXTRA_TEXT, shareText())
    }
    ctx.startActivity(Intent.createChooser(send, "اشتراک‌گذاری ${AppInfo.APP_NAME}"))
}

/**
 * فایل نصب (APK) همین برنامه را کپی و از طریق بلوتوث، پیام‌رسان‌ها و ... می‌فرستد.
 * فقط خودِ فایل فرستاده می‌شود (EXTRA_STREAM)؛ اگر متن (EXTRA_TEXT) هم همراهش باشد بعضی پیام‌رسان‌ها (ایتا، بله، روبیکا)
 * فقط متن را می‌گیرند و فایل را رها می‌کنند؛ دقیقاً باگ «متن می‌رود ولی برنامه نمی‌رود».
 */
internal suspend fun shareApk(ctx: Context) {
    val split = ctx.applicationInfo.splitSourceDirs
    if (split != null && split.isNotEmpty()) {
        Toast.makeText(ctx, "این نصب از چند فایل تشکیل شده (نسخهٔ Bundle) و ارسال مستقیم ممکن نیست؛ فقط متن معرفی ارسال می‌شود. برای ارسال فایل، نسخهٔ APK واحد را نصب کنید.", Toast.LENGTH_LONG).show()
        shareLink(ctx)
        return
    }
    val uri: Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "ShiftMa.apk")
            File(ctx.applicationInfo.sourceDir).copyTo(out, overwrite = true)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
        } catch (e: Exception) { null }
    }
    if (uri == null) {
        Toast.makeText(ctx, "ساخت فایل برنامه ممکن نشد", Toast.LENGTH_LONG).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        AppInfo.attachCaption(ctx, this)
        clipData = ClipData.newRawUri(AppInfo.APP_NAME, uri)      // مجوز خواندن را برای برنامهٔ مقصد قطعی می‌کند
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, "ارسال فایل برنامهٔ ${AppInfo.APP_NAME}"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "برنامه‌ای برای ارسال فایل پیدا نشد", Toast.LENGTH_LONG).show()
    }
}

private fun sendFeedback(ctx: Context) {
    val mail = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${AppInfo.CONTACT_EMAIL}")
        putExtra(Intent.EXTRA_SUBJECT, "بازخورد دربارهٔ ${AppInfo.APP_NAME}")
    }
    try { ctx.startActivity(mail) } catch (e: Exception) { /* برنامهٔ ایمیل نصب نیست */ }
}

/** اول خودِ برنامهٔ تلگرام (tg://)، اگر نبود صفحهٔ t.me در مرورگر. */
internal fun openTelegram(ctx: Context) {
    val id = AppInfo.TELEGRAM_ID
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$id"))) }
    catch (e: Exception) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$id"))) }
        catch (e2: Exception) { Toast.makeText(ctx, "تلگرام یا مرورگر پیدا نشد", Toast.LENGTH_LONG).show() }
    }
}

private fun openStore(ctx: Context) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.STORE_URL))) } catch (e: Exception) { }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val ver = versionOf(ctx)
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)))
            Text(AppInfo.APP_NAME, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            if (ver.isNotBlank()) Text("نسخهٔ ${ver.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
            Text(
                "برنامهٔ مدیریت شیفت کاری برای کارکنان شیفتی؛ جدول شیفت ماهانه را به‌صورت خودکار از روی الگوی چرخشی شما می‌سازد.",
                style = MaterialTheme.typography.bodyMedium, color = Pal.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        SettingsGroup("امکانات", icon = R.drawable.ic_star) {
            listOf(
                "تقویم شمسی با تعطیلات رسمی",
                "الگوهای چرخشی دلخواه و الگوهای آماده",
                "چهارشیفت، کاردکس و نمای گرافیکی ماه",
                "گزارش ماهانه، ساعت کار و مانده مرخصی",
                "تعویض شیفت و یافتن روز مشترک با همکار",
                "یادآور، ویجت و پشتیبان‌گیری خودکار",
                "خروجی PDF، Excel و تصویر",
                "اوقات شرعی، قبله و اذان (اختیاری، پیش‌فرض خاموش)"
            ).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(R.drawable.ic_check_circle, size = 20.dp, tint = Pal.green)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SettingsGroup("حریم خصوصی", icon = R.drawable.ic_lock) {
            Text("همهٔ اطلاعات فقط روی همین گوشی ذخیره می‌شود. برنامه حساب کاربری ندارد و داده‌ای به جایی ارسال نمی‌کند. اوقات شرعی بدون اینترنت محاسبه می‌شود و اذان پیش‌فرض همراه برنامه است. فقط اگر در بخش اذان «پخش آنلاین» یا «دانلود» را بزنید به اینترنت وصل می‌شود، و فقط اگر «موقعیت من» را بزنید آخرین موقعیت تقریبی گوشی خوانده می‌شود؛ هیچ‌کدام جایی ارسال نمی‌شود." + AdLoader.privacyNote, style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("اشتراک‌گذاری و ارتباط")
            AppCard(Modifier.fillMaxWidth()) {
                SettingRow("ارسال خود برنامه (فایل APK)", subtitle = "با بلوتوث، تلگرام، ایتا یا هر برنامهٔ دیگر برای دوستان", icon = R.drawable.ic_share, onClick = { scope.launch { shareApk(ctx) } })
                RowDivider()
                SettingRow("اشتراک‌گذاری متن معرفی", icon = R.drawable.ic_note, onClick = { shareLink(ctx) })
                RowDivider()
                SettingRow("امتیاز به برنامه", subtitle = "ثبت ستاره و نظر در کافه‌بازار", icon = R.drawable.ic_star, onClick = { RatePrompt.openBazaar(ctx) })
                if (AppInfo.TELEGRAM_ID.isNotBlank()) {
                    RowDivider()
                    SettingRow("ارتباط با توسعه‌دهنده در تلگرام", subtitle = "نظر، پیشنهاد یا گزارش مشکل", icon = R.drawable.ic_telegram, onClick = { openTelegram(ctx) })
                }
                if (AppInfo.CONTACT_EMAIL.isNotBlank()) {
                    RowDivider()
                    SettingRow("ارسال نظر و پیشنهاد", icon = R.drawable.ic_mail, onClick = { sendFeedback(ctx) })
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HintText("فونت: استعداد (مجوز OFL)")
            if (AppInfo.DEVELOPER.isNotBlank()) HintText("توسعه‌دهنده: ${AppInfo.DEVELOPER}")
        }
    }
}
