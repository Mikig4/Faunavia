package it.faunavia.exploration

import it.faunavia.domain.*
import it.faunavia.plausibility.ObservabilityGuidance
import it.faunavia.plausibility.SeasonAssessment
import java.time.Instant
import java.util.Locale

enum class SuggestionView { TYPICAL, EASIER, NEVER_OBSERVED, WISHLIST }

/** Evidence is copied without reclassification; curation cannot establish presence. */
data class SuggestionCandidate(
    val id: String,
    val scientificName: String,
    val level: EvidenceLevel,
    val provenance: List<Provenance>,
    val guidance: ObservabilityGuidance = ObservabilityGuidance.Unavailable,
    val season: SeasonAssessment? = null,
    val savedSeason: String? = null,
) {
    init { require(id.isNotBlank() && scientificName.isNotBlank() && provenance.isNotEmpty()) }
}

data class CuratedSuggestion(val scientificName: String, val profile: SuggestionProfile, val bounds: SuggestionBounds)

/** A curator's coverage envelope, never an administrative boundary or species range. */
data class SuggestionBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    init {
        require(south in -90.0..90.0 && north in south..90.0 && west in -180.0..180.0 && east in west..180.0)
    }
    fun contains(point: GeoPoint) = point.latitude in south..north && point.longitude in west..east
}

fun scientificIdentity(name: String): String {
    val parts = name.trim().split(Regex("\\s+"))
    val count = if (parts.size > 2 && parts[2].matches(Regex("[a-z]+"))) 3 else 2
    return parts.take(count).joinToString(" ").lowercase(Locale.ROOT)
}

data class PersonalSuggestion(val candidate: SuggestionCandidate, val profile: SuggestionProfile?, val reason: String)

class PersonalSuggestionEngine(
    private val profiles: List<CuratedSuggestion> = PilotSuggestionProfiles.entries,
    private val distinctivenessThreshold: Double = 0.6,
) {
    init {
        require(distinctivenessThreshold in 0.0..1.0)
        require(profiles.map { scientificIdentity(it.scientificName) to it.profile.area }.distinct().size == profiles.size)
    }

    fun select(candidates: List<SuggestionCandidate>, areaPoints: List<GeoPoint>, view: SuggestionView,
        observedTaxa: List<Taxon> = emptyList(), wishedTaxa: List<Taxon> = emptyList()): List<PersonalSuggestion> {
        val observedIds = observedTaxa.filter(Taxon::isSelectable).map(Taxon::id).toSet()
        val observedNames = observedTaxa.filter(Taxon::isSelectable).map { scientificIdentity(it.scientificName) }.toSet()
        val wishedIds = wishedTaxa.map(Taxon::id).toSet()
        val wishedNames = wishedTaxa.map { scientificIdentity(it.scientificName) }.toSet()
        return candidates.distinctBy(SuggestionCandidate::id).mapNotNull { candidate ->
            val name = scientificIdentity(candidate.scientificName)
            val profile = profiles.filter { it.profile.schemaVersion == 1 && scientificIdentity(it.scientificName) == name && areaPoints.any(it.bounds::contains) }
                .sortedWith(compareByDescending<CuratedSuggestion> { it.profile.distinctivenessScore }.thenBy { it.profile.area })
                .firstOrNull()?.profile?.copy(taxonId = candidate.id)
            val include = when (view) {
                SuggestionView.TYPICAL -> profile != null && !profile.urbanCommon && profile.distinctivenessScore >= distinctivenessThreshold
                SuggestionView.EASIER -> true
                SuggestionView.NEVER_OBSERVED -> candidate.id !in observedIds && name !in observedNames
                SuggestionView.WISHLIST -> candidate.id in wishedIds || name in wishedNames
            }
            if (!include) return@mapNotNull null
            val reason = when (view) {
                SuggestionView.TYPICAL -> requireNotNull(profile).reason
                SuggestionView.EASIER -> "Dati confrontabili sulla facilità di osservazione non disponibili: ordine alfabetico, senza probabilità."
                SuggestionView.NEVER_OBSERVED -> "Non risulta nel tuo diario identificato globale. Bozze ed evidenze esterne non contano come avvistamenti personali."
                SuggestionView.WISHLIST -> "È nella tua lista desideri e nei risultati di questa area e di questo periodo. Il livello di evidenza resta invariato."
            }
            PersonalSuggestion(candidate, profile, reason)
        }.sortedWith(compareByDescending<PersonalSuggestion> {
            if (view == SuggestionView.TYPICAL) it.profile?.distinctivenessScore ?: 0.0 else 0.0
        }.thenBy { scientificIdentity(it.candidate.scientificName) }.thenBy { it.candidate.id })
    }
}

