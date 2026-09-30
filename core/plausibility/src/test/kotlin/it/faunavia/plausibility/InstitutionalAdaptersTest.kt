package it.faunavia.plausibility

import it.faunavia.domain.AppClock
import it.faunavia.route.GeoBounds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstitutionalAdaptersTest {
    private val clock = object : AppClock {
        override fun nowEpochMillis(): Long = 1_758_307_200_000L
    }

    @Test
    fun article12FixturePreservesRangeSeasonCodeAndBoundedQuery() = runBlocking<Unit> {
        val http = RecordingHttp(mutableListOf(response(resource("article12.json"))))
        val result = EeaArticleRangeAdapter(
            http,
            clock,
            EeaArticleAdapterConfig(EeaArticleDirective.ARTICLE_12, pageSize = 5),
        ).query(bounds()) as InstitutionalSourceResult.Success<EeaArticleEvidence>

        assertEquals(1, result.value.ranges.size)
        assertEquals("Accipiter nisus", result.value.ranges.single().taxon.scientificName)
        assertEquals("B", result.value.seasonCodes.single().code)
        assertTrue(result.value.ranges.single().provenance.license.isNotBlank())
        assertTrue(http.urls.single().contains("geometry="))
        assertTrue(http.urls.single().contains("spatialRel=esriSpatialRelIntersects"))
    }

    @Test
    fun article17FixturePreservesReportingRangeWithoutClaimingAnOccurrence() = runBlocking<Unit> {
        val result = EeaArticleRangeAdapter(
            RecordingHttp(mutableListOf(response(resource("article17.json")))),
            clock,
            EeaArticleAdapterConfig(EeaArticleDirective.ARTICLE_17),
        ).query(bounds()) as InstitutionalSourceResult.Success<EeaArticleEvidence>

        val range = result.value.ranges.single()
        assertEquals("Lycaena dispar", range.taxon.scientificName)
        assertEquals(RangeRelation.INTERSECTS, range.relation)
        assertTrue(range.provenance.quality.contains("reporting geometry"))
    }

    @Test
    fun maesFixtureKeepsAssociationStrengthsSeparateFromLandCoverClasses() = runBlocking<Unit> {
        val associations = (MaesHabitatMatrixAdapter(
            RecordingHttp(mutableListOf(response(resource("maes.json")))),
            clock,
            MaesHabitatMatrixConfig("https://example.test/maes"),
        ).load() as InstitutionalSourceResult.Success<List<HabitatAssociation>>).value

        assertEquals(setOf("grassland", "wetlands"), associations.map(HabitatAssociation::ecosystem).toSet())
        assertTrue(associations.all { it.strength == HabitatAssociationStrength.PREFERRED })
        assertTrue(associations.all { it.provenance.attribution.isNotBlank() })
    }

    @Test
    fun nonEquivalentClcplus2021FixtureCannotProduceHabitatCompatibility() {
        val adapter = ClcplusLandCoverAdapter(
            RecordingHttp(mutableListOf()),
            clock,
            ClcplusLandCoverConfig("https://example.test/clcplus", "2021", equivalentToRequiredProduct = false),
        )
        val samples = adapter.parseFixtureEnvelope(resource("clcplus-2021.json"))
        val taxon = TaxonReference("gbif:1929270", "Lycaena dispar")
        val habitat = HabitatCompatibilityResolver().resolve(
            taxon = taxon,
            associations = listOf(HabitatAssociation(taxon, "grassland", HabitatAssociationStrength.PREFERRED, samples.first().provenance)),
            samples = samples,
            crosswalk = CuratedHabitatCrosswalk(
                mappings = mapOf("6" to setOf("grassland")),
                scientificallyCurated = true,
                provenance = samples.first().provenance,
            ),
        )

        assertEquals(listOf("6", "3"), samples.map(LandCoverSample::rawClass))
        assertTrue(samples.none { it.equivalentToRequiredProduct })
        assertEquals(Compatibility.UNAVAILABLE, habitat.compatibility)
    }

    @Test
    fun sourceFailuresAreExplicitForRateLimitsAndMalformedPayloads() = runBlocking<Unit> {
        val rateLimited = EeaArticleRangeAdapter(
            RecordingHttp(mutableListOf(response("", status = 429))),
            clock,
            EeaArticleAdapterConfig(EeaArticleDirective.ARTICLE_17),
        ).query(bounds()) as InstitutionalSourceResult.Failure
        val malformed = EeaArticleRangeAdapter(
            RecordingHttp(mutableListOf(response("not-json"))),
            clock,
            EeaArticleAdapterConfig(EeaArticleDirective.ARTICLE_17),
        ).query(bounds()) as InstitutionalSourceResult.Failure

        assertEquals(InstitutionalSourceFailureReason.RATE_LIMITED, rateLimited.failure.reason)
        assertEquals(InstitutionalSourceFailureReason.MALFORMED_RESPONSE, malformed.failure.reason)
    }

    private fun bounds(): GeoBounds = GeoBounds(9.171, 45.511, 9.210, 45.540)

    private fun response(body: String, status: Int = 200): PlausibilityHttpResponse = PlausibilityHttpResponse(status, body)

    private fun resource(name: String): String = checkNotNull(javaClass.getResource("/fixtures/$name"))
        .readText()

    private class RecordingHttp(
        private val responses: MutableList<PlausibilityHttpResponse>,
    ) : PlausibilityHttpClient {
        val urls = mutableListOf<String>()

        override fun get(url: String): PlausibilityHttpResponse {
            urls += url
            return responses.removeAt(0)
        }
    }
}
