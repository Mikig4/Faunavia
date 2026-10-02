package it.faunavia.local

import it.faunavia.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

private fun Provenance.json() = JSONObject().put("source", source).put("recordId", recordId)
    .put("query", query).put("retrievedAt", retrievedAt.toString()).put("license", license)
    .put("attribution", attribution).put("quality", quality).put("version", version)
private fun JSONObject.provenance() = Provenance(getString("source"), getString("recordId"), getString("query"),
    Instant.parse(getString("retrievedAt")), getString("license"), getString("attribution"), getString("quality"), getString("version"))
private fun TripPlace.json() = JSONObject().put("name", name).put("lat", center.latitude)
    .put("lon", center.longitude).put("kind", kind).put("provenance", provenance.json())
private fun JSONObject.place() = TripPlace(getString("name"), GeoPoint(getDouble("lat"), getDouble("lon")),
    getString("kind"), getJSONObject("provenance").provenance())
private fun JSONObject.nullable(name: String) = if (isNull(name)) null else getString(name)
private fun JSONArray.strings() = List(length()) { getString(it) }

private fun Route.geometryJson(): JSONObject = toRow().let {
    JSONObject().put("id", it.id).put("name", it.name).put("points", it.points).put("importedAt", it.importedAt)
}
private fun JSONObject.geometry(): Route = RouteRow(getString("id"), getString("name"),
    getString("points"), getString("importedAt")).toDomain()

private fun TripRoute.json() = JSONObject().put("geometry", geometry.geometryJson())
    .put("distance", distanceMeters).put("duration", durationSeconds).put("provenance", provenance.json())
private fun JSONObject.tripRoute() = TripRoute(getJSONObject("geometry").geometry(), getDouble("distance"),
    getDouble("duration"), getJSONObject("provenance").provenance())

fun Trip.toRow() = TripRow(id, JSONObject().put("version", 3).put("name", name)
    .put("destination", destination.json()).put("startsOn", startsOn.toString()).put("endsOn", endsOn.toString())
    .put("departure", departure?.json() ?: JSONObject.NULL)
    .put("route", route?.json() ?: JSONObject.NULL)
    .put("stages", JSONArray(stages.map { JSONObject().put("id", it.id).put("destination", it.destination.json())
        .put("date", it.date.toString()).put("route", it.route.json()) }))
    .put("radius", radiusMeters).put("interests", JSONArray(interests.sortedBy { it.name }.map { it.name }))
    .put("createdAt", createdAt.toString()).put("updatedAt", updatedAt.toString()).toString())
fun TripRow.toDomain(): Trip = JSONObject(payload).let {
    Trip(id, it.getString("name"), it.getJSONObject("destination").place(), LocalDate.parse(it.getString("startsOn")),
        LocalDate.parse(it.getString("endsOn")), it.getDouble("radius"),
        it.getJSONArray("interests").strings().map(AnimalInterest::valueOf).toSet(),
        Instant.parse(it.getString("createdAt")), Instant.parse(it.getString("updatedAt")),
        it.optJSONObject("departure")?.place(), it.optJSONObject("route")?.tripRoute(),
        it.optJSONArray("stages")?.let { stages -> List(stages.length()) { index ->
            stages.getJSONObject(index).let { stage -> TripStage(stage.getString("id"),
                stage.getJSONObject("destination").place(), LocalDate.parse(stage.getString("date")),
                stage.getJSONObject("route").tripRoute()) }
        } }.orEmpty())
}
fun Outing.toRow(): OutingRow {
    val geometry = route?.toRow()?.let { JSONObject().put("id", it.id).put("name", it.name)
        .put("points", it.points).put("importedAt", it.importedAt) }
    return OutingRow(id, tripId, JSONObject().put("version", 1).put("name", name).put("date", date.toString())
        .put("place", place.json()).put("route", geometry ?: JSONObject.NULL).toString())
}
fun OutingRow.toDomain(): Outing = JSONObject(payload).let {
    val route = if (it.isNull("route")) null else it.getJSONObject("route").let { geometry ->
        RouteRow(geometry.getString("id"), geometry.getString("name"), geometry.getString("points"), geometry.getString("importedAt")).toDomain()
    }
    Outing(id, tripId, it.getString("name"), LocalDate.parse(it.getString("date")), it.getJSONObject("place").place(), route)
}
fun SavedTripPlace.toRow() = SavedTripPlaceRow(id, tripId, JSONObject().put("place", place.json())
    .put("savedAt", savedAt.toString()).toString())
