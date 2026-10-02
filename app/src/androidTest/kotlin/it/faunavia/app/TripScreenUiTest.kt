package it.faunavia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.faunavia.domain.*
import it.faunavia.exploration.*
import it.faunavia.local.FaunaviaDatabase
import it.faunavia.local.LocalRepositories
import it.faunavia.route.RouteAnalysis
import it.faunavia.testing.FakeClock
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripScreenUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<RouteTestActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "f8b-ui.db"
    private lateinit var db: FaunaviaDatabase
    private lateinit var local: LocalRepositories
    private val provider = F8BProvider()
    private val trip = f8bTrip()
    private val fakeMap = object : ExplorationMapAdapter {
        @Composable override fun Render(analysis: RouteAnalysis) {
            Box(Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFDFEDE2)).testTag("trip-fake-map"))
        }
    }
    private val fakePersonalMap = object : PersonalMapAdapter {
        @Composable override fun Render(observations: List<Observation>) {
            androidx.compose.material3.Text("${observations.size} punti", modifier = Modifier.testTag("diary-fake-map"))
        }
    }

    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase(name)
        db = FaunaviaDatabase.open(context, name)
        local = LocalRepositories(db, FakeClock(f8bNow.toEpochMilli()))
        local.catalogue.saveTaxonWithAliases(f8bTaxon, listOf("Merlo"))
    }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun draftQuantityUsesSharedValidationAndAcceptsOuterSpaces() {
        composeRule.setContent { FaunaviaTheme { TestDiary() } }
        click("diary-list", "diary-new-draft")
        type("diary-editor", "diary-quantity", "0")
        click("diary-editor", "diary-save")
        composeRule.onNodeWithTag("diary-editor-error")
            .assertTextContains("La quantità deve essere tra 1 e $MAX_OBSERVATION_QUANTITY.")
        assertTrue(runBlocking { local.unidentified.list().isEmpty() })
        type("diary-editor", "diary-quantity", " 2 ")
        click("diary-editor", "diary-save")
        waitForNode("diary-draft-ui-memory")
        assertEquals(2, runBlocking { checkNotNull(local.unidentified.get("ui-memory")).input.quantity })
    }

    @Test fun freeStagesValidateDaysPersistAndUseTheSelectedLegForMapsAndEvidence() {
        val planned = trip.copy(departure = trip.destination.copy(name = "Partenza", center = GeoPoint(45.1, 9.1)), route = testTripRoute())
        runBlocking { local.trips.save(planned) }
        var opened: Pair<GeoPoint, GeoPoint>? = null
        show(openDirections = { start, end -> opened = start to end; true })
        selectTrip()
        click("trip-content", "trip-stages-edit")
        click("stage-editor", "stage-add")
        click("stage-editor", "stage-1-manual-toggle")
        type("stage-editor", "stage-1-place-name", "Como")
        type("stage-editor", "stage-1-latitude", "45.2")
        type("stage-editor", "stage-1-longitude", "9.2")
        click("stage-editor", "stage-1-confirm-coordinates")
        click("stage-editor", "stage-plan-1")
        waitForNode("stage-choice-1-0")
        click("stage-editor", "stage-choice-1-0")
        type("stage-editor", "stage-date-1", "2026-10-08")
        click("stage-editor", "stage-save")
        scroll("stage-editor", "stage-error")
        composeRule.onNodeWithTag("stage-error").assertTextContains("rientrare nel viaggio", substring = true)
        assertTrue(runBlocking { local.trips.get(trip.id)!!.stages.isEmpty() })
        type("stage-editor", "stage-date-1", "2026-10-03")
        click("stage-editor", "stage-save")
        waitFor { local.trips.get(trip.id)!!.stages.size == 2 }
        val saved = runBlocking { local.trips.get(trip.id)!! }
        assertEquals(listOf(trip.startsOn, LocalDate.of(2026, 10, 3)), saved.stages.map { it.date })
        assertEquals(6, saved.route!!.geometry.points.size)
        click("trip-content", "trip-stage-ui-memory")
        click("trip-content", "trip-maps-directions")
        assertEquals(trip.destination.center to GeoPoint(45.2, 9.2), opened)
        click("trip-content", "trip-analyze")
        waitForNode("trip-live-results")
        scroll("trip-content", "trip-live-results")
        composeRule.onNodeWithTag("trip-live-results").assertTextContains("2026-10-03 → 2026-10-03", substring = true)
        click("trip-content", "trip-view-all")
        click("trip-content", "trip-save-result-${f8bTaxon.id}")
        waitFor { local.trips.results(trip.id).isNotEmpty() }
        val result = runBlocking { local.trips.results(trip.id).single() }
        assertEquals("ui-memory", result.stageId)
        assertEquals(LocalDate.of(2026, 10, 3), result.startsOn)
        assertEquals("Como", result.area.name)
        click("trip-content", "trip-stage-all")
        scroll("trip-content", "trip-route-saved")
        composeRule.onNodeWithTag("trip-route-saved").assertTextContains("30,0 km", substring = true)
    }

    @Test fun reorderingStagesInvalidatesChangedLegsAndRetryPreservesOtherChoices() {
        val end = trip.destination.copy(name = "Como", center = GeoPoint(45.2, 9.2))
        val a = TripStage("a", trip.destination, trip.startsOn, testTripRoute())
        val b = TripStage("b", end, trip.startsOn, testTripRoute(trip.destination.center, end.center))
        val planned = trip.copy(departure = trip.destination.copy(center = GeoPoint(45.1, 9.1))).withStages(listOf(a, b), f8bNow)
        runBlocking { local.trips.save(planned) }
        var calls = 0
        show(routing = TripRouting { start, finish ->
            calls++
            if (calls == 1) TripRoutingResult.Unavailable else TripRoutingResult.Routes(listOf(testTripRoute(start.center, finish.center)))
        })
        selectTrip(); click("trip-content", "trip-stages-edit")
        click("stage-editor", "stage-up-1")
        click("stage-editor", "stage-save")
        scroll("stage-editor", "stage-error")
        composeRule.onNodeWithTag("stage-error").assertTextContains("ogni tappa", substring = true)
        click("stage-editor", "stage-plan-0")
        waitForNode("stage-error")
        scroll("stage-editor", "stage-error")
        composeRule.onNodeWithTag("stage-error").assertTextContains("Calcolo non disponibile", substring = true)
        click("stage-editor", "stage-plan-0"); waitForNode("stage-choice-0-0"); click("stage-editor", "stage-choice-0-0")
        click("stage-editor", "stage-plan-1"); waitForNode("stage-choice-1-0"); click("stage-editor", "stage-choice-1-0")
        click("stage-editor", "stage-save")
        waitFor { local.trips.get(trip.id)!!.stages.first().id == "b" }
        val saved = runBlocking { local.trips.get(trip.id)!! }
        assertEquals(listOf("b", "a"), saved.stages.map { it.id })
        assertEquals(end.center, saved.stages[1].route.geometry.points.first())
        assertEquals(trip.destination, saved.destination)
    }

    @Test fun mapsButtonSendsOnlyTheConfirmedPointThroughAndroidAndShowsMissingHandler() {
        var opened: Intent? = null
        val failingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                opened = intent
                throw ActivityNotFoundException("fixture no browser")
            }
        }
        composeRule.setContent { FaunaviaTheme {
            CompositionLocalProvider(LocalContext provides failingContext) {
                androidx.compose.foundation.layout.Column {
                    MapsLinkButton(GeoPoint(45.0, 9.0), "maps-test")
                }
            }
        } }
        composeRule.onNodeWithTag("maps-test").performClick()
        composeRule.onNodeWithTag("maps-test-error").assertIsDisplayed()
        val intent = requireNotNull(opened)
        val uri = requireNotNull(intent.data)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", uri.scheme)
        assertEquals("www.google.com", uri.host)
        assertEquals("45.0,9.0", uri.getQueryParameter("query"))
        assertEquals("1", uri.getQueryParameter("api"))
        assertNull(intent.extras)
    }

    @Test fun mapsDirectionsSendBothEndpointsAndHandleMissingActivity() {
        var opened: Intent? = null
        val failingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { opened = intent; throw ActivityNotFoundException("fixture") }
        }
        composeRule.setContent { FaunaviaTheme {
            CompositionLocalProvider(LocalContext provides failingContext) {
                androidx.compose.foundation.layout.Column { MapsDirectionsButton(GeoPoint(45.1, 9.1), GeoPoint(45.0, 9.0)) }
            }
        } }
        composeRule.onNodeWithTag("trip-maps-directions").performClick()
        composeRule.onNodeWithTag("trip-maps-directions-error").assertIsDisplayed()
        val uri = requireNotNull(opened?.data)
        assertEquals("45.1,9.1", uri.getQueryParameter("origin"))
        assertEquals("45.0,9.0", uri.getQueryParameter("destination"))
        assertEquals("driving", uri.getQueryParameter("travelmode"))
        assertNull(opened.extras)
    }

    @Test fun routingFailureCanRetryAndChangingAnEndpointInvalidatesTheChosenTrace() {
        var requests = 0
        show(PlaceSearch { PlaceSearchResult.Matches(listOf(
            PlaceCandidate("1", "Milano", PlaceKind.CITY, "it", GeoPoint(45.0, 9.0), null)), PlaceOrigin.NETWORK) },
            routing = TripRouting { start, end ->
                requests++
                if (requests == 1) TripRoutingResult.Unavailable
                else TripRoutingResult.Routes(listOf(testTripRoute(start.center, end.center)))
            })
        composeRule.onNodeWithTag("trip-new").performClick()
        confirmDeparture()
        type("trip-editor", "trip-place-query", "Milano")
        click("trip-editor", "trip-place-search")
        waitForNode("trip-place-confirm-0")
        click("trip-editor", "trip-place-confirm-0")
        click("trip-editor", "trip-plan")
        waitForNode("trip-plan-error")
        assertNull(runBlocking { local.trips.get("ui-memory") })
        chooseRoute()
        scroll("trip-editor", "trip-route-selected")
        composeRule.onNodeWithTag("trip-route-selected").assertIsDisplayed()
        type("trip-editor", "trip-departure-latitude", "45.2")
        click("trip-editor", "trip-departure-confirm-coordinates")
        composeRule.onNodeWithTag("trip-route-selected").assertDoesNotExist()
        click("trip-editor", "trip-save")
        scroll("trip-editor", "trip-form-error")
        composeRule.onNodeWithTag("trip-form-error").assertIsDisplayed()
        assertNull(runBlocking { local.trips.get("ui-memory") })
        chooseRoute()
        click("trip-editor", "trip-save")
        waitFor { local.trips.get("ui-memory") != null }
        assertEquals(GeoPoint(45.2, 9.1), runBlocking { local.trips.get("ui-memory")!!.route!!.geometry.points.first() })
    }

    @Test fun endpointsDatesAndChosenRouteCreateATripAndLinkedObservation() {
        show(PlaceSearch { PlaceSearchResult.Matches(listOf(
            PlaceCandidate("1", "Milano", PlaceKind.CITY, "it", GeoPoint(45.0, 9.0), null)), PlaceOrigin.NETWORK) })
        composeRule.onNodeWithTag("trip-new").performClick()
        composeRule.onNodeWithTag("trip-name").assertDoesNotExist()
        composeRule.onNodeWithTag("trip-latitude").assertDoesNotExist()
        confirmDeparture()
        type("trip-editor", "trip-place-query", "Milano")
        click("trip-editor", "trip-place-search")
        waitForNode("trip-place-confirm-0")
        click("trip-editor", "trip-place-confirm-0")
        type("trip-editor", "trip-start", "2026-10-01")
        type("trip-editor", "trip-end", "2026-10-07")
        chooseRoute()
        click("trip-editor", "trip-save")
        waitFor { local.trips.get("ui-memory") != null }
        val saved = runBlocking { local.trips.get("ui-memory")!! }
        assertEquals("Milano", saved.name)
        assertEquals(1_000.0, saved.radiusMeters, 0.0)
        assertTrue(saved.interests.isEmpty())
        assertEquals(GeoPoint(45.1, 9.1), saved.departure!!.center)
        assertEquals(3, saved.route!!.geometry.points.size)
        scroll("trip-content", "trip-route-saved")
        composeRule.onNodeWithTag("trip-route-saved").assertIsDisplayed()
        assertEquals(0, provider.calls)
        click("trip-content", "trip-new-observation")
        type("diary-editor", "diary-taxon-query", "merlo")
        waitForNode("diary-taxon-result-${f8bTaxon.id}")
        click("diary-editor", "diary-taxon-result-${f8bTaxon.id}")
        click("diary-editor", "diary-save")
        waitFor { local.diary.get("ui-memory") != null }
        assertEquals(saved.id, runBlocking { local.diary.get("ui-memory")!!.tripId })
        assertNull(runBlocking { local.diary.get("ui-memory")!!.location })
    }

    @Test fun mapsUsesTheConfirmedDestinationAndSelectedOutingAndHandlesLaunchFailure() {
        seedTrip()
        val point = GeoPoint(45.1, 9.1)
        runBlocking { local.trips.saveOuting(Outing("outing", trip.id, "Lago", trip.startsOn,
            TripPlace("Lago", point, "COORDINATES", f8bProvenance))) }
        val opened = mutableListOf<GeoPoint>()
        show(openMap = { opened += it; false })
        selectTrip()
        click("trip-content", "trip-maps")
        composeRule.onNodeWithTag("trip-maps-error").assertIsDisplayed()
        assertEquals(listOf(trip.destination.center), opened)
        click("trip-content", "outing-select-outing")
        click("trip-content", "outing-maps")
        scroll("trip-content", "outing-maps-error")
        composeRule.onNodeWithTag("outing-maps-error").assertIsDisplayed()
        assertEquals(listOf(trip.destination.center, point), opened)
        assertNotNull(runBlocking { local.trips.get(trip.id) })
        assertEquals(0, provider.calls)
    }

    @Test fun createsAndEditsATripOnlyAfterExplicitPlaceConfirmationAndValidDates() {
        var calls = 0
        show(PlaceSearch { calls++; PlaceSearchResult.Matches(listOf(
            PlaceCandidate("1", "Milano città", PlaceKind.CITY, "it", GeoPoint(45.0, 9.0), null),
            PlaceCandidate("2", "Lombardia", PlaceKind.REGION, "it", GeoPoint(45.0, 9.0), null)), PlaceOrigin.NETWORK) })
        composeRule.onNodeWithTag("trip-new").performClick()
        type("trip-editor", "trip-name", "Vacanza")
        type("trip-editor", "trip-start", "2026-10-01")
        type("trip-editor", "trip-end", "2026-10-07")
        click("trip-editor", "trip-save")
        scroll("trip-editor", "trip-form-error")
        composeRule.onNodeWithTag("trip-form-error").assertIsDisplayed()
        type("trip-editor", "trip-place-query", "Lombardia")
        assertEquals(0, calls)
        click("trip-editor", "trip-place-search")
        waitForNode("trip-place-confirm-1")
        click("trip-editor", "trip-place-confirm-1")
        confirmDeparture()
        chooseRoute()
        click("trip-editor", "trip-interest-BIRDS")
        click("trip-editor", "trip-save")
        waitFor { local.trips.get("ui-memory") != null }
        assertEquals("REGION", runBlocking { local.trips.get("ui-memory")!!.destination.kind })
        assertEquals(setOf(AnimalInterest.BIRDS), runBlocking { local.trips.get("ui-memory")!!.interests })
        scroll("trip-content", "trip-coverage")
        composeRule.onNodeWithTag("trip-coverage").assertTextContains("non copre l’intero territorio", substring = true)
        click("trip-content", "trip-edit")
        type("trip-editor", "trip-radius", "20.1")
        click("trip-editor", "trip-save")
        scroll("trip-editor", "trip-form-error")
        composeRule.onNodeWithTag("trip-form-error").assertIsDisplayed()
        type("trip-editor", "trip-radius", "2")
        click("trip-editor", "trip-save")
        waitFor { local.trips.get("ui-memory")!!.radiusMeters == 2_000.0 }
    }

    @Test fun tripResultsLeadToAConfirmedPersonalSightingAndTheLinkedCalendarSummary() {
        seedTrip(); show(); selectTrip()
        assertEquals(0, provider.calls)
        click("trip-content", "trip-analyze")
        waitForNode("trip-live-results")
        click("trip-content", "trip-view-all")
        click("trip-content", "trip-save-result-${f8bTaxon.id}")
        waitFor { local.trips.results(trip.id).isNotEmpty() }
        click("trip-content", "saw-${f8bTaxon.id}")
        waitForNode("diary-taxon-selected")
        scroll("diary-editor", "diary-latitude")
        assertEquals("", composeRule.onNodeWithTag("diary-latitude").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertTrue(runBlocking { local.diary.list().isEmpty() })
        type("diary-editor", "diary-date", "2026-10-02")
        type("diary-editor", "diary-notes", "Merlo osservato davvero")
        click("diary-editor", "diary-save")
        waitFor { local.diary.get("ui-memory") != null }
        val observation = runBlocking { local.diary.get("ui-memory")!! }
        assertEquals(trip.id, observation.tripId); assertNull(observation.location)
        click("trip-content", "trip-diary")
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("1 avvistamenti · 1 specie", substring = true)
        click("diary-list", "diary-view-calendar")
        scroll("diary-list", "diary-calendar-2026-10-02")
        composeRule.onNodeWithTag("diary-calendar-2026-10-02").assertIsDisplayed()
        click("diary-list", "diary-view-map")
        scroll("diary-list", "diary-map-count")
        composeRule.onNodeWithTag("diary-map-count").assertTextContains("1 senza coordinate", substring = true)
    }

    @Test fun persistentUnidentifiedDraftRestoresEditsConvertsAndNeverCountsAsASpecies() {
        seedTrip()
        val tester = StateRestorationTester(composeRule)
        tester.setContent { FaunaviaTheme { TestDiary() } }
        waitForNode("diary-new-draft")
        click("diary-list", "diary-new-draft")
        type("diary-editor", "diary-notes", "Uccello scuro")
        click("diary-editor", "diary-save")
        waitFor { local.unidentified.get("ui-memory") != null }
        assertTrue(runBlocking { local.diary.list().isEmpty() })
        tester.emulateSavedInstanceStateRestore()
        waitForNode("diary-draft-ui-memory")
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("0 avvistamenti · 0 specie", substring = true)
        click("diary-list", "diary-draft-edit-ui-memory")
        type("diary-editor", "diary-notes", "Merlo da confermare")
        click("diary-editor", "diary-save")
        waitFor { local.unidentified.get("ui-memory")?.input?.notes == "Merlo da confermare" }
        click("diary-list", "diary-draft-edit-ui-memory")
        type("diary-editor", "diary-taxon-query", "merlo")
        waitForNode("diary-taxon-result-${f8bTaxon.id}")
        click("diary-editor", "diary-taxon-result-${f8bTaxon.id}")
        click("diary-editor", "diary-save")
        waitFor { local.diary.get("ui-memory") != null && local.unidentified.get("ui-memory") == null }
        assertEquals(1, runBlocking { local.diary.list().size })
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("1 avvistamenti · 1 specie", substring = true)
    }

    @Test fun importedOutingCanBeEditedAndDeletedWithoutLosingItsLinkedMemories() {
        seedTrip()
        val route = Route("route", "Sentiero", listOf(GeoPoint(45.0, 9.0), GeoPoint(45.01, 9.01)), f8bNow, RouteSource.GPX)
        runBlocking { local.routes.save(route) }
        show(); selectTrip()
        click("trip-content", "outing-new")
        type("outing-editor", "outing-name", "Passeggiata")
        waitForNode("outing-route-route")
        click("outing-editor", "outing-route-route")
        click("outing-editor", "outing-save")
        waitFor { local.trips.outing("ui-memory") != null }
        assertEquals(route, runBlocking { local.trips.outing("ui-memory")!!.route })
        click("trip-content", "outing-edit")
        type("outing-editor", "outing-date", "2026-10-02")
        click("outing-editor", "outing-save")
        waitFor { local.trips.outing("ui-memory")!!.date == LocalDate.of(2026, 10, 2) }
        runBlocking {
            local.diary.create(ObservationDraft("linked", f8bTaxon.id, f8bNow, ZoneId.of("UTC"), tripId = trip.id, outingId = "ui-memory"))
            local.unidentified.save(UnidentifiedInput("draft", f8bNow, ZoneId.of("UTC"), tripId = trip.id, outingId = "ui-memory"))
        }
        click("trip-content", "outing-delete")
        click("trip-content", "outing-delete-confirm")
        waitFor { local.trips.outing("ui-memory") == null }
        assertEquals(trip.id, runBlocking { local.diary.get("linked")!!.tripId })
        assertNull(runBlocking { local.diary.get("linked")!!.outingId })
        assertNull(runBlocking { local.unidentified.get("draft")!!.input.outingId })
    }

    @Test fun savedResultsRemainOfflineAndChangedDatesRequireAnExplicitRecalculation() {
        seedTrip()
        val result = SavedTripResult("saved", trip.id, null, f8bTaxon.id, f8bTaxon.scientificName,
            EvidenceLevel.DOCUMENTED, listOf("Osservazione storica"), "Dato stagionale non disponibile",
            listOf(SourceEvidence("source", f8bTaxon.id, EvidenceLevel.DOCUMENTED, null, null, null, "Fonte fixture", f8bProvenance)),
            tripAnalysisKey(trip, null), f8bNow, startsOn = trip.startsOn, endsOn = trip.endsOn, area = trip.destination)
        runBlocking { local.trips.saveResult(result) }
        provider.unavailable = true
        show(); selectTrip()
        assertEquals(0, provider.calls)
        click("trip-content", "trip-edit")
        type("trip-editor", "trip-start", "2026-10-02")
        click("trip-editor", "trip-save")
        waitFor { local.trips.get(trip.id)!!.startsOn == LocalDate.of(2026, 10, 2) }
        scroll("trip-content", "trip-result-outdated-saved")
        composeRule.onNodeWithTag("trip-result-outdated-saved").assertTextContains("ricalcolare", substring = true)
        assertEquals(0, provider.calls)
        click("trip-content", "trip-analyze")
        waitForNode("trip-unavailable")
        scroll("trip-content", "trip-saved-result-saved")
        composeRule.onNodeWithTag("trip-saved-result-saved").assertIsDisplayed()
        click("trip-content", "trip-saved-saw-saved")
        waitForNode("diary-taxon-selected")
        click("diary-editor", "diary-cancel")
        click("trip-content", "trip-delete")
        click("trip-content", "trip-delete-confirm")
        waitFor { local.trips.get(trip.id) == null }
        composeRule.onNodeWithTag("trip-empty").assertIsDisplayed()
    }

    @Test fun diaryTripSpeciesCalendarAndMapFiltersIncludeUnlocatedMemoriesInTheirSummary() {
        seedTrip()
        runBlocking {
            local.trips.save(f8bTrip("other", "Altro viaggio"))
            local.diary.create(ObservationDraft("a", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"), GeoPoint(45.0, 9.0), tripId = trip.id))
            local.diary.create(ObservationDraft("b", f8bTaxon.id, f8bNow.minusSeconds(86_400), ZoneId.of("Europe/Rome"), tripId = trip.id))
            local.diary.create(ObservationDraft("c", f8bTaxon.id, f8bNow, ZoneId.of("Europe/Rome"), tripId = "other"))
            local.unidentified.save(UnidentifiedInput("draft", f8bNow, ZoneId.of("Europe/Rome"), tripId = trip.id))
        }
        composeRule.setContent { FaunaviaTheme { TestDiary() } }
        waitForNode("diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("3 avvistamenti · 1 specie", substring = true)
        click("diary-list", "diary-filter-trip-t")
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("2 avvistamenti", substring = true)
        click("diary-list", "diary-view-calendar")
        scroll("diary-list", "diary-calendar-2026-09-29")
        composeRule.onNodeWithTag("diary-calendar-2026-09-29").assertIsDisplayed()
        click("diary-list", "diary-view-map")
        scroll("diary-list", "diary-map-count")
        composeRule.onNodeWithTag("diary-map-count").assertTextContains("1 senza coordinate", substring = true)
        composeRule.onNodeWithTag("diary-fake-map").assertTextContains("1 punti", substring = true)
        type("diary-list", "diary-species-filter", "Gazella")
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("0 avvistamenti", substring = true)
        type("diary-list", "diary-species-filter", "merlo")
        type("diary-list", "diary-date-filter", "2026-09-30")
        scroll("diary-list", "diary-summary")
        composeRule.onNodeWithTag("diary-summary").assertTextContains("1 avvistamenti", substring = true)
    }

    @Test fun draftDeletionRequiresConfirmationAndLeavesTheDiaryIntact() {
        seedTrip()
        runBlocking { local.unidentified.save(UnidentifiedInput("draft", f8bNow, ZoneId.of("UTC"), notes = "Nota")) }
        composeRule.setContent { FaunaviaTheme { TestDiary() } }
        waitForNode("diary-draft-draft")
        click("diary-list", "diary-draft-delete-draft")
        assertNotNull(runBlocking { local.unidentified.get("draft") })
        click("diary-list", "diary-draft-delete-confirm-draft")
        waitFor { local.unidentified.get("draft") == null }
    }

    @Test fun tripsAndDraftScreensMatchVersionedSignature() {
        seedTrip(); show(); selectTrip()
        val baseline = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("golden/trips-v1.json").bufferedReader().use { it.readText() })
        capture("trips", "screen-trips", baseline.getString("header"))
        click("trip-content", "trip-new-draft")
        waitForNode("diary-confirm-actual")
        composeRule.onNodeWithTag("diary-confirm-actual").assertTextContains("posizione effettive", substring = true)
        capture("draft", "screen-diary", baseline.getString("header"))
        assertEquals(2, baseline.getJSONArray("states").length())
    }

    @Test fun tripSelectionAndSavedResultsRestoreWithoutQueryingProviders() {
        seedTrip()
        runBlocking { local.trips.saveResult(SavedTripResult("saved", trip.id, null, f8bTaxon.id, f8bTaxon.scientificName,
            EvidenceLevel.INSUFFICIENT, listOf("Dati non sufficienti"), "Stagione non disponibile", emptyList(),
            tripAnalysisKey(trip, null), f8bNow, startsOn = trip.startsOn, endsOn = trip.endsOn, area = trip.destination)) }
        val tester = StateRestorationTester(composeRule)
        tester.setContent { FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue,
            F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, f8bExplorer(provider), local.routes,
            fakeMap, now = { f8bNow }, idFactory = { "ui-memory" }) } }
        selectTrip()
        scroll("trip-content", "trip-saved-result-saved")
        tester.emulateSavedInstanceStateRestore()
        waitForNode("trip-detail")
        scroll("trip-content", "trip-saved-result-saved")
        composeRule.onNodeWithTag("trip-saved-result-saved").assertIsDisplayed()
        assertEquals(0, provider.calls)
        click("trip-content", "trip-edit")
        type("trip-editor", "trip-name", "Modifica da conservare")
        tester.emulateSavedInstanceStateRestore()
        waitForNode("trip-editor")
        scroll("trip-editor", "trip-name")
        composeRule.onNodeWithTag("trip-name").assertTextContains("Modifica da conservare", substring = true)
        click("trip-editor", "trip-save")
        waitFor { local.trips.get(trip.id)?.name == "Modifica da conservare" }
        assertEquals(1, runBlocking { local.trips.list().size })
    }

    @Test fun outingEditorRestoresUnsavedFieldsWithoutCreatingADuplicate() {
        seedTrip()
        runBlocking { local.trips.saveOuting(Outing("outing", trip.id, "Passeggiata", trip.startsOn, trip.destination)) }
        val tester = StateRestorationTester(composeRule)
        tester.setContent { FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified, local.catalogue,
            F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, f8bExplorer(provider), local.routes,
            fakeMap, now = { f8bNow }, idFactory = { "ui-memory" }) } }
        selectTrip()
        click("trip-content", "outing-select-outing")
        click("trip-content", "outing-edit")
        type("outing-editor", "outing-name", "Modifica uscita da conservare")
        type("outing-editor", "outing-date", "2026-10-02")
        tester.emulateSavedInstanceStateRestore()
        waitForNode("outing-editor")
        scroll("outing-editor", "outing-name")
        composeRule.onNodeWithTag("outing-name").assertTextContains("Modifica uscita da conservare", substring = true)
        click("outing-editor", "outing-save")
        waitFor { local.trips.outing("outing")?.name == "Modifica uscita da conservare" }
        assertEquals(LocalDate.of(2026, 10, 2), runBlocking { local.trips.outing("outing")!!.date })
        assertEquals(1, runBlocking { local.trips.outings(trip.id).size })
        assertEquals(0, provider.calls)
    }

    @Test fun failedTripSavePreservesTheEditorAndCanBeRetried() {
        seedTrip()
        var attempts = 0
        val failing = object : TripRepository by local.trips {
            override suspend fun save(trip: Trip) {
                attempts++
                if (attempts == 1) throw IllegalStateException("fixture storage failure")
                local.trips.save(trip)
            }
        }
        composeRule.setContent { FaunaviaTheme { TripsScreen(failing, local.diary, local.unidentified, local.catalogue,
            F8BOfflineTaxonomy(), PlaceSearch { PlaceSearchResult.Unavailable() }, f8bExplorer(provider), local.routes,
            fakeMap, now = { f8bNow }, idFactory = { "ui-memory" }) } }
        selectTrip()
        click("trip-content", "trip-edit")
        type("trip-editor", "trip-name", "Nome modificato")
        click("trip-editor", "trip-save")
        waitForNode("trip-error")
        assertEquals(trip.name, runBlocking { local.trips.get(trip.id)!!.name })
        scroll("trip-editor", "trip-name")
        composeRule.onNodeWithTag("trip-name").assertTextContains("Nome modificato", substring = true)
        click("trip-editor", "trip-save")
        waitFor { local.trips.get(trip.id)!!.name == "Nome modificato" }
        assertEquals(2, attempts)
    }

    private fun capture(name: String, tag: String, expected: String) {
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        assertEquals(expected, image.toPixelMap()[2, 2].toArgb().toUInt().toString(16).padStart(8, '0').uppercase())
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = (path?.let(::File) ?: File(context.filesDir, "golden-output")).apply { mkdirs() }
        File(directory, "f8b-$name-actual.png").outputStream().use {
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun show(geocoder: PlaceSearch = PlaceSearch { PlaceSearchResult.Unavailable() },
        openMap: ((GeoPoint) -> Boolean)? = null,
        openDirections: ((GeoPoint, GeoPoint) -> Boolean)? = null,
        routing: TripRouting = TripRouting { start, end -> TripRoutingResult.Routes(listOf(testTripRoute(start.center, end.center))) }) {
        composeRule.setContent { FaunaviaTheme { TripsScreen(local.trips, local.diary, local.unidentified,
            local.catalogue, F8BOfflineTaxonomy(), geocoder, f8bExplorer(provider), local.routes, fakeMap,
            now = { f8bNow }, idFactory = { "ui-memory" }, openMap = openMap,
            routing = routing, openDirections = openDirections,
            routeMap = fakeRouteMap) } }
    }
    private val fakeRouteMap = object : TripRouteMapAdapter {
        @Composable override fun Render(route: TripRoute) {
            androidx.compose.material3.Text("Traccia: ${route.geometry.points.size} punti", modifier = Modifier.testTag("trip-test-route-map"))
        }
    }
    private fun confirmDeparture() {
        click("trip-editor", "trip-departure-manual-toggle")
        type("trip-editor", "trip-departure-place-name", "Partenza")
        type("trip-editor", "trip-departure-latitude", "45.1")
        type("trip-editor", "trip-departure-longitude", "9.1")
        click("trip-editor", "trip-departure-confirm-coordinates")
    }
    private fun chooseRoute() {
        click("trip-editor", "trip-plan")
        waitForNode("trip-route-choice-0")
        click("trip-editor", "trip-route-choice-0")
    }
    @Composable private fun TestDiary() {
        DiaryScreen(local.diary, local.catalogue, F8BOfflineTaxonomy(), now = { f8bNow }, idFactory = { "ui-memory" },
            unidentifiedRepository = local.unidentified, tripRepository = local.trips, personalMap = fakePersonalMap)
    }
    private fun seedTrip() = runBlocking { local.trips.save(trip) }
    private fun selectTrip() { waitForNode("trip-select-t"); click("trip-content", "trip-select-t"); waitForNode("trip-detail") }
    private fun scroll(container: String, tag: String) {
        closeSoftKeyboard()
        if (container == "trip-editor" && (tag in listOf("trip-name", "trip-radius") || tag.startsWith("trip-interest-"))) {
            composeRule.onNodeWithTag(container).performScrollToNode(hasTestTag("trip-options-toggle"))
            val text = composeRule.onNodeWithTag("trip-options-toggle").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            if (text.contains("Personalizza")) composeRule.onNodeWithTag("trip-options-toggle").performClick()
        }
        val horizontal = when {
            tag.startsWith("diary-filter-trip-") -> "diary-trip-filters"
            tag.startsWith("diary-filter-outing-") -> "diary-outing-filters"
            tag.startsWith("diary-view-") -> "diary-view-filters"
            else -> null
        }
        composeRule.onNodeWithTag(container).performScrollToNode(hasTestTag(horizontal ?: tag))
        if (horizontal != null) composeRule.onNodeWithTag(horizontal).performScrollToNode(hasTestTag(tag))
    }
    private fun click(container: String, tag: String) { scroll(container, tag); composeRule.onNodeWithTag(tag).performClick() }
    private fun type(container: String, tag: String, value: String) { scroll(container, tag); composeRule.onNodeWithTag(tag).performTextReplacement(value); closeSoftKeyboard() }
    private fun waitForNode(tag: String) {
        val container = when {
            tag.startsWith("stage-") -> "stage-editor"
            tag.startsWith("outing-route-") -> "outing-editor"
            tag.startsWith("trip-place-confirm-") || tag.startsWith("trip-route-choice-") || tag == "trip-plan-error" -> "trip-editor"
            tag.startsWith("diary-taxon-") || tag == "diary-confirm-actual" -> "diary-editor"
            tag.startsWith("diary-") -> "diary-list"
            else -> "trip-content"
        }
        composeRule.waitUntil(5_000) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
                runCatching { composeRule.onNodeWithTag(container).performScrollToNode(hasTestTag(tag)) }
            }
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun waitFor(block: suspend () -> Boolean) { composeRule.waitUntil(5_000) { runBlocking { block() } } }
}
