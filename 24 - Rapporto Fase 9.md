# Fase 9 — Suggerimenti personali e desideri

Implementazione del 2 ottobre 2026 secondo il perimetro F9 approvato in [[09 - Piano di sviluppo dettagliato]]. Guida d'uso: [[GUIDA-FASE-9]].

La successiva rifinitura concordata nello stesso giorno è documentata in [[25 - Animali tipici e schede compatte]]: selezione principale nella ricerca, dodici profili e schede essenziali con distribuzione cliccabile. Questo rapporto conserva il gate storico della prima F9.

## Comportamento

- **Viaggi → Suggerimenti e desideri** usa area e periodo del viaggio completo, della tappa o dell'uscita selezionata. Le evidenze live mantengono il loro livello; per consultazione offline si riusano soltanto i risultati salvati con la stessa chiave di analisi. Snapshot precedenti rimangono conservati e visibili nel viaggio.
- **Tipici del luogo** applica profili curati versionati, habitat, soglia editoriale 0,6 e controllo `urbanCommon`. Il merlo è escluso solo da questa vista; il diario continua ad accettarlo.
- **Più facili da osservare** mostra esplicitamente che mancano stime confrontabili e usa ordine alfabetico. Non usa quantità di record, habitat generico o peculiarità per simulare probabilità. Eventuali indicazioni F7 documentate restano visibili con la propria fonte.
- **Mai osservati da me** deriva dal diario identificato globale. Aggiornare o eliminare una osservazione aggiorna la vista alla riapertura; le bozze da identificare e i record esterni non contano come avvistamenti personali. L'identità accettata e il nome scientifico senza autore permettono il confronto fra identificativi provider diversi; questo non risolve sinonimi nuovi.
- **Vorrei vederlo** conserva desideri locali con identità Animalia accettata e data iniziale. Aggiunte ripetute sono idempotenti. Nel Catalogo si seleziona il taxon prima di aggiungerlo; dai suggerimenti si riusa o risolve la selezione tassonomica. Una risoluzione fallita mostra un errore e non inventa un taxon.
- La lista desideri mostra la pertinenza alle evidenze disponibili di area/periodo. Un risultato insufficiente resta insufficiente. Una voce senza evidenze correnti è **non valutabile**, non un'assenza. Eliminare il viaggio o modificare il diario non elimina il desiderio.
- **L'ho visto** riusa la conferma del diario: collegamento al viaggio/uscita, data e posizione effettive da confermare, nessun punto esterno copiato. Gli errori di selezione restano visibili. Pulsante Torna e back Android chiudono la vista.

## Curatela e fonti

Quattro profili originali `pilot-2026-10-02-v1`: martin pescatore, picchio nero, stambecco e merlo come controllo urbano. Ogni profilo include area curatoriale, habitat, motivazione, fonte, data di consultazione, attribuzione, qualità e limiti di riuso. I punteggi sono scelte editoriali; non descrivono rarità scientifica, frequenza o probabilità.

