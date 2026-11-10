@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package it.faunavia.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import it.faunavia.domain.MAX_MEMORY_VIDEO_BYTES
import it.faunavia.domain.MemoryVideo
import it.faunavia.domain.MemoryVideoRepository
import it.faunavia.domain.VideoTrim
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class VideoFailure(message: String) : IOException(message)
internal data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)
internal data class StagedVideo(val file: File, val info: VideoInfo, val generation: Long)

internal object VideoInspection {
    fun info(file: File): VideoInfo {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.absolutePath)
            if (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                throw VideoFailure("Seleziona un video leggibile su questo dispositivo.")
            }
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (duration !in 1..604_800_000L || width !in 1..8192 || height !in 1..8192) {
                throw VideoFailure("Durata o dimensioni del video non supportate.")
            }
            return VideoInfo(duration, width, height)
        } finally { reader.release() }
    }

    fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validate(video: MemoryVideo, file: File) {
        if (!file.isFile || file.length() != video.byteSize || hash(file) != video.sha256 ||
            info(file) != VideoInfo(video.durationMs, video.width, video.height)) {
            throw VideoFailure("La copia del video manca o è danneggiata. Puoi sostituirla o rimuoverla; il ricordo è conservato.")
        }
    }
}

/** Bounded staging and frame-accurate local export; no original metadata is copied to the MP4. */
internal class PrivateVideoStore(
    private val context: Context,
    private val mediaLock: PrivatePhotoStore,
    private val root: File = context.noBackupFilesDir,
    private val availableBytes: () -> Long = { StatFs(root.absolutePath).availableBytes },
) {
    companion object { const val MAX_SOURCE_BYTES = 2L * 1024 * 1024 * 1024; private const val RESERVE = 96L * 1024 * 1024 }

    fun file(path: String): File {
        require(path.matches(Regex("videos/[a-f0-9-]{36}/clip\\.mp4")))
        return File(root, path).canonicalFile.also {
            require(it.path.startsWith(root.canonicalPath + File.separator))
        }
    }

    suspend fun stage(uri: Uri): StagedVideo {
        val generation = mediaLock.restoreGeneration
        return withContext(Dispatchers.IO) {
            val directory = File(root, "video-staging/${UUID.randomUUID()}")
            if (!directory.mkdirs()) throw VideoFailure("Non riesco a preparare il video. Libera spazio e riprova.")
            val source = File(directory, "source")
            try {
                if (availableBytes() < RESERVE) throw VideoFailure("Spazio insufficiente per preparare il video.")
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw VideoFailure("Il video non è più accessibile. Selezionalo di nuovo.")
                    FileOutputStream(source).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            total += count
                            if (total > MAX_SOURCE_BYTES) throw VideoFailure("Scegli un video originale fino a 2 GB.")
                            if (availableBytes() < RESERVE) throw VideoFailure("Spazio insufficiente. Libera spazio e riprova.")
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                StagedVideo(source, VideoInspection.info(source), generation)
            } catch (failure: Throwable) { directory.deleteRecursively(); throw failure }
        }
    }

    suspend fun export(source: StagedVideo, trim: VideoTrim, destination: File, progress: (Int) -> Unit) {
        trim.validateFor(source.info.durationMs)
        if (availableBytes() < MAX_MEMORY_VIDEO_BYTES + RESERVE) throw VideoFailure("Libera almeno 200 MB per preparare lo spezzone.")
        if (!destination.parentFile!!.mkdirs()) throw VideoFailure("Non riesco a preparare la copia del video.")
        // Transformer belongs to the application looper. Cancellation always stops codecs before cleanup.
        var activeTransformer: Transformer? = null
        try { withContext(Dispatchers.Main.immediate) {
            withTimeout(5 * 60_000L) {
                suspendCancellableCoroutine { continuation ->
                    val encoder = DefaultEncoderFactory.Builder(context).build()
                    val handler = Handler(Looper.getMainLooper())
                    val token = Any()
                    val transformer = Transformer.Builder(context)
                        .setUsePlatformDiagnostics(false)
                        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(object : Codec.EncoderFactory by encoder {
                            override fun videoNeedsEncoding() = true
                            override fun audioNeedsEncoding() = true
                        })
                        .setMuxerFactory(InAppMp4Muxer.Factory { metadata -> metadata.clear() })
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                handler.removeCallbacksAndMessages(token)
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                handler.removeCallbacksAndMessages(token)
                                if (continuation.isActive) continuation.resumeWithException(VideoFailure("Non riesco a preparare questo formato video. Prova uno spezzone più corto o un altro video."))
                            }
                        }).build()
                    activeTransformer = transformer
                    continuation.invokeOnCancellation {
                        handler.removeCallbacksAndMessages(token)
                        if (Looper.myLooper() == Looper.getMainLooper()) transformer.cancel()
                        else handler.post { transformer.cancel() }
                    }
                    fun poll() {
                        if (!continuation.isActive) return
                        if (destination.length() > MAX_MEMORY_VIDEO_BYTES) {
                            transformer.cancel()
                            continuation.resumeWithException(VideoFailure("Lo spezzone supera 100 MB. Accorcia la selezione e riprova."))
                            return
                        }
                        val holder = ProgressHolder()
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress)
                        handler.postAtTime({ poll() }, token, android.os.SystemClock.uptimeMillis() + 250)
                    }
                    try {
                        val item = MediaItem.Builder().setUri(Uri.fromFile(source.file))
                            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(trim.startMs).setEndPositionMs(trim.endMs).build()).build()
                        transformer.start(EditedMediaItem.Builder(item).build(), destination.absolutePath)
                        poll()
                    } catch (failure: Exception) {
                        handler.removeCallbacksAndMessages(token); transformer.cancel()
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            }
        } } finally { withContext(Dispatchers.Main.immediate + NonCancellable) { activeTransformer?.cancel() } }
    }

    fun delete(video: MemoryVideo): Boolean = try {
        file(video.relativePath).let { !it.exists() || it.delete() }
    } catch (_: IOException) { false } catch (_: SecurityException) { false }

    fun recover(referenced: List<MemoryVideo>): Boolean {
        val keep = referenced.map { file(it.relativePath).path }.toSet()
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        var clean = true
        for (folder in listOf("videos", "video-staging")) {
            File(root, folder).walkTopDown().filter { it.isFile && it.lastModified() < cutoff && it.canonicalPath !in keep }
                .forEach { if (!it.delete()) clean = false }
            File(root, folder).walkBottomUp().filter { it.isDirectory && it != File(root, folder) && it.listFiles()?.isEmpty() == true && it.lastModified() < cutoff }
                .forEach { if (!it.delete()) clean = false }
        }
        return clean
    }
}

