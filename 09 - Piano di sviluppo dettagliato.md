# Piano di sviluppo dettagliato

Questo piano definisce fasi ordinate, dipendenze, artefatti, test e gate. Gli esiti datati conservano la storia delle verifiche; F0–F13 sono completate. F14 è implementata con gate automatizzato verde; la chiusura formale attende revisione umana e prova su telefono. F15 e F16 rimangono aperte. F17 è stata anticipata su richiesta il 2026-10-03 con catalogo curato incluso e consultazione locale: [[33 - Rapporto Fase 17]]. Questo incremento non chiude i pacchetti mappe/gazetteer F15 né la decisione di sincronizzazione F16.

## Estensioni funzionali approvate il 2026-09-20

Questa sezione integra attività e gate delle fasi indicate. In caso di contrasto funzionale prevale sui precedenti dettagli: i gate originali rimangono richiesti. F2–F6 non vengono riaperte retroattivamente; i nuovi modelli e le migrazioni si realizzano nelle fasi che introducono le nuove funzioni.

### F7 — Periodo del viaggio e osservabilità

- Accettare le date previste del viaggio come periodo dell'analisi, distinto dalla data corrente; spiegare eventuali variazioni stagionali all'interno dell'intervallo.
- Separare evidenza di presenza e facilità di osservazione. Aggiungere habitat da cercare, periodo e fascia oraria solo se documentati; in assenza di dati mostrare “non disponibile”, senza inventare probabilità.
- **Gate aggiuntivo:** stessa area con periodi diversi usa i rispettivi segnali stagionali; dati di osservabilità mancanti non alterano il livello di evidenza né generano indicazioni arbitrarie.

### F8A — Esplorazione e scheda essenziale

- Selezionare il periodo di analisi anche senza viaggio salvato e applicare i segnali stagionali F7.
- Anticipare da F13 nomi, immagine o fallback, riconoscimento documentato e sintesi habitat/periodo.
- **Gate aggiuntivo:** luogo/percorso + periodo → risultati → mappa → scheda essenziale; cambio periodo e dati mancanti gestiti esplicitamente.

### F8B — Viaggio salvato e diario collegato

- Estensione del 2026-10-02: tappe libere ordinate, ciascuna con località, giorno e percorso scelto. Consentire inserimento, riordino e rimozione; date comprese nel viaggio e non decrescenti, anche più tappe nello stesso giorno. Offrire vista/analisi del viaggio completo o del singolo tratto nel suo giorno, Maps della tappa e conservazione dei risultati precedenti con invalidazione esplicita. Dettagli in [[23 - Tappe e giorni del viaggio]].
- Rifinitura concordata e precisata: Viaggi è l'ingresso principale; partenza, destinazione, date e tracciato scelto sulla mappa interna, con nome automatico e opzioni facoltative. Risultati resta uno strumento di esplorazione senza viaggio, Percorsi un'importazione facoltativa raggiungibile da Viaggi.
- Calcolare percorsi in auto con OSRM solo su richiesta, inviando i due estremi autorizzati, con rate limit/cache/errori e geometria completa. Salvare la scelta nel viaggio, invalidarla quando cambia un estremo e mantenere leggibili i viaggi precedenti senza traccia.
- Aggiungere le indicazioni Google Maps con partenza/destinazione, su azione esplicita; Maps ricalcola le proprie indicazioni e non restituisce la traccia. Gestire assenza di app/browser senza bloccare i dati locali. Le uscite conservano il link al luogo; date e tracciato restano in Faunavia.
- Cercare il catalogo generale direttamente nell'editor del Diario con debounce, retry e scelte locali se la rete manca. Persistenza del taxon/alias dopo selezione esplicita, prima dell'avvistamento; aggiunta manuale dal viaggio anche senza risultato o coordinate.
- Creare/modificare/eliminare un viaggio con nome, destinazione confermata, date, raggio di spostamento e gruppi animali di interesse. Consentire comunque l'esplorazione senza viaggio.
- Introdurre uscite salvate nel viaggio, anche manuali o basate su percorsi importati; la scoperta di sentieri nuovi resta F17.
- Salvare luoghi e risultati scelti, con data e provenienza, e ricalcolare esplicitamente i suggerimenti se cambiano date o area. Dichiarare i limiti di copertura: ricerca geografica e disponibilità di analisi sono distinte.
- Riutilizzare la scheda essenziale F8A nei viaggi; F13 aggiunge gli approfondimenti.
- Aggiungere “L'ho visto” da risultato/scheda: precompilare taxon e collegamento al viaggio/uscita, richiedendo conferma di data e posizione effettive. Non copiare automaticamente il punto di un'osservazione esterna come luogo dell'avvistamento personale.
- Collegare osservazioni a viaggio/uscita e consultarle per calendario, mappa e filtro specie; le osservazioni senza coordinate restano in lista/calendario. Riepilogare specie e prime osservazioni personali, aggiornandole dopo modifiche o cancellazioni.
- Introdurre bozze persistenti “da identificare” con data, luogo opzionale e note. Restano separate dalle osservazioni con taxon obbligatorio e dai conteggi di specie osservate. Permettere modifica, eliminazione e conversione atomica dopo scelta del taxon, senza duplicare il ricordo. Le foto arrivano in F10.
- Eliminare viaggio/uscita scollega ma non elimina osservazioni e bozze; introdurre migrazioni non distruttive per i nuovi dati.
- **Gate aggiuntivo:** viaggio → risultati per le date scelte → scheda → conferma osservazione → riepilogo; riavvio/offline conserva viaggi e bozze; conversione bozza senza duplicati; cancellazione viaggio senza perdita del diario; filtri/calendario/mappa e migrazione del diario preesistente verificati. Le bozze non entrano nei conteggi delle notifiche F11.

