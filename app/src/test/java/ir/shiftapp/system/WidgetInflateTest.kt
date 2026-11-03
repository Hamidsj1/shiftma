package ir.shiftapp.system

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import ir.shiftapp.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ویجت‌های شیفت روی گوشی «نمی‌توان ابزارک را بارگذاری کرد» نشان می‌دادند. این تست لایه‌ها را با خود فریم‌ورک اندروید
 * (RemoteViews.apply) می‌سازد و همان عملیات ShiftWidget را روی آن اجرا می‌کند؛ هر خطای ساخت/اعمال اینجا با جزئیات دیده می‌شود.
 * ویجت لغات (که روی گوشی کار می‌کند) به‌عنوان شاهد سالم است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetInflateTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun applyOk(rv: RemoteViews): View = rv.apply(ctx, FrameLayout(ctx)).also { assertNotNull(it) }

    @Test fun englishWidgetInflates() { applyOk(RemoteViews(ctx.packageName, R.layout.widget_english)) }

    @Test fun shiftLayoutInflates() { applyOk(RemoteViews(ctx.packageName, R.layout.widget_shift)) }
    @Test fun stripLayoutInflates() { applyOk(RemoteViews(ctx.packageName, R.layout.widget_shift_strip)) }
    @Test fun fallbackLayoutInflates() { applyOk(RemoteViews(ctx.packageName, R.layout.widget_fallback)) }

    /** همان کنش‌هایی که ShiftWidget.update روی هر دو لایه می‌زند. */
    private fun shiftActions(layout: Int, full: Boolean): RemoteViews = RemoteViews(ctx.packageName, layout).apply {
        setViewVisibility(R.id.w_grp_box, View.VISIBLE)
        setTextViewText(R.id.w_grp, "A")
        setInt(R.id.w_grp_bg, "setColorFilter", 0xFF4FB3E8.toInt())
        setInt(R.id.w_dot, "setColorFilter", 0xFF2E9E6B.toInt())
        setTextViewText(R.id.w_name, "شیفت صبح")
        setViewVisibility(R.id.w_bar, View.VISIBLE)
        setImageViewBitmap(R.id.w_bar, ShiftWidget.barBitmap(ctx, 0.4))
        setTextViewText(R.id.w_remain, "۴:۳۷ مانده")
        val bg = intArrayOf(R.id.w_d0_bg, R.id.w_d1_bg, R.id.w_d2_bg, R.id.w_d3_bg, R.id.w_d4_bg, R.id.w_d5_bg, R.id.w_d6_bg)
        val ring = intArrayOf(R.id.w_d0_ring, R.id.w_d1_ring, R.id.w_d2_ring, R.id.w_d3_ring, R.id.w_d4_ring, R.id.w_d5_ring, R.id.w_d6_ring)
        val lbl = intArrayOf(R.id.w_d0_lbl, R.id.w_d1_lbl, R.id.w_d2_lbl, R.id.w_d3_lbl, R.id.w_d4_lbl, R.id.w_d5_lbl, R.id.w_d6_lbl)
        val num = intArrayOf(R.id.w_d0_num, R.id.w_d1_num, R.id.w_d2_num, R.id.w_d3_num, R.id.w_d4_num, R.id.w_d5_num, R.id.w_d6_num)
        for (i in 0 until 7) {
            setTextViewText(lbl[i], "ش"); setTextViewText(num[i], "۱")
            setInt(bg[i], "setColorFilter", 0xFF3F36C9.toInt()); setViewVisibility(ring[i], View.VISIBLE)
        }
        if (full) {
            setViewVisibility(R.id.w_chip1, View.VISIBLE)
            setImageViewResource(R.id.w_chip1_ico, R.drawable.ic_note)
            setTextViewText(R.id.w_chip1_txt, "آلارم · ۰۵:۳۰")
            setViewVisibility(R.id.w_more, View.GONE)
        }
    }

    @Test fun shiftActionsApply() { applyOk(shiftActions(R.layout.widget_shift, true)) }
    @Test fun stripActionsApply() { applyOk(shiftActions(R.layout.widget_shift_strip, false)) }
}
