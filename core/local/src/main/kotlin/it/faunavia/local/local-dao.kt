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
    @Upsert fun saveAliases(rows: List<TaxonAliasRow>)
    @Query("DELETE FROM taxon_aliases WHERE taxonId = :taxonId") fun deleteAliases(taxonId: String)
    @Query("SELECT * FROM taxon_aliases WHERE taxonId = :taxonId ORDER BY normalizedName, name") fun aliases(taxonId: String): List<TaxonAliasRow>
    @Query("""
        SELECT taxa.* FROM taxa
        LEFT JOIN taxon_aliases aliases ON aliases.taxonId = taxa.id
        WHERE taxa.kingdom = 'Animalia' AND taxa.status = 'ACCEPTED' AND (
            lower(taxa.scientificName) LIKE :pattern OR
            lower(COALESCE(taxa.commonName, '')) LIKE :pattern OR
            aliases.normalizedName LIKE :pattern
        )
        GROUP BY taxa.id
        ORDER BY CASE upper(taxa.rank) WHEN 'SPECIES' THEN 0 WHEN 'SUBSPECIES' THEN 1 ELSE 2 END,
            taxa.scientificName COLLATE NOCASE, taxa.id
        LIMIT :limit
    """)
    fun searchTaxa(pattern: String, limit: Int): List<TaxonRow>
    @Query("DELETE FROM taxa WHERE id = :id") fun deleteTaxon(id: String)
    @Upsert fun savePreview(row: TaxonPreviewRow)
    @Query("SELECT * FROM taxon_previews WHERE taxonId = :id") fun preview(id: String): TaxonPreviewRow?
    @Upsert fun saveProfile(row: SpeciesProfileRow)
    @Query("SELECT * FROM species_profiles WHERE taxonId = :id") fun profile(id: String): SpeciesProfileRow?
    @Upsert fun saveSuggestion(row: SuggestionProfileRow)
    @Query("SELECT * FROM suggestion_profiles WHERE taxonId = :id AND area = :area") fun suggestion(id: String, area: String): SuggestionProfileRow?
    @Upsert fun saveEvidence(row: SourceEvidenceRow)
    @Query("SELECT * FROM source_evidence WHERE taxonId = :id ORDER BY id") fun evidence(id: String): List<SourceEvidenceRow>
    @Upsert fun saveOccurrenceCache(row: OccurrenceCacheRow)
    @Query("SELECT * FROM occurrence_cache WHERE `key` = :key") fun occurrenceCache(key: String): OccurrenceCacheRow?
    @Query("DELETE FROM occurrence_cache WHERE `key` = :key") fun deleteOccurrenceCache(key: String)
    @Query("DELETE FROM occurrence_cache") fun clearOccurrenceCache()

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
