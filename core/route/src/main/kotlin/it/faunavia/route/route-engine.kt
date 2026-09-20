package it.faunavia.route

import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RouteEngine {
    fun geometryFingerprint(route: Route): String = sha256(
        "contract=faunavia-geometry-v1|geometry=${canonicalGeometry(route, GEOMETRY_ID_PRECISION_DECIMALS)}",
    )

    fun analyze(
        route: Route,
        config: RouteAnalysisConfig = RouteAnalysisConfig(),
        warnings: List<RouteImportWarning> = emptyList(),
    ): RouteAnalysis {
        validateProjectionArea(route)
        val projectedSegments = route.segments.map { segment -> segment.map(Epsg3035::forward) }
        val sampledSegments = route.segments.map { sampleSegment(it, config.samplingIntervalMeters) }
        val projectedSamples = sampledSegments.map { segment -> segment.map(Epsg3035::forward) }
        val totalLength = route.segments.sumOf(::lineLengthMeters)
        val area = route.segments.sumOf { segment ->
            2.0 * config.corridorRadiusMeters * lineLengthMeters(segment) +
                PI * config.corridorRadiusMeters.pow(2)
        }
        val cells = corridorCells(projectedSegments, config)
        val chunks = queryChunks(cells, config)
        val portions = corridorPortions(projectedSamples, config.corridorRadiusMeters)
        val fingerprint = fingerprint(route, config)
        return RouteAnalysis(
            routeId = route.id,
            routeName = route.name,
            projection = "EPSG:3035",
            totalLengthMeters = totalLength,
            estimatedCorridorAreaSquareMeters = area,
            bounds = geoBounds(route.points),
            samples = sampledSegments.flatten(),
            corridorPortions = portions,
            cells = cells,
            queryChunks = chunks,
            fingerprint = fingerprint,
            warnings = warnings,
        )
    }

    private fun validateProjectionArea(route: Route) {
        route.segments.forEach { segment ->
            segment.zipWithNext().forEach { (start, end) ->
                if (abs(start.longitude - end.longitude) > 180.0) {
                    throw RouteImportException(
                        RouteImportErrorCode.ANTIMERIDIAN_UNSUPPORTED,
                        "Routes crossing the antimeridian are outside the EPSG:3035 pilot.",
                    )
                }
            }
        }
        if (route.points.any { it.latitude !in 24.0..85.0 || it.longitude !in -35.0..45.0 }) {
            throw RouteImportException(
                RouteImportErrorCode.OUTSIDE_EPSG_3035_AREA,
                "The route is outside the European EPSG:3035 analysis area.",
            )
        }
    }

    private fun sampleSegment(points: List<GeoPoint>, intervalMeters: Double): List<GeoPoint> {
        if (points.size == 1) return points
        return buildList {
            add(points.first())
            points.zipWithNext().forEach { (start, end) ->
                val distance = haversineMeters(start, end)
                if (distance == 0.0) return@forEach
                val steps = max(1, ceil(distance / intervalMeters).toInt())
                for (step in 1..steps) {
                    val point = greatCircleInterpolate(start, end, step.toDouble() / steps)
                    if (lastOrNull() != point) add(point)
                }
            }
        }
    }

    private fun corridorPortions(
        segments: List<List<ProjectedPoint>>,
        radius: Double,
    ): List<CorridorPortion> = buildList {
        segments.forEachIndexed { segmentIndex, segment ->
            if (segment.size == 1) {
                val polygon = circle(segment.single(), radius)
                add(portion("corridor/$segmentIndex/0", segmentIndex, polygon))
            } else {
                segment.zipWithNext().forEachIndexed { portionIndex, (start, end) ->
                    val polygon = capsule(start, end, radius)
                    add(portion("corridor/$segmentIndex/$portionIndex", segmentIndex, polygon))
                }
            }
        }
    }

    private fun portion(key: String, segmentIndex: Int, projected: List<ProjectedPoint>): CorridorPortion {
        val polygon = projected.map(Epsg3035::inverse)
        return CorridorPortion(key, segmentIndex, polygon, geoBounds(polygon))
    }

    private fun circle(center: ProjectedPoint, radius: Double): List<ProjectedPoint> {
        val points = (0 until CORRIDOR_ARC_STEPS * 2).map { index ->
            val angle = 2.0 * PI * index / (CORRIDOR_ARC_STEPS * 2)
            ProjectedPoint(center.easting + radius * cos(angle), center.northing + radius * sin(angle))
        }
        return points + points.first()
    }

    private fun capsule(start: ProjectedPoint, end: ProjectedPoint, radius: Double): List<ProjectedPoint> {
        val direction = atan2(end.northing - start.northing, end.easting - start.easting)
        val startArc = (0..CORRIDOR_ARC_STEPS).map { step ->
            val angle = direction + PI / 2.0 + PI * step / CORRIDOR_ARC_STEPS
            ProjectedPoint(start.easting + radius * cos(angle), start.northing + radius * sin(angle))
        }
        val endArc = (0..CORRIDOR_ARC_STEPS).map { step ->
            val angle = direction - PI / 2.0 + PI * step / CORRIDOR_ARC_STEPS
            ProjectedPoint(end.easting + radius * cos(angle), end.northing + radius * sin(angle))
        }
        val polygon = startArc + endArc
        return polygon + polygon.first()
    }

    private fun corridorCells(
        segments: List<List<ProjectedPoint>>,
        config: RouteAnalysisConfig,
    ): List<MetricCell> {
        val cells = linkedMapOf<String, MetricCell>()
        val size = config.cellSizeMeters.toDouble()
        val halfDiagonal = sqrt(2.0) * size / 2.0
        segments.forEach { segment ->
            val edges = if (segment.size == 1) listOf(segment.single() to segment.single()) else segment.zipWithNext()
            edges.forEach { (start, end) ->
                val minX = floor((min(start.easting, end.easting) - config.corridorRadiusMeters) / size).toInt()
                val maxX = floor((max(start.easting, end.easting) + config.corridorRadiusMeters) / size).toInt()
                val minY = floor((min(start.northing, end.northing) - config.corridorRadiusMeters) / size).toInt()
                val maxY = floor((max(start.northing, end.northing) + config.corridorRadiusMeters) / size).toInt()
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        val center = ProjectedPoint((x + 0.5) * size, (y + 0.5) * size)
                        if (distanceToSegment(center, start, end) <= config.corridorRadiusMeters + halfDiagonal) {
                            val key = "epsg3035-${config.cellSizeMeters}m/$x/$y"
                            cells[key] = MetricCell(key, x, y)
                        }
                    }
                }
            }
        }
        return cells.values.sortedWith(compareBy(MetricCell::eastingIndex, MetricCell::northingIndex))
    }

    private fun queryChunks(cells: List<MetricCell>, config: RouteAnalysisConfig): List<QueryChunk> {
        val size = config.queryChunkCellsPerAxis
        val grouped = cells.groupBy { cell ->
            Math.floorDiv(cell.eastingIndex, size) to Math.floorDiv(cell.northingIndex, size)
        }
        return grouped.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (index, chunkCells) ->
                val minEasting = index.first * size * config.cellSizeMeters.toDouble()
                val minNorthing = index.second * size * config.cellSizeMeters.toDouble()
                val span = size * config.cellSizeMeters.toDouble()
                val projectedBoundary = listOf(
                    ProjectedPoint(minEasting, minNorthing),
                    ProjectedPoint(minEasting + span / 2.0, minNorthing),
                    ProjectedPoint(minEasting + span, minNorthing),
                    ProjectedPoint(minEasting + span, minNorthing + span / 2.0),
                    ProjectedPoint(minEasting + span, minNorthing + span),
                    ProjectedPoint(minEasting + span / 2.0, minNorthing + span),
                    ProjectedPoint(minEasting, minNorthing + span),
                    ProjectedPoint(minEasting, minNorthing + span / 2.0),
                )
                QueryChunk(
                    key = "epsg3035-${size * config.cellSizeMeters}m/${index.first}/${index.second}",
                    cellKeys = chunkCells.sortedBy(MetricCell::key).map(MetricCell::key),
                    bounds = geoBounds(projectedBoundary.map(Epsg3035::inverse)),
                )
            }
    }

    private fun fingerprint(route: Route, config: RouteAnalysisConfig): String {
        val canonical = listOf(
            "contract=faunavia-route-v1",
            "projection=EPSG:3035",
            "geometry=${canonicalGeometry(route, config.coordinatePrecisionDecimals)}",
            "precision=${config.coordinatePrecisionDecimals}",
            "radius=${canonical(config.corridorRadiusMeters, 3)}",
            "sampling=${canonical(config.samplingIntervalMeters, 3)}",
            "cell=${config.cellSizeMeters}",
            "chunk=${config.queryChunkCellsPerAxis}",
        ).joinToString("|")
        return sha256(canonical)
    }

    private fun canonicalGeometry(route: Route, decimals: Int): String =
        route.segments.joinToString(prefix = "[", postfix = "]", separator = ";") { segment ->
            segment.joinToString(prefix = "[", postfix = "]", separator = ",") { point ->
                "${canonical(point.longitude, decimals)}:${canonical(point.latitude, decimals)}"
            }
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }

    private fun canonical(value: Double, decimals: Int): String {
        val threshold = 0.5 * 10.0.pow(-decimals)
        val normalized = if (abs(value) < threshold) 0.0 else value
        return BigDecimal.valueOf(normalized).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    }

    private fun lineLengthMeters(points: List<GeoPoint>): Double = points.zipWithNext().sumOf { (start, end) ->
        haversineMeters(start, end)
    }

    private fun haversineMeters(start: GeoPoint, end: GeoPoint): Double {
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val deltaLatitude = endLatitude - startLatitude
        val deltaLongitude = Math.toRadians(end.longitude - start.longitude)
        val a = sin(deltaLatitude / 2.0).pow(2) +
            cos(startLatitude) * cos(endLatitude) * sin(deltaLongitude / 2.0).pow(2)
        return EARTH_MEAN_RADIUS_METERS * 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun greatCircleInterpolate(start: GeoPoint, end: GeoPoint, ratio: Double): GeoPoint {
        if (ratio >= 1.0) return end
        val startVector = unitVector(start)
        val endVector = unitVector(end)
        val omega = acos((startVector dot endVector).coerceIn(-1.0, 1.0))
        if (omega < 1e-12) return start
        val scale = sin(omega)
        val left = sin((1.0 - ratio) * omega) / scale
        val right = sin(ratio * omega) / scale
        val x = left * startVector.x + right * endVector.x
        val y = left * startVector.y + right * endVector.y
        val z = left * startVector.z + right * endVector.z
        return GeoPoint(Math.toDegrees(atan2(z, hypot(x, y))), Math.toDegrees(atan2(y, x)))
    }

    private fun unitVector(point: GeoPoint): Vector3 {
        val latitude = Math.toRadians(point.latitude)
        val longitude = Math.toRadians(point.longitude)
        return Vector3(cos(latitude) * cos(longitude), cos(latitude) * sin(longitude), sin(latitude))
    }

    private fun distanceToSegment(point: ProjectedPoint, start: ProjectedPoint, end: ProjectedPoint): Double {
        val deltaX = end.easting - start.easting
        val deltaY = end.northing - start.northing
        val squaredLength = deltaX * deltaX + deltaY * deltaY
        if (squaredLength == 0.0) return hypot(point.easting - start.easting, point.northing - start.northing)
        val ratio = (((point.easting - start.easting) * deltaX + (point.northing - start.northing) * deltaY) / squaredLength)
            .coerceIn(0.0, 1.0)
        return hypot(point.easting - (start.easting + ratio * deltaX), point.northing - (start.northing + ratio * deltaY))
    }

    private fun geoBounds(points: List<GeoPoint>): GeoBounds = GeoBounds(
        west = points.minOf(GeoPoint::longitude),
        south = points.minOf(GeoPoint::latitude),
        east = points.maxOf(GeoPoint::longitude),
        north = points.maxOf(GeoPoint::latitude),
    )

    private data class Vector3(val x: Double, val y: Double, val z: Double) {
        infix fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z
    }

    private companion object {
        const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8
        const val CORRIDOR_ARC_STEPS = 8
        const val GEOMETRY_ID_PRECISION_DECIMALS = 6
    }
}

