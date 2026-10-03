package it.faunavia.exploration

import it.faunavia.domain.AppClock
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WikipediaPhotoTest {
    private val now = Instant.parse("2026-10-03T14:35:42Z")
    private class Cache : SpeciesMetadataCache {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun save(key: String, normalized: String) { values[key] = normalized }
    }
    private fun response(url: String): String = when {
        url.contains("wbsearchentities") -> """{"search":[{"id":"Q1"}]}"""
        url.contains("wbgetentities") -> """{"entities":{"Q1":{"claims":{"P225":[{"rank":"normal","mainsnak":{"datavalue":{"value":"Turdus merula"}}}]},"sitelinks":{"itwiki":{"title":"Turdus merula"},"enwiki":{"title":"Common blackbird"}}}}}"""
        url.contains("wikipedia.org") -> """{"query":{"pages":{"1":{"title":"Turdus merula","pageimage":"Blackbird.jpg","pageprops":{"wikibase_item":"Q1"}}}}}"""
        else -> """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://thumb.wikimedia.org/blackbird.jpg","thumbmime":"image/jpeg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Blackbird.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"},"Artist":{"value":"<a>Bird photographer</a>"},"DateTimeOriginal":{"value":"2020"}}}]}}}}"""
    }
    private fun lookup(cache: Cache = Cache(), clock: AppClock = object : AppClock { override fun nowEpochMillis() = now.toEpochMilli() },
        reply: (String) -> String = ::response) = RemoteSpeciesMetadata(object : SpeciesMetadataHttp {
        override fun get(url: String) = reply(url)
    }, cache, clock)

    @Test fun exactItalianWikipediaImageHasReusableRightsCreditsAndOriginalDate() = runBlocking {
        val photo = requireNotNull(lookup().photo("gbif:1", "Turdus merula Linnaeus, 1758").value)
        assertEquals("https://it.wikipedia.org/wiki/Turdus_merula", photo.articleUrl)
        assertEquals("https://thumb.wikimedia.org/blackbird.jpg", photo.imageUrl)
        assertEquals("Bird photographer · Wikimedia Commons", photo.source.attribution)
        assertEquals("CC BY-SA 4.0", photo.source.license)
        assertEquals("2020", photo.date)
        assertEquals(now, photo.source.retrievedAt)
        assertTrue(photo.source.version.contains("PageImages"))
    }
    @Test fun absentItalianImageFallsBackToVerifiedEnglishArticle() = runBlocking {
        val photo = lookup(reply = { url -> if (url.contains("it.wikipedia.org"))
            """{"query":{"pages":{"1":{"pageprops":{"wikibase_item":"Q1"}}}}}"""
            else if (url.contains("en.wikipedia.org")) response(url).replace("Turdus merula", "Common blackbird") else response(url) })
            .photo("gbif:1", "Turdus merula").value!!
        assertEquals("https://en.wikipedia.org/wiki/Common_blackbird", photo.articleUrl)
    }
    @Test fun scientificMismatchAmbiguityAndWrongWikipediaIdentityCannotSupplyAPhoto() = runBlocking {
        val wrong = lookup(reply = { url -> if (url.contains("wbgetentities")) response(url).replace("Turdus merula", "Turdus iliacus") else response(url) })
        assertNull(wrong.photo("gbif:1", "Turdus merula").value)
        val article = lookup(reply = { url -> if (url.contains("wikipedia.org")) response(url).replace("Q1", "Q2") else response(url) })
        assertNull(article.photo("gbif:1", "Turdus merula").value)
        val ambiguous = lookup(reply = { url -> if (url.contains("wbgetentities"))
            """{"entities":{"Q1":{"claims":{"P225":[{"mainsnak":{"datavalue":{"value":"Turdus merula"}}}]}},"Q2":{"claims":{"P225":[{"mainsnak":{"datavalue":{"value":"Turdus merula"}}}]}}}}""" else response(url) })
        assertNull(ambiguous.photo("gbif:1", "Turdus merula").value)
    }
    @Test fun unsupportedLicenseMissingAuthorAndUnsafeMediaHaveAnExplicitFallback() = runBlocking {
        for ((original, replacement) in listOf("CC BY-SA 4.0" to "CC BY-NC 4.0", "<a>Bird photographer</a>" to "",
            "https://thumb.wikimedia.org/blackbird.jpg" to "https://evil.test/blackbird.jpg")) {
            assertNull(lookup(reply = { response(it).replace(original, replacement) }).photo("gbif:1", "Turdus merula").value)
        }
        assertFalse(safeWikipediaArticle("https://it.wikipedia.org.evil.test/wiki/Turdus_merula"))
        assertFalse(safeWikipediaArticle("https://user@it.wikipedia.org/wiki/Turdus_merula"))
    }
    @Test fun cacheAvoidsCallsAndOfflineStalePhotoRetainsItsProvenance() = runBlocking {
        var epoch = now.toEpochMilli(); var offline = false; var calls = 0
        val cache = Cache()
        val service = lookup(cache, object : AppClock { override fun nowEpochMillis() = epoch }, { calls++; if (offline) throw IOException("offline"); response(it) })
        val first = service.photo("gbif:1", "Turdus merula")
        assertEquals(4, calls)
        assertEquals(first, service.photo("gbif:1", "Turdus merula")); assertEquals(4, calls)
        epoch += Duration.ofDays(31).toMillis(); offline = true
        val stale = service.photo("gbif:1", "Turdus merula")
        assertTrue(stale.stale); assertNotNull(stale.warning); assertEquals(first.value, stale.value)
        assertTrue(cache.values.values.none { it.contains("\"entities\"") || it.contains("\"imageinfo\"") })
    }
    @Test fun missingPhotoIsCachedAndExplicitRefreshCanRecover() = runBlocking {
        var available = false; var calls = 0
        val service = lookup(reply = { calls++; if (!available && it.contains("wbsearchentities")) """{"search":[]}""" else response(it) })
        assertNull(service.photo("gbif:1", "Turdus merula").value)
        assertNull(service.photo("gbif:1", "Turdus merula").value); assertEquals(1, calls)
        available = true
        assertNotNull(service.photo("gbif:1", "Turdus merula", true).value)
    }
    @Test fun unsafeCachedPhotoIsRevalidatedAndCannotBeDisplayed() = runBlocking {
        val cache = Cache()
        val service = lookup(cache)
        assertNotNull(service.photo("gbif:1", "Turdus merula").value)
        val key = cache.values.keys.single()
        cache.values[key] = cache.values.getValue(key).replace("https://thumb.wikimedia.org", "https://evil.test")
        val offline = lookup(cache, reply = { throw IOException("offline") }).photo("gbif:1", "Turdus merula")
        assertNull(offline.value); assertNotNull(offline.warning)
    }
}
