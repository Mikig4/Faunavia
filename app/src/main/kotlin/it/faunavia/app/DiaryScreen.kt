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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import it.faunavia.taxonomy.MINIMUM_TAXON_QUERY_LENGTH
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.taxonomy.normalizeQuery
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
)

/**
 * UI state stays in memory only. A partially typed taxon query never becomes a diary record.
 * Saving and listing use only local Room repositories; taxonomy lookup is explicitly offline.
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
            )
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
        )
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
) {
    var refresh by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<DiaryListEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<DiaryEditor?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = true
        listError = null
        runCatching {
            withContext(Dispatchers.IO) {
                diary.list().map { observation -> DiaryListEntry(observation, catalogue.taxon(observation.taxonId)) }
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
            DiaryList(
                entries = entries,
                loading = loading,
                error = listError,
                deletingId = deletingId,
                onCreate = { editor = DiaryEditor.new(now(), deviceZone) },
                onEdit = { entry -> editor = DiaryEditor.from(entry) },
                onStartDelete = { deletingId = it },
                onCancelDelete = { deletingId = null },
                onConfirmDelete = { id ->
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { diary.delete(id) } }
                            .onSuccess {
                                deletingId = null
                                refresh++
                            }
                            .onFailure { listError = "Non riesco a eliminare questo avvistamento. Riprova." }
                    }
                },
            )
        } else {
            DiaryEditorForm(
                editor = activeEditor,
                taxonomySearch = taxonomySearch,
                onEditorChange = { editor = it },
                onCancel = { editor = null },
                onSaved = {
                    editor = null
                    refresh++
                },
                onSave = { draft ->
                    if (activeEditor.id == null) diary.create(draft) else diary.update(draft)
                },
                idFactory = idFactory,
            )
        }
    }
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
    onStartDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (String) -> Unit,
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
                    onStartDelete = { onStartDelete(entry.observation.id) },
                    onCancelDelete = onCancelDelete,
                    onConfirmDelete = { onConfirmDelete(entry.observation.id) },
                )
            }
        }
        item {
            Text(
                "Le foto locali saranno aggiunte in F10; il diario resta utilizzabile senza foto.",
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
) {
    var searchEntries by remember { mutableStateOf<List<TaxonomySearchEntry>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(editor.taxonQuery) {
        val query = normalizeQuery(editor.taxonQuery)
        if (query.length < MINIMUM_TAXON_QUERY_LENGTH) {
            searchEntries = emptyList()
            searchMessage = if (query.isEmpty()) null else "Inserisci almeno $MINIMUM_TAXON_QUERY_LENGTH caratteri."
            return@LaunchedEffect
        }
        searchMessage = null
        val result = runCatching { withContext(Dispatchers.IO) { taxonomySearch.search(query, online = false) } }
            .getOrElse {
                searchMessage = "La ricerca locale non è disponibile."
                return@LaunchedEffect
            }
        searchEntries = when (result) {
            is TaxonomySearchResult.Results -> result.entries
            is TaxonomySearchResult.Empty -> {
                searchMessage = "Nessun taxon già salvato corrisponde alla ricerca."
                emptyList()
            }
            is TaxonomySearchResult.AwaitingQuery -> emptyList()
            is TaxonomySearchResult.Failure -> {
                searchMessage = "La ricerca locale non è disponibile."
                result.cachedEntries
            }
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
                if (editor.id == null) "Nuovo avvistamento" else "Modifica avvistamento",
                color = DiaryInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("I campi con specie, data, ora e quantità sono salvati localmente.", color = DiaryMuted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = editor.taxonQuery,
                onValueChange = { query ->
                    onEditorChange(editor.copy(taxonQuery = query, selectedTaxon = null))
                },
                modifier = Modifier.fillMaxWidth().testTag("diary-taxon-query"),
                label = { Text("Specie obbligatoria") },
                supportingText = { Text("Cerca tra i taxa già scelti nel Catalogo: funziona offline.") },
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
            searchMessage?.let { Text(it, color = DiaryMuted, style = MaterialTheme.typography.bodySmall) }
        }
        if (searchEntries.isNotEmpty()) {
            items(searchEntries, key = { it.taxon.id }) { entry ->
                OutlinedButton(
                    onClick = {
                        onEditorChange(editor.copy(selectedTaxon = entry.taxon, taxonQuery = entry.taxon.scientificName))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("diary-taxon-result-${entry.taxon.id}"),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(entry.taxon.scientificName, fontWeight = FontWeight.Bold)
                        Text(entry.taxon.commonName ?: "Nome comune non disponibile")
                    }
                }
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
                OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.testTag("diary-cancel")) { Text("Annulla") }
                Button(
                    onClick = {
                        saveError = null
                        val draft = runCatching { editor.draft(editor.id ?: idFactory()) }
                            .getOrElse {
                                saveError = it.message ?: "Controlla i dati dell'avvistamento."
                                return@Button
                            }
                        scope.launch {
                            saving = true
                            runCatching { withContext(Dispatchers.IO) { onSave(draft) } }
                                .onSuccess { onSaved() }
                                .onFailure { saveError = "Non riesco a salvare l'avvistamento. Riprova." }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.testTag("diary-save"),
                ) { Text(if (saving) "Salvataggio…" else "Salva avvistamento") }
            }
        }
    }
}
