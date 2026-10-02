# Architettura proposta

## Scelta di base

Per il primo rilascio propongo un'app Android nativa in Kotlin con Jetpack Compose. Il database locale è Room/SQLite; foto, diario, cache, specie curate e asset vivono sul dispositivo. Le notifiche serali sono locali e non richiedono Firebase. Firebase/Firestore resta un'opzione successiva per sincronizzare dati strutturati, non una dipendenza dell'MVP.

## Direzione delle dipendenze

La dipendenza è sempre rivolta verso l'interno: UI e framework dipendono dai casi d'uso; i casi d'uso dipendono dal dominio e da interfacce; Room, rete, mappa, notifiche e renderer 3D implementano adapter esterni. Il dominio non importa API Android, client HTTP, modelli Room o tipi MapLibre. In questo modo la logica di percorso, tassonomia, diario e plausibilità resta deterministica e testabile senza dispositivo o rete.

```text
UI / Android / provider / Room / MapLibre / renderer 3D
                         ↓
               adapter e repository
                         ↓
                    casi d'uso
                         ↓
                       dominio
```

## Flusso principale

```text
partenza + destinazione confermate + date → scelta del tracciato sulla mappa → viaggio salvato
         ↓
analisi del viaggio (oppure esplorazione senza viaggio / GPX / GeoJSON facoltativi)
         ↓
geocoding del luogo cercato oppure normalizzazione della traccia e validazione coordinate
        ↓
campionamento della geometria + buffer del corridoio
        ↓
adapter dei provider di biodiversità e cache locale
        ↓
normalizzazione, deduplicazione e ranking delle evidenze
        ↓
lista specie peculiari + mappa + filtri + fonti
        ↓
scheda animale + modello GLB lazy-loaded + fallback 2D
        ↓
diario personale + foto locali + riepilogo serale + notifica locale
```

## Componenti

### 1. UI e stato applicativo

Navigazione principale: Viaggi, Diario, Catalogo e Impostazioni. Viaggi è l'ingresso dell'app e contiene analisi, evidenze e accesso ai ricordi. Esplorazione senza viaggio e importazione di percorsi sono strumenti facoltativi raggiungibili da Viaggi, con ritorno esplicito. Lo stato transitorio resta nella UI; diario, foto, preferenze, cache e dati normalizzati finiscono nella persistenza locale.

### 2. Route engine

Importa e valida GPX/GeoJSON, unifica i segmenti, calcola lunghezza e bounding box, campiona la geometria e produce il corridoio di ricerca. La strategia approvata è ibrida: il motore emette porzioni semplificate del corridoio e chiavi di celle di griglia stabili; ogni adapter sceglie poligoni oppure bounding box contenuti in base alle capacità del provider. Il motore deve essere indipendente dalla UI per poter essere testato con fixture geografiche.

L'implementazione F5 vive in `:core:route`, dipende soltanto dal dominio e usa ETRS89 / LAEA Europe (`EPSG:3035`). Le celle sono metriche da 1 km, raggruppate in chunk da 5 km. Il fingerprint di geometria identifica il percorso salvato; un fingerprint distinto include raggio, intervallo, precisione e griglia per identificare una specifica ricerca. Il pilot rifiuta esplicitamente coordinate fuori dall'area europea e attraversamenti dell'antimeridiano invece di applicare una proiezione non valida.

La F8B collega anche `TripRouting` in `:core:exploration`: OSRM propone percorsi in auto tra due punti confermati, su richiesta esplicita e con limite di una richiesta al secondo. La UI mostra la geometria completa con MapLibre e salva solo la scelta confermata nel viaggio, con provenienza. `TripStage` aggiunge tappe ordinate con giorno e percorso; l'analisi di una tappa usa il suo tratto e la sua data, quella complessiva l'intero corridoio e intervallo. Il payload locale v3 legge i precedenti viaggi v1/v2; Room resta allo schema 6. Google Maps riceve gli estremi della tappa selezionata per le sue indicazioni e non sostituisce la geometria salvata.

### 3. Biodiversity gateway

Un'interfaccia comune nasconde i provider esterni. Il primo adapter usa GBIF e il Network Nazionale della Biodiversità per le evidenze documentate; iNaturalist resta opzionale. Adapter distinti forniscono distribuzione, habitat e stagionalità da fonti istituzionali. Ogni risposta conserva provider, query, data di recupero, identificativo del record, licenza e livello di precisione.

### 4. Evidence engine

Normalizza tassonomia e sinonimi, raggruppa i record per specie, scarta coordinate palesemente errate e calcola un livello di evidenza. “Plausibile” richiede contemporaneamente compatibilità con l'areale e con l'habitat; la stagionalità aumenta o riduce la confidenza. Se una delle due evidenze necessarie manca, il risultato è “informazioni insufficienti”. Il linguaggio della UI deve essere prudente: “segnalata nell'area” o “possibile”, mai “presente adesso”.

