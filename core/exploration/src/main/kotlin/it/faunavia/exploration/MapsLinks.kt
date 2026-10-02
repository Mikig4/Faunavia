package it.faunavia.exploration

import it.faunavia.domain.GeoPoint
import java.net.URLEncoder

/** Opens a confirmed point externally; neither tracks nor personal diary data are included. */
fun mapsSearchUrl(point: GeoPoint): String {
    val coordinates = URLEncoder.encode("${point.latitude},${point.longitude}", "UTF-8")
    return "https://www.google.com/maps/search/?api=1&query=$coordinates"
}

/** Maps calculates its own directions; this URL does not transmit the stored full geometry. */
fun mapsDirectionsUrl(departure: GeoPoint, destination: GeoPoint): String {
    fun coordinates(point: GeoPoint) = URLEncoder.encode("${point.latitude},${point.longitude}", "UTF-8")
    return "https://www.google.com/maps/dir/?api=1&origin=${coordinates(departure)}&destination=${coordinates(destination)}&travelmode=driving"
}
