package it.faunavia.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.faunavia.domain.*
import it.faunavia.exploration.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun OutingDiscoveryDialog(trip: Trip, wishlist: WishlistRepository?, catalogue: CatalogueRepository,
    outings: List<Outing>, response: OutingSearchResult?, searching: Boolean, saving: Boolean, saveError: String?,
    onSearch: () -> Unit, onClose: () -> Unit,
    onSave: (OutingProposal, LocalDate) -> Unit) {
    var dateText by rememberSaveable(trip.id) { mutableStateOf(trip.startsOn.toString()) }
    var shortOnly by rememberSaveable(trip.id) { mutableStateOf(false) }
    var wishedOnly by rememberSaveable(trip.id) { mutableStateOf(false) }
    var seasonalOnly by rememberSaveable(trip.id) { mutableStateOf(false) }
    var sort by rememberSaveable(trip.id) { mutableStateOf(OutingSort.RELEVANCE) }
    var wishes by remember(trip.id) { mutableStateOf<List<Taxon>>(emptyList()) }
    var wishError by remember(trip.id) { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(trip.id, retry, wishlist, catalogue) {
        wishError = false
        runCatching { withContext(Dispatchers.IO) { wishlist?.list().orEmpty().mapNotNull { catalogue.taxon(it.taxonId) } } }
            .onSuccess { wishes = it }.onFailure { wishError = true }
    }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()?.takeIf { it in trip.startsOn..trip.endsOn }
    val discovery = remember(response) { response?.let { OutingDiscovery(OutingCatalogue { it.proposals }) } }
    val result = remember(trip, date, wishes, shortOnly, wishedOnly, seasonalOnly, sort, discovery) {
        date?.let { discovery?.discover(trip, wishes, OutingFilters(it, if (shortOnly) 120 else null,
            wishedOnly = wishedOnly, seasonalOnly = seasonalOnly, sort = sort)) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("discovery-dialog"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aiuto per le uscite", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
                    TextButton(onClick = onClose, modifier = Modifier.testTag("discovery-close")) { Text("Chiudi") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("discovery-content"), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text(trip.departure?.let { "${it.name} → ${trip.destination.name}" } ?: "Vicino a ${trip.destination.name}", style = MaterialTheme.typography.headlineSmall)
                        Text("Ricerca online OpenStreetMap · entro ${trip.radiusMeters / 1_000} km ${if (trip.route == null) "dal luogo" else "dal percorso selezionato"}.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("discovery-coverage"))
                        Text("Documentazione generale del luogo, senza garanzia di avvistamenti o accesso. I dettagli salvati conservano le fonti; verifica gli avvisi del gestore prima di partire.",
                            style = MaterialTheme.typography.bodySmall)
                        if (searching) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("discovery-loading"))
                            Text("Cerco luoghi e itinerari lungo il percorso…")
                        }
                        OutlinedButton(onClick = onSearch, enabled = !searching,
                            modifier = Modifier.testTag("discovery-retry")) { Text(if (response == null) "Cerca online" else "Riprova ricerca") }
                        OutingExternalLink(outingWebSearchUrl(trip), "Approfondisci su Google", "discovery-google")
                        OutlinedTextField(dateText, { dateText = it.take(10) }, label = { Text("Giorno dell’uscita (AAAA-MM-GG)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("discovery-date"))
                        if (date == null) Text("Scegli un giorno tra ${trip.startsOn} e ${trip.endsOn}.", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("discovery-date-error"))
                    }
                    item {
                        Row(Modifier.fillMaxWidth()) { Checkbox(shortOnly, { shortOnly = it }, modifier = Modifier.testTag("discovery-short")); Text("Durata documentata fino a 2 ore", Modifier.padding(top = 12.dp)) }
                        Row(Modifier.fillMaxWidth()) { Checkbox(wishedOnly, { wishedOnly = it }, enabled = !wishError, modifier = Modifier.testTag("discovery-wishes")); Text("Specie della mia lista desideri", Modifier.padding(top = 12.dp)) }
                        Row(Modifier.fillMaxWidth()) { Checkbox(seasonalOnly, { seasonalOnly = it }, modifier = Modifier.testTag("discovery-season")); Text("Periodo consigliato documentato", Modifier.padding(top = 12.dp)) }
                        Text("I filtri escludono i dati mancanti. OpenStreetMap descrive luoghi e percorsi; specie e periodi consigliati potrebbero non essere documentati. Cambiare un filtro non avvia altre ricerche online.", style = MaterialTheme.typography.bodySmall)
                        if (wishError) {
                            Text("Non riesco a leggere i desideri locali.", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { retry++ }) { Text("Riprova desideri") }
                        }
                        LazyRow(Modifier.testTag("discovery-sort"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(OutingSort.entries) { value -> FilterChip(sort == value, { sort = value }, label = { Text(when (value) {
                                OutingSort.RELEVANCE -> "Pertinenza"; OutingSort.DISTANCE -> "Distanza"; OutingSort.DURATION -> "Durata"
                            }) }, modifier = Modifier.testTag("discovery-sort-${value.name}")) }
                        }
                    }
                    saveError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("discovery-save-error")) } }
                    response?.warning?.let { item {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("discovery-warning"))
                    } }
                    if (!searching && result?.outings?.isEmpty() == true && response?.warning == null) item {
                        Text("Nessuna proposta trovata entro questo raggio e questi filtri. Puoi approfondire su Google, creare un’uscita manuale o importare un percorso da Viaggi.",
                            modifier = Modifier.testTag("discovery-empty"))
                    }
                    val matches = result?.outings.orEmpty().take(40)
                    if (result?.outings.orEmpty().size > matches.size) item { Text("Mostrate le prime 40 proposte. Restringi i filtri per vedere altri risultati.") }
                    items(matches, key = { it.proposal.id }) { match ->
                        val proposal = match.proposal
                        val guide = proposal.guide
                        Column(Modifier.fillMaxWidth().testTag("discovery-${proposal.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(proposal.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Text(String.format(Locale.ITALY, "%.1f km in linea d’aria %s · tempo di trasferimento non disponibile", match.distanceMeters / 1_000,
                                if (trip.route == null) "dal luogo" else "dal percorso"),
                                style = MaterialTheme.typography.bodySmall)
                            OutingGuideDetails(guide, proposal.route, "discovery-${proposal.id}")
                            proposal.website?.let { OutingExternalLink(it, "Sito indicato in OpenStreetMap", "discovery-website-${proposal.id}") }
                            Text(if (match.species.isEmpty()) "Specie del luogo non documentate in questa fonte."
                                else "Specie riportate per il luogo: ${match.species.joinToString { it.commonName }}. Documentazione generale, non presenza attuale.",
                                modifier = Modifier.testTag("discovery-species-${proposal.id}"))
                            if (match.wishedSpecies.isNotEmpty()) Text("Nei tuoi desideri: ${match.wishedSpecies.joinToString { it.commonName }}")
                            Text(if (match.seasonalSpecies.isEmpty()) "Periodo consigliato non documentato per queste date."
                                else "Periodo consigliato dal gestore: ${match.seasonalSpecies.joinToString { it.commonName }}")
                            val alreadySaved = outings.any { it.id == "f17:${trip.id}:${proposal.id}:$date" }
                            Button(onClick = { date?.let { onSave(proposal, it) } }, enabled = !saving && date != null,
                                modifier = Modifier.testTag("discovery-save-${proposal.id}")) { Text(if (alreadySaved) "Apri uscita già salvata" else "Salva nel viaggio") }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutingExternalLink(url: String, label: String, tag: String) {
    val uriHandler = LocalUriHandler.current
    val opener = LocalProfileSourceOpener.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(onClick = {
        failed = runCatching { if (opener != null) opener(url) else uriHandler.openUri(url) }.isFailure
    }, modifier = Modifier.testTag(tag)) { Text(label) }
    if (failed) Text("Non riesco ad aprire il collegamento. Riprova.", color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun OutingGuideDetails(guide: OutingGuide, route: Route?, tag: String, now: Instant = Instant.now()) {
    Text("Partenza: ${guide.startLabel}")
    Text("Lunghezza: ${guide.lengthMeters?.let { String.format(Locale.ITALY, "%.1f km", it / 1_000.0) } ?: "non disponibile"} · Durata indicativa: ${guide.durationMinutes?.let { "$it min" } ?: "non disponibile"}")
    Text("Difficoltà: ${guide.difficulty ?: "non documentata"} · Accessibilità: ${guide.accessibility ?: "non documentata"}")
    Text(guide.accessNotes, modifier = Modifier.testTag("$tag-access"))
    Text("Coordinate rappresentative del luogo pubblico; non indicano l’ingresso esatto né località di animali.", style = MaterialTheme.typography.bodySmall)
    if (route == null) Text("Traccia non disponibile. Consulta i percorsi ufficiali; nessun sentiero è ricostruito dai punti fauna.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("$tag-no-trace"))
    else OutingTrace(route, tag)
    Text("Fonti consultate ${guide.source.retrievedAt.toString().take(10)} · ${guide.catalogueVersion}", style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("$tag-source-date"))
    if (Duration.between(guide.source.retrievedAt, now) > Duration.ofDays(90)) Text("Dati datati: verifica gli aggiornamenti del gestore.",
        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("$tag-stale"))
    ProfileSource("$tag-guide", guide.source, "Fonte dell’itinerario")
    if (guide.accessSource != guide.source) ProfileSource("$tag-access", guide.accessSource, "Fonte degli accessi")
    ProfileSource("$tag-coordinate", guide.coordinateSource, "Fonte delle coordinate")
    guide.species.map { it.source }.distinct().forEachIndexed { index, source -> ProfileSource("$tag-species-$index", source, "Fonte delle specie") }
}

@Composable
internal fun OutingTrace(route: Route, tag: String) {
    val ink = MaterialTheme.colorScheme.primary
    Text("Traccia salvata · schema offline, non navigazione", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(160.dp).testTag("$tag-trace").semantics { contentDescription = "Geometria del percorso salvato, segmenti separati" }) {
        val south = route.points.minOf { it.latitude }; val north = route.points.maxOf { it.latitude }
        val west = route.points.minOf { it.longitude }; val east = route.points.maxOf { it.longitude }
        fun position(point: GeoPoint) = Offset((16 + ((point.longitude - west) / (east - west).coerceAtLeast(.00001)) * (size.width - 32)).toFloat(),
            (16 + ((north - point.latitude) / (north - south).coerceAtLeast(.00001)) * (size.height - 32)).toFloat())
        route.segments.forEach { segment -> segment.zipWithNext().forEach { (a, b) -> drawLine(ink, position(a), position(b), strokeWidth = 4f) } }
        drawCircle(ink, 6f, position(route.points.first()))
    }
}
