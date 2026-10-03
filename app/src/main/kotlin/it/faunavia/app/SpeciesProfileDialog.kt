package it.faunavia.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.Provenance
import it.faunavia.exploration.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset

internal val LocalSpeciesProfiles = staticCompositionLocalOf<SpeciesProfileLookup?> { null }
internal val LocalProfileSourceOpener = staticCompositionLocalOf<((String) -> Unit)?> { null }
internal fun interface ProfileFallbackLoader { fun load(context: Context): ImageBitmap }
internal val LocalProfileFallback = staticCompositionLocalOf<ProfileFallbackLoader> {
    ProfileFallbackLoader { context -> requireNotNull(ContextCompat.getDrawable(context, R.drawable.species_fallback)).toBitmap(192, 192).asImageBitmap() }
}

@Composable
internal fun SpeciesProfileAction(id: String, scientificName: String, catalogue: CatalogueRepository? = null,
    commonName: String? = null, nameSource: Provenance? = null) {
    var opened by rememberSaveable(id, scientificName) { mutableStateOf(false) }
    val localLookup = remember(catalogue) { SpeciesProfileService(catalogue) }
    val lookup = LocalSpeciesProfiles.current ?: localLookup
    TextButton(onClick = { opened = true }, modifier = Modifier.testTag("species-profile-$id")) { Text("Apri scheda specie") }
    if (opened) SpeciesProfileDialog(id, scientificName, commonName, lookup, nameSource) { opened = false }
}

@Composable
internal fun SpeciesProfileDialog(id: String, scientificName: String, commonName: String? = null,
    lookup: SpeciesProfileLookup? = null, nameSource: Provenance? = null, onClose: () -> Unit) {
    val parentDensity = LocalDensity.current
    val reader = lookup ?: remember { SpeciesProfileService() }
    var result by remember(id, scientificName) { mutableStateOf<SpeciesProfileResult?>(null) }
    var retry by rememberSaveable(id, scientificName) { mutableIntStateOf(0) }
    var loading by remember(id, scientificName) { mutableStateOf(true) }
    var curiositiesOpen by rememberSaveable(id, scientificName) { mutableStateOf(false) }
    var distributionOpen by rememberSaveable(id, scientificName) { mutableStateOf(false) }
    var modelOpen by rememberSaveable(id, scientificName) { mutableStateOf(false) }
    val modelStore = LocalPersonalModels.current ?: (LocalContext.current.applicationContext as? FaunaviaApplication)?.personalModels
    LaunchedEffect(id, scientificName, commonName, reader, retry) {
        loading = true
        try { result = withContext(Dispatchers.IO) { reader.read(id, scientificName, commonName) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            result = SpeciesProfileResult(ReadableSpeciesProfile(scientificName, commonName, null, emptyMap(), contentVersion = "unavailable-v1"),
                "Non riesco a leggere la scheda. Riprova; il nome scientifico resta disponibile.")
        } finally { loading = false }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The native dialog installs its own density. Keep the app's font scale for its content.
        CompositionLocalProvider(LocalDensity provides parentDensity) {
        Surface(Modifier.fillMaxSize().testTag("species-profile-dialog"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Scheda specie", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
                    TextButton(onClick = onClose, modifier = Modifier.testTag("profile-close")) { Text("Chiudi") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp).testTag("profile-content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val profile = result?.profile
                    Text(profile?.commonName ?: commonName ?: scientificName, style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("profile-title").semantics { heading() })
                    if (profile?.commonName != null || commonName != null) Text(scientificName, style = MaterialTheme.typography.bodyMedium)
                    Text("Storia naturale generale · non conferma la presenza nel luogo o nel periodo del viaggio.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-evidence-boundary"))
                    SpeciesFallback()
                    if (modelStore != null) SpeciesModelAction { modelOpen = true }
                    if (loading) Text("Caricamento della scheda…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    result?.warning?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-warning"))
                        OutlinedButton(onClick = { retry++ }, modifier = Modifier.testTag("profile-retry")) { Text("Riprova scheda") }
                    }
                    profile?.let { value ->
                        Text("Scheda disponibile offline", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("profile-version"))
                        value.nameSource?.let { ProfileSource("names", it, "Fonte dei nomi") }
                        if (nameSource != null && nameSource != value.nameSource) ProfileSource("taxonomy", nameSource, "Fonte del nome consultato")
                        if (value.nameSource == null && nameSource == null) Text("Fonte dei nomi non disponibile.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-name-source-unavailable"))
                        TextButton(onClick = { curiositiesOpen = !curiositiesOpen }, modifier = Modifier.fillMaxWidth().testTag("profile-curiosities-toggle")
                            .semantics { stateDescription = if (curiositiesOpen) "Espanse" else "Chiuse" }) {
                            Text(if (curiositiesOpen) "Chiudi curiosità" else "Curiosità")
                        }
                        if (curiositiesOpen) {
                            if (value.curiosities.isEmpty()) Text("Curiosità documentate non disponibili per questa specie.", modifier = Modifier.testTag("profile-curiosities-unavailable"))
                            value.curiosities.forEachIndexed { index, curiosity ->
                                Text(curiosity.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Text(curiosity.fact.text, modifier = Modifier.testTag("profile-curiosity-$index"))
                                val source = curiosity.fact.source
                                Text("${source.attribution} · consultata ${source.retrievedAt.atZone(ZoneOffset.UTC).toLocalDate()}", style = MaterialTheme.typography.bodySmall)
                                ProfileSource("curiosity-$index", source)
                            }
                        }
                        ProfileField.entries.filter { it != ProfileField.DESCRIPTION || it in value.facts }.forEach { field ->
                            HorizontalDivider()
                            Text(field.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            val fact = value.facts[field]
                            Text(fact?.text ?: "Dato documentato non disponibile", modifier = Modifier.testTag("profile-field-${field.name}"))
                            if (fact != null) ProfileSource(field.name, fact.source)
                            if (field == ProfileField.HABITAT) TextButton(onClick = { distributionOpen = true }, modifier = Modifier.testTag("profile-distribution")) {
                                Text("Apri distribuzione nell’app")
                            }
                        }
                        Text("Le evidenze del viaggio e le loro fonti restano nel risultato di ricerca: chiudi la scheda e apri Evidenze e fonti.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-return-evidence"))
                    }
                }
            }
        }
        }
    }
    if (distributionOpen) SpeciesDistributionDialog(id, scientificName) { distributionOpen = false }
    // Keep both dialog visibility and its saveable controls in the profile's owning composition.
    if (modelOpen && modelStore != null) SpeciesModelDialog(id, scientificName, modelStore) { modelOpen = false }
}

