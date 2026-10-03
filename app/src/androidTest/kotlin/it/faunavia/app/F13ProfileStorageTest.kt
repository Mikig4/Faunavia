package it.faunavia.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.local.*
import it.faunavia.testing.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class F13ProfileStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "f13-profile.db"
    private val photoRoot = File(context.noBackupFilesDir, "f13-profile-photos")
    private val preferences get() = context.getSharedPreferences("species-profiles-v1", android.content.Context.MODE_PRIVATE)
    private lateinit var database: FaunaviaDatabase
    private lateinit var repositories: LocalRepositories
    @Before fun setup() {
        context.deleteDatabase(name); preferences.edit().clear().commit()
        database = FaunaviaDatabase.open(context, name)
        repositories = LocalRepositories(database, FakeClock(f8bNow.toEpochMilli()))
    }
    @After fun cleanup() { database.close(); context.deleteDatabase(name); photoRoot.deleteRecursively(); preferences.edit().clear().commit() }

    @Test fun viewingLeavesAllDurableRowsUnchangedAndLegacyProfileDiaryBackupRoundTripRemainIndependent() = runBlocking<Unit> {
        val taxon = f8bTaxon.copy(scientificName = "Synthetic animal", commonName = "Animale sintetico")
        val stored = SpeciesProfile(taxon.id, "Profilo locale", listOf("Bosco"), setOf(1, 12), "Semi", "20 cm", null, null, f8bProvenance)
        repositories.catalogue.saveTaxon(taxon); repositories.catalogue.saveProfile(stored)
        repositories.diary.create(ObservationDraft("f13-memory", taxon.id, f8bNow, ZoneId.of("Europe/Rome"), notes = "Memoria personale"))
        val before = repositories.backup.snapshot().payload
        val service = SpeciesProfileService(repositories.catalogue, SpeciesProfileCachePreferences(context))
        assertEquals("Semi", service.read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.DIET]?.text)
        assertTrue(service.read("not-selected", "Alcedo atthis", null).profile.curiosities.isNotEmpty())
        assertNull(repositories.catalogue.taxon("not-selected")); assertEquals(before, repositories.backup.snapshot().payload)
        val archive = LocalBackupArchive(context, repositories.backup, PrivatePhotoStore(context, photoRoot), { Long.MAX_VALUE })
        val bytes = ByteArrayOutputStream().also { archive.export(it) }.toByteArray()
        repositories.catalogue.saveProfile(stored.copy(diet = "Frutti"))
        assertEquals("Frutti", service.read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.DIET]?.text)
        val prepared = archive.prepare(ByteArrayInputStream(bytes)); archive.restore(prepared)
        assertEquals(before, repositories.backup.snapshot().payload)
        assertEquals(stored, repositories.catalogue.profile(taxon.id))
        assertEquals("Semi", SpeciesProfileService(repositories.catalogue, SpeciesProfileCachePreferences(context))
            .read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.DIET]?.text)
        assertEquals("Memoria personale", repositories.diary.get("f13-memory")?.notes)
    }

    @Test fun normalizedViewedProfileCacheReopensIsBoundedAndRecoversACorruptPilotCopy() = runBlocking<Unit> {
        val cache = SpeciesProfileCachePreferences(context)
        val profile = PilotNaturalHistory.entries.first()
        val raw = SpeciesProfileCodec.encode(profile, f8bNow)
        repeat(65) { cache.save("entry-$it", raw) }
        assertEquals(64, preferences.all.size)
        assertEquals(profile, SpeciesProfileCodec.decode(requireNotNull(SpeciesProfileCachePreferences(context).read("entry-64")), profile.scientificName))
        val before = preferences.all
        assertTrue(runCatching { cache.save("too-large", "x".repeat(SpeciesProfileCodec.MAX_BYTES + 1)) }.isFailure)
        assertEquals(before, preferences.all)
        preferences.edit().putString("profile-v1:pilot:alcedo atthis", "corrupt").commit()
        assertEquals(profile, SpeciesProfileService(repositories.catalogue, SpeciesProfileCachePreferences(context)).read("pilot", profile.scientificName, null).profile)
        assertEquals(profile, SpeciesProfileCodec.decode(requireNotNull(cache.read("profile-v1:pilot:alcedo atthis")), profile.scientificName))
    }
}
