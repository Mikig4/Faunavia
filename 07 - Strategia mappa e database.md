# Strategia mappa e database

## La distinzione fondamentale

Non serve salvare “tutta la mappa con tutte le informazioni sugli animali” in un unico database. Ci sono tre livelli diversi:

1. **Basemap:** strade, sentieri, corsi d'acqua, rilievi e confini.
2. **Catalogo naturalistico:** specie, habitat, stagionalità, peculiarità e fonti.
3. **Diario personale:** i miei avvistamenti, foto, posizione, note e data/ora.

Questi livelli devono poter funzionare anche separatamente.

## Catalogo completo vs suggerimenti

Il database deve contenere un catalogo tassonomico generale, mentre la schermata suggerimenti deve usare una vista filtrata e curata.

- `Taxon`: tutti i taxa animali riconosciuti dalla fonte adottata, con nome accettato, sinonimi, rango e identificativo.
- `SpeciesProfile`: informazioni divulgative, habitat, stagionalità, asset e fonti.
- `SuggestionProfile`: specie del luogo considerate peculiari, con regole di esclusione degli animali urbani comuni.
- `Observation`: sempre collegato a un `Taxon` scelto dall'utente, anche se non è suggerito.

La lista completa non va necessariamente copiata tutta nell'APK. È più sostenibile cercarla tramite il servizio tassonomico, salvare il risultato scelto e aggiornare periodicamente la cache.

## Proposta zero-costi

### Database locale: indispensabile

Room/SQLite sul telefono è la fonte primaria dell'MVP. Contiene:

- avvistamenti manuali;
- riferimenti alle foto locali;
- specie e profili scaricati o inclusi nell'app;
- lista delle specie peculiari per area;
- risultati GBIF già recuperati;
- preferenze, orario della notifica e stato dei pacchetti mappa.

Vantaggi: nessun server, funziona offline, privacy migliore, nessun costo operativo.

### Firebase/Firestore: opzionale

Firestore può essere utile più avanti per sincronizzare specie curate, configurazioni e avvistamenti strutturati. Il piano Spark offre quote gratuite, ma sono quote giornaliere/mensili e il progetto deve gestire il superamento senza perdere i dati. Non lo userei come unico archivio del diario.

Come ordine di grandezza, la documentazione attuale indica 1 GiB di dati, 50.000 letture/giorno, 20.000 scritture/giorno, 20.000 cancellazioni/giorno e 10 GiB/mese di traffico in uscita per la quota gratuita Firestore. Sono limiti da ricontrollare prima dell'implementazione: [Firestore usage and limits](https://firebase.google.com/docs/firestore/quotas).

### Foto: locali nell'MVP

Le foto devono restare nel file storage dell'app o in una cartella esportabile. Ogni `ObservationPhoto` salva URI locale, miniatura, dimensione, hash, data e orientamento. Il backup è un archivio esportato manualmente con database + foto.

Cloud Storage per Firebase non è la scelta zero-costi da assumere: la documentazione Android attuale richiede il piano Blaze per usare un bucket Firebase. Prima di qualsiasi attivazione va quindi presa una decisione esplicita sui costi.

## Mappa

### Online

Quando c'è rete, l'app può usare MapLibre Native con una sorgente di tile/vector tile compatibile e attribuzione visibile. MapLibre Native Android prevede regioni offline, ma la sorgente deve consentirne il download. Le tile standard di `tile.openstreetmap.org` non vanno pre-scaricate per creare una modalità offline. Riferimenti: [MapLibre offline API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html) e [OSM Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

### Offline

Per l'MVP non è necessario salvare il mondo intero. Si può supportare un pacchetto regionale scelto dall'utente, con area e livelli di zoom limitati. Il pacchetto può essere generato da un estratto OSM usando strumenti open source e poi incluso o importato nel telefono; serve verificare licenza, dimensioni e processo di aggiornamento.

### Degrado elegante

Se la mappa non è disponibile, l'app deve comunque mostrare lista, coordinate, distanza, foto e diario. La mappa è una vista utile, non il database degli avvistamenti.

## Decisione proposta

Per il primo prototipo: Room/SQLite locale + foto locali + mappa online quando disponibile + nessun Firebase. Dopo aver usato l'app per alcune settimane, valutare se serve davvero sincronizzare il catalogo o il diario.
