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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
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
import it.faunavia.taxonomy.MINIMUM_TAXON_QUERY_LENGTH
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchEntry
import it.faunavia.taxonomy.TaxonomySearchOrigin
import it.faunavia.taxonomy.TaxonomySearchResult
import it.faunavia.taxonomy.TaxonomyFailure
import it.faunavia.taxonomy.normalizeQuery
import it.faunavia.domain.CatalogueRepository
import it.faunavia.domain.DiaryRepository
import it.faunavia.domain.RouteRepository
import it.faunavia.domain.TripRepository
import it.faunavia.domain.UnidentifiedRepository
import it.faunavia.domain.WishlistRepository
import it.faunavia.domain.Taxon
import it.faunavia.exploration.SuggestionView
import it.faunavia.route.RouteImportService
import it.faunavia.exploration.ExplorationService
import it.faunavia.exploration.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    HOME("home", "Home", "Punto di partenza per posizione e itinerari."),
    ROUTES("routes", "Percorsi", "Importa GPX o GeoJSON e prepara un corridoio di analisi locale."),
    RESULTS("results", "Risultati", "Evidenze documentate e plausibili resteranno separate."),
    TRIPS("trips", "Viaggi", "Destinazioni, uscite e ricordi personali salvati localmente."),
    DIARY("diary", "Diario", "Gli avvistamenti locali saranno disponibili offline."),
    CATALOGUE("catalogue", "Catalogo", "Ricerca di taxa Animalia accettati, con cache locale dei selezionati."),
    SETTINGS("settings", "Impostazioni", "Preferenze locali, cache e notifiche."),
}

internal val PrimaryDestinations = listOf(
    AppDestination.TRIPS, AppDestination.DIARY, AppDestination.CATALOGUE, AppDestination.SETTINGS,
)

private val FaunaviaBackground = Color(0xFFF4F7F2)
private val FaunaviaGreen = Color(0xFF1F5C3F)
private val FaunaviaNavigation = Color(0xFFE4EEE5)

@Composable
fun FaunaviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FaunaviaGreen,
            onPrimary = Color.White,
            background = FaunaviaBackground,
            surface = FaunaviaBackground,
            surfaceVariant = FaunaviaNavigation,
        ),
        content = content,
    )
}

