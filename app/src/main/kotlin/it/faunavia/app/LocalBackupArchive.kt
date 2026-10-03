package it.faunavia.app

import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import it.faunavia.local.LocalBackupSnapshot
import it.faunavia.local.LocalBackupStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class BackupFailure(message: String) : IOException(message)
internal data class BackupInventory(val path: String, val bytes: Long, val sha256: String)
internal data class BackupPreview(val createdAt: Instant, val schema: Int, val counts: Map<String, Int>, val bytes: Long) {
    private fun count(table: String, one: String, many: String): String = (counts[table] ?: 0).let { "$it ${if (it == 1) one else many}" }
    val summary: String get() = listOf(count("observations", "avvistamento", "avvistamenti"), count("unidentified_drafts", "bozza", "bozze"),
        count("trips", "viaggio", "viaggi"), count("outings", "uscita", "uscite"),
        "${(counts["observation_photos"] ?: 0) + (counts["draft_photos"] ?: 0)} foto",
        "${counts["personal_models"] ?: 0} modelli 3D").joinToString(", ")
}
internal class PreparedBackup(val directory: File, val snapshot: LocalBackupSnapshot,
    val inventory: List<BackupInventory>, val preview: BackupPreview) {
    var consumed = false
    fun discard(): Boolean { consumed = true; return directory.deleteRecursively() }
}

