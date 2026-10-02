package it.faunavia.exploration

import it.faunavia.domain.Provenance
import java.net.URLEncoder
import java.time.Instant

data class DistributionReference(val source: Provenance, val mapDate: String)
data class SpeciesPresentation(val scientificName: String, val commonName: String, val distribution: DistributionReference)

/** Reviewed file references: remote presentation only, no occurrence points converted into ranges. */
object PilotSpeciesPresentation {
    private fun entry(name: String, common: String, file: String, author: String, license: String, date: String): SpeciesPresentation {
        val url = "https://commons.wikimedia.org/wiki/File:" + URLEncoder.encode(file, "UTF-8").replace("+", "%20")
        return SpeciesPresentation(name, common, DistributionReference(Provenance(url, file,
            "mappa generale di distribuzione; fonte originale consultabile su richiesta", Instant.parse("2026-10-02T00:00:00Z"),
            license, "$author · Wikimedia Commons", "illustrazione generale datata, non presenza attuale né compatibilità locale", "commons-reviewed-2026-10-02"), date))
    }

    val entries = listOf(
        entry("Alcedo atthis", "Martin pescatore", "Alcedo atthis -range map-2-cp.png", "Snowmanradio; Devil m25", "CC BY-SA 3.0", "2008"),
        entry("Dryocopus martius", "Picchio nero", "Dryocopus martius distr.PNG", "Scops", "CC BY-SA 3.0", "2007; fonte di base 1995"),
        entry("Capra ibex", "Stambecco", "AlpineIbex distribution.png", "BhagyaMani", "CC BY-SA 4.0", "2023"),
        entry("Turdus merula", "Merlo", "Turdus merula distribution map.png", "Cactus26; MPF", "CC BY-SA 3.0", "2016"),
        entry("Ardea cinerea", "Airone cenerino", "ArdeaCinereaIUCN2019 2.png", "SanoAK: Alexander Kürthy", "CC BY-SA 3.0", "2019"),
        entry("Egretta garzetta", "Garzetta", "EgrettaGarzettaIUVNver2018 2.png", "SanoAK: Alexander Kürthy", "CC BY-SA 3.0", "2019; dati indicati 2018"),
        entry("Podiceps cristatus", "Svasso maggiore", "PodicepsCristatusIUCN2019-2.png", "SanoAK: Alexander Kürthy", "CC BY-SA 3.0", "2019"),
        entry("Upupa epops", "Upupa", "Upupa distribution.png", "Ulrich Prokop", "CC BY 2.5", "2005"),
        entry("Aquila chrysaetos", "Aquila reale", "AquilaChrysaetosIUCNver2018 2.png", "SanoAK: Alexander Kürthy; base BirdLife International", "CC BY-SA 3.0", "2019; base BirdLife 2016"),
        entry("Falco peregrinus", "Falco pellegrino", "PeregrineRangeMap.png", "MPF", "CC BY-SA 3.0", "2007"),
        entry("Rupicapra rupicapra", "Camoscio", "Rupicapra rupicapra.png", "Christophe cagé", "CC BY-SA 3.0", "2010"),
        entry("Marmota marmota", "Marmotta alpina", "Mapa Marmota marmota.png", "Aavitus", "Public domain", "2019"),
    )

    fun find(scientificName: String): SpeciesPresentation? = entries.firstOrNull {
        scientificIdentity(it.scientificName) == scientificIdentity(scientificName)
    }
}
