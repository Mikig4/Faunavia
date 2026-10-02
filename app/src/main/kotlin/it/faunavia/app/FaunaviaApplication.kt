package it.faunavia.app

import android.app.Application
import it.faunavia.domain.AppClock
import it.faunavia.local.LocalRepositories
import it.faunavia.occurrence.GbifOccurrenceProvider
import it.faunavia.occurrence.NnbOccurrenceProvider
import it.faunavia.occurrence.OccurrenceGateway
import it.faunavia.occurrence.UrlConnectionOccurrenceHttpClient
import it.faunavia.route.RouteImportService
import it.faunavia.exploration.ExplorationService
import it.faunavia.exploration.NominatimPlaceSearch
import it.faunavia.exploration.PlaceSearch
import it.faunavia.exploration.UrlConnectionPlaceHttpClient
import it.faunavia.exploration.OsrmTripRouting
import it.faunavia.exploration.UrlConnectionRoutingHttpClient
import it.faunavia.taxonomy.CatalogueTaxonSelectionStore
import it.faunavia.taxonomy.GbifTaxonomyProvider
import it.faunavia.taxonomy.TaxonomySearch
import it.faunavia.taxonomy.TaxonomySearchService
import it.faunavia.taxonomy.UrlConnectionGbifHttpClient

class FaunaviaApplication : Application() {
    private val clock = object : AppClock {
        override fun nowEpochMillis(): Long = System.currentTimeMillis()
    }

    val repositories by lazy {
        LocalRepositories.open(this, clock)
    }

    val taxonomySearch: TaxonomySearch by lazy {
        TaxonomySearchService(
            store = CatalogueTaxonSelectionStore(repositories.catalogue),
            provider = GbifTaxonomyProvider(UrlConnectionGbifHttpClient(), clock),
            clock = clock,
        )
    }

    val routeImportService by lazy {
        RouteImportService(clock, repositories.routes)
    }

    /** F6 composes the gateway here; F8 will render its explicit cache and failure states. */
    val occurrenceGateway by lazy {
        val http = UrlConnectionOccurrenceHttpClient()
        OccurrenceGateway(
            cache = repositories.occurrenceCache,
            providers = listOf(
                GbifOccurrenceProvider(http, clock),
                NnbOccurrenceProvider(http, clock),
            ),
            clock = clock,
        )
    }

    val explorationService by lazy { ExplorationService(occurrenceGateway) }
    val tripRouting by lazy { OsrmTripRouting(UrlConnectionRoutingHttpClient()) }

    val placeSearch: PlaceSearch by lazy {
        NominatimPlaceSearch(UrlConnectionPlaceHttpClient(), PlaceCachePreferences(this))
    }
}
