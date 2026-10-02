package it.faunavia.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.DiaryRepository
import it.faunavia.domain.Observation
import it.faunavia.domain.Taxon
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

private data class SummaryEntry(val observation: Observation, val taxon: Taxon?, val photoCount: Int)

@Composable
internal fun DailySummaryScreen(link: SummaryLink, diary: DiaryRepository, catalogue: CatalogueRepository,
    photos: MemoryPhotos, onBack: () -> Unit) {
    var entries by remember(link) { mutableStateOf<List<SummaryEntry>?>(null) }
    var failure by remember(link) { mutableStateOf(false) }
    var retry by remember(link) { mutableIntStateOf(0) }
    var gallery by rememberSaveable(link.date.toString(), link.zoneId.id) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(link, retry, gallery) {
        failure = false
        try {
            entries = diary.onDate(link.date, link.zoneId).map {
                SummaryEntry(it, catalogue.taxon(it.taxonId), diary.photos(it.id).size)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
    }
    gallery?.let { id -> PhotoGallery(id, photos, onClose = { gallery = null }) }
    Column(Modifier.fillMaxSize().testTag("screen-daily-summary")) {
        FaunaviaHeader("Riepilogo del giorno", "screen-title-daily-summary")
        LazyColumn(Modifier.weight(1f).testTag("summary-content"), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TextButton(onClick = onBack, modifier = Modifier.testTag("summary-back")) { Text("Vai al diario") }
                Text(link.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Data nel fuso ${link.zoneId.id}", color = FaunaviaColors.Muted)
            }
            when {
                failure -> item {
                    Text("Non riesco a leggere il riepilogo. Gli avvistamenti restano nel diario.", color = FaunaviaColors.Error)
                    Button(onClick = { retry++ }, modifier = Modifier.testTag("summary-retry")) { Text("Riprova") }
                }
                entries == null -> item { Text("Lettura degli avvistamenti…") }
                entries!!.isEmpty() -> item {
                    Text("Nessun avvistamento identificato in questa giornata. Registra un animale nel diario per ritrovarlo qui.",
                        modifier = Modifier.testTag("summary-empty"))
                }
                else -> {
                    item {
                        val count = entries!!.size
                        Text("${if (count == 1) "1 avvistamento" else "$count avvistamenti"} · ${entries!!.map { it.observation.taxonId }.distinct().size} specie",
                            modifier = Modifier.testTag("summary-count"), fontWeight = FontWeight.Medium)
                    }
                    items(entries!!, key = { it.observation.id }) { entry ->
                        val observation = entry.observation
                        Column(Modifier.fillMaxWidth().testTag("summary-observation-${observation.id}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.taxon?.commonName ?: entry.taxon?.scientificName ?: "Specie salvata",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            entry.taxon?.scientificName?.let { Text(it, color = FaunaviaColors.Muted) }
                            Text("${observation.observedAt.atZone(link.zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))} · Quantità: ${observation.quantity}")
                            if (observation.notes.isNotBlank()) Text(observation.notes)
                            TextButton(onClick = { gallery = observation.id }, modifier = Modifier.testTag("summary-photos-${observation.id}")) {
                                Text("Foto (${entry.photoCount})")
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
