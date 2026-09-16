package it.faunavia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun routesAreUniqueAndComplete() {
        assertEquals(6, AppDestination.entries.size)
        assertEquals(6, AppDestination.entries.map { it.route }.toSet().size)
    }

    @Test
    fun selectionRequiresExactRoute() {
        assertTrue(currentRouteMatches("home", AppDestination.HOME))
        assertFalse(currentRouteMatches("diary", AppDestination.HOME))
        assertFalse(currentRouteMatches(null, AppDestination.HOME))
    }
}
