package ir.shiftapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.AdFeed
import ir.shiftapp.system.AdLoader
import kotlinx.coroutines.delay

/** برچسب کوچک روی تصویر پنل صفحهٔ اول؛ هر واژه‌ای که می‌خواهید را اینجا بگذارید (فقط متن نمایشی است، به فایل‌های گیت‌هاب ربطی ندارد). */
const val PANEL_LABEL = "اطلاع‌رسانی"

/**
 * تبلیغ‌های این کاربر (محل کار + در صورت درخواست فایل کاشی، عمومی). [tile] خالی = تبلیغ‌های پنل صفحهٔ اول؛
 * وگرنه فقط تبلیغ‌هایی که فیلد tile آن‌ها برابر کلید همان کاشی است (مثل meals یا phonebook).
 */
@Composable
private fun rememberAds(adsFeed: String, publicAds: Boolean, tile: String): List<AdLoader.Loaded> {
    val ctx = LocalContext.current
    val feeds = remember(adsFeed, publicAds) { AdLoader.feedsFor(adsFeed, publicAds) }
    var ads by remember { mutableStateOf<List<AdLoader.Loaded>>(emptyList()) }
    LaunchedEffect(feeds) {
        if (feeds.isEmpty()) { ads = emptyList(); return@LaunchedEffect }
        val per = HashMap<String, List<AdLoader.Loaded>>()
        fun publish() { ads = feeds.flatMap { per[it].orEmpty() } }
        for (f in feeds) per[f] = AdLoader.cached(ctx, f, feeds)
        publish()
        for (f in feeds) AdLoader.refresh(ctx, f, feeds)?.let { per[f] = it; publish() }
    }
    return remember(ads, tile) { AdFeed.forTile(ads, tile) { it.item.tile } }
}

/**
 * پنل تبلیغاتی ثابت پایین صفحهٔ اول. بدون تبلیغ هیچ‌چیز نشان نمی‌دهد.
 * بنر ۱۲۸۰×۲۰۰ پیکسل (نسبت ۶٫۴ به ۱) پیشنهاد می‌شود. چند تبلیغ هر ۱۰ ثانیه جابه‌جا می‌شوند.
 */
@Composable
fun AdPanel(adsFeed: String = "", publicAds: Boolean = false) {
    val ads = rememberAds(adsFeed, publicAds, "")
    AdBanner(ads, withDivider = true)
}

/** تبلیغ انحصاری یک کاشی؛ بالای صفحهٔ همان کاشی. فقط تبلیغ‌هایی که tile آن‌ها برابر [tile] است. */
@Composable
fun TileAd(tile: String, adsFeed: String = "", publicAds: Boolean = false) {
    val ads = rememberAds(adsFeed, publicAds, tile)
    AdBanner(ads, withDivider = false)
}

@Composable
private fun AdBanner(ads: List<AdLoader.Loaded>, withDivider: Boolean) {
    val ctx = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(ads) {
        index = 0
        while (ads.size > 1) {
            delay(10_000)
            index = (index + 1) % ads.size
        }
    }
    if (ads.isEmpty()) return
    val ad = ads[index.coerceIn(0, ads.lastIndex)]
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        if (withDivider) HorizontalDivider(color = Pal.hairline)
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), onClick = { openUrl(ctx, ad.item.linkUrl) }) {
            Box {
                Image(
                    bitmap = ad.bitmap.asImageBitmap(),
                    contentDescription = ad.item.title.ifBlank { PANEL_LABEL },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(6.4f)
                )
                // برچسب پنل (PANEL_LABEL) بالای تصویر
                Text(
                    PANEL_LABEL, style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
