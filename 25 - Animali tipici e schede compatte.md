# Animali tipici e schede compatte — rifinitura F9

Questo rapporto descrive la consegna 0.9.1-f9. Il successivo aggiornamento [[26 - Nomi, rotazione e mappe interne]] corregge il contrasto dei titoli e la rotazione e sostituisce l’apertura esterna con mappe nell’app disponibili anche fuori dal pilota.

Richiesta approvata il 2 ottobre 2026: aprire la ricerca sui soli animali caratteristici, con le evidenze complete come vista secondaria; mostrare nella scheda iniziale nomi, habitat cliccabile verso distribuzione e stagionalità. Curiosità cliccabili nella successiva F13.

## Risultato

- La ricerca libera e quella di viaggio/tappa/uscita partono su **Animali tipici**. La selezione usa le geometrie analizzate e profili curati, non la quantità di avvistamenti. **Tutte le specie documentate** mantiene accessibili tutti i risultati, inclusi quelli con dati insufficienti chiaramente spiegati; non significa che tutti abbiano livello DOCUMENTED.
- Dodici profili `pilot-2026-10-02-v2`: martin pescatore, picchio nero, stambecco, merlo (controllo urbano escluso), airone cenerino, garzetta, svasso maggiore, upupa, aquila reale, falco pellegrino, camoscio e marmotta alpina. È una selezione regionale non esaustiva, non un elenco mondiale o una nuova base di presenza.
- Identiche evidenze, provenienza e classificazioni prima/dopo il filtro. Nessuna specie viene creata dai profili. Vuoto significa nessuna corrispondenza disponibile con la curatela/filtri, non assenza di animali. I risultati già scelti personalmente restano consultabili offline.
- Schede condivise compatte: nome comune disponibile e scientifico, habitat generale e stagionalità. Rimossi i messaggi fotografici e il riconoscimento dalla vista iniziale. **Evidenze e fonti** rende consultabili le informazioni precedenti e le fonti curatoriale/geografica. Azioni diario/desideri e salvataggio restano separate.
- Habitat cliccabile apre **nel browser** una pagina di mappa Wikimedia Commons verificata, con autore, licenza, legenda e data. Dodici mappe generalizzate, nessuna mappa costruita dal rettangolo curatoriale o dai punti esterni. Non è una mappa interna interattiva e non funziona senza rete; le informazioni testuali restano offline. Dati mancanti e browser indisponibile hanno fallback visibili.
- Nomi italiani curati sono etichette informative, non identità tassonomiche accettate. Aggiungere desideri/osservazioni continua a risolvere o usare un taxon selezionato. Nessuna migrazione o dipendenza nuova; Room resta schema 7.

## Curiosità e fonti

Le curiosità non avevano una fase o un provider dedicato, ma gli approfondimenti di comportamento/dieta erano già previsti in F13. La richiesta ora è esplicita in requisiti, roadmap e piano: **Curiosità sarà una sezione cliccabile/espandibile F13**, con fonte per fatto, data e stato mancante.

Non era fissata una fonte unica. La curatela fattuale privilegia schede ufficiali di enti parco e Lipu; Wikipedia/Wikidata sono supporto complementare da verificare e attribuire sul singolo fatto. GBIF/NNB restano fonti di occorrenze, non un motore di curiosità. Un'importazione automatica di testo richiederà una scelta del provider e licenze compatibili; non è stata introdotta adesso. Dettagli e collegamenti in [[03 - Dati e fonti]].

Gli otto nuovi profili sono brevi formulazioni originali da schede consultate Lipu (airone cenerino, garzetta, svasso maggiore, upupa, aquila reale, falco pellegrino) e Parcopedia Alpi Cozie (camoscio, marmotta). Link/provenienza sono nel catalogo versionato. Nessun testo integrale o fotografia redistribuito. Metadati originali delle mappe in `artifacts/f9-refinement-map-sources.json`; Commons è utilizzato per collegamenti, non per importare areali nel motore F7.

## Verifica e consegna

Il gate finale `verifyAll --no-daemon` è **verde** sulla versione da consegnare: **13 F0, 96 JVM, 87 Android**, zero failure/error/skipped e zero omissioni. Lint, formattazione, confini e tre firme visive preesistenti sono passati; nessuna nuova golden è stata dichiarata. Log `artifacts/f9-refinement-all.log`, XML `artifacts/f9-refinement-android-results.xml`, sintesi visiva `artifacts/f9-refinement-visual-summary.txt`. Indice completo nella build root esterna `root/reports/verification/index.html`.

Nuovi test: 3 JVM (evidenze identiche dopo selezione, regioni/assenza, copertura di tutti i collegamenti/nome comune/provenienza) e 5 Android (default e vista completa con restoration, vuoto urbano, Intent della mappa con errore/retry, specie sconosciuta, filtro del viaggio senza perdita di snapshot). Le regressioni precedenti aprono esplicitamente la vista completa per le specie urbane e il dettaglio per fonti/livelli. Le verifiche mirate F9 sono passate, seguite dal gate cumulativo completo. Nessuna nuova prova su telefono fisico o query live GBIF/NNB; le fonti naturalistiche e i metadati Commons sono stati verificati online.

Checklist convenzioni:

1. **PASS** — UI senza endpoint provider o chiamate dirette: gate dei confini.
2. **PASS** — Fonte, data, licenza, qualità e versioni preservate; selezione mantiene gli oggetti originali e audit delle mappe verificato.
3. **PASS** — Fallback espliciti per curatela/dati/mappe mancanti e browser indisponibile, con retry; regressioni cumulative provider/storage.
4. **PASS** — Livelli documentato/plausibile/insufficiente invariati, consultabili nel dettaglio; un profilo non crea evidenze.
5. **PASS** — Nessun asset esterno copiato; dodici mappe collegate con autore/licenza/data verificati, contenuto generale distinto dal viaggio.
6. **PASS** — Ranking e copertura deterministici, geometria cumulativa verificata; nessun conteggio usato come probabilità o peculiarità.

APK: `artifacts/Faunavia-f9-tipici-debug.apk`, **0.9.1-f9**, versionCode **14**, **56.069.719 byte**, SHA-256 **BE60097880224BBC426E63871476C05C00C6971E1BF54797224338A2E302BC00**. Package `it.faunavia.app`, minSdk 26, targetSdk 37. Firma verificata e identica alla F9 precedente: aggiornabile senza disinstallare. Room resta 7.

Il grafo MEX non risolve i file Kotlin interessati: impatto verificato direttamente sui riferimenti, sulle schede condivise e sui test cumulativi. Requisiti e note canoniche sono modifiche nel working tree, da commit/push per condividerle; nessun commit o push eseguito.

Le note MEX canoniche sono state validate: 27 file, zero errori e avvisi. La rigenerazione dell'indice wiki derivato non è stata completata: primo tentativo con database occupato, secondo interrotto durante la pubblicazione. I documenti restano conservati e validi; l'indice è ricostruibile e questo esito non riguarda il gate dell'app o l'APK.
