package it.faunavia.local

import android.content.Context
import it.faunavia.domain.*
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

    val diary: DiaryRepository = object : DiaryRepository {
        private fun validateTaxon(id: String) {
            require(dao.taxon(id)?.toDomain()?.isSelectable == true) { "Choose an accepted Animalia taxon." }
        }

        override suspend fun create(draft: ObservationDraft, photos: List<ObservationPhoto>): Observation = write {
            validateTaxon(draft.taxonId)
            val now = Instant.ofEpochMilli(clock.nowEpochMillis())
            val observation = Observation(draft.id, draft.taxonId, draft.observedAt, draft.zoneId, draft.location, draft.notes, now, now)
            dao.insertObservation(observation.toRow())
            photos.forEach {
                require(it.observationId == observation.id) { "Photo belongs to another observation." }
                dao.insertPhoto(it.toRow())
            }
            observation
        }

        override suspend fun update(draft: ObservationDraft): Observation = write {
            validateTaxon(draft.taxonId)
            val old = requireNotNull(dao.observation(draft.id)) { "Observation does not exist." }.toDomain()
            val now = maxOf(old.updatedAt, Instant.ofEpochMilli(clock.nowEpochMillis()))
            val observation = old.copy(taxonId = draft.taxonId, observedAt = draft.observedAt,
                zoneId = draft.zoneId, location = draft.location, notes = draft.notes, updatedAt = now)
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

    val catalogue: CatalogueRepository = object : CatalogueRepository {
        override suspend fun saveTaxon(taxon: Taxon) { write { dao.saveTaxon(taxon.toRow()) } }
        override suspend fun taxon(id: String): Taxon? = read { dao.taxon(id)?.toDomain() }
        override suspend fun deleteTaxon(id: String) { write { dao.deleteTaxon(id) } }
        override suspend fun savePreview(preview: TaxonPreview) { write { dao.savePreview(preview.toRow()) } }
        override suspend fun preview(taxonId: String): TaxonPreview? = read { dao.preview(taxonId)?.toDomain() }
        override suspend fun saveProfile(profile: SpeciesProfile) { write { dao.saveProfile(profile.toRow()) } }
        override suspend fun profile(taxonId: String): SpeciesProfile? = read { dao.profile(taxonId)?.toDomain() }
        override suspend fun saveSuggestion(profile: SuggestionProfile) { write { dao.saveSuggestion(profile.toRow()) } }
        override suspend fun suggestion(taxonId: String, area: String): SuggestionProfile? = read { dao.suggestion(taxonId, area)?.toDomain() }
        override suspend fun saveEvidence(evidence: SourceEvidence) { write { dao.saveEvidence(evidence.toRow()) } }
        override suspend fun evidence(taxonId: String): List<SourceEvidence> = read { dao.evidence(taxonId).map { it.toDomain() } }
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
