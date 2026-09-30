package it.faunavia.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.EvidenceLevel
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import it.faunavia.domain.SpeciesProfile
import it.faunavia.domain.Taxon
import it.faunavia.exploration.ExplorationResult
import it.faunavia.exploration.ExplorationService
import it.faunavia.exploration.ExploredTaxon
import it.faunavia.exploration.PlaceCandidate
import it.faunavia.exploration.PlaceKind
import it.faunavia.exploration.PlaceOrigin
import it.faunavia.exploration.PlaceSearch
import it.faunavia.exploration.PlaceSearchResult
import it.faunavia.plausibility.AnalysisPeriod
import it.faunavia.plausibility.PlausibilityExplanationStep
import it.faunavia.plausibility.PlausibilityStepKind
import it.faunavia.plausibility.SeasonDataQuality
import it.faunavia.occurrence.OccurrenceResultOrigin
import it.faunavia.route.RouteAnalysisConfig
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExploreInk = Color(0xFF26382E)
private val ExploreMuted = Color(0xFF52675A)
private val ExploreError = Color(0xFF9B1C1C)
private val ExploreGreen = Color(0xFF1F5C3F)

private enum class EvidenceFilter(val label: String) {
    ALL("Tutti"), DOCUMENTED("Documentati"), PLAUSIBLE("Plausibili"), INSUFFICIENT("Dati insufficienti"),
}

