# F8B: tappe libere con giorni separati

Il 2 ottobre 2026 l'utente ha scelto l'inserimento libero delle località e dei giorni, con calcolo dei tratti fra tappe. La funzione estende la pianificazione F8B. F9 e F13 mantengono il perimetro concordato.

## Uso

1. Aprire un viaggio con partenza confermata e scegliere **Organizza tappe**.
2. Confermare la partenza e gli arrivi delle tappe, cercando le località o inserendo coordinate. La prima tappa parte dalla partenza del viaggio; le successive dall'arrivo precedente. L'ultimo arrivo diventa la destinazione del viaggio.
3. Assegnare a ogni tratto un giorno nelle date del viaggio, in ordine cronologico. Sono ammessi più tratti nello stesso giorno e giorni intermedi di sosta. Per ampliare l'intervallo usare **Modifica viaggio**.
4. Usare **Calcola percorso della tappa** e scegliere una delle alternative sulla mappa. Il calcolo resta in auto e usa OSRM su richiesta, con i due estremi del singolo tratto e i limiti/cache già presenti.
5. Aggiungere, riordinare con **Sposta su** o rimuovere tappe, quindi **Salva tappe**. Le modifiche restano nell'editor fino al salvataggio; **Annulla** conserva il viaggio precedente.
6. Nel viaggio scegliere **Intero viaggio** oppure una tappa. La mappa mostra la relativa geometria; la ricerca animali della tappa usa esclusivamente il suo tratto e il suo giorno. La ricerca sull'intero viaggio usa il corridoio complessivo e l'intervallo complessivo, senza attribuire automaticamente ogni risultato a una giornata.
7. Selezionare una tappa per aprire le sue indicazioni in Google Maps. Maps le ricalcola autonomamente; la traccia scelta rimane in Faunavia.

Le uscite facoltative restano utilizzabili per escursioni o luoghi aggiuntivi. Gli avvistamenti mantengono i collegamenti esistenti a viaggio/uscita; questa estensione non aggiunge un nuovo filtro del Diario per tappa e non imposta la data pianificata come data di un avvistamento reale.

## Coerenza e recupero

- Cambiare una località o l'ordine invalida solo le geometrie i cui estremi cambiano; una risposta tardiva non può ripristinare una scelta obsoleta. Le date si possono correggere senza ricalcolo stradale.
- Date fuori intervallo, ordine cronologico incoerente, località non confermate o percorsi mancanti impediscono il salvataggio, conservando l'editor e i dati precedenti.
- Una richiesta fallita conserva le altre scelte e consente di riprovare. I tratti già salvati si rileggono senza OSRM; nuove strade richiedono rete.
- Gli snapshot naturalistici conservano ID della tappa, giorno, area e provenienza. Dopo modifica o rimozione della tappa restano leggibili e risultano da ricalcolare; non si trasformano in osservazioni personali.
- Il Diario non viene cancellato quando si modifica la pianificazione.
- I campi piccoli dell'editor sono ripristinabili. Dopo ricreazione del processo, i percorsi nuovi non ancora salvati richiedono ricalcolo: le geometrie complete non vengono inserite nel bundle Android.

## Implementazione e stack

`TripStage` contiene identità, destinazione, giorno e `TripRoute` con geometria e provenienza. L'ordine è quello della lista nel viaggio. Il percorso completo conserva i segmenti di ogni tratto, senza inventare collegamenti rettilinei; distanze e tempi indicativi sono sommati. La provenienza specifica rimane su ciascuna tappa.

Il payload Trip JSON passa a v3, con lettura compatibile v1/v2; Room resta allo schema 6. La chiave delle analisi dei vecchi viaggi non cambia. Gli snapshot di tappe diverse hanno identità distinte anche per la stessa specie; una tappa rimossa non elimina lo snapshot originale.

Sono riutilizzati Kotlin/Compose, Room, MapLibre, OSRM e i servizi di ricerca esistenti. Nessuna nuova dipendenza, chiave o piattaforma esterna.

## Verifiche

Gate completo `verifyAll --no-daemon --console=plain` verde il 2 ottobre 2026: 13 test F0, 84 JVM e 70 Android, senza fallimenti, errori o test saltati. Passati build, formato, confini, lint, controllo delle omissioni e tre controlli visivi mediante firme colore (non confronto completo dei pixel).

I nuovi test deterministici coprono geometrie complete e date delle tappe, giorni uguali/di sosta, date invalide e identità duplicate, persistenza dopo riapertura, lettura del payload v2, conservazione di Diario e snapshot dopo rimozione, inserimento/riordino, retry, estremi Maps e analisi/salvataggio del giorno selezionato. Le prove Compose su API 36 usano provider e mappe di test; nessuna nuova prova live OSRM né prova sul telefono fisico in questa iterazione. Il precedente smoke live è documentato in [[22 - Tracciato viaggio e Catalogo]].

APK finale `artifacts/Faunavia-f8b-tappe-debug.apk`, versione `0.8.3-f8b` (12), 55.475.290 byte. SHA-256 `A02859447ED1B3DFA9F8BE9615DDA335E011FB99592D4CE9E087C40048D0D08D`, identico alla build verificata. Report in `$env:FAUNAVIA_BUILD_ROOT/root/reports/verification/index.html`. Requisiti, piano F8B e memoria MEX aggiornati nel working tree; la condivisione richiede commit/push.
