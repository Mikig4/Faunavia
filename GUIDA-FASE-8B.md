# Verifica e recupero della fase 8B

Questa nota permette di verificare viaggi, uscite, diario collegato e bozze senza cancellare dati per risolvere un errore. Il gate completo deve essere verde, non basta compilare l'APK.

## Ripetere tutti i controlli

Dalla radice del progetto, in PowerShell:

```powershell
. .\scripts\android-env.ps1
& "$env:LOCALAPPDATA/Faunavia/toolchains/gradle/gradle-9.6.0/bin/gradle.bat" verifyAll --no-daemon --stacktrace
```

I risultati sono sotto `$env:FAUNAVIA_BUILD_ROOT`, fuori da OneDrive. Consultare `root/reports/verification/index.html`; per Android leggere `app/outputs/androidTest-results/managedDevice/debug/pixel2Api36/TEST-pixel2Api36.xml`, per lint `app/reports/lint-results-debug.html`. Il riferimento F8B è 13 test F0, 71 JVM e 54 Android; i conteggi potranno crescere con le fasi successive.

La successiva rifinitura Viaggi/Maps/catalogo nel Diario è documentata in [[21 - Rifinitura Viaggi e Diario]], con gate verde aggiornato a 13 F0, 73 JVM e 60 Android e APK dedicato. L'app si apre su Viaggi; esplorazione e importazione si raggiungono dai suoi strumenti facoltativi.

Il requisito precisato di partenza e tracciato completo e la correzione dei nomi comuni sono verificati in [[22 - Tracciato viaggio e Catalogo]]: gate della versione precedente verde a 13 F0, 82 JVM e 66 Android, tre controlli visivi, nessun test fallito o saltato. Il relativo APK storico è `artifacts/Faunavia-f8b-viaggi-debug.apk`, versione `0.8.2-f8b` (11). La mappa interna conserva la traccia scelta in auto; Google Maps ricalcola autonomamente le proprie indicazioni.

## Individuare il problema

Versione corrente con tappe: `artifacts/Faunavia-f8b-tappe-debug.apk`, `0.8.3-f8b` (12), gate verde a 13 F0, 84 JVM e 70 Android. Aprire il viaggio → **Organizza tappe** → inserire località/giorni → calcolare e scegliere ogni tratto → **Salva tappe**. Per cambiare l'intervallo generale usare **Modifica viaggio**; le date devono includere tutte le tappe. Se cambia un estremo o l'ordine, ricalcolare i tratti invalidati. Selezionare una tappa prima di aprire Maps o cercare gli animali per il suo giorno. Dettagli e limiti in [[23 - Tappe e giorni del viaggio]].

1. **Gradle non parte:** seguire [[GUIDA-GRADLE-LOOPBACK]]. Un accesso negato a un JAR della toolchain nel sandbox non è un errore dell'app; usare la stessa toolchain con i permessi appropriati, senza disattivare i gate.
2. **Lint:** una funzione `@Composable` che restituisce `Unit` deve avere nome PascalCase, come `TestDiary`. Per inizializzare le date con minSdk 26 usare `instant.atZone(zone).toLocalDate()`: `LocalDate.ofInstant` richiede API 34 o desugaring. Correggere il codice, non aggiungere una suppression per nascondere il problema.
3. **Test UI non trova un controllo:** chiudere la tastiera e scorrere il contenitore lazy con `performScrollToNode`, anche dopo l'arrivo di un risultato asincrono o l'apertura della conferma di eliminazione: la presenza in semantics non garantisce visibilità. Per i filtri dentro una `LazyRow`, portare prima la riga nel viewport verticale, poi scorrere orizzontalmente la riga: il contenitore esterno non può cercare un elemento non ancora composto. Dopo un ripristino attendere dati/editor, non soltanto un contatore del provider. I form esistenti non devono inizializzarsi come nuovi prima del caricamento Room.
4. **Migrazione o collegamenti falliscono:** controllare schema 6, `MIGRATION_5_6` e i trigger in `faunavia-database.kt`. Non ricostruire `observations`, disabilitare i vincoli, usare migrazione distruttiva o reinstallare cancellando dati.
5. **Conversione bozza fallisce:** `UnidentifiedRepository.convert` rimuove la bozza e inserisce l'avvistamento nella stessa transazione. Un taxon invalido o un collegamento errato devono ripristinare la bozza; correggere l'input e riprovare. Non fare due scritture indipendenti né generare un nuovo ID.
6. **Risultati non aggiornati:** gli snapshot mantengono area e periodo originali. Dopo una modifica usare “Cerca animali per queste date”; un provider indisponibile non deve eliminare i risultati salvati né copiarne le coordinate nel diario.
7. **Catalogo indisponibile nel Diario:** scegliere una specie già salvata oppure usare “Riprova ricerca”. Se fallisce la selezione locale, selezionare di nuovo la specie; i campi restano compilati e non viene creato un avvistamento incompleto. Una specie già confermata si salva senza rete.
8. **Maps non si apre:** i dati del viaggio restano disponibili. Riprovare dopo aver reso disponibile un gestore di collegamenti web/Maps; non cancellare il viaggio. I nuovi viaggi aprono indicazioni con i due estremi, le uscite/viaggi legacy un punto. Maps ricalcola autonomamente: il tracciato salvato in Faunavia resta distinto.
9. **Calcolo percorso non disponibile:** controllare la rete e riprovare da “Calcola percorso”. La traccia già salvata resta locale; una nuova traccia non viene inventata. Un cambio di estremo richiede nuova scelta. Il demo OSRM non garantisce disponibilità/traffico attuale e il prototipo calcola percorsi in auto.
10. **Ricerca “merlo”:** nell'APK aggiornato il primo risultato atteso è “Turdus merula / Merlo”, ID `gbif:2490719`. Gli omonimi in altre lingue possono comparire dopo. Il caricamento deve terminare in risultati o errore con “Riprova”; le scelte locali restano utilizzabili. Verifiche aggiornate in [[22 - Tracciato viaggio e Catalogo]].

Dopo la riparazione rilanciare `verifyAll`: `verifyDevice` intercetta test omessi e `verifyVisual` richiede anche `tripsAndDraftScreensMatchVersionedSignature`. Non aggiornare la baseline solo per rendere verde un errore.
