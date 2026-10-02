package it.faunavia.local

import it.faunavia.domain.*
import it.faunavia.occurrence.DocumentedOccurrence
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.occurrence.OccurrenceProviderId
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

private fun List<String>.json(): String = JSONArray(this).toString()
private fun String.strings(): List<String> = JSONArray(this).let { array ->
    List(array.length()) { array.getString(it) }
}
private fun point(latitude: Double?, longitude: Double?): GeoPoint? {
    check((latitude == null) == (longitude == null)) { "Incomplete stored coordinate." }
    return latitude?.let { GeoPoint(it, checkNotNull(longitude)) }
}

fun Provenance.toRow() = ProvenanceRow(source, recordId, query, retrievedAt.toString(), license, attribution, quality, version)
fun ProvenanceRow.toDomain() = Provenance(source, recordId, query, Instant.parse(retrievedAt), license, attribution, quality, version)
fun Taxon.toRow() = TaxonRow(id, scientificName, commonName, kingdom, status.name, rank, provenance.toRow())
fun TaxonRow.toDomain() = Taxon(id, scientificName, commonName, kingdom, TaxonomicStatus.valueOf(status), rank, provenance.toDomain())
private fun normalizeTaxonName(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
fun TaxonAlias.toRow() = TaxonAliasRow(taxonId, name.trim(), normalizeTaxonName(name))
fun TaxonAliasRow.toDomain() = TaxonAlias(taxonId, name)
fun TaxonPreview.toRow() = TaxonPreviewRow(taxonId, imageUri, provenance.toRow())
fun TaxonPreviewRow.toDomain() = TaxonPreview(taxonId, imageUri, provenance.toDomain())
fun SpeciesProfile.toRow() = SpeciesProfileRow(taxonId, description, habitats.json(), activeMonths.sorted().map { it.toString() }.json(), diet, size, behavior, conservation, provenance.toRow())
fun SpeciesProfileRow.toDomain() = SpeciesProfile(taxonId, description, habitats.strings(), activeMonths.strings().map { it.toInt() }.toSet(), diet, size, behavior, conservation, provenance.toDomain())
fun SuggestionProfile.toRow() = SuggestionProfileRow(taxonId, area, habitats.json(), urbanCommon, distinctivenessScore, reason, provenance.toRow())
fun SuggestionProfileRow.toDomain() = SuggestionProfile(taxonId, area, habitats.strings(), urbanCommon, distinctivenessScore, reason, provenance.toDomain())
fun Observation.toRow() = ObservationRow(id, taxonId, observedAt.toString(), observedAt.epochSecond, zoneId.id, location?.latitude, location?.longitude, notes, createdAt.toString(), updatedAt.toString(), quantity, tripId, outingId)
fun ObservationRow.toDomain() = Observation(id, taxonId, Instant.parse(observedAt), ZoneId.of(zoneId), point(latitude, longitude), notes, Instant.parse(createdAt), Instant.parse(updatedAt), quantity, tripId, outingId)
fun ObservationPhoto.toRow() = ObservationPhotoRow(id, observationId, relativePath, sha256, byteSize, mimeType)
fun ObservationPhotoRow.toDomain() = ObservationPhoto(id, observationId, relativePath, sha256, byteSize, mimeType)
fun Route.toRow(): RouteRow {
    val geometry = JSONObject()
        .put("schemaVersion", 2)
        .put("source", source.name)
        .put("sourceName", sourceName ?: JSONObject.NULL)
        .put("segments", JSONArray(segments.map { segment ->
            segment.map { point -> listOf(point.latitude, point.longitude) }
        }))
    return RouteRow(id, name, geometry.toString(), importedAt.toString())
}
fun RouteRow.toDomain(): Route {
    if (points.trimStart().startsWith("[")) {
        val legacy = JSONArray(points)
        val legacyPoints = List(legacy.length()) { index -> legacy.getJSONArray(index).point() }
        return Route(id, name, legacyPoints, Instant.parse(importedAt))
    }
    val geometry = JSONObject(points)
    val storedSegments = geometry.getJSONArray("segments")
    val segments = List(storedSegments.length()) { segmentIndex ->
        val segment = storedSegments.getJSONArray(segmentIndex)
        List(segment.length()) { pointIndex -> segment.getJSONArray(pointIndex).point() }
    }
    val source = runCatching { RouteSource.valueOf(geometry.getString("source")) }.getOrDefault(RouteSource.LEGACY)
    val sourceName = if (geometry.isNull("sourceName")) null else geometry.getString("sourceName")
    return Route(
        id = id,
        name = name,
        points = segments.flatten(),
        importedAt = Instant.parse(importedAt),
        source = source,
        segments = segments,
        sourceName = sourceName,
    )
}
private fun JSONArray.point() = GeoPoint(getDouble(0), getDouble(1))
fun SourceEvidence.toRow() = SourceEvidenceRow(id, taxonId, level.name, eventAt?.toString(), location?.latitude, location?.longitude, uncertaintyMeters, explanation, provenance.toRow())
fun SourceEvidenceRow.toDomain() = SourceEvidence(id, taxonId, EvidenceLevel.valueOf(level), eventAt?.let(Instant::parse), point(latitude, longitude), uncertaintyMeters, explanation, provenance.toDomain())
fun OccurrenceCacheEntry.toRow(): OccurrenceCacheRow = OccurrenceCacheRow(
    key = key,
    cachedAt = cachedAt.toString(),
    expiresAt = expiresAt.toString(),
    occurrences = JSONArray(occurrences.map { it.toJson() }).toString(),
)
fun OccurrenceCacheRow.toDomain(): OccurrenceCacheEntry = OccurrenceCacheEntry(
    key = key,
    cachedAt = Instant.parse(cachedAt),
    expiresAt = Instant.parse(expiresAt),
    occurrences = JSONArray(occurrences).let { array -> List(array.length()) { array.getJSONObject(it).toOccurrence() } },
)
private fun DocumentedOccurrence.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("provider", provider.name)
    .put("providerRecordId", providerRecordId)
    .put("taxonId", taxonId ?: JSONObject.NULL)
    .put("scientificName", scientificName)
    .put("observedOn", observedOn ?: JSONObject.NULL)
    .put("latitude", location?.latitude ?: JSONObject.NULL)
    .put("longitude", location?.longitude ?: JSONObject.NULL)
    .put("coordinateUncertaintyMeters", coordinateUncertaintyMeters ?: JSONObject.NULL)
    .put("sourceUrl", sourceUrl)
    .put("provenance", provenance.toJson())
private fun Provenance.toJson(): JSONObject = JSONObject()
    .put("source", source)
    .put("recordId", recordId)
    .put("query", query)
    .put("retrievedAt", retrievedAt.toString())
    .put("license", license)
    .put("attribution", attribution)
    .put("quality", quality)
    .put("version", version)
private fun JSONObject.toOccurrence(): DocumentedOccurrence = DocumentedOccurrence(
    id = getString("id"),
    provider = OccurrenceProviderId.valueOf(getString("provider")),
    providerRecordId = getString("providerRecordId"),
    taxonId = getNullableString("taxonId"),
    scientificName = getString("scientificName"),
    observedOn = getNullableString("observedOn"),
    location = if (isNull("latitude")) null else GeoPoint(getDouble("latitude"), getDouble("longitude")),
    coordinateUncertaintyMeters = if (isNull("coordinateUncertaintyMeters")) null else getDouble("coordinateUncertaintyMeters"),
    sourceUrl = getString("sourceUrl"),
    provenance = getJSONObject("provenance").toProvenance(),
)
private fun JSONObject.toProvenance(): Provenance = Provenance(
    source = getString("source"),
    recordId = getString("recordId"),
    query = getString("query"),
    retrievedAt = Instant.parse(getString("retrievedAt")),
    license = getString("license"),
    attribution = getString("attribution"),
    quality = getString("quality"),
    version = getString("version"),
)
private fun JSONObject.getNullableString(name: String): String? = if (isNull(name)) null else getString(name)
fun AppSettings.toRow() = AppSettingsRow(1, reminderEnabled, reminderTime.hour * 60 + reminderTime.minute, zoneId.id)
fun AppSettingsRow.toDomain() = AppSettings(reminderEnabled, LocalTime.of(reminderMinute / 60, reminderMinute % 60), ZoneId.of(zoneId))