### F9 — Lista desideri e suggerimenti personali

- Correzione approvata 2026-10-02: titoli leggibili, recupero del nome italiano quando disponibile con identità esatta, analisi e risultati conservati durante la rotazione anche nei viaggi. Habitat apre la distribuzione nell’app per tutte le specie; areale illustrato Commons verificato via Wikidata quando disponibile e mappa GBIF delle segnalazioni come alternativa dichiarata. Curiosità restano una sezione cliccabile F13. Implementazione e verifiche in [[26 - Nomi, rotazione e mappe interne]].

- Rifinitura approvata 2026-10-02: ricerca del viaggio e ricerca libera aperte su “Animali tipici”, con vista completa secondaria. Usare una curatela versionata ampliata, non il numero di record; specie urbane comuni escluse soltanto dalla selezione tipica, motivazione e livello delle evidenze consultabili. Schede compatte: nome comune quando disponibile, nome scientifico, habitat e stagionalità; spiegazioni e fonti in un dettaglio espandibile. Habitat cliccabile apre una mappa generale di distribuzione verificata, distinta dalla mappa del viaggio; dato mancante e apertura fallita restano espliciti. I risultati già scelti dall'utente restano conservati e consultabili.
- Salvare/rimuovere taxa dalla lista “vorrei vederlo” e indicarne la pertinenza al viaggio usando le evidenze disponibili.
- Offrire viste “tipici del luogo”, “più facili da osservare” e “mai osservati da me”, con motivazioni e ordinamento stabile. L'ultima vista dipende solo da osservazioni personali identificate, non da bozze o evidenze esterne.
- Le specie urbane comuni possono essere de-prioritizzate nella vista dei taxa tipici, senza esclusione rigida nelle altre viste. Se manca una base per stimare l'osservabilità, dichiararlo invece di simulare un ordinamento affidabile.
- **Gate aggiuntivo:** desideri persistenti offline; specie comune ma nuova per l'utente selezionabile; modifica/eliminazione del diario aggiorna la vista dei mai osservati; nessuna vista modifica la classificazione dell'evidenza.

### F10–F12 — Ricordi e recuperabilità

