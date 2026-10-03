package it.faunavia.app

import android.content.Context
import android.net.Uri
import android.view.Choreographer
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.IndirectLight
import com.google.android.filament.Engine
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

internal val LocalPersonalModels = staticCompositionLocalOf<PersonalModelStore?> { null }

@Composable
internal fun SpeciesModelAction(onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.testTag("profile-model-open")) { Text("Modello 3D e libreria personale") }
}

@Composable
internal fun SpeciesModelDialog(id: String, scientificName: String, store: PersonalModelStore, onClose: () -> Unit) {
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var refresh by remember(id) { mutableIntStateOf(0) }
    var loaded by remember(id, refresh) { mutableStateOf<LoadedModel?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var busy by remember(id) { mutableStateOf(false) }
    var message by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var failure by remember(id, refresh) { mutableStateOf<String?>(null) }
    var personal by remember(id) { mutableStateOf(false) }
    var edit by rememberSaveable(id) { mutableStateOf(false) }
    var removeConfirm by rememberSaveable(id) { mutableStateOf(false) }
    var author by rememberSaveable(id) { mutableStateOf("") }
    var source by rememberSaveable(id) { mutableStateOf("") }
    var license by rememberSaveable(id) { mutableStateOf("") }
    var modifications by rememberSaveable(id) { mutableStateOf("Nessuna") }
    var rights by rememberSaveable(id) { mutableStateOf(false) }
    var selected by rememberSaveable(id) { mutableIntStateOf(0) }
    var playing by rememberSaveable(id) { mutableStateOf(false) }
    var reset by remember(id) { mutableIntStateOf(0) }
    var ready by remember(id, refresh) { mutableStateOf(false) }

    LaunchedEffect(id, refresh, store) {
        loading = true
        try {
            personal = store.repository.get(id) != null
            loaded = store.load(id, scientificName)
            selected = selected.coerceIn(0, maxOf(0, (loaded?.info?.clips?.size ?: 0) - 1))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Modello non disponibile; la scheda 2D resta utilizzabile." }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    store.import(id, scientificName, uri, ModelCredit(author, source, license, modifications))
                    message = "Modello personale salvato per $scientificName. Disponibile offline."
                    playing = false; selected = 0; edit = false; refresh++
                } catch (error: Exception) { message = error.message ?: "Importazione fallita. Il modello precedente è conservato." }
                finally { busy = false }
            }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Surface(Modifier.fillMaxSize().testTag("species-model-dialog"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Modello 3D", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.testTag("model-close")) { Text("Chiudi") }
                    }
                    HorizontalDivider()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(scientificName, style = MaterialTheme.typography.titleLarge)
                        Text("Illustrazione facoltativa · non conferma l’identificazione o la presenza della specie.", style = MaterialTheme.typography.bodySmall)
                        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        message?.let { Text(it, modifier = Modifier.testTag("model-message")) }
                        (LocalContext.current.applicationContext as? FaunaviaApplication)?.modelCleanupWarning?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        failure?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("model-error"))
                            TextButton(onClick = { refresh++ }, enabled = !busy, modifier = Modifier.testTag("model-retry")) { Text("Riprova 3D") }
                            Text("Il simbolo 2D, il diario e le evidenze restano disponibili chiudendo il 3D.", modifier = Modifier.testTag("model-fallback"))
                        }
                        val asset = loaded
                        if (asset != null && failure == null) {
                            key(refresh) {
                                AndroidView(factory = { context -> ModelSurface(context, asset, owner,
                                    onReady = { ready = true }, onFailure = { failure = it }) },
                                    update = { it.control(selected, playing, reset) },
                                    modifier = Modifier.fillMaxWidth().height(280.dp).testTag("model-surface"))
                            }
                            Text(if (ready) "3D pronto · trascina per ruotare, pizzica per zoomare" else "Preparazione del 3D…", modifier = Modifier.testTag("model-status"))
                            TextButton(onClick = { reset++ }, modifier = Modifier.testTag("model-reset")) { Text("Ripristina vista") }
                            if (asset.info.clips.isEmpty()) Text("Modello statico · nessuna clip di animazione", modifier = Modifier.testTag("model-static"))
                            else {
                                Text("Animazioni")
                                asset.info.clips.forEachIndexed { index, clip ->
                                    FilterChip(selected == index, onClick = { selected = index }, label = { Text(clip.name) }, modifier = Modifier.testTag("model-clip-$index"))
                                }
                                Button(onClick = { playing = !playing }, enabled = ready, modifier = Modifier.testTag("model-play")) {
                                    Text(if (playing) "Pausa" else "Riproduci")
                                }
                            }
                            Text(asset.credit, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("model-credit"))
                        } else if (!loading && failure == null) Text("Nessun modello 3D per questa specie. Puoi importare un tuo GLB.", modifier = Modifier.testTag("model-unavailable"))
                        OutlinedButton(onClick = { edit = !edit }, enabled = !busy, modifier = Modifier.testTag("model-import-form")) {
                            Text(if (personal) "Sostituisci modello personale" else "Importa GLB personale")
                        }
                        if (edit) {
                            Text("Il file viene copiato nell’app e associato a $scientificName. Massimo 20 MB; risorse incorporate. Scegli un modello che hai diritto di usare.")
                            OutlinedTextField(author, { author = it }, label = { Text("Autore") }, modifier = Modifier.fillMaxWidth().testTag("model-author"))
                            OutlinedTextField(source, { source = it }, label = { Text("Fonte o origine personale") }, modifier = Modifier.fillMaxWidth().testTag("model-source"))
                            OutlinedTextField(license, { license = it }, label = { Text("Licenza o diritti d’uso") }, modifier = Modifier.fillMaxWidth().testTag("model-license"))
                            OutlinedTextField(modifications, { modifications = it }, label = { Text("Modifiche") }, modifier = Modifier.fillMaxWidth().testTag("model-modifications"))
                            Row { Checkbox(rights, { rights = it }, modifier = Modifier.testTag("model-rights")); Text("Confermo provenienza e diritto di usare questo modello.", modifier = Modifier.padding(top = 10.dp)) }
                            Button(onClick = {
                                try { ModelCredit(author, source, license, modifications).checked(); picker.launch(arrayOf("*/*")) }
                                catch (error: Exception) { message = error.message }
                            }, enabled = rights && !busy, modifier = Modifier.testTag("model-pick")) { Text("Scegli file GLB") }
                        }
                        if (personal) TextButton(onClick = { removeConfirm = true }, enabled = !busy, modifier = Modifier.testTag("model-remove")) { Text("Rimuovi modello personale") }
                    }
                }
            }
        }
    }
    if (removeConfirm) AlertDialog(onDismissRequest = { removeConfirm = false }, title = { Text("Rimuovere il modello personale?") },
        text = { Text("Scheda e osservazioni sono conservate. Tornerà il modello incluso, se disponibile, oppure il simbolo 2D.") },
        confirmButton = { TextButton(onClick = {
            removeConfirm = false; busy = true
            scope.launch {
                try { store.remove(id); playing = false; refresh++; message = "Modello personale rimosso." }
                catch (error: Exception) { message = error.message ?: "Rimozione fallita. Riprova." }
                finally { busy = false }
            }
        }, modifier = Modifier.testTag("model-remove-confirm")) { Text("Rimuovi") } },
        dismissButton = { TextButton(onClick = { removeConfirm = false }) { Text("Annulla") } })
}

