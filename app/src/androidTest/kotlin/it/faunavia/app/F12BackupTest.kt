package it.faunavia.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.occurrence.OccurrenceCacheEntry
import it.faunavia.testing.FakeClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class F12BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sourceName = "f12-source.db"
    private val targetName = "f12-target.db"
    private val sourceRoot = File(context.noBackupFilesDir, "f12-source")
    private val targetRoot = File(context.noBackupFilesDir, "f12-target")
    private val image = File(context.cacheDir, "f12-image.jpg")
    private val zone = ZoneId.of("Europe/Rome")
    private lateinit var sourceDb: FaunaviaDatabase
    private lateinit var targetDb: FaunaviaDatabase
    private lateinit var source: LocalRepositories
    private lateinit var target: LocalRepositories
    private lateinit var sourcePhotos: PrivatePhotoStore
    private lateinit var targetPhotos: PrivatePhotoStore
    private lateinit var sourceArchive: LocalBackupArchive
    private lateinit var targetArchive: LocalBackupArchive

    @Before fun setup() {
        context.deleteDatabase(sourceName); context.deleteDatabase(targetName)
        sourceRoot.mkdirs(); targetRoot.mkdirs()
        sourceDb = FaunaviaDatabase.open(context, sourceName)
        targetDb = FaunaviaDatabase.open(context, targetName)
        source = LocalRepositories(sourceDb, FakeClock(f8bNow.toEpochMilli()))
        target = LocalRepositories(targetDb, FakeClock(f8bNow.toEpochMilli()))
        photoFixture(image)
        sourcePhotos = PrivatePhotoStore(context, sourceRoot, { image.inputStream() }, { Long.MAX_VALUE })
        targetPhotos = PrivatePhotoStore(context, targetRoot, { image.inputStream() }, { Long.MAX_VALUE })
        sourceArchive = LocalBackupArchive(context, source.backup, sourcePhotos, { Long.MAX_VALUE })
        targetArchive = LocalBackupArchive(context, target.backup, targetPhotos, { Long.MAX_VALUE })
    }
    @After fun cleanup() {
        sourceDb.close(); targetDb.close()
        context.deleteDatabase(sourceName); context.deleteDatabase(targetName)
        sourceRoot.deleteRecursively(); targetRoot.deleteRecursively(); image.delete()
        context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("backup-") }.forEach { it.deleteRecursively() }
    }

    private suspend fun populated() {
        source.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo", "Blackbird"))
        source.catalogue.savePreview(TaxonPreview(f8bTaxon.id, "https://example.test/licensed.jpg", f8bProvenance))
        source.catalogue.saveProfile(SpeciesProfile(f8bTaxon.id, "Profilo", listOf("Bosco"), setOf(1, 12), "Dieta", "20 cm", "Comportamento", "LC", f8bProvenance))
        source.catalogue.saveSuggestion(SuggestionProfile(f8bTaxon.id, "Lombardia", listOf("Bosco"), false, 0.3, "Motivo", f8bProvenance))
        val evidence = SourceEvidence("e", f8bTaxon.id, EvidenceLevel.DOCUMENTED, f8bNow, GeoPoint(45.0, 9.0), 20.0, "Storico", f8bProvenance)
        source.catalogue.saveEvidence(evidence)
        source.occurrenceCache.save(OccurrenceCacheEntry("cache", f8bNow, f8bNow.plusSeconds(3600), emptyList()))
        source.wishlist.add(f8bTaxon.id)
        val route = testTripRoute()
        source.routes.save(route.geometry)
        val trip = f8bTrip().copy(departure = f8bTrip().destination.copy(name = "Partenza", center = GeoPoint(45.1, 9.1)), route = route)
            .withStages(listOf(TripStage("stage", f8bTrip().destination, f8bTrip().startsOn, route)), f8bNow)
        source.trips.save(trip)
        val outing = Outing("out", trip.id, "Parco", trip.startsOn, trip.destination, route.geometry)
        source.trips.saveOuting(outing)
        source.trips.savePlace(SavedTripPlace("place", trip.id, trip.destination, f8bNow))
        source.trips.saveResult(SavedTripResult("result", trip.id, outing.id, f8bTaxon.id, f8bTaxon.scientificName,
            EvidenceLevel.DOCUMENTED, listOf("Storico, non presenza garantita"), "Stagione", listOf(evidence),
            tripAnalysisKey(trip, outing), f8bNow, partial = true, stale = true,
            startsOn = outing.date, endsOn = outing.date, area = outing.place))
        source.diary.create(ObservationDraft("o", f8bTaxon.id, f8bNow, zone, GeoPoint(45.0, 9.0), "Memoria", 4, trip.id, outing.id))
        source.unidentified.save(UnidentifiedInput("d", f8bNow, zone, notes = "Da identificare", tripId = trip.id, outingId = outing.id))
        val manager = MemoryPhotos(sourcePhotos, source.diary, source.unidentified)
        manager.attach("o", Uri.fromFile(image)); manager.attach("d", Uri.fromFile(image))
        source.settings.save(AppSettings(true, LocalTime.MIDNIGHT, zone))
        assertEquals(ReminderDelivery.SENT, source.dailyReminder.deliver(f8bNow, zone) { true })
    }

    private suspend fun sentinel() {
        target.catalogue.saveTaxon(f8bTaxon)
        target.diary.create(ObservationDraft("old", f8bTaxon.id, f8bNow, zone, notes = "Non perdere"))
        MemoryPhotos(targetPhotos, target.diary, target.unidentified).attach("old", Uri.fromFile(image))
    }
    private suspend fun exported(): ByteArray = ByteArrayOutputStream().let { output -> sourceArchive.export(output); output.toByteArray() }
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break
            result[entry.name] = zip.readBytes()
        } }
        return result
    }
    private fun zipped(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().let { output ->
        ZipOutputStream(output).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        output.toByteArray()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun mutate(bytes: ByteArray, action: (LinkedHashMap<String, ByteArray>, JSONObject) -> Unit): ByteArray {
        val files = entries(bytes)
        val manifest = JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8))
        action(files, manifest)
        files["manifest.json"] = manifest.toString().toByteArray()
        return zipped(files)
    }
    private fun changePayload(files: LinkedHashMap<String, ByteArray>, manifest: JSONObject, change: (JSONObject) -> Unit) {
        val payload = JSONObject(files.getValue("database.json").toString(Charsets.UTF_8))
        change(payload)
        val bytes = payload.toString().toByteArray()
        files["database.json"] = bytes
        val list = manifest.getJSONArray("files")
        for (i in 0 until list.length()) if (list.getJSONObject(i).getString("path") == "database.json") {
            list.getJSONObject(i).put("bytes", bytes.size).put("sha256", digest(bytes))
        }
    }
    private suspend fun rejects(bytes: ByteArray) {
        val before = target.backup.snapshot().payload
        val photos = targetRoot.walkTopDown().filter { it.isFile }.map { it.relativeTo(targetRoot).path }.toSet()
        assertTrue(runCatching { targetArchive.prepare(ByteArrayInputStream(bytes)) }.isFailure)
        assertEquals(before, target.backup.snapshot().payload)
        assertEquals(photos, targetRoot.walkTopDown().filter { it.isFile }.map { it.relativeTo(targetRoot).path }.toSet())
        assertFalse(context.databaseList().any { it.startsWith("backup-stage-") })
    }

    @Test fun emptyArchiveRestoresAnEmptyFreshInstallation() = runBlocking<Unit> {
        val archive = exported()
        val prepared = targetArchive.prepare(ByteArrayInputStream(archive))
        assertEquals(0, prepared.preview.counts.values.sum())
        targetArchive.restore(prepared)
        assertEquals(source.backup.snapshot().payload, target.backup.snapshot().payload)
        assertEquals(AppSettings(), target.settings.get())
    }

    @Test fun completeRoundTripPreservesEveryTablePhotosLinksProvenanceAndNotificationLedgerAfterReopen() = runBlocking<Unit> {
        populated(); sentinel()
        val original = source.backup.snapshot()
        // This released F12 fixture populates every original table; F14 models have their own full round-trip fixture.
        assertTrue(original.counts.filterKeys { it != "personal_models" }.values.all { it > 0 })
        assertEquals(0, original.counts.getValue("personal_models"))
        val prepared = targetArchive.prepare(ByteArrayInputStream(exported()))
        assertEquals(original.counts, prepared.preview.counts)
        // Staging and preview must leave the populated installation alone.
        assertNotNull(target.diary.get("old"))
        targetArchive.restore(prepared)
        assertNull(target.diary.get("old"))
        targetDb.close()
        targetDb = FaunaviaDatabase.open(context, targetName)
        target = LocalRepositories(targetDb, FakeClock(f8bNow.toEpochMilli()))
        val restored = target.backup.snapshot()
        assertEquals(original.counts, restored.counts)
        val oldTables = JSONObject(original.payload).getJSONObject("tables")
        val newTables = JSONObject(restored.payload).getJSONObject("tables")
        LocalBackupStore.TABLES.filterNot { it.endsWith("photos") }.forEach { assertEquals(it, oldTables.getJSONObject(it).toString(), newTables.getJSONObject(it).toString()) }
        assertEquals(source.diary.get("o"), target.diary.get("o"))
        assertEquals(source.unidentified.get("d"), target.unidentified.get("d"))
        assertEquals(source.trips.get("t"), target.trips.get("t"))
        assertEquals(source.settings.get(), target.settings.get())
        restored.photos.forEach { photo ->
            val old = original.photos.single { it.id == photo.id }
            assertEquals(old.copy(relativePath = photo.relativePath, thumbnailPath = photo.thumbnailPath), photo)
            assertArrayEquals(sourcePhotos.file(old.relativePath).readBytes(), targetPhotos.file(photo.relativePath).readBytes())
            assertNotNull(targetPhotos.load(photo, true)?.also { it.recycle() })
        }
        assertEquals(ReminderDelivery.ALREADY_SENT, target.dailyReminder.deliver(f8bNow, zone) { fail("Restored day must not notify twice"); false })
        val draft = target.unidentified.get("d")!!.input
        target.unidentified.convert(ObservationDraft(draft.id, f8bTaxon.id, draft.observedAt, draft.zoneId,
            draft.location, draft.notes, draft.quantity, draft.tripId, draft.outingId))
        assertEquals(1, target.diary.photos("d").size)
        target.trips.delete("t")
        assertNull(target.diary.get("o")!!.tripId)
        assertEquals(1, target.diary.photos("o").size)
    }

    @Test fun hashMismatchMissingFileUnexpectedFileAndMalformedZipLeaveExistingMemoriesIntact() = runBlocking<Unit> {
        populated(); sentinel()
        val valid = exported()
        rejects(mutate(valid) { files, _ -> val key = files.keys.first { it.startsWith("photos/") }; files[key] = files.getValue(key).clone().apply { this[0] = (this[0].toInt() xor 1).toByte() } })
        rejects(mutate(valid) { files, _ -> files.remove(files.keys.first { it.startsWith("photos/") }) })
        rejects(mutate(valid) { files, _ -> files["unexpected.txt"] = byteArrayOf(1) })
        rejects(byteArrayOf(1, 2, 3))
        rejects(valid.copyOf(valid.size / 2))
        rejects(valid.copyOf(valid.size - 20))
    }

    @Test fun futureVersionsDuplicateInventoryTraversalAndWrongSizesAreRejectedBeforeReplacement() = runBlocking<Unit> {
        populated(); sentinel()
        val valid = exported()
        rejects(mutate(valid) { _, manifest -> manifest.put("formatVersion", 999) })
        rejects(mutate(valid) { _, manifest -> manifest.put("databaseSchema", 999) })
        rejects(mutate(valid) { _, manifest -> val files = manifest.getJSONArray("files"); files.put(files.getJSONObject(0)) })
        rejects(mutate(valid) { _, manifest -> manifest.getJSONArray("files").getJSONObject(1).put("path", "photos/../../outside.jpg") })
        rejects(mutate(valid) { _, manifest -> manifest.getJSONArray("files").getJSONObject(1).put("bytes", 1) })
        rejects(mutate(valid) { _, manifest -> manifest.getJSONObject("counts").put("observations", 999) })
    }

    @Test fun invalidDiaryTaxonLinksQuantityAndPayloadAreRejectedEvenWithValidHashes() = runBlocking<Unit> {
        populated(); sentinel()
        val valid = exported()
        for ((field, value) in listOf("taxonId" to "unknown", "tripId" to "missing", "quantity" to 0, "observedAt" to "invalid")) {
            rejects(mutate(valid) { files, manifest -> changePayload(files, manifest) { payload ->
                val table = payload.getJSONObject("tables").getJSONObject("observations")
                val columns = table.getJSONArray("columns")
                val index = (0 until columns.length()).first { columns.getString(it) == field }
                table.getJSONArray("rows").getJSONArray(0).put(index, value)
            } })
        }
        rejects(mutate(valid) { files, manifest -> changePayload(files, manifest) { payload ->
            payload.getJSONObject("tables").getJSONObject("trips").getJSONArray("rows").getJSONArray(0).put(1, "{bad}")
        } })
    }

    @Test fun precedingSchemaEightArchiveImportsWithoutInventingDeliveryHistory() = runBlocking<Unit> {
        populated()
        val older = mutate(exported()) { files, manifest ->
            manifest.put("databaseSchema", 8)
            manifest.getJSONObject("counts").remove("daily_summary_deliveries")
            manifest.getJSONObject("counts").remove("personal_models")
            manifest.put("formatVersion", 1)
            changePayload(files, manifest) { it.put("schemaVersion", 8); it.getJSONObject("tables").remove("daily_summary_deliveries"); it.getJSONObject("tables").remove("personal_models") }
        }
        val pending = targetArchive.prepare(ByteArrayInputStream(older))
        assertEquals(8, pending.preview.schema)
        targetArchive.restore(pending)
        assertEquals(0, target.backup.snapshot().counts["daily_summary_deliveries"])
        assertEquals(source.diary.get("o"), target.diary.get("o"))
        assertEquals(2, target.backup.snapshot().photos.size)
    }

    @Test fun failureAfterAllRowsWereInsertedRollsBackDatabaseAndRemovesOnlyNewPhotoCopies() = runBlocking<Unit> {
        populated(); sentinel()
        val before = target.backup.snapshot()
        val files = targetRoot.walkTopDown().filter { it.isFile }.map { it.path }.toSet()
        val failing = LocalBackupArchive(context, target.backup, targetPhotos, { Long.MAX_VALUE }, { throw IOException("Injected commit failure") })
        val pending = failing.prepare(ByteArrayInputStream(exported()))
        assertTrue(runCatching { failing.restore(pending) }.isFailure)
        assertEquals(before.payload, target.backup.snapshot().payload)
        assertEquals(files, targetRoot.walkTopDown().filter { it.isFile }.map { it.path }.toSet())
        assertNotNull(targetPhotos.load(before.photos.single(), false)?.also { it.recycle() })
        assertTrue(pending.discard())
    }

    @Test fun lowSpaceAndMissingOrCorruptSourcePhotoPreserveLocalData() = runBlocking<Unit> {
        populated(); sentinel()
        val valid = exported()
        val before = target.backup.snapshot().payload
        val limited = LocalBackupArchive(context, target.backup, targetPhotos, { 0 })
        assertTrue(runCatching { limited.prepare(ByteArrayInputStream(valid)) }.exceptionOrNull() is BackupFailure)
        val pending = targetArchive.prepare(ByteArrayInputStream(valid))
        assertTrue(runCatching { limited.restore(pending) }.exceptionOrNull() is BackupFailure)
        assertEquals(before, target.backup.snapshot().payload)
        pending.discard()
        val photo = source.diary.photos("o").single()
        val original = sourcePhotos.file(photo.relativePath).readBytes()
        sourcePhotos.file(photo.relativePath).writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { exported() }.exceptionOrNull() is BackupFailure)
        sourcePhotos.file(photo.relativePath).writeBytes(original)
        sourcePhotos.file(photo.relativePath).delete()
        assertTrue(runCatching { exported() }.exceptionOrNull() is BackupFailure)
        assertNotNull(source.diary.get("o"))
    }

    @Test fun aWriteQueuedBeforeRestoreCannotReinsertOldDiaryDataAfterCommit() = runBlocking<Unit> {
        source.catalogue.saveTaxon(f8bTaxon)
        source.diary.create(ObservationDraft("fresh", f8bTaxon.id, f8bNow, zone))
        sentinel()
        val queue = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        }
        val delayed = LocalRepositories(targetDb, FakeClock(0), dispatcher)
        val payload = source.backup.snapshot().payload
        supervisorScope {
            val lateWrite = async(start = CoroutineStart.UNDISPATCHED) { delayed.diary.create(ObservationDraft("late", f8bTaxon.id, f8bNow, zone)) }
            val restore = async(start = CoroutineStart.UNDISPATCHED) { delayed.backup.replace(payload) }
            assertEquals(2, queue.size)
            queue.removeAt(1).run()
            restore.await()
            queue.removeAt(0).run()
            assertTrue(runCatching { lateWrite.await() }.isFailure)
        }
        assertNull(target.diary.get("old"))
        assertNull(target.diary.get("late"))
        assertNotNull(target.diary.get("fresh"))
    }

    @Test fun cancellationAndDiscardedPreviewDoNotTouchCurrentDatabaseOrPhotos() = runBlocking<Unit> {
        populated(); sentinel()
        val valid = exported()
        val before = target.backup.snapshot().payload
        val pending = targetArchive.prepare(ByteArrayInputStream(valid))
        assertTrue(pending.discard())
        assertEquals(before, target.backup.snapshot().payload)
        val attempt = async(start = CoroutineStart.LAZY) {
            targetArchive.prepare(ByteArrayInputStream(valid)) { if (it.startsWith("Verifica dei file")) cancel() }
        }
        attempt.start()
        assertTrue(runCatching { attempt.await() }.exceptionOrNull() is CancellationException)
        assertEquals(before, target.backup.snapshot().payload)
        assertFalse(context.databaseList().any { it.startsWith("backup-stage-") })
    }

    @Test fun successfulRestoreKeepsCleanupWarningInTheReportForTheNewActivity() = runBlocking<Unit> {
        populated()
        val application = context.applicationContext as FaunaviaApplication
        val pending = application.localBackup.prepare(ByteArrayInputStream(exported()))
        // Read/execute are sufficient for copying; removing entries requires directory write permission.
        android.system.Os.chmod(pending.directory.absolutePath, 0b101000000)
        try {
            application.restoreBackup(pending)
            assertNotNull(application.repositories.diary.get("o"))
            assertTrue(pending.consumed)
            assertTrue("The fixture must actually leave staging behind", pending.directory.exists())
            val warning = requireNotNull(application.localBackup.cleanupWarning)
            assertTrue(requireNotNull(application.localBackup.lastMessage).contains(warning))
            assertTrue(requireNotNull(application.localBackup.lastMessage).startsWith("Ripristino completato"))
        } finally {
            android.system.Os.chmod(pending.directory.absolutePath, 0b111000000)
            assertTrue(pending.discard())
        }
    }
}
