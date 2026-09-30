package it.faunavia.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.faunavia.domain.GeoPoint
import it.faunavia.domain.Route
import it.faunavia.domain.RouteRepository
import it.faunavia.route.MAX_ROUTE_DOCUMENT_CHARS
import it.faunavia.route.RouteAnalysis
import it.faunavia.route.RouteAnalysisConfig
import it.faunavia.route.RouteImportErrorCode
import it.faunavia.route.RouteImportException
import it.faunavia.route.RouteImportOutcome
import it.faunavia.route.RouteImportService
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RouteInk = Color(0xFF26382E)
private val RouteMuted = Color(0xFF52675A)
private val RouteError = Color(0xFF9B1C1C)
private val RouteSurface = Color.White

private data class SavedRouteAnalysis(
    val route: Route,
    val analysis: RouteAnalysis?,
    val failure: String? = null,
)

@Composable
internal fun RouteScreen(
    repository: RouteRepository,
    importer: RouteImportService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var radiusKilometers by rememberSaveable { mutableStateOf("1") }
    var samplingMeters by rememberSaveable { mutableStateOf("500") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var latest by remember { mutableStateOf<RouteImportOutcome?>(null) }
    var savedRoutes by remember { mutableStateOf<List<SavedRouteAnalysis>>(emptyList()) }

    val config = routeConfig(radiusKilometers, samplingMeters)
    LaunchedEffect(refresh, config) {
        if (config != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.list().map { route ->
                        runCatching { importer.analyze(route, config) }
                            .fold(
                                onSuccess = { analysis -> SavedRouteAnalysis(route, analysis) },
                                onFailure = { failure -> SavedRouteAnalysis(route, null, routeErrorMessage(failure)) },
                            )
                    }
                }
            }.onSuccess { savedRoutes = it }
                .onFailure { error = "Non riesco a leggere i percorsi locali." }
        }
    }

    fun accept(action: suspend (RouteAnalysisConfig) -> RouteImportOutcome) {
        val validConfig = config
        if (validConfig == null) {
            error = "Usa un raggio tra 0,05 e 20 km e un intervallo tra 10 e 10.000 m."
            return
        }
        importing = true
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action(validConfig) } }
                .onSuccess { outcome ->
                    latest = outcome
                    refresh++
                }
                .onFailure { failure -> error = routeErrorMessage(failure) }
            importing = false
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            accept { validConfig ->
                val document = readRouteDocument(context, uri)
                importer.importAndSave(document.name, document.content, validConfig)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F2))
            .testTag("screen-routes"),
    ) {
        RouteHeader()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("route-content"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "Prepara l’area da analizzare",
                        color = RouteInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Il file resta sul dispositivo. Salviamo la geometria normalizzata, non il documento originale, e non contattiamo provider in questa fase.",
                        color = RouteMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                AnalysisConfiguration(
                    radiusKilometers = radiusKilometers,
                    onRadiusChange = { radiusKilometers = it },
                    samplingMeters = samplingMeters,
                    onSamplingChange = { samplingMeters = it },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Importa un percorso", color = RouteInk, fontWeight = FontWeight.Bold)
                    Text(
                        "Formati accettati: GPX e GeoJSON, inclusi percorsi con più segmenti.",
                        color = RouteMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            documentLauncher.launch(
                                arrayOf("application/gpx+xml", "application/geo+json", "application/json", "text/xml", "application/xml"),
                            )
                        },
                        enabled = !importing,
                        modifier = Modifier.testTag("route-import"),
                    ) {
                        Text(if (importing) "Analisi in corso…" else "Scegli GPX o GeoJSON")
                    }
                }
            }
            item {
                ManualLocation(
                    latitude = latitude,
                    onLatitudeChange = { latitude = it },
                    longitude = longitude,
                    onLongitudeChange = { longitude = it },
                    enabled = !importing,
                    onAnalyze = {
                        val point = runCatching {
                            GeoPoint(
                                latitude.trim().replace(',', '.').toDouble(),
                                longitude.trim().replace(',', '.').toDouble(),
                            )
                        }.getOrNull()
                        if (point == null) {
                            error = "Inserisci latitudine e longitudine WGS84 valide."
                        } else {
                            accept { validConfig -> importer.saveLocation("Posizione ${point.latitude}, ${point.longitude}", point, validConfig) }
                        }
                    },
                )
            }
            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = RouteError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("route-error"),
                    )
                }
            }
            latest?.let { outcome -> item { AnalysisSummary(outcome.analysis, "Ultima analisi") } }
            item { HorizontalDivider(color = Color(0xFFCCD8CE)) }
            item {
                Column {
                    Text("Percorsi locali", color = RouteInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (savedRoutes.isEmpty()) "Nessun percorso salvato. Importa un file o analizza una posizione."
                        else "Disponibili offline e pronti per i provider delle fasi successive.",
                        color = RouteMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("route-empty"),
                    )
                }
            }
            items(savedRoutes, key = { it.route.id }) { saved ->
                SavedRoute(saved)
            }
        }
    }
}

@Composable
private fun RouteHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(Color(0xFF1F5C3F))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text("Faunavia", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "Percorsi",
                modifier = Modifier.testTag("screen-title-routes"),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AnalysisConfiguration(
    radiusKilometers: String,
    onRadiusChange: (String) -> Unit,
    samplingMeters: String,
    onSamplingChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Parametri", color = RouteInk, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = radiusKilometers,
            onValueChange = onRadiusChange,
            modifier = Modifier.fillMaxWidth().testTag("route-radius"),
            label = { Text("Raggio del corridoio (km)") },
            supportingText = { Text("Predefinito: 1 km") },
            singleLine = true,
        )
        OutlinedTextField(
            value = samplingMeters,
            onValueChange = onSamplingChange,
            modifier = Modifier.fillMaxWidth().testTag("route-sampling"),
            label = { Text("Distanza massima tra campioni (m)") },
            supportingText = { Text("Predefinita: 500 m") },
            singleLine = true,
        )
    }
}

