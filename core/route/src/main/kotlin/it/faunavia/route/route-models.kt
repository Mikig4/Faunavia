package it.faunavia.route

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route

const val MAX_ROUTE_DOCUMENT_CHARS = 5_000_000

data class RouteAnalysisConfig(
    val corridorRadiusMeters: Double = 1_000.0,
    val samplingIntervalMeters: Double = 500.0,
    val cellSizeMeters: Int = 1_000,
    val queryChunkCellsPerAxis: Int = 5,
    val coordinatePrecisionDecimals: Int = 6,
) {
    init {
        require(corridorRadiusMeters.isFinite() && corridorRadiusMeters in 50.0..20_000.0)
        require(samplingIntervalMeters.isFinite() && samplingIntervalMeters in 10.0..10_000.0)
        require(cellSizeMeters in 100..10_000)
        require(queryChunkCellsPerAxis in 1..20)
        require(coordinatePrecisionDecimals in 5..8)
    }
}

data class GeoBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west in -180.0..180.0 && east in -180.0..180.0 && west <= east)
        require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north)
    }
}

data class CorridorPortion(
    val key: String,
    val segmentIndex: Int,
    val polygon: List<GeoPoint>,
    val bounds: GeoBounds,
) {
    init {
        require(key.isNotBlank() && segmentIndex >= 0)
        require(polygon.size >= 5 && polygon.first() == polygon.last())
    }
}

data class MetricCell(
    val key: String,
    val eastingIndex: Int,
    val northingIndex: Int,
)

data class QueryChunk(
    val key: String,
    val cellKeys: List<String>,
    val bounds: GeoBounds,
) {
    init { require(key.isNotBlank() && cellKeys.isNotEmpty()) }
}

enum class RouteImportWarningCode { CONSECUTIVE_DUPLICATE_REMOVED, EMPTY_SEGMENT_IGNORED }

data class RouteImportWarning(
    val code: RouteImportWarningCode,
    val count: Int,
) {
    init { require(count > 0) }
}

data class RouteAnalysis(
    val routeId: String,
    val routeName: String,
    val projection: String,
    val totalLengthMeters: Double,
    val estimatedCorridorAreaSquareMeters: Double,
    val bounds: GeoBounds,
    val samples: List<GeoPoint>,
    val corridorPortions: List<CorridorPortion>,
    val cells: List<MetricCell>,
    val queryChunks: List<QueryChunk>,
    val fingerprint: String,
    val warnings: List<RouteImportWarning>,
) {
    init {
        require(routeId.isNotBlank() && routeName.isNotBlank())
        require(projection == "EPSG:3035")
        require(totalLengthMeters.isFinite() && totalLengthMeters >= 0.0)
        require(estimatedCorridorAreaSquareMeters.isFinite() && estimatedCorridorAreaSquareMeters > 0.0)
        require(samples.isNotEmpty() && corridorPortions.isNotEmpty() && cells.isNotEmpty() && queryChunks.isNotEmpty())
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
    }
}

data class ParsedRoute(
    val route: Route,
    val warnings: List<RouteImportWarning>,
)

data class RouteImportOutcome(
    val route: Route,
    val analysis: RouteAnalysis,
)

enum class RouteImportErrorCode {
    EMPTY_DOCUMENT,
    DOCUMENT_TOO_LARGE,
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_CRS,
    UNSUPPORTED_GEOMETRY,
    MALFORMED_DOCUMENT,
    EMPTY_ROUTE,
    INVALID_COORDINATE,
    ANTIMERIDIAN_UNSUPPORTED,
    OUTSIDE_EPSG_3035_AREA,
}

class RouteImportException(
    val code: RouteImportErrorCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
