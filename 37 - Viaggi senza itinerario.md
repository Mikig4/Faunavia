# Viaggi senza itinerario obbligatorio

## Comportamento

Per creare o modificare un viaggio bastano una **destinazione confermata** e **date valide**. Partenza e percorso sono facoltativi; il nome continua a derivare dalla destinazione se non viene personalizzato.

- Senza tracciato, la ricerca degli animali usa il campione geografico attorno alla destinazione e il raggio scelto, non un corridoio stradale inventato.
- Un errore di calcolo del percorso non impedisce di salvare il viaggio.
- Le alternative calcolate ma non scelte non vengono salvate automaticamente.
- Si può aggiungere un percorso in seguito con **Modifica viaggio → Calcola percorso → Scegli questo percorso → Salva viaggio**.
- Cambiare un estremo elimina la traccia ormai non pertinente, ma consente di salvare senza ricalcolarla.
- Le tappe datate esplicite conservano i propri vincoli di geometria, ordine e intervallo temporale. La modifica non cambia schemi Room, migrazioni o formato dei backup.
- Nessuna chiamata OSRM viene aggiunta al salvataggio: il calcolo resta un'azione esplicita.

## Implementazione e regressioni

`app/src/main/kotlin/it/faunavia/app/TripScreen.kt` rimuove il requisito di partenza/percorso dal salvataggio e rende esplicita la loro facoltatività nei testi. Il modello `Trip` supportava già entrambi i campi nullabili e il flusso di analisi disponeva già del caso puntuale.

`TripScreenUiTest.kt` aggiunge due scenari: creazione, riapertura del database e modifica senza partenza/percorso, con rifiuto delle date invertite e analisi esplicita; salvataggio dopo errore di routing, alternative non selezionate e aggiunta successiva di una traccia. Il test del cambio di estremo ora richiede di poter salvare senza geometria obsoleta. `TripModelsTest.kt` aggiunge una regressione per il viaggio senza itinerario, con e senza partenza.

## Verifica effettiva — 2026-11-10

- PASS: `verifyAll --no-daemon --stacktrace` senza filtri, in 17m37s; formato, confini architetturali, scansione dei segreti, lint e build.
- PASS: 13 test F0 con validazione degli schemi, 152 JVM e 174 Android, zero fallimenti/errori/test saltati o omessi e quattro firme visive.
- PASS: tutti i 21 test `TripScreenUiTest`, inclusi viaggio senza partenza/percorso, errore di routing, alternative non selezionate, aggiunta successiva della traccia e rimozione della geometria obsoleta.

APK corrente: `artifacts/Faunavia-verificata-debug.apk`, stessa firma del precedente APK; include anche il nome comune personale. Report: `artifacts/pending-verification.json`. Il gate usa una nuova cartella di build e uno snapshot dell'emulatore rigenerato dopo un blocco del Launcher3; il recupero è documentato in `artifacts/pending-emulator-recovery.json`. Nessuna prova su telefono fisico. Le modifiche sono nel working tree e richiedono commit/push per essere condivise.
