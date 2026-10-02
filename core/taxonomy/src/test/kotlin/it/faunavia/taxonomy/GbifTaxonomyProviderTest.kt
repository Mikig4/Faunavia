package it.faunavia.taxonomy

import it.faunavia.domain.AppClock
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GbifTaxonomyProviderTest {
    private val clock = object : AppClock { override fun nowEpochMillis(): Long = 1_789_729_200_000L }

    @Test fun mapsRealCommonNameSearchShapeWithProvenance() = runBlocking {
        val http = FakeHttp().apply {
            response("search?q=merlo", """{"results":[
                {"key":2490719,"scientificName":"Turdus merula Linnaeus, 1758","canonicalName":"Turdus merula",
                 "vernacularNames":[{"vernacularName":"Blackbird","language":"eng"},{"vernacularName":"Merlo","language":"ita"}],
                 "kingdom":"Animalia","rank":"SPECIES","taxonomicStatus":"ACCEPTED"}
            ]}""")
        }

        val result = GbifTaxonomyProvider(http, clock).suggest("merlo", 8) as TaxonomyProviderResult.Success
        val taxon = result.candidates.single().taxon

        assertEquals("gbif:2490719", taxon.id)
        assertEquals("Animalia", taxon.kingdom)
        assertEquals("Merlo", taxon.commonName)
        assertTrue(result.candidates.single().aliases.contains("Blackbird"))
        assertEquals(1, http.requests.size)
        assertTrue(http.requests.single().contains("qField=VERNACULAR"))
        assertEquals("GBIF Backbone Taxonomy", taxon.provenance.attribution)
        assertEquals("GBIF Species API v1", taxon.provenance.version)
    }

    @Test fun resolvesSuggestedSynonymToAcceptedTaxon() = runBlocking {
        val http = FakeHttp().apply {
            response("suggest?q=merula", """[
                {"key":111,"scientificName":"Merula vulgaris","kingdom":"Animalia","rank":"SPECIES",
                 "status":"SYNONYM","acceptedKey":2490719}
            ]""")
            response("/2490719", """{
                "key":2490719,"scientificName":"Turdus merula","kingdom":"Animalia","rank":"SPECIES","taxonomicStatus":"ACCEPTED"
            }""")
        }

        val result = GbifTaxonomyProvider(http, clock).suggest("merula", 8) as TaxonomyProviderResult.Success
        assertEquals("gbif:2490719", result.candidates.single().taxon.id)
        assertTrue(result.candidates.single().aliases.contains("Merula vulgaris"))
    }

    @Test fun mapsMalformedPayloadToRecoverableFailure() = runBlocking {
        val http = FakeHttp().apply { response("suggest?q=merlo", "not json") }
        assertEquals(
            TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE),
            GbifTaxonomyProvider(http, clock).suggest("merlo", 8),
        )
    }

    @Test fun mapsTimeoutToRecoverableFailure() = runBlocking {
        val http = object : GbifHttpClient {
            override fun get(url: String): String = throw SocketTimeoutException("fixture timeout")
        }
        assertEquals(
            TaxonomyProviderResult.Failure(TaxonomyFailure.TIMEOUT),
            GbifTaxonomyProvider(http, clock).suggest("merlo", 8),
        )
    }

    @Test fun fallsBackToScientificAutocompleteWhenCommonNamesAreEmpty() = runBlocking {
        val http = FakeHttp().apply { response("suggest?q=Turdus+merula", """[
            {"key":2490719,"scientificName":"Turdus merula","canonicalName":"Turdus merula",
             "kingdom":"Animalia","rank":"SPECIES","status":"ACCEPTED"}
        ]""") }
        val result = GbifTaxonomyProvider(http, clock).suggest("Turdus merula", 8) as TaxonomyProviderResult.Success
        assertEquals("gbif:2490719", result.candidates.single().taxon.id)
        assertEquals(2, http.requests.size)
    }

    @Test fun malformedCommonNameResultsAreRecoverable() = runBlocking {
        val http = FakeHttp().apply { response("search?q=merlo", """{"results":null}""") }
        assertEquals(TaxonomyProviderResult.Failure(TaxonomyFailure.MALFORMED_RESPONSE),
            GbifTaxonomyProvider(http, clock).suggest("merlo", 8))
    }

    private class FakeHttp : GbifHttpClient {
        private val responses = linkedMapOf<String, String>()
        val requests = mutableListOf<String>()
        fun response(pathPart: String, body: String) { responses[pathPart] = body }
        override fun get(url: String): String {
            requests += url
            return responses.entries.firstOrNull { url.contains(it.key) }?.value
                ?: if (url.contains("/search?")) """{"results":[]}""" else error("No fixture for $url")
        }
    }
}
