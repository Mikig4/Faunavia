package it.faunavia.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.faunavia.domain.ObservationPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** IO continues across Activity recreation; committed albums always reload from Room. */
internal class PhotoGalleryModel : ViewModel() {
    var photos by mutableStateOf<List<ObservationPhoto>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load(id: String, manager: MemoryPhotos) = runOperation {
        val recovered = manager.recover()
        photos = manager.list(id)
        if (!recovered) error = "Alcuni file temporanei non sono stati rimossi. Riprova; i ricordi sono conservati."
    }

    fun import(id: String, uris: List<Uri>, manager: MemoryPhotos) = runOperation {
        var failed = 0
        var reason: String? = null
        for (uri in uris.take(5)) {
            try { manager.attach(id, uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed++; reason = (failure as? PhotoFailure)?.message }
        }
        photos = manager.list(id)
        if (failed > 0) error = "$failed foto non aggiunte. ${reason ?: "Riprova a selezionarle; il ricordo è conservato."}"
        else if (uris.size > 5) error = "Aggiunte le prime 5 foto. Seleziona di nuovo le altre immagini."
    }

    fun delete(id: String, photo: ObservationPhoto, manager: MemoryPhotos) = runOperation {
        val cleaned = manager.delete(id, photo.id)
        photos = manager.list(id)
        if (!cleaned) error = "Foto scollegata. Alcuni file privati attendono la pulizia: riprova più tardi."
    }

    fun pickerFailed() { error = "Il selettore foto non è disponibile. Riprova; il ricordo è conservato." }

    private fun runOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = (failure as? PhotoFailure)?.message ?: "Non riesco a gestire le foto locali. Riprova; il ricordo è conservato." }
            finally { busy = false }
        }
    }
}

@Composable
internal fun PhotoGallery(memoryId: String, manager: MemoryPhotos, onClose: () -> Unit) {
    val model = viewModel<PhotoGalleryModel>(key = "photos-$memoryId")
    var fullPhotoId by rememberSaveable(memoryId) { mutableStateOf<String?>(null) }
    var removingId by rememberSaveable(memoryId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) model.import(memoryId, uris, manager)
    }
    LaunchedEffect(memoryId) { model.load(memoryId, manager) }
    Dialog(onDismissRequest = { if (!model.busy) onClose() }) {
        Card(Modifier.fillMaxWidth().heightIn(max = 640.dp).testTag("photo-gallery")) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("photo-list")) {
                item {
                    Text("Foto del ricordo", style = MaterialTheme.typography.titleLarge)
                    Text("Copie private su questo dispositivo. Posizione GPS e altri metadati EXIF rimossi.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("${model.photos.size} foto", modifier = Modifier.testTag("photo-count"))
                    Button(onClick = {
                        try { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                        catch (_: Exception) { model.pickerFailed() }
                    }, enabled = !model.busy, modifier = Modifier.testTag("photo-add")) { Text("Aggiungi foto") }
                    if (model.busy) Text("Preparazione foto…", modifier = Modifier.testTag("photo-busy"))
                    model.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("photo-error"))
                        OutlinedButton(onClick = { model.load(memoryId, manager) }, enabled = !model.busy) { Text("Riprova lettura") }
                    }
                    TextButton(onClick = onClose, enabled = !model.busy, modifier = Modifier.testTag("photo-close")) { Text("Chiudi") }
                }
                if (!model.busy && model.photos.isEmpty()) item {
                    Text("Nessuna foto. Il ricordo resta completo anche senza immagini.", modifier = Modifier.testTag("photo-empty"))
                }
                items(model.photos, key = { it.id }) { photo ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivatePhotoPreview(photo, manager.store, fullPhotoId != photo.id)
                        Text("${if (photo.width > 0) "${photo.width} × ${photo.height}" else "Dimensioni non disponibili"} · ${photo.byteSize / 1024} KB",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { fullPhotoId = if (fullPhotoId == photo.id) null else photo.id },
                            modifier = Modifier.testTag("photo-open-${photo.id}")) {
                            Text(if (fullPhotoId == photo.id) "Mostra miniatura" else "Apri foto")
                        }
                        if (removingId == photo.id) {
                            Text("Eliminare solo questa foto? Il ricordo sarà conservato.")
                            Button(onClick = { model.delete(memoryId, photo, manager); removingId = null }, enabled = !model.busy,
                                modifier = Modifier.testTag("photo-delete-confirm-${photo.id}")) { Text("Elimina foto") }
                            TextButton(onClick = { removingId = null }) { Text("Annulla") }
                        } else TextButton(onClick = { removingId = photo.id }, enabled = !model.busy,
                            modifier = Modifier.testTag("photo-delete-${photo.id}")) { Text("Rimuovi foto") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivatePhotoPreview(photo: ObservationPhoto, store: PrivatePhotoStore, thumbnail: Boolean) {
    var loaded by remember(photo, thumbnail) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, photo, thumbnail) {
        loaded = false
        value = withContext(Dispatchers.IO) { runCatching { store.load(photo, thumbnail) }.getOrNull() }
        loaded = true
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), "Foto privata del ricordo", modifier = Modifier
        .fillMaxWidth().heightIn(max = if (thumbnail) 180.dp else 400.dp).testTag("photo-image-${photo.id}"))
    else Text(if (loaded) "Foto mancante o non leggibile. Puoi rimuoverla o aggiungerla di nuovo; il ricordo è conservato."
        else "Caricamento foto…", modifier = Modifier.padding(8.dp).testTag("photo-unavailable-${photo.id}"))
}
