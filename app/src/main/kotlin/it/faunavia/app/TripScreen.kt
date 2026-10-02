package it.faunavia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.occurrence.OccurrenceResultOrigin
import it.faunavia.plausibility.AnalysisPeriod
import it.faunavia.route.RouteAnalysisConfig
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TripInk = Color(0xFF26382E)
private val TripMuted = Color(0xFF52675A)
private val TripError = Color(0xFF9B1C1C)

internal fun tripAnalysisKey(trip: Trip, outing: Outing?, stageId: String? = null): String {
    if (stageId != null && trip.stages.none { it.id == stageId }) return "removed-stage:$stageId"
    val scope = trip.stageScope(stageId).analysisKey + (stageId?.let { "|stage:$it" } ?: "") + "|" +
        (outing?.let { listOf(it.id, it.date, it.place.center, it.route?.segments).joinToString("|") } ?: "destination")
    return MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun resultSnapshot(taxon: ExploredTaxon, trip: Trip, outing: Outing?, result: ExplorationResult.Ready,
    savedAt: Instant, stageId: String? = null): SavedTripResult {
    val analysisTrip = trip.stageScope(stageId)
    val evidence = taxon.occurrences.map { record ->
        SourceEvidence(record.id, taxon.id, taxon.assessment.level,
            runCatching { LocalDate.parse(record.observedOn).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull(),
            record.location, record.coordinateUncertaintyMeters,
            "${record.provider.name} · ${record.observedOn ?: "data non disponibile"} · ${record.sourceUrl}", record.provenance)
    } + taxon.assessment.explanation.flatMap { step -> step.provenance.map { source ->
        SourceEvidence("${step.kind}:${source.source}:${source.recordId}", taxon.id, taxon.assessment.level,
            null, null, null, explanationText(step), source)
    } }
    return SavedTripResult("${trip.id}:${outing?.id ?: stageId?.let { "stage:$it" } ?: "destination"}:${taxon.id}", trip.id, outing?.id, taxon.id,
        taxon.scientificName, taxon.assessment.level, taxon.assessment.explanation.map(::explanationText),
        seasonSummary(taxon.assessment.season),
        evidence, tripAnalysisKey(trip, outing, stageId), savedAt, result.partial, result.origin == OccurrenceResultOrigin.STALE_CACHE,
        outing?.date ?: analysisTrip.startsOn, outing?.date ?: analysisTrip.endsOn, outing?.place ?: analysisTrip.destination, stageId)
}

/** Never infer an accepted identity from a provider occurrence's name alone. */
internal suspend fun observationPrefill(id: String, name: String, catalogue: CatalogueRepository,
    search: TaxonomySearch, tripId: String? = null, outingId: String? = null): DiaryPrefill {
    val local = catalogue.taxon(id)?.takeIf { it.isSelectable }
    if (local != null) return DiaryPrefill(local, name, tripId, outingId)
    val found = runCatching { search.search(name) }.getOrNull()
    val entries = when (found) {
        is TaxonomySearchResult.Results -> found.entries
        is TaxonomySearchResult.Failure -> found.cachedEntries
        else -> emptyList()
    }
    val exact = entries.firstOrNull { it.taxon.id == id }
        ?: entries.singleOrNull { it.taxon.scientificName.equals(name.trim(), true) }
    if (exact != null) {
        search.select(exact)
        return DiaryPrefill(exact.taxon, name, tripId, outingId)
    }
    return DiaryPrefill(query = name, tripId = tripId, outingId = outingId)
}

@Composable
internal fun TripsScreen(
    repository: TripRepository, diary: DiaryRepository, unidentified: UnidentifiedRepository,
    catalogue: CatalogueRepository, taxonomy: TaxonomySearch, geocoder: PlaceSearch,
    explorer: ExplorationService, routes: RouteRepository,
    map: ExplorationMapAdapter = MapLibreExplorationMapAdapter,
    now: () -> Instant = Instant::now, idFactory: () -> String = { UUID.randomUUID().toString() },
    onExplore: (() -> Unit)? = null, onImportRoute: (() -> Unit)? = null,
    openMap: ((GeoPoint) -> Boolean)? = null,
    routing: TripRouting = TripRouting { _, _ -> TripRoutingResult.Unavailable },
    routeMap: TripRouteMapAdapter = MapLibreTripRouteMapAdapter,
    openDirections: ((GeoPoint, GeoPoint) -> Boolean)? = null,
    wishlist: WishlistRepository? = null,
) {
    var personalSuggestions by rememberSaveable { mutableStateOf(false) }
    var typicalOnly by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var outings by remember { mutableStateOf<List<Outing>>(emptyList()) }
    var places by remember { mutableStateOf<List<SavedTripPlace>>(emptyList()) }
    var savedResults by remember { mutableStateOf<List<SavedTripResult>>(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var outingId by rememberSaveable { mutableStateOf("") }
    var stageId by rememberSaveable { mutableStateOf<String?>(null) }
    var stageEditor by rememberSaveable { mutableStateOf(false) }
    var tripEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var outingEditor by rememberSaveable { mutableStateOf<String?>(null) }
    var memories by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<DiaryPrefill?>(null) }
    val analysisState = viewModel<AnalysisViewModel>(key = "trip-analysis")
    val live by analysisState.result
    val liveKey by analysisState.scopeKey
    var actionBusy by remember { mutableStateOf(false) }
    val busy = actionBusy || analysisState.loading.value
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val trip = trips.firstOrNull { it.id == selectedId }
    val outing = outings.firstOrNull { it.id == outingId }
    val analysisTrip = trip?.stageScope(stageId?.takeIf { selected -> trip.stages.any { it.id == selected } })

    LaunchedEffect(refresh, selectedId) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val list = repository.list()
                val selected = list.firstOrNull { it.id == selectedId }
                list to Triple(selected?.let { repository.outings(it.id) }.orEmpty(),
                    selected?.let { repository.places(it.id) }.orEmpty(), selected?.let { repository.results(it.id) }.orEmpty())
            }
        }.onSuccess { (list, details) ->
            trips = list; outings = details.first; places = details.second; savedResults = details.third
            if (outingId.isNotEmpty() && outings.none { it.id == outingId }) outingId = ""
            if (trips.firstOrNull { it.id == selectedId }?.stages?.none { it.id == stageId } != false) stageId = null
        }.onFailure { error = "Non riesco a leggere i viaggi locali. Riprova." }
        loading = false
    }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            actionBusy = true; error = null
            runCatching { block() }.onFailure {
                error = if (it is IllegalArgumentException) it.message else "Operazione non riuscita. Riprova: i dati locali sono conservati."
            }
            actionBusy = false
        }
    }

    fun saw(id: String, name: String, linkedOutingId: String? = outing?.id) = action {
        prefill = withContext(Dispatchers.IO) { observationPrefill(id, name, catalogue, taxonomy, trip?.id, linkedOutingId) }
    }

    if (memories || prefill != null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { memories = false; prefill = null; refresh++ }, modifier = Modifier.testTag("trip-back")) { Text("Torna al viaggio") }
            DiaryScreen(diary, catalogue, taxonomy, unidentifiedRepository = unidentified, tripRepository = repository,
                fixedTripId = trip?.id, fixedOutingId = outing?.id, prefill = prefill,
                now = now, idFactory = idFactory,
                onExitEditor = { if (prefill != null) { prefill = null; refresh++ } })
        }
        return
    }

    if (personalSuggestions && trip != null && analysisTrip != null && wishlist != null) {
        val current = (live as? ExplorationResult.Ready)?.takeIf { liveKey == tripAnalysisKey(trip, outing, stageId) }
        val snapshots = savedResults.filter { it.analysisKey == tripAnalysisKey(trip, outing, stageId) }
        val candidates = current?.taxa.orEmpty().map { it.suggestionCandidate() } + snapshots.mapNotNull { it.suggestionCandidate() }
        val points = outing?.route?.points ?: outing?.let { listOf(it.place.center) }
            ?: analysisTrip.route?.geometry?.points ?: listOf(analysisTrip.destination.center)
        PersonalSuggestionsScreen(wishlist, catalogue, diary, taxonomy, candidates, points,
            "${outing?.name ?: analysisTrip.destination.name} · ${outing?.date ?: analysisTrip.startsOn} → ${outing?.date ?: analysisTrip.endsOn}" +
                if (current?.partial == true || current?.origin == OccurrenceResultOrigin.STALE_CACHE || snapshots.any { it.partial || it.stale }) " · dati parziali o cache non aggiornata" else " · risultati disponibili, copertura non esaustiva",
            onBack = { personalSuggestions = false; refresh++ }, onSaw = { id, name -> saw(id, name) }, externalError = error)
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("screen-trips")) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(MaterialTheme.colorScheme.primary).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
            Column {
                Text("Faunavia", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                Text("Viaggi", color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("screen-title-trips"))
            }
        }
        when {
            stageEditor && trip != null -> TripStagesForm(trip, geocoder, routing, routeMap, now, idFactory, busy,
                onCancel = { stageEditor = false }, onSave = { changed -> action {
                    repository.save(changed); stageEditor = false; stageId = null; outingId = ""; analysisState.clear(); refresh++
                } })
            loading && ((tripEditor != null && tripEditor != "new") || outingEditor != null) ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Caricamento dei dati dell’editor…", color = TripMuted, modifier = Modifier.testTag("trip-editor-loading"))
                }
            tripEditor != null -> TripForm(trips.firstOrNull { it.id == tripEditor }, geocoder, now, idFactory, busy,
                onCancel = { tripEditor = null }, onSave = { changed -> action {
                    repository.save(changed); selectedId = changed.id; outingId = ""; tripEditor = null; refresh++
                } }, routing = routing, routeMap = routeMap)
            outingEditor != null && trip != null -> OutingForm(trip, outings.firstOrNull { it.id == outingEditor }, routes, places,
                geocoder, now, idFactory, busy, onCancel = { outingEditor = null }, onSave = { changed -> action {
                    repository.saveOuting(changed); outingId = changed.id; outingEditor = null; refresh++
                } }, onImportRoute = onImportRoute)
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("trip-content"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Button(onClick = { tripEditor = "new" }, enabled = !busy, modifier = Modifier.testTag("trip-new")) { Text("Nuovo viaggio") }
                    Text("Scegli partenza, destinazione, date e percorso. Ritrovi qui animali documentati e ricordi del viaggio.", color = TripMuted)
                    onExplore?.let { TextButton(onClick = it, modifier = Modifier.testTag("trip-explore")) { Text("Esplora senza salvare un viaggio") } }
                }
                if (loading) item { Text("Caricamento dei viaggi…", color = TripMuted, modifier = Modifier.testTag("trip-loading")) }
                if (!loading && trips.isEmpty()) item { Text("Crea il tuo primo viaggio e scegli il tracciato sulla mappa.", color = TripMuted, modifier = Modifier.testTag("trip-empty")) }
                items(trips, key = { it.id }) { saved ->
                    OutlinedButton(onClick = { selectedId = saved.id; outingId = ""; stageId = null; analysisState.clear() }, modifier = Modifier.fillMaxWidth().testTag("trip-select-${saved.id}")) {
                        Column(Modifier.fillMaxWidth()) { Text(saved.name, fontWeight = FontWeight.Bold); Text("${saved.destination.name} · ${saved.startsOn} → ${saved.endsOn}") }
                    }
                }
                (error ?: analysisState.error.value)?.let { item { Text(it, color = TripError, modifier = Modifier.testTag("trip-error")); OutlinedButton(onClick = { refresh++ }) { Text("Riprova lettura") } } }
                if (trip != null && analysisTrip != null) {
                    if (trip.departure != null) item {
                        Text("Tappe e giorni", color = TripInk, fontWeight = FontWeight.Bold)
                        Button(onClick = { stageEditor = true }, enabled = !busy,
                            modifier = Modifier.testTag("trip-stages-edit")) { Text("Organizza tappe") }
                        if (trip.stages.isNotEmpty()) OutlinedButton(onClick = { stageId = null; outingId = ""; analysisState.clear() },
                            modifier = Modifier.testTag("trip-stage-all")) { Text("Intero viaggio") }
                    }
                    items(trip.stages, key = { "stage-${it.id}" }) { stage ->
                        val index = trip.stages.indexOf(stage)
                        val origin = if (index == 0) trip.departure else trip.stages[index - 1].destination
                        OutlinedButton(onClick = { stageId = stage.id; outingId = ""; analysisState.clear() },
                            modifier = Modifier.fillMaxWidth().testTag("trip-stage-${stage.id}")) {
                            Text("${index + 1}. ${origin?.name} → ${stage.destination.name} · ${stage.date}${if (stageId == stage.id) " · selezionata" else ""}")
                        }
                    }
                    item {
                        Text(trip.name, color = TripInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("trip-detail"))
                        Text("${trip.destination.name} · ${trip.startsOn} → ${trip.endsOn} · ${trip.radiusMeters / 1_000} km", color = TripMuted)
                        val departurePlace = analysisTrip.departure
                        if (departurePlace != null) {
                            Text("${departurePlace.name} → ${analysisTrip.destination.name}", color = TripInk)
                            if (stageId != null || trip.stages.isEmpty()) MapsDirectionsButton(departurePlace.center, analysisTrip.destination.center, openDirections)
                            else Text("Seleziona una tappa per aprire le sue indicazioni in Google Maps.", color = TripMuted)
                        } else MapsLinkButton(trip.destination.center, "trip-maps", openMap)
                        analysisTrip.route?.let { planned ->
                            Text(routeSummary(planned), color = TripMuted, modifier = Modifier.testTag("trip-route-saved"))
                            routeMap.Render(planned)
                            Text(planned.provenance.attribution, color = TripMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            val key = tripAnalysisKey(trip, outing, stageId)
                            val place = outing?.place ?: analysisTrip.destination
                            val route = if (outing != null) outing.route ?: explorer.point(place.name, place.center)
                                else analysisTrip.route?.geometry ?: explorer.point(place.name, place.center)
                            val period = outing?.let { AnalysisPeriod(it.date, it.date) } ?: AnalysisPeriod(analysisTrip.startsOn, analysisTrip.endsOn)
                            error = null
                            analysisState.start(UUID.randomUUID().toString(), key,
                                failureMessage = { "Ricerca non riuscita. Riprova: i risultati salvati sono conservati." }) {
                                explorer.explore(route, period, RouteAnalysisConfig(corridorRadiusMeters = trip.radiusMeters), refresh = true)
                            }
                        }, enabled = !busy, modifier = Modifier.testTag("trip-analyze")) { Text(if (busy) "Ricerca in corso…" else "Cerca animali per queste date") }
                        OutlinedButton(onClick = { memories = true }, modifier = Modifier.testTag("trip-diary")) { Text("Diario del viaggio") }
                        Button(onClick = { prefill = DiaryPrefill(tripId = trip.id, outingId = outing?.id) }, modifier = Modifier.testTag("trip-new-observation")) { Text("Aggiungi avvistamento") }
                        Text("Gruppi di interesse: ${trip.interests.joinToString { interestLabel(it) }.ifBlank { "tutti" }}. Se manca la classificazione del provider, scegli i risultati manualmente.", color = TripMuted)
                        Text(if (trip.route != null && outing == null)
                            "Copertura: corridoio attorno al tracciato scelto; non copre l’intero territorio. Le osservazioni storiche non garantiscono nuovi avvistamenti."
                            else "Copertura: campione attorno al luogo confermato, massimo 20 km; non copre l’intero territorio. Un luogo ricercabile può avere dati naturalistici insufficienti.", color = TripMuted, modifier = Modifier.testTag("trip-coverage"))
                        OutlinedButton(onClick = { tripEditor = trip.id }, enabled = !busy, modifier = Modifier.testTag("trip-edit")) { Text("Modifica viaggio") }
                        OutlinedButton(onClick = { deleting = "trip" }, enabled = !busy, modifier = Modifier.testTag("trip-delete")) { Text("Elimina viaggio") }
                        if (deleting == "trip") {
                            Text("Eliminare il viaggio e la sua pianificazione? Avvistamenti e bozze restano nel diario, senza collegamento.", color = TripError)
                            Button(onClick = { action { repository.delete(trip.id); selectedId = ""; outingId = ""; deleting = null; analysisState.clear(); refresh++ } }, enabled = !busy, modifier = Modifier.testTag("trip-delete-confirm")) { Text("Conferma eliminazione") }
                            TextButton(onClick = { deleting = null }) { Text("Annulla") }
                        }
                    }
                    item {
                        Text("Uscite", color = TripInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { outingEditor = "new" }, enabled = !busy, modifier = Modifier.testTag("outing-new")) { Text("Nuova uscita") }
                        OutlinedButton(onClick = { outingId = ""; stageId = null; analysisState.clear() }, modifier = Modifier.testTag("outing-destination")) { Text(if (trip.route == null) "Usa destinazione del viaggio" else "Usa percorso del viaggio") }
                    }
                    items(outings, key = { "outing-${it.id}" }) { saved ->
                        OutlinedButton(onClick = { outingId = saved.id; stageId = null; analysisState.clear() }, modifier = Modifier.fillMaxWidth().testTag("outing-select-${saved.id}")) {
                            Text("${saved.name} · ${saved.date}${if (saved.date !in trip.startsOn..trip.endsOn) " · fuori dalle nuove date del viaggio" else ""}")
                        }
                    }
                    item {
                        Text("Area selezionata: ${outing?.name ?: analysisTrip.destination.name}", color = TripInk, modifier = Modifier.testTag("trip-analysis-area"))
                        wishlist?.let { Button(onClick = { personalSuggestions = true }, modifier = Modifier.testTag("trip-suggestions")) { Text("Suggerimenti e desideri") } }
                        Text("Date selezionate: ${outing?.date ?: analysisTrip.startsOn} → ${outing?.date ?: analysisTrip.endsOn}", color = TripMuted)
                        if (outing != null) {
                            Text("Uscita del ${outing.date}; ${if (outing.route == null) "luogo manuale" else "percorso importato conservato"}", color = TripMuted)
                            OutlinedButton(onClick = { outingEditor = outing.id }, enabled = !busy, modifier = Modifier.testTag("outing-edit")) { Text("Modifica uscita") }
                            OutlinedButton(onClick = { deleting = "outing" }, enabled = !busy, modifier = Modifier.testTag("outing-delete")) { Text("Elimina uscita") }
                            if (deleting == "outing") {
                                Text("I ricordi resteranno collegati al viaggio, senza questa uscita.", color = TripMuted)
                                Button(onClick = { action { repository.deleteOuting(outing.id); outingId = ""; deleting = null; analysisState.clear(); refresh++ } }, enabled = !busy, modifier = Modifier.testTag("outing-delete-confirm")) { Text("Conferma eliminazione uscita") }
                                TextButton(onClick = { deleting = null }) { Text("Annulla") }
                            }
                        }
                        Button(onClick = { action {
                            repository.savePlace(SavedTripPlace("${trip.id}:${outing?.id ?: stageId?.let { "stage:$it" } ?: "destination"}", trip.id, outing?.place ?: analysisTrip.destination, now())); refresh++
                        } }, enabled = !busy, modifier = Modifier.testTag("trip-save-place")) { Text("Salva questo luogo") }
                        Button(onClick = { prefill = DiaryPrefill(tripId = trip.id, outingId = outing?.id, unidentified = true) }, modifier = Modifier.testTag("trip-new-draft")) { Text("Ricordo da identificare") }
                        if (outing != null) MapsLinkButton(outing.place.center, "outing-maps", openMap)
                        Text("I risultati salvati conservano la data e le fonti. Modificare il viaggio richiede un ricalcolo esplicito.", color = TripMuted)
                    }
                    if (places.isNotEmpty()) item { Text("Luoghi salvati", color = TripInk, fontWeight = FontWeight.Bold) }
                    items(places, key = { "place-${it.id}" }) { saved ->
                        Text("${saved.place.name} · ${saved.savedAt} · ${saved.place.provenance.attribution}", color = TripMuted, modifier = Modifier.testTag("trip-place-${saved.id}"))
                        OutlinedButton(onClick = { action { repository.deletePlace(saved.id); refresh++ } }, enabled = !busy) { Text("Rimuovi luogo salvato") }
                    }
                    when (val result = live) {
                        is ExplorationResult.Unavailable -> item { Text("Provider non disponibili. Viaggio, risultati salvati e diario restano consultabili.", color = TripError, modifier = Modifier.testTag("trip-unavailable")) }
                        is ExplorationResult.Ready -> if (liveKey == tripAnalysisKey(trip, outing, stageId)) {
                            val selectedTaxa = if (typicalOnly) typicalTaxa(result.taxa, result.analysis.samples) else result.taxa
                            item {
                                Text("Risultati per ${outing?.date ?: analysisTrip.startsOn} → ${outing?.date ?: analysisTrip.endsOn}", color = TripInk, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("trip-live-results"))
                                map.Render(result.analysis)
                                Text("© OpenStreetMap contributors · Percorso/campioni, senza posizioni delle osservazioni esterne.", color = TripMuted)
                                if (result.partial) Text("Risultati parziali: una fonte non ha risposto.", color = TripError)
                                if (result.origin == OccurrenceResultOrigin.STALE_CACHE) Text("Cache non aggiornata: rete non disponibile.", color = TripError)
                                if (result.taxa.isEmpty()) Text("Nessuna specie classificabile dai dati disponibili; non significa assenza di fauna.", color = TripMuted)
                                Text("Selezione non esaustiva da evidenze storiche; nessun avvistamento è garantito.", color = TripMuted)
                                FilterChip(selected = typicalOnly, onClick = { typicalOnly = true }, label = { Text("Animali tipici") }, modifier = Modifier.testTag("trip-view-typical"))
                                FilterChip(selected = !typicalOnly, onClick = { typicalOnly = false }, label = { Text("Tutte le specie documentate") }, modifier = Modifier.testTag("trip-view-all"))
                                Text(if (typicalOnly) "Curatela pilota Lombardia e dintorni; specie urbane comuni escluse da questa vista."
                                    else "Vista completa delle fonti, compresi dati insufficienti; consulta Evidenze e fonti per il livello.", color = TripMuted, style = MaterialTheme.typography.bodySmall)
                                if (selectedTaxa.isEmpty() && result.taxa.isNotEmpty()) Text("Nessun animale tipico selezionabile con questa curatela e le evidenze disponibili. Puoi consultare tutte le specie documentate.",
                                    color = TripMuted, modifier = Modifier.testTag("trip-selection-empty"))
                            }
                            items(selectedTaxa, key = { "live-${it.id}" }) { taxon ->
                                ExploredTaxonCard(taxon, catalogue, onSaw = { saw(taxon.id, taxon.scientificName) }, onSave = {
                                    action { repository.saveResult(resultSnapshot(taxon, trip, outing, result, now(), stageId)); refresh++ }
                                })
                            }
                        }
                        null -> Unit
                    }
                    if (savedResults.isNotEmpty()) item { Text("Risultati scelti · disponibili offline", color = TripInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(savedResults, key = { "saved-${it.id}" }) { saved ->
                        val savedOuting = outings.firstOrNull { it.id == saved.outingId }
                        val changed = saved.analysisKey != tripAnalysisKey(trip, savedOuting, saved.stageId)
                        Column(Modifier.fillMaxWidth().testTag("trip-saved-result-${saved.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            EssentialSpeciesDetails(saved.taxonId, saved.scientificName, catalogue, savedSeasonSummary(saved.season))
                            if (changed) Text("Date o area modificate: risultato da ricalcolare.", color = TripError, modifier = Modifier.testTag("trip-result-outdated-${saved.id}"))
                            if (saved.partial || saved.stale) Text("Snapshot ${if (saved.partial) "parziale" else ""} ${if (saved.stale) "da cache non aggiornata" else ""}", color = TripError)
                            SpeciesEvidenceDetails("trip-saved-details-${saved.id}") {
                                SpeciesGeneralSources(saved.scientificName, catalogue, saved.taxonId)
                                Text("${saved.level.name} · analisi salvata ${saved.savedAt}; non garantisce la presenza attuale.", color = TripMuted)
                                Text("${saved.area.name} · periodo analizzato ${saved.startsOn} → ${saved.endsOn}", color = TripMuted)
                                saved.explanation.forEach { Text(it, color = TripMuted, style = MaterialTheme.typography.bodySmall) }
                                saved.evidence.forEach { source -> Text("${source.explanation}\n${source.provenance.source} · ${source.provenance.retrievedAt} · licenza ${source.provenance.license} · ${source.provenance.attribution} · qualità ${source.provenance.quality}", color = TripMuted, style = MaterialTheme.typography.bodySmall) }
                            }
                            Button(onClick = { saw(saved.taxonId, saved.scientificName, saved.outingId) }, enabled = !busy, modifier = Modifier.testTag("trip-saved-saw-${saved.id}")) { Text("L’ho visto") }
                            OutlinedButton(onClick = { action { repository.deleteResult(saved.id); refresh++ } }, enabled = !busy, modifier = Modifier.testTag("trip-result-remove-${saved.id}")) { Text("Rimuovi risultato salvato") }
                        }
                    }
                }
                item {
                    onImportRoute?.let { TextButton(onClick = it, modifier = Modifier.testTag("trip-import-route")) { Text("Importa un percorso (facoltativo)") } }
                }
            }
        }
        if (tripEditor != null || outingEditor != null || stageEditor) error?.let { Text(it, color = TripError, modifier = Modifier.padding(16.dp).testTag("trip-error")) }
    }
}

