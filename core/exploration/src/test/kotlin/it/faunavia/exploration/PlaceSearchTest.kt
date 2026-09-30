package it.faunavia.exploration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {
    private val milan = """{"place_id":101,"display_name":"Milano, Lombardia, Italia","addresstype":"city","lat":"45.4642","lon":"9.1900","boundingbox":["45.3","45.6","8.9","9.4"],"address":{"country_code":"it"}}"""
    private val rome = """{"place_id":102,"display_name":"Roma, Lazio, Italia","addresstype":"city","lat":"41.9","lon":"12.5","address":{"country_code":"it"}}"""

    @Test fun singleAmbiguousEmptyAndCachedResultsAreExplicit() = runBlocking {
        val cache = MemoryPlaceCache()
        var calls = 0
        val search = NominatimPlaceSearch(PlaceHttpClient { url ->
            calls++
            assertTrue(url.contains("format=jsonv2"))
            when {
                url.contains("Milano") -> PlaceHttpResponse(200, "[$milan]")
                url.contains("Roma") -> PlaceHttpResponse(200, "[$milan,$rome]")
                else -> PlaceHttpResponse(200, "[]")
            }
        }, cache, waitMillis = {})

        val single = search.search(" Milano ") as PlaceSearchResult.Matches
        assertEquals(1, single.places.size)
        assertEquals(PlaceKind.CITY, single.places.single().kind)
        assertEquals("it", single.places.single().countryCode)
        assertEquals(PlaceOrigin.NETWORK, single.origin)
        assertEquals(PlaceOrigin.CACHE, (search.search("Milano") as PlaceSearchResult.Matches).origin)
        assertEquals(2, (search.search("Roma") as PlaceSearchResult.Matches).places.size)
        assertTrue((search.search("Nessunposto") as PlaceSearchResult.Matches).places.isEmpty())
        assertEquals(3, calls)
    }

    @Test fun unavailableAndMalformedProviderRemainDistinctFromEmpty() = runBlocking {
        val cache = MemoryPlaceCache()
        val unavailable = NominatimPlaceSearch(PlaceHttpClient { PlaceHttpResponse(503, "") }, cache)
        val malformed = NominatimPlaceSearch(PlaceHttpClient { PlaceHttpResponse(200, "not json") }, cache)
        assertTrue(unavailable.search("Milano") is PlaceSearchResult.Unavailable)
        assertTrue(malformed.search("Milano") is PlaceSearchResult.Unavailable)
    }

    @Test fun requestsAreRateLimitedAndQueryIsEncoded() = runBlocking {
        var time = 10_000L
        val waits = mutableListOf<Long>()
        val urls = mutableListOf<String>()
        val search = NominatimPlaceSearch(PlaceHttpClient {
            urls += it
            PlaceHttpResponse(200, "[]")
        }, MemoryPlaceCache(), nowMillis = { time }, waitMillis = { delay -> waits += delay; time += delay })

        search.search("Milano, Italia")
        search.search("Roma Lazio")
        assertEquals(listOf(1_000L), waits)
        assertTrue(urls.first().contains("Milano%2C+Italia"))
    }

    @Test fun overlongQueryDoesNotReachPublicProvider() = runBlocking {
        var calls = 0
        val search = NominatimPlaceSearch(PlaceHttpClient { calls++; PlaceHttpResponse(200, "[]") }, MemoryPlaceCache())
        assertTrue((search.search("a".repeat(161)) as PlaceSearchResult.Matches).places.isEmpty())
        assertEquals(0, calls)
    }

    private class MemoryPlaceCache : PlaceCache {
        private val entries = mutableMapOf<String, List<PlaceCandidate>>()
        override fun read(query: String): List<PlaceCandidate>? = entries[query]
        override fun write(query: String, places: List<PlaceCandidate>) { entries[query] = places }
    }
}
