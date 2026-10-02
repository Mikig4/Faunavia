# Foto private nel Diario

Salva un avvistamento o una bozza da identificare, poi apri **Foto (n)** nella sua riga e premi **Aggiungi foto**. Il selettore Android consente fino a cinque immagini per volta; puoi riaprirlo per aggiungerne altre. La chiusura senza selezione non cambia il ricordo. Sui dispositivi privi di Photo Picker AndroidX usa il selettore documenti, senza permessi generali sulla galleria.

La galleria mostra miniature, numero di foto e dimensioni della copia. **Apri foto** mostra la copia più grande; **Rimuovi foto** richiede conferma e conserva avvistamento, note e specie. Eliminare il ricordo elimina i suoi allegati privati. Eliminare un viaggio scollega i ricordi e conserva le foto.

Le bozze hanno la stessa galleria. Dopo la scelta di una specie accettata e **Conferma identificazione**, le foto appartengono all’avvistamento con lo stesso ID. La conversione è atomica: un errore conserva bozza e foto, senza duplicare il ricordo. Le bozze continuano a non contare come specie osservate.

## Copie e privacy

L’app importa solo le immagini scelte dalla persona. Gli originali rimangono invariati nel selettore; non sono caricati su servizi esterni. La copia nell’area privata `noBackupFilesDir/photos` è un JPEG con lato massimo 2048 pixel, orientamento applicato ai pixel, qualità 85 e miniatura fino a 320 pixel. I metadati originali, inclusi GPS, data/camera ed EXIF, non vengono ricopiati. Sono registrati percorso relativo privato, SHA-256 della copia, byte, tipo, larghezza/altezza, orientamento normalizzato e percorso della miniatura. Nessun URI originale resta in Room.

Un’importazione accetta file fino a 32 MiB e immagini fino a 100 milioni di pixel, con decodifica campionata e una riserva di spazio di 48 MiB. I file originali temporanei restano privati e sono rimossi al termine; residui di un arresto improvviso vengono rimossi alla successiva apertura di una galleria dopo 24 ore, soltanto se nessun riferimento Room li usa. Copie valide e file recenti non sono cancellati dalla recovery. Nessuna richiesta ai provider naturalistici o permesso fotocamera/galleria è introdotto dal flusso foto.

## Errori e recupero

- **Accesso scaduto/revocato:** seleziona di nuovo la foto. Le copie già importate non dipendono dalla disponibilità dell’originale.
- **Immagine troppo grande, non supportata o corrotta:** scegli una versione più piccola o un’altra immagine. Il ricordo testuale è già salvato.
- **Spazio insufficiente:** libera spazio e riprova. Un fallimento prima del collegamento Room rimuove i nuovi file parziali; non modifica il ricordo.
- **Copia mancante o hash diverso:** la galleria mostra un messaggio esplicito. Puoi rimuovere il riferimento o aggiungere di nuovo l’immagine. Una miniatura mancante usa la copia principale valida.
- **Errore di cancellazione dei file:** il riferimento viene rimosso prima dei file; un messaggio segnala la pulizia incompleta. La recovery successiva ritenta i residui vecchi e non collegati.

La ricreazione dell’Activity conserva la galleria aperta e il lavoro foto in corso tramite ViewModel. Dopo una nuova apertura del processo, metadati e copie completate si rileggono dal database e dall’area privata. Un’importazione interrotta dalla terminazione del processo va ripetuta; il ricordo e le foto già collegate restano salvati. F10 non conserva gli originali ad alta risoluzione, non acquisisce direttamente dalla fotocamera e non introduce export/import o backup: recupero su altro dispositivo in F12. La disinstallazione rimuove l’archivio privato dell’app.

## Verifica ripetibile

```powershell
. scripts/android-env.ps1
.\gradlew.bat verifyAll --no-daemon
```

La suite F10 include metadati e path nel dominio, migrazione Room 7→8, persistenza/riapertura, hash, EXIF e otto orientamenti, immagini grandi, URI revocati, copie corrotte/mancanti, spazio e rollback, conversione/eliminazione di bozze, recovery e galleria Compose. Le prove UI usano foto sintetiche e il Photo Picker reale via UI Automator sul dispositivo gestito API 36; il rapporto finale registra gli esiti effettivi. Per gli errori della toolchain seguire `GUIDA-GRADLE-LOOPBACK.md`. Il gate non sostituisce una prova su telefono fisico.
