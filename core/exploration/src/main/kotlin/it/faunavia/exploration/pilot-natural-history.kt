package it.faunavia.exploration

import it.faunavia.domain.Provenance
import java.time.Instant

/** Original short factual notes. No source prose, photographs or generated curiosities are imported. */
object PilotNaturalHistory {
    const val VERSION = "natural-history-2026-10-03-v1"
    private fun source(name: String, slug: String, park: Boolean = false): Provenance {
        val url = if (park) "https://www.pngp.it/natura-e-ricerca/fauna/praterie-e-ambienti-rocciosi/$slug"
            else "https://www.lipu.it/uccelli/conoscerli-proteggerli/$slug"
        return Provenance(url, name, "Fatti generali di storia naturale: $name", Instant.parse("2026-10-03T00:00:00Z"),
            if (park) "Licenza di riuso del testo non indicata: testo e fotografie non importati; note fattuali originali"
            else "Fonte: CC BY-NC-ND 4.0, fotografie escluse; testo e fotografie non importati; note fattuali originali",
            if (park) "Parco Nazionale Gran Paradiso" else "Lipu ODV",
            "Fonte primaria divulgativa; data di pubblicazione non indicata; non evidenza di presenza locale", VERSION)
    }
    private val respect = ProfileFact("Osserva a distanza e non offrire cibo agli animali selvatici. Rispetta i sentieri e le regole del luogo.",
        Provenance("https://www.pngp.it/visita-il-parco/come-comportarsi", "wildlife-respect", "Indicazioni generali per osservare senza disturbare",
            Instant.parse("2026-10-03T00:00:00Z"), "Licenza non indicata; note originali, nessun testo o immagine importato",
            "Parco Nazionale Gran Paradiso", "Indicazioni del Parco; verificare il regolamento del luogo visitato", VERSION))

    private fun entry(name: String, common: String, slug: String, habitat: String, season: String, size: String, diet: String,
        behavior: String, conservation: String?, curiosityTitle: String, curiosity: String, park: Boolean = false): ReadableSpeciesProfile {
        val reference = source(name, slug, park)
        val facts = mapOf(ProfileField.HABITAT to habitat, ProfileField.SEASON to season, ProfileField.SIZE to size,
            ProfileField.DIET to diet, ProfileField.BEHAVIOR to behavior, ProfileField.CONSERVATION to conservation)
            .mapNotNull { (field, text) -> text?.let { field to ProfileFact(it, reference) } }.toMap() + (ProfileField.SAFETY to respect)
        return ReadableSpeciesProfile(name, common, reference, facts,
            listOf(SpeciesCuriosity(curiosityTitle, ProfileFact(curiosity, reference))), VERSION)
    }

