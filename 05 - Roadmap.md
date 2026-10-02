# Roadmap

Estensione della pianificazione F8B del 2026-10-02: [[23 - Tappe e giorni del viaggio]], con località libere, ordine, giorni e analisi del singolo tratto.

La roadmap contiene 19 fasi operative, da F0 a F17 con F8 suddivisa in F8A e F8B, chiuse da gate verificabili. Una fase produce un incremento utilizzabile e non si apre la successiva finché build, test propri e gate di non regressione richiesti non sono verdi. F0–F8B sono completate e verificate; le estensioni delle fasi successive rimangono pianificate e non cambiano retroattivamente gli esiti delle fasi precedenti. Esiti F8B in [[20 - Rapporto Fase 8B]].

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
| F8A | esplorazione naturalistica | luogo/percorso + periodo → risultati spiegati → mappa → scheda essenziale |
| F8B | viaggi e diario collegato | viaggio/uscita → osservazione o bozza → calendario e riepilogo |
| F9 | suggerimenti peculiari | distinzione dai taxa comuni e dal catalogo generale |
| F10 | foto locali | Photo Picker, copie controllate, privacy e gestione errori |
| F11 | notifica flessibile | WorkManager, fuso, permessi, zero notifiche vuote |
| F12 | export/import | archivio verificato, ripristino e rollback sicuro |
| F13 | scheda specie e fallback 2D | contenuto tracciabile, accessibile e disponibile dalla cache |
| F14 | pipeline e primo asset 3D | GLB validato, manifest, budget mobile e fallback integro |
| F15 | mappa e gazetteer regionali offline | licenza, pacchetto versionato, ricerca per nome, import/cancellazione |
| F16 | sincronizzazione opzionale | decisione architetturale motivata prima di qualsiasi backend |
| F17 | scoperta di luoghi e sentieri naturalistici | destinazione/date/interessi → uscite documentate → viaggio → diario |

## Esperienza funzionale prevista

La rifinitura F8B rende Viaggi l'ingresso principale, con partenza, destinazione, date e scelta del tracciato sulla mappa interna, indicazioni esterne Google Maps e ricerca del catalogo generale dentro il Diario. Esplorazione senza viaggio, importazione di percorsi, raggio e interessi restano facoltativi. La precisazione del percorso completo e la correzione del Catalogo sono documentate in [[22 - Tracciato viaggio e Catalogo]]. Le fasi F9 e F13 mantengono il perimetro già concordato; queste correzioni sono assegnate alla F8B, non rinviate alle schede complete.

Il flusso principale è: preparo un viaggio → scelgo animali e luoghi → osservo → conservo i ricordi. La copertura iniziale rimane regionale/europea entro i limiti dichiarati; una destinazione ricercabile non implica copertura naturalistica disponibile.

- **F7–F8B, viaggio e suggerimenti pratici:** viaggio salvato con destinazione confermata, date, raggio di spostamento e gruppi animali di interesse; risultati riferiti alle date della vacanza, zone e habitat consigliati, periodo e fascia oraria quando documentati. Presenza documentata/plausibile e facilità di osservazione sono informazioni distinte, senza percentuali inventate.
- **F8A, scheda essenziale; F8B, diario collegato:** anticipare nomi, immagine disponibile, caratteri di riconoscimento e informazioni essenziali; F13 completa la scheda. Il pulsante “L'ho visto” precompila un'osservazione da confermare. Viaggi e uscite raccolgono osservazioni consultabili per calendario, mappa e specie, con riepilogo delle prime osservazioni personali.
- **F8B–F10, bozze da identificare:** appunti persistenti separati dalle osservazioni identificate, con data, luogo, note e successivamente foto; conversione in osservazione solo dopo selezione di un taxon valido. Non modificare il vincolo della specie obbligatoria nelle osservazioni.
- **F9, desideri e personalizzazione:** lista “vorrei vederlo” e viste “tipici del luogo”, “più facili da osservare” e “mai osservati da me”. Le specie comuni sono de-prioritizzabili, non escluse rigidamente da ogni vista.
- **F12, backup completo:** includere viaggi, uscite, collegamenti, desideri e bozze con foto, oltre al diario esistente.
- **F14, contenuti 3D personali:** importare un modello, associarlo a una specie, visualizzarlo, sostituirlo e rimuoverlo; per modelli animati selezionare una clip e riprodurla/metterla in pausa. Il fallback 2D resta disponibile.
- **F15, prepara il viaggio offline:** salvare insieme luoghi, risultati, schede e mappe autorizzate, con copertura, data dei dati, dimensione e stato di disponibilità espliciti.
- **F17, suggerire uscite:** scoprire punti di osservazione e sentieri anche senza importare una traccia, confrontando durata, difficoltà, distanza dalla base e specie pertinenti quando i dati sono disponibili. Salvare l'uscita nel viaggio e collegarla al diario; nessuna navigazione turn-by-turn richiesta.

