# Fase 11 — Riepilogo giornaliero locale

F11 aggiunge una notifica locale degli avvistamenti identificati, con attivazione e orario nelle Impostazioni. La build è **0.11.0-f11** (versionCode **18**), Room **9**; mantiene le funzionalità F10 e il refactoring precedente. Gate cumulativo finale **verde: 13 F0, 115 JVM, 123 Android**, zero errori, skipped o omissioni.

## Funzionalità aggiunte

- Riepilogo giornaliero disattivato di default, interruttore e orario indicativo `HH:mm` persistiti localmente; messaggi di salvataggio, stato e riprova.
- Richiesta esplicita di `POST_NOTIFICATIONS` su Android 13+, riconciliazione del permesso revocato e collegamento alle impostazioni notifiche Android. La negazione lascia il Diario utilizzabile.
- Una notifica soltanto se esistono avvistamenti identificati della data locale corrente. Conta i ricordi, non la quantità di animali; bozze e segnalazioni esterne non contano.
- Finestra dall’orario scelto fino a fine giornata, fuso corrente e gestione dei cambi d’orario/fuso/ora di sistema. Un controllo oltre mezzanotte valuta la nuova data.
- Registro persistente delle date già notificate, protezione da esecuzioni concorrenti e ripetute, riapertura dell’archivio e ritorno allo stesso giorno dopo un cambio di fuso.
- Tap sulla notifica: riepilogo della data/fuso originari con nomi locali, conteggi, ora, quantità, note e galleria privata. Ricreazione dell’Activity conserva la schermata. Consultazione di oggi anche dalle Impostazioni senza attivare notifiche o connessione.
- Disattivazione cancella il lavoro e le notifiche del riepilogo, conservando Diario e foto.

Uso e recupero: [[GUIDA-FASE-11]]. La notifica contiene un conteggio; note, coordinate e immagini non vengono copiate nel suo testo. La visibilità sulla schermata di blocco è privata.

## Pianificazione e persistenza

AndroidX WorkManager **2.12.0** è una nuova dipendenza, con test library della stessa versione. Il lavoro periodico unico ha intervallo di **15 minuti** e ritardo iniziale fino all’orario scelto, senza vincolo di rete. Il controllo corto mantiene la valutazione allineata al giorno locale anche durante DST, evitando che una periodicità di 24 ore faccia saltare la finestra dopo un cambio d’ora. Prima dell’orario il worker non interroga il Diario; dopo l’invio la lettura del registro evita di ricaricare i ricordi.

`KEEP` conserva il lavoro alla riapertura; `CANCEL_AND_REENQUEUE` riallinea le preferenze o un cambio di fuso/ora. WorkManager fornisce la persistenza e il ripristino al boot; il receiver dell’app reagisce a `TIME_SET` e `TIMEZONE_CHANGED`, mentre avvio/ripresa riconciliano impostazioni e permessi. Il worker usa comunque il fuso corrente a ogni esecuzione, non quello registrato sull’avvistamento.

Room **8→9** aggiunge solo `daily_summary_deliveries(date, zoneId, notifiedAt)`. La migrazione conserva tutti i dati F10; gli schemi JSON storici restano invariati. La chiave è la data locale, senza il fuso, per non ripetere un giorno già consegnato dopo spostamenti geografici. Il controllo conta soltanto `observations` tra l’inizio del giorno e l’inizio del successivo nel fuso corrente. `unidentified_drafts`, cache e `source_evidence` non sono coinvolti.

La transazione serializza lettura, invio locale e registrazione del successo. I post falliti non consumano la data; il worker ritenta errori temporanei. `NonCancellable` protegge il breve tratto invio→marker dalla cancellazione della coroutine. Le preferenze completano anche il passaggio persistenza→pianificazione sotto mutex quando la schermata viene chiusa. Un tag Android per data distingue i giorni; `onlyAlertOnce` limita l’alert quando viene sostituita la stessa notifica.

Room e NotificationManager non condividono una transazione atomica. Se il processo viene terminato fra invio e commit, un ritentativo può sostituire la stessa scheda. Il registro copre esecuzioni ordinarie, concorrenti e riaperture; non viene dichiarata una garanzia assoluta di esattamente un effetto anche in quella interruzione. Il riepilogo legge i ricordi correnti, non una copia congelata dell’archivio.

