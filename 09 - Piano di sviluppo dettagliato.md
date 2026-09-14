# Piano di sviluppo dettagliato

Questo è il piano operativo iniziale. Non contiene ancora codice: definisce l'ordine in cui costruire il progetto, cosa deve essere verificato e quando una fase può considerarsi conclusa.

## Regole di esecuzione

- Una fase non si considera conclusa perché “compila”: deve avere un risultato osservabile e un test minimo.
- Le decisioni che cambiano costi, privacy, licenze o formato dei dati devono essere documentate prima di implementare.
- Prima si costruisce il percorso locale dell'app; i servizi esterni restano adapter sostituibili.
- Il catalogo generale degli animali e i suggerimenti peculiari del luogo restano separati.

## P0 — Conferma del perimetro

**Dipendenze:** nessuna.

Definire come assunzioni di partenza: Android nativo, APK installabile manualmente, diario locale, foto locali, notifica alle 20:30, mappa offline rimandata e nessun Firebase nell'MVP.

**Completamento:** le decisioni sono riportate in `06 - Domande aperte.md` e `.mex/context/decisions.md`.

## P1 — Progetto Android vuoto e APK installabile

**Dipendenze:** P0.

1. Creare il progetto Kotlin/Jetpack Compose.
2. Impostare min/target SDK e verificare compatibilità con il dispositivo di test.
3. Configurare build debug e release locale.
4. Creare le schermate vuote: Home, Cerca animale, Nuovo avvistamento, Diario, Percorso, Dettaglio animale, Impostazioni.
5. Installare l'APK sul telefono.

**Completamento:** l'APK si installa e apre tutte le schermate placeholder senza rete.

## P2 — Modello dati locale e Room

**Dipendenze:** P1.

Creare schema e repository per:

- `Taxon` — identificativo fonte, nome accettato, nomi comuni, sinonimi, rango, fonte/versione;
- `TaxonPreview` — URL miniatura, autore, licenza, fonte, scadenza cache;
- `SpeciesProfile` — dati divulgativi e asset associati;
- `SuggestionProfile` — area, habitat, peculiarità, esclusioni e motivazione;
- `Observation` — taxon obbligatorio, data/ora, posizione, quantità, note, origine;
- `ObservationPhoto` — percorso locale, miniatura, hash e metadati di attribuzione se presenti;
- `Route` — file originale, geometria normalizzata e metadati;
- `AppSettings` — orario notifica, fuso, preferenze e stato dei pacchetti.

**Completamento:** inserimento, modifica, cancellazione e lettura di un record di test funzionano con database vuoto e dopo riavvio dell'app.

## P3 — Ricerca animale con foto preview

**Dipendenze:** P2.

1. Creare adapter per la Species API tassonomica.
2. Cercare per nome comune, scientifico e sinonimo.
3. Applicare debounce e minimo di caratteri.
4. Limitare i risultati a `Animalia` e a taxa selezionabili.
5. Mostrare nome scientifico, nome comune, rango e stato accettato.
6. Caricare lazy una miniatura per i risultati visibili.
7. Mostrare fonte, autore e licenza della foto.
8. Usare placeholder quando manca una foto o la licenza non consente riuso locale.
9. Salvare il taxon scelto e la preview in cache.

**Completamento:** cercando un animale si vedono risultati con foto quando disponibile; selezionando un risultato si salva un taxon stabile; una foto mancante non impedisce la selezione.

## P4 — Inserimento manuale dell'avvistamento

**Dipendenze:** P2, P3.

1. Aprire “Nuovo avvistamento”.
2. Cercare e selezionare un animale dal catalogo generale.
3. Precompilare data, ora e posizione corrente, lasciando la possibilità di modificarle.
4. Aggiungere quantità e note.
5. Salvare senza rete.
6. Mostrare il record nel diario.

**Completamento:** un animale non suggerito può essere scelto, salvato, modificato e visualizzato nel diario offline.

## P5 — Foto dell'avvistamento

**Dipendenze:** P4.

1. Selezionare una foto con Photo Picker.
2. Prevedere successivamente l'acquisizione da fotocamera.
3. Copiare una versione controllata nell'area privata dell'app.
4. Generare una miniatura.
5. Evitare di esporre automaticamente EXIF e coordinate personali.
6. Consentire eliminazione della foto senza cancellare l'avvistamento.

