# Piano di sviluppo dettagliato

Questo piano non avvia ancora lo sviluppo. Definisce fasi ordinate, dipendenze, artefatti, test e gate necessari per iniziare senza lasciare decisioni implicite.

## Regole di esecuzione

- Ogni punto numerato è una fase di sviluppo autonoma e produce un incremento dimostrabile.
- Una fase parte soltanto quando il gate della precedente è verde.
- F0 valida assunzioni e fonti; F1 crea l'infrastruttura automatizzata; da F2 ogni fase aggiunge test propri e riesegue la suite di non regressione completa.
- I test deterministici non dipendono dalla rete: usano fake, snapshot e fixture versionate. Le API reali hanno smoke test separati e controllati.
- I dati esterni conservano fonte, query, timestamp, licenza, qualità e versione.
- Il dominio non dipende da Android, Room, HTTP, MapLibre, WorkManager o renderer 3D.
- Nessun servizio a pagamento, backend o upload di foto entra nel progetto senza una decisione esplicita.
- Una fase fallita non viene aggirata riducendo la copertura: si corregge il difetto o si documenta una nuova decisione.

## Gate automatici comuni

- `verifyFast`: compilazione, formattazione/lint, analisi statica e unit test JVM.
- `verifyDevice`: test strumentati Room, Compose UI e UI Automator su Android Gradle Managed Device.
- `verifyVisual`: confronto degli screenshot golden e report delle differenze.
- `verifyAll`: tutti i gate precedenti, validatori dati/asset ed eventuali test end-to-end.

I nomi diventano task Gradle o script equivalenti in F1. Ogni esecuzione salva report leggibili e artefatti utili a diagnosticare un errore.

## F0 — Chiusura perimetro e spike dati

**Obiettivo:** trasformare le scelte approvate in contratti verificabili prima di creare l'app.

**Dipendenze:** nessuna.

**Attività e artefatti:**

1. Scegliere percorso pilota, prima area italiana e set iniziale di gruppi animali.
2. Validare il corridoio predefinito di 1 km su 3–5 itinerari diversi.
3. Provare la strategia ibrida: porzioni semplificate + celle di griglia stabili; annotare per ogni provider se accetta poligono o bounding box.
4. Acquisire piccole fixture riproducibili da GBIF, NNB, Article 12/17, matrice specie–habitat e CLCplus.
5. Definire contratti normalizzati per taxon, occorrenza, areale, habitat, stagione e provenienza.
6. Registrare licenza, attribuzione, limiti e comportamento in errore di ogni fonte.

**Test della fase:** validazione dello schema delle fixture; replay offline; coordinate WGS84 valide; query sotto i limiti stabiliti; stesso input → stessa chiave di cella e stesso fingerprint; assenza di una fonte → risultato insufficiente, non presenza inventata.

**Gate di completamento:** dataset di prova versionato, ADR delle fonti e della strategia geografica, rischi noti e criteri di plausibilità leggibili senza consultare il codice.

**Esito 2026-09-14:** completata. Artefatti, ADR e gate automatico sono in `f0/`; il rapporto leggibile è in [[11 - Rapporto Fase 0]].

## F1 — Scaffold Android e fondazione dell'automazione

**Obiettivo:** produrre un APK vuoto ma installabile e una pipeline locale completamente automatizzata.

**Dipendenze:** F0.

**Attività e artefatti:**

1. Creare progetto Kotlin/Jetpack Compose e moduli con dipendenze verso il dominio.
2. Fissare JDK, Gradle, Android SDK, min/target SDK e version catalog.
3. Creare schermate placeholder e navigazione di base.
4. Configurare lint, analisi statica, unit test, test strumentati, Compose UI, UI Automator, screenshot golden e Android Gradle Managed Devices.
5. Implementare i quattro gate comuni e la raccolta automatica dei report.
6. Preparare fixture, fake clock, fake location e fake provider condivisi.

**Test della fase:** build debug ripetibile; installazione e avvio su emulatore; navigazione tra tutte le schermate placeholder; test JVM campione; test Compose campione; screenshot baseline; esecuzione deliberatamente fallita che dimostri la pubblicazione del report.

