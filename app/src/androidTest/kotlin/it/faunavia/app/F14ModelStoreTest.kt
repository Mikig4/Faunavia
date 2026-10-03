package it.faunavia.app

import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

internal fun modelFixture(change: (JSONObject) -> Unit = {}): ByteArray {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val original = context.assets.open("models/blackbird-v1.glb").use { it.readBytes() }
    val buffer = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
    val length = buffer.getInt(12)
    val root = JSONObject(original.copyOfRange(20, 20 + length).toString(Charsets.UTF_8))
    change(root)
    val json = root.toString().toByteArray(Charsets.UTF_8)
    val padded = (json.size + 3) / 4 * 4
    val tail = original.copyOfRange(20 + length, original.size)
    return ByteBuffer.allocate(20 + padded + tail.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(0x46546c67).putInt(2).putInt(20 + padded + tail.size).putInt(padded).putInt(0x4e4f534a)
        .put(json).put(ByteArray(padded - json.size) { 32 }).put(tail).array()
}

@RunWith(AndroidJUnit4::class)
class F14ModelStoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val sourceName = "f14-source.db"
    private val targetName = "f14-target.db"
    private val sourceRoot = File(context.noBackupFilesDir, "f14-source")
    private val targetRoot = File(context.noBackupFilesDir, "f14-target")
    private lateinit var sourceDb: FaunaviaDatabase
    private lateinit var targetDb: FaunaviaDatabase
    private lateinit var source: LocalRepositories
    private lateinit var target: LocalRepositories
    private lateinit var sourcePhotos: PrivatePhotoStore
    private lateinit var targetPhotos: PrivatePhotoStore
    private lateinit var sourceModels: PersonalModelStore
    private lateinit var targetModels: PersonalModelStore
    private lateinit var data: ByteArray
    private val uri = Uri.parse("content://synthetic/model.glb")
    private val credit = ModelCredit("Fixture originale", "Fixture locale sintetica", "Asset originale del progetto", "Nessuna")
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)

    @Before fun setup() {
        context.deleteDatabase(sourceName); context.deleteDatabase(targetName)
        sourceRoot.mkdirs(); targetRoot.mkdirs()
        sourceDb = FaunaviaDatabase.open(context, sourceName)
        targetDb = FaunaviaDatabase.open(context, targetName)
        source = LocalRepositories(sourceDb, FakeClock(f8bNow.toEpochMilli()))
        target = LocalRepositories(targetDb, FakeClock(f8bNow.toEpochMilli()))
        sourcePhotos = PrivatePhotoStore(context, sourceRoot, availableBytes = { Long.MAX_VALUE })
        targetPhotos = PrivatePhotoStore(context, targetRoot, availableBytes = { Long.MAX_VALUE })
        data = modelFixture()
        sourceModels = PersonalModelStore(context, source.personalModels, sourcePhotos, sourceRoot, { data.inputStream() }, { Long.MAX_VALUE })
        targetModels = PersonalModelStore(context, target.personalModels, targetPhotos, targetRoot, { data.inputStream() }, { Long.MAX_VALUE })
    }
    @After fun cleanup() {
        sourceDb.close(); targetDb.close()
        context.deleteDatabase(sourceName); context.deleteDatabase(targetName); context.deleteDatabase("f14-migration.db")
        sourceRoot.deleteRecursively(); targetRoot.deleteRecursively()
    }
    private fun archive(local: LocalRepositories, photos: PrivatePhotoStore, models: PersonalModelStore,
        beforeCommit: () -> Unit = {}) = LocalBackupArchive(context, local.backup, photos, { Long.MAX_VALUE }, beforeCommit, models)
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { } catch (_: java.io.IOException) { }
    }
    @Test fun includedModelMeetsMobileBudgetsAndHasTwoSupportedClips() = runBlocking<Unit> {
        val included = sourceModels.load("included", "Turdus merula")!!
        assertNull(included.personal)
        assertTrue(included.bytes.size < 300_000)
        assertTrue(included.info.triangles in 1..10_000)
        assertEquals(2, included.info.clips.size)
        assertTrue(included.info.clips.all { it.duration > 0 })
        assertEquals(0L, included.info.textureBytes)
        validateModelResources(included.bytes)
        assertTrue(source.personalModels.all().isEmpty())
    }
    @Test fun staticAndAnimatedImportsPersistAndFailedReplacementsPreservePreviousAndDiary() = runBlocking<Unit> {
        source.catalogue.saveTaxon(f8bTaxon)
        source.diary.create(ObservationDraft("sentinel", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"), notes = "preserved"))
        val diary = source.diary.list()
        data = modelFixture { it.remove("animations") }
        val static = sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit)
        assertTrue(sourceModels.load(f8bTaxon.id, f8bTaxon.scientificName)!!.info.clips.isEmpty())
        sourceDb.close()
        sourceDb = FaunaviaDatabase.open(context, sourceName)
        source = LocalRepositories(sourceDb, FakeClock(0))
        sourceModels = PersonalModelStore(context, source.personalModels, sourcePhotos, sourceRoot, { data.inputStream() }, { Long.MAX_VALUE })
        assertEquals(static, source.personalModels.get(f8bTaxon.id))
        data = modelFixture()
        val animated = sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit)
        assertEquals(2, sourceModels.load(f8bTaxon.id, f8bTaxon.scientificName)!!.info.clips.size)
        data = modelFixture { it.getJSONArray("animations").getJSONObject(0).getJSONArray("channels").getJSONObject(0).getJSONObject("target").put("path", "pointer") }
        rejects { sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit) }
        data = modelFixture().copyOf(30)
        rejects { sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit) }
        data = modelFixture { it.getJSONArray("buffers").getJSONObject(0).put("uri", "https://example.test/private") }
        rejects { sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit) }
        data = ByteArray(GlbInspection.MAX_BYTES + 1)
        rejects { sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit) }
        assertEquals(animated, source.personalModels.get(f8bTaxon.id))
        assertEquals(diary, source.diary.list())
        sourceModels.remove(f8bTaxon.id)
        assertNull(source.personalModels.get(f8bTaxon.id))
        assertNull(sourceModels.load(f8bTaxon.id, "Different animal"))
        assertEquals(diary, source.diary.list())
    }
    @Test fun geometryBoundsCyclesSparseAndMissingCreditFailBeforePublication() = runBlocking<Unit> {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("bufferViews").getJSONObject(0).put("byteLength", Int.MAX_VALUE) },
            { it.getJSONArray("accessors").getJSONObject(0).put("count", Int.MAX_VALUE) },
            { it.getJSONArray("nodes").getJSONObject(0).put("children", org.json.JSONArray(listOf(0))) },
            { it.getJSONArray("accessors").getJSONObject(0).put("sparse", JSONObject()) },
            { it.put("extensionsRequired", org.json.JSONArray(listOf("KHR_draco_mesh_compression"))) },
            { it.getJSONArray("animations").getJSONObject(0).put("samplers", org.json.JSONArray()) }
        )
        for (change in changes) {
            data = modelFixture(change)
            rejects { sourceModels.import("synthetic", "Animal", uri, credit) }
        }
        rejects { sourceModels.import("synthetic", "Animal", uri, credit.copy(license = "")) }
        assertTrue(source.personalModels.all().isEmpty())
        assertFalse(File(sourceRoot, "models").exists())
    }
    @Test fun modelsAndProvenanceRoundTripAndFailedRestoreKeepPriorFilesAndDiary() = runBlocking<Unit> {
        source.catalogue.saveTaxon(f8bTaxon)
        source.diary.create(ObservationDraft("source-sentinel", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome")))
        val model = sourceModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit)
        target.catalogue.saveTaxon(f8bTaxon)
        target.diary.create(ObservationDraft("target-sentinel", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome")))
        data = modelFixture { it.remove("animations") }
        val prior = targetModels.import(f8bTaxon.id, f8bTaxon.scientificName, uri, credit.copy(author = "Prior"))
        val output = ByteArrayOutputStream()
        archive(source, sourcePhotos, sourceModels).export(output)
        val before = target.backup.snapshot()
        val failing = archive(target, targetPhotos, targetModels) { throw IllegalStateException("Synthetic before commit") }
        val pending = failing.prepare(ByteArrayInputStream(output.toByteArray()))
        assertTrue(pending.preview.summary.contains("1 modelli 3D"))
        rejects { failing.restore(pending) }
        pending.discard()
        assertEquals(before, target.backup.snapshot())
        assertTrue(targetModels.file(prior.relativePath).isFile)
        val receiver = archive(target, targetPhotos, targetModels)
        receiver.restore(receiver.prepare(ByteArrayInputStream(output.toByteArray())))
        val restored = target.personalModels.get(f8bTaxon.id)!!
        assertEquals(model.copy(relativePath = restored.relativePath), restored)
        assertNotEquals(model.relativePath, restored.relativePath)
        assertEquals(2, targetModels.load(f8bTaxon.id, f8bTaxon.scientificName)!!.info.clips.size)
        assertEquals(source.diary.list(), target.diary.list())
    }
    @Test fun missingCorruptAndLowSpaceModelsAreExplicitAndCannotProduceIncompleteBackup() = runBlocking<Unit> {
        val model = sourceModels.import("synthetic", "Animal", uri, credit)
        val file = sourceModels.file(model.relativePath)
        file.writeBytes(ByteArray(data.size))
        rejects { sourceModels.load("synthetic", "Animal") }
        rejects { archive(source, sourcePhotos, sourceModels).export(ByteArrayOutputStream()) }
        file.delete()
        rejects { sourceModels.load("synthetic", "Animal") }
        val low = PersonalModelStore(context, source.personalModels, sourcePhotos, sourceRoot, { data.inputStream() }, { 0L })
        rejects { low.import("synthetic", "Animal", uri, credit) }
        assertEquals(model, source.personalModels.get("synthetic"))
    }
    @Test fun releasedFormatOneSchemaNineRemainsReadableWithoutInventingModels() = runBlocking<Unit> {
        source.catalogue.saveTaxon(f8bTaxon)
        val output = ByteArrayOutputStream()
        archive(source, sourcePhotos, sourceModels).export(output)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes()
        } }
        val payload = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        payload.put("schemaVersion", 9); payload.getJSONObject("tables").remove("personal_models")
        entries["database.json"] = payload.toString().toByteArray(Charsets.UTF_8)
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.put("formatVersion", 1).put("databaseSchema", 9)
        manifest.getJSONObject("counts").remove("personal_models")
        manifest.getJSONArray("files").getJSONObject(0).put("bytes", entries.getValue("database.json").size).put("sha256", sha256(entries.getValue("database.json")))
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val older = ByteArrayOutputStream()
        ZipOutputStream(older).use { zip -> entries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } }
        val receiver = archive(target, targetPhotos, targetModels)
        receiver.restore(receiver.prepare(ByteArrayInputStream(older.toByteArray())))
        assertTrue(target.personalModels.all().isEmpty())
        assertEquals(f8bTaxon, target.catalogue.taxon(f8bTaxon.id))
    }
    @Test fun populatedNineToTenMigrationAddsModelsWithoutLosingMemoriesOrSettings() = runBlocking<Unit> {
        val name = "f14-migration.db"
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO taxa VALUES ('fixture:1','Turdus merula','Merlo','Animalia','ACCEPTED','SPECIES','fixture','record:1','local','2026-09-16T10:00:00Z','CC0','Faunavia','synthetic','v1')")
            execSQL("INSERT INTO observations VALUES ('o','fixture:1','2026-09-16T10:00:00Z',1789552800,'Europe/Rome',NULL,NULL,'preserved','2026-09-16T10:00:00Z','2026-09-16T10:00:00Z',3,NULL,NULL)")
            execSQL("INSERT INTO app_settings VALUES (1,1,1200,'Europe/Rome')")
            close()
        }
        helper.runMigrationsAndValidate(name, 10, true, FaunaviaDatabase.MIGRATION_9_10).close()
        val db = FaunaviaDatabase.open(context, name)
        try {
            val local = LocalRepositories(db, FakeClock(0))
            assertEquals("preserved", local.diary.get("o")!!.notes)
            assertEquals(3, local.diary.get("o")!!.quantity)
            assertTrue(local.settings.get().reminderEnabled)
            assertTrue(local.personalModels.all().isEmpty())
        } finally { db.close() }
    }
}
