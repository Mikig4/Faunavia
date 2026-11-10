package it.faunavia.domain

const val MAX_MEMORY_VIDEO_BYTES = 100L * 1024 * 1024

/** One private MP4 per stable memory, independent from its identification status. */
data class MemoryVideo(
    val memoryId: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
) {
    init {
        require(memoryId.isNotBlank())
        require(relativePath.matches(Regex("videos/[a-f0-9-]{36}/clip\\.mp4")))
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(byteSize in 1..MAX_MEMORY_VIDEO_BYTES && durationMs > 0 && width > 0 && height > 0)
    }
}

data class VideoTrim(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
    val durationMs: Long get() = endMs - startMs
    fun validateFor(durationMs: Long) { require(endMs <= durationMs) }
}

interface MemoryVideoRepository {
    suspend fun get(memoryId: String): MemoryVideo?
    suspend fun all(): List<MemoryVideo>
    /** Replaces metadata only after the new complete file has been published. */
    suspend fun save(video: MemoryVideo)
    suspend fun remove(memoryId: String): MemoryVideo?
}
