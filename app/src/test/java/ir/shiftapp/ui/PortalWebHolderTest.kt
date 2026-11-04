package ir.shiftapp.ui

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** نگه‌داشتن موقت WebView سامانه: برگشت به همان سامانه همان نمونه (با ورود) را بدهد، نه یک صفحهٔ نو. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortalWebHolderTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() { PortalWebHolder.clear() }

    @Test fun reopenWithinIdleReusesSameWebView() {
        val a = PortalWebHolder.obtain(ctx, "nicico", "https://x.ir/", now = 1_000)
        assertFalse(a.reused)
        PortalWebHolder.release(ctx, "nicico", a.web, now = 2_000)
        val b = PortalWebHolder.obtain(ctx, "nicico", "https://x.ir/", now = 3_000)
        assertTrue(b.reused)
        assertSame(a.web, b.web)
    }

    @Test fun differentSiteOrChangedUrlOrExpiredGetsNewWebView() {
        val a = PortalWebHolder.obtain(ctx, "nicico", "https://x.ir/", now = 0)
        PortalWebHolder.release(ctx, "nicico", a.web, now = 0)
        assertFalse(PortalWebHolder.obtain(ctx, "rasa", "https://r.ir/", now = 10).reused)      // سامانهٔ دیگر
        assertFalse(PortalWebHolder.obtain(ctx, "nicico", "https://y.ir/", now = 10).reused)    // آدرس عوض شده
        val b = PortalWebHolder.obtain(ctx, "k", "https://k.ir/", now = 0)
        PortalWebHolder.release(ctx, "k", b.web, now = 0)
        assertFalse(PortalWebHolder.obtain(ctx, "k", "https://k.ir/", now = PortalWebHolder.IDLE_MS + 5).reused)   // گذشتن زمان مجاز
    }

    @Test fun keepsAtMostMaxSites() {
        val ws = (1..3).map { i -> PortalWebHolder.obtain(ctx, "s$i", "https://s$i.ir/", now = 0).also { PortalWebHolder.release(ctx, "s$i", it.web, now = 0) } }
        assertFalse(PortalWebHolder.obtain(ctx, "s1", "https://s1.ir/", now = 1).reused)          // قدیمی‌ترین کنار رفته
        assertTrue(PortalWebHolder.obtain(ctx, "s3", "https://s3.ir/", now = 1).reused)
        assertEquals(3, ws.size)
    }
}
