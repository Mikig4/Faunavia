package it.faunavia.exploration

import it.faunavia.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MapsLinksTest {
    @Test fun directionsIncludeDepartureAndArrivalWithoutTheTrackOrDates() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=45.0%2C9.0&destination=45.1%2C9.1&travelmode=driving",
            mapsDirectionsUrl(GeoPoint(45.0, 9.0), GeoPoint(45.1, 9.1)))
    }
    @Test fun linksOnlyTheConfirmedCoordinatesWithUniversalMapsSyntax() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=45.0%2C9.0",
            mapsSearchUrl(GeoPoint(45.0, 9.0)))
        assertEquals("https://www.google.com/maps/search/?api=1&query=-33.86%2C151.2",
            mapsSearchUrl(GeoPoint(-33.86, 151.2)))
    }
}
