package it.faunavia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import android.net.Uri
import android.os.StatFs
import it.faunavia.domain.DiaryRepository
import it.faunavia.domain.ObservationPhoto
import it.faunavia.domain.UnidentifiedRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PhotoFailure(message: String) : IOException(message)

/** Only freshly encoded pixels leave staging; original metadata and URIs are never stored in Room. */
internal class PrivatePhotoStore(
    context: Context,
    private val root: File = context.noBackupFilesDir,
    private val openInput: (Uri) -> InputStream = { uri ->
        context.contentResolver.openInputStream(uri) ?: throw PhotoFailure("Questa foto non è più accessibile.")
    },
    private val availableBytes: () -> Long = { StatFs(root.absolutePath).availableBytes },
) {
    companion object {
        const val MAX_INPUT_BYTES = 32L * 1024 * 1024
        const val MAX_EDGE = 2048
        const val THUMB_EDGE = 320
        private const val RESERVE_BYTES = 48L * 1024 * 1024
        private const val ORPHAN_GRACE_MILLIS = 24L * 60 * 60 * 1000
    }

    // Shared by every manager in this application, including the trip Diary surface.
    val mutex = Mutex()
    @Volatile var restoreGeneration = 0L

    fun file(relativePath: String): File {
        require(relativePath.startsWith("photos/") && ':' !in relativePath && '\\' !in relativePath)
        require(relativePath.split('/').none { it.isEmpty() || it == "." || it == ".." })
        val resolved = File(root, relativePath).canonicalFile
        require(resolved.path.startsWith(root.canonicalPath + File.separator))
        return resolved
    }

    fun import(uri: Uri, memoryId: String): ObservationPhoto {
        val id = UUID.randomUUID().toString()
        val directory = File(root, "photos")
        if (!directory.exists() && !directory.mkdirs()) throw PhotoFailure("Non riesco a creare lo spazio per le foto.")
        if (availableBytes() < RESERVE_BYTES) throw PhotoFailure("Spazio insufficiente. Libera spazio e riprova; il ricordo è conservato.")
        val staging = File(directory, "$id.source.tmp")
        val image = file("photos/$id.jpg")
        val thumbnail = file("photos/$id-thumb.jpg")
        var decoded: Bitmap? = null
        var rotated: Bitmap? = null
        var flattened: Bitmap? = null
        var preview: Bitmap? = null
        try {
            openInput(uri).use { input ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_INPUT_BYTES) throw PhotoFailure("Foto troppo grande: scegli un file fino a 32 MB.")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staging.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) {
                throw PhotoFailure("Immagine non leggibile o troppo grande. Scegli un’altra foto.")
            }
            var sample = 1
            while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > MAX_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            decoded = BitmapFactory.decodeFile(staging.absolutePath, options)
                ?: throw PhotoFailure("Immagine corrotta o formato non supportato.")
            val orientation = ExifInterface(staging.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL).coerceIn(1, 8)
            rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix(orientation), true)
            flattened = createBitmap(rotated.width, rotated.height)
            Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(rotated, 0f, 0f, null) }
            encode(flattened, image, 85)
            val factor = minOf(1.0, THUMB_EDGE.toDouble() / maxOf(flattened.width, flattened.height))
            preview = flattened.scale(maxOf(1, (flattened.width * factor).toInt()),
                maxOf(1, (flattened.height * factor).toInt()))
            encode(preview, thumbnail, 75)
            return ObservationPhoto(id, memoryId, "photos/${image.name}", hash(image), image.length(), "image/jpeg",
                flattened.width, flattened.height, 1, "photos/${thumbnail.name}")
        } catch (failure: Throwable) {
            val cleaned = removeFile(image) and removeFile(thumbnail)
            if (!cleaned) failure.addSuppressed(IOException("Pulizia dei file parziali non completata."))
            when (failure) {
                is SecurityException -> throw PhotoFailure("Accesso alla foto scaduto. Selezionala di nuovo; il ricordo è conservato.")
                is PhotoFailure -> throw failure
                is OutOfMemoryError -> throw PhotoFailure("Foto troppo complessa per il dispositivo. Scegli una versione più piccola.")
                is IOException -> throw PhotoFailure("Non riesco a leggere o copiare la foto. Controlla il file e lo spazio disponibile.")
                else -> throw failure
            }
        } finally {
            listOfNotNull(preview, flattened, rotated, decoded).distinct().forEach { it.recycle() }
            if (!removeFile(staging)) throw PhotoFailure("Non riesco a rimuovere la copia temporanea. Riprova dopo aver liberato spazio.")
        }
    }

    private fun encode(bitmap: Bitmap, destination: File, quality: Int) {
        FileOutputStream(destination).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw PhotoFailure("Non riesco a preparare la foto.")
            output.fd.sync()
        }
    }

    fun load(photo: ObservationPhoto, thumbnail: Boolean): Bitmap? {
        val original = file(photo.relativePath)
        if (!original.isFile || original.length() != photo.byteSize || hash(original) != photo.sha256) return null
        val target = if (thumbnail) photo.thumbnailPath?.let(::file)?.takeIf { it.isFile } ?: original else original
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val edge = if (thumbnail) THUMB_EDGE else MAX_EDGE
        while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > edge) sample *= 2
        return BitmapFactory.decodeFile(target.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun delete(photo: ObservationPhoto): Boolean = try {
        removeFile(file(photo.relativePath)) and (photo.thumbnailPath?.let { removeFile(file(it)) } ?: true)
    } catch (_: Exception) {
        // Metadata may already be gone: return a visible cleanup warning rather than a false DB failure.
        false
    }

    /** Crash leftovers are removed only after a grace period, and only when no Room reference exists. */
    fun recover(referenced: List<ObservationPhoto>, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val keep = referenced.flatMap { listOfNotNull(it.relativePath, it.thumbnailPath) }.toSet()
        var success = true
        val directory = File(root, "photos")
        directory.walkBottomUp().filter { it.isFile }.forEach { candidate ->
            val path = candidate.relativeTo(root).invariantSeparatorsPath
            if (path !in keep && nowMillis - candidate.lastModified() > ORPHAN_GRACE_MILLIS) {
                success = removeFile(candidate) and success
            }
        }
        directory.walkBottomUp().filter { it.isDirectory && it != directory }.forEach {
            if (it.listFiles()?.isEmpty() == true) success = it.delete() and success
        }
        return success
    }

    private fun removeFile(file: File): Boolean = !file.exists() || file.delete()

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        val values = when (orientation) {
            2 -> floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            3 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
            4 -> floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
            5 -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            6 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            7 -> floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            8 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            else -> return@apply
        }
        setValues(values)
    }
}