fun SavedTripPlaceRow.toDomain() = JSONObject(payload).let {
    SavedTripPlace(id, tripId, it.getJSONObject("place").place(), Instant.parse(it.getString("savedAt")))
}
fun SavedTripResult.toRow() = SavedTripResultRow(id, tripId, outingId, JSONObject().put("version", 1)
    .put("stageId", stageId ?: JSONObject.NULL)
    .put("taxonId", taxonId).put("scientificName", scientificName).put("level", level.name)
    .put("explanation", JSONArray(explanation)).put("season", season).put("analysisKey", analysisKey)
    .put("savedAt", savedAt.toString()).put("partial", partial).put("stale", stale)
    .put("startsOn", startsOn.toString()).put("endsOn", endsOn.toString()).put("area", area.json())
    .put("evidence", JSONArray(evidence.map { source -> JSONObject().put("id", source.id)
        .put("level", source.level.name).put("eventAt", source.eventAt?.toString() ?: JSONObject.NULL)
        .put("lat", source.location?.latitude ?: JSONObject.NULL).put("lon", source.location?.longitude ?: JSONObject.NULL)
        .put("uncertainty", source.uncertaintyMeters ?: JSONObject.NULL).put("explanation", source.explanation)
        .put("provenance", source.provenance.json()) })).toString())
fun SavedTripResultRow.toDomain(): SavedTripResult = JSONObject(payload).let { root ->
    val taxonId = root.getString("taxonId")
    val evidence = root.getJSONArray("evidence").let { items -> List(items.length()) { index ->
        val item = items.getJSONObject(index)
        SourceEvidence(item.getString("id"), taxonId, EvidenceLevel.valueOf(item.getString("level")),
            item.nullable("eventAt")?.let(Instant::parse),
            if (item.isNull("lat")) null else GeoPoint(item.getDouble("lat"), item.getDouble("lon")),
            if (item.isNull("uncertainty")) null else item.getDouble("uncertainty"),
            item.getString("explanation"), item.getJSONObject("provenance").provenance())
    } }
    SavedTripResult(id, tripId, outingId, taxonId, root.getString("scientificName"),
        EvidenceLevel.valueOf(root.getString("level")), root.getJSONArray("explanation").strings(),
        root.getString("season"), evidence, root.getString("analysisKey"), Instant.parse(root.getString("savedAt")),
        root.getBoolean("partial"), root.getBoolean("stale"), LocalDate.parse(root.getString("startsOn")),
        LocalDate.parse(root.getString("endsOn")), root.getJSONObject("area").place(), root.nullable("stageId"))
}
fun UnidentifiedDraft.toRow(): UnidentifiedRow = input.let {
    UnidentifiedRow(it.id, it.observedAt.toString(), it.zoneId.id, it.location?.latitude, it.location?.longitude,
        it.notes, it.quantity, it.tripId, it.outingId, createdAt.toString(), updatedAt.toString())
}
fun UnidentifiedRow.toDomain() = UnidentifiedDraft(UnidentifiedInput(id, Instant.parse(observedAt), ZoneId.of(zoneId),
    latitude?.let { GeoPoint(it, checkNotNull(longitude)) }, notes, quantity, tripId, outingId),
    Instant.parse(createdAt), Instant.parse(updatedAt))
