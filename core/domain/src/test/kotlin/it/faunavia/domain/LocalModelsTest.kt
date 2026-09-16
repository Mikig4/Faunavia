package it.faunavia.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LocalModelsTest {
    private val now = Instant.parse("2026-09-16T22:30:00.123456789Z")
    private val source = Provenance("fixture", "1", "local", now, "CC0", "Faunavia", "synthetic", "1")

    @Test fun onlyAcceptedAnimaliaIsSelectable() {
        val animal = Taxon("fixture:1", "Turdus merula", null, "Animalia", TaxonomicStatus.ACCEPTED, "SPECIES", source)
        assertTrue(animal.isSelectable)
        assertFalse(animal.copy(kingdom = "Plantae").isSelectable)
        assertFalse(animal.copy(status = TaxonomicStatus.SYNONYM).isSelectable)
        assertFalse(animal.copy(status = TaxonomicStatus.DOUBTFUL).isSelectable)
    }

    @Test fun observationRequiresIdentityAndRetainsItsLocalDate() {
        val observation = Observation("o", "t", now, ZoneId.of("Europe/Rome"), null, "", now, now)
        assertEquals(LocalDate.of(2026, 9, 17), observation.localDate)
        assertThrows(IllegalArgumentException::class.java) { observation.copy(taxonId = " ") }
        assertThrows(IllegalArgumentException::class.java) { observation.copy(updatedAt = now.minusSeconds(1)) }
    }

    @Test fun provenanceCannotSilentlyLoseLicenseOrQuality() {
        assertThrows(IllegalArgumentException::class.java) { source.copy(license = "") }
        assertThrows(IllegalArgumentException::class.java) { source.copy(quality = "") }
        assertThrows(IllegalArgumentException::class.java) { source.copy(version = "") }
    }

    @Test fun photoReferencesStayInsidePrivateStorage() {
        val photo = ObservationPhoto("p", "o", "photos/p.jpg", "a".repeat(64), 100, "image/jpeg")
        listOf("../p.jpg", "/p.jpg", "C:/p.jpg", "photos/../p.jpg", "photos\\p.jpg").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { photo.copy(relativePath = path) }
        }
        assertThrows(IllegalArgumentException::class.java) { photo.copy(byteSize = 0) }
    }

    @Test fun invalidRouteAndConfidenceInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Route("r", "route", emptyList(), now) }
        assertThrows(IllegalArgumentException::class.java) {
            SuggestionProfile("t", "Lombardia", emptyList(), false, Double.NaN, "reason", source)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceEvidence("e", "t", EvidenceLevel.INSUFFICIENT, null, null, -1.0, "reason", source)
        }
    }
}
