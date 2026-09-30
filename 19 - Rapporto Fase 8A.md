# Rapporto fase 8A — esplorazione senza viaggio salvato

La schermata **Risultati** ora accetta coordinate WGS84, GPX/GeoJSON, luoghi cercati per nome e percorsi locali già salvati. Un luogo trovato va confermato prima della query. Si sceglie un periodo di massimo un anno e un raggio tra 0,05 e 20 km; per paese o regione l'interfaccia avverte che si analizza un campione attorno al centro, non l'intero territorio. I nuovi input non sono salvati come viaggi.

`core:exploration` orchestra F5 → F6 → F7 e conserva la distinzione fra risultati vuoti, provider indisponibili, risposta parziale e cache stale. Un'osservazione datata e utilizzabile può essere `documented`; dati vecchi o imprecisi restano `insufficient` senza inventare un range o habitat. Record senza un nome binomiale utilizzabile vengono contati separatamente, non presentati come area vuota. Il periodo modifica il segnale stagionale, non promuove da solo il livello. La scheda essenziale mostra taxon, spiegazione, periodo, fonte e i dati locali di riconoscimento/habitat quando presenti; altrimenti mostra un fallback esplicito per dati e immagine mancanti.

MapLibre Native Android 13.6.1 (OpenGL) è isolato in un adapter. La mappa raster usa le tile standard OSM solo per la vista, con User-Agent identificabile, cache HTTP e attribuzione visibile. Mostra percorso, corridoio e campioni; le coordinate esatte delle osservazioni non sono disegnate. I livelli sono disponibili come riepilogo aggregato e nell'elenco filtrabile. Elenco e diario restano utilizzabili senza tile o rete. Non è prevista una mappa offline in 8A.

La ricerca luoghi usa Nominatim solo dopo il pulsante **Cerca luogo**: nessun autocomplete di rete, massimo una richiesta al secondo per processo, massimo cinque risultati e cache locale di 30 giorni. La scelta è deliberata perché la [policy pubblica Nominatim](https://operations.osmfoundation.org/policies/nominatim/) vieta l'autocomplete client-side e richiede limite, identificazione, cache e attribuzione. La [policy tile OSM](https://operations.osmfoundation.org/policies/tiles/) vieta il prefetch per creare regioni offline. Il gazetteer e le tile offline restano F15.

## Verifica

`verifyAll --no-daemon` completato il 30 settembre 2026: 13 test F0, 66 test JVM, 32 test strumentali su Pixel 2 API 36, lint, formattazione, confini architetturali e golden visivi. Test 8A nuovi: 11 JVM e 7 Android, inclusi query troppo lunghe, record classificabili solo al genere, persistenza della cache luogo e spiegazioni leggibili. Report cumulativo: directory di build esterna `root/reports/verification/index.html`; istruzioni in `GUIDA-FASE-8A.md`.

Smoke live separati, non deterministici: GBIF e NNB WFS HTTP 200 tramite `verifyOccurrenceSmoke`; una singola ricerca Nominatim identificata ha restituito HTTP 200. Il primo candidato per “Milano, Italia” era una località omonima, confermando che la scelta esplicita del luogo è necessaria e che lo smoke non valida la pertinenza geografica dei risultati.

## Limiti intenzionali

- I provider istituzionali di range/habitat non sono ancora cablati alla ricerca live. Senza entrambe quelle prove F7 non dichiara `plausible`; i risultati non vengono promossi artificialmente.
- Un paese o una regione non sono analizzati integralmente: il campione è dichiarato e limitato a 20 km.
- Il server pubblico Nominatim e le tile OSM non sono una garanzia di disponibilità o una soluzione offline. Se una URI GPX/GeoJSON non mantiene il permesso dopo la ricreazione del processo, l'app chiede di selezionare nuovamente il file.
- Il test di ripristino automatico copre la selezione per coordinate. La golden della mappa usa un adapter finto deterministico; un test aggiuntivo istanzia il MapLibre reale senza richiedere che le tile arrivino.
