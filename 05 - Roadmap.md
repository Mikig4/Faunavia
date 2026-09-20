# Roadmap

La roadmap contiene 17 fasi, da F0 a F16, chiuse da gate verificabili. Una fase produce un incremento utilizzabile e non si apre la successiva finché build, test propri e gate di non regressione richiesti non sono verdi.

| Fase | Incremento | Gate principale |
|---|---|---|
| F0 | decisioni, spike dati e fixture | fonti e query riproducibili, nessun codice prodotto |
| F1 | scaffold Android e automazione | APK installabile + `verifyFast`, `verifyDevice`, `verifyVisual`, `verifyAll` |
| F2 | dominio, Room e repository | vincoli, CRUD, migrazioni e persistenza dopo riavvio |
| F3 | ricerca tassonomica | taxon Animalia accettato, sinonimi, cache e fallback offline |
| F4 | diario manuale | creazione/modifica/eliminazione offline con specie obbligatoria |
| F5 | route engine | GPX/GeoJSON → corridoio, porzioni e celle stabili |
| F6 | gateway occorrenze | adapter, cache, provenienza, retry e deduplicazione |
| F7 | motore di plausibilità | areale + habitat obbligatori, stagione come modificatore |
| F8 | risultati, ricerca geografica e mappa online | nome/coordinate/percorso → area confermata → risultati spiegati → mappa |
| F9 | suggerimenti peculiari | distinzione dai taxa comuni e dal catalogo generale |
| F10 | foto locali | Photo Picker, copie controllate, privacy e gestione errori |
| F11 | notifica flessibile | WorkManager, fuso, permessi, zero notifiche vuote |
| F12 | export/import | archivio verificato, ripristino e rollback sicuro |
| F13 | scheda specie e fallback 2D | contenuto tracciabile, accessibile e disponibile dalla cache |
| F14 | pipeline e primo asset 3D | GLB validato, manifest, budget mobile e fallback integro |
| F15 | mappa e gazetteer regionali offline | licenza, pacchetto versionato, ricerca per nome, import/cancellazione |
| F16 | sincronizzazione opzionale | decisione architetturale motivata prima di qualsiasi backend |

## Stato corrente

F3 è completata: il Catalogo cerca il primo provider GBIF con debounce, risolve i sinonimi al taxon Animalia accettato e conserva localmente solo le scelte esplicite con relativi nomi ricercabili. Le scelte restano utilizzabili offline; i suggerimenti remoti in memoria hanno una scadenza controllata. Il dettaglio è in [[14 - Rapporto Fase 3]].

F4 è implementata nel working tree e in verifica: il Diario crea, modifica ed elimina avvistamenti offline, richiede un taxon selezionato e registra data/ora locale, quantità, note e coordinate opzionali. La migrazione Room v3→v4 aggiunge la quantità preservando i record esistenti come un esemplare. L'APK debug `0.4.0-f4` è stato generato; build, formattazione, analisi statica e lint sono verdi. La fase non è ancora dichiarata completata finché test JVM e gate device/visual, bloccati in questo ambiente dalla connessione loopback dei worker Gradle, non tornano verdi.

F5 è implementata nel working tree e in verifica: la schermata Percorsi importa GPX/GeoJSON tramite il picker Android oppure analizza coordinate WGS84, conserva localmente la geometria normalizzata e mostra lunghezza, campioni, celle, chunk e fingerprint. Il modulo puro Kotlin `:core:route` usa EPSG:3035 con celle metriche da 1 km e chunk da 5 km; preserva i segmenti, deduplica per geometria e genera fingerprint di ricerca sensibili alla configurazione. I 13 test F5 sono verdi; compilazione Android e lint sono verdi. Il gate device è ancora bloccato prima dell'esecuzione dal loopback del worker UTP, quindi F5 non è dichiarata completata. Dettagli in [[15 - Rapporto Fase 5]].

F6 è implementata nel working tree e in verifica: il modulo puro Kotlin `:core:occurrence` espone un gateway comune per GBIF e NNB WFS, riceve dal motore F5 solo porzioni/celle aggregate, usa paginazione limitata e deduplica per provider più identificativo del record. GBIF preferisce poligoni e può ripiegare sui bounding box; NNB usa bounding box WFS. La cache Room v5 conserva record normalizzati, timestamp e scadenza, mentre un refresh fallito restituisce esplicitamente la cache stale con l'errore del provider. I sei test F6, la compilazione Android e lo smoke online GBIF/NNB sono verdi; i gate device/visual restano bloccati dal loopback. Dettagli in [[16 - Rapporto Fase 6]].

## Politica dei test

- F0 valida dati e assunzioni con fixture riproducibili.
- F1 costruisce l'infrastruttura di test e i gate automatici.
- Da F2 in poi ogni fase aggiunge i propri test e riesegue l'intera suite accumulata come non regressione.
- I provider reali vengono verificati con smoke test controllati; i test deterministici usano fake e fixture versionate.
- I flussi critici Android vengono provati su emulatore gestito con Compose UI/UI Automator; screenshot e artefatti di test restano consultabili.
- Playwright entra solo con una futura superficie browser/WebView. Il 3D usa Blender headless, glTF Validator, render golden e prova su dispositivo.

## Criteri per non espandere troppo il progetto

Una funzione entra nel backlog solo se migliora direttamente: (a) trovare specie lungo un percorso, (b) capire perché sono state mostrate, oppure (c) esplorare la scheda dell'animale. Account, social, AI di riconoscimento e copertura mondiale restano fuori finché l'MVP non è piacevole e affidabile.

Per la sequenza operativa dettagliata vedere [[09 - Piano di sviluppo dettagliato]].