- F10: consentire foto anche nelle bozze da identificare, preservandole durante la conversione; riutilizzare le regole di privacy e gestione file del diario.
- F11: riepiloghi e notifiche contano solo osservazioni identificate, escludendo le bozze.
- F12: includere viaggi, uscite, luoghi/risultati salvati, desideri, bozze, foto e collegamenti nel backup versionato. Prevedere l'importazione degli archivi precedenti, senza inventare dati mancanti.
- **Gate aggiuntivo:** conversione bozza con foto senza perdita/orfani; round-trip completo e import di archivio precedente; rollback conserva tutti i dati e collegamenti.

### F14 — Libreria personale di modelli e animazioni

- Oltre al primo asset incluso, offrire importazione locale di GLB, associazione a un taxon, anteprima, sostituzione e rimozione. Raccogliere provenienza e licenza; un file non valido non sostituisce un asset funzionante.
- Per file con animazioni supportate mostrare le clip disponibili, selezione e riproduci/pausa; per file statici mantenere rotazione e zoom. Creare o animare modelli dentro l'app resta fuori perimetro.
- La rimozione di un asset non elimina scheda o osservazioni. Modelli personali e metadati devono essere recuperabili tramite estensione versionata del backup F12.
- **Gate aggiuntivo:** import statico/animato, clip assenti/multiple/non supportate, sostituzione fallita, riavvio, rimozione e backup/ripristino; fallback 2D e diario sempre utilizzabili. Restano obbligatori i controlli di provenienza, validità e prestazioni della fase.

### F15 — Prepara il viaggio offline

- Un'azione raccoglie luoghi, risultati con provenienza/data, schede essenziali e mappe autorizzate del viaggio; F17 estende il pacchetto alle uscite suggerite. Non promettere nuove analisi offline se i dati richiesti non sono inclusi.
- Mostrare copertura, dimensione prevista, avanzamento, data dei dati e stato completo/parziale; consentire aggiornamento e rimozione senza cancellare diario, viaggi, desideri o asset personali.
- **Gate aggiuntivo:** in modalità aereo viaggio, risultati e schede preparati sono consultabili; download interrotto o spazio insufficiente non dichiarano il viaggio pronto; aree fuori copertura e materiali mancanti hanno un fallback esplicito.

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

**Esito 2026-09-20:** completata e verificata. Il modulo `:core:route` importa GPX e GeoJSON Point/LineString/MultiLineString, conserva i segmenti, campiona per distanza, costruisce porzioni di corridoio, celle metriche EPSG:3035, chunk e fingerprint. La UI Percorsi usa il picker Android e offre anche coordinate WGS84 manuali. I test deterministici F5, build, lint e flussi Android Percorsi sono verdi. Dettagli in [[15 - Rapporto Fase 5]].

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

**Esito 2026-09-20:** completata e verificata. `:core:occurrence` normalizza le risposte iniziali GBIF e NNB WFS, limita paginazione e retry, conserva licenza/attribuzione/query/timestamp/precisione e non ricostruisce coordinate generalizzate. Il gateway usa il fingerprint F5 e l'insieme degli adapter come chiave di cache, restituisce fresh/network/stale in modo esplicito e deduplica soltanto `(provider, recordId)`. Room v5 aggiunge la cache persistente normalizzata con migrazione v4→v5. Sei test JVM F6, test Room v5, gate device/visual e smoke online sono verdi. Dettagli in [[16 - Rapporto Fase 6]].

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

**Esito 2026-09-20:** implementata nel working tree e in verifica. `:core:plausibility` rende deterministica la regola di F7, separa osservabilità e presenza, usa gli adapter Article 12/17, MAES e CLCplus con errori/provenienza espliciti e non traduce classi CLCplus in MAES senza un crosswalk scientificamente curato. I 15 test JVM F7 coprono matrice, fonti mancanti, areale confinante, habitat misto, dati vecchi, Natura 2000, fixture e ripetibilità. La suite F0 e i controlli statici sono verdi; il gate Gradle cumulativo deve ancora avviarsi fuori dal runner che nega il loopback.

## F8A — Esplorazione naturalistica, mappa e scheda essenziale

**Obiettivo:** completare il flusso nome/coordinate/GPX/GeoJSON + periodo → area confermata → analisi → risultati spiegati → mappa → scheda essenziale, senza richiedere un viaggio salvato.

