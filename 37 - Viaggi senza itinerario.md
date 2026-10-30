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

## Verifica effettiva

- PASS: `scripts/quality/check-format.ps1`, eseguito con Windows PowerShell.
- PASS: `scripts/quality/check-boundaries.ps1`, inclusa la scansione dei segreti.
- PASS: `git diff --check`.
- PASS: `node --test f0/tests/*.test.mjs`, 13 test, nessun errore o test saltato. Questa esecuzione diretta non comprende la validazione JSON Schema del gate F0 completo.
- BLOCCATO: `npm --prefix f0 test`, perché `pwsh` non è installato.
- BLOCCATO: `. scripts/android-env.ps1; .\gradlew.bat verifyAll --no-daemon --stacktrace`, perché manca il JDK nella toolchain configurata. Anche l'SDK Android indicato da `local.properties` non è presente. I nuovi test Kotlin/Android non sono stati eseguiti.

Non è stato prodotto un nuovo APK. Gli esiti verdi e gli APK delle fasi precedenti non certificano questa modifica. Prima di distribuirla, ripristinare PowerShell 7 e la toolchain tramite `scripts/bootstrap-android.ps1`, quindi eseguire il gate canonico `verifyAll`.