**Gate di completamento:** un solo comando `verifyAll` compila, installa, prova l'app e restituisce un esito non ambiguo senza operazioni manuali.

**Esito 2026-09-14:** completata. `verifyAll` esegue la regressione F0, compila l'APK, applica lint e controlli architetturali, esegue test JVM e tre test strumentati su dispositivo gestito (avvio, navigazione e golden visuale), quindi pubblica un indice dei report. Dettagli e percorsi sono in [[12 - Rapporto Fase 1]].

## F2 — Dominio, Room e repository locali

**Obiettivo:** creare la fonte dati locale e i vincoli che proteggono il diario.

**Dipendenze:** F1.

**Attività e artefatti:**

1. Modellare `Taxon`, `TaxonPreview`, `SpeciesProfile`, `SuggestionProfile`, `Observation`, `ObservationPhoto`, `Route`, `SourceEvidence` e `AppSettings`.
2. Separare entità Room, modelli di dominio, mapper e repository.
3. Rendere `taxonId` obbligatorio per ogni osservazione persistita, con riferimento a un taxon Animalia accettato.
4. Introdurre versione schema, migrazione iniziale, clock iniettabile e operazioni transazionali.
5. Definire cancellazione locale e comportamento per riferimenti mancanti.

**Test della fase:** CRUD e query Room; persistenza dopo riavvio; migrazione; rollback transazionale; date/fusi; vincoli di integrità; rifiuto di taxon nullo, non accettato o non Animalia; mapper round-trip; repository con database vuoto.

**Non regressione:** rieseguire build, installazione, navigazione, test Compose e screenshot di F1; aggiungere un sentinel test che riapre un database popolato e verifica che l'osservazione resti leggibile.

**Gate di completamento:** `verifyAll` verde e impossibilità, dimostrata dai test, di persistere un avvistamento senza specie valida.

**Esito 2026-09-16:** completata. Nove modelli di dominio, modulo `:core:local`, repository transazionali, schema Room v2 con migrazione iniziale e vincoli SQLite su taxa Animalia accettati. `verifyAll` verde: 13 test F0, 12 JVM e 13 strumentati, incluse migrazioni e riapertura del database. Dettagli in [[13 - Rapporto Fase 2]].

## F3 — Ricerca tassonomica e cache

**Obiettivo:** consentire la scelta affidabile di qualsiasi specie animale riconosciuta dal catalogo.

**Dipendenze:** F2.

**Attività e artefatti:**

1. Definire l'interfaccia del provider tassonomico e il primo adapter GBIF Species.
2. Cercare nome comune, scientifico, sinonimi e varianti con debounce e minimo caratteri.
3. Filtrare `Animalia`, privilegiare specie/sottospecie e salvare il taxon accettato stabile.
4. Conservare versione/data fonte e cache degli elementi già scelti.
5. Usare placeholder se preview o licenza immagine non sono disponibili.

**Test della fase:** fake provider per successo, vuoto, timeout e risposta malformata; sinonimo → nome accettato; esclusione di taxa non Animalia; debounce; risultati duplicati; selezione senza preview; cache offline; scadenza controllata; UI con stato loading/errore/vuoto.

**Non regressione:** suite completa F1–F2; sentinel su vincolo `taxonId`; riapertura del database; ricerca offline di un taxon precedentemente selezionato.

**Gate di completamento:** un taxon valido può essere cercato, selezionato e riletto offline senza dipendere dalla preview fotografica.

## F4 — Diario manuale

**Obiettivo:** realizzare il primo flusso utente utile completamente locale.

**Dipendenze:** F3.

**Attività e artefatti:**

1. Creare, modificare, visualizzare ed eliminare un avvistamento.
2. Richiedere la selezione di un taxon prima del salvataggio.
3. Gestire data/ora locale, posizione opzionale, quantità e note.
4. Mantenere gli avvistamenti manuali separati dalle occorrenze esterne e dai suggerimenti.
5. Conservare una ricerca incompleta solo nello stato transitorio della UI.

