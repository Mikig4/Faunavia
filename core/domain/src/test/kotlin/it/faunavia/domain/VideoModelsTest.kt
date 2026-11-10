package it.faunavia.domain

import org.junit.Assert.*
import org.junit.Test

class VideoModelsTest {
    @Test fun trimUsesExplicitNonemptyBoundariesInsideTheSource() {
        val trim = VideoTrim(2350, 3650)
        assertEquals(1300L, trim.durationMs)
        trim.validateFor(6000)
        assertThrows(IllegalArgumentException::class.java) { VideoTrim(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { VideoTrim(10, 10) }
        assertThrows(IllegalArgumentException::class.java) { VideoTrim(20, 10) }
        assertThrows(IllegalArgumentException::class.java) { trim.validateFor(3000) }
    }

    @Test fun privateVideoRejectsEscapingPathsAndExcessiveBytes() {
        val video = MemoryVideo("memory", "videos/00000000-0000-0000-0000-000000000000/clip.mp4", "a".repeat(64), 2048, 1300, 160, 120)
        assertEquals(1300L, video.durationMs)
        assertThrows(IllegalArgumentException::class.java) { video.copy(relativePath = "videos/../clip.mp4") }
        assertThrows(IllegalArgumentException::class.java) { video.copy(byteSize = MAX_MEMORY_VIDEO_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { video.copy(durationMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { video.copy(sha256 = "invalid") }
    }
}
