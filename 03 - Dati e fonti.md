# Dati e fonti

## Principio di interpretazione

Il dato di biodiversità è un'evidenza di osservazione, non una promessa di incontro. La UI deve esplicitare: dove è stata registrata la specie, quando, da quale fonte, con quale accuratezza e con quale eventuale generalizzazione della coordinata.

## Fonti candidate a costo zero

### GBIF

Prima fonte per l'MVP: l'API di GBIF espone ricerche di occorrenze in JSON e, per la maggior parte delle operazioni di ricerca, non richiede autenticazione. Va usata con query spaziali contenute, cache e attribuzione per singolo risultato. Riferimenti: [GBIF API reference](https://techdocs.gbif.org/en/openapi/) e [GBIF data use](https://www.gbif.org/terms/data-use).

### iNaturalist

Adapter opzionale per osservazioni e contenuti naturalistici più leggibili. L'API moderna è documentata su [api.inaturalist.org](https://api.inaturalist.org/v1/docs/); l'accesso pubblico e i termini per immagini, osservazioni e nomi degli autori vanno verificati prima di includere dati in una cache persistente.

### OpenStreetMap

Usare OSM per la mappa e, se necessario, per contesto geografico. Le tile standard non sono una sorgente da cui fare prefetch offline: la [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) richiede attribuzione, User-Agent identificabile, rispetto della cache e vieta il bulk download/offline sui server standard. Per il geocoding, la [Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/) richiede carico limitato e vieta query sistematiche.

## Pipeline dati

1. Definire il corridoio in locale.
2. Interrogare i provider solo per bounding box/segmenti aggregati.
3. Salvare risposta grezza minima con identificativo e metadati della query.
4. Normalizzare specie e sinonimi in un modello interno.
5. Deduplicare record e calcolare evidenze, senza alterare la provenienza.
6. Mostrare risultati con data di recupero, fonte, licenza e spiegazione del ranking.
7. Scadere o aggiornare la cache senza cancellare la provenienza storica.

## Qualità, privacy e casi delicati

- Le coordinate di specie sensibili possono essere sfocate o generalizzate: non tentare di ricostruirle.
- Le osservazioni recenti non equivalgono a disponibilità in tempo reale.
- Le specie rare, protette o pericolose meritano una scheda separata con limiti e avvertenze.
- Non inviare la traccia completa se basta un bounding box o una griglia a risoluzione ridotta.
- Ogni importazione deve essere riproducibile tramite query e timestamp registrati.

## Decisione iniziale proposta

Partire con GBIF e dati locali curati. Aggiungere iNaturalist solo dopo aver definito chiaramente licenze, attribuzione, limiti di frequenza e formato della cache.

## Catalogo completo degli animali

Per il selettore degli avvistamenti non costruiremo una lista manuale. Useremo un backbone tassonomico con ricerca per nome comune, nome scientifico e sinonimi. GBIF espone servizi Species per scoprire taxa, interpretare nomi e recuperare identificativi; il suo backbone globale viene aggiornato più volte l'anno e deriva in larga parte dal Catalogue of Life.

La ricerca applicativa userà questo flusso:

1. l'utente digita almeno 2–3 caratteri;
2. l'app cerca nomi e sinonimi con filtro `Animalia`;
3. mostra risultati accettati, con nome comune/scientifico e rango;
4. salva il `taxonKey` e la versione/data della fonte;
5. usa il taxon selezionato per diario, scheda, suggerimenti e query di occorrenza.

Il catalogo generale e i suggerimenti locali sono due insiemi diversi. Una specie può essere selezionabile nel catalogo completo ma non apparire tra i suggerimenti di Milano.

I nomi comuni non sono completi o univoci per tutte le specie e cambiano tra lingue e regioni. Per questo il risultato mostrerà sempre il nome scientifico come riferimento, affiancandolo al nome italiano quando disponibile e agli eventuali sinonimi.

### Online o offline

- **Scelta raccomandata:** autocomplete online con cache locale degli animali usati, del catalogo della zona e delle ricerche recenti.
- **Modalità completamente offline:** scaricare e trasformare una fotografia del backbone in SQLite; è possibile, ma richiede più spazio, un processo di aggiornamento e gestione delle modifiche tassonomiche.

Per l'MVP sceglierei la prima opzione e renderei offline almeno tutti gli animali già selezionati dall'utente.

## Preview fotografica durante la ricerca

La ricerca tassonomica e la preview fotografica sono due richieste separate:

1. `Species search` restituisce nomi, rango e identificativi.
2. Dopo il debounce, l'app richiede al massimo una o poche immagini per i risultati visibili.
3. La miniatura mostra fonte, autore e licenza quando disponibili.
4. L'immagine non viene incorporata nell'APK né copiata permanentemente senza una licenza compatibile.
5. Le preview vengono caricate lazy, messe in cache temporanea e possono mancare senza bloccare la selezione.

GBIF offre un'Occurrence Image API per immagini collegate a record di osservazione, ma avverte che i diritti delle immagini possono essere più restrittivi dei diritti sui dati di occorrenza. La preview deve quindi conservare i metadati di attribuzione e la decisione di riutilizzo va presa per fonte/immagine.

## Diario personale e catalogo suggerimenti

Gli avvistamenti inseriti dall'utente sono dati di prima parte e hanno una provenienza diversa dai record naturalistici esterni. Devono essere salvati con `origin: manual`, devono riferirsi a un taxon selezionato e non devono diventare automaticamente una nuova “evidenza scientifica” per il ranking.

Il catalogo dei suggerimenti deve essere più piccolo del catalogo tassonomico. Per ogni specie candidata registrare almeno: area, habitat, `distinctivenessScore`, `urbanCommon`, motivazione curatoriale e fonte. Questo consente di escludere gli animali comuni senza vietare all'utente di registrarli.

## Firebase come opzione, non come fondazione

Firestore può contenere in futuro solo dati strutturati e versionati: profili specie, regole regionali e sincronizzazione del diario. Foto e database locale restano indipendenti. Il progetto non deve attivare Cloud Storage o un piano con fatturazione senza una decisione esplicita.