**Test della fase:** flusso Compose create/edit/delete; salvataggio offline; validazione specie obbligatoria; cambio fuso; quantità limite; note vuote/lunghe; posizione assente; processo ricreato; taxon non suggerito comunque registrabile.

**Non regressione:** suite completa F1–F3; sentinel su ricerca tassonomica e cache; database popolato riaperto; nessun record manuale compare tra le evidenze esterne.

**Gate di completamento:** una persona costruisce e modifica il diario offline; nessun percorso UI o repository salva un record senza specie.

## F5 — Route engine deterministico

**Obiettivo:** convertire GPX/GeoJSON o posizione in una descrizione spaziale riutilizzabile dai provider.

**Dipendenze:** F2.

**Attività e artefatti:**

1. Importare e validare GPX e GeoJSON.
2. Normalizzare WGS84, segmenti, duplicati e coordinate.
3. Campionare la traccia e costruire il corridoio configurabile.
4. Suddividere il corridoio in porzioni semplificate e celle di griglia stabili.
5. Generare fingerprint di ricerca riproducibili per cache e deduplicazione.

**Test della fase:** fixture con punto, linea, più segmenti e percorso senza tappe; file malformato; coordinate fuori intervallo; duplicati; attraversamento antimeridiano; traccia vuota; densità di campionamento; buffer; stabilità di chunk, celle e fingerprint.

**Non regressione:** suite completa F1–F4; sentinel su diario offline e specie obbligatoria; import fallito non altera Room né cancella dati esistenti.

**Gate di completamento:** ogni fixture valida produce sempre lo stesso corridoio e le stesse unità di query; ogni fixture invalida fallisce senza effetti collaterali.

## F6 — Gateway delle occorrenze e provenienza

**Obiettivo:** ottenere evidenze documentate senza accoppiare l'app a un singolo provider.

**Dipendenze:** F3, F5.

**Attività e artefatti:**

1. Definire un contratto comune e implementare gli adapter iniziali GBIF/NNB.
2. Lasciare all'adapter la scelta tra poligono e bounding box per ogni porzione.
3. Normalizzare, deduplicare e conservare query, timestamp, record, licenza e precisione.
4. Implementare rate limit, retry con backoff, cache TTL e lettura stale esplicita.
5. Separare smoke test online dai test riproducibili.

**Test della fase:** contract test comuni agli adapter; fixture HTTP; bbox/poligono fallback; paginazione; duplicati tra porzioni; timeout; 429/5xx; retry limitato; cache hit/miss/stale; provenienza completa; coordinate generalizzate non ricostruite.

**Non regressione:** suite completa F1–F5; sentinel sui fingerprint del route engine; ricerca tassonomica e diario offline; provider indisponibile non rende inutilizzabili i dati salvati.

**Gate di completamento:** un itinerario produce un insieme deduplicato di evidenze documentate e spiegabili, oppure un errore recuperabile senza perdita locale.

## F7 — Motore di plausibilità e fonti istituzionali

**Obiettivo:** distinguere in modo prudente “documentato”, “plausibile” e “insufficiente”.

**Dipendenze:** F5, F6.

**Attività e artefatti:**

1. Integrare tramite adapter range Article 12/17, matrice specie–habitat e classi CLCplus.
2. Usare Natura 2000 solo come evidenza positiva di contesto.
3. Applicare la regola: areale + habitat sono entrambi obbligatori; stagione modifica la confidenza.
4. Derivare segnali mensili GBIF/NNB solo quando mancano dati stagionali istituzionali e marcarli a qualità inferiore.
5. Generare una spiegazione strutturata con fonti e passaggi del calcolo.

**Test della fase:** matrice di casi range sì/no × habitat sì/no × stagione sì/no; fonti mancanti; areale confinante; habitat misto; dati vecchi; Natura 2000 assente; fixture Article 12/17 e CLCplus; stesso input → stesso livello e stessa spiegazione.

