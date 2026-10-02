package it.faunavia.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal fun photoFixture(destination: File, orientation: Int = 1, width: Int = 80, height: Int = 40): ByteArray {
    destination.parentFile!!.mkdirs()
    val bitmap = createBitmap(width, height)
    bitmap.eraseColor(Color.RED)
    // Asymmetric corner colors catch mirrored orientations as well as swapped dimensions.
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.color = Color.BLUE
    canvas.drawRect(width / 2f, 0f, width.toFloat(), height / 2f, paint)
    paint.color = Color.GREEN
    canvas.drawRect(0f, height / 2f, width / 2f, height.toFloat(), paint)
    paint.color = Color.YELLOW
    canvas.drawRect(width / 2f, height / 2f, width.toFloat(), height.toFloat(), paint)
    destination.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
    bitmap.recycle()
    ExifInterface(destination.absolutePath).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        setAttribute(ExifInterface.TAG_GPS_LATITUDE, "45/1,30/1,0/1")
        setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "9/1,12/1,0/1")
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
        setAttribute(ExifInterface.TAG_DATETIME, "2026:10:02 09:00:00")
        setAttribute(ExifInterface.TAG_MAKE, "Private camera")
        saveAttributes()
    }
    return destination.readBytes()
}

@RunWith(AndroidJUnit4::class)
class F10PhotoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "f10-photos.db"
    private val root = File(context.noBackupFilesDir, "f10-test")
    private val source = File(context.cacheDir, "f10-source.jpg")
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private lateinit var store: PrivatePhotoStore
    private lateinit var manager: MemoryPhotos
    @get:Rule val helper = MigrationTestHelper(instrumentation, FaunaviaDatabase::class.java)

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        root.mkdirs()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxon(f8bTaxon)
        local.diary.create(observation("o"))
        photoFixture(source)
        store = PrivatePhotoStore(context, root, { source.inputStream() }, { Long.MAX_VALUE })
        manager = MemoryPhotos(store, local.diary, local.unidentified)
    }

    @After fun cleanup() { db.close(); context.deleteDatabase(name); root.deleteRecursively(); source.delete() }
    private fun observation(id: String) = ObservationDraft(id, f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"), notes = "ricordo")
    private val uri = Uri.parse("content://fixture/image")

    @Test fun zeroOneMultiplePhotosReopenWithoutOriginalUriAndDeleteIndependently() = runBlocking<Unit> {
        assertTrue(manager.list("o").isEmpty())
        val first = manager.attach("o", uri)
        val second = manager.attach("o", uri)
        val originalBytes = source.readBytes()
        db.close()
        source.delete()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(0))
        manager = MemoryPhotos(store, local.diary, local.unidentified)
        assertEquals(2, manager.list("o").size)
        assertEquals(first, local.diary.photos("o").first { it.id == first.id })
        assertNotNull(store.load(first, true))
        assertNotNull(store.load(second, false))
        assertTrue(manager.delete("o", first.id))
        assertFalse(store.file(first.relativePath).exists())
        assertFalse(store.file(first.thumbnailPath!!).exists())
        assertEquals("ricordo", local.diary.get("o")!!.notes)
        assertEquals(listOf(second), manager.list("o"))
        assertTrue(manager.deleteMemory("o", true))
        assertNull(local.diary.get("o"))
        assertFalse(store.file(second.relativePath).exists())
        assertTrue(originalBytes.isNotEmpty())
    }

    @Test fun allExifOrientationsNormalizePixelsAndRemoveSensitiveMetadata() {
        val expectedCorners = listOf(Color.RED, Color.BLUE, Color.YELLOW, Color.GREEN, Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE)
        for (orientation in 1..8) {
            val original = photoFixture(source, orientation)
            val photo = store.import(uri, "o")
            assertArrayEquals(original, source.readBytes())
            assertEquals(if (orientation >= 5) 40 else 80, photo.width)
            assertEquals(if (orientation >= 5) 80 else 40, photo.height)
            assertEquals(1, photo.orientation)
            val bitmap = store.load(photo, false)!!
            val actual = bitmap.getPixel(5, 5)
            val expected = expectedCorners[orientation - 1]
            assertTrue("Orientation $orientation", kotlin.math.abs(Color.red(actual) - Color.red(expected)) < 25 &&
                kotlin.math.abs(Color.green(actual) - Color.green(expected)) < 25 && kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) < 25)
            bitmap.recycle()
            val exif = ExifInterface(store.file(photo.relativePath).absolutePath)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(ExifInterface(store.file(photo.thumbnailPath!!).absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            val digest = MessageDigest.getInstance("SHA-256").digest(store.file(photo.relativePath).readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(digest, photo.sha256)
            assertEquals(store.file(photo.relativePath).length(), photo.byteSize)
            assertTrue(store.delete(photo))
        }
    }

    @Test fun largeRotatedImagesAreSampledBeforeDecodingAndHaveBoundedThumbnails() {
        photoFixture(source, 6, 4800, 2400)
        val photo = store.import(uri, "o")
        assertTrue(maxOf(photo.width, photo.height) <= PrivatePhotoStore.MAX_EDGE)
        assertTrue(photo.height > photo.width)
        val thumbnail = store.load(photo, true)!!
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= PrivatePhotoStore.THUMB_EDGE)
        thumbnail.recycle()
    }

    @Test fun revokedCorruptOversizedAndLowStorageInputsPreserveTextAndLeaveNoPartialFiles() = runBlocking<Unit> {
        val revoked = PrivatePhotoStore(context, root, { throw SecurityException("revoked") }, { Long.MAX_VALUE })
        val corrupt = PrivatePhotoStore(context, root, { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, { Long.MAX_VALUE })
        val noSpace = PrivatePhotoStore(context, root, { source.inputStream() }, { 0 })
        val huge = PrivatePhotoStore(context, root, { object : InputStream() {
            var left = PrivatePhotoStore.MAX_INPUT_BYTES + 1
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (left <= 0) return -1
                val count = minOf(left, length.toLong()).toInt()
                buffer.fill(0, offset, offset + count); left -= count; return count
            }
        } }, { Long.MAX_VALUE })
        for (failed in listOf(revoked, corrupt, noSpace, huge)) {
            assertThrows(PhotoFailure::class.java) { failed.import(uri, "o") }
            assertEquals("ricordo", local.diary.get("o")!!.notes)
            assertTrue(local.diary.photos("o").isEmpty())
            assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun missingCorruptAndMissingThumbnailFilesDegradeWithoutDeletingMetadata() = runBlocking<Unit> {
        val photo = manager.attach("o", uri)
        store.file(photo.thumbnailPath!!).delete()
        assertNotNull(store.load(photo, true))
        store.file(photo.relativePath).appendBytes(byteArrayOf(1))
        assertNull(store.load(photo, false))
        store.file(photo.relativePath).delete()
        assertNull(store.load(photo, true))
        assertEquals(listOf(photo), manager.list("o"))
        assertEquals("ricordo", local.diary.get("o")!!.notes)
        assertTrue(manager.delete("o", photo.id))
    }

    @Test fun failedMetadataWriteRemovesFilesAndRetryDoesNotDuplicateTheMemory() = runBlocking<Unit> {
        val failing = object : DiaryRepository by local.diary {
            override suspend fun addPhoto(photo: ObservationPhoto) { throw IllegalStateException("storage failure") }
        }
        try { MemoryPhotos(store, failing, local.unidentified).attach("o", uri); fail("Expected failure") }
        catch (_: IllegalStateException) { }
        assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
        assertEquals(1, local.diary.list().size)
        assertNotNull(manager.attach("o", uri))
        assertEquals(1, local.diary.photos("o").size)
    }

    @Test fun draftConversionPreservesPhotosAndFailedConversionRollsBackTheirOwnership() = runBlocking<Unit> {
        local.unidentified.save(UnidentifiedInput("d", f8bNow, ZoneId.of("Europe/Rome"), notes = "da identificare"))
        val first = manager.attach("d", uri)
        val second = manager.attach("d", uri)
        local.trips.save(f8bTrip())
        local.trips.delete("t")
        try { local.unidentified.convert(observation("d").copy(taxonId = "missing")); fail("Expected invalid taxon") }
        catch (_: IllegalArgumentException) { }
        assertNotNull(local.unidentified.get("d"))
        assertEquals(setOf(first, second), local.unidentified.photos("d").toSet())
        local.unidentified.convert(observation("d"))
        assertNull(local.unidentified.get("d"))
        assertTrue(local.unidentified.photos("d").isEmpty())
        assertEquals(setOf(first, second), local.diary.photos("d").toSet())
        assertNotNull(store.load(first, false))
        assertTrue(manager.deleteMemory("d", true))
        assertFalse(store.file(first.relativePath).exists())
        assertFalse(store.file(second.relativePath).exists())
    }

    @Test fun draftDeletionAndTripUnlinkingKeepTheCorrectPhotoLifetimes() = runBlocking<Unit> {
        local.trips.save(f8bTrip())
        local.unidentified.save(UnidentifiedInput("d", f8bNow, ZoneId.of("Europe/Rome"), tripId = "t"))
        val photo = manager.attach("d", uri)
        local.trips.delete("t")
        assertNull(local.unidentified.get("d")!!.input.tripId)
        assertNotNull(store.load(photo, true))
        assertTrue(manager.deleteMemory("d", false))
        assertNull(local.unidentified.get("d"))
        assertFalse(store.file(photo.relativePath).exists())
        assertNotNull(local.diary.get("o"))
    }

    @Test fun recoveryRemovesOnlyOldUnreferencedFilesAndRejectsPathEscapes() = runBlocking<Unit> {
        val photo = manager.attach("o", uri)
        val abandoned = store.import(uri, "missing")
        val cutoff = System.currentTimeMillis() - 48L * 60 * 60 * 1000
        store.file(photo.relativePath).setLastModified(cutoff)
        store.file(abandoned.relativePath).setLastModified(cutoff)
        store.file(abandoned.thumbnailPath!!).setLastModified(cutoff)
        val recent = store.import(uri, "missing")
        assertTrue(manager.recover())
        assertTrue(store.file(photo.relativePath).exists())
        assertFalse(store.file(abandoned.relativePath).exists())
        assertTrue(store.file(recent.relativePath).exists())
        assertThrows(IllegalArgumentException::class.java) { store.file("photos/../../outside.jpg") }
        assertThrows(IllegalArgumentException::class.java) { store.file("https://example/image.jpg") }
    }

    @Test fun f9MigrationPreservesLegacyMetadataDiaryWishlistAndDraftThenReopens() = runBlocking<Unit> {
        db.close()
        context.deleteDatabase(name)
        helper.createDatabase(name, 7).apply {
            val row = f8bTaxon.toRow()
            execSQL("INSERT INTO taxa VALUES (?, ?, ?, 'Animalia', 'ACCEPTED', 'SPECIES', 'fixture', 's', 'test', ?, 'CC0', 'Faunavia', 'synthetic', 'v1')",
                arrayOf(row.id, row.scientificName, row.commonName, f8bNow.toString()))
            execSQL("INSERT INTO observations VALUES ('o', ?, ?, ?, 'Europe/Rome', NULL, NULL, 'legacy', ?, ?, 2, NULL, NULL)",
                arrayOf<Any>(f8bTaxon.id, f8bNow.toString(), f8bNow.epochSecond, f8bNow.toString(), f8bNow.toString()))
            execSQL("INSERT INTO observation_photos VALUES ('p', 'o', 'photos/legacy.jpg', ?, 100, 'image/jpeg')", arrayOf("a".repeat(64)))
            execSQL("INSERT INTO wishlist VALUES (?, ?)", arrayOf(f8bTaxon.id, f8bNow.toString()))
            execSQL("INSERT INTO unidentified_drafts VALUES ('d', ?, 'Europe/Rome', NULL, NULL, 'draft', 1, NULL, NULL, ?, ?)",
                arrayOf(f8bNow.toString(), f8bNow.toString(), f8bNow.toString()))
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, FaunaviaDatabase.MIGRATION_7_8).close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(0))
        val legacy = local.diary.photos("o").single()
        assertEquals(0, legacy.width)
        assertEquals(1, legacy.orientation)
        assertNull(legacy.thumbnailPath)
        assertEquals("legacy", local.diary.get("o")!!.notes)
        assertEquals(2, local.diary.get("o")!!.quantity)
        assertEquals(f8bNow, local.wishlist.list().single().addedAt)
        assertEquals("draft", local.unidentified.get("d")!!.input.notes)
        assertTrue(local.unidentified.photos("d").isEmpty())
        manager = MemoryPhotos(store, local.diary, local.unidentified)
        val fresh = manager.attach("d", uri)
        db.close()
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(0))
        assertEquals(fresh, local.unidentified.photos("d").single())
    }
}
