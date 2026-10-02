package it.faunavia.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.faunavia.domain.*
import it.faunavia.exploration.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private data class StageDraft(val id: String, val destination: TripPlace?, val date: String)

// Only small editor fields enter the Android bundle; complete geometries remain outside it.
private val StageDraftSaver = listSaver<List<StageDraft>, String>(save = { drafts -> drafts.map { draft ->
    val place = draft.destination
    JSONArray(listOf(draft.id, draft.date) + if (place == null) emptyList() else with(place.provenance) {
        listOf(place.name, place.center.latitude.toString(), place.center.longitude.toString(), place.kind,
            source, recordId, query, retrievedAt.toString(), license, attribution, quality, version)
    }).toString()
} }, restore = { values -> values.map { value ->
    val fields = JSONArray(value)
    StageDraft(fields.getString(0), if (fields.length() == 2) null else TripPlace(fields.getString(2),
        GeoPoint(fields.getString(3).toDouble(), fields.getString(4).toDouble()), fields.getString(5),
        Provenance(fields.getString(6), fields.getString(7), fields.getString(8), Instant.parse(fields.getString(9)),
            fields.getString(10), fields.getString(11), fields.getString(12), fields.getString(13))), fields.getString(1))
} })

@Composable
internal fun ColumnScope.TripStagesForm(trip: Trip, geocoder: PlaceSearch, routing: TripRouting,
    routeMap: TripRouteMapAdapter, now: () -> Instant, idFactory: () -> String, saving: Boolean,
    onCancel: () -> Unit, onSave: (Trip) -> Unit) {
    var departure by rememberSaveable(trip.id, stateSaver = PlaceSaver) { mutableStateOf(trip.departure) }
    val initial = remember(trip.id) { trip.stages.ifEmpty {
        trip.route?.let { listOf(TripStage("${trip.id}:first", trip.destination, trip.startsOn, it)) }.orEmpty()
    } }
    var drafts by rememberSaveable(trip.id, stateSaver = StageDraftSaver) {
        mutableStateOf(initial.map { StageDraft(it.id, it.destination, it.date.toString()) }
            .ifEmpty { listOf(StageDraft("${trip.id}:first", trip.destination, trip.startsOn.toString())) })
    }
    var routes by remember(trip.id) { mutableStateOf(initial.filter { stage ->
        val index = drafts.indexOfFirst { it.id == stage.id }
        val oldIndex = initial.indexOf(stage)
        index >= 0 && drafts[index].destination?.center == stage.destination.center &&
            (if (index == 0) departure else drafts[index - 1].destination)?.center ==
            (if (oldIndex == 0) trip.departure else initial[oldIndex - 1].destination)?.center
    }.associate { it.id to it.route }) }
    var choices by remember { mutableStateOf<Map<String, List<TripRoute>>>(emptyMap()) }
    var planning by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun updateDestinations(changed: List<StageDraft>) {
        val valid = changed.filterIndexed { index, draft ->
            val oldIndex = drafts.indexOfFirst { it.id == draft.id }
            oldIndex >= 0 && draft.destination?.center == drafts[oldIndex].destination?.center &&
                (if (index == 0) departure else changed[index - 1].destination)?.center ==
                (if (oldIndex == 0) departure else drafts[oldIndex - 1].destination)?.center
        }.map { it.id }.toSet()
        drafts = changed; routes = routes.filterKeys { it in valid }; choices = emptyMap(); generation++; message = null
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("stage-editor"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Tappe e giorni · ${trip.name}", fontWeight = FontWeight.Bold)
            Text("Ogni tappa parte dall’arrivo della precedente. L’ultima diventa la destinazione del viaggio.")
            Text("Date del viaggio: ${trip.startsOn} → ${trip.endsOn}. Puoi inserire più tappe nello stesso giorno o lasciare giorni di sosta.")
            ConfirmedPlaceForm("stage-departure", departure, geocoder, now, title = "partenza del viaggio", onConfirmed = {
                if (it.center != departure?.center) {
                    routes = routes - drafts.first().id; choices = emptyMap(); generation++; message = null
                }
                departure = it
            })
        }
        itemsIndexed(drafts, key = { _, draft -> draft.id }) { index, draft ->
            val origin = if (index == 0) departure else drafts[index - 1].destination
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tappa ${index + 1} · da ${origin?.name ?: "conferma la tappa precedente"}", fontWeight = FontWeight.Bold)
                OutlinedTextField(draft.date, { value -> drafts = drafts.map { if (it.id == draft.id) it.copy(date = value) else it } },
                    modifier = Modifier.fillMaxWidth().testTag("stage-date-$index"), label = { Text("Giorno (AAAA-MM-GG)") }, singleLine = true)
                ConfirmedPlaceForm("stage-$index", draft.destination, geocoder, now, title = "arrivo della tappa", onConfirmed = { place ->
                    updateDestinations(drafts.map { if (it.id == draft.id) it.copy(destination = place) else it })
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (index > 0) OutlinedButton(onClick = {
                        val changed = drafts.toMutableList(); changed[index] = drafts[index - 1]; changed[index - 1] = draft
                        updateDestinations(changed)
                    }, enabled = !saving, modifier = Modifier.testTag("stage-up-$index")) { Text("Sposta su") }
                    if (drafts.size > 1) OutlinedButton(onClick = { updateDestinations(drafts.filter { it.id != draft.id }) },
                        enabled = !saving, modifier = Modifier.testTag("stage-remove-$index")) { Text("Rimuovi") }
                }
                Button(onClick = {
                    val start = origin ?: return@Button
                    val end = draft.destination ?: return@Button
                    val requestGeneration = generation
                    planning = draft.id; message = null
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { routing.plan(start, end) }
                            if (requestGeneration == generation) when (result) {
                                is TripRoutingResult.Routes -> {
                                    choices = choices + (draft.id to result.choices)
                                    if (result.choices.isEmpty()) message = "Nessun percorso disponibile per la tappa ${index + 1}. Riprova."
                                }
                                TripRoutingResult.NoRoute -> message = "Nessuna strada trovata per la tappa ${index + 1}. Controlla le località."
                                TripRoutingResult.Unavailable -> message = "Calcolo non disponibile per la tappa ${index + 1}. Riprova; i percorsi già scelti sono conservati."
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (requestGeneration == generation) message = "Calcolo non riuscito. Riprova." }
                        finally { planning = null }
                    }
                }, enabled = origin != null && draft.destination != null && planning == null && !saving,
                    modifier = Modifier.testTag("stage-plan-$index")) { Text(if (planning == draft.id) "Calcolo…" else "Calcola percorso della tappa") }
                choices[draft.id].orEmpty().forEachIndexed { choiceIndex, choice ->
                    Text(routeSummary(choice)); routeMap.Render(choice)
                    OutlinedButton(onClick = { routes = routes + (draft.id to choice); choices = choices - draft.id },
                        modifier = Modifier.testTag("stage-choice-$index-$choiceIndex")) { Text("Scegli questo percorso") }
                }
                routes[draft.id]?.let { selected ->
                    Text("Percorso scelto: ${routeSummary(selected)}", modifier = Modifier.testTag("stage-selected-$index"))
                    if (choices[draft.id].isNullOrEmpty()) routeMap.Render(selected)
                    Text(selected.provenance.attribution, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
        item {
            OutlinedButton(onClick = {
                val previous = runCatching { LocalDate.parse(drafts.last().date) }.getOrDefault(trip.startsOn)
                drafts = drafts + StageDraft(idFactory(), null, minOf(previous.plusDays(1), trip.endsOn).toString())
            }, enabled = !saving, modifier = Modifier.testTag("stage-add")) { Text("Aggiungi tappa") }
            Text("Ogni calcolo in auto invia a OSRM solo partenza e arrivo del tratto. © OpenStreetMap contributors.", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("stage-error")) }
            Button(onClick = {
                runCatching {
                    val stages = drafts.map { draft ->
                        val date = runCatching { LocalDate.parse(draft.date) }.getOrElse { error("Inserisci le date nel formato AAAA-MM-GG.") }
                        require(date in trip.startsOn..trip.endsOn) { "Le date delle tappe devono rientrare nel viaggio." }
                        TripStage(draft.id, requireNotNull(draft.destination) { "Conferma tutte le località." }, date,
                            requireNotNull(routes[draft.id]) { "Calcola e scegli il percorso di ogni tappa." })
                    }
                    require(stages.zipWithNext().all { (a, b) -> !b.date.isBefore(a.date) }) { "Le date devono seguire l’ordine delle tappe." }
                    trip.copy(departure = requireNotNull(departure), stages = emptyList()).withStages(stages, now())
                }.onSuccess(onSave).onFailure { message = it.message ?: "Controlla le tappe e riprova." }
            }, enabled = !saving && planning == null, modifier = Modifier.testTag("stage-save")) { Text(if (saving) "Salvataggio…" else "Salva tappe") }
            OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.testTag("stage-cancel")) { Text("Annulla") }
        }
    }
    }
}