Le schede consultate supportano i fatti generali sugli habitat; la selezione e i punteggi sono una cura originale Faunavia. Fonti: [Lipu — Martin pescatore](https://www.lipu.it/uccelli/conoscerli-proteggerli/martin-pescatore), [Lipu — Picchio nero](https://www.lipu.it/uccelli/conoscerli-proteggerli/picchio-nero), [Lipu — Merlo](https://www.lipu.it/uccelli/conoscerli-proteggerli/merlo), [Aree Protette Alpi Cozie — Stambecco](https://www.parchialpicozie.it/it/parcopedia/stambecco/). Le fonti sono apribili dalla scheda; assenza di un browser mostra un errore conservando l'indirizzo.

Nessun testo integrale, fotografia o modello delle fonti è incluso. La provenienza registra CC BY-NC-ND 4.0 per le schede Lipu; per Alpi Cozie il riuso non è verificato, pertanto non si redistribuiscono i contenuti. I riassunti fattuali e le motivazioni sono originali.

La copertura curatoriale è il rettangolo 44,5–46,7° N, 8,3–11,5° E, utile al pilota Lombardia e dintorni. Non è un confine amministrativo né un areale; non verifica habitat presenti lungo il viaggio. Senza un candidato nelle evidenze esistenti un profilo non produce una specie, né promuove il livello a `plausible`.

## Persistenza e compatibilità

Room schema **7**, migrazione additiva **6→7**: tabella `wishlist` e `schemaVersion` dei profili. Conserva dati, timestamp, provenienza e collegamenti F8B. I profili precedenti diventano legacy versione 0 leggibile, anche senza habitat: non si inventano dati mancanti e non si usano come curatela F9. Nuovi profili versione 1 richiedono habitat e motivazione; le scritture legacy sono rifiutate.

Foreign key e trigger SQL difendono anche le scritture dirette: desideri soltanto per taxa Animalia accettati, taxon desiderato protetto da cancellazione e cambio a sinonimo/non Animalia. Rimuovere il desiderio non cancella taxon o diario. Nessuna nuova dipendenza, account o servizio.

## Verifiche

Il gate conclusivo `verifyAll --no-daemon` è **verde** sulla versione finale: **13 test F0, 93 JVM e 82 Android**, zero failure/error/skipped, nessuna omissione del runner. Build APK, lint app/storage, confini, formattazione e tre firme visive preesistenti sono passati. Log `artifacts/f9-all.log`, XML Android `artifacts/f9-android-results.xml`, sintesi visiva `artifacts/f9-visual-summary.txt`; indice completo sotto la build root esterna `root/reports/verification/index.html`.

Nuove regressioni: 9 test JVM sul motore/profili e 12 Android (4 persistenza/migrazione, 8 UI). Coprono soglia, profili invalidi/legacy, copertura/assenza, ordine stabile, fonti, invariabilità dell'evidenza, desideri idempotenti, vincoli SQL, riapertura offline, migrazione popolata, specie urbana registrabile, diario modificato/eliminato, bozze escluse, errori, restoration e snapshot validi/obsoleti. I test acquisiscono due immagini F9, ma queste non risultano conservate nella cartella degli output aggiuntivi; non si dichiara una nuova golden F9 o una revisione manuale di tali immagini. I tre golden preesistenti restano verificati.

Checklist convenzioni sul gate conclusivo:

1. **PASS** — Nessuna chiamata diretta ai provider nella UI: gate dei confini.
2. **PASS** — Fonte, timestamp, licenza e qualità conservati: mapper, profili e migrazione popolata.
3. **PASS** — Fallimento provider/storage con fallback o retry visibile: regressioni F8 e nuovo errore dei desideri.
4. **PASS** — Risultati documentati e plausibili distinti: livelli originali immutati, sentinel con candidato insufficiente.
5. **PASS** — Nuovi asset con licenza verificata: nessun asset multimediale esterno aggiunto; fonti/limiti dei profili espliciti.
6. **PASS** — Geometria/ranking deterministici e casi limite: regressioni cumulative della geometria e 9 test del motore F9.

APK finale: `artifacts/Faunavia-f9-debug.apk`, **0.9.0-f9**, versionCode **13**, **56.020.474 byte**, SHA-256 **199083EAA12D7CE4DA60778658DDADA872B632107B11ABDB19C90BD09D9B37A4**. Copia identica all'output della build; firma verificata e identica all'APK F8B tappe, quindi aggiornamento senza disinstallazione.

L'indice MEX non risolve i simboli Kotlin di questa fase: l'impatto è stato verificato sui contratti, riferimenti testuali e regressioni, senza considerare l'esito negativo del grafo come assenza di dipendenze.

Aggiornati router, contesti, runbook e nota di decisione MEX, roadmap, piano e guida. Sono artefatti nel working tree: richiedono commit/push per essere condivisi. Nel vault Obsidian sono stati aggiunti un aggiornamento F9 datato, un checkpoint al log e le voci mancanti dello stack; il contenuto precedente è conservato. Nessun commit o push eseguito.

## Evoluzioni previste delle funzionalità F9

| Fase | Come estende suggerimenti e desideri F9 |
|---|---|
| F12 | Rendere la lista desideri recuperabile mediante backup/import |
| F13 | Approfondire gli animali suggeriti con habitat, stagionalità, comportamento, dimensioni, dieta e fonti; conservare le schede viste offline |
| F15 | Preparare insieme risultati, schede e materiali del viaggio per consultarli offline, con copertura e data esplicite |
| F17 | Usare anche la lista desideri per proporre luoghi e sentieri: destinazione, date, raggio e interessi → uscite motivate dalle specie e dalle evidenze, confrontabili e salvabili nel viaggio |

F17 è l'evoluzione principale della F9: passa dalla selezione di animali alla proposta di **dove andare per cercare quelli desiderati**. Non promette incontri certi. Per la vista mai-osservati non è pianificato un ulteriore motore separato: continua a derivare dal diario aggiornato.

Ampliare il catalogo curato oltre il piccolo pilota e introdurre una vera graduatoria dei più facili da osservare non sono assegnati esplicitamente a una fase successiva. Sono possibili sviluppi sui dati, ma richiedono una nuova curatela e stime scientifiche confrontabili: non vanno presentati come già promessi. F13 arricchisce le informazioni, senza trasformare da sola l'habitat/stagionalità in facilità di incontro.

Il test su telefono fisico e una nuova prova live dei provider non sono stati eseguiti in F9. Mappe regionali, nuove analisi offline, feed istituzionali di habitat/areale e identificazione automatica restano nei limiti già documentati.