**Dipendenze:** F4, F7.

**Attività e artefatti:**

1. Integrare MapLibre dietro un map adapter.
2. Integrare un geographic search adapter per paese, regione e città, con debounce, cache, attribuzione e conferma del risultato.
3. Mostrare percorso, corridoio, campioni, evidenze, livello e filtri.
4. Rendere attribuzioni sempre visibili e coordinate sensibili prudenti.
5. Consentire lista e diario anche quando mappa o rete non sono disponibili.
6. Collegare risultato, spiegazione, fonte e taxon senza ancora dipendere dal 3D.

**Test della fase:** end-to-end con GPX/GeoJSON, coordinate e nomi geografici (risultato unico, ambiguo, vuoto e provider indisponibile) e provider fake; Compose UI dei filtri e della conferma luogo; screenshot golden di mappa/lista/errore; attribuzione; map/geocoder adapter fake; rotazione e ricreazione processo; assenza rete e tile; accessibilità dei livelli di evidenza.

**Non regressione:** suite completa F1–F7; sentinel GPX → fingerprint → fixture provider → classificazione attesa; diario e ricerca restano utilizzabili senza mappa.

**Gate di completamento:** il primo vertical slice funziona su emulatore da luogo/percorso e periodo al risultato spiegato e alla scheda essenziale, inclusi i gate aggiuntivi F8A, con report e screenshot automatici.

## F8B — Viaggi e diario collegato

**Obiettivo:** conservare la preparazione della vacanza e collegarla ai ricordi personali.

**Dipendenze:** F8A e diario F4.

**Attività e artefatti:**

1. Implementare viaggi con partenza, destinazione, date, tracciato scelto sulla mappa, raggio e interessi e uscite manuali o da percorsi importati. Conservare la geometria completa con fonte/data/licenza; i viaggi precedenti senza traccia rimangono leggibili.
2. Salvare luoghi e risultati con provenienza; riutilizzare F8A e aggiornare esplicitamente l'analisi al cambio di date o destinazione.
3. Collegare diario e uscite; aggiungere “L'ho visto” con conferma dei dati effettivi.
4. Introdurre bozze persistenti separate dalle osservazioni identificate; foto in F10.
5. Aggiungere calendario, mappa del diario, filtri specie e riepiloghi delle prime osservazioni personali.
6. Applicare migrazioni non distruttive; eliminare viaggi/uscite scollega senza cancellare i ricordi.
7. Rifinire la navigazione attorno a Viaggi e rendere facoltativi nome, coordinate manuali, raggio, interessi e importazione percorso. Calcolare e scegliere il tracciato interno tramite OSRM su richiesta; aggiungere le indicazioni esterne Maps e il fallback dei vecchi viaggi. Il payload JSON v2 resta nello schema Room 6.
8. Integrare la ricerca completa del catalogo nell'editor Diario, con selezione persistita e fallback locale; verificare creazione con estremi/date/traccia, avvistamento diretto collegato, nomi comuni reali, errori/retry del catalogo e fallimento apertura Maps.

**Test della fase:** casi F8B delle estensioni approvate, inclusi riavvio/offline, conversione atomica delle bozze, collegamenti, cancellazioni, filtri e migrazione del diario esistente.

**Non regressione:** suite completa F1–F8A; esplorazione senza viaggio, scheda essenziale e diario preesistente restano utilizzabili.

**Gate di completamento:** viaggio → risultati → uscita → osservazione/bozza → diario e riepilogo funzionano dopo riavvio; cancellare un viaggio non elimina ricordi. F9 parte solo dopo il gate F8B.

**Esito F8B:** completata e verificata con `verifyAll --no-daemon`: 13 test F0, 71 JVM e 54 Android, senza errori o test omessi. Dettagli e limiti in [[20 - Rapporto Fase 8B]], recupero in [[GUIDA-FASE-8B]]. F9 è stata completata successivamente, come documentato in [[24 - Rapporto Fase 9]].

