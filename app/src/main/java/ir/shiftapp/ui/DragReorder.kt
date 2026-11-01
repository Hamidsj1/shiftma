package ir.shiftapp.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import ir.shiftapp.core.HomeLayout
import kotlinx.coroutines.isActive

/**
 * حالت کشیدن و رها کردن برای چیدمان صفحهٔ اول. همهٔ مختصات در «ریشهٔ پنجره» حساب می‌شود (positionInRoot زنده)،
 * پس با اسکرول صفحه و جابه‌جایی گروه والد هم درست می‌ماند. ترتیب موقت در [order] است و فقط هنگام رها کردن ذخیره می‌شود.
 */
@Stable
internal class DragReorder {
    var activeId by mutableStateOf<String?>(null); private set
    var order by mutableStateOf<List<String>?>(null); private set
    var finger by mutableStateOf(Offset.Zero); private set
    private var tick by mutableStateOf(0)

    /** ReorderLayout این دو را پر می‌کند: مختصات خود لایه‌چین و مستطیل هر خانه (نسبت به لایه‌چین، با درنظرگرفتن RTL). */
    var container: LayoutCoordinates? = null
    var slots: List<Rect> = emptyList()

    private var grab = Offset.Zero
    private var size = Size.Zero

    private fun slotRoot(i: Int): Offset? {
        val c = container?.takeIf { it.isAttached } ?: return null
        val r = slots.getOrNull(i) ?: return null
        return c.positionInRoot() + r.topLeft
    }

    fun start(base: List<String>, id: String, fingerRoot: Offset) {
        val idx = base.indexOf(id)
        val root = slotRoot(idx) ?: return
        activeId = id; order = base; finger = fingerRoot
        grab = fingerRoot - root
        size = slots[idx].size
    }

    fun move(fingerRoot: Offset) {
        if (activeId == null) return
        finger = fingerRoot
        hit()
    }

    /** بعد از هر اسکرول خودکار صدا زده می‌شود: کاشی باید زیر انگشت بماند و جای هدف دوباره سنجیده شود. */
    fun refresh() { tick++; hit() }

    private fun hit() {
        val cur = order ?: return
        val id = activeId ?: return
        val center = finger - grab + Offset(size.width / 2f, size.height / 2f)
        val from = cur.indexOf(id)
        for (i in cur.indices) {
            if (i == from) continue
            val r = slotRoot(i) ?: continue
            val w = slots[i].width
            val h = slots[i].height
            if (center.x >= r.x && center.x <= r.x + w && center.y >= r.y && center.y <= r.y + h) {
                order = HomeLayout.moveTo(cur, id, i)
                return
            }
        }
    }

    /** جابه‌جایی لازم برای اینکه کاشی در حال کشیدن زیر انگشت بماند (کاشی‌های دیگر صفر). */
    fun translation(id: String): Offset {
        tick   // فقط برای دنبال کردن اسکرول خودکار
        if (id != activeId) return Offset.Zero
        val i = order?.indexOf(id) ?: return Offset.Zero
        val r = slotRoot(i) ?: return Offset.Zero
        return finger - grab - r
    }

    /** ترتیب نهایی را برمی‌گرداند (اگر کشیدنی در کار بود) و حالت را پاک می‌کند. */
    fun end(): List<String>? {
        val o = order
        activeId = null; order = null
        return o
    }
}

/** ترتیبی که باید نمایش داده شود: ترتیب موقت هنگام کشیدن، بعد ترتیب رهاشده تا ذخیره‌سازی برسد، بعد ترتیب ذخیره‌شده. */
@Composable
internal fun rememberShownOrder(dr: DragReorder, ids: List<String>): Pair<List<String>, (List<String>) -> Unit> {
    var pending by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(ids) { pending = null }
    return (dr.order ?: pending ?: ids) to { o: List<String> -> pending = o }
}

private class CoordsHolder { var c: LayoutCoordinates? = null }