/** Filament owns and destroys its engine on view detachment; stop frame callbacks before that event. */
@android.annotation.SuppressLint("ViewConstructor") // Created programmatically with one asset; never inflated from XML.
internal class ModelSurface(context: Context, private val model: LoadedModel, private val owner: LifecycleOwner,
    private val onReady: () -> Unit, private val onFailure: (String) -> Unit,
) : TextureView(context), DefaultLifecycleObserver, Choreographer.FrameCallback {
    private var viewer: ModelViewer? = null
    private var light: IndirectLight? = null
    private var sky: Skybox? = null
    private var active = false
    private var selected = 0
    private var playing = false
    private var reset = 0
    private var elapsed = 0f
    private var previous = 0L
    private var ready = false
    internal var renderedFrames = 0
        private set

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            Utils.init()
            val backend = if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) Engine.Backend.VULKAN else Engine.Backend.OPENGL
            val value = ModelViewer(this, engine = Engine.create(backend))
            viewer = value
            value.autoPlayAnimations = false
            light = IndirectLight.Builder().irradiance(1, floatArrayOf(.8f, .8f, .8f)).intensity(25_000f).build(value.engine)
            sky = Skybox.Builder().color(.84f, .88f, .83f, 1f).build(value.engine)
            value.scene.indirectLight = light; value.scene.skybox = sky
            value.loadModelGlb(ByteBuffer.allocateDirect(model.bytes.size).apply { put(model.bytes); flip() })
            if (value.asset == null) throw ModelFailure("Il viewer non riesce a leggere il modello. Chiudi il 3D per usare il simbolo 2D.")
            value.transformToUnitCube()
            contentDescription = "Modello illustrativo 3D: trascina per ruotare e pizzica per zoomare"
            setOnTouchListener(value)
            owner.lifecycle.addObserver(this)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        } catch (_: Exception) { onFailure("Viewer 3D non disponibile. La scheda 2D resta utilizzabile.") }
    }
    fun control(clip: Int, play: Boolean, newReset: Int) {
        if (selected != clip) { selected = clip; elapsed = 0f }
        playing = play
        if (reset != newReset) { reset = newReset; viewer?.resetToDefaultState(); elapsed = 0f }
    }
    override fun onResume(owner: LifecycleOwner) = start()
    override fun onPause(owner: LifecycleOwner) = stop()
    private fun start() {
        if (active || viewer == null) return
        active = true; previous = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }
    private fun stop() { active = false; previous = 0L; Choreographer.getInstance().removeFrameCallback(this) }
    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        val value = viewer ?: return
        try {
            if (previous > 0 && playing) elapsed += ((frameTimeNanos - previous).coerceAtMost(100_000_000L) / 1_000_000_000f)
            previous = frameTimeNanos
            value.animator?.let { animator ->
                if (selected in 0 until animator.animationCount) {
                    val duration = animator.getAnimationDuration(selected)
                    animator.applyAnimation(selected, if (duration > 0) elapsed % duration else 0f)
                    animator.updateBoneMatrices()
                }
            }
            if (value.render(frameTimeNanos)) renderedFrames++
            if (!ready && value.progress >= 1f && renderedFrames > 0) { ready = true; onReady() }
            Choreographer.getInstance().postFrameCallback(this)
        } catch (_: Exception) { stop(); onFailure("Rendering 3D interrotto. Riprova oppure chiudi per tornare alla scheda 2D.") }
    }
    override fun onDetachedFromWindow() {
        stop(); owner.lifecycle.removeObserver(this)
        viewer?.let { value ->
            value.scene.indirectLight = null; value.scene.skybox = null
            light?.let(value.engine::destroyIndirectLight); sky?.let(value.engine::destroySkybox)
        }
        light = null; sky = null; viewer = null
        // ModelViewer's detach listener releases its model, loader, swap chain and engine once.
        super.onDetachedFromWindow()
    }
}
