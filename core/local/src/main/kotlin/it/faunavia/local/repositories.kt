package it.faunavia.local

import android.content.Context
import it.faunavia.domain.*
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.occurrence.OccurrenceCacheStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Callable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Own one instance per application; all disk operations run off the caller's thread. */
class LocalRepositories(
    private val database: FaunaviaDatabase,
    private val clock: AppClock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        fun open(context: Context, clock: AppClock): LocalRepositories =
            LocalRepositories(FaunaviaDatabase.open(context), clock)
    }

    private val dao = database.dao()
    private suspend fun <T> read(block: () -> T): T = withContext(io) { block() }
    private suspend fun <T> write(block: () -> T): T = withContext(io) {
        database.runInTransaction(Callable { block() })
    }

    private fun validateLink(tripId: String?, outingId: String?) {
        require(tripId == null || dao.trip(tripId) != null) { "Trip does not exist." }
        require(outingId == null || (tripId != null && dao.outing(outingId)?.tripId == tripId)) { "Outing does not belong to this trip." }
    }

    private fun createObservation(draft: ObservationDraft, photos: List<ObservationPhoto> = emptyList(), createdAt: Instant? = null): Observation {
        require(dao.taxon(draft.taxonId)?.toDomain()?.isSelectable == true) { "Choose an accepted Animalia taxon." }
        validateLink(draft.tripId, draft.outingId)
        val now = maxOf(createdAt ?: Instant.MIN, Instant.ofEpochMilli(clock.nowEpochMillis()))
        val observation = Observation(draft.id, draft.taxonId, draft.observedAt, draft.zoneId,
            draft.location, draft.notes, createdAt ?: now, now, draft.quantity, draft.tripId, draft.outingId)
        dao.insertObservation(observation.toRow())
        photos.forEach {
            require(it.observationId == observation.id) { "Photo belongs to another observation." }
            dao.insertPhoto(it.toRow())
        }
        return observation
    }

    val wishlist: WishlistRepository = object : WishlistRepository {
        override suspend fun add(taxonId: String) { write {
            require(dao.taxon(taxonId)?.toDomain()?.isSelectable == true) { "Choose an accepted Animalia taxon." }
            dao.addWish(taxonId, Instant.ofEpochMilli(clock.nowEpochMillis()).toString())
        } }
        override suspend fun remove(taxonId: String) { write { dao.removeWish(taxonId) } }
        override suspend fun list(): List<WishlistEntry> = read { dao.wishes().map { WishlistEntry(it.taxonId, Instant.parse(it.addedAt)) } }
    }

    val diary: DiaryRepository = object : DiaryRepository {
        private fun validateTaxon(id: String) {
            require(dao.taxon(id)?.toDomain()?.isSelectable == true) { "Choose an accepted Animalia taxon." }
        }

        override suspend fun create(draft: ObservationDraft, photos: List<ObservationPhoto>): Observation = write {
            createObservation(draft, photos)
        }

        override suspend fun update(draft: ObservationDraft): Observation = write {
            validateTaxon(draft.taxonId)
            validateLink(draft.tripId, draft.outingId)
            val old = requireNotNull(dao.observation(draft.id)) { "Observation does not exist." }.toDomain()
            val now = maxOf(old.updatedAt, Instant.ofEpochMilli(clock.nowEpochMillis()))
            val observation = old.copy(taxonId = draft.taxonId, observedAt = draft.observedAt,
                zoneId = draft.zoneId, location = draft.location, notes = draft.notes,
                quantity = draft.quantity, updatedAt = now, tripId = draft.tripId, outingId = draft.outingId)
            check(dao.updateObservation(observation.toRow()) == 1)
            observation
        }

        override suspend fun get(id: String): Observation? = read { dao.observation(id)?.toDomain() }
        override suspend fun list(): List<Observation> = read { dao.observations().map { it.toDomain() } }
        override suspend fun onDate(date: LocalDate, zoneId: ZoneId): List<Observation> = read {
            val start = date.atStartOfDay(zoneId).toEpochSecond()
            val end = date.plusDays(1).atStartOfDay(zoneId).toEpochSecond()
            dao.observationsBetween(start, end).map { it.toDomain() }
        }
        override suspend fun photos(observationId: String): List<ObservationPhoto> = read {
            dao.photos(observationId).map { it.toDomain() }
        }
        override suspend fun addPhoto(photo: ObservationPhoto) { write { dao.insertPhoto(photo.toRow()) } }
        override suspend fun deletePhoto(id: String): ObservationPhoto? = write {
            val photo = dao.photo(id)?.toDomain()
            dao.deletePhoto(id)
            photo
        }
        override suspend fun delete(id: String): List<ObservationPhoto> = write {
            val photos = dao.photos(id).map { it.toDomain() }
            dao.deleteObservation(id)
            photos
        }
    }

    val trips: TripRepository = object : TripRepository {
        override suspend fun save(trip: Trip) { write {
            val old = dao.trip(trip.id)?.toDomain()
            val stored = if (old == null) trip else trip.copy(createdAt = old.createdAt,
                updatedAt = maxOf(old.updatedAt, trip.updatedAt, Instant.ofEpochMilli(clock.nowEpochMillis())))
            dao.saveTrip(stored.toRow())
        } }
        override suspend fun get(id: String): Trip? = read { dao.trip(id)?.toDomain() }
        override suspend fun list(): List<Trip> = read { dao.trips().map { it.toDomain() }.sortedWith(compareBy(Trip::startsOn, Trip::id)) }
        override suspend fun delete(id: String) { write { dao.deleteTrip(id) } }
        override suspend fun saveOuting(outing: Outing) { write {
            val trip = requireNotNull(dao.trip(outing.tripId)?.toDomain()) { "Trip does not exist." }
            require(outing.date in trip.startsOn..trip.endsOn) { "Outing date must be within the trip." }
            require(dao.outing(outing.id)?.tripId.let { it == null || it == outing.tripId }) { "An outing cannot change trip." }
            dao.saveOuting(outing.toRow())
        } }
        override suspend fun outing(id: String): Outing? = read { dao.outing(id)?.toDomain() }
        override suspend fun outings(tripId: String): List<Outing> = read { dao.outings(tripId).map { it.toDomain() }.sortedWith(compareBy(Outing::date, Outing::id)) }
        override suspend fun deleteOuting(id: String) { write { dao.deleteOuting(id) } }
        override suspend fun savePlace(place: SavedTripPlace) { write { dao.saveTripPlace(place.toRow()) } }
        override suspend fun places(tripId: String): List<SavedTripPlace> = read { dao.tripPlaces(tripId).map { it.toDomain() } }
        override suspend fun deletePlace(id: String) { write { dao.deleteTripPlace(id) } }
        override suspend fun saveResult(result: SavedTripResult) { write {
            validateLink(result.tripId, result.outingId)
            if (result.stageId != null) require(dao.trip(result.tripId)?.toDomain()?.stages?.any { it.id == result.stageId } == true) {
                "Stage does not belong to this trip."
            }
            dao.saveTripResult(result.toRow())
        } }
        override suspend fun results(tripId: String): List<SavedTripResult> = read { dao.tripResults(tripId).map { it.toDomain() } }
        override suspend fun deleteResult(id: String) { write { dao.deleteTripResult(id) } }
    }

    val unidentified: UnidentifiedRepository = object : UnidentifiedRepository {
        override suspend fun save(input: UnidentifiedInput): UnidentifiedDraft = write {
            validateLink(input.tripId, input.outingId)
            require(dao.observation(input.id) == null) { "This memory is already identified." }
            val old = dao.unidentified(input.id)?.toDomain()
            val now = maxOf(old?.updatedAt ?: Instant.MIN, Instant.ofEpochMilli(clock.nowEpochMillis()))
            UnidentifiedDraft(input, old?.createdAt ?: now, now).also { dao.saveUnidentified(it.toRow()) }
        }
        override suspend fun get(id: String): UnidentifiedDraft? = read { dao.unidentified(id)?.toDomain() }
        override suspend fun list(): List<UnidentifiedDraft> = read { dao.unidentifiedDrafts().map { it.toDomain() } }
        override suspend fun delete(id: String) { write { dao.deleteUnidentified(id) } }
        override suspend fun photos(draftId: String): List<ObservationPhoto> = read { dao.draftPhotos(draftId).map { it.toDomain() } }
        override suspend fun addPhoto(photo: ObservationPhoto) { write { dao.insertDraftPhoto(photo.toDraftRow()) } }
        override suspend fun deletePhoto(id: String): ObservationPhoto? = write {
            val photo = dao.draftPhoto(id)?.toDomain()
            dao.deleteDraftPhoto(id)
            photo
        }
        override suspend fun deleteWithPhotos(id: String): List<ObservationPhoto> = write {
            val photos = dao.draftPhotos(id).map { it.toDomain() }
            dao.deleteUnidentified(id)
            photos
        }
        override suspend fun convert(draft: ObservationDraft): Observation = write {
            val old = requireNotNull(dao.unidentified(draft.id)?.toDomain()) { "Draft does not exist or was already converted." }
            val photos = dao.draftPhotos(draft.id).map { it.toDomain() }
            dao.deleteUnidentified(draft.id)
            createObservation(draft, photos = photos, createdAt = old.createdAt)
        }
    }

    val catalogue: CatalogueRepository = object : CatalogueRepository {
        override suspend fun saveTaxon(taxon: Taxon) { write { dao.saveTaxon(taxon.toRow()) } }
        override suspend fun saveTaxonWithAliases(taxon: Taxon, aliases: List<String>) {
            require(taxon.isSelectable) { "Only accepted Animalia taxa can be selected." }
            val normalizedAliases = (aliases + taxon.scientificName + listOfNotNull(taxon.commonName))
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinctBy { it.lowercase() }
                .map { TaxonAlias(taxon.id, it) }
            write {
                dao.saveTaxon(taxon.toRow())
                dao.deleteAliases(taxon.id)
                dao.saveAliases(normalizedAliases.map { it.toRow() })
            }
        }
        override suspend fun taxon(id: String): Taxon? = read { dao.taxon(id)?.toDomain() }
        override suspend fun aliases(taxonId: String): List<TaxonAlias> = read {
            dao.aliases(taxonId).map { it.toDomain() }
        }
        override suspend fun searchTaxa(query: String, limit: Int): List<Taxon> {
            require(query.isNotBlank()) { "A taxon search needs a query." }
            require(limit > 0) { "A taxon search needs a positive limit." }
            val pattern = "%${query.trim().lowercase().replace(Regex("\\s+"), " ")}%"
            return read { dao.searchTaxa(pattern, limit).map { it.toDomain() } }
        }
        override suspend fun deleteTaxon(id: String) { write { dao.deleteTaxon(id) } }
        override suspend fun savePreview(preview: TaxonPreview) { write { dao.savePreview(preview.toRow()) } }
        override suspend fun preview(taxonId: String): TaxonPreview? = read { dao.preview(taxonId)?.toDomain() }
        override suspend fun saveProfile(profile: SpeciesProfile) { write { dao.saveProfile(profile.toRow()) } }
        override suspend fun profile(taxonId: String): SpeciesProfile? = read { dao.profile(taxonId)?.toDomain() }
        override suspend fun saveSuggestion(profile: SuggestionProfile) {
            require(profile.schemaVersion == 1) { "Legacy suggestion profiles are read-only; curate a version 1 profile." }
            write { dao.saveSuggestion(profile.toRow()) }
        }
        override suspend fun suggestion(taxonId: String, area: String): SuggestionProfile? = read { dao.suggestion(taxonId, area)?.toDomain() }
        override suspend fun saveEvidence(evidence: SourceEvidence) { write { dao.saveEvidence(evidence.toRow()) } }
        override suspend fun evidence(taxonId: String): List<SourceEvidence> = read { dao.evidence(taxonId).map { it.toDomain() } }
    }

    val occurrenceCache: OccurrenceCacheStore = object : OccurrenceCacheStore {
        override suspend fun read(key: String): OccurrenceCacheEntry? = read {
            dao.occurrenceCache(key)?.toDomain()
        }
        override suspend fun save(entry: OccurrenceCacheEntry) { write { dao.saveOccurrenceCache(entry.toRow()) } }
        override suspend fun delete(key: String) { write { dao.deleteOccurrenceCache(key) } }
        override suspend fun clear() { write { dao.clearOccurrenceCache() } }
    }

    val routes: RouteRepository = object : RouteRepository {
        override suspend fun save(route: Route) { write { dao.saveRoute(route.toRow()) } }
        override suspend fun get(id: String): Route? = read { dao.route(id)?.toDomain() }
        override suspend fun list(): List<Route> = read { dao.routes().map { it.toDomain() } }
        override suspend fun delete(id: String) { write { dao.deleteRoute(id) } }
    }

    val settings: SettingsRepository = object : SettingsRepository {
        override suspend fun get(): AppSettings = read { dao.settings()?.toDomain() ?: AppSettings() }
        override suspend fun save(settings: AppSettings) { write { dao.saveSettings(settings.toRow()) } }
    }
}
