package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class ImageCompressTest {
    @Test fun fitSizeKeepsRatio() {
        assertEquals(2048 to 1536, ImageCompress.fitSize(4000, 3000, 2048))
        assertEquals(1536 to 2048, ImageCompress.fitSize(3000, 4000, 2048))
        assertEquals(800 to 600, ImageCompress.fitSize(800, 600, 2048))
    }

    @Test fun sampleSizeNeverGoesBelowTarget() {
        assertEquals(1, ImageCompress.sampleSize(4000, 3000, 2048))
        assertEquals(2, ImageCompress.sampleSize(8000, 6000, 2048))
        assertEquals(4, ImageCompress.sampleSize(8000, 6000, 1024))
    }

    @Test fun bestQualityBinarySearch() {
        // حجم = ۱۰۰۰ × کیفیت
        assertEquals(70, ImageCompress.bestQuality(70_000) { it * 1000L })
        assertEquals(95, ImageCompress.bestQuality(500_000) { it * 1000L })
        assertNull(ImageCompress.bestQuality(10_000) { it * 1000L })
    }

    @Test fun formatAndPercent() {
        assertEquals("۵۰۰ بایت", ImageCompress.formatSize(500))
        assertEquals("۲ کیلوبایت", ImageCompress.formatSize(2048))
        assertEquals("۱٫۵ مگابایت", ImageCompress.formatSize(1_572_864))
        assertEquals(90, ImageCompress.savedPercent(1000, 100))
        assertEquals(0, ImageCompress.savedPercent(100, 200))
        assertEquals(0, ImageCompress.savedPercent(0, 0))
    }

    @Test fun parseTarget() {
        assertEquals(200, ImageCompress.parseTargetKb("۲۰۰"))
        assertEquals(200, ImageCompress.parseTargetKb("200"))
        assertNull(ImageCompress.parseTargetKb("5"))
        assertNull(ImageCompress.parseTargetKb(""))
    }

    @Test fun names() {
        assertEquals("IMG_1_small.jpg", ImageCompress.outName("IMG_1.png", "jpg"))
        assertEquals("image_small.jpg", ImageCompress.outName(null, "jpg"))
        assertEquals("a_b_small.webp", ImageCompress.outName("a/b.jpeg", "webp"))
        assertEquals("x_small_2.jpg", ImageCompress.uniqueName("x_small.jpg", setOf("x_small.jpg")))
        assertEquals("x_small.jpg", ImageCompress.uniqueName("x_small.jpg", emptySet()))
        assertEquals("png", ImageCompress.extForMime("image/PNG"))
        assertEquals("jpg", ImageCompress.extForMime(null))
    }
}
