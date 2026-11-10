package it.faunavia.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MemoryCover(val bitmap: Bitmap?, val video: Boolean = false)

/** Decode only a visible row's first gallery photo, or the saved video's opening frame. */
@Composable
internal fun MemoryMediaPreview(memoryId: String, manager: MemoryPhotos, refresh: Int, onOpen: () -> Unit,
    openLabel: String = "Apri foto e video") {
    val cover by produceState<MemoryCover?>(null, memoryId, manager, refresh) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                val photo = manager.list(memoryId).firstOrNull()
                if (photo != null) MemoryCover(manager.store.load(photo, thumbnail = true))
                else manager.videos?.let { videos ->
                    videos.get(memoryId)?.let { video -> MemoryCover(videoCover(videos.store.file(video.relativePath)), video = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { MemoryCover(null) }
        }
    }
    val preview = cover ?: return
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
        .testTag("diary-media-$memoryId")) {
        val bitmap = preview.bitmap
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), if (preview.video) "Anteprima video del ricordo" else "Prima foto del ricordo",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp)
                    .testTag("diary-media-image-$memoryId"))
            if (preview.video) Text("▶ Video", color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.75f)).padding(8.dp)
                    .testTag("diary-media-video-$memoryId"))
        } else Text("Anteprima non disponibile · $openLabel", color = FaunaviaColors.Muted,
            modifier = Modifier.padding(8.dp).testTag("diary-media-unavailable-$memoryId"))
    }
}

private fun videoCover(file: File): Bitmap? {
    val reader = MediaMetadataRetriever()
    try {
        reader.setDataSource(file.absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
        }
        val frame = reader.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val scale = 640f / maxOf(frame.width, frame.height)
        if (scale >= 1f) return frame
        return frame.scale((frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1)).also { if (it !== frame) frame.recycle() }
    } finally { reader.release() }
}
