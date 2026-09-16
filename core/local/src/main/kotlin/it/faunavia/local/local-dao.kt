package it.faunavia.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert

/** Blocking DAO is confined to the repository IO dispatcher; Room still rejects main-thread access. */
@Dao
interface LocalDao {
    @Upsert fun saveTaxon(row: TaxonRow)
    @Query("SELECT * FROM taxa WHERE id = :id") fun taxon(id: String): TaxonRow?
    @Query("DELETE FROM taxa WHERE id = :id") fun deleteTaxon(id: String)
    @Upsert fun savePreview(row: TaxonPreviewRow)
    @Query("SELECT * FROM taxon_previews WHERE taxonId = :id") fun preview(id: String): TaxonPreviewRow?
    @Upsert fun saveProfile(row: SpeciesProfileRow)
    @Query("SELECT * FROM species_profiles WHERE taxonId = :id") fun profile(id: String): SpeciesProfileRow?
    @Upsert fun saveSuggestion(row: SuggestionProfileRow)
    @Query("SELECT * FROM suggestion_profiles WHERE taxonId = :id AND area = :area") fun suggestion(id: String, area: String): SuggestionProfileRow?
    @Upsert fun saveEvidence(row: SourceEvidenceRow)
    @Query("SELECT * FROM source_evidence WHERE taxonId = :id ORDER BY id") fun evidence(id: String): List<SourceEvidenceRow>

    @Insert fun insertObservation(row: ObservationRow)
    @Update fun updateObservation(row: ObservationRow): Int
    @Query("SELECT * FROM observations WHERE id = :id") fun observation(id: String): ObservationRow?
    @Query("SELECT * FROM observations ORDER BY observedEpochSecond, id") fun observations(): List<ObservationRow>
    @Query("SELECT * FROM observations WHERE observedEpochSecond >= :start AND observedEpochSecond < :end ORDER BY observedEpochSecond, id")
    fun observationsBetween(start: Long, end: Long): List<ObservationRow>
    @Query("DELETE FROM observations WHERE id = :id") fun deleteObservation(id: String)
    @Insert fun insertPhoto(row: ObservationPhotoRow)
    @Query("SELECT * FROM observation_photos WHERE observationId = :id ORDER BY id") fun photos(id: String): List<ObservationPhotoRow>
    @Query("SELECT * FROM observation_photos WHERE id = :id") fun photo(id: String): ObservationPhotoRow?
    @Query("DELETE FROM observation_photos WHERE id = :id") fun deletePhoto(id: String)

    @Upsert fun saveRoute(row: RouteRow)
    @Query("SELECT * FROM routes WHERE id = :id") fun route(id: String): RouteRow?
    @Query("SELECT * FROM routes ORDER BY id") fun routes(): List<RouteRow>
    @Query("DELETE FROM routes WHERE id = :id") fun deleteRoute(id: String)
    @Upsert fun saveSettings(row: AppSettingsRow)
    @Query("SELECT * FROM app_settings WHERE id = 1") fun settings(): AppSettingsRow?
}