internal data class ProjectedPoint(val easting: Double, val northing: Double)

/** Ellipsoidal Lambert Azimuthal Equal Area, ETRS89 / LAEA Europe (EPSG:3035). */
internal object Epsg3035 {
    private const val SEMI_MAJOR_AXIS = 6_378_137.0
    private const val INVERSE_FLATTENING = 298.257222101
    private const val ORIGIN_LATITUDE_DEGREES = 52.0
    private const val ORIGIN_LONGITUDE_DEGREES = 10.0
    private const val FALSE_EASTING = 4_321_000.0
    private const val FALSE_NORTHING = 3_210_000.0
    private val flattening = 1.0 / INVERSE_FLATTENING
    private val eccentricitySquared = 2.0 * flattening - flattening * flattening
    private val eccentricity = sqrt(eccentricitySquared)
    private val originLatitude = Math.toRadians(ORIGIN_LATITUDE_DEGREES)
    private val originLongitude = Math.toRadians(ORIGIN_LONGITUDE_DEGREES)
    private val qPole = q(PI / 2.0)
    private val authalicRadius = SEMI_MAJOR_AXIS * sqrt(qPole / 2.0)
    private val originAuthalicLatitude = asin(q(originLatitude) / qPole)
    private val originScale = cos(originLatitude) / sqrt(1.0 - eccentricitySquared * sin(originLatitude).pow(2))
    private val d = SEMI_MAJOR_AXIS * originScale / (authalicRadius * cos(originAuthalicLatitude))

