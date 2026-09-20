package it.faunavia.route

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteSource
import java.io.StringReader
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

class RouteDocumentParser {
    fun parse(
        fileName: String,
        content: String,
        importedAt: Instant,
    ): ParsedRoute {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            throw RouteImportException(RouteImportErrorCode.EMPTY_DOCUMENT, "The route document is empty.")
        }
        if (content.length > MAX_ROUTE_DOCUMENT_CHARS) {
            throw RouteImportException(RouteImportErrorCode.DOCUMENT_TOO_LARGE, "The route document exceeds the 5 MB text limit.")
        }
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when {
            extension == "gpx" || trimmed.startsWith("<") -> parseGpx(fileName, trimmed, importedAt)
            extension in setOf("json", "geojson") || trimmed.startsWith("{") -> parseGeoJson(fileName, trimmed, importedAt)
            else -> throw RouteImportException(
                RouteImportErrorCode.UNSUPPORTED_FORMAT,
                "Only GPX and GeoJSON route documents are supported.",
            )
        }
    }

    private fun parseGpx(fileName: String, content: String, importedAt: Instant): ParsedRoute {
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            throw RouteImportException(RouteImportErrorCode.MALFORMED_DOCUMENT, "GPX declarations and external entities are not allowed.")
        }
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                optionalFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                optionalFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                optionalFeature("http://xml.org/sax/features/external-general-entities", false)
                optionalFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder().apply {
                setErrorHandler(object : DefaultHandler() {
                    override fun error(error: SAXParseException) = throw error
                    override fun fatalError(error: SAXParseException) = throw error
                })
            }.parse(InputSource(StringReader(content)))
        } catch (error: Exception) {
            throw RouteImportException(RouteImportErrorCode.MALFORMED_DOCUMENT, "The GPX document is malformed.", error)
        }
        if (document.documentElement.localName != "gpx" && document.documentElement.nodeName.substringAfter(':') != "gpx") {
            throw RouteImportException(RouteImportErrorCode.UNSUPPORTED_FORMAT, "The XML document is not GPX.")
        }

        val rawSegments = mutableListOf<List<GeoPoint>>()
        val trackSegments = document.getElementsByTagNameNS("*", "trkseg")
        repeat(trackSegments.length) { index ->
            val element = trackSegments.item(index) as Element
            rawSegments += element.childElements("trkpt").map(::gpxPoint)
        }
        if (rawSegments.isEmpty()) {
            val routes = document.getElementsByTagNameNS("*", "rte")
            repeat(routes.length) { index ->
                val element = routes.item(index) as Element
                rawSegments += element.childElements("rtept").map(::gpxPoint)
            }
        }

        val normalized = normalizeSegments(rawSegments)
        val name = document.getElementsByTagNameNS("*", "name")
            .item(0)
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: fileName.substringBeforeLast('.').ifBlank { "Percorso GPX" }
        return parsedRoute(fileName, name, RouteSource.GPX, normalized, importedAt)
    }

    private fun Element.childElements(localName: String): List<Element> = buildList {
        val children = childNodes
        repeat(children.length) { index ->
            val child = children.item(index)
            if (child is Element && (child.localName == localName || child.nodeName.substringAfter(':') == localName)) {
                add(child)
            }
        }
    }

    private fun gpxPoint(element: Element): GeoPoint {
        val latitude = element.getAttribute("lat").toDoubleOrNull()
        val longitude = element.getAttribute("lon").toDoubleOrNull()
        return coordinate(latitude, longitude)
    }

    private fun parseGeoJson(fileName: String, content: String, importedAt: Instant): ParsedRoute {
        val root = try {
            Json.parseToJsonElement(content).jsonObject
        } catch (error: Exception) {
            throw RouteImportException(RouteImportErrorCode.MALFORMED_DOCUMENT, "The GeoJSON document is malformed.", error)
        }
        validateGeoJsonCrs(root)
        val namedSegments = geoJsonSegments(root)
        val normalized = normalizeSegments(namedSegments.second)
        val fallback = fileName.substringBeforeLast('.').ifBlank { "Percorso GeoJSON" }
        return parsedRoute(fileName, namedSegments.first ?: fallback, RouteSource.GEOJSON, normalized, importedAt)
    }

    private fun validateGeoJsonCrs(root: JsonObject) {
        val crs = root["crs"] as? JsonObject ?: return
        val name = (crs["properties"] as? JsonObject)
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.uppercase()
            .orEmpty()
        if ("4326" !in name && "CRS84" !in name) {
            throw RouteImportException(RouteImportErrorCode.UNSUPPORTED_CRS, "GeoJSON must use WGS84 coordinates.")
        }
    }

    private fun geoJsonSegments(root: JsonObject): Pair<String?, List<List<GeoPoint>>> {
        return when (root.requiredString("type")) {
            "FeatureCollection" -> {
                val features = root["features"] as? JsonArray
                    ?: throw malformed("GeoJSON FeatureCollection needs a features array.")
                var name: String? = null
                val segments = buildList {
                    features.forEach { featureElement ->
                        val feature = featureElement as? JsonObject ?: throw malformed("A GeoJSON feature is not an object.")
                        val parsed = geoJsonFeature(feature)
                        if (name == null) name = parsed.first
                        addAll(parsed.second)
                    }
                }
                name to segments
            }
            "Feature" -> geoJsonFeature(root)
            else -> null to geometrySegments(root)
        }
    }

    private fun geoJsonFeature(feature: JsonObject): Pair<String?, List<List<GeoPoint>>> {
        if (feature.requiredString("type") != "Feature") throw malformed("Expected a GeoJSON Feature.")
        val properties = feature["properties"] as? JsonObject
        val name = properties?.get("name")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        val geometry = feature["geometry"] as? JsonObject
            ?: throw RouteImportException(RouteImportErrorCode.EMPTY_ROUTE, "The GeoJSON feature has no geometry.")
        return name to geometrySegments(geometry)
    }

    private fun geometrySegments(geometry: JsonObject): List<List<GeoPoint>> = when (geometry.requiredString("type")) {
        "Point" -> listOf(listOf(jsonCoordinate(geometry["coordinates"])))
        "LineString" -> listOf(lineString(geometry["coordinates"]))
        "MultiLineString" -> {
            val lines = geometry["coordinates"] as? JsonArray ?: throw malformed("MultiLineString coordinates are missing.")
            lines.map(::lineString)
        }
        else -> throw RouteImportException(
            RouteImportErrorCode.UNSUPPORTED_GEOMETRY,
            "Only Point, LineString and MultiLineString GeoJSON geometries are supported.",
        )
    }

    private fun lineString(element: JsonElement?): List<GeoPoint> {
        val coordinates = element as? JsonArray ?: throw malformed("LineString coordinates are missing.")
        if (coordinates.size < 2) {
            throw RouteImportException(RouteImportErrorCode.EMPTY_ROUTE, "A LineString needs at least two coordinates.")
        }
        return coordinates.map(::jsonCoordinate)
    }

    private fun jsonCoordinate(element: JsonElement?): GeoPoint {
        val values = element as? JsonArray ?: throw malformed("A coordinate is not an array.")
        if (values.size < 2) throw malformed("A coordinate needs longitude and latitude.")
        return coordinate(values[1].number(), values[0].number())
    }

    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.requiredString(name: String): String = this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?: throw malformed("GeoJSON is missing $name.")

    private fun coordinate(latitude: Double?, longitude: Double?): GeoPoint {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) {
            throw RouteImportException(RouteImportErrorCode.INVALID_COORDINATE, "A route coordinate is not a finite number.")
        }
        return try {
            GeoPoint(latitude, longitude)
        } catch (error: IllegalArgumentException) {
            throw RouteImportException(RouteImportErrorCode.INVALID_COORDINATE, "A route coordinate is outside WGS84.", error)
        }
    }

    private fun normalizeSegments(raw: List<List<GeoPoint>>): NormalizedSegments {
        var duplicates = 0
        var empty = 0
        val segments = buildList {
            raw.forEach { segment ->
                if (segment.isEmpty()) {
                    empty++
                } else {
                    val normalized = buildList {
                        segment.forEach { point ->
                            if (lastOrNull() == point) duplicates++ else add(point)
                        }
                    }
                    if (normalized.isEmpty()) empty++ else add(normalized)
                }
            }
        }
        if (segments.isEmpty()) {
            throw RouteImportException(RouteImportErrorCode.EMPTY_ROUTE, "The route contains no usable geometry.")
        }
        val warnings = buildList {
            if (duplicates > 0) add(RouteImportWarning(RouteImportWarningCode.CONSECUTIVE_DUPLICATE_REMOVED, duplicates))
            if (empty > 0) add(RouteImportWarning(RouteImportWarningCode.EMPTY_SEGMENT_IGNORED, empty))
        }
        return NormalizedSegments(segments, warnings)
    }

    private fun parsedRoute(
        fileName: String,
        name: String,
        source: RouteSource,
        normalized: NormalizedSegments,
        importedAt: Instant,
    ): ParsedRoute {
        val points = normalized.segments.flatten()
        return ParsedRoute(
            route = Route(
                id = "unpersisted",
                name = name.take(160),
                points = points,
                importedAt = importedAt,
                source = source,
                segments = normalized.segments,
                sourceName = fileName.take(255).ifBlank { null },
            ),
            warnings = normalized.warnings,
        )
    }

    private fun malformed(message: String) = RouteImportException(RouteImportErrorCode.MALFORMED_DOCUMENT, message)

    private fun DocumentBuilderFactory.optionalFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
            // Android XML implementations vary; the explicit DOCTYPE/ENTITY pre-check remains mandatory.
        }
    }

    private data class NormalizedSegments(
        val segments: List<List<GeoPoint>>,
        val warnings: List<RouteImportWarning>,
    )
}
