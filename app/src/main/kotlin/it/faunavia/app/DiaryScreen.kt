package it.faunavia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.DiaryRepository
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.MAX_OBSERVATION_NOTES_LENGTH
import it.faunavia.domain.MAX_OBSERVATION_QUANTITY
import it.faunavia.domain.Observation
import it.faunavia.domain.ObservationDraft
import it.faunavia.domain.Taxon
import it.faunavia.domain.Trip
import it.faunavia.domain.Outing
import it.faunavia.domain.TripRepository
import it.faunavia.domain.UnidentifiedRepository
import it.faunavia.domain.UnidentifiedDraft
import it.faunavia.domain.UnidentifiedInput
import it.faunavia.domain.PersonalDiaryViews
import it.faunavia.taxonomy.MINIMUM_TAXON_QUERY_LENGTH
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.normalizeQuery
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DiaryInk = Color(0xFF26382E)
private val DiaryMuted = Color(0xFF52675A)
private val DiaryError = Color(0xFF9B1C1C)
private val DiaryDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val DiaryTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class DiaryListEntry(
    val observation: Observation,
    val taxon: Taxon?,
    val photoCount: Int = 0,
)

internal data class DiaryPrefill(val taxon: Taxon? = null, val query: String = "",
    val tripId: String? = null, val outingId: String? = null, val unidentified: Boolean = false)

/**
 * UI state stays in memory only. A partially typed taxon query never becomes a diary record.
 * Saving and listing use local Room repositories; catalogue search falls back to selected taxa.
 */
private data class DiaryEditor(
    val id: String?,
    val selectedTaxon: Taxon?,
    val taxonQuery: String,
    val date: String,
    val time: String,
    val zoneId: ZoneId,
    val quantity: String,
    val notes: String,
    val latitude: String,
    val longitude: String,
    val tripId: String? = null,
    val outingId: String? = null,
    val unidentified: Boolean = false,
) {
    companion object {
        fun new(now: Instant, zoneId: ZoneId): DiaryEditor {
            val local = now.atZone(zoneId)
            return DiaryEditor(
                id = null,
                selectedTaxon = null,
                taxonQuery = "",
                date = DiaryDateFormatter.format(local),
                time = DiaryTimeFormatter.format(local),
                zoneId = zoneId,
                quantity = "1",
                notes = "",
                latitude = "",
                longitude = "",
            )
        }

        fun from(entry: DiaryListEntry): DiaryEditor {
            val local = entry.observation.observedAt.atZone(entry.observation.zoneId)
            return DiaryEditor(
                id = entry.observation.id,
                selectedTaxon = entry.taxon,
                taxonQuery = entry.taxon?.scientificName.orEmpty(),
                date = DiaryDateFormatter.format(local),
                time = DiaryTimeFormatter.format(local),
                zoneId = entry.observation.zoneId,
                quantity = entry.observation.quantity.toString(),
                notes = entry.observation.notes,
                latitude = entry.observation.location?.latitude?.toString().orEmpty(),
                longitude = entry.observation.location?.longitude?.toString().orEmpty(),
                tripId = entry.observation.tripId,
                outingId = entry.observation.outingId,
            )
        }

        fun from(draft: UnidentifiedDraft): DiaryEditor = draft.input.let {
            new(it.observedAt, it.zoneId).copy(id = it.id, notes = it.notes, quantity = it.quantity.toString(),
                latitude = it.location?.latitude?.toString().orEmpty(), longitude = it.location?.longitude?.toString().orEmpty(),
                tripId = it.tripId, outingId = it.outingId, unidentified = true)
        }
    }

    fun draft(id: String): ObservationDraft {
        val taxon = requireNotNull(selectedTaxon) { "Scegli una specie prima di salvare." }
        val localDate = runCatching { LocalDate.parse(date.trim(), DiaryDateFormatter) }
            .getOrElse { throw IllegalArgumentException("Inserisci una data valida nel formato AAAA-MM-GG.") }
        val localTime = runCatching { LocalTime.parse(time.trim(), DiaryTimeFormatter) }
            .getOrElse { throw IllegalArgumentException("Inserisci un'ora valida nel formato HH:MM.") }
        val parsedQuantity = quantity.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Inserisci una quantità intera.")
        require(parsedQuantity in 1..MAX_OBSERVATION_QUANTITY) {
            "La quantità deve essere tra 1 e $MAX_OBSERVATION_QUANTITY."
        }
        val location = optionalLocation(latitude, longitude)
        return ObservationDraft(
            id = id,
            taxonId = taxon.id,
            observedAt = localDate.atTime(localTime).atZone(zoneId).toInstant(),
            zoneId = zoneId,
            location = location,
            notes = notes,
            quantity = parsedQuantity,
            tripId = tripId,
            outingId = outingId,
        )
    }

    fun unidentifiedInput(id: String): UnidentifiedInput {
        val parsed = draftForUnidentified(id)
        return UnidentifiedInput(id, parsed.first, zoneId, optionalLocation(latitude, longitude), notes,
            parsed.second, tripId, outingId)
    }

    private fun draftForUnidentified(id: String): Pair<Instant, Int> {
        require(id.isNotBlank())
        val dateValue = runCatching { LocalDate.parse(date.trim(), DiaryDateFormatter) }
            .getOrElse { throw IllegalArgumentException("Inserisci una data valida nel formato AAAA-MM-GG.") }
        val timeValue = runCatching { LocalTime.parse(time.trim(), DiaryTimeFormatter) }
            .getOrElse { throw IllegalArgumentException("Inserisci un'ora valida nel formato HH:MM.") }
        val count = quantity.toIntOrNull() ?: throw IllegalArgumentException("Inserisci una quantità intera.")
        require(count in 1..MAX_OBSERVATION_QUANTITY) { "Controlla la quantità." }
        return dateValue.atTime(timeValue).atZone(zoneId).toInstant() to count
    }
}