internal val PlaceSaver = listSaver<TripPlace?, String>(save = { place -> if (place == null) emptyList() else with(place.provenance) {
    listOf(place.name, place.center.latitude.toString(), place.center.longitude.toString(), place.kind,
        source, recordId, query, retrievedAt.toString(), license, attribution, quality, version)
} }, restore = { values -> if (values.isEmpty()) null else TripPlace(values[0], GeoPoint(values[1].toDouble(), values[2].toDouble()), values[3],
    Provenance(values[4], values[5], values[6], Instant.parse(values[7]), values[8], values[9], values[10], values[11])) })

internal fun manualTripPlace(name: String, point: GeoPoint, now: Instant) = TripPlace(name.trim(), point, "COORDINATES",
    Provenance("user", "confirmed-location", "manual coordinates", now, "private", "Scelta personale", "user-confirmed", "v1"))

@Composable
internal fun ConfirmedPlaceForm(prefix: String, current: TripPlace?, geocoder: PlaceSearch, now: () -> Instant,
    onConfirmed: (TripPlace) -> Unit, title: String = "Destinazione o luogo") {
    var query by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf(current?.name.orEmpty()) }
    var latitude by rememberSaveable { mutableStateOf(current?.center?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf(current?.center?.longitude?.toString().orEmpty()) }
    var candidates by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf("NETWORK") }
    var manual by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OutlinedTextField(query, { query = it.take(160) }, Modifier.fillMaxWidth().testTag("$prefix-place-query"), label = { Text("Cerca $title") }, singleLine = true)
    Button(onClick = {
        searching = true; message = null; candidates = emptyList()
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { geocoder.search(query) } }.getOrElse { PlaceSearchResult.Unavailable() }
            candidates = when (result) { is PlaceSearchResult.Matches -> result.places; is PlaceSearchResult.Unavailable -> result.cachedPlaces }
            origin = when (result) { is PlaceSearchResult.Matches -> result.origin.name; is PlaceSearchResult.Unavailable -> "STALE_CACHE" }
            message = when { candidates.isNotEmpty() -> "Conferma il luogo corretto."; result is PlaceSearchResult.Unavailable -> "Ricerca non disponibile. Usa le coordinate o i luoghi salvati."; else -> "Nessun luogo trovato. Puoi usare le coordinate." }
            searching = false
        }
    }, enabled = !searching && query.trim().length >= 3, modifier = Modifier.testTag("$prefix-place-search")) { Text(if (searching) "Ricerca…" else "Cerca luogo") }
    Text("Luoghi: © OpenStreetMap contributors · Nominatim. Ricerca solo su richiesta.", color = TripMuted, style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, color = TripMuted) }
    candidates.forEachIndexed { index, candidate ->
        OutlinedButton(onClick = { onConfirmed(TripPlace(candidate.name.take(160), candidate.center, candidate.kind.name,
            Provenance("Nominatim", candidate.id, query.trim(), now(), "ODbL", "OpenStreetMap contributors", "geocoded-user-confirmed-$origin", "jsonv2"))) },
            modifier = Modifier.fillMaxWidth().testTag("$prefix-place-confirm-$index")) { Text("${candidate.name} · Conferma") }
    }
    current?.let { Text("Luogo confermato: ${it.name}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("$prefix-place-confirmed")) }
    TextButton(onClick = { manual = !manual }, modifier = Modifier.testTag("$prefix-manual-toggle")) { Text(if (manual) "Nascondi coordinate" else "Inserisci coordinate (facoltativo)") }
    if (manual) {
    OutlinedTextField(label, { label = it.take(160) }, Modifier.fillMaxWidth().testTag("$prefix-place-name"), label = { Text("Nome del luogo manuale") }, singleLine = true)
    OutlinedTextField(latitude, { latitude = it }, Modifier.fillMaxWidth().testTag("$prefix-latitude"), label = { Text("Latitudine WGS84") }, singleLine = true)
    OutlinedTextField(longitude, { longitude = it }, Modifier.fillMaxWidth().testTag("$prefix-longitude"), label = { Text("Longitudine WGS84") }, singleLine = true)
    Button(onClick = {
        runCatching { manualTripPlace(label, GeoPoint(latitude.replace(',', '.').toDouble(), longitude.replace(',', '.').toDouble()), now()) }
            .onSuccess { onConfirmed(it); message = null }.onFailure { message = "Controlla nome e coordinate WGS84 del luogo." }
    }, modifier = Modifier.testTag("$prefix-confirm-coordinates")) { Text("Conferma luogo manuale") }
    }
}

@Composable
private fun ColumnScope.TripForm(initial: Trip?, geocoder: PlaceSearch, now: () -> Instant, idFactory: () -> String, saving: Boolean,
    onCancel: () -> Unit, onSave: (Trip) -> Unit, routing: TripRouting, routeMap: TripRouteMapAdapter) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    val today = now().atZone(ZoneId.systemDefault()).toLocalDate()
    var startsOn by rememberSaveable(initial?.id) { mutableStateOf((initial?.startsOn ?: today).toString()) }
    var endsOn by rememberSaveable(initial?.id) { mutableStateOf((initial?.endsOn ?: today.plusDays(7)).toString()) }
    var radius by rememberSaveable(initial?.id) { mutableStateOf((initial?.radiusMeters?.div(1_000) ?: 1.0).toString()) }
    var options by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var interests by rememberSaveable(initial?.id) { mutableStateOf(initial?.interests?.map { it.name }.orEmpty()) }
    var place by rememberSaveable(initial?.id, stateSaver = PlaceSaver) { mutableStateOf(initial?.destination) }
    var departure by rememberSaveable(initial?.id, stateSaver = PlaceSaver) { mutableStateOf(initial?.departure) }
    var selectedRoute by remember(initial?.id) { mutableStateOf(initial?.route) }
    var choices by remember { mutableStateOf<List<TripRoute>>(emptyList()) }
    var planning by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val identity = rememberSaveable(initial?.id) { initial?.id ?: idFactory() }
    val hasStages = initial?.stages?.isNotEmpty() == true
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("trip-editor"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (initial == null) "Nuovo viaggio" else "Modifica viaggio", color = TripInk, fontWeight = FontWeight.Bold) }
        if (initial != null && initial.route == null) item {
            Text("Questo viaggio precedente non ha un tracciato. Aggiungi la partenza per scegliere il percorso.", color = TripMuted)
        }
        if (hasStages) item { Text("${departure?.name} → ${place?.name}. Per modificare località e percorsi usa Organizza tappe nel viaggio.", color = TripMuted) }
        if (!hasStages) item { ConfirmedPlaceForm("trip-departure", departure, geocoder, now, title = "partenza", onConfirmed = {
            if (departure?.center != it.center) { selectedRoute = null; choices = emptyList(); generation++; routeError = null }
            departure = it
        }) }
        if (!hasStages) item { ConfirmedPlaceForm("trip", place, geocoder, now, title = "destinazione", onConfirmed = {
            if (place?.center != it.center) { selectedRoute = null; choices = emptyList(); generation++; routeError = null }
            place = it
        }) }
        item { OutlinedTextField(startsOn, { startsOn = it }, Modifier.fillMaxWidth().testTag("trip-start"), label = { Text("Dal (AAAA-MM-GG)") }, singleLine = true) }
        item { OutlinedTextField(endsOn, { endsOn = it }, Modifier.fillMaxWidth().testTag("trip-end"), label = { Text("Al (AAAA-MM-GG, massimo un anno)") }, singleLine = true) }
        if (!hasStages) item {
            Text("Percorso in auto", color = TripInk, fontWeight = FontWeight.Bold)
            Button(onClick = {
                val start = departure ?: return@Button
                val end = place ?: return@Button
                val requestGeneration = generation
                planning = true; routeError = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { routing.plan(start, end) }
                        if (requestGeneration == generation) when (result) {
                            is TripRoutingResult.Routes -> {
                                choices = result.choices
                                if (choices.isEmpty()) routeError = "Nessun percorso disponibile. Riprova."
                            }
                            TripRoutingResult.NoRoute -> routeError = "Non trovo un percorso stradale tra questi luoghi. Controlla partenza e destinazione."
                            TripRoutingResult.Unavailable -> routeError = "Calcolo non disponibile. Controlla la connessione e riprova; il percorso già salvato è conservato."
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (requestGeneration == generation) routeError = "Non riesco a calcolare il percorso. Riprova." }
                    finally { planning = false }
                }
            }, enabled = departure != null && place != null && !planning && !saving,
                modifier = Modifier.testTag("trip-plan")) { Text(if (planning) "Calcolo percorso…" else "Calcola percorso") }
            Text("Il calcolo invia partenza e destinazione a OSRM. © OpenStreetMap contributors.", color = TripMuted, style = MaterialTheme.typography.bodySmall)
            routeError?.let { Text(it, color = TripError, modifier = Modifier.testTag("trip-plan-error")) }
        }
        items(choices, key = { "choice-${it.geometry.id}" }) { choice ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(routeSummary(choice), color = TripInk)
                routeMap.Render(choice)
                OutlinedButton(onClick = { selectedRoute = choice }, modifier = Modifier.testTag("trip-route-choice-${choices.indexOf(choice)}")) {
                    Text(if (selectedRoute?.geometry?.id == choice.geometry.id) "Percorso scelto" else "Scegli questo percorso")
                }
            }
        }
        selectedRoute?.let { selected -> item {
            Text("Tracciato scelto: ${routeSummary(selected)}. Sarà salvato con il viaggio.", color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("trip-route-selected"))
            if (choices.isEmpty()) routeMap.Render(selected)
        } }
        item { TextButton(onClick = { options = !options }, modifier = Modifier.testTag("trip-options-toggle")) { Text(if (options) "Nascondi opzioni" else "Personalizza il viaggio (facoltativo)") } }
        if (options) {
        item { OutlinedTextField(name, { name = it.take(160) }, Modifier.fillMaxWidth().testTag("trip-name"), label = { Text("Nome viaggio (facoltativo)") }, singleLine = true) }
        item { OutlinedTextField(radius, { radius = it }, Modifier.fillMaxWidth().testTag("trip-radius"), label = { Text("Raggio di spostamento (km, 0,05–20)") }, singleLine = true) }
        item { Text("Gruppi animali di interesse (nessuna scelta = tutti)", color = TripInk) }
        items(AnimalInterest.entries) { interest ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(interest.name in interests, { checked -> interests = if (checked) interests + interest.name else interests - interest.name }, modifier = Modifier.testTag("trip-interest-${interest.name}"))
                Text(interestLabel(interest), color = TripInk)
            }
        }
        }
        error?.let { item { Text(it, color = TripError, modifier = Modifier.testTag("trip-form-error")) } }
        item {
            Button(onClick = {
                runCatching {
                    if (initial == null || departure != null || initial.route != null) {
                        requireNotNull(departure) { "Conferma la partenza." }
                        requireNotNull(selectedRoute) { "Calcola e scegli il percorso prima di salvare." }
                    }
                    Trip(identity, name.trim().ifBlank { place?.name.orEmpty() }, requireNotNull(place), LocalDate.parse(startsOn), LocalDate.parse(endsOn),
                        radius.replace(',', '.').toDouble() * 1_000, interests.map(AnimalInterest::valueOf).toSet(), initial?.createdAt ?: now(), now(), departure, selectedRoute, initial?.stages.orEmpty())
                }.onSuccess { error = null; onSave(it) }.onFailure {
                    error = if (hasStages) "Le date devono includere tutte le tappe (massimo un anno). Controlla il raggio 0,05–20 km."
                        else "Conferma partenza e destinazione, calcola e scegli il percorso. Controlla date (massimo un anno) e raggio 0,05–20 km."
                }
            }, enabled = !saving && !planning, modifier = Modifier.testTag("trip-save")) { Text(if (saving) "Salvataggio…" else "Salva viaggio") }
            OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.testTag("trip-cancel")) { Text("Annulla") }
        }
    }
}

