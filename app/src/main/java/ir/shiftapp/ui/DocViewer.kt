package ir.shiftapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.shiftapp.R
import ir.shiftapp.docs.DocKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** نمایش تمام‌صفحهٔ فایل: عکس (با زوم) و PDF داخل برنامه؛ فایل نمایشی با برنامهٔ دیگر. */
@Composable
fun DocViewerDialog(title: String, file: File, kind: DocKind, mime: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BarIconButton(R.drawable.ic_close, "بستن", onClose)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BarIconButton(R.drawable.ic_open_new, "باز کردن با برنامهٔ دیگر", { openDocExternally(ctx, file, mime) })
                    BarIconButton(R.drawable.ic_share, "ارسال فایل", { shareDocFile(ctx, file, mime, title) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (kind) {
                        DocKind.IMAGE -> ImageViewer(file)
                        DocKind.PDF -> PdfViewer(file)
                        DocKind.SLIDES -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Ico(R.drawable.ic_note, size = 48.dp, tint = Pal.muted)
                            Text("فایل نمایشی را برنامهٔ دیگری مثل پاورپوینت یا WPS باز می‌کند.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            Button({ openDocExternally(ctx, file, mime) }) { Text("باز کردن") }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeScaled(file: File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) { 0f }
    if (degrees == 0f) return bmp
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
}

@Composable
private fun ImageViewer(file: File) {
    val bmp by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { decodeScaled(file, 2400)?.asImageBitmap() } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator() else Image(
            b, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )
    }
}

/** PdfRenderer فقط یک صفحه را هم‌زمان باز می‌کند؛ پس رندر صفحه‌ها پشت یک قفل صف می‌شود. */
private class PdfHolder(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    val pageCount: Int get() = renderer.pageCount
    private val mutex = Mutex()

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(index)
                try {
                    val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally { page.close() }
            } catch (e: Exception) { null }
        }
    }

    fun close() { try { renderer.close() } catch (e: Exception) { }; try { pfd.close() } catch (e: Exception) { } }

    companion object {
        fun open(file: File): PdfHolder? = try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try { PdfHolder(pfd, PdfRenderer(pfd)) } catch (e: Exception) { pfd.close(); null }
        } catch (e: Exception) { null }
    }
}

@Composable
private fun ZoomButton(text: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun PdfViewer(file: File) {
    val holder = remember(file) { PdfHolder.open(file) }
    DisposableEffect(holder) { onDispose { holder?.close() } }
    if (holder == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("این PDF باز نشد. «باز کردن با برنامهٔ دیگر» را بزنید.", textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val density = LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }            // ۱ تا ۳ برابر؛ بزرگ‌نمایی = رندر دوباره با عرض بیشتر + کشیدن افقی
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewW = maxWidth
        val widthPx = (with(density) { viewW.roundToPx() } * zoom).toInt().coerceIn(400, 2600)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                Modifier.width(viewW * zoom).fillMaxHeight(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0 until holder.pageCount).toList()) { i ->
                    val bmp by produceState<ImageBitmap?>(null, i, widthPx) { value = holder.render(i, widthPx)?.asImageBitmap() }
                    val b = bmp
                    if (b == null) Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else Image(b, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZoomButton("−", "کوچک‌نمایی") { zoom = (zoom - 0.5f).coerceAtLeast(1f) }
            ZoomButton("+", "بزرگ‌نمایی") { zoom = (zoom + 0.5f).coerceAtMost(3f) }
        }
    }
}

/**
 * پیش‌نمایش داخل کارت: عکس یا صفحهٔ اول PDF (بالای صفحه)؛ برای فایل نمایشی چیزی نشان نمی‌دهد.
 * با زدن روی کارت، نمایشگر تمام‌صفحه باز می‌شود.
 */
@Composable
internal fun FilePreview(file: File, kind: DocKind, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 220.dp) {
    if (kind == DocKind.SLIDES) return
    val bmp by produceState<ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            try {
                if (kind == DocKind.IMAGE) decodeScaled(file, 1400)?.asImageBitmap()
                else {
                    val h = PdfHolder.open(file)
                    try { h?.render(0, 1000)?.asImageBitmap() } finally { h?.close() }
                }
            } catch (e: Exception) { null }
        }
    }
    val b = bmp
    Box(
        modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.medium).background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (b == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        else Image(b, contentDescription = "پیش‌نمایش", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
    }
}
