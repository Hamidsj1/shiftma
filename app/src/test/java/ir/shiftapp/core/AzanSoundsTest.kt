package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test

class AzanSoundsTest {
    @Test fun kinds() {
        assertEquals(AzanSounds.Kind.DEFAULT, AzanSounds.kind(""))
        assertEquals(AzanSounds.Kind.BUILTIN, AzanSounds.kind(AzanSounds.BUILTIN))
        assertEquals(AzanSounds.Kind.FILE, AzanSounds.kind(AzanSounds.fileToken("a.mp3")))
        assertEquals(AzanSounds.Kind.SYSTEM, AzanSounds.kind("content://media/internal/audio/media/12"))
    }

    @Test fun fileTokenRejectsUnsafeNames() {
        assertEquals("azan.mp3", AzanSounds.fileName(AzanSounds.fileToken("azan.mp3")))
        assertNull(AzanSounds.fileName("file:../secret.db"))
        assertNull(AzanSounds.fileName("file:a/b.mp3"))
        assertNull(AzanSounds.fileName("file:.hidden"))
        assertNull(AzanSounds.fileName("file:x.mp3.part"))
        assertNull(AzanSounds.fileName("file:"))
        assertNull(AzanSounds.fileName("builtin:x"))
    }

    @Test fun namesFromUrls() {
        assertEquals("moazen.mp3", AzanSounds.nameFromUrl("https://example.com/files/moazen.mp3?dl=1#x"))
        assertEquals("dl.mp3", AzanSounds.nameFromUrl("https://example.com/dl/?x=1"))
        assertEquals("azan.mp3", AzanSounds.sanitize(""))
        assertEquals("azan.mp3", AzanSounds.sanitize("___"))
        assertEquals("track.mp3", AzanSounds.nameFromUrl("https://example.com/track"))          // بدون پسوند صوتی
        assertEquals("a_b.ogg", AzanSounds.nameFromUrl("https://example.com/a%20b.ogg"))
        assertTrue(AzanSounds.isSafeName(AzanSounds.nameFromUrl("https://x.y/../../etc/passwd")))
        assertTrue(AzanSounds.nameFromUrl("https://x.y/" + "a".repeat(200) + ".mp3").length <= 60)
    }

    @Test fun httpsOnly() {
        assertTrue(AzanSounds.isHttps("https://example.com/a.mp3"))
        assertTrue(AzanSounds.isHttps("  HTTPS://example.com/a.mp3 "))
        assertFalse(AzanSounds.isHttps("http://example.com/a.mp3"))
        assertFalse(AzanSounds.isHttps("file:///sdcard/a.mp3"))
        assertFalse(AzanSounds.isHttps("https://"))
    }

    @Test fun displayName() {
        assertEquals("rahim moazen", AzanSounds.display("rahim_moazen.mp3"))
    }

    @Test fun audioUrlDetection() {
        assertTrue(AzanSounds.looksLikeAudioUrl("https://dl.example.ir/azan/moazen.MP3"))
        assertTrue(AzanSounds.looksLikeAudioUrl("https://example.com/a.mp3?token=1#t=5"))
        assertTrue(AzanSounds.looksLikeAudioUrl("http://example.com/a.ogg"))
        assertFalse(AzanSounds.looksLikeAudioUrl("https://example.com/page.html?file=a.mp3"))
        assertFalse(AzanSounds.looksLikeAudioUrl("https://example.com/azan"))
        assertFalse(AzanSounds.looksLikeAudioUrl("intent://x/a.mp3"))
        assertFalse(AzanSounds.looksLikeAudioUrl("file:///sdcard/a.mp3"))
    }

    @Test fun browserTargets() {
        assertEquals("https://example.com/a", AzanSounds.browserTarget("https://example.com/a"))
        assertEquals("https://masjed.ir", AzanSounds.browserTarget(" masjed.ir "))
        assertEquals("https://www.google.com/search?q=%D8%A7%D8%B0%D8%A7%D9%86+mp3", AzanSounds.browserTarget("اذان mp3"))
        assertEquals("https://www.bing.com/search?q=azan+mp3", AzanSounds.browserTarget("azan mp3", "bing"))
        assertEquals("https://www.google.com/", AzanSounds.browserTarget(""))
        assertTrue(AzanSounds.browserTarget("اذان.mp3").startsWith("https://www.google.com/search?q="))   // فارسی؛ نام سایت نیست
    }
}