F8A permette di scegliere il periodo anche senza salvare un viaggio; F8B conserva destinazione, date e preferenze e le collega al diario. F9 parte dopo F8B. Le fasi successive mantengono la numerazione, inclusa F17 per i sentieri.

Priorità di prodotto: viaggio, suggerimenti utilizzabili e diario collegato prima degli arricchimenti 3D; scoperta automatica dei sentieri nella nuova F17. I dettagli e i gate delle estensioni sono nel piano di sviluppo.

## Stato corrente

F3 è completata: il Catalogo cerca il primo provider GBIF con debounce, risolve i sinonimi al taxon Animalia accettato e conserva localmente solo le scelte esplicite con relativi nomi ricercabili. Le scelte restano utilizzabili offline; i suggerimenti remoti in memoria hanno una scadenza controllata. Il dettaglio è in [[14 - Rapporto Fase 3]].

F4 è completata e verificata: il Diario crea, modifica ed elimina avvistamenti offline, richiede un taxon selezionato e registra data/ora locale, quantità, note e coordinate opzionali. La migrazione Room v3→v4 aggiunge la quantità preservando i record esistenti come un esemplare. Il flusso Compose di creazione, modifica, eliminazione e validazione della specie è verde nel gate Android cumulativo.

F5 è completata e verificata: la schermata Percorsi importa GPX/GeoJSON tramite il picker Android oppure analizza coordinate WGS84, conserva localmente la geometria normalizzata e mostra lunghezza, campioni, celle, chunk e fingerprint. Il modulo puro Kotlin `:core:route` usa EPSG:3035 con celle metriche da 1 km e chunk da 5 km; preserva i segmenti, deduplica per geometria e genera fingerprint di ricerca sensibili alla configurazione. I 13 test F5 e i flussi Android Percorsi sono verdi. Dettagli in [[15 - Rapporto Fase 5]].

F6 è completata e verificata: il modulo puro Kotlin `:core:occurrence` espone un gateway comune per GBIF e NNB WFS, riceve dal motore F5 solo porzioni/celle aggregate, usa paginazione limitata e deduplica per provider più identificativo del record. GBIF preferisce poligoni e può ripiegare sui bounding box; NNB usa bounding box WFS. La cache Room v5 conserva record normalizzati, timestamp e scadenza, mentre un refresh fallito restituisce esplicitamente la cache stale con l'errore del provider. I sei test F6, la migrazione/cache Room v5, `verifyAll` e gli smoke online GBIF/NNB sono verdi. Dettagli in [[16 - Rapporto Fase 6]].

## Politica dei test

- F0 valida dati e assunzioni con fixture riproducibili.
- F1 costruisce l'infrastruttura di test e i gate automatici.
- Da F2 in poi ogni fase aggiunge i propri test e riesegue l'intera suite accumulata come non regressione.
- I provider reali vengono verificati con smoke test controllati; i test deterministici usano fake e fixture versionate.
- I flussi critici Android vengono provati su emulatore gestito con Compose UI/UI Automator; screenshot e artefatti di test restano consultabili.
- Playwright entra solo con una futura superficie browser/WebView. Il 3D usa Blender headless, glTF Validator, render golden e prova su dispositivo.

## Criteri per non espandere troppo il progetto

Una funzione entra nel backlog solo se migliora direttamente: (a) preparare un viaggio e trovare specie o uscite pertinenti, (b) capire perché sono state mostrate, (c) registrare e ritrovare osservazioni e ricordi, oppure (d) esplorare e arricchire la scheda dell'animale. Account, social, AI di riconoscimento e copertura mondiale restano fuori finché l'MVP non è piacevole e affidabile.

Per la sequenza operativa dettagliata vedere [[09 - Piano di sviluppo dettagliato]].
