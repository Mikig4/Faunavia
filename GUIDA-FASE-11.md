# Fase 11 — Riepilogo giornaliero locale

## Attivazione

1. Apri **Impostazioni** e scegli l’**orario indicativo** nel formato `HH:mm`.
2. Premi **Salva orario**, poi attiva **Riepilogo giornaliero**.
3. Su Android 13 o successivi consenti le notifiche quando Android lo chiede. La richiesta compare soltanto dopo l’attivazione esplicita.
4. Registra almeno un avvistamento identificato nel Diario. Il riepilogo conta i ricordi, non la somma della quantità di animali: due ricordi con quantità 9 e 1 valgono due avvistamenti.

Le bozze da identificare e i record dei provider sono esclusi. La funzione è inizialmente disattivata. Il diario funziona anche con permesso negato.

## Quando arriva

WorkManager mantiene un unico controllo periodico locale di 15 minuti, con primo controllo dall’orario preferito. Prima dell’orario non legge gli avvistamenti. Android decide l’esecuzione effettiva: batteria, Doze e restrizioni del produttore possono ritardarla.

La finestra utile va dall’orario scelto alla fine della data locale corrente. Il worker ricalcola data e fuso del dispositivo a ogni esecuzione. Un’esecuzione oltre mezzanotte valuta la nuova giornata: non recupera il riepilogo di ieri. Scegliere un orario vicinissimo a mezzanotte lascia una finestra ridotta. Non è un allarme al minuto.

Una giornata vuota non produce una notifica. Se il primo avvistamento viene aggiunto dopo l’orario, un controllo successivo della stessa giornata può riepilogarlo. Dopo il primo invio non vengono emessi nuovi riepiloghi della stessa data, anche se si aggiungono avvistamenti o si cambia orario/fuso.

## Specie e foto

Il tap su **Gli avvistamenti di oggi** apre il riepilogo della data e del fuso salvati nella notifica. Mostra nomi locali, numero di avvistamenti e specie, orario, quantità, note e galleria privata dei ricordi. I dati vengono riletti: modifiche e cancellazioni del Diario si riflettono alla nuova apertura. Non viene congelata una copia del Diario nella notifica.

**Apri riepilogo di oggi** nelle Impostazioni consente la consultazione senza attivare notifiche o rete. Una data senza ricordi identificati mostra uno stato vuoto. **Vai al diario** torna agli avvistamenti. La galleria riusa le copie private F10; file mancanti o corrotti mantengono il fallback e il testo.

## Permessi, cambio d’orario e recupero

- **Permesso negato o notifiche bloccate:** nessun invio. Le Impostazioni spiegano lo stato e aprono le impostazioni Android. Dopo avere consentito le notifiche, riattiva il riepilogo. Alla riapertura una preferenza attiva con permesso revocato viene disattivata e il lavoro cancellato.
- **Disattivazione:** cancella il lavoro unico e le notifiche di riepilogo ancora presenti. Non modifica i ricordi o le foto.
- **Cambio orario/fuso/ora di sistema:** il lavoro viene riallineato; il worker usa comunque il fuso corrente. Riavvio e perdita del processo conservano pianificazione WorkManager e registro Room. Un force-stop Android impedisce l’esecuzione finché l’app non viene riaperta.
- **Errore di lettura/salvataggio/pianificazione:** compare un errore con riprova. Uscire dalla schermata durante il salvataggio non interrompe il breve passaggio preferenza→pianificazione. Il worker ritenta i fallimenti temporanei di archivio/invio.
- **Notifica già inviata:** il registro Room impedisce una seconda emissione della stessa data anche dopo riapertura. Un tag per data e `onlyAlertOnce` sostituiscono la stessa scheda in un eventuale ritentativo interrotto dal processo. Room e NotificationManager non condividono una transazione: una terminazione forzata fra invio e commit può richiedere la sostituzione della scheda; non viene promesso un effetto esattamente una volta in quel caso.

## Verifica ripetibile

```powershell
. ./scripts/android-env.ps1
./gradlew.bat verifyAll --no-daemon
```

Test F11: `DailyReminderPolicyTest`, `ReminderInputTest`, `F11ReminderTest`, `F11ScheduleTest`, `F11ReminderUiTest`. Comprendono clock controllato, zero/N, quantità e bozze, estremi del giorno, DST/fuso, doppie esecuzioni concorrenti, riapertura dell’archivio, migrazione popolata 8→9, permesso negato/concesso, lavoro unico e tap sulla notifica Android reale.

Il riavvio è simulato tramite riapertura e riconciliazione delle persistenze; non è una prova di reboot fisico o Doze prolungato. Dettagli, risultati e APK: [[29 - Rapporto Fase 11]]. Backup/export restano F12.

Documentazione primaria: [WorkManager e periodicità](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [permesso notifiche](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
