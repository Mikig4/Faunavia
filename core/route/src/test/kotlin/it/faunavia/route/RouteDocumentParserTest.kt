package it.faunavia.route

import it.faunavia.domain.RouteSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDocumentParserTest {
    private val parser = RouteDocumentParser()
    private val importedAt = Instant.parse("2026-09-18T12:00:00Z")

    @Test
    fun parsesMultiSegmentGpxAndReportsOnlyConsecutiveDuplicates() {
        val parsed = parser.parse("parco.gpx", GPX, importedAt)

        assertEquals("Parco Nord", parsed.route.name)
        assertEquals(RouteSource.GPX, parsed.route.source)
        assertEquals("parco.gpx", parsed.route.sourceName)
        assertEquals(listOf(2, 2), parsed.route.segments.map(List<*>::size))
        assertEquals(4, parsed.route.points.size)
        assertEquals(
            listOf(RouteImportWarning(RouteImportWarningCode.CONSECUTIVE_DUPLICATE_REMOVED, 1)),
            parsed.warnings,
        )
    }

    @Test
    fun parsesGeoJsonPointLineAndMultiLineString() {
        val point = parser.parse("punto.geojson", GEOJSON_POINT, importedAt)
        val line = parser.parse("linea.json", GEOJSON_LINE, importedAt)
        val multi = parser.parse("multi.geojson", GEOJSON_MULTI, importedAt)

        assertEquals(listOf(1), point.route.segments.map(List<*>::size))
        assertEquals("Punto", point.route.name)
        assertEquals(listOf(2), line.route.segments.map(List<*>::size))
        assertEquals(listOf(2, 2), multi.route.segments.map(List<*>::size))
        assertEquals(RouteSource.GEOJSON, multi.route.source)
    }

    @Test
    fun rejectsMalformedEmptyUnsupportedAndOutOfRangeDocuments() {
        assertCode(RouteImportErrorCode.EMPTY_DOCUMENT) { parser.parse("empty.gpx", "  ", importedAt) }
        assertCode(RouteImportErrorCode.MALFORMED_DOCUMENT) { parser.parse("broken.gpx", "<gpx>", importedAt) }
        assertCode(RouteImportErrorCode.UNSUPPORTED_GEOMETRY) {
            parser.parse("polygon.geojson", """{"type":"Polygon","coordinates":[]}""", importedAt)
        }
        assertCode(RouteImportErrorCode.INVALID_COORDINATE) {
            parser.parse("invalid.geojson", """{"type":"Point","coordinates":[9.0,91.0]}""", importedAt)
        }
        assertCode(RouteImportErrorCode.EMPTY_ROUTE) {
            parser.parse("empty.geojson", """{"type":"FeatureCollection","features":[]}""", importedAt)
        }
    }

    @Test
    fun rejectsNonWgs84AndXmlEntities() {
        assertCode(RouteImportErrorCode.UNSUPPORTED_CRS) {
            parser.parse(
                "wrong.geojson",
                """{"type":"Point","coordinates":[9,45],"crs":{"properties":{"name":"EPSG:3857"}}}""",
                importedAt,
            )
        }
        val error = assertThrows(RouteImportException::class.java) {
            parser.parse("entity.gpx", "<!DOCTYPE gpx [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><gpx>&xxe;</gpx>", importedAt)
        }
        assertEquals(RouteImportErrorCode.MALFORMED_DOCUMENT, error.code)
        assertTrue(error.message.orEmpty().contains("entities"))
    }

    private fun assertCode(expected: RouteImportErrorCode, block: () -> Unit) {
        val error = assertThrows(RouteImportException::class.java, block)
        assertEquals(expected, error.code)
    }

    private companion object {
        val GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1">
              <metadata><name>Parco Nord</name></metadata>
              <trk><trkseg>
                <trkpt lat="45.520000" lon="9.180000" />
                <trkpt lat="45.520000" lon="9.180000" />
                <trkpt lat="45.525000" lon="9.185000" />
              </trkseg><trkseg>
                <trkpt lat="45.530000" lon="9.190000" />
                <trkpt lat="45.535000" lon="9.195000" />
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val GEOJSON_POINT = """
            {"type":"Feature","properties":{"name":"Punto"},"geometry":{"type":"Point","coordinates":[9.19,45.52]}}
        """.trimIndent()

        val GEOJSON_LINE = """
            {"type":"LineString","coordinates":[[9.18,45.52],[9.185,45.525]]}
        """.trimIndent()

        val GEOJSON_MULTI = """
            {"type":"MultiLineString","coordinates":[[[9.18,45.52],[9.185,45.525]],[[9.19,45.53],[9.195,45.535]]]}
        """.trimIndent()
    }
}