internal fun routeSummary(route: TripRoute): String = "%.1f km · circa %d min".format(
    java.util.Locale.ITALY, route.distanceMeters / 1_000, (route.durationSeconds / 60).toInt())

@Composable
private fun ColumnScope.OutingForm(trip: Trip, initial: Outing?, routes: RouteRepository, savedPlaces: List<SavedTripPlace>,
    geocoder: PlaceSearch, now: () -> Instant, idFactory: () -> String, saving: Boolean, onCancel: () -> Unit, onSave: (Outing) -> Unit,
    onImportRoute: (() -> Unit)? = null) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var date by rememberSaveable(initial?.id) { mutableStateOf((initial?.date ?: trip.startsOn).toString()) }
    var place by rememberSaveable(initial?.id, stateSaver = PlaceSaver) { mutableStateOf<TripPlace?>(initial?.place ?: trip.destination) }
    var routeId by rememberSaveable(initial?.id) { mutableStateOf(initial?.route?.id.orEmpty()) }
    var available by remember { mutableStateOf<List<Route>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val identity = rememberSaveable(initial?.id) { initial?.id ?: idFactory() }
    LaunchedEffect(routes) {
        runCatching { withContext(Dispatchers.IO) { routes.list() } }.onSuccess { available = it }
            .onFailure { error = "Percorsi locali non disponibili. Puoi creare un’uscita manuale." }
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("outing-editor"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("${if (initial == null) "Nuova" else "Modifica"} uscita · ${trip.name}", color = TripInk, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(name, { name = it.take(160) }, Modifier.fillMaxWidth().testTag("outing-name"), label = { Text("Nome uscita") }, singleLine = true) }
        item { OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth().testTag("outing-date"), label = { Text("Data uscita (AAAA-MM-GG)") }, singleLine = true) }
        item { ConfirmedPlaceForm("outing", place, geocoder, now, onConfirmed = { place = it; routeId = "" }) }
        items(savedPlaces, key = { "place-${it.id}" }) { saved ->
            OutlinedButton(onClick = { place = saved.place; routeId = "" }, modifier = Modifier.testTag("outing-place-${saved.id}")) { Text("Usa ${saved.place.name}") }
        }
        item {
            Text("Oppure scegli un percorso importato", color = TripMuted)
            onImportRoute?.let { TextButton(onClick = it, modifier = Modifier.testTag("outing-import-route")) { Text("Importa un percorso") } }
        }
        items(available, key = { it.id }) { route ->
            OutlinedButton(onClick = { routeId = route.id; place = manualTripPlace(route.name.take(160), route.points.first(), now()) }, modifier = Modifier.testTag("outing-route-${route.id}")) { Text(route.name) }
        }
        item {
            Text("${if (routeId.isBlank()) "Uscita manuale" else "Percorso selezionato: $routeId"}", color = TripMuted)
            if (routeId.isNotBlank()) OutlinedButton(onClick = { routeId = "" }) { Text("Usa solo il luogo") }
        }
        error?.let { item { Text(it, color = TripError, modifier = Modifier.testTag("outing-form-error")) } }
        item {
            Button(onClick = {
                runCatching {
                    val parsedDate = LocalDate.parse(date)
                    require(parsedDate in trip.startsOn..trip.endsOn) { "Scegli una data entro il viaggio." }
                    val route = if (routeId.isBlank()) null else available.firstOrNull { it.id == routeId }
                        ?: initial?.route?.takeIf { it.id == routeId } ?: error("Percorso non disponibile.")
                    Outing(identity, trip.id, name.trim(), parsedDate, requireNotNull(place), route)
                }.onSuccess { error = null; onSave(it) }.onFailure { error = "Controlla nome, luogo confermato e una data compresa nel viaggio." }
            }, enabled = !saving, modifier = Modifier.testTag("outing-save")) { Text(if (saving) "Salvataggio…" else "Salva uscita") }
            OutlinedButton(onClick = onCancel, enabled = !saving) { Text("Annulla") }
        }
    }
}

private fun interestLabel(interest: AnimalInterest): String = when (interest) {
    AnimalInterest.BIRDS -> "Uccelli"
    AnimalInterest.MAMMALS -> "Mammiferi"
    AnimalInterest.REPTILES -> "Rettili"
    AnimalInterest.AMPHIBIANS -> "Anfibi"
    AnimalInterest.FISH -> "Pesci"
    AnimalInterest.INVERTEBRATES -> "Invertebrati"
}