Fonti primarie verificate: [release WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [periodicità e ritardo iniziale](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [policy dei lavori unici](https://developer.android.com/reference/androidx/work/ExistingPeriodicWorkPolicy), [test CoroutineWorker](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl), [permesso notifiche](https://developer.android.com/develop/ui/compose/notifications/notification-permission). Context7 copre i principi ma non tutti i dettagli delle API: per questi sono state consultate le pagine Android ufficiali.

## Verifica

I test mirati Room/worker (**7**), scheduling/cancellazione (**2**) e UI (**4**) sono passati. Nuova copertura JVM: tre prove temporali e una di parsing orario. La suite Android aggiunge sette prove Room/worker, due di scheduling persistente/cancellazione e quattro UI; il gate cumulativo verifica anche omissioni e firme visive precedenti.

La copertura comprende zero/N, quantità e bozze escluse, intervallo locale e mezzanotte, DST primavera/autunno, cambio di fuso, sentinelle del Diario e dei metadati foto, worker con fake clock, concorrenti/doppie esecuzioni, riapertura file-backed, post fallito e retry, errore temporaneo, migrazione popolata, lavoro unico conservato/riallineato/cancellato. UI Automator nega il permesso dal dialogo Android reale e apre una notifica reale; la prova legge nome e foto e ricrea realmente l’Activity. La prova di attivazione concessa verifica anche la cancellazione del lavoro alla disattivazione.

L’Intent cambia dopo il tap: ActivityScenario filtra gli eventi per l’Intent launcher iniziale. Il test ripristina solo quella firma del test dopo avere consumato il deep link e invoca la ricreazione reale; il codice di navigazione dell’app resta quello effettivo. Su Windows il filtro mirato con più classi separate da virgole ha eseguito solo la prima classe: sono state eseguite separatamente le prove UI e il gate finale senza filtro copre tutte le classi.

**`verifyAll --no-daemon` PASS** sulla build finale: **13 F0, 115 JVM, 123 Android**, zero failure/error/skipped, nessuna omissione; build, lint, formattazione, confini e tre firme visive precedenti passati. Evidenze: `artifacts/f11-all.log`, `artifacts/f11-android-results.xml`, `artifacts/f11-visual-summary.txt`, `artifacts/f11-verification.json` e report completo archiviato `artifacts/f11-android-report/index.html`. Gli esiti dei tentativi intermedi sono diagnostici storici, non il gate finale. La prova mirata successiva alle verifiche serve a conservare le immagini delle nuove schermate, sullo stesso APK, mentre i risultati cumulativi restano archiviati.

Nuove schermate controllate visivamente: `artifacts/f11-settings.png` e `artifacts/f11-summary.png`, rigenerate da una prova UI mirata passata (`artifacts/f11-screens.log`). Testi, orario, stato, specie e accesso alle foto sono leggibili; le schermate scorrono sul dispositivo di prova. Le tre firme visive preesistenti restano il gate di regressione, non sono state sostituite da queste immagini.

APK `artifacts/Faunavia-f11-debug.apk`: **56.707.897 byte**, package `it.faunavia.app`, minSdk **26**, targetSdk **37**. SHA-256 **D6AE7478231D2EC5C3A3D187946BF027388D1DC289F161BBE9E77C9771BC4F86**. Firma v2 verificata; certificato SHA-256 **96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24**, uguale alle versioni F9/F10/refactoring: aggiornamento senza disinstallazione e migrazione additiva verificata.

Checklist delle convenzioni:

1. **PASS** — UI senza endpoint provider; worker, riepilogo e foto dipendono soltanto dall’archivio locale e dall’adattatore Android.
2. **PASS** — Provenienza dei dati importati e contenuti F10 conservati dalla migrazione additiva.
3. **PASS** — Stati vuoti/errori/riprova visibili; permesso negato non limita Diario e consultazione del riepilogo.
4. **PASS** — Avvistamenti personali identificati, bozze ed evidenze/plausibilità restano separati.
5. **PASS** — Piccola icona vettoriale originale del progetto, senza immagine esterna; origine/licenza registrate in `app/src/main/assets/asset-manifest.json`. Fixture fotografiche sintetiche nei soli test.
6. **PASS** — Logica temporale deterministica, regressioni cumulative di geometria/ranking e tutte le classi Android previste; controllo anti-omissione passato.

## Limiti e seguito

L’orario è indicativo: WorkManager, Doze e politiche di risparmio energetico possono ritardare o far perdere la finestra di quel giorno. Nessun allarme esatto, servizio remoto o FCM. Il riavvio è simulato attraverso riapertura/ripristino delle persistenze, non mediante reboot fisico. Non sono state eseguite prove su telefono fisico, Doze prolungato o nuove chiamate live ai provider. Il riepilogo consulta nomi del catalogo già salvati e foto F10, senza richiedere nuove schede online.

Backup, export/import e trasferimento tra telefoni restano F12. MEX registra stato F11, contratto, scheduling, migrazione e limite degli effetti fra Android e SQLite nel working tree; commit/push necessari per condividerlo. Nessun commit/push eseguito. WorkManager e API di notifiche sono registrati nello stack Obsidian, con milestone e riferimenti al rapporto canonico.