    fun forward(point: GeoPoint): ProjectedPoint {
        val latitude = Math.toRadians(point.latitude)
        val longitude = Math.toRadians(point.longitude)
        val authalicLatitude = asin((q(latitude) / qPole).coerceIn(-1.0, 1.0))
        val deltaLongitude = longitude - originLongitude
        val denominator = 1.0 + sin(originAuthalicLatitude) * sin(authalicLatitude) +
            cos(originAuthalicLatitude) * cos(authalicLatitude) * cos(deltaLongitude)
        val b = authalicRadius * sqrt(2.0 / denominator)
        return ProjectedPoint(
            easting = FALSE_EASTING + b * d * cos(authalicLatitude) * sin(deltaLongitude),
            northing = FALSE_NORTHING + (b / d) * (
                cos(originAuthalicLatitude) * sin(authalicLatitude) -
                    sin(originAuthalicLatitude) * cos(authalicLatitude) * cos(deltaLongitude)
                ),
        )
    }

    fun inverse(point: ProjectedPoint): GeoPoint {
        val x = (point.easting - FALSE_EASTING) / d
        val y = d * (point.northing - FALSE_NORTHING)
        val radius = hypot(x, y)
        if (radius < 1e-9) return GeoPoint(ORIGIN_LATITUDE_DEGREES, ORIGIN_LONGITUDE_DEGREES)
        val angularDistance = 2.0 * asin((radius / (2.0 * authalicRadius)).coerceIn(-1.0, 1.0))
        val authalicLatitude = asin(
            cos(angularDistance) * sin(originAuthalicLatitude) +
                y * sin(angularDistance) * cos(originAuthalicLatitude) / radius,
        )
        val longitude = originLongitude + atan2(
            x * sin(angularDistance),
            radius * cos(originAuthalicLatitude) * cos(angularDistance) -
                y * sin(originAuthalicLatitude) * sin(angularDistance),
        )
        val latitude = inverseAuthalicLatitude(authalicLatitude)
        return GeoPoint(Math.toDegrees(latitude), normalizeLongitude(Math.toDegrees(longitude)))
    }

    private fun q(latitude: Double): Double {
        val sine = sin(latitude)
        val eccentricSine = eccentricity * sine
        return (1.0 - eccentricitySquared) * (
            sine / (1.0 - eccentricitySquared * sine * sine) -
                ln((1.0 - eccentricSine) / (1.0 + eccentricSine)) / (2.0 * eccentricity)
            )
    }

    private fun inverseAuthalicLatitude(authalicLatitude: Double): Double {
        val target = qPole * sin(authalicLatitude)
        var lower = -PI / 2.0
        var upper = PI / 2.0
        repeat(64) {
            val middle = (lower + upper) / 2.0
            if (q(middle) < target) lower = middle else upper = middle
        }
        return (lower + upper) / 2.0
    }

    private fun normalizeLongitude(value: Double): Double {
        var longitude = value
        while (longitude > 180.0) longitude -= 360.0
        while (longitude < -180.0) longitude += 360.0
        return longitude
    }
}
