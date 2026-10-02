# Correzioni F9 — nomi, rotazione e distribuzione nell’app

Intervento approvato il 2 ottobre 2026: titolo apparentemente vuoto, ricerca che riparte quando si ruota il telefono e distribuzione consultabile nell’app per tutte le specie.

## Risultato e cause

Il nome era presente nei dati, ma poteva risultare invisibile: lo Scaffold verde passava implicitamente il colore bianco ai testi delle schede chiare. Il contenuto ora usa `onBackground`; titolo, scientifico e stagione hanno anche un colore esplicito. Senza nome comune si mostra subito quello scientifico, anche durante il recupero dei metadati. Fuori dal pilota si cerca un nome **italiano** GBIF solo con identità scientifica esatta di un taxon Animalia accettato; nomi stranieri, corrispondenze fuzzy o taxa superiori non sostituiscono il titolo. Se manca, il testo lo dichiara. La cache informativa non seleziona automaticamente un taxon nel diario.

Prima della correzione, i campi e l’azione di analisi erano saveable, mentre risultato e caricamento vivevano in `remember`: la ricreazione perdeva il risultato e rilanciava l’effetto. `AnalysisViewModel` conserva ora sia risultati sia job in corso per ricerca libera e viaggio. Il token della ricerca evita avvii duplicati; cambiare esplicitamente luogo/ambito cancella la richiesta precedente. Risultati grandi non entrano nel bundle Android. La morte del processo resta distinta dalla rotazione: si conservano gli input piccoli, la cache F6 e gli snapshot salvati in Room, senza promettere la persistenza di risultati live nel processo terminato.

## Mappe per tutte le specie

Habitat è cliccabile anche fuori dal pilota, nei risultati completi e nelle schede condivise. Apre `SpeciesDistributionDialog` nell’app; nessun Intent verso Wikipedia o il browser.

- **Areale illustrato:** file Commons già revisionato per il pilota oppure mappa P181 di un singolo item Wikidata con P225 esattamente corrispondente al nome scientifico. L’immagine mantiene la legenda originale, zoom/pan, autore, licenza, data e provenienza. Si accettano CC BY/CC BY-SA nelle versioni supportate, CC0 e pubblico dominio; licenze assenti/incompatibili e identità ambigue producono il fallback.
- **Segnalazioni GBIF:** mappa mondiale navigabile con MapLibre e OSM, tasselli GBIF Maps v2 filtrati per taxon, problemi geospaziali esclusi e osservazioni umane/automatiche. È disponibile come alternativa o vista selezionabile insieme all’illustrazione. I colori indicano osservazioni storiche aggregate e risentono della raccolta dei dati: non sono un confine di areale, presenza attuale o probabilità d’incontro. Le aree vuote non dimostrano assenza.
- **Dati mancanti:** azione comunque disponibile, stato esplicito e retry. Un errore dell’immagine lascia consultabili metadati/fonti e ha un retry separato. Nessuna mappa viene ricavata dal rettangolo curatoriale o dalle geometrie dei record esterni.

**IUCN non è integrato.** Le mappe illustrate disponibili provengono da Commons; la mappa interattiva rappresenta segnalazioni GBIF. Un feed di geometrie istituzionali e le sue condizioni d’uso restano da concordare. Questi materiali sono di presentazione: non alimentano il motore F7 e non cambiano i livelli documentato/plausibile/insufficiente.

## Limiti, cache e dipendenze

Metadati normalizzati: massimo 256 voci in SharedPreferences, TTL 30 giorni, fonte/data/licenza/qualità/versione preservate nei fallback stale. Due richieste metadati simultanee al massimo; risposte limitate a 1 MiB e timeout espliciti. Cache delle immagini 32 MiB, risposta massima 4 MiB, dimensioni controllate e decodifica campionata. Cache delle tile e attribuzione OSM seguono il contratto MapLibre esistente. Non è un pacchetto cartografico regionale offline e non c’è prefetch.

La UI usa porte iniettate; endpoint e parsing stanno nell’adattatore puro. Le richieste di nomi/mappe non contengono il diario o il percorso personale. Room resta schema 7. Dichiarata direttamente `lifecycle-viewmodel-compose` 2.10.0, versione già presente nelle dipendenze runtime; nessun backend o account nuovo.