private fun optionalLocation(latitude: String, longitude: String): GeoPoint? {
    val latitudeValue = latitude.trim()
    val longitudeValue = longitude.trim()
    if (latitudeValue.isEmpty() && longitudeValue.isEmpty()) return null
    require(latitudeValue.isNotEmpty() && longitudeValue.isNotEmpty()) {
        "Per la posizione inserisci sia latitudine sia longitudine."
    }
    val parsedLatitude = latitudeValue.toDoubleOrNull()
        ?: throw IllegalArgumentException("La latitudine non è valida.")
    val parsedLongitude = longitudeValue.toDoubleOrNull()
        ?: throw IllegalArgumentException("La longitudine non è valida.")
    return GeoPoint(parsedLatitude, parsedLongitude)
}

@Composable
internal fun DiaryScreen(
    diary: DiaryRepository,
    catalogue: CatalogueRepository,
    taxonomySearch: TaxonomySearch,
    deviceZone: ZoneId = ZoneId.systemDefault(),
    now: () -> Instant = { Instant.now() },
    idFactory: () -> String = { UUID.randomUUID().toString() },
    unidentifiedRepository: UnidentifiedRepository? = null,
    tripRepository: TripRepository? = null,
    prefill: DiaryPrefill? = null,
    fixedTripId: String? = null,
    fixedOutingId: String? = null,
    onExitEditor: () -> Unit = {},
    personalMap: PersonalMapAdapter = MapLibrePersonalMapAdapter,
    photoManager: MemoryPhotos? = null,
) {
    val context = LocalContext.current
    val photos = remember(context, diary, unidentifiedRepository, photoManager) {
        photoManager ?: MemoryPhotos((context.applicationContext as FaunaviaApplication).privatePhotos, diary, unidentifiedRepository)
    }
    var photoMemoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftPhotoCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refresh by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<DiaryListEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
    var fileCleanupWarning by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember(prefill) { mutableStateOf(prefill?.let {
        DiaryEditor.new(now(), deviceZone).copy(selectedTaxon = it.taxon, taxonQuery = it.taxon?.scientificName ?: it.query,
            tripId = it.tripId, outingId = it.outingId, unidentified = it.unidentified)
    }) }
    var drafts by remember { mutableStateOf<List<UnidentifiedDraft>>(emptyList()) }
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var outings by remember { mutableStateOf<List<Outing>>(emptyList()) }
    var selectedTrip by remember { mutableStateOf(fixedTripId) }
    var selectedOuting by remember { mutableStateOf(fixedOutingId) }
    var speciesFilter by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("list") }
    var deletingDraft by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = true
        listError = null
        runCatching {
            withContext(Dispatchers.IO) {
                drafts = unidentifiedRepository?.list().orEmpty()
                draftPhotoCounts = drafts.associate { it.input.id to unidentifiedRepository?.photos(it.input.id).orEmpty().size }
                trips = tripRepository?.list().orEmpty()
                outings = trips.flatMap { tripRepository?.outings(it.id).orEmpty() }
                diary.list().map { observation -> DiaryListEntry(observation, catalogue.taxon(observation.taxonId), diary.photos(observation.id).size) }
            }
        }.onSuccess { entries = it }
            .onFailure { listError = "Non riesco a leggere il diario locale. Riprova." }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen-diary"),
    ) {
        DiaryHeader()
        val activeEditor = editor
        if (activeEditor == null) {
            val scoped = entries.filter { entry ->
                (selectedTrip == null || entry.observation.tripId == selectedTrip) &&
                    (selectedOuting == null || entry.observation.outingId == selectedOuting) &&
                    (speciesFilter.isBlank() || (entry.taxon?.scientificName ?: entry.observation.taxonId).contains(speciesFilter, true) ||
                        entry.taxon?.commonName?.contains(speciesFilter, true) == true) &&
                    (dateFilter.isBlank() || entry.observation.localDate.toString() == dateFilter)
            }
            DiaryList(
                entries = scoped,
                loading = loading,
                error = listError ?: fileCleanupWarning,
                deletingId = deletingId,
                onCreate = { editor = DiaryEditor.new(now(), deviceZone).copy(tripId = selectedTrip, outingId = selectedOuting) },
                onEdit = { entry -> editor = DiaryEditor.from(entry) },
                onPhotos = { photoMemoryId = it },
                onStartDelete = { deletingId = it },
                onCancelDelete = { deletingId = null },
                onConfirmDelete = { id ->
                    scope.launch {
                        runCatching { photos.deleteMemory(id, identified = true) }
                            .onSuccess {
                                deletingId = null
                                refresh++
                                fileCleanupWarning = if (!it) "Avvistamento eliminato. Alcuni file privati attendono la pulizia; riapri le foto più tardi." else null
                            }
                            .onFailure { listError = "Non riesco a eliminare questo avvistamento. Riprova." }
                    }
                },
                extra = {
                    if (tripRepository != null) item {
                        Text("Filtri e viste", color = DiaryInk, fontWeight = FontWeight.Bold)
                        if (fixedTripId == null) LazyRow(modifier = Modifier.testTag("diary-trip-filters"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { OutlinedButton(onClick = { selectedTrip = null; selectedOuting = null }, modifier = Modifier.testTag("diary-filter-all")) { Text("Tutti") } }
                            items(trips, key = { it.id }) { trip ->
                                OutlinedButton(onClick = { selectedTrip = trip.id; selectedOuting = null }, modifier = Modifier.testTag("diary-filter-trip-${trip.id}")) { Text(trip.name) }
                            }
                        }
                        if (selectedTrip != null && fixedOutingId == null) LazyRow(modifier = Modifier.testTag("diary-outing-filters"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { OutlinedButton(onClick = { selectedOuting = null }) { Text("Tutte le uscite") } }
                            items(outings.filter { it.tripId == selectedTrip }, key = { it.id }) { outing ->
                                OutlinedButton(onClick = { selectedOuting = outing.id }, modifier = Modifier.testTag("diary-filter-outing-${outing.id}")) { Text(outing.name) }
                            }
                        }
                        OutlinedTextField(speciesFilter, { speciesFilter = it }, Modifier.fillMaxWidth().testTag("diary-species-filter"), label = { Text("Filtra specie") }, singleLine = true)
                        OutlinedTextField(dateFilter, { dateFilter = it }, Modifier.fillMaxWidth().testTag("diary-date-filter"), label = { Text("Filtra giorno (AAAA-MM-GG, vuoto per tutti)") }, singleLine = true)
                        LazyRow(modifier = Modifier.testTag("diary-view-filters"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("list" to "Elenco", "calendar" to "Calendario", "map" to "Mappa")) { (id, label) ->
                                OutlinedButton(onClick = { view = id }, modifier = Modifier.testTag("diary-view-$id")) { Text(label) }
                            }
                        }
                        val allSummary = PersonalDiaryViews.species(entries.map { it.observation }).associateBy { it.taxonId }
                        val summaries = PersonalDiaryViews.species(scoped.map { it.observation })
                        Text("${scoped.size} avvistamenti · ${summaries.size} specie · bozze escluse", modifier = Modifier.testTag("diary-summary"), color = DiaryInk)
                        summaries.forEach { summary ->
                            val name = entries.firstOrNull { it.observation.taxonId == summary.taxonId }?.taxon?.scientificName ?: summary.taxonId
                            Text("$name · prima osservazione personale ${allSummary.getValue(summary.taxonId).first.localDate}", color = DiaryMuted)
                        }
                        if (view == "calendar") PersonalDiaryViews.calendar(scoped.map { it.observation }).forEach { (day, memories) ->
                            Text("$day · ${memories.size} avvistamenti", modifier = Modifier.testTag("diary-calendar-$day"), color = DiaryInk)
                        }
                        if (view == "map") {
                            val points = PersonalDiaryViews.map(scoped.map { it.observation })
                            Text("${points.size} avvistamenti sulla mappa; ${scoped.size - points.size} senza coordinate restano nell’elenco e nel calendario.",
                                modifier = Modifier.testTag("diary-map-count"), color = DiaryMuted)
                            if (points.isNotEmpty()) personalMap.Render(points)
                        }
                    }
                    if (unidentifiedRepository != null) {
                        item {
                            Button(onClick = { editor = DiaryEditor.new(now(), deviceZone).copy(unidentified = true, tripId = selectedTrip, outingId = selectedOuting) },
                                modifier = Modifier.testTag("diary-new-draft")) { Text("Nuova bozza da identificare") }
                            Text("Le bozze conservano il ricordo e non contano come specie osservate.", color = DiaryMuted)
                        }
                        items(drafts.filter { (selectedTrip == null || it.input.tripId == selectedTrip) &&
                            (selectedOuting == null || it.input.outingId == selectedOuting) && speciesFilter.isBlank() &&
                            (dateFilter.isBlank() || it.input.observedAt.atZone(it.input.zoneId).toLocalDate().toString() == dateFilter) }, key = { "draft-${it.input.id}" }) { draft ->
                            Column(Modifier.fillMaxWidth().testTag("diary-draft-${draft.input.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Da identificare · ${draft.input.observedAt.atZone(draft.input.zoneId).toLocalDate()}", fontWeight = FontWeight.Bold, color = DiaryInk)
                                Text(draft.input.notes.ifBlank { "Nessuna nota" }, color = DiaryMuted)
                                OutlinedButton(onClick = { photoMemoryId = draft.input.id }, modifier = Modifier.testTag("diary-photos-${draft.input.id}")) {
                                    Text("Foto (${draftPhotoCounts[draft.input.id] ?: 0})")
                                }
                                OutlinedButton(onClick = { editor = DiaryEditor.from(draft) }, modifier = Modifier.testTag("diary-draft-edit-${draft.input.id}")) { Text("Modifica o identifica") }
                                if (deletingDraft == draft.input.id) {
                                    Text("Eliminare questa bozza?", color = DiaryError)
                                    Button(onClick = { scope.launch {
                                        runCatching { photos.deleteMemory(draft.input.id, identified = false) }
                                            .onSuccess {
                                                deletingDraft = null; refresh++
                                                fileCleanupWarning = if (!it) "Bozza eliminata. Alcuni file privati attendono la pulizia; riapri le foto più tardi." else null
                                            }
                                            .onFailure { listError = "Non riesco a eliminare la bozza. Riprova." }
                                    } }, modifier = Modifier.testTag("diary-draft-delete-confirm-${draft.input.id}")) { Text("Elimina bozza") }
                                    OutlinedButton(onClick = { deletingDraft = null }) { Text("Annulla") }
                                } else OutlinedButton(onClick = { deletingDraft = draft.input.id }, modifier = Modifier.testTag("diary-draft-delete-${draft.input.id}")) { Text("Elimina") }
                            }
                        }
                    }
                },
            )
        } else {
            DiaryEditorForm(
                editor = activeEditor,
                taxonomySearch = taxonomySearch,
                onEditorChange = { editor = it },
                onCancel = { editor = null; onExitEditor() },
                onSaved = {
                    editor = null
                    refresh++
                    onExitEditor()
                },
                onSave = { draft ->
                    if (activeEditor.unidentified && activeEditor.id != null) requireNotNull(unidentifiedRepository).convert(draft)
                    else if (activeEditor.id == null) diary.create(draft) else diary.update(draft)
                },
                idFactory = idFactory,
                onSaveUnidentified = { requireNotNull(unidentifiedRepository).save(it) },
                trips = trips,
                outings = outings,
            )
        }
    }
    photoMemoryId?.let { id -> PhotoGallery(id, photos, onClose = { photoMemoryId = null; refresh++ }) }
}

@Composable
private fun DiaryHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text("Faunavia", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "Diario",
                modifier = Modifier.testTag("screen-title-diary"),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DiaryList(
    entries: List<DiaryListEntry>,
    loading: Boolean,
    error: String?,
    deletingId: String?,
    onCreate: () -> Unit,
    onEdit: (DiaryListEntry) -> Unit,
    onPhotos: (String) -> Unit,
    onStartDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (String) -> Unit,
    extra: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("diary-list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("I tuoi avvistamenti", color = DiaryInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Salvati su questo dispositivo. La specie è sempre obbligatoria.",
                color = DiaryMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCreate, modifier = Modifier.testTag("diary-new")) { Text("Nuovo avvistamento") }
        }
        error?.let { message ->
            item { Text(message, modifier = Modifier.testTag("diary-list-error"), color = DiaryError) }
        }
        extra()
        when {
            loading -> item { Text("Caricamento del diario…", modifier = Modifier.testTag("diary-loading"), color = DiaryMuted) }
            entries.isEmpty() -> item {
                Text(
                    "Nessun avvistamento ancora. Registra il primo anche senza connessione.",
                    modifier = Modifier.testTag("diary-empty"),
                    color = DiaryMuted,
                )
            }
            else -> items(entries, key = { it.observation.id }) { entry ->
                DiaryEntry(
                    entry = entry,
                    deleteConfirmation = deletingId == entry.observation.id,
                    onEdit = { onEdit(entry) },
                    onPhotos = { onPhotos(entry.observation.id) },
                    onStartDelete = { onStartDelete(entry.observation.id) },
                    onCancelDelete = onCancelDelete,
                    onConfirmDelete = { onConfirmDelete(entry.observation.id) },
                )
            }
        }
        item {
            Text(
                "Aggiungi le foto dall’elenco dopo aver salvato il ricordo. Le immagini restano private; il diario funziona anche senza foto.",
                color = DiaryMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DiaryEntry(
    entry: DiaryListEntry,
    deleteConfirmation: Boolean,
    onEdit: () -> Unit,
    onPhotos: () -> Unit,
    onStartDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val observation = entry.observation
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diary-entry-${observation.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.taxon?.scientificName ?: observation.taxonId, color = DiaryInk, fontWeight = FontWeight.Bold)
            entry.taxon?.commonName?.let { Text(it, color = DiaryMuted, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "${DiaryDateFormatter.format(observation.observedAt.atZone(observation.zoneId))} · " +
                    "${DiaryTimeFormatter.format(observation.observedAt.atZone(observation.zoneId))} · " +
                    "${observation.quantity} ${if (observation.quantity == 1) "esemplare" else "esemplari"}",
                color = DiaryMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (observation.notes.isNotBlank()) Text(observation.notes, color = DiaryInk, style = MaterialTheme.typography.bodyMedium)
            if (observation.location != null) {
                Text("Posizione salvata", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (observation.tripId != null) Text("Collegato al viaggio${if (observation.outingId != null) " e all’uscita" else ""}", color = DiaryMuted)
            OutlinedButton(onClick = onPhotos, modifier = Modifier.testTag("diary-photos-${observation.id}")) { Text("Foto (${entry.photoCount})") }
            if (deleteConfirmation) {
                Text("Eliminare definitivamente questo avvistamento?", color = DiaryError, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancelDelete, modifier = Modifier.testTag("diary-delete-cancel-${observation.id}")) {
                        Text("Annulla")
                    }
                    Button(onClick = onConfirmDelete, modifier = Modifier.testTag("diary-delete-confirm-${observation.id}")) {
                        Text("Elimina")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit, modifier = Modifier.testTag("diary-edit-${observation.id}")) { Text("Modifica") }
                    TextButton(onClick = onStartDelete, modifier = Modifier.testTag("diary-delete-${observation.id}")) { Text("Elimina") }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DiaryEditorForm(
    editor: DiaryEditor,
    taxonomySearch: TaxonomySearch,
    onEditorChange: (DiaryEditor) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onSave: suspend (ObservationDraft) -> Observation,
    idFactory: () -> String,
    onSaveUnidentified: suspend (UnidentifiedInput) -> Unit,
    trips: List<Trip>,
    outings: List<Outing>,
) {
    var searchEntries by remember { mutableStateOf<List<TaxonomySearchEntry>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchRefresh by remember { mutableIntStateOf(0) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val latestEditor by rememberUpdatedState(editor)
    var retrySearch by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(editor.taxonQuery, editor.selectedTaxon?.id, searchRefresh) {
        val generation = ++searchGeneration
        val query = normalizeQuery(editor.taxonQuery)
        searchEntries = emptyList()
        retrySearch = false
        searching = false
        searchMessage = null
        if (editor.selectedTaxon != null || query.length < MINIMUM_TAXON_QUERY_LENGTH) {
            if (editor.selectedTaxon == null && query.isNotEmpty()) searchMessage = "Inserisci almeno $MINIMUM_TAXON_QUERY_LENGTH caratteri."
            return@LaunchedEffect
        }
        searching = true
        try {
            delay(350)
            val result = withContext(Dispatchers.IO) { taxonomySearch.search(query) }
            searchEntries = when (result) {
                is TaxonomySearchResult.Results -> {
                    if (result.origin == TaxonomySearchOrigin.OFFLINE_SELECTED) searchMessage = "Mostro le specie già salvate sul dispositivo."
                    result.entries
                }
                is TaxonomySearchResult.Empty -> {
                    searchMessage = if (result.origin == TaxonomySearchOrigin.OFFLINE_SELECTED)
                        "Nessuna specie salvata corrisponde. Cerca nel catalogo quando torni online."
                    else "Nessuna specie trovata. Prova un nome comune, scientifico o un sinonimo."
                    emptyList()
                }
                is TaxonomySearchResult.AwaitingQuery -> emptyList()
                is TaxonomySearchResult.Failure -> {
                    retrySearch = true
                    searchMessage = "${taxonomyFailureMessage(result.reason)} Puoi scegliere le specie già salvate."
                    result.cachedEntries
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == searchGeneration) {
                retrySearch = true
                searchMessage = "La ricerca non è disponibile. Riprova; i ricordi salvati sono conservati."
            }
        } finally {
            if (generation == searchGeneration) searching = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("diary-editor"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (editor.unidentified) "Bozza da identificare" else if (editor.id == null) "Nuovo avvistamento" else "Modifica avvistamento",
                color = DiaryInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("I campi con specie, data, ora e quantità sono salvati localmente.", color = DiaryMuted, style = MaterialTheme.typography.bodyMedium)
            Text("Conferma la data e la posizione effettive. Nessuna coordinata esterna viene copiata.", color = DiaryMuted, modifier = Modifier.testTag("diary-confirm-actual"))
            Text("Dopo il salvataggio puoi aggiungere foto dall’elenco. Anche le bozze conservano le foto quando vengono identificate.",
                color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedTextField(
                value = editor.taxonQuery,
                onValueChange = { query ->
                    onEditorChange(editor.copy(taxonQuery = query, selectedTaxon = null))
                },
                modifier = Modifier.fillMaxWidth().testTag("diary-taxon-query"),
                label = { Text(if (editor.unidentified) "Specie (se identificata, converte la bozza)" else "Specie obbligatoria") },
                supportingText = { Text("Cerca nel catalogo generale. Le specie scelte restano disponibili offline.") },
                enabled = !selecting && !saving,
                singleLine = true,
            )
            editor.selectedTaxon?.let { taxon ->
                Text(
                    "Specie selezionata: ${taxon.scientificName}",
                    modifier = Modifier.padding(top = 6.dp).testTag("diary-taxon-selected"),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (searching) Text("Ricerca nel catalogo…", color = DiaryMuted, modifier = Modifier.testTag("diary-taxon-loading"))
            if (selecting) Text("Salvataggio della specie…", color = DiaryMuted)
            searchMessage?.let { Text(it, color = DiaryMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diary-taxon-message")) }
            if (retrySearch) OutlinedButton(onClick = { searchRefresh++ }, enabled = !searching && !selecting,
                modifier = Modifier.testTag("diary-taxon-retry")) { Text("Riprova ricerca") }
        }
        if (searchEntries.isNotEmpty()) {
            items(searchEntries, key = { it.taxon.id }) { entry ->
                OutlinedButton(
                    onClick = {
                        selecting = true
                        searchMessage = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { taxonomySearch.select(entry) }
                                onEditorChange(latestEditor.copy(selectedTaxon = entry.taxon, taxonQuery = entry.taxon.scientificName))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                searchMessage = "Non riesco a salvare questa specie sul dispositivo. Selezionala di nuovo per riprovare."
                            } finally {
                                selecting = false
                            }
                        }
                    },
                    enabled = !selecting && !saving,
                    modifier = Modifier.fillMaxWidth().testTag("diary-taxon-result-${entry.taxon.id}"),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(entry.taxon.scientificName, fontWeight = FontWeight.Bold)
                        Text(entry.taxon.commonName ?: "Nome comune non disponibile")
                    }
                }
            }
        }
        if (trips.isNotEmpty()) item {
            Text("Collegamento al viaggio", color = DiaryInk)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OutlinedButton(onClick = { onEditorChange(editor.copy(tripId = null, outingId = null)) }) { Text("Senza viaggio") } }
                items(trips, key = { it.id }) { trip ->
                    OutlinedButton(onClick = { onEditorChange(editor.copy(tripId = trip.id, outingId = null)) }, modifier = Modifier.testTag("diary-link-trip-${trip.id}")) { Text(trip.name) }
                }
            }
            editor.tripId?.let { tripId ->
                Text("Viaggio: ${trips.firstOrNull { it.id == tripId }?.name ?: tripId}", color = DiaryMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { OutlinedButton(onClick = { onEditorChange(editor.copy(outingId = null)) }) { Text("Senza uscita") } }
                    items(outings.filter { it.tripId == tripId }, key = { it.id }) { outing ->
                        OutlinedButton(onClick = { onEditorChange(editor.copy(outingId = outing.id)) }, modifier = Modifier.testTag("diary-link-outing-${outing.id}")) { Text(outing.name) }
                    }
                }
                Text("Uscita: ${outings.firstOrNull { it.id == editor.outingId }?.name ?: "nessuna"}", color = DiaryMuted)
            }
        }
        item {
            OutlinedTextField(
                value = editor.date,
                onValueChange = { onEditorChange(editor.copy(date = it)) },
                modifier = Modifier.fillMaxWidth().testTag("diary-date"),
                label = { Text("Data locale (AAAA-MM-GG)") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = editor.time,
                onValueChange = { onEditorChange(editor.copy(time = it)) },
                modifier = Modifier.fillMaxWidth().testTag("diary-time"),
                label = { Text("Ora locale (HH:MM) · ${editor.zoneId.id}") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = editor.quantity,
                onValueChange = { onEditorChange(editor.copy(quantity = it)) },
                modifier = Modifier.fillMaxWidth().testTag("diary-quantity"),
                label = { Text("Quantità") },
                supportingText = { Text("Da 1 a $MAX_OBSERVATION_QUANTITY; usa 1 se non conosci il numero.") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = editor.notes,
                onValueChange = { onEditorChange(editor.copy(notes = it)) },
                modifier = Modifier.fillMaxWidth().testTag("diary-notes"),
                label = { Text("Note opzionali") },
                supportingText = { Text("Massimo $MAX_OBSERVATION_NOTES_LENGTH caratteri.") },
                minLines = 3,
                maxLines = 5,
            )
        }
        item {
            Text("Posizione opzionale", color = DiaryInk, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Lascia entrambi i campi vuoti se non vuoi salvarla.", color = DiaryMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.latitude,
                    onValueChange = { onEditorChange(editor.copy(latitude = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("diary-latitude"),
                    label = { Text("Latitudine") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.longitude,
                    onValueChange = { onEditorChange(editor.copy(longitude = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("diary-longitude"),
                    label = { Text("Longitudine") },
                    singleLine = true,
                )
            }
        }
        saveError?.let { message ->
            item { Text(message, modifier = Modifier.testTag("diary-editor-error"), color = DiaryError) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCancel, enabled = !saving && !selecting, modifier = Modifier.testTag("diary-cancel")) { Text("Annulla") }
                Button(
                    onClick = {
                        saveError = null
                        val identity = editor.id ?: idFactory()
                        scope.launch {
                            saving = true
                            runCatching { withContext(Dispatchers.IO) {
                                if (editor.unidentified && editor.selectedTaxon == null) onSaveUnidentified(editor.unidentifiedInput(identity))
                                else onSave(editor.draft(identity))
                            } }
                                .onSuccess { onSaved() }
                                .onFailure { saveError = if (it is IllegalArgumentException) it.message else "Non riesco a salvare il ricordo. Riprova." }
                            saving = false
                        }
                    },
                    enabled = !saving && !selecting,
                    modifier = Modifier.testTag("diary-save"),
                ) { Text(if (saving) "Salvataggio…" else if (editor.unidentified && editor.selectedTaxon == null) "Salva bozza"
                    else if (editor.unidentified && editor.id != null) "Conferma identificazione" else "Salva avvistamento") }
            }
        }
    }
}