@Composable
private fun ProfileSource(tag: String, source: Provenance, label: String = "Fonte e licenza") {
    var open by rememberSaveable(tag, source.source) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, modifier = Modifier.testTag("profile-source-$tag")) { Text(if (open) "Chiudi fonte" else label) }
    if (open) {
        Text("${source.attribution}\n${source.source}\nConsultata ${source.retrievedAt}\nLicenza: ${source.license}\nQualità: ${source.quality}\nVersione: ${source.version}\nRecord: ${source.recordId}\nContesto: ${source.query}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-source-details-$tag"))
        val uriHandler = LocalUriHandler.current
        val opener = LocalProfileSourceOpener.current
        var failed by remember(source.source) { mutableStateOf(false) }
        if (safeProfileSource(source.source)) TextButton(onClick = {
            failed = runCatching { if (opener != null) opener(source.source) else uriHandler.openUri(source.source) }.isFailure
        }, modifier = Modifier.testTag("profile-source-link-$tag")) { Text("Apri fonte originale") }
        else Text("Collegamento alla fonte non disponibile.", modifier = Modifier.testTag("profile-source-unavailable-$tag"))
        if (failed) Text("Non riesco ad aprire la fonte. L'indirizzo resta visibile.", color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("profile-source-error-$tag"))
    }
}

@Composable
private fun SpeciesFallback() {
    val context = LocalContext.current
    val loader = LocalProfileFallback.current
    val bitmap = remember(loader, context) { runCatching { loader.load(context) }.getOrNull() }
    val description = "Simbolo generico della fauna, non immagine identificativa della specie"
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            if (bitmap != null) Image(bitmap, description, Modifier.fillMaxWidth().height(88.dp).testTag("profile-fallback"))
            else {
                val ink = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(88.dp).testTag("profile-fallback").semantics { contentDescription = description }) {
                    val unit = size.height / 5f
                    drawOval(ink, Offset(size.width / 2 - unit, 2 * unit), Size(2 * unit, 2 * unit))
                    listOf(-1.1f, -0.4f, 0.4f, 1.1f).forEach { x -> drawCircle(ink, unit * .35f, Offset(size.width / 2 + x * unit, unit)) }
                }
                Text("Anteprima non leggibile: mostro un simbolo 2D di riserva.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("profile-fallback-recovery"))
            }
            Text("Immagine identificativa non disponibile · simbolo generico", style = MaterialTheme.typography.bodySmall)
        }
    }
}
