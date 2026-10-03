package it.faunavia.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.faunavia.domain.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun parseReminderTime(text: String): LocalTime? =
    text.takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

@Composable
internal fun SettingsScreen(preferences: ReminderPreferences, notifications: DailySummaryNotifications,
    onSummary: (SummaryLink) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<AppSettings?>(null) }
    var time by rememberSaveable { mutableStateOf("20:00") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(notifications.allowed()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf(false) }
    val parsed = parseReminderTime(time)

    fun update(current: AppSettings) {
        settings = current
        allowed = notifications.allowed()
        if (!dirty) time = current.reminderTime.toString()
    }
    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try { update(preferences.reconcile()); error = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true; message = "Non riesco a leggere le preferenze. Riprova." }
            finally { busy = false }
        }
    }
    fun save(enabled: Boolean) {
        val validTime = parseReminderTime(time) ?: settings?.reminderTime?.takeIf { !enabled }
        if (validTime == null) { busy = false; message = "Inserisci un orario valido, ad esempio 20:30."; return }
        busy = true
        scope.launch {
            try {
                val current = preferences.save(enabled, validTime)
                dirty = false
                error = false
                update(current)
                message = if (enabled && !current.reminderEnabled) "Le notifiche sono bloccate nelle impostazioni Android."
                    else "Preferenze salvate."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true; message = "Non riesco a completare la pianificazione. Riprova a salvare." }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save(true) else {
            busy = false
            allowed = false
            message = "Permesso negato: il riepilogo resta disattivato. Il diario è sempre disponibile."
        }
    }
    LaunchedEffect(preferences) { refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().testTag("screen-settings")) {
        FaunaviaHeader("Impostazioni", "screen-title-settings")
        LazyColumn(Modifier.weight(1f).testTag("settings-content"), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Il riepilogo della giornata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Una notifica con gli avvistamenti identificati registrati oggi. Le bozze da identificare sono escluse.",
                    modifier = Modifier.padding(top = 8.dp), color = FaunaviaColors.Muted)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Riepilogo giornaliero", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = settings?.reminderEnabled == true,
                        enabled = settings != null && !busy && (settings?.reminderEnabled == true || parsed != null),
                        modifier = Modifier.testTag("reminder-enabled"), onCheckedChange = { enabled ->
                            message = null
                            if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                busy = true
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else save(enabled)
                        })
                }
            }
            item {
                OutlinedTextField(time, onValueChange = { time = it; dirty = true; message = null },
                    label = { Text("Orario indicativo (HH:mm)") }, singleLine = true, enabled = !busy,
                    isError = parsed == null, modifier = Modifier.fillMaxWidth().testTag("reminder-time"),
                    supportingText = { Text(if (parsed == null) "Usa un orario da 00:00 a 23:59."
                        else "Fuso del dispositivo: ${ZoneId.systemDefault().id}") })
                Text("Android può ritardare il riepilogo per risparmiare energia. Viene inviato solo se ci sono avvistamenti, dall’orario scelto fino a fine giornata.",
                    color = FaunaviaColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Button(onClick = { save(settings?.reminderEnabled == true) }, enabled = settings != null && !busy && parsed != null,
                    modifier = Modifier.testTag("reminder-save")) { Text("Salva orario") }
                Text(if (busy) "Aggiornamento in corso…" else if (settings?.reminderEnabled == true) "Riepilogo attivo" else "Riepilogo disattivato",
                    modifier = Modifier.testTag("reminder-status"))
            }
            if (!allowed) item {
                Text("Le notifiche non sono abilitate. Puoi attivarle con l’interruttore o nelle impostazioni Android.", color = FaunaviaColors.Muted)
                TextButton(onClick = {
                    try { notifications.openSettings() }
                    catch (_: Exception) { message = "Apri Impostazioni Android → App → Faunavia → Notifiche." }
                }, modifier = Modifier.testTag("reminder-system-settings")) { Text("Apri impostazioni Android") }
            }
            message?.let { text -> item {
                Text(text, color = if (error) FaunaviaColors.Error else FaunaviaColors.Ink, modifier = Modifier.testTag("reminder-message"))
                if (error) TextButton(onClick = { if (settings == null) refresh() else save(settings?.reminderEnabled == true) },
                    enabled = !busy, modifier = Modifier.testTag("reminder-retry")) { Text("Riprova") }
            } }
            item {
                HorizontalDivider()
                TextButton(onClick = { onSummary(SummaryLink(LocalDate.now(), ZoneId.systemDefault())) },
                    modifier = Modifier.testTag("summary-today")) { Text("Apri riepilogo di oggi") }
                Text("Specie e foto restano sul dispositivo e si consultano anche senza connessione.", color = FaunaviaColors.Muted)
            }
            item {
                HorizontalDivider()
                BackupSection()
            }
        }
    }
}