### 5. Taxonomy catalogue

Fornisce autocomplete per nomi comuni, scientifici e sinonimi. Ogni scelta viene normalizzata a un taxon accettato con identificativo della fonte, rango, nome scientifico e nomi visualizzabili. Per i risultati visibili carica lazy una preview fotografica con autore/licenza/fonte quando disponibile. Il catalogo generale è separato dalla lista più piccola delle specie peculiari suggerite.

### 6. Observation diary

Registra avvistamenti manuali anche quando l'animale non è tra i suggeriti. Il Diario cerca direttamente nel catalogo generale e conserva una specie e i suoi alias dopo selezione esplicita; senza rete usa le scelte locali. Data, ora, posizione opzionale, note e quantità restano dati dell'utente. Viaggio e uscita sono collegamenti opzionali indipendenti dalle coordinate; le bozze da identificare restano separate.

### 7. Suggestion engine

Seleziona specie curate come peculiari dell'area e dell'habitat. Il profilo conserva `urbanCommon`, `distinctivenessScore` e motivazione dell'inclusione. Gli animali comuni possono avere priorità inferiore secondo la vista già prevista dalla F9 e possono sempre essere inseriti nel diario.

### 8. Local store

Room/SQLite, con repository che separa modelli applicativi e dettagli della persistenza. Cache con TTL, versionamento dello schema, foto come file locali e possibilità di cancellare dati e posizione.

### 9. Notification scheduler

Programma con WorkManager un controllo locale giornaliero attorno all'orario scelto, entro una finestra flessibile compatibile con le politiche energetiche Android. Il controllo legge Room, conta gli avvistamenti della giornata nel fuso locale e crea una notifica solo quando il conteggio è maggiore di zero.

### 10. Species profile e asset registry

Contiene i profili curati dall'utente, i riferimenti alle fonti e il mapping specie → modello 3D/immagine. Gli asset sono file locali e vengono caricati solo quando servono.

### 11. Map adapter

Rende traccia, corridoio, campioni, risultati e punti del diario. MapLibre Native è il candidato Android; il motore dati non deve dipendere dal formato della mappa. L'attribuzione della fonte deve essere sempre visibile.

### 12. Geographic search adapter

Un caso d'uso separato risolve nomi di paese, regione e città in un luogo canonico con punto, riquadro o poligono. L'adapter di geocoding resta dietro un'interfaccia di dominio: la UI non conosce URL o parametri del provider. F8 usa un servizio online con query limitate, attribuzione, cache locale e conferma dell'utente; F15 può aggiungere un gazetteer regionale offline. Un nome ambiguo o non trovato produce uno stato esplicito e non avvia una query naturalistica.

## Modello concettuale minimo

- `Route`: sorgente, geometria, punti campionati, timestamp.
- `SearchCorridor`: geometria, raggio, regole di campionamento.
- `Occurrence`: specie, coordinate o area generalizzata, data, fonte, qualità e licenza.
- `Species`: tassonomia, nomi, caratteristiche, habitat e stagionalità.
- `Asset`: specie, URL/file locale, formato, versione, autore e licenza.
- `EvidenceSummary`: specie, livello, punteggio, spiegazione e fonti.
- `Taxon`: identificativo fonte, nome accettato, sinonimi, rango e nomi comuni.
- `Observation`: `taxonId`, data/ora locale, posizione, note, foto e origine `manual`.
- `SuggestionProfile`: specie, area/habitat, priorità, peculiarità, esclusioni e motivazione.
- `DailySummary`: data locale, numero di avvistamenti e stato della notifica.
- `SourceEvidence`: tipo `occurrence | range | habitat | season`, fonte, versione, recupero, licenza, qualità e riferimento riproducibile.

## Strategia di verifica

La build espone quattro gate automatizzati: `verifyFast` per compilazione, lint e unit test; `verifyDevice` per test strumentati, Compose UI e UI Automator; `verifyVisual` per confronti screenshot; `verifyAll` per la suite completa. I provider esterni sono sostituiti da fixture nei test riproducibili. Emulatore gestito, ADB e controllo schermo verificano i flussi Android; Playwright viene introdotto soltanto se nascerà una superficie web o WebView.

## Evoluzione possibile

Se l'app richiederà sincronizzazione, si potrà aggiungere Firestore per i record strutturati. Le foto potranno restare locali con backup manuale; Cloud Storage non è compatibile con il vincolo zero-costi senza verificare un piano di fatturazione. Per mappe offline, si potrà distribuire un pacchetto regionale generato da dati OSM con una licenza e un provider compatibili, senza scaricare in massa le tile standard di OSM.