**Non regressione:** suite completa F1–F6; sentinel che impedisce di promuovere a plausibile una specie con solo areale o solo habitat; distinzione invariata tra osservazioni manuali ed evidenze esterne.

**Gate di completamento:** nessun caso di test sovrastima la presenza e ogni risultato visualizzabile dispone di una spiegazione tracciabile.

## F8 — Risultati, mappa online e primo vertical slice

**Obiettivo:** completare il flusso importazione → analisi → risultati spiegati → mappa.

**Dipendenze:** F4, F7.

**Attività e artefatti:**

1. Integrare MapLibre dietro un map adapter.
2. Mostrare percorso, corridoio, campioni, evidenze, livello e filtri.
3. Rendere attribuzioni sempre visibili e coordinate sensibili prudenti.
4. Consentire lista e diario anche quando mappa o rete non sono disponibili.
5. Collegare risultato, spiegazione, fonte e taxon senza ancora dipendere dal 3D.

**Test della fase:** end-to-end con GPX/GeoJSON e provider fake; Compose UI dei filtri; screenshot golden di mappa/lista/errore; attribuzione; map adapter fake; rotazione e ricreazione processo; assenza rete e tile; accessibilità dei livelli di evidenza.

**Non regressione:** suite completa F1–F7; sentinel GPX → fingerprint → fixture provider → classificazione attesa; diario e ricerca restano utilizzabili senza mappa.

**Gate di completamento:** il primo vertical slice funziona su emulatore dall'import al risultato spiegato, con report e screenshot automatici.

## F9 — Suggerimenti peculiari del luogo

**Obiettivo:** offrire una selezione curata senza confonderla con il catalogo generale o con tutte le occorrenze.

**Dipendenze:** F3, F7, F8.

**Attività e artefatti:**

1. Versionare `SuggestionProfile` con area, habitat, `urbanCommon`, `distinctivenessScore`, motivazione e fonte.
2. Escludere o de-prioritizzare specie urbane comuni tramite regole esplicite.
3. Mostrare sempre la motivazione e il carattere non esaustivo della lista.
4. Lasciare invariata la possibilità di registrare qualsiasi taxon Animalia accettato.

**Test della fase:** profili curati validi/invalidi; soglia di peculiarità; esclusione `urbanCommon`; ordinamento stabile; motivazione e fonte obbligatorie; lista vuota; specie esclusa dai suggerimenti ma salvabile nel diario.

**Non regressione:** suite completa F1–F8; sentinel sulla separazione fra suggerimenti, catalogo, evidenze e diario; vertical slice cartografico invariato.

**Gate di completamento:** i suggerimenti sono motivati, riproducibili e non limitano il diario.

## F10 — Foto locali e privacy

**Obiettivo:** allegare immagini senza introdurre upload o permessi più ampi del necessario.

**Dipendenze:** F4.

**Attività e artefatti:**

1. Integrare Android Photo Picker.
2. Copiare una versione controllata nell'area privata e generare una miniatura.
3. Registrare hash, dimensione, orientamento e relazione con l'avvistamento.
4. Rimuovere o non esportare EXIF sensibili per default.
5. Eliminare una foto senza eliminare l'avvistamento.

**Test della fase:** zero/una/più foto; URI revocato; file mancante/corrotto; immagini grandi e ruotate; hash; EXIF; eliminazione indipendente; storage insufficiente; ricreazione processo; Photo Picker tramite UI Automator dove supportato.

**Non regressione:** suite completa F1–F9; sentinel CRUD del diario con e senza foto; ricerca, import e risultati non richiedono permessi galleria.

**Gate di completamento:** le foto restano private e recuperabili localmente; ogni errore degrada al diario testuale senza perdita del record.

## F11 — Notifica locale a orario flessibile

**Obiettivo:** ricordare gli avvistamenti della giornata senza allarme esatto o backend.

**Dipendenze:** F4.

**Attività e artefatti:**

