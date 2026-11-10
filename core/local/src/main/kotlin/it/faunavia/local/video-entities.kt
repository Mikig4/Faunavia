package it.faunavia.local

import androidx.room.Entity
import androidx.room.ForeignKey
import it.faunavia.domain.MemoryVideo

@Entity(tableName = "observation_videos", primaryKeys = ["memoryId"],
    foreignKeys = [ForeignKey(entity = ObservationRow::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE)])
data class ObservationVideoRow(val memoryId: String, val relativePath: String, val sha256: String,
    val byteSize: Long, val durationMs: Long, val width: Int, val height: Int)

@Entity(tableName = "draft_videos", primaryKeys = ["memoryId"],
    foreignKeys = [ForeignKey(entity = UnidentifiedRow::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE)])
data class DraftVideoRow(val memoryId: String, val relativePath: String, val sha256: String,
    val byteSize: Long, val durationMs: Long, val width: Int, val height: Int)

internal fun ObservationVideoRow.toDomain() = MemoryVideo(memoryId, relativePath, sha256, byteSize, durationMs, width, height)
internal fun DraftVideoRow.toDomain() = MemoryVideo(memoryId, relativePath, sha256, byteSize, durationMs, width, height)
internal fun MemoryVideo.toObservationRow() = ObservationVideoRow(memoryId, relativePath, sha256, byteSize, durationMs, width, height)
internal fun MemoryVideo.toDraftVideoRow() = DraftVideoRow(memoryId, relativePath, sha256, byteSize, durationMs, width, height)