/** Only complete, verified private copies are made visible by the Room commit. */
internal class LocalBackupArchive(
    private val context: Context,
    private val database: LocalBackupStore,
    private val photos: PrivatePhotoStore,
    private val availableBytes: () -> Long = { StatFs(context.noBackupFilesDir.absolutePath).availableBytes },
    private val beforeCommit: () -> Unit = {},
    private val models: PersonalModelStore? = null,
) {
    companion object {
        const val FORMAT = 2
        const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        private const val RESERVE = 64L * 1024 * 1024
        private const val MAX_MANIFEST = 2L * 1024 * 1024
        private const val MAX_FILES = 10_001
    }
    @Volatile var lastMessage: String? = null
    @Volatile var cleanupWarning: String? = null

    private fun cleanup(directory: File) {
        if (!directory.deleteRecursively()) cleanupWarning = "Pulizia temporanea incompleta: libera spazio e riprova un backup."
    }

    private fun recoverTemporary() {
        cleanupWarning = null
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        context.cacheDir.listFiles().orEmpty().filter {
            it.isDirectory && it.name.matches(Regex("backup-[a-f0-9-]{36}")) && it.lastModified() < cutoff
        }.forEach(::cleanup)
        context.databaseList().filter { it.matches(Regex("backup-stage-[a-f0-9-]{36}\\.db")) }.forEach {
            if (context.getDatabasePath(it).lastModified() < cutoff && !context.deleteDatabase(it)) {
                cleanupWarning = "Pulizia del database temporaneo incompleta. Libera spazio e riprova."
            }
        }
    }

    private fun staging(): File = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply {
        if (!mkdirs()) throw BackupFailure("Non riesco a preparare lo spazio temporaneo.")
    }

    suspend fun export(output: OutputStream, progress: (String) -> Unit = {}): BackupPreview = withContext(Dispatchers.IO) {
        recoverTemporary()
        photos.mutex.withLock {
            val stage = staging()
            try {
                progress("Preparazione del database…")
                val snapshot = database.snapshot()
                requireSpace(snapshot.payload.toByteArray(Charsets.UTF_8).size.toLong() + RESERVE)
                val data = File(stage, "database.json")
                writeSynced(data, snapshot.payload.toByteArray(Charsets.UTF_8))
                val paths = snapshot.photos.flatMap { listOfNotNull(it.relativePath, it.thumbnailPath) }.toSet().sorted()
                val files = linkedMapOf("database.json" to data)
                paths.forEach { path -> files[path] = photos.file(path) }
                snapshot.models.forEach { model -> files[model.relativePath] = requireNotNull(models) { "Model store unavailable." }.file(model.relativePath) }
                if (files.size > MAX_FILES) throw BackupFailure("Il backup supera il limite di 10.000 file foto.")
                val inventory = files.map { (path, file) ->
                    checkedPath(path)
                    if (!file.isFile) throw BackupFailure("Una foto del diario manca. Ripristinala o rimuovi il riferimento prima di esportare.")
                    BackupInventory(path, file.length(), hash(file)).also(::checkInventory)
                }
                validatePhotos(snapshot, inventory) { photos.file(it) }
                validateModels(snapshot, inventory) { requireNotNull(models).file(it) }
                val total = inventory.sumOf { it.bytes }
                if (total > MAX_BYTES) throw BackupFailure("Il backup supera il limite di 2 GB.")
                val preview = BackupPreview(Instant.now(), LocalBackupStore.SCHEMA, snapshot.counts, total)
                val manifest = manifest(preview, inventory).toString().toByteArray(Charsets.UTF_8)
                if (manifest.size > MAX_MANIFEST) throw BackupFailure("Inventario del backup troppo grande.")
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                    files.entries.forEachIndexed { index, (path, file) ->
                        progress("Scrittura del backup: ${index + 1}/${files.size} file…")
                        zip.putNextEntry(ZipEntry(path))
                        file.inputStream().use { copyChecked(it, zip, inventory[index].bytes) }
                        zip.closeEntry()
                    }
                }
                preview
            } finally { cleanup(stage) }
        }
    }

    suspend fun prepare(input: InputStream, progress: (String) -> Unit = {}): PreparedBackup = withContext(Dispatchers.IO) {
        recoverTemporary()
        val stage = staging()
        try {
            val inventory: List<BackupInventory>
            val preview: BackupPreview
            progress("Lettura dell’archivio…")
            val raw = File(stage, "archive.zip")
            FileOutputStream(raw).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES + 16L * 1024 * 1024) throw BackupFailure("Archivio troppo grande.")
                    requireSpace(count.toLong() + RESERVE)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            // ZipFile also requires the central directory; a truncated ZIP tail must not pass as a backup.
            ZipFile(raw).use { zip ->
                progress("Controllo dell’archivio…")
                val entries = mutableListOf<ZipEntry>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    if (entries.size >= MAX_FILES + 1) throw BackupFailure("L’archivio contiene troppi file.")
                    entries.add(enumeration.nextElement())
                }
                val header = entries.firstOrNull()
                if (header?.name != "manifest.json" || header.size !in 1..MAX_MANIFEST) throw BackupFailure("Questo file non è un backup Faunavia valido.")
                val manifest = zip.getInputStream(header).use { JSONObject(readBounded(it, MAX_MANIFEST).toString(Charsets.UTF_8)) }
                val format = manifest.getInt("formatVersion")
                if (manifest.getString("application") != "it.faunavia.app" || format !in 1..FORMAT) {
                    throw BackupFailure("Versione dell’archivio non supportata. Usa una versione compatibile di Faunavia.")
                }
                val schema = manifest.getInt("databaseSchema")
                if (schema !in 8..LocalBackupStore.SCHEMA) throw BackupFailure("Versione del database non supportata.")
                if (format == 1 && schema > 9) throw BackupFailure("Versioni di archivio e database incompatibili.")
                val listed = manifest.getJSONArray("files")
                if (listed.length() !in 1..MAX_FILES) throw BackupFailure("Inventario non valido o troppo grande.")
                inventory = (0 until listed.length()).map { i -> listed.getJSONObject(i).let {
                    BackupInventory(it.getString("path"), it.getLong("bytes"), it.getString("sha256")).also(::checkInventory)
                } }
                if (format == 1 && inventory.any { it.path.startsWith("models/") }) throw BackupFailure("Il formato precedente non supporta modelli 3D.")
                if (inventory.map { it.path }.distinct().size != inventory.size || inventory.count { it.path == "database.json" } != 1) {
                    throw BackupFailure("L’archivio contiene file duplicati o manca il database.")
                }
                val total = inventory.sumOf { it.bytes }
                if (total > MAX_BYTES) throw BackupFailure("Il backup supera il limite di 2 GB.")
                requireSpace(total * 3 + RESERVE)
                val countsJson = manifest.getJSONObject("counts")
                val counts = countsJson.keys().asSequence().associateWith { countsJson.getInt(it) }
                val expectedTables = LocalBackupStore.tablesFor(schema)
                if (counts.keys != expectedTables.toSet() || counts.values.any { it < 0 }) throw BackupFailure("Conteggi del backup non validi.")
                preview = BackupPreview(Instant.parse(manifest.getString("createdAt")), schema, counts, total)
                val expected = inventory.associateBy { it.path }
                val seen = mutableSetOf<String>()
                for (entry in entries.drop(1)) {
                    val item = expected[entry.name] ?: throw BackupFailure("L’archivio contiene un file inatteso.")
                    if (entry.isDirectory || !seen.add(entry.name)) throw BackupFailure("L’archivio contiene file duplicati.")
                    if (entry.size != item.bytes) throw BackupFailure("La dimensione di un file non corrisponde all’inventario.")
                    progress("Verifica dei file: ${seen.size}/${inventory.size}…")
                    val target = stagedFile(stage, item.path)
                    if (target.parentFile?.mkdirs() == false && !target.parentFile!!.isDirectory) throw BackupFailure("Spazio temporaneo non disponibile.")
                    zip.getInputStream(entry).use { inputFile -> FileOutputStream(target).use { output ->
                        copyChecked(inputFile, output, item.bytes); output.fd.sync()
                    } }
                    if (hash(target) != item.sha256) throw BackupFailure("Il backup è corrotto: la verifica di un file è fallita.")
                }
                if (seen != expected.keys) throw BackupFailure("Il backup è incompleto: manca un file previsto.")
            }
            progress("Verifica di dati e collegamenti…")
            val payload = File(stage, "database.json").readText(Charsets.UTF_8)
            if (JSONObject(payload).getInt("schemaVersion") != preview.schema) throw BackupFailure("Le versioni del backup non corrispondono.")
            val snapshot = database.validate(context, payload)
            if (preview.counts.any { (table, count) -> snapshot.counts[table] != count }) throw BackupFailure("I conteggi dell’archivio non corrispondono ai dati.")
            validatePhotos(snapshot, inventory) { stagedFile(stage, it) }
            validateModels(snapshot, inventory) { stagedFile(stage, it) }
            PreparedBackup(stage, snapshot, inventory, preview)
        } catch (failure: Throwable) {
            cleanup(stage)
            if (failure is ZipException) throw BackupFailure("Questo file non è un backup Faunavia valido: l’archivio è corrotto o incompleto.")
            throw failure
        }
    }

    suspend fun restore(prepared: PreparedBackup, progress: (String) -> Unit = {}): BackupPreview =
        withContext(Dispatchers.IO + NonCancellable) { photos.mutex.withLock {
            check(!prepared.consumed) { "Backup preview expired." }
            val photoEntries = prepared.inventory.filter { it.path.startsWith("photos/") }
            val modelEntries = prepared.inventory.filter { it.path.startsWith("models/") }
            requireSpace((photoEntries + modelEntries).sumOf { it.bytes } + RESERVE)
            val prefix = "photos/restore-${UUID.randomUUID()}"
            val directory = photos.file("$prefix/placeholder").parentFile!!
            if (!directory.mkdirs()) throw BackupFailure("Non riesco a preparare le foto del ripristino.")
            var committed = false
            val modelDirectories = mutableListOf<File>()
            try {
                val mapping = photoEntries.mapIndexed { i, item -> item.path to "$prefix/$i.image" }.toMap()
                photoEntries.forEachIndexed { i, item ->
                    progress("Preparazione delle foto: ${i + 1}/${photoEntries.size}…")
                    val source = stagedFile(prepared.directory, item.path)
                    val target = photos.file(mapping.getValue(item.path))
                    source.inputStream().use { input -> FileOutputStream(target).use { output ->
                        copyChecked(input, output, item.bytes); output.fd.sync()
                    } }
                    if (hash(target) != item.sha256) throw BackupFailure("La copia delle foto non è riuscita. I dati precedenti sono conservati.")
                }
                val modelMapping = modelEntries.associate { it.path to "models/${UUID.randomUUID()}/model.glb" }
                modelEntries.forEachIndexed { i, item ->
                    progress("Preparazione dei modelli: ${i + 1}/${modelEntries.size}…")
                    val target = requireNotNull(models) { "Model store unavailable." }.file(modelMapping.getValue(item.path))
                    val modelDirectory = target.parentFile!!
                    if (!modelDirectory.mkdirs()) throw BackupFailure("Non riesco a preparare i modelli del ripristino.")
                    modelDirectories.add(modelDirectory)
                    stagedFile(prepared.directory, item.path).inputStream().use { input -> FileOutputStream(target).use { output ->
                        copyChecked(input, output, item.bytes); output.fd.sync()
                    } }
                    if (hash(target) != item.sha256) throw BackupFailure("Copia dei modelli fallita. I dati precedenti sono conservati.")
                }
                progress("Ripristino dei dati…")
                val rewritten = JSONObject(prepared.snapshot.payload)
                val tables = rewritten.getJSONObject("tables")
                for (table in listOf("observation_photos", "draft_photos")) {
                    val content = tables.getJSONObject(table)
                    val columns = content.getJSONArray("columns")
                    val names = (0 until columns.length()).map(columns::getString)
                    val rows = content.getJSONArray("rows")
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONArray(i)
                        for (field in listOf("relativePath", "thumbnailPath")) {
                            val column = names.indexOf(field)
                            if (!row.isNull(column)) row.put(column, mapping.getValue(row.getString(column)))
                        }
                    }
                }
                tables.optJSONObject("personal_models")?.let { content ->
                    val columns = content.getJSONArray("columns")
                    val column = (0 until columns.length()).first { columns.getString(it) == "relativePath" }
                    val rows = content.getJSONArray("rows")
                    for (i in 0 until rows.length()) rows.getJSONArray(i).let { it.put(column, modelMapping.getValue(it.getString(column))) }
                }
                database.replace(rewritten.toString(), beforeCommit)
                committed = true
                photos.restoreGeneration++
                if (!prepared.discard()) cleanupWarning = "Ripristino completato; pulizia dei file temporanei da riprovare dopo aver liberato spazio."
                prepared.preview
            } finally { if (!committed) { cleanup(directory); modelDirectories.forEach(::cleanup) } }
        } }

    private fun manifest(preview: BackupPreview, files: List<BackupInventory>) = JSONObject()
        .put("application", "it.faunavia.app").put("formatVersion", FORMAT).put("databaseSchema", preview.schema)
        .put("createdAt", preview.createdAt.toString()).put("counts", JSONObject(preview.counts))
        .put("files", JSONArray(files.map { JSONObject().put("path", it.path).put("bytes", it.bytes).put("sha256", it.sha256) }))

    private fun checkedPath(path: String) {
        if (path.length > 240 || (path != "database.json" && !path.startsWith("photos/") && !path.matches(Regex("models/[a-f0-9-]{36}/model\\.glb"))) ||
            ':' in path || '\\' in path || path.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            throw BackupFailure("L’archivio contiene un percorso non valido.")
        }
    }
    private fun checkInventory(item: BackupInventory) {
        checkedPath(item.path)
        val limit = when { item.path == "database.json" -> LocalBackupStore.MAX_PAYLOAD_BYTES
            item.path.startsWith("models/") -> GlbInspection.MAX_BYTES.toLong()
            else -> PrivatePhotoStore.MAX_INPUT_BYTES }
        if (item.bytes !in 1..limit || !item.sha256.matches(Regex("[a-f0-9]{64}"))) throw BackupFailure("Inventario file non valido.")
    }
    private fun stagedFile(root: File, path: String): File {
        checkedPath(path)
        return File(root, path).canonicalFile.also {
            if (!it.path.startsWith(root.canonicalPath + File.separator)) throw BackupFailure("Percorso non valido.")
        }
    }
    private fun requireSpace(required: Long) {
        if (availableBytes() < required) throw BackupFailure("Spazio insufficiente. Libera spazio e riprova; i dati attuali sono conservati.")
    }
    private suspend fun copyChecked(input: InputStream, output: OutputStream, expected: Long) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > expected) throw BackupFailure("La dimensione di un file non corrisponde all’inventario.")
            output.write(buffer, 0, count)
        }
        if (total != expected) throw BackupFailure("L’archivio è incompleto: un file è troncato.")
    }
    private suspend fun readBounded(input: InputStream, limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw BackupFailure("Manifest del backup troppo grande.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private suspend fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun writeSynced(file: File, bytes: ByteArray) = FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    private fun validatePhotos(snapshot: LocalBackupSnapshot, inventory: List<BackupInventory>, file: (String) -> File) {
        val referenced = snapshot.photos.flatMap { listOfNotNull(it.relativePath, it.thumbnailPath) }.toSet()
        if (inventory.filterNot { it.path.startsWith("models/") }.map { it.path }.toSet() != referenced + "database.json") throw BackupFailure("Inventario delle foto incompleto o incoerente.")
        val byPath = inventory.associateBy { it.path }
        snapshot.photos.forEach { photo ->
            val item = byPath.getValue(photo.relativePath)
            if (item.sha256 != photo.sha256 || item.bytes != photo.byteSize) throw BackupFailure("Una foto non corrisponde ai metadati del diario.")
            listOfNotNull(photo.relativePath, photo.thumbnailPath).forEach { path ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file(path).absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || maxOf(bounds.outWidth, bounds.outHeight) > PrivatePhotoStore.MAX_EDGE ||
                    (path == photo.relativePath && photo.width > 0 && (photo.width != bounds.outWidth || photo.height != bounds.outHeight))) {
                    throw BackupFailure("Il backup contiene una foto non leggibile o incoerente.")
                }
            }
        }
    }
    private fun validateModels(snapshot: LocalBackupSnapshot, inventory: List<BackupInventory>, file: (String) -> File) {
        val listed = inventory.filter { it.path.startsWith("models/") }.associateBy { it.path }
        if (snapshot.models.size > 100 || snapshot.models.map { it.relativePath }.toSet() != listed.keys ||
            snapshot.models.map { it.relativePath }.distinct().size != snapshot.models.size) throw BackupFailure("Inventario dei modelli non valido.")
        snapshot.models.forEach { model ->
            val item = listed.getValue(model.relativePath)
            if (model.byteSize != item.bytes || model.sha256 != item.sha256) throw BackupFailure("Modello incoerente con i metadati.")
            val bytes = file(model.relativePath).readBytes()
            GlbInspection.inspect(bytes)
            validateModelResources(bytes)
        }
    }
}
