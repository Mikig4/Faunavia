# Architettura proposta

## Scelta di base

Per il primo rilascio propongo un'app Android nativa in Kotlin con Jetpack Compose. Il database locale è Room/SQLite; foto, diario, cache, specie curate e asset vivono sul dispositivo. Le notifiche serali sono locali e non richiedono Firebase. Firebase/Firestore resta un'opzione successiva per sincronizzare dati strutturati, non una dipendenza dell'MVP.

## Flusso principale

```text
posizione corrente / GPX / GeoJSON / luogo cercato / nuovo avvistamento manuale
        ↓
normalizzazione della traccia e validazione coordinate
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

Schermate: Home, Analisi percorso, Risultati, Nuovo avvistamento, Diario, Dettaglio specie, Libreria locale e Impostazioni. Lo stato transitorio resta nella UI; diario, foto, preferenze, cache e dati normalizzati finiscono nella persistenza locale.

### 2. Route engine

Importa e valida GPX/GeoJSON, unifica i segmenti, calcola lunghezza e bounding box, campiona la geometria e produce il corridoio di ricerca. Il motore deve essere indipendente dalla UI per poter essere testato con fixture geografiche.

### 3. Biodiversity gateway

Un'interfaccia comune nasconde i provider esterni. Il primo adapter può usare GBIF per le occorrenze; iNaturalist è un adapter opzionale. Ogni risposta conserva provider, query, data di recupero, identificativo del record, licenza e livello di precisione.

### 4. Evidence engine

Normalizza tassonomia e sinonimi, raggruppa i record per specie, scarta coordinate palesemente errate e calcola un livello di evidenza. Il linguaggio della UI deve essere prudente: “segnalata nell'area” o “possibile”, mai “presente adesso”.

### 5. Taxonomy catalogue

Fornisce autocomplete per nomi comuni, scientifici e sinonimi. Ogni scelta viene normalizzata a un taxon accettato con identificativo della fonte, rango, nome scientifico e nomi visualizzabili. Per i risultati visibili carica lazy una preview fotografica con autore/licenza/fonte quando disponibile. Il catalogo generale è separato dalla lista più piccola delle specie peculiari suggerite.

### 6. Observation diary

Registra avvistamenti manuali anche quando l'animale non è tra i suggeriti. L'animale viene scelto dal catalogo generale; foto locale, posizione, data, ora, note e numero di esemplari restano dati dell'utente.

### 7. Suggestion engine

Seleziona solo specie curate come peculiari dell'area e dell'habitat. Il catalogo conserva `suggested`, `urbanCommon`, `distinctivenessScore` e motivazione dell'inclusione. Gli animali comuni non vengono mostrati tra i suggeriti, ma possono sempre essere inseriti nel diario.

### 8. Local store

Room/SQLite, con repository che separa modelli applicativi e dettagli della persistenza. Cache con TTL, versionamento dello schema, foto come file locali e possibilità di cancellare dati e posizione.

### 9. Notification scheduler

Programma un controllo locale giornaliero all'orario scelto. Il controllo legge Room, conta gli avvistamenti della giornata nel fuso locale e crea una notifica solo quando il conteggio è maggiore di zero.

### 10. Species profile e asset registry

Contiene i profili curati dall'utente, i riferimenti alle fonti e il mapping specie → modello 3D/immagine. Gli asset sono file locali e vengono caricati solo quando servono.

### 11. Map adapter

Rende traccia, corridoio, campioni, risultati e punti del diario. MapLibre Native è il candidato Android; il motore dati non deve dipendere dal formato della mappa. L'attribuzione della fonte deve essere sempre visibile.

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

## Evoluzione possibile

Se l'app richiederà sincronizzazione, si potrà aggiungere Firestore per i record strutturati. Le foto potranno restare locali con backup manuale; Cloud Storage non è compatibile con il vincolo zero-costi senza verificare un piano di fatturazione. Per mappe offline, si potrà distribuire un pacchetto regionale generato da dati OSM con una licenza e un provider compatibili, senza scaricare in massa le tile standard di OSM.