/** Files first, metadata second. DB failures remove the new files; deletion reverses that order. */
internal class MemoryPhotos(
    val store: PrivatePhotoStore,
    private val diary: DiaryRepository,
    private val drafts: UnidentifiedRepository?,
) {
    suspend fun list(id: String): List<ObservationPhoto> =
        if (diary.get(id) != null) diary.photos(id) else drafts?.photos(id).orEmpty()

    suspend fun attach(id: String, uri: Uri): ObservationPhoto {
        val generation = store.restoreGeneration
        return withContext(Dispatchers.IO + NonCancellable) {
            store.mutex.withLock {
                if (generation != store.restoreGeneration) throw PhotoFailure("I dati sono stati ripristinati. Apri il ricordo e seleziona di nuovo la foto.")
                val identified = diary.get(id) != null
                if (!identified && drafts?.get(id) == null) throw PhotoFailure("Salva prima il ricordo; poi aggiungi le foto.")
                val photo = store.import(uri, id)
                try {
                    if (identified) diary.addPhoto(photo) else requireNotNull(drafts).addPhoto(photo)
                    photo
                } catch (failure: Throwable) {
                    if (!store.delete(photo)) failure.addSuppressed(IOException("File non collegato: pulizia da riprovare."))
                    throw failure
                }
            }
        }
    }

    suspend fun delete(id: String, photoId: String): Boolean {
        val generation = store.restoreGeneration
        return withContext(Dispatchers.IO + NonCancellable) {
            store.mutex.withLock {
                if (generation != store.restoreGeneration) throw PhotoFailure("I dati sono stati ripristinati. Apri di nuovo il ricordo.")
                val metadata = list(id).firstOrNull { it.id == photoId } ?: return@withLock true
                if (diary.get(id) != null) diary.deletePhoto(photoId) else requireNotNull(drafts).deletePhoto(photoId)
                store.delete(metadata)
            }
        }
    }

    suspend fun deleteMemory(id: String, identified: Boolean): Boolean {
        val generation = store.restoreGeneration
        return withContext(Dispatchers.IO + NonCancellable) {
            store.mutex.withLock {
                if (generation != store.restoreGeneration) throw PhotoFailure("I dati sono stati ripristinati. Apri di nuovo il ricordo.")
                val removed = if (identified) diary.delete(id) else requireNotNull(drafts).deleteWithPhotos(id)
                removed.map { store.delete(it) }.all { it }
            }
        }
    }

    suspend fun recover(): Boolean = withContext(Dispatchers.IO) {
        store.mutex.withLock {
            val referenced = diary.list().flatMap { diary.photos(it.id) } +
                drafts?.list().orEmpty().flatMap { requireNotNull(drafts).photos(it.input.id) }
            store.recover(referenced)
        }
    }
}
