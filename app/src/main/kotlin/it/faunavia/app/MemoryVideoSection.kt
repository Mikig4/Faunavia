package it.faunavia.app

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.faunavia.domain.MemoryVideo
import it.faunavia.domain.VideoTrim
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun videoTime(ms: Long): String = String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, ms / 1000 % 60, ms % 1000)

internal class MemoryVideoModel : ViewModel() {
    var video by mutableStateOf<MemoryVideo?>(null); private set
    var loaded by mutableStateOf(false); private set
    var source by mutableStateOf<StagedVideo?>(null); private set
    var trim by mutableStateOf(VideoTrim(0, 1)); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    private var job: Job? = null

    fun load(id: String, manager: MemoryVideos) = operation {
        val clean = manager.recover()
        video = manager.get(id)
        loaded = true
        if (!clean) error = "Alcuni file temporanei attendono la pulizia. Il video salvato è conservato."
    }

    fun select(uri: Uri, manager: MemoryVideos) = operation {
        val next = manager.store.stage(uri)
        source?.file?.parentFile?.deleteRecursively()
        source = next
        trim = VideoTrim(0, next.info.durationMs)
    }

    fun range(range: ClosedFloatingPointRange<Float>) {
        val duration = source?.info?.durationMs ?: return
        val minimum = minOf(100L, duration)
        val start = range.start.toLong().coerceIn(0, duration - minimum)
        val end = range.endInclusive.toLong().coerceIn(start + minimum, duration)
        trim = VideoTrim(start, end)
    }

    fun save(id: String, manager: MemoryVideos) = operation {
        val clean = manager.attach(id, requireNotNull(source), trim) { progress = it }
        video = manager.get(id)
        discard()
        if (!clean) error = "Video salvato. La vecchia copia attende la pulizia."
    }

    fun remove(id: String, manager: MemoryVideos) = operation {
        val clean = manager.remove(id)
        video = manager.get(id)
        if (!clean) error = "Video scollegato. La copia privata attende la pulizia."
    }

    fun pickerFailed() { error = "Il selettore video non è disponibile. Riprova." }
    fun cancelExport() { job?.cancel() }
    fun discard() {
        source?.file?.parentFile?.deleteRecursively()
        source = null
    }

    private fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; progress = 0
        job = viewModelScope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) { error = "La preparazione richiede troppo tempo. Accorcia lo spezzone e riprova." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = (failure as? VideoFailure)?.message ?: "Non riesco a gestire questo video. Riprova; il ricordo è conservato." }
            finally { busy = false }
        }
    }

    override fun onCleared() {
        val pending = source
        job?.cancel()
        job?.invokeOnCompletion { pending?.file?.parentFile?.deleteRecursively() }
        super.onCleared()
    }
}