    val entries: List<ReadableSpeciesProfile> = listOf(
        entry("Alcedo atthis", "Martin pescatore", "martin-pescatore", "Fiumi, laghi e altre zone umide con rive adatte alla nidificazione.",
            "Popolazioni sedentarie e migratrici a corto raggio; varia secondo l'area.", "Lunghezza 17–19,5 cm.",
            "Soprattutto piccoli pesci.", "Caccia tuffandosi dall'appostamento sopra l'acqua.",
            "La qualità dell'acqua e la conservazione delle rive sono importanti per la specie.", "Un nido nella riva",
            "Scava il nido nelle scarpate di terra lungo le rive."),
        entry("Dryocopus martius", "Picchio nero", "picchio-nero", "Boschi con alberi di grandi dimensioni e legno morto.",
            "Generalmente sedentario; sono possibili spostamenti invernali a quote inferiori.", "Apertura alare 64–68 cm.",
            "Formiche e altri insetti del legno.", "Scava cavità negli alberi per nidificare.",
            "Gli alberi con cavità e il legno morto sono risorse da conservare.", "Case per altri uccelli",
            "Le cavità scavate dal picchio possono essere riutilizzate da altri uccelli, come la civetta capogrosso."),
        entry("Capra ibex", "Stambecco", "lo-stambecco", "Praterie d'alta quota e pareti rocciose alpine.",
            "L'alimentazione varia tra estate e altre stagioni.", "Maschi adulti: circa 160 cm; femmine: circa 135 cm, secondo il Parco.",
            "Erba estiva; anche arbusti, germogli e licheni nelle altre stagioni.", "Si muove su versanti rocciosi ripidi.",
            "La popolazione del Gran Paradiso ha avuto un ruolo decisivo nella sopravvivenza dello stambecco alpino.", "Corna permanenti",
            "Maschi e femmine hanno corna permanenti; quelle dei maschi sono più grandi.", park = true),
        entry("Turdus merula", "Merlo", "merlo", "Boschi, siepi, giardini e parchi urbani.",
            "Popolazioni sedentarie e parzialmente migratrici.", "Lunghezza 24–25 cm.",
            "Invertebrati, tra cui lombrichi e insetti; anche frutti e bacche.", "Cerca spesso il cibo sul terreno.",
            "Siepi e arbusti offrono riparo; l'uso di pesticidi può ridurre le risorse alimentari.", "Colori diversi",
            "Il maschio adulto è nero con becco aranciato; la femmina ha un piumaggio bruno."),
        entry("Ardea cinerea", "Airone cenerino", "airone-cenerino", "Zone umide, risaie e ambienti lungo i corsi d'acqua.",
            "In Italia nidifica, migra e sverna; parte delle popolazioni è sedentaria.", "Lunghezza 90–98 cm; apertura alare 160–175 cm.",
            "Pesci, anfibi e altri piccoli vertebrati.", "Nidifica anche in colonie chiamate garzaie.",
            "Le colonie di nidificazione necessitano di luoghi protetti dal disturbo.", "Il collo in volo",
            "Durante il volo ripiega il collo a forma di S."),
        entry("Egretta garzetta", "Garzetta", "garzetta", "Acque poco profonde, stagni, lagune e risaie.",
            "In Italia è nidificante, migratrice e svernante; una parte è sedentaria.", "Lunghezza 55–67 cm.",
            "Pesci, anfibi e invertebrati acquatici.", "Può nidificare in colonie su alberi e cespugli presso zone umide.",
            "La perdita di zone umide e i cambiamenti nelle risaie riducono gli ambienti alimentari.", "Le penne ornamentali",
            "Nel periodo riproduttivo compaiono penne allungate dietro la testa, chiamate egrette."),
        entry("Podiceps cristatus", "Svasso maggiore", "svasso-maggiore", "Laghi e altre acque calme con vegetazione lungo le rive.",
            "In Italia è nidificante, sedentario, migratore e svernante.", "Lunghezza 46–51 cm.",
            "Soprattutto pesci, catturati immergendosi.", "Costruisce un nido galleggiante ancorato alla vegetazione.",
            "Variazioni del livello dell'acqua e disturbo presso i nidi possono compromettere la riproduzione.", "Un nido galleggiante",
            "Il nido resta ancorato alla vegetazione palustre mentre galleggia sull'acqua."),
        entry("Upupa epops", "Upupa", "upupa", "Paesaggi aperti con alberi sparsi, coltivi e mosaici agricoli.",
            "Migratrice a lungo raggio.", "Lunghezza 25–29 cm.", "Insetti e larve; anche altri piccoli invertebrati.",
            "Nidifica in cavità di alberi, rocce e muri.", "Vecchi alberi e muretti con cavità offrono siti di nidificazione.",
            "Una cresta mobile", "Durante il corteggiamento il maschio può aprire la cresta."),
        entry("Aquila chrysaetos", "Aquila reale", "aquila-reale", "Montagne con pareti rocciose, praterie e pascoli aperti.",
            "Generalmente sedentaria in Italia; i giovani possono disperdersi.", "Apertura alare 190–240 cm.",
            "Mammiferi e uccelli; può alimentarsi anche di carcasse.", "Caccia soprattutto in ambienti aperti montani.",
            "La disponibilità delle prede e la tranquillità dei siti di nidificazione sono importanti.", "Più nidi nello stesso territorio",
            "Una coppia può disporre di diversi nidi e scegliere quale utilizzare di anno in anno."),
        entry("Falco peregrinus", "Falco pellegrino", "falco-pellegrino", "Pareti rocciose; può nidificare anche su grandi edifici.",
            "In Italia nidifica e sverna; sono presenti anche individui migratori.", "Maschi 38–45 cm; femmine 46–51 cm.",
            "Soprattutto uccelli di piccole e medie dimensioni.", "Cattura le prede in volo.",
            "Il disturbo vicino ai nidi, anche durante l'arrampicata, può causare l'abbandono della covata.", "La femmina è più grande",
            "Nella stessa specie la femmina ha in genere dimensioni maggiori del maschio."),
        entry("Rupicapra rupicapra", "Camoscio", "il-camoscio", "Pendii ripidi e rocciosi di media e alta montagna.",
            "La dieta cambia tra estate e inverno.", "Lunghezza 100–130 cm.",
            "Erbe fresche in estate; anche foglie, arbusti, licheni e muschi in inverno.", "I movimenti stagionali variano tra gli individui.",
            null, "Zoccoli adatti alla roccia", "La parte morbida dello zoccolo favorisce l'aderenza; il bordo duro aiuta a usare piccoli appigli.", park = true),
        entry("Marmota marmota", "Marmotta alpina", "la-marmotta", "Praterie alpine e subalpine con terreno adatto a scavare tane.",
            "Supera l'inverno in letargo nelle tane.", "Corpo 53–73 cm, oltre alla coda di 13–16 cm.",
            "Prevalentemente erbe; anche germogli, semi e radici.", "Vive in gruppi familiari e usa tane profonde.",
            null, "Vigilanza condivisa", "Tutti i membri del gruppo controllano l'ambiente mentre si alimentano; non esistono sentinelle dedicate.", park = true),
    )
    fun find(name: String): ReadableSpeciesProfile? = entries.firstOrNull { scientificIdentity(it.scientificName) == scientificIdentity(name) }
}
