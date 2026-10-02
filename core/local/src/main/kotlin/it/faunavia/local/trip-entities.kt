package it.faunavia.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "trips", primaryKeys = ["id"])
data class TripRow(val id: String, val payload: String)

@Entity(tableName = "outings", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = TripRow::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")])
data class OutingRow(val id: String, val tripId: String, val payload: String)

@Entity(tableName = "saved_trip_places", primaryKeys = ["id"],
    foreignKeys = [ForeignKey(entity = TripRow::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")])
data class SavedTripPlaceRow(val id: String, val tripId: String, val payload: String)

@Entity(tableName = "saved_trip_results", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = TripRow::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = OutingRow::class, parentColumns = ["id"], childColumns = ["outingId"], onDelete = ForeignKey.SET_NULL),
    ], indices = [Index("tripId"), Index("outingId")])
data class SavedTripResultRow(val id: String, val tripId: String, val outingId: String?, val payload: String)

/** Trip/outing integrity and unlinking are enforced by triggers, as for the existing diary table. */
@Entity(tableName = "unidentified_drafts", primaryKeys = ["id"], indices = [Index("tripId"), Index("outingId")])
data class UnidentifiedRow(
    val id: String, val observedAt: String, val zoneId: String,
    val latitude: Double?, val longitude: Double?, val notes: String, val quantity: Int,
    val tripId: String?, val outingId: String?, val createdAt: String, val updatedAt: String,
)