internal class MemoryVideos(val store: PrivateVideoStore, private val repository: MemoryVideoRepository, private val mediaLock: PrivatePhotoStore) {
    suspend fun get(id: String) = repository.get(id)
    suspend fun attach(id: String, source: StagedVideo, trim: VideoTrim, progress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (source.generation != mediaLock.restoreGeneration) throw VideoFailure("I dati sono stati ripristinati. Riapri il ricordo e seleziona il video.")
        val path = "videos/${UUID.randomUUID()}/clip.mp4"
        val target = store.file(path)
        var committed = false
        try {
            store.export(source, trim, target, progress)
            if (target.length() !in 1..MAX_MEMORY_VIDEO_BYTES) throw VideoFailure("Lo spezzone supera 100 MB. Accorcia la selezione e riprova.")
            FileOutputStream(target, true).use { it.fd.sync() }
            val info = VideoInspection.info(target)
            val video = MemoryVideo(id, path, VideoInspection.hash(target), target.length(), info.durationMs, info.width, info.height)
            withContext(NonCancellable) { mediaLock.mutex.withLock {
                if (source.generation != mediaLock.restoreGeneration) throw VideoFailure("I dati sono stati ripristinati. Riapri il ricordo e seleziona il video.")
                val old = repository.get(id)
                repository.save(video)
                committed = true
                old == null || store.delete(old)
            } }
        } finally { if (!committed) { target.delete(); target.parentFile?.delete() } }
    }

    suspend fun remove(id: String): Boolean {
        val generation = mediaLock.restoreGeneration
        return withContext(Dispatchers.IO + NonCancellable) { mediaLock.mutex.withLock {
            if (generation != mediaLock.restoreGeneration) throw VideoFailure("I dati sono stati ripristinati. Riapri il ricordo.")
            repository.remove(id)?.let(store::delete) ?: true
        } }
    }
    suspend fun recover(): Boolean = withContext(Dispatchers.IO) { mediaLock.mutex.withLock { store.recover(repository.all()) } }
}
