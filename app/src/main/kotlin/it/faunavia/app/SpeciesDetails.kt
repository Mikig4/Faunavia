package it.faunavia.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.plausibility.SeasonAssessment
import it.faunavia.plausibility.SeasonDataQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

internal fun seasonSummary(season: SeasonAssessment?): String {
    val months = season?.activeMonths.orEmpty().sorted().joinToString { Month.of(it).getDisplayName(TextStyle.FULL, Locale.ITALIAN) }
    return when (season?.quality) {
    null, SeasonDataQuality.UNAVAILABLE -> "Dato non disponibile"
    SeasonDataQuality.INSTITUTIONAL -> "$months · fonte istituzionale"
    SeasonDataQuality.DERIVED_OCCURRENCES -> "Segnalazioni in $months · segnale debole dalle osservazioni"
    }
}

internal fun savedSeasonSummary(value: String): String = value
    .replace("DERIVED_OCCURRENCES", "segnale debole dalle osservazioni")
    .replace("INSTITUTIONAL", "fonte istituzionale")
    .replace("UNAVAILABLE", "dato non disponibile")

/** Compact body shared by research, trips and suggestions; scientific fallback is immediate. */
@Composable
internal fun EssentialSpeciesDetails(id: String, scientificName: String, catalogue: CatalogueRepository,
    seasonText: String = "Dato non disponibile") {
    val lookup = LocalSpeciesMetadata.current
    var localTaxon by remember(id) { mutableStateOf<Taxon?>(null) }
    var profile by remember(id) { mutableStateOf<SpeciesProfile?>(null) }
    var nameResult by remember(id, scientificName) { mutableStateOf<MetadataResult<SpeciesNameMetadata>?>(null) }
    var nameRefresh by rememberSaveable(id) { mutableIntStateOf(0) }
    var showDistribution by rememberSaveable(id, scientificName) { mutableStateOf(false) }
    var readFailed by remember(id) { mutableStateOf(false) }
    val presentation = PilotSpeciesPresentation.find(scientificName)
    val curated = PilotSuggestionProfiles.entries.firstOrNull { scientificIdentity(it.scientificName) == scientificIdentity(scientificName) }
    LaunchedEffect(id, scientificName, lookup, nameRefresh) {
        try {
            val loaded = withContext(Dispatchers.IO) { catalogue.taxon(id) to catalogue.profile(id) }
            localTaxon = loaded.first; profile = loaded.second; readFailed = false
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { readFailed = true }
        if (localTaxon?.commonName.isNullOrBlank() && presentation == null) {
            try { nameResult = withContext(Dispatchers.IO) { lookup.name(id, scientificName, nameRefresh > 0) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { nameResult = MetadataResult(null, "Nome comune non recuperabile. Riprova.") }
        }
    }
    val commonName = localTaxon?.commonName?.takeIf(String::isNotBlank) ?: presentation?.commonName
        ?: nameResult?.value?.commonName?.takeIf(String::isNotBlank)
    val scientificLabel = scientificName.trim().ifBlank { "Specie non identificata" }
    Text(commonName ?: scientificLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("species-name-$id"))
    if (commonName != null) Text(scientificLabel, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
    else if (nameResult != null) Text("Nome comune italiano non disponibile", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    nameResult?.warning?.let { TextButton(onClick = { nameRefresh++ }) { Text("Riprova nome comune") } }
    val habitats = profile?.habitats?.takeIf(List<String>::isNotEmpty) ?: curated?.profile?.habitats
    val habitat = habitats?.joinToString() ?: "Dato documentato non disponibile"
    TextButton(onClick = { showDistribution = true }, modifier = Modifier.fillMaxWidth().testTag("species-habitat-$id"), contentPadding = PaddingValues(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Habitat: $habitat")
            Text("Apri distribuzione nell’app", style = MaterialTheme.typography.labelSmall)
        }
    }
    Text("Stagionalità: $seasonText", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("species-season-$id"))
    SpeciesProfileAction(id, scientificName, catalogue, commonName, localTaxon?.provenance ?: nameResult?.value?.source)
    if (readFailed) Text("Scheda locale non disponibile; mostro le informazioni curate disponibili.", color = MaterialTheme.colorScheme.error)
    if (showDistribution) SpeciesDistributionDialog(id, scientificName, onClose = { showDistribution = false })
}

@Composable
internal fun SpeciesEvidenceDetails(tag: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(tag)) {
        Text(if (expanded) "Chiudi evidenze e fonti" else "Evidenze e fonti")
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
}

@Composable
internal fun SpeciesGeneralSources(scientificName: String, catalogue: CatalogueRepository? = null, id: String? = null,
    includeCurated: Boolean = true) {
    var profile by remember(id) { mutableStateOf<SpeciesProfile?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    val lookup = LocalSpeciesMetadata.current
    var nameResult by remember(id, scientificName) { mutableStateOf<MetadataResult<SpeciesNameMetadata>?>(null) }
    LaunchedEffect(id, catalogue, scientificName, lookup) {
        try { profile = withContext(Dispatchers.IO) { id?.let { catalogue?.profile(it) } }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { failed = true }
        try { id?.let { nameResult = withContext(Dispatchers.IO) { lookup.name(it, scientificName) } }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { failed = true }
    }
    profile?.provenance?.let {
        Text("Scheda locale: ${it.attribution} · ${it.source}\n${it.retrievedAt} · ${it.license} · ${it.quality} · ${it.version}", style = MaterialTheme.typography.bodySmall)
    }
    if (failed) Text("Fonti della scheda locale non disponibili. Riapri il dettaglio per riprovare.", color = MaterialTheme.colorScheme.error)
    nameResult?.value?.source?.let { SourceSummary(it) }
    if (nameResult?.stale == true) Text("Nome comune da cache non aggiornata.", style = MaterialTheme.typography.bodySmall)
    if (includeCurated) PilotSuggestionProfiles.entries.firstOrNull { scientificIdentity(it.scientificName) == scientificIdentity(scientificName) }?.let {
        Text("Motivazione curatoriale, non evidenza locale: ${it.profile.reason}", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("species-curation-reason-${id ?: scientificName}"))
        Text("Habitat generale: ${it.profile.provenance.attribution} · ${it.profile.provenance.source}\nConsultata ${it.profile.provenance.retrievedAt} · ${it.profile.provenance.license}\nQualità ${it.profile.provenance.quality} · ${it.profile.provenance.version}",
            style = MaterialTheme.typography.bodySmall)
    }
    PilotSpeciesPresentation.find(scientificName)?.distribution?.let {
        Text("Distribuzione generale, mappa ${it.mapDate}: ${it.source.attribution} · ${it.source.license}\n${it.source.source}\nConsultata ${it.source.retrievedAt} · ${it.source.quality} · ${it.source.version}\nNon è l'area del viaggio, né una conferma di presenza attuale. Apri Habitat per consultare la mappa e la legenda nell’app.",
            style = MaterialTheme.typography.bodySmall)
    }
}