Il controllo live ha rilevato la migrazione delle miniature da `upload.wikimedia.org` a `thumb.wikimedia.org`: entrambi gli host HTTPS ufficiali sono accettati esattamente, con regression test contro host simili. Conferma primaria: [Wikimedia T439443](https://phabricator.wikimedia.org/T439443). URL delle immagini ottenuti da imageinfo, mai ricostruiti manualmente.

## Verifica

Gate finale `verifyAll --no-daemon` **verde sulla build consegnata**: **13 F0, 102 JVM, 94 Android**, zero failure/error/skipped/omissioni. Passati lint, formattazione, confini e tre firme visive preesistenti. Log `artifacts/f9-fixes-all.log`, XML Android `artifacts/f9-fixes-android-results.xml`, sintesi visiva `artifacts/f9-fixes-visual-summary.txt`. Indice completo nella build root esterna `root/reports/verification/index.html`. Il gate finale segue la prova lifecycle mirata verde e include la correzione dei domini delle miniature.

APK `artifacts/Faunavia-f9-mappe-debug.apk`: **0.9.2-f9**, versionCode **15**, package `it.faunavia.app`, minSdk 26, targetSdk 37, **56.153.409 byte**. SHA-256 **E8E1FD6B065DB884288314F80509A8C8373D72B8DCAFD4C873FF9530347CC255**. Firma verificata: certificato SHA-256 `96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24`, identico alle versioni F9 precedenti; installabile sopra l’app esistente senza disinstallarla. Room resta 7.

Nuova copertura: sei test JVM per identità esatta, lingua italiana, associazione della mappa, licenze/origini, cache/stale e filtri delle tile; tre test Android con **ricreazione reale dell’Activity** per risultato pronto, analisi in corso e risultati live del viaggio. La cache fake restituisce sempre miss, così il contatore distingue realmente un riavvio. La suite di presentazione aggiunge contrasto misurato sui pixel, nome fuori dal pilota senza selezione Room, errore/retry dell’immagine e renderer MapLibre reale. I casi precedenti verificano ora l’apertura interna senza browser e l’assenza di dati cartografici.

I problemi dei test sono stati risolti senza indebolire i gate: il titolo dei risultati restava fuori schermo dopo il ripristino della posizione della lista; il contenitore di prova impostava la composizione iniziale e quella ricreata da due punti diversi, alterando le chiavi dei campi saveable. Il test ora usa lo stesso metodo dell’Activity per entrambe, verifica anche le coordinate conservate e attende la rilettura Room prima di scorrere nei dettagli. I tre casi lifecycle sono passati nella prova mirata. Il test di cache controlla l’assenza della risposta grezza, senza vietare il legittimo URL di provenienza.

Controllo live limitato su Sciurus vulgaris e Alcedo atthis: identità accettate/esatte, un item Wikidata corrispondente per ciascuna specie, mappe PNG con pubblico dominio/CC BY-SA 3.0 e host ufficiali. Il primo taxon non aveva un nome italiano nel risultato GBIF: la mancanza resta dichiarata. Un tassello GBIF con i filtri dell’app ha restituito HTTP 200 e PNG. Sintesi `artifacts/f9-fixes-live.json` e `artifacts/f9-fixes-tile.json`; nessuna immagine remota scaricata per l’audit. Le prove UI usano fixture; lo smoke del renderer non garantisce disponibilità di tutte le tile. Nessun test su telefono fisico.

Checklist delle convenzioni:

1. **PASS** — Nessun endpoint provider nella UI; parsing e normalizzazione nel modulo puro, porte iniettate nell’app.
2. **PASS** — Fonte, data, licenza, attribuzione, qualità e versione mantenute anche nei metadati stale.
3. **PASS** — Nome scientifico immediato, mappa alternativa, stato mancante, retry metadati/immagine e cache dichiarata.
4. **PASS** — Evidenze esterne, plausibilità e ricordi personali distinti; mappe informative non promuovono F7.
5. **PASS** — Nessun nuovo asset esterno nell’APK; le immagini remote richiedono metadati e licenza compatibile e mantengono la legenda.
6. **PASS** — Geometria/ranking non alterati, regressioni deterministiche cumulative; identità, lingua e query delle tile verificate.

## Evoluzioni delle funzionalità F9

Curiosità restano **F13**, come sezione cliccabile con fonte per fatto, priorità a enti parco/Lipu e Wikimedia complementare verificato. Non è stato aggiunto un provider automatico di curiosità. Le schede complete F13 e la preparazione offline F15 potranno arricchire queste schede/mappe; la disponibilità di areali istituzionali universali e di nomi italiani per ogni taxon non è garantita o assegnata automaticamente a una fase.

Memoria MEX canonica aggiornata nel working tree: architettura, contratto dati, lifecycle Android, stack e pattern ricerca/mappe. `mex wiki validate`: 27 file, zero errori/avvisi; un primo problema di lettura dei placeholder OneDrive è stato risolto materializzando i file con una lettura, senza modificare i contenuti. Le modifiche richiedono commit/push per essere condivise; nessun commit o push eseguito. Obsidian conserva i riepiloghi precedenti e registra Wikidata/AndroidX Lifecycle ViewModel e il nuovo uso di Commons. Non è stato richiesto un Relay o un contributo Inbox.