@Composable
internal fun MemoryVideoSection(memoryId: String, manager: MemoryVideos) {
    val model = viewModel<MemoryVideoModel>(key = "video-$memoryId")
    var removing by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { model.select(it, manager) } }
    LaunchedEffect(memoryId) { model.load(memoryId, manager) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("memory-video")) {
        HorizontalDivider()
        Text("Video del ricordo", style = MaterialTheme.typography.titleMedium)
        Text("Un solo video, fino a 100 MB. Scegli lo spezzone prima di salvarlo. L’originale resta intatto.", style = MaterialTheme.typography.bodySmall)
        model.video?.let { video ->
            Text("${videoTime(video.durationMs)} · ${video.byteSize / (1024 * 1024)} MB", modifier = Modifier.testTag("video-details"))
            OutlinedButton(onClick = { playing = true }, enabled = !model.busy, modifier = Modifier.testTag("video-open")) { Text("Riproduci video") }
            if (removing) {
                Text("Rimuovere il video? Il ricordo e le foto saranno conservati.")
                Button(onClick = { removing = false; model.remove(memoryId, manager) }, enabled = !model.busy,
                    modifier = Modifier.testTag("video-remove-confirm")) { Text("Rimuovi video") }
                TextButton(onClick = { removing = false }) { Text("Annulla") }
            } else TextButton(onClick = { removing = true }, enabled = !model.busy,
                modifier = Modifier.testTag("video-remove")) { Text("Rimuovi video") }
            if (playing) VideoPlaybackDialog(video, manager.store) { playing = false }
        } ?: Text(if (model.loaded) "Nessun video allegato" else "Lettura del video…", modifier = Modifier.testTag("video-empty"))
        Button(onClick = {
            try { picker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly)) }
            catch (_: Exception) { model.pickerFailed() }
        }, enabled = !model.busy && model.source == null, modifier = Modifier.testTag("video-add")) {
            Text(if (model.video == null) "Aggiungi video" else "Sostituisci video")
        }
        if (model.busy && model.source == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparazione video…")
            TextButton(onClick = model::cancelExport) { Text("Annulla importazione") }
        }
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("video-error")) }
        if (model.error != null && model.source == null) OutlinedButton(onClick = { model.load(memoryId, manager) }, enabled = !model.busy) { Text("Riprova lettura video") }
    }
    model.source?.let { source ->
        Dialog(onDismissRequest = { if (!model.busy) model.discard() }) {
            Card {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()).testTag("video-editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scegli lo spezzone", style = MaterialTheme.typography.titleLarge)
                    Text("Sposta i due cursori per scegliere inizio e fine.")
                    VideoPreview(source.file, model.trim, enabled = !model.busy)
                    Text("Inizio ${videoTime(model.trim.startMs)} · Fine ${videoTime(model.trim.endMs)}", modifier = Modifier.testTag("video-range-label"))
                    RangeSlider(value = model.trim.startMs.toFloat()..model.trim.endMs.toFloat(), onValueChange = model::range,
                        valueRange = 0f..source.info.durationMs.toFloat(), enabled = !model.busy, modifier = Modifier.testTag("video-range"))
                    Text("Durata selezionata: ${videoTime(model.trim.durationMs)}", modifier = Modifier.testTag("video-selected-duration"))
                    Text("Copia privata senza posizione GPS o metadati originali. La preparazione può richiedere qualche minuto.", style = MaterialTheme.typography.bodySmall)
                    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("video-editor-error")) }
                    if (model.busy) {
                        LinearProgressIndicator(progress = { model.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Preparazione ${model.progress}%…")
                        TextButton(onClick = model::cancelExport, modifier = Modifier.testTag("video-cancel-export")) { Text("Interrompi preparazione") }
                    } else {
                        Button(onClick = { model.save(memoryId, manager) }, modifier = Modifier.testTag("video-save")) {
                            Text(if (model.video == null) "Salva spezzone" else "Sostituisci con questo spezzone")
                        }
                        TextButton(onClick = model::discard, modifier = Modifier.testTag("video-discard")) { Text("Annulla") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaybackDialog(video: MemoryVideo, store: PrivateVideoStore, onClose: () -> Unit) {
    var ready by remember(video) { mutableStateOf(false) }
    var error by remember(video) { mutableStateOf<String?>(null) }
    LaunchedEffect(video) {
        try { withContext(Dispatchers.IO) { VideoInspection.validate(video, store.file(video.relativePath)) }; ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Video non disponibile o danneggiato. Puoi sostituirlo o rimuoverlo." }
    }
    Dialog(onDismissRequest = onClose) {
        Card { Column(Modifier.padding(16.dp)) {
            if (ready) VideoPreview(store.file(video.relativePath), VideoTrim(0, video.durationMs))
            else Text(error ?: "Apertura video…", modifier = Modifier.testTag("video-playback-status"))
            TextButton(onClick = onClose) { Text("Chiudi video") }
        } }
    }
}

/** Explicit playback only; pause at the selected end and whenever the app leaves the foreground. */
@Composable
internal fun VideoPreview(file: File, trim: VideoTrim, enabled: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(file) { VideoView(context) }
    var prepared by remember(file) { mutableStateOf(false) }
    var error by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var player by remember(file) { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(view, lifecycle) {
        view.setVideoURI(Uri.fromFile(file))
        view.setOnPreparedListener { player = it; prepared = true; it.seekTo(trim.startMs, android.media.MediaPlayer.SEEK_CLOSEST) }
        view.setOnErrorListener { _, _, _ -> error = true; playing = false; true }
        view.setOnCompletionListener { playing = false }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { view.pause(); playing = false } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.stopPlayback(); player = null }
    }
    LaunchedEffect(trim, prepared, enabled) {
        view.pause()
        playing = false
        if (prepared && !error) player?.seekTo(trim.startMs, android.media.MediaPlayer.SEEK_CLOSEST)
        while (prepared && !error && enabled) {
            if (view.isPlaying && view.currentPosition >= trim.endMs) { view.pause(); playing = false }
            delay(50)
        }
    }
    if (error) Text("Anteprima non disponibile per questo formato. Puoi provare a salvare lo spezzone.")
    else AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(190.dp).testTag("video-preview"))
    OutlinedButton(onClick = {
        if (view.isPlaying) { view.pause(); playing = false }
        else {
            if (view.currentPosition < trim.startMs || view.currentPosition >= trim.endMs) player?.seekTo(trim.startMs, android.media.MediaPlayer.SEEK_CLOSEST)
            view.start(); playing = true
        }
    }, enabled = prepared && !error && enabled, modifier = Modifier.testTag("video-preview-play")) { Text(if (playing) "Pausa" else "Riproduci spezzone") }
}
