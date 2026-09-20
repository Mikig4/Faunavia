package it.faunavia.occurrence

import it.faunavia.domain.AppClock
import it.faunavia.domain.GeoPoint
import it.faunavia.route.CorridorPortion
import it.faunavia.route.GeoBounds
import it.faunavia.route.QueryChunk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrenceProviderContractTest {
    private val clock = object : AppClock { override fun nowEpochMillis(): Long = 1_758_196_800_000L }

    @Test
    fun gbifUsesPolygonPaginatesAndPreservesPerRecordProvenance() = runBlocking<Unit> {
        val http = RecordingHttp(
            mutableListOf(
                response("""{"results":[${gbifRecord("1")}],"endOfRecords":false}"""),
                response("""{"results":[],"endOfRecords":true}"""),
            ),
        )
        val provider = GbifOccurrenceProvider(http, clock, GbifOccurrenceProviderConfig(pageSize = 1, maxPagesPerArea = 2))
        val result = provider.search(scope()) as OccurrenceProviderResult.Success
        val record = result.occurrences.single()

        assertEquals(2, http.urls.size)
        assertTrue(http.urls.first().contains("geometry="))
        assertTrue(http.urls[1].contains("offset=1"))
        assertEquals("gbif:1", record.id)
        assertEquals("CC BY", record.provenance.license)
        assertTrue(record.provenance.query.contains("spatialMode=polygon"))
        assertEquals(800.0, record.coordinateUncertaintyMeters!!, 0.0)
    }

    @Test
    fun gbifCanFallBackToBoundingBoxesAndMapsRateLimit() = runBlocking<Unit> {
        val http = RecordingHttp(mutableListOf(response("""{"results":[],"endOfRecords":true}""")))
        val fallback = GbifOccurrenceProvider(http, clock, GbifOccurrenceProviderConfig(maxPolygonVertices = 5))
        assertTrue(fallback.search(scope()) is OccurrenceProviderResult.Success)
        assertTrue(http.urls.single().contains("geometry="))

        val limited = GbifOccurrenceProvider(RecordingHttp(mutableListOf(response("", 429, "2"))), clock)
            .search(scope()) as OccurrenceProviderResult.Failure
        assertEquals(OccurrenceFailureReason.RATE_LIMITED, limited.failure.reason)
        assertEquals(2_000L, limited.failure.retryAfterMillis)
    }

    @Test
    fun nnbUsesBboxPaginatesAndNeverInventsCoordinatePrecision() = runBlocking<Unit> {
        val http = RecordingHttp(
            mutableListOf(
                response("""{"numberMatched":2,"numberReturned":1,"features":[${nnbFeature("417")}]}"""),
                response("""{"numberMatched":2,"numberReturned":1,"features":[${nnbFeature("418")}]}"""),
            ),
        )
        val result = NnbOccurrenceProvider(http, clock, NnbOccurrenceProviderConfig(pageSize = 1, maxPagesPerArea = 2))
            .search(scope()) as OccurrenceProviderResult.Success

        assertEquals(2, result.occurrences.size)
        assertTrue(http.urls.first().contains("bbox="))
        assertTrue(http.urls[1].contains("startIndex=1"))
        assertNull(result.occurrences.first().coordinateUncertaintyMeters)
        assertEquals(45.5284, result.occurrences.first().location!!.latitude, 0.0)
        assertTrue(result.occurrences.first().provenance.license.contains("not exposed"))
        assertFalse(result.occurrences.first().provenance.attribution.isBlank())
    }

    private fun scope(): OccurrenceSearchScope = OccurrenceSearchScope(
        routeId = "route",
        fingerprint = "b".repeat(64),
        corridorPortions = listOf(
            CorridorPortion(
                "portion",
                0,
                listOf(
                    GeoPoint(45.0, 9.0), GeoPoint(45.0, 9.1), GeoPoint(45.1, 9.1),
                    GeoPoint(45.1, 9.0), GeoPoint(45.05, 9.0), GeoPoint(45.0, 9.0),
                ),
                GeoBounds(9.0, 45.0, 9.1, 45.1),
            ),
        ),
        queryChunks = listOf(QueryChunk("chunk", listOf("cell"), GeoBounds(9.0, 45.0, 9.1, 45.1))),
    )

    private fun gbifRecord(id: String): String = """
        {"key":$id,"acceptedTaxonKey":2490719,"acceptedScientificName":"Turdus merula","eventDate":"2026-01-02",
        "decimalLatitude":45.53,"decimalLongitude":9.20,"coordinateUncertaintyInMeters":800,
        "basisOfRecord":"HUMAN_OBSERVATION","issues":[],"license":"CC BY","datasetName":"Fixture"}
    """.trimIndent()

    private fun nnbFeature(id: String): String = """
        {"type":"Feature","id":"Osservazioni_puntuali.$id","geometry":{"type":"Point","coordinates":[9.1789,45.5284]},
        "properties":{"id_osservazione":$id,"nome_scientifico":"Carduelis chloris","anno":2014,"banca_dati":"Fixture NNB"}}
    """.trimIndent()

    private fun response(body: String, status: Int = 200, retryAfter: String = ""): OccurrenceHttpResponse =
        OccurrenceHttpResponse(status, body, mapOf("Retry-After" to retryAfter))

    private class RecordingHttp(private val responses: MutableList<OccurrenceHttpResponse>) : OccurrenceHttpClient {
        val urls = mutableListOf<String>()
        override fun get(url: String): OccurrenceHttpResponse {
            urls += url
            return responses.removeAt(0)
        }
    }
}
