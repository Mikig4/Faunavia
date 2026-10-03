package it.faunavia.app

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackupViewModel(private val application: android.app.Application,
    private val archive: LocalBackupArchive,
    private val restoreBackup: suspend (PreparedBackup) -> BackupPreview = { archive.restore(it) }) : AndroidViewModel(application) {
    var busy by mutableStateOf(false); private set
    var restoring by mutableStateOf(false); private set
    var phase by mutableStateOf(""); private set
    var message by mutableStateOf(archive.lastMessage); private set
    var failed by mutableStateOf(false); private set
    var prepared by mutableStateOf<PreparedBackup?>(null); private set
    private var job: Job? = null

    private fun start(action: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false; message = null
        job = viewModelScope.launch {
            try { action() }
            catch (_: CancellationException) {
                withContext(Dispatchers.IO + NonCancellable) { discard() }
                message = "Operazione annullata. I dati locali sono conservati."
            }
            catch (failure: Exception) {
                failed = true
                message = (failure as? BackupFailure)?.message ?: "Non riesco a completare il backup. Controlla il file, i permessi e lo spazio; i dati precedenti sono conservati."
            } catch (_: OutOfMemoryError) {
                failed = true
                message = "Il backup è troppo complesso per la memoria disponibile. Chiudi le altre app e riprova; i dati precedenti sono conservati."
            } finally {
                archive.cleanupWarning?.let { warning ->
                    if (message?.contains(warning) != true) message = listOfNotNull(message, warning).joinToString(" ")
                }
                busy = false; restoring = false
            }
        }
    }

    fun export(uri: Uri) = start {
        withContext(Dispatchers.IO) {
            try {
                val output = application.contentResolver.openOutputStream(uri, "w") ?: throw BackupFailure("Destinazione non accessibile. Scegli un altro file.")
                val result = output.use { archive.export(it) { phase = it } }
                message = "Backup creato: ${result.summary}."
            } catch (failure: Throwable) {
                // CreateDocument returns a newly chosen document. Remove an incomplete export when its provider permits it.
                withContext(NonCancellable) {
                    if (!runCatching { DocumentsContract.deleteDocument(application.contentResolver, uri) }.getOrDefault(false)) {
                        archive.cleanupWarning = "Il file di esportazione incompleto va eliminato dalla destinazione scelta."
                    }
                }
                throw failure
            }
        }
    }

    fun prepare(uri: Uri) = start {
        withContext(Dispatchers.IO) {
            discard()
            val input = application.contentResolver.openInputStream(uri) ?: throw BackupFailure("File non accessibile. Selezionalo di nuovo.")
            prepared = input.use { archive.prepare(it) { phase = it } }
        }
    }

    fun restore(onRestored: () -> Unit) {
        val pending = prepared ?: return
        start {
            restoring = true
            phase = "Ripristino dei dati e delle foto…"
            val result = withContext(NonCancellable) { restoreBackup(pending) }
            prepared = null
            archive.lastMessage = archive.lastMessage ?: "Ripristino completato: ${result.summary}."
            message = archive.lastMessage
            onRestored()
        }
    }

    fun cancel() { if (!restoring) job?.cancel() }
    fun dismissPreview() = start { withContext(Dispatchers.IO) { discard() }; message = "Importazione annullata. I dati locali sono conservati." }
    private fun discard() {
        prepared?.let { if (!it.discard()) throw BackupFailure("Non riesco a eliminare tutti i file temporanei. Libera spazio e riprova.") }
        prepared = null
    }
    override fun onCleared() {
        val pending = prepared
        if (pending != null && !pending.consumed) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { pending.discard() }
    }
}

@Composable
internal fun BackupSection() {
    val activity = LocalActivity.current as ComponentActivity
    val application = activity.application as FaunaviaApplication
    val model = viewModel<BackupViewModel>(viewModelStoreOwner = activity, key = "local-backup",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupViewModel(application, application.localBackup, application::restoreBackup) as T
        })
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(model::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::prepare) }
    BackupControls(model, onExport = { export.launch("Faunavia-backup-${LocalDate.now()}.zip") },
        onImport = { import.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        onRestored = {
            // Clear every old editor and retained analysis only after the local transaction has committed.
            activity.startActivity(Intent(activity, MainActivity::class.java).putExtra("backup-restored", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        })
}

@Composable
internal fun BackupControls(model: BackupViewModel, onExport: () -> Unit, onImport: () -> Unit, onRestored: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("backup-section")) {
        Text("Backup e ripristino", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Conserva diario, foto, viaggi, uscite, luoghi, risultati salvati, desideri e impostazioni in un unico archivio.", color = FaunaviaColors.Muted)
        Text("Il file contiene i tuoi ricordi e le posizioni personali. Scegli dove conservarlo. Limite: 2 GB e 10.000 file foto; serve spazio temporaneo sul dispositivo.",
            style = MaterialTheme.typography.bodyMedium, color = FaunaviaColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, enabled = !model.busy && model.prepared == null, modifier = Modifier.testTag("backup-export")) { Text("Esporta backup") }
            OutlinedButton(onClick = onImport, enabled = !model.busy && model.prepared == null, modifier = Modifier.testTag("backup-import")) { Text("Importa backup") }
        }
        model.message?.let { Text(it, color = if (model.failed) FaunaviaColors.Error else FaunaviaColors.Ink, modifier = Modifier.testTag("backup-message")) }
    }
    if (model.busy) AlertDialog(onDismissRequest = {}, title = { Text(if (model.restoring) "Ripristino in corso" else "Backup in corso") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(model.phase, modifier = Modifier.testTag("backup-progress"))
            if (model.restoring) Text("Attendi il completamento prima di usare il diario.")
        } }, confirmButton = {}, dismissButton = {
            if (!model.restoring) TextButton(onClick = model::cancel, modifier = Modifier.testTag("backup-cancel-work")) { Text("Annulla") }
        })
    if (!model.busy) model.prepared?.let { prepared ->
        AlertDialog(onDismissRequest = { model.dismissPreview() }, title = { Text("Ripristinare questo backup?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Archivio del ${prepared.preview.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
                Text(prepared.preview.summary, modifier = Modifier.testTag("backup-preview"))
                Text("Dimensione dei dati: ${prepared.preview.bytes / (1024 * 1024)} MB")
                if (prepared.preview.schema == 8) Text("Questo archivio precedente non contiene lo storico di invio delle notifiche.")
                Text("${prepared.preview.counts["wishlist"] ?: 0} desideri; ${prepared.preview.counts["saved_trip_results"] ?: 0} risultati salvati. Impostazioni e collegamenti sono inclusi.")
                Text("I dati attuali verranno sostituiti. Per conservarli, annulla ed esporta prima un backup. L’archivio è stato verificato; un errore durante il ripristino conserva lo stato precedente.")
            } }, confirmButton = { Button(onClick = { model.restore(onRestored) }, modifier = Modifier.testTag("backup-confirm")) { Text("Sostituisci i dati") } },
            dismissButton = { TextButton(onClick = { model.dismissPreview() }, modifier = Modifier.testTag("backup-dismiss")) { Text("Annulla") } })
    }
}