@Composable
fun FaunaviaApp(
    taxonomySearch: TaxonomySearch? = null,
    diaryRepository: DiaryRepository? = null,
    catalogueRepository: CatalogueRepository? = null,
    routeRepository: RouteRepository? = null,
    routeImportService: RouteImportService? = null,
    explorationService: ExplorationService? = null,
    placeSearch: PlaceSearch? = null,
    mapAdapter: ExplorationMapAdapter = MapLibreExplorationMapAdapter,
    tripRepository: TripRepository? = null,
    unidentifiedRepository: UnidentifiedRepository? = null,
    wishlistRepository: WishlistRepository? = null,
) {
    val application = LocalContext.current.applicationContext as FaunaviaApplication
    val catalogueSearch = taxonomySearch ?: application.taxonomySearch
    val diary = diaryRepository ?: application.repositories.diary
    val catalogue = catalogueRepository ?: application.repositories.catalogue
    val routes = routeRepository ?: application.repositories.routes
    val routeImporter = routeImportService ?: application.routeImportService
    val explorer = explorationService ?: application.explorationService
    val geocoder = placeSearch ?: application.placeSearch
    val trips = tripRepository ?: application.repositories.trips
    val unidentified = unidentifiedRepository ?: application.repositories.unidentified
    val wishes = wishlistRepository ?: application.repositories.wishlist
    var observationSeed by remember { mutableStateOf<DiaryPrefill?>(null) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigate(destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(AppDestination.TRIPS.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(FaunaviaGreen)
            .testTag("app-root"),
        bottomBar = {
            DestinationBar(
                selectedRoute = if (currentDestination?.route in listOf("results", "routes")) "trips" else currentDestination?.route,
                onDestinationSelected = ::navigate,
            )
        },
        containerColor = FaunaviaGreen,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(FaunaviaBackground)) {
            if (currentDestination?.route in listOf("results", "routes")) {
                TextButton(onClick = { navigate(AppDestination.TRIPS) }, modifier = Modifier.testTag("back-to-trips")) {
                    Text("Torna ai viaggi")
                }
            }
            NavHost(
                navController = navController,
                startDestination = AppDestination.TRIPS.route,
                modifier = Modifier.weight(1f),
            ) {
                AppDestination.entries.forEach { destination ->
                    composable(destination.route) {
                        when (destination) {
                            AppDestination.CATALOGUE -> CatalogueScreen(catalogueSearch, wishlist = wishes,
                                catalogue = catalogue, diary = diary)
                            AppDestination.DIARY -> DiaryScreen(diary, catalogue, catalogueSearch,
                                unidentifiedRepository = unidentified, tripRepository = trips, prefill = observationSeed,
                                onExitEditor = { observationSeed = null })
                            AppDestination.ROUTES -> RouteScreen(routes, routeImporter)
                            AppDestination.RESULTS -> ExplorationScreen(explorer, geocoder, catalogue, mapAdapter, routes,
                                onSaw = { taxon -> scope.launch {
                                    observationSeed = runCatching { withContext(Dispatchers.IO) {
                                        observationPrefill(taxon.id, taxon.scientificName, catalogue, catalogueSearch)
                                    } }.getOrElse { DiaryPrefill(query = taxon.scientificName) }
                                    navController.navigate(AppDestination.DIARY.route) { launchSingleTop = true }
                                } })
                            AppDestination.TRIPS -> TripsScreen(trips, diary, unidentified, catalogue, catalogueSearch,
                                geocoder, explorer, routes, mapAdapter,
                                onExplore = { navigate(AppDestination.RESULTS) },
                                onImportRoute = { navigate(AppDestination.ROUTES) }, routing = application.tripRouting,
                                wishlist = wishes)
                            else -> PlaceholderScreen(destination)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(destination: AppDestination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaunaviaBackground)
            .testTag("screen-${destination.route}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(FaunaviaGreen)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = "Faunavia",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = destination.label,
                    modifier = Modifier.testTag("screen-title-${destination.route}"),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = destination.description,
                color = Color(0xFF26382E),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Placeholder F1 — nessuna rete o dato personale richiesto.",
                color = Color(0xFF52675A),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun CatalogueScreen(
    taxonomySearch: TaxonomySearch,
    debounceMillis: Long = 350,
    wishlist: WishlistRepository? = null,
    catalogue: CatalogueRepository? = null,
    diary: DiaryRepository? = null,
) {
    var showWishes by rememberSaveable { mutableStateOf(false) }
    var selectedTaxon by remember { mutableStateOf<Taxon?>(null) }
    require(debounceMillis >= 0) { "The catalogue debounce cannot be negative." }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<TaxonomySearchResult>(TaxonomySearchResult.AwaitingQuery()) }
    var loading by remember { mutableStateOf(false) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionError by rememberSaveable { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    if (showWishes && wishlist != null && catalogue != null && diary != null) {
        PersonalSuggestionsScreen(wishlist, catalogue, diary, taxonomySearch, initialView = SuggestionView.WISHLIST,
            onBack = { showWishes = false })
        return
    }

    LaunchedEffect(query, refresh) {
        val generation = ++searchGeneration
        searchError = null
        if (normalizeQuery(query).length < MINIMUM_TAXON_QUERY_LENGTH) {
            loading = false
            result = TaxonomySearchResult.AwaitingQuery()
            return@LaunchedEffect
        }
        loading = true
        try {
            delay(debounceMillis)
            result = withContext(Dispatchers.IO) { taxonomySearch.search(query) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == searchGeneration) searchError = "Non riesco a completare la ricerca. Riprova; le specie salvate sono conservate."
        } finally {
            if (generation == searchGeneration) loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaunaviaBackground)
            .testTag("screen-catalogue"),
    ) {
        CatalogueHeader()
        wishlist?.let { TextButton(onClick = { showWishes = true }, modifier = Modifier.testTag("catalogue-wishlist")) { Text("Lista: vorrei vederlo") } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Cerca un animale",
                color = Color(0xFF26382E),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Nome comune, scientifico o sinonimo. Salviamo solo il taxon accettato.",
                color = Color(0xFF52675A),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalogue-query"),
                label = { Text("Cerca, ad esempio merlo") },
                singleLine = true,
            )
            selectedName?.let { name ->
                Text(
                    text = "Selezionato: $name. Sarà disponibile anche offline.",
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("catalogue-selected"),
                    color = FaunaviaGreen,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            selectionError?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("catalogue-selection-error"),
                    color = Color(0xFF9B1C1C),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            selectedTaxon?.let { taxon -> wishlist?.let { store ->
                TextButton(onClick = { scope.launch {
                    runCatching { store.add(taxon.id) }
                        .onSuccess { selectedName = "${taxon.scientificName} · aggiunto ai desideri"; selectionError = null }
                        .onFailure { selectionError = "Non riesco a salvare il desiderio. Riprova." }
                } }, modifier = Modifier.testTag("catalogue-add-wish")) { Text("Vorrei vederlo") }
            } }
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Text(
                    text = "Ricerca in corso…",
                    modifier = Modifier.testTag("catalogue-loading"),
                    color = Color(0xFF52675A),
                )
                searchError != null -> Column {
                    Text(requireNotNull(searchError), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("catalogue-error"))
                    Button(onClick = { refresh++ }, modifier = Modifier.testTag("catalogue-retry")) { Text("Riprova") }
                }
                else -> CatalogueSearchResult(
                    result = result,
                    onRetry = { refresh++ },
                    onSelect = { entry ->
                        scope.launch {
                            selectionError = null
                            runCatching { withContext(Dispatchers.IO) { taxonomySearch.select(entry) } }
                                .onSuccess { selectedName = entry.taxon.scientificName; selectedTaxon = entry.taxon }
                                .onFailure { selectionError = "Non riesco a salvare la selezione. Riprova." }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogueHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(FaunaviaGreen)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text("Faunavia", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "Catalogo",
                modifier = Modifier.testTag("screen-title-catalogue"),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CatalogueSearchResult(
    result: TaxonomySearchResult,
    onRetry: () -> Unit,
    onSelect: (TaxonomySearchEntry) -> Unit,
) {
    when (result) {
        is TaxonomySearchResult.AwaitingQuery -> Text(
            text = "Inserisci almeno ${result.minimumLength} caratteri.",
            modifier = Modifier.testTag("catalogue-prompt"),
            color = Color(0xFF52675A),
        )
        is TaxonomySearchResult.Empty -> Text(
            text = if (result.origin == TaxonomySearchOrigin.OFFLINE_SELECTED) {
                "Offline: non ci sono taxa selezionati che corrispondono alla ricerca."
            } else {
                "Nessun taxon Animalia accettato trovato."
            },
            modifier = Modifier.testTag("catalogue-empty"),
            color = Color(0xFF52675A),
        )
        is TaxonomySearchResult.Failure -> {
            Column(modifier = Modifier.testTag("catalogue-error")) {
                Text(
                    text = "${taxonomyFailureMessage(result.reason)} I taxa già selezionati restano consultabili offline.",
                    color = Color(0xFF9B1C1C),
                )
                Button(onClick = onRetry, modifier = Modifier.testTag("catalogue-retry")) { Text("Riprova") }
                CatalogueEntries(result.cachedEntries, onSelect)
            }
        }
        is TaxonomySearchResult.Results -> {
            if (result.origin == TaxonomySearchOrigin.OFFLINE_SELECTED) {
                Text(
                    text = "Offline: risultati già selezionati su questo dispositivo.",
                    modifier = Modifier.testTag("catalogue-offline"),
                    color = Color(0xFF52675A),
                )
            }
            CatalogueEntries(result.entries, onSelect)
        }
    }
}

internal fun taxonomyFailureMessage(reason: TaxonomyFailure): String = when (reason) {
    TaxonomyFailure.TIMEOUT -> "Il catalogo sta impiegando troppo tempo a rispondere. Riprova."
    TaxonomyFailure.NETWORK -> "Non riesco a raggiungere il catalogo. Controlla la connessione e riprova."
    TaxonomyFailure.MALFORMED_RESPONSE -> "Il catalogo ha restituito una risposta non leggibile. Riprova."
}

@Composable
private fun CatalogueEntries(
    entries: List<TaxonomySearchEntry>,
    onSelect: (TaxonomySearchEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("catalogue-results"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.taxon.id }) { entry ->
            Button(
                onClick = { onSelect(entry) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalogue-result-${entry.taxon.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF26382E),
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(entry.taxon.scientificName, fontWeight = FontWeight.Bold)
                    Text(entry.taxon.commonName ?: "Nome comune non disponibile")
                    Text("${entry.taxon.rank} · ${entry.taxon.provenance.recordId}")
                    Text(
                        text = "Anteprima fotografica non disponibile o senza licenza compatibile.",
                        color = Color(0xFF52675A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationBar(
    selectedRoute: String?,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(FaunaviaNavigation)
            .navigationBarsPadding()
            .testTag("destination-bar"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(PrimaryDestinations, key = { it.route }) { destination ->
            val selected = currentRouteMatches(selectedRoute, destination)
            Button(
                onClick = { onDestinationSelected(destination) },
                modifier = Modifier.testTag("nav-${destination.route}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) FaunaviaGreen else Color.White,
                    contentColor = if (selected) Color.White else FaunaviaGreen,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(destination.label)
                }
            }
        }
    }
}

internal fun currentRouteMatches(
    selectedRoute: String?,
    destination: AppDestination,
): Boolean = selectedRoute == destination.route