**Prima rifinitura F8B:** verificata con 13 F0, 73 JVM e 60 Android; dettagli storici in [[21 - Rifinitura Viaggi e Diario]]. La successiva precisazione aggiunge partenza e tracciato completo scelto nella mappa interna e corregge la ricerca comune del Catalogo; stato e verifiche in [[22 - Tracciato viaggio e Catalogo]]. F9 e F13 conservano il perimetro precedente.

## F9 — Suggerimenti peculiari del luogo

**Obiettivo:** offrire una selezione curata senza confonderla con il catalogo generale o con tutte le occorrenze.

**Dipendenze:** F3, F7, F8A e F8B.

**Attività e artefatti:**

1. Versionare `SuggestionProfile` con area, habitat, `urbanCommon`, `distinctivenessScore`, motivazione e fonte.
2. Escludere o de-prioritizzare specie urbane comuni tramite regole esplicite.
3. Mostrare sempre la motivazione e il carattere non esaustivo della lista.
4. Lasciare invariata la possibilità di registrare qualsiasi taxon Animalia accettato.

**Test della fase:** profili curati validi/invalidi; soglia di peculiarità; esclusione `urbanCommon`; ordinamento stabile; motivazione e fonte obbligatorie; lista vuota; specie esclusa dai suggerimenti ma salvabile nel diario.

**Non regressione:** suite completa F1–F8B (incluse F8A e F8B); sentinel sulla separazione fra suggerimenti, catalogo, evidenze e diario; vertical slice cartografico invariato.

**Gate di completamento:** i suggerimenti sono motivati, riproducibili e non limitano il diario.

**Esito F9:** completata con desideri offline e quattro viste personali; profili pilota versionati, nessuna alterazione dei livelli e osservabilità confrontabile dichiarata non disponibile. Gate finale `verifyAll --no-daemon` verde: 13 F0, 93 JVM, 82 Android, zero errori/skipped/omissioni, lint, confini, formattazione e tre firme visive. APK `artifacts/Faunavia-f9-debug.apk`, 0.9.0-f9 (13). Uso e limiti in [[GUIDA-FASE-9]]; evoluzioni specifiche dei suggerimenti/desideri in [[24 - Rapporto Fase 9]].

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

**Stato:** completata e verificata. `verifyAll`: 13 F0, 115 JVM, 123 Android, zero errori/skipped/omissioni. Rapporto [[29 - Rapporto Fase 11]], uso [[GUIDA-FASE-11]]; riavvio simulato tramite persistenza, nessuna promessa al minuto.

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

**Esito F12 — 2026-10-03:** completata e verificata nella build 0.12.0-f12 (19), Room 9. SAF export/import, ZIP con manifest/hash/inventario, staging Room e immagini, anteprima, sostituzione transazionale, rollback e protezione delle modifiche accodate. Tutte le 19 tabelle e copie fotografiche incluse; preferenze/registro notifiche conservati e pianificazione ricreata. Gate cumulativo verde: 13 F0, 115 JVM, 136 Android, zero errori/skipped/omissioni e tre firme visive. Compatibilità schema 8 provata con fixture sintetica, nessun archivio precedentemente distribuito. Dettagli e limiti in [[30 - Rapporto Fase 12]], uso in [[GUIDA-FASE-12]]. F13 rimane la fase successiva.

## F13 — Scheda specie e fallback 2D

**Obiettivo:** fornire una scheda utile e tracciabile prima di introdurre il renderer 3D.

**Dipendenze:** F3, F7, F8A e F8B.

**Attività e artefatti:**

1. Definire contenuti, fonti e versionamento del profilo specie.
2. Mostrare nomi, habitat, stagionalità, dimensioni, dieta, comportamento e note di sicurezza/conservazione.
   - Mantenere compatta la scheda iniziale: nomi, habitat cliccabile verso distribuzione geografica e stagionalità. Gli approfondimenti si aprono su richiesta.
   - Aggiungere **Curiosità** come sezione cliccabile/espandibile, non un blocco sempre aperto. Comprende fatti verificati su comportamento, adattamenti e particolarità; fonte/data e stato non disponibile obbligatori, nessuna generazione non documentata.
   - Non era stata fissata una fonte unica per le curiosità. Piano di curatela: schede ufficiali di enti parco e Lipu come fonti fattuali prioritarie; Wikipedia/Wikidata come supporto complementare collegato e verificato, con riferimento alla fonte del singolo fatto. Prima di importare testi o immagini verificare la licenza specifica e conservarne attribuzione/versione. Nessun nuovo adapter automatico per curiosità è incluso nella rifinitura F9.
