package it.faunavia.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

data class ProvenanceRow(
    val source: String,
    val recordId: String,
    val query: String,
    val retrievedAt: String,
    val license: String,
    val attribution: String,
    val quality: String,
    val version: String,
)

@Entity(
    tableName = "taxa",
    primaryKeys = ["id"],
)
data class TaxonRow(
    val id: String,
    val scientificName: String,
    val commonName: String?,
    val kingdom: String,
    val status: String,
    val rank: String,
    @Embedded(prefix = "source_") val provenance: ProvenanceRow,
)

@Entity(
    tableName = "taxon_previews",
    primaryKeys = ["taxonId"],
    foreignKeys = [ForeignKey(entity = TaxonRow::class, parentColumns = ["id"], childColumns = ["taxonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taxonId")],
)
data class TaxonPreviewRow(
    val taxonId: String,
    val imageUri: String,
    @Embedded(prefix = "source_") val provenance: ProvenanceRow,
)

@Entity(
    tableName = "species_profiles",
    primaryKeys = ["taxonId"],
    foreignKeys = [ForeignKey(entity = TaxonRow::class, parentColumns = ["id"], childColumns = ["taxonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taxonId")],
)
data class SpeciesProfileRow(
    val taxonId: String,
    val description: String,
    val habitats: String,
    val activeMonths: String,
    val diet: String?,
    val size: String?,
    val behavior: String?,
    val conservation: String?,
    @Embedded(prefix = "source_") val provenance: ProvenanceRow,
)

@Entity(
    tableName = "suggestion_profiles",
    primaryKeys = ["taxonId", "area"],
    foreignKeys = [ForeignKey(entity = TaxonRow::class, parentColumns = ["id"], childColumns = ["taxonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taxonId")],
)
data class SuggestionProfileRow(
    val taxonId: String,
    val area: String,
    val habitats: String,
    val urbanCommon: Boolean,
    val distinctivenessScore: Double,
    val reason: String,
    @Embedded(prefix = "source_") val provenance: ProvenanceRow,
)

@Entity(
    tableName = "observations",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = TaxonRow::class, parentColumns = ["id"], childColumns = ["taxonId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("taxonId"), Index("observedEpochSecond")],
)
data class ObservationRow(
    val id: String,
    val taxonId: String,
    val observedAt: String,
    val observedEpochSecond: Long,
    val zoneId: String,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "observation_photos",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = ObservationRow::class, parentColumns = ["id"], childColumns = ["observationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("observationId")],
)
data class ObservationPhotoRow(
    val id: String,
    val observationId: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val mimeType: String,
)

@Entity(
    tableName = "routes",
    primaryKeys = ["id"],
)
data class RouteRow(
    val id: String,
    val name: String,
    val points: String,
    val importedAt: String,
)

@Entity(
    tableName = "source_evidence",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = TaxonRow::class, parentColumns = ["id"], childColumns = ["taxonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taxonId")],
)
data class SourceEvidenceRow(
    val id: String,
    val taxonId: String,
    val level: String,
    val eventAt: String?,
    val latitude: Double?,
    val longitude: Double?,
    val uncertaintyMeters: Double?,
    val explanation: String,
    @Embedded(prefix = "source_") val provenance: ProvenanceRow,
)

@Entity(
    tableName = "app_settings",
    primaryKeys = ["id"],
)
data class AppSettingsRow(
    val id: Int,
    val reminderEnabled: Boolean,
    val reminderMinute: Int,
    @ColumnInfo(defaultValue = "'UTC'") val zoneId: String,
)
