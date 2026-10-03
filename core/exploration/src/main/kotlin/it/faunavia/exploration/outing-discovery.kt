package it.faunavia.exploration

import it.faunavia.domain.*
import java.time.Instant
import java.time.LocalDate
import kotlin.math.*

data class OutingProposal(val id: String, val name: String, val place: TripPlace, val guide: OutingGuide,
    val route: Route? = null, val publicLocation: Boolean = true) {
    init { require(id == guide.catalogueId && name.isNotBlank() && name.length <= 160) }
    fun saveFor(trip: Trip, date: LocalDate, at: Instant): Outing {
        require(publicLocation) { "Una località sensibile non può diventare una proposta pubblica." }
        require(date in trip.startsOn..trip.endsOn) { "Scegli una data compresa nel viaggio." }
        // Stable identity prevents repeated taps from duplicating the same proposal on the same day.
        return Outing("f17:${trip.id}:$id:$date", trip.id, name, date, place, route, guide.copy(savedAt = at))
    }
}

fun interface OutingCatalogue { fun load(): List<OutingProposal> }
enum class OutingSort { RELEVANCE, DISTANCE, DURATION }
data class OutingFilters(val date: LocalDate? = null, val maxDurationMinutes: Int? = null,
    val maxLengthMeters: Int? = null, val difficulty: String? = null, val wishedOnly: Boolean = false,
    val seasonalOnly: Boolean = false, val sort: OutingSort = OutingSort.RELEVANCE) {
    init {
        require(maxDurationMinutes == null || maxDurationMinutes > 0)
        require(maxLengthMeters == null || maxLengthMeters > 0)
    }
}
data class DiscoveredOuting(val proposal: OutingProposal, val distanceMeters: Double,
    val species: List<OutingSpecies>, val wishedSpecies: List<OutingSpecies>, val seasonalSpecies: List<OutingSpecies>)
data class OutingDiscoveryResult(val outings: List<DiscoveredOuting>, val warning: String? = null)

/** Ranking general place documentation never changes F7 or creates a diary sighting. */
class OutingDiscovery(private val catalogue: OutingCatalogue = PilotOutingCatalogue) {
    fun discover(trip: Trip, wishedTaxa: List<Taxon> = emptyList(), filters: OutingFilters = OutingFilters()): OutingDiscoveryResult {
        require(filters.date == null || filters.date in trip.startsOn..trip.endsOn)
        val entries = try { catalogue.load() } catch (_: Exception) {
            return OutingDiscoveryResult(emptyList(), "Catalogo uscite non disponibile. Le uscite salvate, i percorsi importati e la pianificazione manuale restano disponibili.")
        }
        val months = mutableSetOf<Int>()
        var date = filters.date ?: trip.startsOn
        val end = filters.date ?: trip.endsOn
        while (!date.isAfter(end)) { months += date.monthValue; date = date.plusDays(1) }
        val wishedNames = wishedTaxa.filter(Taxon::isSelectable).map { scientificIdentity(it.scientificName) }.toSet()
        val candidates = entries.filter { it.publicLocation }.distinctBy { it.id }.mapNotNull { entry ->
            val distance = geographicDistanceMeters(trip.destination.center, entry.place.center)
            if (distance > trip.radiusMeters) return@mapNotNull null
            val guide = entry.guide
            if (filters.maxDurationMinutes != null && (guide.durationMinutes?.let { it <= filters.maxDurationMinutes } != true)) return@mapNotNull null
            if (filters.maxLengthMeters != null && (guide.lengthMeters?.let { it <= filters.maxLengthMeters } != true)) return@mapNotNull null
            if (filters.difficulty != null && guide.difficulty != filters.difficulty) return@mapNotNull null
            val species = guide.species.filter { trip.interests.isEmpty() || it.interest in trip.interests }
            if (species.isEmpty() && trip.interests.isNotEmpty()) return@mapNotNull null
            val wishes = species.filter { scientificIdentity(it.scientificName) in wishedNames }
            val seasonal = species.filter { it.recommendedMonths.any(months::contains) }
            if (filters.wishedOnly && wishes.isEmpty()) return@mapNotNull null
            if (filters.seasonalOnly && seasonal.isEmpty()) return@mapNotNull null
            DiscoveredOuting(entry, distance, species, wishes, seasonal)
        }
        val comparator = when (filters.sort) {
            OutingSort.RELEVANCE -> compareByDescending<DiscoveredOuting> { it.wishedSpecies.size }
                .thenByDescending { it.seasonalSpecies.size }.thenByDescending { it.species.size }.thenBy { it.distanceMeters }
            OutingSort.DISTANCE -> compareBy { it.distanceMeters }
            OutingSort.DURATION -> compareBy { it.proposal.guide.durationMinutes ?: Int.MAX_VALUE }
        }
        return OutingDiscoveryResult(candidates.sortedWith(comparator.thenBy { it.proposal.id }))
    }
}

