package ir.shiftapp.core

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class AzanCatalogTest {
    private val commons = """
    {"batchcomplete":"","query":{"pages":{
      "111":{"pageid":111,"ns":6,"title":"File:Adhan Makkah.ogg","index":2,"imageinfo":[{"size":2400000,"url":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Adhan_Makkah.ogg","mime":"application/ogg","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]},
      "222":{"pageid":222,"ns":6,"title":"File:Azan Iran.mp3","index":1,"imageinfo":[{"size":900000,"url":"https://upload.wikimedia.org/wikipedia/commons/c/cd/Azan_Iran.mp3","mime":"audio/mpeg","extmetadata":{}}]},
      "333":{"pageid":333,"ns":6,"title":"File:Huge.flac","index":3,"imageinfo":[{"size":99000000,"url":"https://upload.wikimedia.org/x/Huge.flac","mime":"audio/flac"}]},
      "444":{"pageid":444,"ns":6,"title":"File:Not audio.pdf","index":4,"imageinfo":[{"size":50000,"url":"https://upload.wikimedia.org/x/a.pdf","mime":"application/pdf"}]},
      "555":{"pageid":555,"ns":6,"title":"File:Insecure.ogg","index":5,"imageinfo":[{"size":50000,"url":"http://example.com/a.ogg","mime":"audio/ogg"}]}
    }}}""".trimIndent()

    @Test fun commonsKeepsOnlySafeAudioInRelevanceOrder() {
        val r = AzanCatalog.parseCommons(commons)
        assertEquals(listOf("Azan Iran", "Adhan Makkah"), r.map { it.title })
        assertEquals("CC BY-SA 4.0", r[1].license); assertNull(r[0].license)
        assertTrue(r.all { it.url.startsWith("https://") && it.source == AzanCatalog.SRC_COMMONS && !it.isItem })
    }

    @Test fun emptyOrBrokenCommonsPages() {
        assertTrue(AzanCatalog.parseCommons("""{"batchcomplete":""}""").isEmpty())
    }

    @Test fun archiveSearchGivesItemsAndDropsUnsafeIds() {
        val json = """{"response":{"numFound":3,"docs":[
          {"identifier":"azan-moazen-zadeh","title":"اذان موذن‌زاده"},
          {"identifier":"bad id/../x","title":"x"},
          {"identifier":"adhan_collection","title":["Adhan collection","other"]}]}}"""
        val r = AzanCatalog.parseArchiveSearch(json)
        assertEquals(listOf("azan-moazen-zadeh", "adhan_collection"), r.map { it.itemId })
        assertEquals("اذان موذن‌زاده", r[0].title); assertEquals("Adhan collection", r[1].title)
        assertTrue(r.all { it.isItem && it.url.isBlank() })
    }

    @Test fun archiveFilesPickAudioWithinSizeAndDedupe() {
        val json = """{"files":[
          {"name":"azan.mp3","format":"VBR MP3","size":"1500000","source":"original"},
          {"name":"azan.ogg","format":"Ogg Vorbis","size":"1200000","source":"derivative"},
          {"name":"cover.jpg","format":"JPEG","size":"50000"},
          {"name":"item_meta.xml","format":"Metadata","size":"900"},
          {"name":"big.wav","format":"WAVE","size":"90000000"},
          {"name":"sub dir/part 2.mp3","format":"VBR MP3","size":"700000"},
          {"name":"tiny.mp3","format":"VBR MP3","size":"500"}]}"""
        val r = AzanCatalog.parseArchiveFiles("azan-item", json)
        assertEquals(listOf("azan", "part 2"), r.map { it.title })
        assertEquals("https://archive.org/download/azan-item/azan.mp3", r[0].url)
        assertEquals("https://archive.org/download/azan-item/sub%20dir/part%202.mp3", r[1].url)
        assertEquals("sub dir/part 2.mp3", URLDecoder.decode(r[1].url.substringAfter("azan-item/"), "UTF-8"))
        assertTrue(AzanCatalog.parseArchiveFiles("bad id", json).isEmpty())
    }

    @Test fun urlsAreEncodedAndHttps() {
        val c = AzanCatalog.commonsUrl("اذان موذن‌زاده")
        assertTrue(c.startsWith("https://commons.wikimedia.org/w/api.php?"))
        assertFalse(c.contains(" ")); assertFalse(c.contains("|")); assertTrue(c.contains("filetype%3Aaudio"))
        val a = AzanCatalog.archiveSearchUrl("adhan")
        assertTrue(a.startsWith("https://archive.org/advancedsearch.php?")); assertTrue(a.contains("output=json")); assertTrue(a.contains("mediatype%3Aaudio"))
        assertEquals("https://archive.org/metadata/x", AzanCatalog.archiveMetaUrl("x"))
    }
}
