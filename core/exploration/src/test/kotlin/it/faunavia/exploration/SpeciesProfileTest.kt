package it.faunavia.exploration

import it.faunavia.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class SpeciesProfileTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val source = Provenance("https://example.test/biology", "1", "fact query", now, "CC0", "Synthetic tests", "fixture", "fixture-v1")
    private val taxon = Taxon("gbif:test", "Synthetic animal", "Animale di prova", "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", source)
    private val local = SpeciesProfile(taxon.id, "Descrizione locale", listOf("Bosco"), setOf(1, 12), "Semi", "20 cm", null, null, source)
    private class Cache : SpeciesProfileCache {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun save(key: String, value: String) { values[key] = value }
    }
    private fun catalogue(taxonRead: () -> Taxon? = { taxon }, profileRead: () -> SpeciesProfile? = { local }): CatalogueRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CatalogueRepository::class.java)) { _, method, _ ->
            when (method.name) { "taxon" -> taxonRead(); "profile" -> profileRead(); else -> error("Profile viewing must not call ${method.name}") }
        } as CatalogueRepository

    @Test fun bundledProfilesCoverThePilotWithPerFactProvenanceAndNoImportedMedia() {
        assertEquals(PilotSpeciesPresentation.entries.map { it.scientificName }.toSet(), PilotNaturalHistory.entries.map { it.scientificName }.toSet())
        PilotNaturalHistory.entries.forEach { profile ->
            assertEquals(PilotNaturalHistory.VERSION, profile.contentVersion)
            assertNotNull(profile.nameSource); assertTrue(profile.curiosities.isNotEmpty())
            assertTrue(profile.facts.keys.containsAll(listOf(ProfileField.HABITAT, ProfileField.SEASON, ProfileField.SIZE, ProfileField.DIET, ProfileField.BEHAVIOR, ProfileField.SAFETY)))
            (profile.facts.values + profile.curiosities.map { it.fact }).forEach { fact ->
                assertTrue(safeProfileSource(fact.source.source)); assertTrue(fact.source.quality.contains("locale") || fact.source.quality.contains("Parco"))
                assertTrue(fact.source.license.contains("non importat") || fact.source.license.contains("nessun testo"))
                assertEquals("2026-10-03", fact.source.retrievedAt.toString().take(10))
            }
        }
    }

    @Test fun authorityNamesMatchButSubspeciesAndUncoveredTaxaRemainExplicitlyUnavailable() = runBlocking {
        val service = SpeciesProfileService()
        assertEquals("Merlo", service.read("a", "Turdus merula Linnaeus, 1758", null).profile.commonName)
        val missing = service.read("b", "Turdus merula aterrimus", "Nome fornito")
        assertTrue(missing.profile.facts.isEmpty()); assertTrue(missing.profile.curiosities.isEmpty())
        assertEquals("Nome fornito", missing.profile.commonName)
    }

    @Test fun cacheRoundTripPreservesEverySourceFieldAndFactWithoutNetwork() = runBlocking {
        val cache = Cache()
        val service = SpeciesProfileService(cache = cache, clock = { now })
        val profile = service.read("pilot", "Alcedo atthis", null).profile
        assertEquals(profile, SpeciesProfileCodec.decode(cache.values.values.single(), "Alcedo atthis Linnaeus, 1758"))
        assertEquals(profile, SpeciesProfileService(cache = cache).read("pilot", "Alcedo atthis", null).profile)
    }

    @Test fun corruptFutureOversizedIdentityAndMissingProvenancePayloadsAreRejected() {
        val valid = SpeciesProfileCodec.encode(PilotNaturalHistory.entries.first(), now)
        val root = Json.parseToJsonElement(valid).jsonObject
        val fact = root.getValue("facts").jsonObject.getValue("DIET").jsonObject
        val sourceWithoutLicense = JsonObject(fact.getValue("source").jsonObject.filterKeys { it != "license" })
        val badFact = JsonObject(fact + ("source" to sourceWithoutLicense))
        val badFacts = JsonObject(root.getValue("facts").jsonObject + ("DIET" to badFact))
        listOf("not json", valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            JsonObject(root + ("facts" to badFacts)).toString(), " ".repeat(SpeciesProfileCodec.MAX_BYTES + 1)).forEach {
            assertTrue(runCatching { SpeciesProfileCodec.decode(it, "Alcedo atthis") }.isFailure)
        }
        assertTrue(runCatching { SpeciesProfileCodec.decode(valid, "Turdus merula") }.isFailure)
    }

    @Test fun freshLocalProfilesOverrideCacheAndSuccessfulRestoreRemovalCannotResurrectOldFacts() = runBlocking {
        val cache = Cache()
        var current: SpeciesProfile? = local
        val service = SpeciesProfileService(catalogue(profileRead = { current }), cache, { now })
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("gennaio, dicembre", service.read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.SEASON]?.text)
        } finally { java.util.Locale.setDefault(previousLocale) }
        assertEquals("20 cm", service.read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.SIZE]?.text)
        current = local.copy(size = "30 cm")
        assertEquals("30 cm", service.read(taxon.id, taxon.scientificName, null).profile.facts[ProfileField.SIZE]?.text)
        current = null
        assertTrue(service.read(taxon.id, taxon.scientificName, null).profile.facts.isEmpty())
    }

    @Test fun unreadableLocalStoreUsesTheViewedCopyWithWarningAndCorruptCacheStillHasAFallback() = runBlocking {
        val cache = Cache()
        SpeciesProfileService(catalogue(), cache, { now }).read(taxon.id, taxon.scientificName, null)
        val unavailable = SpeciesProfileService(catalogue(taxonRead = { throw java.io.IOException("offline local store") }), cache)
        val result = unavailable.read(taxon.id, taxon.scientificName, null)
        assertTrue(result.cached); assertTrue(result.warning!!.contains("Copia offline")); assertEquals("Semi", result.profile.facts[ProfileField.DIET]?.text)
        cache.values.keys.toList().forEach { cache.values[it] = "corrupt" }
        val fallback = unavailable.read(taxon.id, taxon.scientificName, null)
        assertFalse(fallback.cached); assertTrue(fallback.profile.facts.isEmpty()); assertTrue(fallback.warning!!.contains("Cache"))
    }

    @Test fun cacheWriteFailureIsVisibleAndCoroutineCancellationIsNotConvertedToMissingData() = runBlocking {
        val cache = object : SpeciesProfileCache {
            override fun read(key: String): String? = null
            override fun save(key: String, value: String) { throw java.io.IOException("disk full") }
        }
        assertTrue(SpeciesProfileService(cache = cache).read("pilot", "Turdus merula", null).warning!!.contains("copia offline"))
        assertTrue(runCatching { SpeciesProfileService(catalogue(taxonRead = { throw CancellationException() })).read("a", "Synthetic animal", null) }
            .exceptionOrNull() is CancellationException)
    }

    @Test fun unsafeSourceSchemesCredentialsAndMalformedUrlsNeverBecomeClickable() {
        listOf("javascript:alert(1)", "file:///sdcard/a", "http://example.test", "https://user:pass@example.test", "https:broken", "https://example.test:444/a", "https://example.test/a\n").forEach {
            assertFalse(it, safeProfileSource(it))
        }
        assertTrue(safeProfileSource("https://www.lipu.it/uccelli/conoscerli-proteggerli/merlo"))
    }
}