@Composable
private fun ManualLocation(
    latitude: String,
    onLatitudeChange: (String) -> Unit,
    longitude: String,
    onLongitudeChange: (String) -> Unit,
    enabled: Boolean,
    onAnalyze: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Oppure analizza una posizione", color = RouteInk, fontWeight = FontWeight.Bold)
        Text("Coordinate WGS84 nel pilot europeo EPSG:3035.", color = RouteMuted, style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = latitude,
                onValueChange = onLatitudeChange,
                modifier = Modifier.weight(1f).testTag("route-latitude"),
                label = { Text("Latitudine") },
                singleLine = true,
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = longitude,
                onValueChange = onLongitudeChange,
                modifier = Modifier.weight(1f).testTag("route-longitude"),
                label = { Text("Longitudine") },
                singleLine = true,
            )
        }
        Button(
            onClick = onAnalyze,
            enabled = enabled,
            modifier = Modifier.testTag("route-analyze-location"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF365E49)),
        ) { Text("Analizza coordinate") }
    }
}

@Composable
private fun AnalysisSummary(analysis: RouteAnalysis, heading: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RouteSurface, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFCCD8CE), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .testTag("route-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(heading, color = RouteInk, fontWeight = FontWeight.Bold)
        Text(analysis.routeName, color = RouteInk, style = MaterialTheme.typography.titleMedium)
        Text(
            "${formatKilometers(analysis.totalLengthMeters)} km · ${analysis.samples.size} campioni · ${analysis.cells.size} celle · ${analysis.queryChunks.size} porzioni query",
            color = RouteMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${analysis.projection} · ${analysis.fingerprint.take(16)}…",
            modifier = Modifier.testTag("route-projection"),
            color = RouteMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (analysis.warnings.isNotEmpty()) {
            Text(
                "Normalizzazione: ${analysis.warnings.sumOf { it.count }} elementi corretti.",
                color = RouteMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SavedRoute(saved: SavedRouteAnalysis) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("route-saved-${saved.route.id}"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(saved.route.name, color = RouteInk, fontWeight = FontWeight.Bold)
        Text(
            saved.analysis?.let { analysis ->
                "${saved.route.source.name} · ${saved.route.segments.size} segmenti · ${formatKilometers(analysis.totalLengthMeters)} km"
            } ?: "${saved.route.source.name} · ${saved.route.segments.size} segmenti",
            color = RouteMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        saved.analysis?.let { analysis ->
            Text("Fingerprint ${analysis.fingerprint.take(16)}…", color = RouteMuted, style = MaterialTheme.typography.bodySmall)
        }
        saved.failure?.let { failure ->
            Text(failure, color = RouteError, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun routeConfig(radiusKilometers: String, samplingMeters: String): RouteAnalysisConfig? {
    val radius = radiusKilometers.trim().replace(',', '.').toDoubleOrNull()?.times(1_000.0) ?: return null
    val sampling = samplingMeters.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return runCatching { RouteAnalysisConfig(corridorRadiusMeters = radius, samplingIntervalMeters = sampling) }.getOrNull()
}

private fun routeErrorMessage(error: Throwable): String = when (error) {
    is RouteImportException -> when (error.code) {
        RouteImportErrorCode.EMPTY_DOCUMENT -> "Il file è vuoto."
        RouteImportErrorCode.DOCUMENT_TOO_LARGE -> "Il file supera il limite di 5 MB."
        RouteImportErrorCode.UNSUPPORTED_FORMAT -> "Formato non supportato: scegli GPX o GeoJSON."
        RouteImportErrorCode.UNSUPPORTED_CRS -> "Il GeoJSON deve usare coordinate WGS84."
        RouteImportErrorCode.UNSUPPORTED_GEOMETRY -> "Il GeoJSON deve contenere Point, LineString o MultiLineString."
        RouteImportErrorCode.MALFORMED_DOCUMENT -> "Il file non è un GPX o GeoJSON valido."
        RouteImportErrorCode.EMPTY_ROUTE -> "Il file non contiene un percorso utilizzabile."
        RouteImportErrorCode.INVALID_COORDINATE -> "Il percorso contiene coordinate WGS84 non valide."
        RouteImportErrorCode.ANTIMERIDIAN_UNSUPPORTED -> "Il pilot europeo non supporta percorsi attraverso l’antimeridiano."
        RouteImportErrorCode.OUTSIDE_EPSG_3035_AREA -> "Il percorso è fuori dall’area europea supportata da EPSG:3035."
    }
    is SecurityException -> "Faunavia non può leggere il file selezionato."
    is IOException -> "Non riesco a leggere il file selezionato."
    else -> "Non riesco ad analizzare il percorso. Riprova."
}

private data class SelectedRouteDocument(val name: String, val content: String)

private fun readRouteDocument(context: Context, uri: Uri): SelectedRouteDocument {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "percorso"
    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        val buffer = CharArray(8_192)
        val result = StringBuilder()
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            result.append(buffer, 0, count)
            if (result.length > MAX_ROUTE_DOCUMENT_CHARS) {
                throw RouteImportException(RouteImportErrorCode.DOCUMENT_TOO_LARGE, "The route document exceeds the text limit.")
            }
        }
        result.toString()
    } ?: throw IOException("The selected route could not be opened.")
    return SelectedRouteDocument(name, content)
}

private fun formatKilometers(meters: Double): String = String.format(Locale.ITALIAN, "%.2f", meters / 1_000.0)