1. Salvare orario locale preferito e pianificare lavoro periodico unico con WorkManager.
2. Contare soltanto gli avvistamenti manuali della data locale corrente.
3. Notificare solo se il conteggio è maggiore di zero e aprire il riepilogo.
4. Gestire cambio orario/fuso, riavvio, risparmio energetico e permesso negato.
5. Rendere il worker idempotente e indipendente dalla rete.

**Test della fase:** fake clock; zero vs N avvistamenti; finestra temporale; cambio fuso e mezzanotte; ripianificazione; doppia esecuzione; reboot simulato; permesso negato; notifica e deep link verificati con UI Automator.

**Non regressione:** suite completa F1–F10; sentinel sul diario dopo esecuzione del worker; nessuna chiamata GBIF/NNB; nessuna notifica vuota o duplicata.

**Gate di completamento:** comportamento corretto in tutti i casi temporali senza promettere il minuto esatto e senza compromettere il diario se il permesso manca.

## F12 — Export, import e backup locale

**Obiettivo:** rendere recuperabili database e foto senza sincronizzazione cloud.

**Dipendenze:** F10, F11.

**Attività e artefatti:**

1. Definire archivio con manifest, versione schema, hash e inventario file.
2. Esportare database, foto e impostazioni con Storage Access Framework.
3. Validare un import in staging prima della sostituzione transazionale.
4. Gestire versioni incompatibili, file mancanti e spazio insufficiente.
5. Produrre un report comprensibile senza includere dati sensibili nei log.

**Test della fase:** round-trip su database vuoto/popolato; import su installazione nuova; archivio corrotto; hash errato; file mancante; versione futura; annullamento; spazio insufficiente; rollback dopo errore; conteggi e relazioni invariati.

**Non regressione:** suite completa F1–F11; sentinel su diario, foto, impostazioni e pianificazione notifica prima/dopo round-trip; import fallito lascia intatto lo stato precedente.

**Gate di completamento:** un archivio valido ripristina integralmente i dati; nessun archivio invalido modifica lo stato locale.

## F13 — Scheda specie e fallback 2D

**Obiettivo:** fornire una scheda utile e tracciabile prima di introdurre il renderer 3D.

**Dipendenze:** F3, F7, F8.

**Attività e artefatti:**

1. Definire contenuti, fonti e versionamento del profilo specie.
2. Mostrare nomi, habitat, stagionalità, dimensioni, dieta, comportamento e note di sicurezza/conservazione.
3. Collegare spiegazione di evidenza e provenienza.
4. Offrire immagine o silhouette 2D accessibile e sempre disponibile come fallback.
5. Conservare offline le schede già viste.

**Test della fase:** profilo completo/parziale; fonte mancante; cache offline; link; testo lungo; localizzazione; screen reader e contrasto; screenshot golden; fallback assente/corrotto gestito senza crash.

**Non regressione:** suite completa F1–F12; sentinel risultato → scheda → ritorno alla mappa; diario e backup non dipendono dal profilo remoto.

**Gate di completamento:** ogni specie selezionabile ha una scheda leggibile o un fallback esplicito senza dipendenza dal 3D.

## F14 — Pipeline 3D e primo asset GLB

**Obiettivo:** aggiungere il 3D come arricchimento verificato, mai come requisito per usare l'app.

**Dipendenze:** F13.

**Attività e artefatti:**

1. Versionare sorgente `.blend`, script Blender Python headless, reference board e licenze.
2. Automatizzare convenzioni, export GLB, preview e aggiornamento di `asset-manifest.json`.
3. Eseguire Khronos glTF Validator e controlli di scala, asse, pivot, poligoni, materiali, texture, hash e peso.
4. Caricare il modello lazy e liberare memoria quando la scheda viene chiusa.
5. Eseguire revisione umana anatomica, visiva e legale prima dell'approvazione.

**Test della fase:** export ripetibile; validatore glTF; manifest coerente; asset mancante/corrotto; budget dimensione/memoria/tempo; render golden; rotazione/touch; ripresa ciclo vita; test su emulatore e almeno un dispositivo reale.

**Non regressione:** suite completa F1–F13; sentinel che rimuove o corrompe il GLB e verifica scheda 2D, diario, mappa e risultati ancora funzionanti.