**Completamento:** un avvistamento può avere zero, una o più foto; l'app funziona anche se la foto viene rimossa o non è disponibile.

## P6 — Notifica serale locale

**Dipendenze:** P2, P4.

1. Salvare l'orario predefinito 20:30.
2. Richiedere il permesso notifiche solo nel momento opportuno.
3. Programmare un controllo giornaliero idempotente.
4. Calcolare la data nel fuso orario del dispositivo.
5. Contare solo avvistamenti manuali della giornata.
6. Notificare soltanto se il conteggio è maggiore di zero.
7. Aprire il riepilogo giornaliero al tap.
8. Gestire riavvio, cambio fuso, cambio orario e permesso negato.

**Completamento:** con avvistamenti viene mostrato il riepilogo; senza avvistamenti non viene inviata una notifica vuota; il diario continua a funzionare anche senza permesso.

## P7 — Catalogo dei suggerimenti peculiari

**Dipendenze:** P2, P3.

1. Definire `SuggestionProfile` separato da `Taxon`.
2. Aggiungere regole `urbanCommon`, `distinctivenessScore`, habitat e area.
3. Escludere gli animali comuni dalla lista primaria.
4. Mostrare la motivazione del suggerimento.
5. Consentire sempre la ricerca nel catalogo generale.

**Completamento:** i suggerimenti mostrano solo la selezione peculiare configurata; la ricerca generale continua a permettere qualunque animale.

## P8 — Scheda animale e primo asset 3D

**Dipendenze:** P3, P7.

1. Definire il template della scheda.
2. Collegare nomi, caratteristiche, habitat, stagionalità e fonti.
3. Creare un primo modello low-poly in Blender.
4. Esportare GLB.
5. Registrare autore, fonte, licenza e versione.
6. Caricare il modello solo quando richiesto.
7. Prevedere immagine/silhouette di fallback.

**Completamento:** una specie selezionata ha una scheda leggibile e un modello 3D oppure un fallback funzionante.

## P9 — Importazione e analisi itinerari

**Dipendenze:** P2, P3.

1. Importare GPX e GeoJSON.
2. Validare coordinate e segmenti.
3. Normalizzare la geometria.
4. Campionare il percorso.
5. Creare il corridoio di analisi.
6. Interrogare le occorrenze GBIF tramite adapter.
7. Salvare cache, fonte, data e qualità.

**Completamento:** un itinerario senza tappe produce corridoio, risultati e spiegazione delle evidenze.

## P10 — Mappa online e visualizzazione

**Dipendenze:** P9.

1. Integrare MapLibre Native.
2. Visualizzare posizione, percorso, corridoio, osservazioni e risultati.
3. Mostrare attribuzione della mappa.
4. Gestire assenza di rete lasciando accessibili diario e risultati salvati.

**Completamento:** la mappa è una vista dei dati, non una dipendenza per leggere il diario.

## P11 — Export/import e backup locale

**Dipendenze:** P2, P5, P6.

1. Esportare database, foto e manifest in un archivio.
2. Importare un archivio su un nuovo dispositivo.
3. Validare versione dello schema.
4. Gestire file mancanti senza perdere gli avvistamenti.

**Completamento:** il diario può essere salvato e ripristinato senza Firebase.

## P12 — Pacchetto mappa offline per itinerari

**Dipendenze:** P10, decisione su area e provider.

1. Scegliere una sola regione iniziale.
2. Scegliere fonte e licenza che permettano il download offline.
3. Generare il pacchetto con livelli di zoom limitati.
4. Versionare, importare e cancellare il pacchetto.
5. Testare dimensioni e memoria sul telefono.

**Completamento:** un itinerario scelto funziona con il pacchetto regionale senza connessione.

## P13 — Sincronizzazione opzionale

**Dipendenze:** P11 e uso reale dell'app.

Valutare Firestore solo se emerge un bisogno concreto di sincronizzare catalogo o diario. Le foto restano escluse finché non esiste una soluzione di backup compatibile con il vincolo economico.

**Completamento:** decisione documentata, non semplice aggiunta tecnica.

## Ordine del primo ciclo

`P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9 → P10 → P11`

P12 e P13 vengono dopo il primo utilizzo reale dell'app.
