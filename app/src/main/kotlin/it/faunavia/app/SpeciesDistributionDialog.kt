package it.faunavia.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.faunavia.exploration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalSpeciesMetadata = staticCompositionLocalOf<SpeciesMetadataLookup> { UnavailableSpeciesMetadata }
internal val LocalSpeciesImageLoader = staticCompositionLocalOf<SpeciesMapImageLoader> {
    object : SpeciesMapImageLoader { override fun load(url: String): Bitmap = error("Map image loader unavailable") }
}
internal interface SpeciesDensityMapAdapter { @Composable fun Render(gbifKey: String) }
internal val LocalSpeciesDensityMap = staticCompositionLocalOf<SpeciesDensityMapAdapter> { MapLibreSpeciesDensityMapAdapter }

@Composable
internal fun SpeciesDistributionDialog(id: String, scientificName: String, onClose: () -> Unit) {
    val lookup = LocalSpeciesMetadata.current
    val renderer = LocalSpeciesDensityMap.current
    var refresh by rememberSaveable(id) { mutableIntStateOf(0) }
    var result by remember(id, scientificName) { mutableStateOf<MetadataResult<SpeciesDistribution>?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var observations by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(id, scientificName, refresh, lookup) {
        loading = true
        try { result = withContext(Dispatchers.IO) { lookup.distribution(id, scientificName, refresh > 0) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { result = MetadataResult(null, "Dati cartografici non recuperabili. Riprova.")
        } finally { loading = false }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("species-distribution-dialog"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()).testTag("species-distribution-content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClose, modifier = Modifier.testTag("species-distribution-close")) { Text("Torna alla scheda") }
                Text("Distribuzione · $scientificName", style = MaterialTheme.typography.titleLarge)
                if (loading) Text("Caricamento dei dati cartografici…", modifier = Modifier.testTag("species-distribution-loading"))
                result?.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (result?.stale == true) Text("Metadati dalla cache non aggiornata.", style = MaterialTheme.typography.bodySmall)
                val value = result?.value
                val illustration = value?.illustration
                val gbifKey = value?.gbifKey
                if (illustration != null && gbifKey != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !observations, onClick = { observations = false }, label = { Text("Areale illustrato") })
                        FilterChip(selected = observations, onClick = { observations = true }, label = { Text("Segnalazioni GBIF") })
                    }
                }
                if (illustration != null && (!observations || gbifKey == null)) {
                    Text("Areale illustrato · ingrandisci con due dita")
                    RangeMapImage(illustration)
                    Text("Data indicata: ${illustration.date}", style = MaterialTheme.typography.bodySmall)
                    SourceSummary(illustration.source)
                    Text("La mappa descrive la distribuzione generale secondo la sua fonte e legenda. Non conferma la presenza attuale nel luogo ricercato.", style = MaterialTheme.typography.bodySmall)
                } else if (gbifKey != null) {
                    Text("Segnalazioni GBIF · osservazioni storiche aggregate", modifier = Modifier.testTag("species-distribution-observations"))
                    renderer.Render(gbifKey)
                    Text("© OpenStreetMap contributors · GBIF. I colori mostrano la densità delle segnalazioni archiviate, influenzata dalla raccolta dei dati. La mappa non misura la probabilità di un incontro; una zona vuota non dimostra assenza della specie.", style = MaterialTheme.typography.bodySmall)
                    SourceSummary(requireNotNull(value).source)
                    Text("Mappa online: se i tasselli non si caricano, riprova con una connessione disponibile.", style = MaterialTheme.typography.bodySmall)
                } else if (!loading) Text("Nessuna mappa disponibile per questa identità scientifica. Non è prova di assenza della specie.", modifier = Modifier.testTag("species-distribution-unavailable"))
                if (!loading) OutlinedButton(onClick = { refresh++ }, modifier = Modifier.testTag("species-distribution-retry")) { Text("Riprova dati cartografici") }
            }
        }
    }
}

@Composable
private fun RangeMapImage(illustration: RangeIllustration) {
    val loader = LocalSpeciesImageLoader.current
    var refresh by rememberSaveable(illustration.imageUrl) { mutableIntStateOf(0) }
    var bitmap by remember(illustration.imageUrl) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(illustration.imageUrl) { mutableStateOf(false) }
    var loading by remember(illustration.imageUrl) { mutableStateOf(true) }
    var zoom by remember(illustration.imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(illustration.imageUrl) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, factor, pan, _ ->
        zoom = (zoom * factor).coerceIn(1f, 5f)
        offset = if (zoom <= 1f) Offset.Zero else offset + pan
    }
    LaunchedEffect(illustration.imageUrl, refresh, loader) {
        failed = false; loading = true
        try { bitmap = withContext(Dispatchers.IO) { loader.load(illustration.imageUrl) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { failed = true
        } finally { loading = false }
    }
    if (loading) Text("Caricamento dell’immagine…")
    bitmap?.let { image ->
        Box(Modifier.fillMaxWidth().height(320.dp).clipToBounds().transformable(transform)) {
            Image(image.asImageBitmap(), "Mappa di distribuzione con legenda originale", Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
            }.testTag("species-distribution-image"))
        }
        TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Ripristina zoom") }
    }
    if (failed) {
        Text("Immagine non disponibile: dati e fonti restano consultabili.", color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("species-distribution-image-error"))
        OutlinedButton(onClick = { refresh++ }) { Text("Riprova immagine") }
    }
}