**Gate di completamento:** primo modello approvato, validato e performante; fallback automatico sempre verde. Solo dopo il gate si scala a 5–10 specie.

## F15 — Pacchetto mappa regionale offline

**Obiettivo:** analizzare e consultare un'area pilota senza tile online, nel rispetto delle licenze.

**Dipendenze:** F8 e decisione sull'area pilota.

**Attività e artefatti:**

1. Scegliere una sorgente che autorizzi esplicitamente l'uso offline; non fare bulk download delle tile standard OSM.
2. Limitare regione, zoom, dimensione e frequenza di aggiornamento.
3. Versionare manifest, licenza, hash e provenienza del pacchetto.
4. Implementare import, attivazione, aggiornamento e cancellazione recuperabile.
5. Mantenere lista, coordinate e diario disponibili senza pacchetto.

**Test della fase:** licenza/manifest; pacchetto valido/corrotto; spazio insufficiente; aggiornamento/rollback; regione fuori copertura; assenza totale rete; memoria e prestazioni; cancellazione; attribuzione visibile.

**Non regressione:** suite completa F1–F14; sentinel del vertical slice con rete disattivata; modalità online e fallback testuale invariati; asset 3D non caricato durante la mappa.

**Gate di completamento:** il percorso pilota funziona offline entro limiti dichiarati e la rimozione del pacchetto non elimina dati utente.

## F16 — Valutazione della sincronizzazione opzionale

**Obiettivo:** decidere se esiste un bisogno reale di backend senza indebolire il local-first.

**Dipendenze:** F12 e uso reale dell'app.

**Attività e artefatti:**

1. Raccogliere casi d'uso concreti non risolti dall'export/import.
2. Redigere un ADR con costi, privacy, conflitti, autenticazione, portabilità e strategia offline.
3. Se approvato, definire una porta di sincronizzazione separata e mantenere Room come fonte locale.
4. Escludere le foto finché costi, consenso e recuperabilità non sono risolti esplicitamente.
5. Se il bisogno non è dimostrato, chiudere la fase con decisione “nessun backend”.

**Test della fase, solo se implementata:** contract test del sync adapter; offline/online; conflitti; retry; idempotenza; cancellazione; migrazione; account assente; server irraggiungibile; nessun upload foto; export sempre disponibile.

**Non regressione:** suite completa F1–F15 in modalità local-only; sentinel che disabilita l'adapter remoto e verifica tutte le funzioni core; un errore server non modifica o perde dati locali.

**Gate di completamento:** ADR approvato. L'eventuale implementazione è accettabile solo se `verifyAll` resta verde anche senza backend.

## Sequenza e traguardi

```text
F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8
                                           ↓
F9 → F10 → F11 → F12 → F13 → F14 → F15 → F16
```

- **Primo valore locale:** F4, diario offline con specie obbligatoria.
- **Primo vertical slice naturalistico:** F8, percorso e risultati spiegati.
- **MVP completo definito nei requisiti:** F14, con un GLB e fallback 2D.
- **Estensioni post-MVP:** F15 e F16.

## Strumenti durante lo sviluppo

- Gradle e Android Gradle Managed Devices orchestrano build e test ripetibili.
- Test JVM, Room instrumentation, Compose UI, UI Automator, screenshot golden e ADB coprono logica, persistenza e interazione nativa.
- Un Mobile/Android MCP può pilotare emulatore o dispositivo per ispezioni esplorative e controllo schermo; i test di accettazione restano comunque script ripetibili nel repository.
- Playwright MCP serve soltanto se viene introdotta una superficie browser o WebView; non è lo strumento primario per un'app Android nativa.
- Blender headless + Python + Khronos glTF Validator costituiscono la pipeline 3D riproducibile. Un Blender MCP è opzionale e va abilitato solo dopo verifica di sicurezza, permessi e telemetria.
- Il controllo schermo di Codex può aiutare la verifica esplorativa; non sostituisce i gate automatici né i test di non regressione.