/** Original factual curation; linked sources and their media are not bundled or redistributed. */
object PilotSuggestionProfiles {
    const val VERSION = "pilot-2026-10-02-v2"
    private val envelope = SuggestionBounds(44.5, 8.3, 46.7, 11.5)
    private fun entry(name: String, habitat: String, score: Double, urban: Boolean, reason: String,
        url: String, attribution: String, license: String) = CuratedSuggestion(name,
        SuggestionProfile("curation:$name", "Area curatoriale Lombardia e dintorni (rettangolo pilota)", listOf(habitat), urban, score, reason,
            Provenance(url, name, "consultazione habitat; cura originale Faunavia, non prova di presenza", Instant.parse("2026-10-02T00:00:00Z"),
                license, attribution, "curatoriale; punteggio editoriale, non probabilità", VERSION)), envelope)
    val entries = listOf(
        entry("Alcedo atthis", "Zone umide e rive", 0.85, false,
            "Selezione per gli ambienti acquatici: invita a cercare rive e zone umide quando le evidenze locali includono questa specie.",
            "https://www.lipu.it/uccelli/conoscerli-proteggerli/martin-pescatore", "Lipu, scheda Martin pescatore; cura Faunavia", "Fonte CC BY-NC-ND 4.0; testo/media non redistribuiti"),
        entry("Dryocopus martius", "Foreste con alberi maturi", 0.9, false,
            "Selezione per il paesaggio forestale: gli alberi maturi sono un habitat da approfondire, non una conferma di presenza lungo tutto il viaggio.",
            "https://www.lipu.it/uccelli/conoscerli-proteggerli/picchio-nero", "Lipu, scheda Picchio nero; cura Faunavia", "Fonte CC BY-NC-ND 4.0; testo/media non redistribuiti"),
        entry("Capra ibex", "Ambienti rocciosi alpini e praterie", 0.95, false,
            "Selezione per ambienti alpini: rocce e praterie motivano l'interesse; la scheda generale non garantisce un avvistamento locale.",
            "https://www.parchialpicozie.it/it/parcopedia/stambecco/", "Aree Protette Alpi Cozie, Stambecco; cura Faunavia", "Fonte consultata; riuso non verificato, nessun testo/media redistribuito"),
        entry("Turdus merula", "Boschi, parchi e giardini", 0.2, true,
            "Comune anche nei parchi urbani: resta selezionabile per desideri, diario e prime osservazioni personali.",
            "https://www.lipu.it/uccelli/conoscerli-proteggerli/merlo", "Lipu, scheda Merlo; cura Faunavia", "Fonte CC BY-NC-ND 4.0; testo/media non redistribuiti"),
        bird("Ardea cinerea", "Acque basse, rive e risaie", 0.75, "airone-cenerino", "Airone cenerino",
            "Selezione per zone umide e paesaggi di risaia; l'habitat generale non conferma la presenza nel punto cercato."),
        bird("Egretta garzetta", "Zone umide, rive e coltivi allagati", 0.8, "garzetta", "Garzetta",
            "Selezione per rive e zone umide della pianura; resta subordinata alle evidenze disponibili nell'area."),
        bird("Podiceps cristatus", "Laghi e acque calme con vegetazione ripariale", 0.8, "svasso-maggiore", "Svasso maggiore",
            "Selezione per il paesaggio lacustre; i dati disponibili motivano l'interesse, senza garantire incontri."),
        bird("Upupa epops", "Prati, frutteti e mosaici agricoli alberati", 0.85, "upupa", "Upupa",
            "Selezione per il mosaico agricolo tradizionale; verificare le evidenze del periodo scelto."),
        bird("Aquila chrysaetos", "Pareti rocciose e praterie montane", 0.95, "aquila-reale", "Aquila reale",
            "Selezione per paesaggi montani aperti e rupestri; il punteggio editoriale non misura rarità o facilità di incontro."),
        bird("Falco peregrinus", "Pareti rocciose e ambienti aperti", 0.85, "falco-pellegrino", "Falco pellegrino",
            "Selezione per ambienti rupestri; può frequentare anche città, ma non è escluso per il solo uso di edifici."),
        mammal("Rupicapra rupicapra", "Praterie alpine, rocce e boschi montani", 0.95, "camoscio", "Camoscio",
            "Selezione per il paesaggio alpino; le evidenze locali restano necessarie e la copertura curatoriale non è un areale."),
        mammal("Marmota marmota", "Praterie e pascoli alpini", 0.9, "marmotta", "Marmotta alpina",
            "Selezione per gli ambienti aperti alpini; habitat e interesse non implicano attività o presenza nel periodo scelto."),
    )

    private fun bird(name: String, habitat: String, score: Double, slug: String, common: String, reason: String) =
        entry(name, habitat, score, false, reason, "https://www.lipu.it/uccelli/conoscerli-proteggerli/$slug",
            "Lipu, scheda $common; cura Faunavia", "Fonte CC BY-NC-ND 4.0; testo/media non redistribuiti")

    private fun mammal(name: String, habitat: String, score: Double, slug: String, common: String, reason: String) =
        entry(name, habitat, score, false, reason, "https://www.parchialpicozie.it/it/parcopedia/$slug/",
            "Aree Protette Alpi Cozie, $common; cura Faunavia", "Fonte consultata; riuso non verificato, nessun testo/media redistribuito")
}

/** Selects existing evidence only; every route segment is used, never the curator's envelope as a range. */
fun typicalTaxa(taxa: List<ExploredTaxon>, areaPoints: List<GeoPoint>): List<ExploredTaxon> {
    val selected = PersonalSuggestionEngine().select(taxa.map { it.suggestionCandidate() }, areaPoints, SuggestionView.TYPICAL)
    val byId = taxa.associateBy(ExploredTaxon::id)
    return selected.map { requireNotNull(byId[it.candidate.id]) }
}

fun ExploredTaxon.suggestionCandidate() = SuggestionCandidate(id, scientificName, assessment.level,
    (occurrences.map { it.provenance } + assessment.explanation.flatMap { it.provenance }).distinct(), assessment.observability, assessment.season)

fun SavedTripResult.suggestionCandidate(): SuggestionCandidate? = evidence.map(SourceEvidence::provenance).distinct()
    .takeIf { it.isNotEmpty() }?.let { SuggestionCandidate(taxonId, scientificName, level, it, savedSeason = season) }