/**
 * لمس طولانی و کشیدن. [base] ترتیب فعلی را می‌دهد و [onDrop] ترتیب نهایی را؛ مختصات انگشت از LayoutCoordinates زنده
 * گرفته می‌شود تا حتی وقتی خودِ این گره همراه والدش حرکت می‌کند (کشیدن گروه از عنوانش) درست باشد.
 */
internal fun Modifier.dragHandle(dr: DragReorder, id: String, base: () -> List<String>, onDrop: (List<String>) -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }
    val baseNow by rememberUpdatedState(base)
    val dropNow by rememberUpdatedState(onDrop)
    this.onGloballyPositioned { holder.c = it }.pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { off ->
                val c = holder.c
                if (c != null && c.isAttached) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dr.start(baseNow(), id, c.positionInRoot() + off)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                val c = holder.c
                if (c != null && c.isAttached) dr.move(c.positionInRoot() + change.position)
            },
            onDragEnd = { dr.end()?.let { dropNow(it) } },
            onDragCancel = { dr.end() }
        )
    }
}

/** کاشی یا گروهِ در حال کشیدن را زیر انگشت می‌برد، کمی بزرگ و روی بقیه می‌کشد. */
internal fun Modifier.reorderItem(dr: DragReorder, id: String): Modifier {
    val active = dr.activeId == id
    return this.zIndex(if (active) 1f else 0f).graphicsLayer {
        val t = dr.translation(id)
        translationX = t.x
        translationY = t.y
        if (active) { scaleX = 1.03f; scaleY = 1.03f; alpha = 0.94f }
    }
}

/** اسکرول خودکار صفحه وقتی انگشت هنگام کشیدن نزدیک بالا یا پایین صفحه است. */
@Composable
internal fun AutoScrollWhileDragging(dr: DragReorder, scroll: ScrollState) {
    val density = LocalDensity.current.density
    val screenH = LocalConfiguration.current.screenHeightDp * density
    val active = dr.activeId
    LaunchedEffect(active) {
        if (active == null) return@LaunchedEffect
        val edge = 120f * density
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { }
            val y = dr.finger.y
            val d = when {
                y < edge -> -14f
                y > screenH - edge -> 14f
                else -> 0f
            }
            if (d != 0f && scroll.scrollBy(d) != 0f) dr.refresh()
        }
    }
}

/**
 * چیدمان ستونی یا شبکه‌ای (ستون‌ها = [columns]) که مستطیل هر خانه را به [dr] می‌دهد.
 * در حالت چند ستونه همهٔ کاشی‌های یک ردیف هم‌قد می‌شوند. فرزندها باید با key(id) ساخته شوند تا هنگام جابه‌جایی گره‌هایشان بمانند.
 */
@Composable
internal fun ReorderLayout(columns: Int, spacing: Dp, dr: DragReorder, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.onGloballyPositioned { dr.container = it }) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val w = constraints.maxWidth
        val cellW = ((w - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val placed = ArrayList<Triple<Placeable, Int, Int>>()
        val rects = ArrayList<Rect>()
        var y = 0
        measurables.chunked(columns).forEach { row ->
            val ps: List<Placeable>
            val rowH: Int
            if (columns > 1) {
                rowH = row.maxOf { it.minIntrinsicHeight(cellW) }
                ps = row.map { it.measure(Constraints.fixed(cellW, rowH)) }
            } else {
                ps = row.map { it.measure(Constraints(minWidth = cellW, maxWidth = cellW)) }
                rowH = ps.maxOf { it.height }
            }
            ps.forEachIndexed { c, p ->
                val x = c * (cellW + gap)
                placed.add(Triple(p, x, y))
                val left = if (rtl) w - x - cellW else x
                rects.add(Rect(left.toFloat(), y.toFloat(), (left + cellW).toFloat(), (y + rowH).toFloat()))
            }
            y += rowH + gap
        }
        dr.slots = rects
        val total = if (placed.isEmpty()) 0 else y - gap
        layout(w, total) { placed.forEach { (p, x, yy) -> p.placeRelative(x, yy) } }
    }
}
