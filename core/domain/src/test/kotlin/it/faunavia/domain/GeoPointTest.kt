package it.faunavia.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoPointTest {
    @Test
    fun acceptsWgs84Boundaries() {
        assertEquals(GeoPoint(-90.0, -180.0), GeoPoint(-90.0, -180.0))
        assertEquals(GeoPoint(90.0, 180.0), GeoPoint(90.0, 180.0))
    }

    @Test
    fun rejectsInvalidLatitude() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(90.0001, 9.19)
        }
    }

    @Test
    fun evidenceLevelsRemainDistinct() {
        assertEquals(3, EvidenceLevel.entries.toSet().size)
    }
}
