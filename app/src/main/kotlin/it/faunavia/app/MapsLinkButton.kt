package it.faunavia.app

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import it.faunavia.domain.GeoPoint
import it.faunavia.exploration.mapsSearchUrl
import it.faunavia.exploration.mapsDirectionsUrl

@Composable
internal fun MapsLinkButton(point: GeoPoint, tag: String, openMap: ((GeoPoint) -> Boolean)? = null) {
    val context = LocalContext.current
    var unavailable by remember(point) { mutableStateOf(false) }
    OutlinedButton(onClick = {
        unavailable = !(openMap?.invoke(point) ?: try {
            context.startActivity(Intent(Intent.ACTION_VIEW, mapsSearchUrl(point).toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        })
    }, modifier = Modifier.testTag(tag)) { Text("Apri in Google Maps") }
    if (unavailable) Text("Non riesco ad aprire Maps o il browser. Riprova.",
        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("$tag-error"))
}

@Composable
internal fun MapsDirectionsButton(departure: GeoPoint, destination: GeoPoint,
    openDirections: ((GeoPoint, GeoPoint) -> Boolean)? = null) {
    val context = LocalContext.current
    var unavailable by remember(departure, destination) { mutableStateOf(false) }
    OutlinedButton(onClick = {
        unavailable = !(openDirections?.invoke(departure, destination) ?: try {
            context.startActivity(Intent(Intent.ACTION_VIEW, mapsDirectionsUrl(departure, destination).toUri()))
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false })
    }, modifier = Modifier.testTag("trip-maps-directions")) { Text("Apri indicazioni in Google Maps") }
    Text("Google Maps ricalcola le indicazioni; il tracciato scelto resta salvato qui.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    if (unavailable) Text("Non riesco ad aprire Maps o il browser. Riprova.",
        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip-maps-directions-error"))
}