3. Collegare spiegazione di evidenza e provenienza.
4. Offrire immagine o silhouette 2D accessibile e sempre disponibile come fallback.
5. Conservare offline le schede già viste.

**Test della fase:** profilo completo/parziale; fonte mancante; cache offline; link; testo lungo; localizzazione; screen reader e contrasto; screenshot golden; fallback assente/corrotto gestito senza crash.

**Non regressione:** suite completa F1–F12; sentinel risultato → scheda → ritorno alla mappa; diario e backup non dipendono dal profilo remoto.

**Gate di completamento:** ogni specie selezionabile ha una scheda leggibile o un fallback esplicito senza dipendenza dal 3D.

**Esito F13 — 2026-10-03:** completata e verificata nella build 0.13.0-f13 (20), Room 9/backup formato 1. Schede condivise, dodici profili con fatti originali da fonti primarie, provenienza per campo/nome, curiosità cliccabili e dati mancanti espliciti. Simbolo 2D generico originale e recupero Canvas, cache normalizzata distinta dai dati durevoli. Gate cumulativo verde: 13 F0, 123 JVM, 144 Android, zero errori/skipped/omissioni e quattro firme visive. Testo renderizzato a 1,8× misurato, link/fallback/recreation e sentinel risultati/mappa/diario/backup verificati su API 36; nessun telefono o TalkBack reale. Dettagli in [[31 - Rapporto Fase 13]], uso in [[GUIDA-FASE-13]]. F14 è la prossima fase.

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

**Esito implementazione F14 — 2026-10-03:** build 0.14.0-f14 (21), Room 10 e backup 2 con reader formato 1. Merlo originale procedurale, sorgente .blend/script/reference/preview, GLB ripetibile di 172.764 byte con due clip e Khronos zero errori/warning. Viewer lazy Filament 1.77.1, gesti, clip/pausa, libreria GLB personale con crediti e sostituzione controllata, copie private e backup completo. Verifica cumulativa automatica verde in 10m 31s: 13 F0, 123 JVM, 155 Android, zero errori/skipped/omissioni e cinque firme visive. APK 86.635.610 byte, firma identica a F13. **Gate formale aperto:** review umana anatomica/visiva/dei termini di distribuzione e test su almeno un telefono fisico non eseguiti; non sono dichiarati approvati. Dettagli in [[32 - Rapporto Fase 14]], uso in [[GUIDA-FASE-14]].

## F15 — Pacchetto mappa regionale offline

**Obiettivo:** analizzare e consultare un'area pilota, anche cercata per nome, senza tile o geocoder online e nel rispetto delle licenze.

**Dipendenze:** F8A, F8B e decisione sull'area pilota.

**Attività e artefatti:**

1. Scegliere una sorgente che autorizzi esplicitamente l'uso offline; non fare bulk download delle tile standard OSM.
2. Limitare regione, zoom, dimensione e frequenza di aggiornamento.
3. Versionare manifest, licenza, hash e provenienza del pacchetto.
4. Implementare import, attivazione, aggiornamento e cancellazione recuperabile.
5. Includere un gazetteer regionale versionato per la ricerca di nomi coperti dal pacchetto.
6. Mantenere lista, coordinate e diario disponibili senza pacchetto.

**Test della fase:** licenza/manifest; pacchetto valido/corrotto; spazio insufficiente; aggiornamento/rollback; ricerca per nome dentro/fuori copertura; regione fuori copertura; assenza totale rete; memoria e prestazioni; cancellazione; attribuzione visibile.

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

## F17 — Scoperta di luoghi e sentieri naturalistici