/** Great-circle distance to a public reference point; never a transfer time or walking length. */
fun geographicDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 6_371_008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Original factual summaries, not copied articles/maps; only public visitor places are exposed. */
object PilotOutingCatalogue : OutingCatalogue {
    const val VERSION = "lombardia-2026-10-03-v1"
    private val consulted = Instant.parse("2026-10-03T14:35:42Z")
    private fun source(url: String, record: String, author: String, quality: String = "Sintesi fattuale del gestore; verifica accessi e aggiornamenti prima della visita") =
        Provenance(url, record, "Catalogo pilota uscite Lombardia", consulted,
            "Sintesi originale Faunavia; testo, foto e cartografia delle fonti non incorporati", author, quality, VERSION)
    private fun coordinate(url: String, record: String, license: String, author: String, quality: String) =
        Provenance(url, record, "Punto pubblico rappresentativo del luogo, non posizione di animali", consulted, license, author, quality, VERSION)
    private fun species(name: String, common: String, group: AnimalInterest, months: Set<Int>, source: Provenance) = OutingSpecies(name, common, group, months, source)
    private val sebino = source("https://torbieresebino.it/sentieri/", "sentieri", "Ente Riserva Torbiere del Sebino")
    private val sebinoAccess = source("https://torbieresebino.it/percorso-centrale-3/", "centrale-2026", "Ente Riserva Torbiere del Sebino")
    private val sebinoFauna = source("https://torbieresebino.it/riserva-naturale/martin-pescatore/", "martin-pescatore", "Ente Riserva Torbiere del Sebino",
        "Specie riportata nella riserva; tarda estate/autunno consigliati dal gestore, non garanzia di incontro")
    private val sebinoCoordinate = coordinate("https://www.wikidata.org/wiki/Q3936730", "Q3936730/P625", "CC0", "Wikidata",
        "Centro pubblico della riserva; precisione dichiarata 0.00001 gradi, non coordinata del singolo ingresso")
    private val brabbia = source("https://www.lipupaludebrabbia.it/oasi/fruizione-e-servizi/", "sentieri-pubblici", "Lipu · Palude Brabbia")
    private val brabbiaFauna = source("https://www.lipupaludebrabbia.it/oasi/fauna/avifauna/", "avifauna", "Lipu · Palude Brabbia",
        "Specie riportate dal gestore; stagionalità indicata nella fonte, non segnalazioni in tempo reale")
    private val brabbiaCoordinate = coordinate("https://www.openstreetmap.org/node/3832743702", "node/3832743702", "ODbL 1.0", "© OpenStreetMap contributors",
        "Centro visite pubblico ottenuto con Nominatim; precisione metrica non dichiarata")
    private val vanzago = source("https://www.boscowwfdivanzago.it/visita-loasi/", "visita-guidata", "WWF · Bosco di Vanzago")
    private val vanzagoFauna = source("https://www.wwf.it/dove-interveniamo/il-nostro-lavoro-in-italia/oasi/oasi-bosco-di-vanzago/", "fauna-accessi", "WWF Italia")
    private val vanzagoCoordinate = coordinate("https://www.openstreetmap.org/way/254680883", "way/254680883", "ODbL 1.0", "© OpenStreetMap contributors",
        "Centro rappresentativo della riserva ottenuto con Nominatim; non ingresso, precisione metrica non dichiarata")
    private fun proposal(id: String, name: String, point: GeoPoint, start: String, length: Int?, minutes: Int?, access: String,
        fauna: List<OutingSpecies>, source: Provenance, accessSource: Provenance, coordinates: Provenance) = OutingProposal(id, name,
        TripPlace(name, point, "CURATED_PUBLIC_PLACE", coordinates), OutingGuide(id, VERSION, consulted, start, length, minutes, null,
            access, null, fauna, source, accessSource, coordinates))
    private val sebinoSpecies = listOf(species("Alcedo atthis", "Martin pescatore", AnimalInterest.BIRDS, setOf(8, 9, 10, 11), sebinoFauna))
    override fun load(): List<OutingProposal> = listOf(
        proposal("sebino-tutti", "Torbiere del Sebino · percorso per tutti", GeoPoint(45.65027778, 10.03333333),
            "Centro Accoglienza Visitatori, Iseo", 2_000, 60,
            "Andata e ritorno sullo stesso sentiero. Verifica sul sito accesso, biglietto e chiusure; il nome del percorso non certifica l’accessibilità personale.",
            sebinoSpecies, sebino, sebino, sebinoCoordinate),
        proposal("sebino-nord-sud", "Torbiere del Sebino · anello nord-sud", GeoPoint(45.65027778, 10.03333333),
            "Uno dei tre ingressi ufficiali: Iseo, Provaglio d’Iseo o Corte Franca", 8_000, 180,
            "Seguire i percorsi ufficiali e verificare gli avvisi del gestore. Il centrale ha restrizioni separate: non aggiungerlo senza verifica.",
            sebinoSpecies, sebino, sebinoAccess, sebinoCoordinate),
        proposal("sebino-nord-centrale", "Torbiere del Sebino · nord-centrale", GeoPoint(45.65027778, 10.03333333),
            "Centro Accoglienza Visitatori, Iseo", 5_000, 120,
            "ATTENZIONE: centrale chiuso dal 16 marzo al 18 luglio 2026 e poi riaperto solo parzialmente per sicurezza. Lunghezza e durata descrivono l’itinerario completo, attualmente non garantito. Verifica gli avvisi e usa i tratti autorizzati.",
            sebinoSpecies, sebino, sebinoAccess, sebinoCoordinate),
        proposal("brabbia-sentieri", "Palude Brabbia · sentieri e capanni", GeoPoint(45.7858499, 8.7353903),
            "Centro visite, via Patrioti 22, Inarzo", null, null,
            "Percorsi segnati pubblici dall’alba al tramonto. Torretta e riserva integrale richiedono accompagnamento/prenotazione. Verifica gli avvisi del gestore.",
            listOf(species("Turdus merula", "Merlo", AnimalInterest.BIRDS, emptySet(), brabbiaFauna),
                species("Turdus iliacus", "Tordo sassello", AnimalInterest.BIRDS, setOf(12, 1, 2), brabbiaFauna)), brabbia, brabbia, brabbiaCoordinate),
        proposal("vanzago-guidata", "Bosco WWF di Vanzago · visita guidata", GeoPoint(45.5201168, 8.9723693),
            "Via Tre Campane 21, Vanzago", null, 120,
            "Solo visite guidate/attività organizzate; visite ordinarie nel fine settimana, escluso agosto. Conferma disponibilità e prenotazione con il gestore.",
            listOf(species("Capreolus capreolus", "Capriolo", AnimalInterest.MAMMALS, emptySet(), vanzagoFauna),
                species("Lucanus cervus", "Cervo volante", AnimalInterest.INVERTEBRATES, emptySet(), vanzagoFauna)), vanzago, vanzagoFauna, vanzagoCoordinate),
    )
}
