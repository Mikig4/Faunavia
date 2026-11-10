package it.faunavia.app

import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryVideoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "memory-video.db"
    private val root = File(context.noBackupFilesDir, "video-test")
    private val original = File(context.cacheDir, "synthetic-video.mp4")
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private lateinit var photos: PrivatePhotoStore
    private lateinit var store: PrivateVideoStore
    private lateinit var manager: MemoryVideos
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)
    private val zone = ZoneId.of("Europe/Rome")

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name); root.mkdirs()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxon(f8bTaxon)
        local.diary.create(ObservationDraft("o", f8bTaxon.id, f8bNow, zone))
        photos = PrivatePhotoStore(context, root)
        store = PrivateVideoStore(context, photos, root, { Long.MAX_VALUE })
        manager = MemoryVideos(store, local.videos, photos)
        videoFixture(original)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); root.deleteRecursively(); original.delete() }

    @Test fun trimStartsInsideAGopAtTheChosenMomentAndStripsLocationWithoutChangingOriginal() = runBlocking<Unit> {
        val before = original.readBytes()
        val source = store.stage(Uri.fromFile(original))
        assertTrue(manager.attach("o", source, VideoTrim(2350, 3650)) {})
        val saved = requireNotNull(manager.get("o"))
        assertTrue(saved.durationMs in 1200..1450)
        assertTrue(saved.byteSize <= MAX_MEMORY_VIDEO_BYTES)
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(store.file(saved.relativePath).absolutePath)
            assertNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            for (time in listOf(0L, 1_000_000L)) {
                val frame = requireNotNull(reader.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST))
                val pixel = frame.getPixel(frame.width / 2, frame.height / 2)
                assertTrue("The selected green section must be encoded, not the preceding red keyframe", Color.green(pixel) > Color.red(pixel) + 50 && Color.green(pixel) > Color.blue(pixel) + 50)
                frame.recycle()
            }
        } finally { reader.release() }
        assertArrayEquals(before, original.readBytes())
        original.delete()
        VideoInspection.validate(saved, store.file(saved.relativePath))
    }

    @Test fun oneVideoReplacementConversionReopeningAndDeletionPreserveTheMemory() = runBlocking<Unit> {
        local.unidentified.save(UnidentifiedInput("d", f8bNow, zone, notes = "preserved"))
        val source = store.stage(Uri.fromFile(original))
        manager.attach("d", source, VideoTrim(0, 1000)) {}
        val old = requireNotNull(manager.get("d"))
        manager.attach("d", source, VideoTrim(2200, 3400)) {}
        assertEquals(1, local.videos.all().size)
        assertFalse(store.file(old.relativePath).exists())
        val selected = requireNotNull(manager.get("d"))
        try { local.unidentified.convert(ObservationDraft("d", "missing", f8bNow, zone)); fail("Expected rollback") }
        catch (_: IllegalArgumentException) { assertEquals(selected, manager.get("d")) }
        local.unidentified.convert(ObservationDraft("d", f8bTaxon.id, f8bNow, zone, notes = "preserved"))
        assertEquals(selected, manager.get("d"))
        assertNull(db.dao().draftVideo("d"))
        db.close(); db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(0)); manager = MemoryVideos(store, local.videos, photos)
        assertEquals(selected, manager.get("d"))
        assertTrue(manager.remove("d"))
        assertNull(manager.get("d")); assertNotNull(local.diary.get("d"))
        assertFalse(store.file(selected.relativePath).exists())
    }

    @Test fun invalidSourceFailedReplacementLowSpaceAndStaleRestoreKeepTheSavedVideo() = runBlocking<Unit> {
        val source = store.stage(Uri.fromFile(original))
        manager.attach("o", source, VideoTrim(2100, 3300)) {}
        val old = manager.get("o")
        rejects { manager.attach("missing", source, VideoTrim(0, 1000)) {} }
        rejects { manager.attach("o", source, VideoTrim(6000, 7000)) {} }
        photos.restoreGeneration++
        rejects { manager.attach("o", source, VideoTrim(0, 1000)) {} }
        assertEquals(old, manager.get("o"))
        val low = PrivateVideoStore(context, photos, root, { 0 })
        rejects { low.stage(Uri.fromFile(original)) }
        val invalid = File(root, "invalid.mp4").apply { writeText("not a video") }
        rejects { store.stage(Uri.fromFile(invalid)) }
        assertEquals(old, manager.get("o"))
        val remaining = File(root, "videos").walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, remaining.size)
    }

    @Test fun videoBackupRoundTripsBothOwnersAndMissingBytesCannotReplaceCurrentData() = runBlocking<Unit> {
        local.unidentified.save(UnidentifiedInput("d", f8bNow, zone))
        val staged = store.stage(Uri.fromFile(original))
        manager.attach("o", staged, VideoTrim(2100, 3300)) {}
        manager.attach("d", staged, VideoTrim(4100, 5300)) {}
        val archive = LocalBackupArchive(context, local.backup, photos, videos = store)
        val bytes = ByteArrayOutputStream()
        archive.export(bytes)
        val expected = local.videos.all().associateBy { it.memoryId }
        val prepared = archive.prepare(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, prepared.preview.counts["observation_videos"])
        assertEquals(1, prepared.preview.counts["draft_videos"])
        archive.restore(prepared)
        for (video in local.videos.all()) {
            assertEquals(expected.getValue(video.memoryId).sha256, video.sha256)
            assertNotEquals(expected.getValue(video.memoryId).relativePath, video.relativePath)
            VideoInspection.validate(video, store.file(video.relativePath))
        }
        val saved = requireNotNull(manager.get("o"))
        store.file(saved.relativePath).delete()
        rejects { archive.export(ByteArrayOutputStream()) }
        assertEquals(saved, manager.get("o"))
    }

    @Test fun cancellingExportStopsTheEncoderBeforeRemovingOnlyTheUncommittedCopy() = runBlocking<Unit> {
        val source = store.stage(Uri.fromFile(original))
        manager.attach("o", source, VideoTrim(2100, 3300)) {}
        val old = requireNotNull(manager.get("o"))
        val started = CompletableDeferred<Unit>()
        val export = launch { manager.attach("o", source, VideoTrim(0, 5900)) { started.complete(Unit) } }
        withTimeout(30_000) { started.await() }
        export.cancelAndJoin()
        assertEquals(old, manager.get("o"))
        VideoInspection.validate(old, store.file(old.relativePath))
        assertEquals(1, File(root, "videos").walkTopDown().filter { it.isFile }.count())
    }

    @Test fun formatTwoSchemaTenCompatibilityFixtureRestoresWithEmptyVideoTables() = runBlocking<Unit> {
        val archive = LocalBackupArchive(context, local.backup, photos, videos = store)
        val output = ByteArrayOutputStream()
        archive.export(output)
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes() }
        }
        val payload = JSONObject(files.getValue("database.json").toString(Charsets.UTF_8))
        payload.put("schemaVersion", 10)
        removePersonalCommonNameColumn(payload)
        val manifest = JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.put("databaseSchema", 10).put("formatVersion", 2)
        for (table in listOf("observation_videos", "draft_videos")) {
            payload.getJSONObject("tables").remove(table)
            manifest.getJSONObject("counts").remove(table)
        }
        files["database.json"] = payload.toString().toByteArray(Charsets.UTF_8)
        manifest.getJSONArray("files").getJSONObject(0).put("bytes", files.getValue("database.json").size)
            .put("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(files.getValue("database.json")).joinToString("") { "%02x".format(it) })
        files["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val older = ByteArrayOutputStream()
        ZipOutputStream(older).use { zip -> files.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } }
        val prepared = archive.prepare(ByteArrayInputStream(older.toByteArray()))
        assertEquals(10, prepared.preview.schema)
        archive.restore(prepared)
        assertNotNull(local.diary.get("o"))
        assertTrue(local.videos.all().isEmpty())
    }

    @Test fun populatedSchemaTenMigratesWithoutLosingItsDiary() = runBlocking<Unit> {
        val legacy = "video-migration.db"
        context.deleteDatabase(legacy)
        helper.createDatabase(legacy, 10).apply {
            execSQL("INSERT INTO taxa VALUES ('fixture:1','Turdus merula','Merlo','Animalia','ACCEPTED','SPECIES','fixture','record:1','local','2026-09-16T10:00:00Z','CC0','Faunavia','synthetic','v1')")
            execSQL("INSERT INTO observations VALUES ('legacy','fixture:1','2026-09-16T10:00:00Z',1789552800,'Europe/Rome',NULL,NULL,'preserved','2026-09-16T10:00:00Z','2026-09-16T10:00:00Z',3,NULL,NULL)")
            close()
        }
        helper.runMigrationsAndValidate(legacy, 11, true, FaunaviaDatabase.MIGRATION_10_11).close()
        val migrated = FaunaviaDatabase.open(context, legacy)
        try {
            val repositories = LocalRepositories(migrated, FakeClock(0))
            assertEquals("preserved", repositories.diary.get("legacy")?.notes)
            assertTrue(repositories.videos.all().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(legacy) }
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Exception) { /* All prior references must survive. */ }
    }
}