**Obiettivo:** partire dalla destinazione e dalle date per scegliere dove andare a osservare animali, senza dover già possedere una traccia.

**Dipendenze:** viaggi e uscite F8B, suggerimenti F9, backup F12 e preparazione offline F15. F16 può essere chiusa senza backend.

**Attività e artefatti:**

1. Selezionare fonti riutilizzabili per un'area pilota di punti di osservazione e sentieri; iniziare anche con un catalogo curato e delimitato. Non derivare un sentiero percorribile dai soli punti di presenza animale.
2. Proporre uscite pertinenti a destinazione, date, raggio di spostamento, interessi e lista desideri, motivando il collegamento con le specie e il livello delle evidenze.
3. Mostrare punto di partenza, lunghezza, durata indicativa, difficoltà e distanza dalla base quando documentati; distinguere distanza geografica e tempo di trasferimento, senza inventare quest'ultimo. Indicare fonte/data ed eventuali informazioni di accessibilità o chiusura disponibili; dato assente non significa accesso garantito.
4. Confrontare e filtrare le uscite; salvare una proposta nel viaggio, visualizzarne la geometria quando disponibile e collegare osservazioni/bozze all'uscita. Conservare la proposta salvata anche se la fonte diventa indisponibile, indicandone l'anzianità.
5. Estendere backup e pacchetto offline ai dettagli e alle geometrie delle uscite consentite dalle licenze. Rispettare la precisione delle coordinate e la protezione delle località sensibili già prevista per le evidenze.
6. Gestire copertura assente o dati insufficienti con luoghi documentati, percorsi importati e pianificazione manuale. Restano esclusi navigazione turn-by-turn, generazione automatica di sentieri e copertura mondiale implicita.

**Test della fase:** destinazione con/senza copertura; date/interessi diversi; campi mancanti; proposta senza traccia; fonte indisponibile; dati datati; geometrie e coordinate sensibili; ordinamento riproducibile; salvataggio senza duplicati; uscita → osservazione/bozza → diario; backup e consultazione in modalità aereo.

**Non regressione:** suite cumulativa F1–F16 per le funzioni implementate; viaggio, desideri, diario e import di percorsi rimangono indipendenti dalla disponibilità del catalogo sentieri.

**Gate di completamento:** nell'area pilota una persona inserisce destinazione e periodo, confronta uscite motivate, ne salva una, consulta i materiali preparati offline e registra un ricordo collegato senza confondere suggerimento e avvistamento.

## Sequenza e traguardi

```text
F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8A → F8B
                                                    ↓
F9 → F10 → F11 → F12 → F13 → F14 → F15 → F16 → F17
```

- **Primo valore locale:** F4, diario offline con specie obbligatoria.
- **Primo vertical slice naturalistico:** F8A, luogo/percorso e periodo, risultati spiegati, mappa e scheda essenziale.
- **Vacanza e ricordi collegati:** F8B, viaggi e uscite salvati, diario collegato e bozze persistenti.
- **MVP completo definito nei requisiti:** F14, con un GLB e fallback 2D.
- **Estensioni post-MVP:** F15 viaggio offline, F16 valutazione sync e F17 scoperta di luoghi/sentieri.

## Strumenti durante lo sviluppo

- Gradle e Android Gradle Managed Devices orchestrano build e test ripetibili.
- Test JVM, Room instrumentation, Compose UI, UI Automator, screenshot golden e ADB coprono logica, persistenza e interazione nativa.
- Un Mobile/Android MCP può pilotare emulatore o dispositivo per ispezioni esplorative e controllo schermo; i test di accettazione restano comunque script ripetibili nel repository.
- Playwright MCP serve soltanto se viene introdotta una superficie browser o WebView; non è lo strumento primario per un'app Android nativa.
- Blender headless + Python + Khronos glTF Validator costituiscono la pipeline 3D riproducibile. Un Blender MCP è opzionale e va abilitato solo dopo verifica di sicurezza, permessi e telemetria.
- Il controllo schermo di Codex può aiutare la verifica esplorativa; non sostituisce i gate automatici né i test di non regressione.
