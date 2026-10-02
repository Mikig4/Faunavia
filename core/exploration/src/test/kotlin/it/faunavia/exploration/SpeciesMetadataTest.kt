package it.faunavia.exploration

import it.faunavia.domain.AppClock
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpeciesMetadataTest {
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val record = """{"key":123,"usageKey":123,"canonicalName":"Sciurus vulgaris","scientificName":"Sciurus vulgaris Linnaeus, 1758","kingdom":"Animalia","rank":"SPECIES","status":"ACCEPTED","matchType":"EXACT"}"""
    private class Cache : SpeciesMetadataCache {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun save(key: String, normalized: String) { values[key] = normalized }
    }
    private fun service(response: (String) -> String, cache: Cache = Cache(), clock: AppClock = object : AppClock {
        override fun nowEpochMillis() = now.toEpochMilli()
    }) = RemoteSpeciesMetadata(object : SpeciesMetadataHttp { override fun get(url: String) = response(url) }, cache, clock)

    private fun response(url: String): String = when {
        url.contains("vernacularNames") -> """{"results":[{"language":"eng","vernacularName":"Red squirrel"},{"language":"ita","vernacularName":"Scoiattolo comune"}]}"""
        url.contains("api.gbif.org") -> record
        url.contains("wbsearchentities") -> """{"search":[{"id":"Q1"}]}"""
        url.contains("wbgetentities") -> """{"entities":{"Q1":{"claims":{"P225":[{"rank":"normal","mainsnak":{"datavalue":{"value":"Sciurus vulgaris"}}}],"P181":[{"rank":"normal","mainsnak":{"datavalue":{"value":"Range.png"}}}]}}}}"""
        else -> """{"query":{"pages":{"1":{"imageinfo":[{"thumburl":"https://thumb.wikimedia.org/map.png","thumbmime":"image/png","descriptionurl":"https://commons.wikimedia.org/wiki/File:Range.png","extmetadata":{"LicenseShortName":{"value":"CC BY-SA 4.0"},"Artist":{"value":"<a>Map author</a>"},"DateTimeOriginal":{"value":"2020"}}}]}}}}"""
    }

    @Test fun italianNameComesFromExactTaxonomyAndNeverFromTheForeignFallback() = runBlocking {
        val result = service(::response).name("gbif:123", "Sciurus vulgaris")
        val value = requireNotNull(result.value)
        assertEquals("Scoiattolo comune", value.commonName)
        assertEquals("123", value.gbifKey)
        assertEquals(now, requireNotNull(value.source).retrievedAt)
        val foreignOnly = service({ url -> if (url.contains("vernacularNames")) """{"results":[{"language":"eng","vernacularName":"Red squirrel"}]}""" else record })
        assertNull(foreignOnly.name("gbif:123", "Sciurus vulgaris").value!!.commonName)
    }

    @Test fun fuzzyOrHigherTaxonMatchCannotSupplyAnIdentityOrAnInventedCommonName() = runBlocking {
        val bad = service({ record.replace("EXACT", "FUZZY").replace("Sciurus vulgaris", "Sciurus other") })
        val result = bad.name("nnb:123", "Sciurus vulgaris")
        val value = requireNotNull(result.value)
        assertNull(value.gbifKey)
        assertNull(value.commonName)
    }

    @Test fun nonCuratedSpeciesGetsAnExactlyAssociatedLicensedRangeImageAndGlobalObservationLayer() = runBlocking {
        assertNull(PilotSpeciesPresentation.find("Sciurus vulgaris"))
        val result = service(::response).distribution("nnb:123", "Sciurus vulgaris")
        val value = requireNotNull(result.value)
        val illustration = requireNotNull(value.illustration)
        assertEquals("https://thumb.wikimedia.org/map.png", illustration.imageUrl)
        assertEquals("Map author · Wikimedia Commons", illustration.source.attribution)
        assertEquals("CC BY-SA 4.0", illustration.source.license)
        assertEquals("2020", illustration.date)
        assertEquals("123", value.gbifKey)
    }

    @Test fun wrongTaxonRangeAndUnsupportedMediaRightsFallBackToObservations() = runBlocking {
        val wrong = service({ url -> response(url).let { if (url.contains("wbgetentities")) it.replace("Sciurus vulgaris", "Sciurus other") else it } })
        assertNull(wrong.distribution("gbif:123", "Sciurus vulgaris").value!!.illustration)
        val restricted = service({ url -> response(url).replace("CC BY-SA 4.0", "All rights reserved") })
        val value = restricted.distribution("gbif:123", "Sciurus vulgaris").value!!
        assertNull(value.illustration)
        assertEquals("123", value.gbifKey)
        assertFalse(reusableMapLicense("CC BY-NC 4.0"))
        assertFalse(safeCommonsMedia("https://upload.wikimedia.org.evil.test/image.png"))
        assertFalse(safeCommonsMedia("https://thumb.wikimedia.org.evil.test/image.png"))
        assertTrue(safeCommonsMedia("https://upload.wikimedia.org/image.png"))
        assertFalse(safeCommonsMedia("http://upload.wikimedia.org/image.png"))
    }

    @Test fun normalizedCachePreventsRepeatedCallsAndOfflineStaleMetadataKeepsOriginalProvenance() = runBlocking {
        var epoch = now.toEpochMilli()
        var offline = false
        var requests = 0
        val cache = Cache()
        val lookup = service({ url -> requests++; if (offline) throw IOException("offline"); response(url) }, cache,
            object : AppClock { override fun nowEpochMillis() = epoch })
        val first = lookup.name("gbif:123", "Sciurus vulgaris")
        assertEquals(2, requests)
        assertEquals(first, lookup.name("gbif:123", "Sciurus vulgaris"))
        assertEquals(2, requests)
        epoch += Duration.ofDays(31).toMillis(); offline = true
        val stale = lookup.name("gbif:123", "Sciurus vulgaris")
        assertTrue(stale.stale)
        assertNotNull(stale.warning)
        assertEquals(first.value!!.source, stale.value!!.source)
        assertTrue(cache.values.values.all { !it.contains("\"results\"") && !it.contains("Red squirrel") })
    }

    @Test fun globalMapTilesOnlyAcceptProviderKeysAndDeclareObservationFilters() {
        val url = gbifDistributionTiles("123")
        assertTrue(url.contains("taxonKey=123"))
        assertTrue(url.contains("basisOfRecord=HUMAN_OBSERVATION"))
        assertTrue(url.contains("hasGeospatialIssue=false"))
        assertThrows(IllegalArgumentException::class.java) { gbifDistributionTiles("123&taxonKey=1") }
    }
}
