package it.faunavia.app

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import it.faunavia.domain.PersonalModel
import it.faunavia.domain.PersonalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal data class ModelCredit(val author: String, val source: String, val license: String, val modifications: String) {
    fun checked(): ModelCredit {
        val values = listOf(author, source, license, modifications).map(String::trim)
        if (values.any { it.isBlank() || it.length > 2000 }) throw ModelFailure("Compila autore, fonte, licenza e modifiche (massimo 2.000 caratteri ciascuno).")
        return ModelCredit(values[0], values[1], values[2], values[3])
    }
}
internal data class LoadedModel(val bytes: ByteArray, val info: GlbInfo, val personal: PersonalModel?, val credit: String)

internal class PersonalModelStore(private val context: Context, val repository: PersonalModelRepository,
    private val photos: PrivatePhotoStore, private val root: File = context.noBackupFilesDir,
    private val openInput: (Uri) -> InputStream = { context.contentResolver.openInputStream(it) ?: throw ModelFailure("File non accessibile.") },
    private val availableBytes: () -> Long = { StatFs(root.absolutePath).availableBytes },
) {
    fun file(path: String): File {
        require(path.matches(Regex("models/[a-f0-9-]{36}/model\\.glb")))
        return File(root, path).canonicalFile.also { require(it.path.startsWith(root.canonicalPath + File.separator)) }
    }
    suspend fun load(taxonId: String, scientificName: String): LoadedModel? = withContext(Dispatchers.IO) {
        photos.mutex.withLock {
            val personal = repository.get(taxonId)
            if (personal != null) {
                val target = file(personal.relativePath)
                if (!target.isFile || target.length() != personal.byteSize) throw ModelFailure("Modello personale mancante. Reimportalo o rimuovilo; la scheda 2D resta disponibile.")
                val bytes = bounded(target.inputStream())
                if (sha256(bytes) != personal.sha256) throw ModelFailure("Modello personale corrotto. Reimportalo o rimuovilo; la scheda 2D resta disponibile.")
                LoadedModel(bytes, GlbInspection.inspect(bytes), personal,
                    "${personal.author}\nFonte: ${personal.source}\nLicenza: ${personal.license}\nModifiche: ${personal.modifications}\nImportato ${personal.importedAt}\nProvenienza e diritti dichiarati dall’utente; associazione illustrativa personale.")
            } else if (scientificName.trim().equals("Turdus merula", ignoreCase = true)) {
                val bytes = bounded(context.assets.open("models/blackbird-v1.glb"))
                val manifest = org.json.JSONObject(context.assets.open("asset-manifest.json").bufferedReader().use { it.readText() }).getJSONArray("assets")
                val entry = (0 until manifest.length()).map(manifest::getJSONObject).single { it.optString("path") == "models/blackbird-v1.glb" }
                if (sha256(bytes) != entry.getString("sha256")) throw ModelFailure("Modello incluso non leggibile; la scheda 2D resta disponibile.")
                LoadedModel(bytes, GlbInspection.inspect(bytes), null,
                    "Merlo maschio stilizzato · modello illustrativo, non identificativo\n${entry.getString("author")} · ${entry.getString("license")}\n${entry.getString("source")}\nVersione ${entry.getString("version")} · revisione umana da completare")
            } else null
        }
    }
    suspend fun import(taxonId: String, scientificName: String, uri: Uri, credit: ModelCredit): PersonalModel {
        val generation = photos.restoreGeneration
        val checked = credit.checked()
        return withContext(Dispatchers.IO + NonCancellable) { photos.mutex.withLock {
            if (generation != photos.restoreGeneration) throw ModelFailure("Dati ripristinati: riapri la scheda e scegli nuovamente il modello.")
            if (availableBytes() < GlbInspection.MAX_BYTES * 2L + 64L * 1024 * 1024) throw ModelFailure("Spazio insufficiente. Il modello precedente è conservato.")
            val bytes = bounded(openInput(uri))
            GlbInspection.inspect(bytes)
            validateModelResources(bytes)
            val path = "models/${UUID.randomUUID()}/model.glb"
            val model = PersonalModel(taxonId, scientificName, path, sha256(bytes), bytes.size.toLong(),
                checked.author, checked.source, checked.license, checked.modifications, Instant.now())
            if (repository.all().size >= 100 && repository.get(taxonId) == null) throw ModelFailure("La libreria contiene già 100 modelli. Rimuovine uno prima di importare.")
            val target = file(path)
            if (!target.parentFile!!.mkdirs()) throw ModelFailure("Non riesco a preparare il modello.")
            try {
                FileOutputStream(target).use { it.write(bytes); it.fd.sync() }
                repository.save(model)
                // Old complete files remain for the same recovery grace as photos; no open viewer loses its bytes.
                model
            } catch (failure: Throwable) {
                if (!target.parentFile!!.deleteRecursively()) failure.addSuppressed(ModelFailure("Pulizia del modello temporaneo da riprovare."))
                throw failure
            }
        } }
    }
    suspend fun remove(taxonId: String) {
        val generation = photos.restoreGeneration
        withContext(Dispatchers.IO + NonCancellable) { photos.mutex.withLock {
            if (generation != photos.restoreGeneration) throw ModelFailure("Dati ripristinati: riapri la scheda.")
            repository.remove(taxonId)
        } }
    }
    suspend fun recover(): Boolean = withContext(Dispatchers.IO) { photos.mutex.withLock {
        val referenced = repository.all().map { file(it.relativePath).canonicalPath }.toSet()
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        File(root, "models").listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) }
            .map { directory ->
                val model = File(directory, "model.glb")
                if (model.canonicalPath !in referenced && directory.lastModified() < cutoff && model.lastModified() < cutoff) directory.deleteRecursively() else true
            }.all { it }
    } }
    private fun bounded(input: InputStream): ByteArray = input.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > GlbInspection.MAX_BYTES) throw ModelFailure("GLB superiore a 20 MB.")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Use the renderer's resource parser before publishing a replacement or restoring its database references. */
internal fun validateModelResources(bytes: ByteArray) {
    Utils.init()
    val engine = Engine.create()
    val provider = UbershaderProvider(engine)
    val loader = AssetLoader(engine, provider, EntityManager.get())
    val resources = ResourceLoader(engine)
    var asset: com.google.android.filament.gltfio.FilamentAsset? = null
    try {
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
        asset = loader.createAsset(buffer) ?: throw ModelFailure("Geometria GLB non leggibile dal viewer.")
        if (asset.resourceUris.isNotEmpty()) throw ModelFailure("Risorse GLB non supportate o incomplete.")
        resources.loadResources(asset)
        val extent = asset.boundingBox.halfExtent
        if (extent.any { !it.isFinite() || it < 0 } || extent.maxOrNull() == 0f || asset.renderableEntities.isEmpty()) {
            throw ModelFailure("Il modello non ha geometria visibile valida.")
        }
    } finally {
        asset?.let(loader::destroyAsset)
        resources.destroy(); loader.destroy(); provider.destroyMaterials(); provider.destroy(); engine.destroy()
    }
}
