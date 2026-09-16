package it.faunavia.local

import it.faunavia.domain.*
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray

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
fun TaxonPreview.toRow() = TaxonPreviewRow(taxonId, imageUri, provenance.toRow())
fun TaxonPreviewRow.toDomain() = TaxonPreview(taxonId, imageUri, provenance.toDomain())
fun SpeciesProfile.toRow() = SpeciesProfileRow(taxonId, description, habitats.json(), activeMonths.sorted().map { it.toString() }.json(), diet, size, behavior, conservation, provenance.toRow())
fun SpeciesProfileRow.toDomain() = SpeciesProfile(taxonId, description, habitats.strings(), activeMonths.strings().map { it.toInt() }.toSet(), diet, size, behavior, conservation, provenance.toDomain())
fun SuggestionProfile.toRow() = SuggestionProfileRow(taxonId, area, habitats.json(), urbanCommon, distinctivenessScore, reason, provenance.toRow())
fun SuggestionProfileRow.toDomain() = SuggestionProfile(taxonId, area, habitats.strings(), urbanCommon, distinctivenessScore, reason, provenance.toDomain())
fun Observation.toRow() = ObservationRow(id, taxonId, observedAt.toString(), observedAt.epochSecond, zoneId.id, location?.latitude, location?.longitude, notes, createdAt.toString(), updatedAt.toString())
fun ObservationRow.toDomain() = Observation(id, taxonId, Instant.parse(observedAt), ZoneId.of(zoneId), point(latitude, longitude), notes, Instant.parse(createdAt), Instant.parse(updatedAt))
fun ObservationPhoto.toRow() = ObservationPhotoRow(id, observationId, relativePath, sha256, byteSize, mimeType)
fun ObservationPhotoRow.toDomain() = ObservationPhoto(id, observationId, relativePath, sha256, byteSize, mimeType)
fun Route.toRow() = RouteRow(id, name, JSONArray(points.map { listOf(it.latitude, it.longitude) }).toString(), importedAt.toString())
fun RouteRow.toDomain(): Route {
    val array = JSONArray(points)
    return Route(id, name, List(array.length()) { index ->
        val coordinates = array.getJSONArray(index)
        GeoPoint(coordinates.getDouble(0), coordinates.getDouble(1))
    }, Instant.parse(importedAt))
}
fun SourceEvidence.toRow() = SourceEvidenceRow(id, taxonId, level.name, eventAt?.toString(), location?.latitude, location?.longitude, uncertaintyMeters, explanation, provenance.toRow())
fun SourceEvidenceRow.toDomain() = SourceEvidence(id, taxonId, EvidenceLevel.valueOf(level), eventAt?.let(Instant::parse), point(latitude, longitude), uncertaintyMeters, explanation, provenance.toDomain())
fun AppSettings.toRow() = AppSettingsRow(1, reminderEnabled, reminderTime.hour * 60 + reminderTime.minute, zoneId.id)
fun AppSettingsRow.toDomain() = AppSettings(reminderEnabled, LocalTime.of(reminderMinute / 60, reminderMinute % 60), ZoneId.of(zoneId))