@Composable
internal fun ExplorationScreen(
    explorer: ExplorationService,
    placeSearch: PlaceSearch,
    catalogue: CatalogueRepository,
    mapAdapter: ExplorationMapAdapter = MapLibreExplorationMapAdapter,
    routeRepository: RouteRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("1") }
    var periodStart by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var periodEnd by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var selectedKind by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf("") }
    var selectedLatitude by rememberSaveable { mutableStateOf("") }
    var selectedLongitude by rememberSaveable { mutableStateOf("") }
    var selectedFileUri by rememberSaveable { mutableStateOf("") }
    var selectedRouteId by rememberSaveable { mutableStateOf("") }
    var savedRoutes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }
    var placeMessage by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ExplorationResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(EvidenceFilter.ALL.name) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedFileUri = uri.toString()
            selectedName = uri.lastPathSegment ?: "Percorso importato"
            selectedKind = "file"
            selectedRouteId = ""
            submitted = false
            result = null
            error = null
        }
    }

    fun selectPoint(name: String, point: GeoPoint, kind: String) {
        selectedName = name
        selectedLatitude = point.latitude.toString()
        selectedLongitude = point.longitude.toString()
        selectedFileUri = ""
        selectedRouteId = ""
        selectedKind = kind
        submitted = false
        result = null
        error = null
        candidates = emptyList()
    }

    LaunchedEffect(routeRepository) {
        savedRoutes = routeRepository?.let { repository ->
            runCatching { withContext(Dispatchers.IO) { repository.list() } }.getOrDefault(emptyList())
        }.orEmpty()
    }

    LaunchedEffect(submitted, revision) {
        if (!submitted) return@LaunchedEffect
        val period = runCatching { AnalysisPeriod(LocalDate.parse(periodStart), LocalDate.parse(periodEnd)) }.getOrNull()
        val radiusMeters = radius.replace(',', '.').toDoubleOrNull()?.times(1_000.0)
        val config = runCatching { RouteAnalysisConfig(corridorRadiusMeters = radiusMeters ?: Double.NaN) }.getOrNull()
        if (period == null || config == null) {
            error = "Controlla le date ISO (AAAA-MM-GG), il periodo massimo di un anno e il raggio tra 0,05 e 20 km."
            submitted = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        result = null
        runCatching {
            withContext(Dispatchers.IO) {
                val route = when (selectedKind) {
                    "file" -> {
                        val document = readRouteDocument(context, selectedFileUri.toUri())
                        explorer.document(document.name, document.content)
                    }
                    "saved" -> routeRepository?.get(selectedRouteId)
                        ?: throw IllegalStateException("The selected local route is no longer available.")
                    else -> explorer.point(selectedName, GeoPoint(selectedLatitude.toDouble(), selectedLongitude.toDouble()))
                }
                explorer.explore(route, period, config)
            }
        }.onSuccess { result = it }
            .onFailure { failure -> error = explorationError(failure) }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F7F2)).testTag("screen-results")) {
        Box(Modifier.fillMaxWidth().height(104.dp).background(ExploreGreen).padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart) {
            Column {
                Text("Faunavia", color = Color.White, fontSize = 16.sp)
                Text("Esplora", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("screen-title-results"))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("explore-content"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Scegli un’area", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ExploreInk)
                    Text("Cerca un luogo, usa coordinate o importa GPX/GeoJSON. Il percorso non viene salvato come viaggio.", color = ExploreMuted)
                    OutlinedTextField(query, { query = it.take(160) }, Modifier.fillMaxWidth().testTag("place-query"),
                        label = { Text("Paese, regione o città") }, singleLine = true,
                        supportingText = { Text("Fino a 160 caratteri; la ricerca parte solo premendo Cerca luogo.") })
                    Button(onClick = {
                        searching = true
                        placeMessage = null
                        candidates = emptyList()
                        scope.launch {
                            val found = withContext(Dispatchers.IO) { placeSearch.search(query) }
                            when (found) {
                                is PlaceSearchResult.Matches -> {
                                    candidates = found.places
                                    placeMessage = when {
                                        found.places.isEmpty() -> "Nessun luogo trovato. Prova un altro nome o usa le coordinate."
                                        found.origin == PlaceOrigin.CACHE -> "Risultati dalla cache locale. Conferma il luogo prima di analizzare."
                                        else -> "Conferma il luogo corretto prima di analizzare."
                                    }
                                }
                                is PlaceSearchResult.Unavailable -> {
                                    candidates = found.cachedPlaces
                                    placeMessage = if (candidates.isEmpty()) "Ricerca luoghi non disponibile: puoi usare coordinate o un file."
                                        else "Ricerca non disponibile: risultati locali già consultati."
                                }
                            }
                            searching = false
                        }
                    }, enabled = !searching && query.trim().length >= 3,
                        modifier = Modifier.testTag("place-search")) { Text(if (searching) "Ricerca…" else "Cerca luogo") }
                    Text("Luoghi: © OpenStreetMap contributors · Nominatim. Ricerca solo su richiesta.",
                        color = ExploreMuted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("place-attribution"))
                    placeMessage?.let { Text(it, color = ExploreMuted, modifier = Modifier.testTag("place-message")) }
                    candidates.forEachIndexed { index, candidate ->
                        Button(onClick = { selectPoint(candidate.name, candidate.center, candidate.kind.name) },
                            modifier = Modifier.fillMaxWidth().testTag("place-confirm-$index"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExploreInk)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(candidate.name, fontWeight = FontWeight.Bold)
                                Text("${candidate.kind.name.lowercase()} · ${candidate.countryCode ?: "paese non indicato"} · Conferma",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Oppure coordinate WGS84", fontWeight = FontWeight.Bold, color = ExploreInk)
                    Row {
                        OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f).testTag("explore-latitude"),
                            label = { Text("Latitudine") }, singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f).testTag("explore-longitude"),
                            label = { Text("Longitudine") }, singleLine = true)
                    }
                    Button(onClick = {
                        val point = runCatching { GeoPoint(latitude.replace(',', '.').toDouble(), longitude.replace(',', '.').toDouble()) }.getOrNull()
                        if (point == null) error = "Coordinate WGS84 non valide."
                        else selectPoint("Coordinate ${point.latitude}, ${point.longitude}", point, "coordinates")
                    }, modifier = Modifier.testTag("explore-confirm-coordinates")) { Text("Conferma coordinate") }
                    Button(onClick = { launcher.launch(arrayOf("application/gpx+xml", "application/geo+json", "application/json", "text/xml")) },
                        modifier = Modifier.testTag("explore-import")) { Text("Scegli GPX o GeoJSON") }
                }
            }
            if (savedRoutes.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Percorsi locali", fontWeight = FontWeight.Bold, color = ExploreInk)
                    savedRoutes.forEachIndexed { index, route ->
                        Button(onClick = {
                            selectedRouteId = route.id
                            selectedName = route.name
                            selectedKind = "saved"
                            selectedFileUri = ""
                            submitted = false
                            result = null
                            error = null
                        }, modifier = Modifier.fillMaxWidth().testTag("explore-saved-$index"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExploreInk)) {
                            Text(route.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (selectedKind.isNotBlank()) item {
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFCCD8CE), RoundedCornerShape(12.dp)).padding(16.dp)
                    .testTag("explore-confirmed"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Area confermata", fontWeight = FontWeight.Bold, color = ExploreInk)
                    Text(selectedName, color = ExploreInk, modifier = Modifier.testTag("explore-selected-name"))
                    if (selectedKind in setOf(PlaceKind.COUNTRY.name, PlaceKind.REGION.name)) {
                        Text("Analisi campione attorno al centro selezionato; non copre l’intero territorio.",
                            color = ExploreMuted, modifier = Modifier.testTag("explore-sample-disclaimer"))
                    }
                    OutlinedTextField(radius, { radius = it }, Modifier.fillMaxWidth().testTag("explore-radius"),
                        label = { Text("Raggio area/corridoio (km, max 20)") }, singleLine = true)
                    OutlinedTextField(periodStart, { periodStart = it }, Modifier.fillMaxWidth().testTag("explore-period-start"),
                        label = { Text("Dal (AAAA-MM-GG)") }, singleLine = true)
                    OutlinedTextField(periodEnd, { periodEnd = it }, Modifier.fillMaxWidth().testTag("explore-period-end"),
                        label = { Text("Al (AAAA-MM-GG)") }, singleLine = true)
                    Button(onClick = { submitted = true; revision++ }, enabled = !loading,
                        modifier = Modifier.testTag("explore-analyze")) { Text("Analizza area") }
                }
            }
            error?.let { item { Text(it, color = ExploreError, modifier = Modifier.testTag("explore-error")) } }
            if (loading) item { Text("Analisi in corso…", color = ExploreMuted, modifier = Modifier.testTag("explore-loading")) }
            when (val current = result) {
                is ExplorationResult.Unavailable -> item {
                    Column(Modifier.testTag("explore-unavailable")) {
                        Text("Provider non disponibili. Il percorso locale e il diario restano utilizzabili.", color = ExploreError)
                        Button(onClick = { revision++ }) { Text("Riprova") }
                    }
                }
                is ExplorationResult.Ready -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Risultati · ${current.analysis.routeName}", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = ExploreInk, modifier = Modifier.testTag("explore-results-title"))
                            Text("${current.analysis.samples.size} campioni · ${current.analysis.corridorPortions.size} porzioni · EPSG:3035",
                                color = ExploreMuted)
                            if (current.partial) Text("Risultati parziali: una fonte non ha risposto.", color = ExploreError)
                            if (current.unclassifiedCount > 0) Text(
                                "${current.unclassifiedCount} osservazioni senza nome di specie utilizzabile non sono classificate.",
                                color = ExploreMuted, modifier = Modifier.testTag("explore-unclassified"))
                            if (current.origin == OccurrenceResultOrigin.STALE_CACHE)
                                Text("Dati dalla cache non aggiornati: rete non disponibile.", color = ExploreError)
                            Button(onClick = { showMap = !showMap }, modifier = Modifier.testTag("explore-toggle-map")) {
                                Text(if (showMap) "Nascondi mappa" else "Mostra mappa")
                            }
                            if (showMap) {
                                mapAdapter.Render(current.analysis)
                                val documented = current.taxa.count { it.assessment.level == EvidenceLevel.DOCUMENTED }
                                val plausible = current.taxa.count { it.assessment.level == EvidenceLevel.PLAUSIBLE }
                                val insufficient = current.taxa.count { it.assessment.level == EvidenceLevel.INSUFFICIENT }
                                Text("Evidenze nell’area: $documented documentati · $plausible plausibili · $insufficient con dati insufficienti.",
                                    color = ExploreInk, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("map-evidence-summary"))
                                Text("© OpenStreetMap contributors · Geometria: percorso e campioni; posizioni precise delle osservazioni non mostrate.",
                                    color = ExploreMuted, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("map-attribution"))
                                Text("Se le tile non si caricano, elenco e schede qui sotto restano disponibili.",
                                    color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (current.taxa.isEmpty()) item {
                        Text(if (current.unclassifiedCount > 0) "Nessuna osservazione classificabile a specie nell’area. Non significa assenza di fauna."
                            else "Nessuna osservazione nell’area dai provider consultati. Non significa assenza di fauna.",
                            color = ExploreMuted, modifier = Modifier.testTag("explore-empty"))
                    } else {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("explore-filters")) {
                                items(EvidenceFilter.entries) { option ->
                                    Button(onClick = { filter = option.name },
                                        modifier = Modifier.testTag("filter-${option.name.lowercase()}"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (filter == option.name) ExploreGreen else Color.White,
                                            contentColor = if (filter == option.name) Color.White else ExploreGreen,
                                        )) { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        val visible = current.taxa.filter { taxon ->
                            filter == EvidenceFilter.ALL.name || taxon.assessment.level.name == filter
                        }
                        if (visible.isEmpty()) item { Text("Nessun risultato per questo filtro.", color = ExploreMuted) }
                        items(visible, key = { it.id }) { taxon -> ExploredTaxonCard(taxon, catalogue) }
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ExploredTaxonCard(taxon: ExploredTaxon, catalogue: CatalogueRepository) {
    val context = LocalContext.current
    var localTaxon by remember(taxon.id) { mutableStateOf<Taxon?>(null) }
    var profile by remember(taxon.id) { mutableStateOf<SpeciesProfile?>(null) }
    LaunchedEffect(taxon.id) {
        withContext(Dispatchers.IO) {
            localTaxon = catalogue.taxon(taxon.id)
            profile = catalogue.profile(taxon.id)
        }
    }
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp))
        .border(1.dp, Color(0xFFCCD8CE), RoundedCornerShape(12.dp)).padding(16.dp)
        .testTag("explore-taxon-${taxon.id}"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(localTaxon?.commonName ?: taxon.scientificName, color = ExploreInk, fontWeight = FontWeight.Bold)
        if (localTaxon?.commonName != null) Text(taxon.scientificName, color = ExploreMuted)
        Text(when (taxon.assessment.level) {
            EvidenceLevel.DOCUMENTED -> "Documentato · osservazione storica utilizzabile, non presenza garantita oggi"
            EvidenceLevel.PLAUSIBLE -> "Plausibile · area e habitat compatibili"
            EvidenceLevel.INSUFFICIENT -> "Dati insufficienti · non è prova di assenza"
        }, color = ExploreGreen, modifier = Modifier.testTag("explore-level-${taxon.id}"))
        Text("Immagine non disponibile: nessuna fotografia con licenza verificata.", color = ExploreMuted,
            style = MaterialTheme.typography.bodySmall)
        Text("Riconoscimento: ${profile?.description?.takeIf(String::isNotBlank) ?: "descrizione documentata non disponibile"}",
            color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
        Text("Habitat: ${profile?.habitats?.takeIf(List<String>::isNotEmpty)?.joinToString() ?: "dato documentato non disponibile"}",
            color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
        profile?.let { stored ->
            Text("Scheda: ${stored.provenance.attribution} · licenza ${stored.provenance.license}",
                color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
        }
        val season = taxon.assessment.season
        val seasonSource = when (season.quality) {
            SeasonDataQuality.INSTITUTIONAL -> "fonte istituzionale"
            SeasonDataQuality.DERIVED_OCCURRENCES -> "ricavato dalle osservazioni; indicazione debole"
            SeasonDataQuality.UNAVAILABLE -> "dato non disponibile"
        }
        Text("Periodo: ${if (season.quality == SeasonDataQuality.UNAVAILABLE) "dati stagionali non disponibili" else "mesi ${season.activeMonths.sorted().joinToString()} · $seasonSource"}",
            color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
        taxon.assessment.explanation.forEach { step ->
            Text(explanationText(step), color = ExploreMuted,
                style = MaterialTheme.typography.bodySmall)
            step.provenance.take(2).forEach { source ->
                Text("Fonte: ${source.attribution} · licenza ${source.license} · qualità ${source.quality}",
                    color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        taxon.occurrences.take(3).forEach { occurrence ->
            Text("${occurrence.provider.name} · ${occurrence.observedOn ?: "data non disponibile"} · ${occurrence.provenance.attribution}",
                color = ExploreMuted, style = MaterialTheme.typography.bodySmall)
            Text("Licenza: ${occurrence.provenance.license} · qualità: ${occurrence.provenance.quality}",
                color = ExploreMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("explore-license-${occurrence.providerRecordId}"))
            if (occurrence.sourceUrl.toUri().scheme == "https") {
                Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, occurrence.sourceUrl.toUri())) },
                    modifier = Modifier.testTag("explore-source-${occurrence.providerRecordId}")) { Text("Apri fonte") }
            }
        }
    }
}

internal fun explanationText(step: PlausibilityExplanationStep): String = when (step.kind) {
    PlausibilityStepKind.DIRECT_OCCURRENCE -> if (step.conclusion == "usable-direct-occurrence")
        "Osservazione diretta recente e utilizzabile nell’area." else "Nessuna osservazione diretta recente e utilizzabile."
    PlausibilityStepKind.RANGE -> when (step.conclusion) {
        "range-supported" -> "Areale documentato compatibile con l’area."
        "range-unsupported" -> "Areale documentato non compatibile con l’area."
        else -> "Areale documentato non disponibile."
    }
    PlausibilityStepKind.HABITAT -> when (step.conclusion) {
        "habitat-supported" -> "Habitat documentato compatibile."
        "habitat-unsupported" -> "Habitat documentato non compatibile."
        else -> "Dati habitat sufficienti non disponibili."
    }
    PlausibilityStepKind.SEASON -> when {
        step.conclusion.contains("-supports-") -> "Il periodo scelto coincide con mesi documentati; non garantisce un avvistamento."
        step.conclusion.contains("-reduces-") -> "Il periodo scelto è fuori dai mesi documentati."
        step.conclusion.contains("-mixed-") -> "Il periodo scelto coincide solo in parte con i mesi documentati."
        else -> "Compatibilità stagionale non valutabile con i dati disponibili."
    }
    PlausibilityStepKind.NATURA2000_CONTEXT -> if (step.conclusion == "natura2000-positive-context")
        "Contesto Natura 2000 positivo documentato." else "Nessun contesto Natura 2000 positivo verificato; non è una prova negativa."
    PlausibilityStepKind.OBSERVABILITY -> if (step.conclusion == "observability-documented")
        "Indicazioni pratiche documentate disponibili." else "Indicazioni pratiche documentate non disponibili."
}

private fun explorationError(failure: Throwable): String = when (failure) {
    is it.faunavia.route.RouteImportException -> "Il percorso non è valido o è fuori dall’area europea supportata: ${failure.code.name}."
    is SecurityException -> "Impossibile leggere il file. Selezionalo di nuovo."
    else -> "Analisi non riuscita. Controlla l’area e riprova."
}
