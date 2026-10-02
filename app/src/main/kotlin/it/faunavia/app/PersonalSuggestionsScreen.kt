package it.faunavia.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.plausibility.ObservabilityGuidance
import it.faunavia.taxonomy.TaxonomySearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun SuggestionView.label() = when (this) {
    SuggestionView.TYPICAL -> "Tipici del luogo"
    SuggestionView.EASIER -> "Più facili da osservare"
    SuggestionView.NEVER_OBSERVED -> "Mai osservati da me"
    SuggestionView.WISHLIST -> "Vorrei vederlo"
}

@Composable
internal fun PersonalSuggestionsScreen(wishlist: WishlistRepository, catalogue: CatalogueRepository,
    diary: DiaryRepository, taxonomy: TaxonomySearch, candidates: List<SuggestionCandidate> = emptyList(),
    areaPoints: List<GeoPoint> = emptyList(), contextDescription: String = "Lista personale · disponibile offline",
    initialView: SuggestionView = SuggestionView.TYPICAL, onBack: () -> Unit,
    onSaw: ((String, String) -> Unit)? = null, externalError: String? = null) {
    var viewName by rememberSaveable { mutableStateOf(initialView.name) }
    var refresh by remember { mutableIntStateOf(0) }
    var wishes by remember { mutableStateOf<List<Taxon>>(emptyList()) }
    var observed by remember { mutableStateOf<List<Taxon>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val engine = remember { PersonalSuggestionEngine() }
    val view = SuggestionView.valueOf(viewName)
    BackHandler(onBack = onBack)
    LaunchedEffect(refresh) {
        loading = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                wishlist.list().map { requireNotNull(catalogue.taxon(it.taxonId)) } to
                    diary.list().map(Observation::taxonId).distinct().map { requireNotNull(catalogue.taxon(it)) }
            }
            wishes = loaded.first; observed = loaded.second; error = null
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { error = "Non riesco a leggere desideri e diario. Riprova." }
        finally { loading = false }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { withContext(Dispatchers.IO) { block() }; refresh++
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = "Operazione non riuscita. Riprova; puoi scegliere il taxon nel Catalogo e aggiungerlo ai desideri." }
            finally { busy = false }
        }
    }
    val visible = engine.select(candidates, areaPoints, view, observed, wishes)
    LazyColumn(Modifier.fillMaxSize().testTag("suggestion-list"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.testTag("suggestion-back")) { Text("Torna") }
            Text("Suggerimenti e desideri", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(contextDescription, style = MaterialTheme.typography.bodySmall)
            Text("Selezione non esaustiva. Le fonti descrivono dati storici; nessun avvistamento è garantito.")
            Text("La selezione curata copre l'area pilota lombarda e dintorni; fuori copertura può essere vuota. Non misura la probabilità di un incontro.", style = MaterialTheme.typography.bodySmall)
            SuggestionView.entries.forEach { option ->
                FilterChip(selected = view == option, onClick = { viewName = option.name }, label = { Text(option.label()) },
                    modifier = Modifier.testTag("suggestion-view-${option.name}"))
            }
            if (view == SuggestionView.TYPICAL) Text("Selezione delle specie caratteristiche; specie urbane comuni escluse solo da questa vista.", style = MaterialTheme.typography.bodySmall)
            if (view == SuggestionView.EASIER) Text("Facilità di osservazione non disponibile: mancano stime confrontabili. Ordine alfabetico; habitat e stagionalità non sono una probabilità.",
                modifier = Modifier.testTag("suggestion-ease-unavailable"))
            if (loading) Text("Caricamento dei dati personali…", modifier = Modifier.testTag("suggestion-loading"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("suggestion-error"));
                OutlinedButton(onClick = { refresh++ }) { Text("Riprova lettura") } }
            externalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("suggestion-selection-error")) }
        }
        if (!loading && error == null) {
            if (view == SuggestionView.WISHLIST) {
                if (wishes.isEmpty()) item { Text("Nessun desiderio salvato. Aggiungi una specie dal Catalogo o dai risultati.", modifier = Modifier.testTag("wishlist-empty")) }
                items(wishes.sortedWith(compareBy({ scientificIdentity(it.scientificName) }, Taxon::id)), key = { "wish-${it.id}" }) { taxon ->
                    val match = visible.firstOrNull { it.candidate.id == taxon.id || scientificIdentity(it.candidate.scientificName) == scientificIdentity(taxon.scientificName) }
                    Column(Modifier.fillMaxWidth().testTag("wishlist-${taxon.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EssentialSpeciesDetails(taxon.id, taxon.scientificName, catalogue,
                            match?.candidate?.savedSeason?.let(::savedSeasonSummary) ?: seasonSummary(match?.candidate?.season))
                        SpeciesEvidenceDetails("wishlist-details-${taxon.id}") {
                            SpeciesGeneralSources(taxon.scientificName, catalogue, taxon.id)
                            Text(if (match == null) "Pertinenza non valutabile: nessuna evidenza nei risultati disponibili di quest'area e periodo. Non è prova di assenza."
                                else "Nei risultati dell'area e periodo · ${match.candidate.level.name}. Non garantisce la presenza attuale.", modifier = Modifier.testTag("wishlist-relevance-${taxon.id}"))
                            match?.let { value -> value.candidate.provenance.forEach { SourceSummary(it) } }
                        }
                        OutlinedButton(onClick = { action { wishlist.remove(taxon.id) } }, enabled = !busy, modifier = Modifier.testTag("wishlist-remove-${taxon.id}")) { Text("Rimuovi desiderio") }
                        onSaw?.let { Button(onClick = { it(taxon.id, taxon.scientificName) }, enabled = !busy) { Text("L’ho visto") } }
                    }
                }
            } else {
                if (visible.isEmpty()) item { Text("Nessun suggerimento per questa vista. Puoi analizzare l'area e salvare risultati; il Diario accetta ogni taxon Animalia valido.", modifier = Modifier.testTag("suggestion-empty")) }
                items(visible, key = { it.candidate.id }) { suggestion ->
                    val candidate = suggestion.candidate
                    Column(Modifier.fillMaxWidth().testTag("suggestion-${candidate.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EssentialSpeciesDetails(candidate.id, candidate.scientificName, catalogue,
                            candidate.savedSeason?.let(::savedSeasonSummary) ?: seasonSummary(candidate.season))
                        SpeciesEvidenceDetails("suggestion-details-${candidate.id}") {
                            SpeciesGeneralSources(candidate.scientificName, catalogue, candidate.id, includeCurated = suggestion.profile == null)
                            Text("Evidenza: ${candidate.level.name} · classificazione originale invariata", modifier = Modifier.testTag("suggestion-level-${candidate.id}"))
                            Text(suggestion.reason, modifier = Modifier.testTag("suggestion-reason-${candidate.id}"))
                            suggestion.profile?.let { profile ->
                                Text("Habitat da approfondire: ${profile.habitats.joinToString()}; compatibilità locale non verificata.")
                                SourceSummary(profile.provenance)
                            }
                            when (val guidance = candidate.guidance) {
                                is ObservabilityGuidance.Documented -> {
                                    Text("Indicazioni documentate: ${guidance.habitatsToLookFor.joinToString()} · ${guidance.documentedPeriod ?: "periodo non disponibile"} · ${guidance.documentedTimeOfDay ?: "orario non disponibile"}")
                                    guidance.provenance.forEach { SourceSummary(it) }
                                }
                                ObservabilityGuidance.Unavailable -> Text("Indicazioni locali di osservabilità non disponibili.", style = MaterialTheme.typography.bodySmall)
                            }
                            candidate.provenance.forEach { SourceSummary(it) }
                        }
                        val wished = wishes.any { it.id == candidate.id || scientificIdentity(it.scientificName) == scientificIdentity(candidate.scientificName) }
                        OutlinedButton(onClick = { action {
                            if (wished) {
                                wishes.filter { it.id == candidate.id || scientificIdentity(it.scientificName) == scientificIdentity(candidate.scientificName) }.forEach { wishlist.remove(it.id) }
                            } else {
                                val selected = observationPrefill(candidate.id, candidate.scientificName, catalogue, taxonomy).taxon
                                requireNotNull(selected) { "Select an accepted taxon in the catalogue first." }
                                wishlist.add(selected.id)
                            }
                        } }, enabled = !busy, modifier = Modifier.testTag("suggestion-wish-${candidate.id}")) { Text(if (wished) "Rimuovi desiderio" else "Vorrei vederlo") }
                        onSaw?.let { Button(onClick = { it(candidate.id, candidate.scientificName) }, enabled = !busy,
                            modifier = Modifier.testTag("suggestion-saw-${candidate.id}")) { Text("L’ho visto") } }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SourceSummary(source: Provenance) {
    val context = LocalContext.current
    var failed by remember(source.source) { mutableStateOf(false) }
    Text("${source.attribution}\n${source.source}\nConsultata ${source.retrievedAt} · ${source.license} · qualità ${source.quality} · ${source.version}",
        style = MaterialTheme.typography.bodySmall)
    if (source.source.toUri().scheme == "https") TextButton(onClick = {
        failed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, source.source.toUri())) }.isFailure
    }) { Text("Apri fonte") }
    if (failed) Text("Non riesco ad aprire la fonte. L'indirizzo resta visibile.", color = MaterialTheme.colorScheme.error)
}
