# Video privati e taglio nell’app

Funzionalità introdotta nella 0.17.2-video (24), disponibile anche nella build corrente 0.17.3-no3d (25).

1. Salva l’avvistamento oppure il ricordo **Da identificare**.
2. Apri **Foto e video**, quindi **Aggiungi video**. Il selettore Android accetta un solo video.
3. In **Scegli lo spezzone**, sposta i due cursori per impostare inizio e fine. I tempi e la durata selezionata sono visibili; **Riproduci spezzone** prova il tratto scelto, **Pausa** lo ferma.
4. Premi **Salva spezzone**. Il video viene preparato sul dispositivo, mantenendo l’audio se presente e lasciando intatto l’originale. Tieni aperta l’app durante la preparazione.
5. Se la copia supera 100 MB, accorcia la selezione e riprova. L’errore conserva i cursori e l’eventuale video già salvato. **Interrompi preparazione** torna alla selezione; **Annulla** chiude senza sostituire l’allegato.
6. **Riproduci video** apre la copia privata. **Sostituisci video** sceglie un nuovo originale: il precedente resta collegato fino al salvataggio completo del nuovo spezzone. **Rimuovi video** richiede conferma e conserva testo e foto.

Ogni ricordo contiene al massimo un video, oltre alle foto. Identificare una bozza conserva il video; rimuovere il viaggio scollega il ricordo senza cancellarlo. La selezione e il lavoro in corso sopravvivono alla ricreazione dell’Activity. Dopo la terminazione del processo si rilegge soltanto la copia già salvata: un’importazione incompleta richiede una nuova selezione.

## Privacy e limiti

- Nessun upload, account o permesso generale sulla galleria. Un provider del selettore può richiedere rete per recuperare un originale remoto; Faunavia lavora poi sulla copia temporanea locale.
- Originali fino a 2 GiB, copiati temporaneamente nello spazio privato; durata leggibile fino a sette giorni e dimensioni fino a 8192 pixel per lato. Serve spazio per l’originale più lo spezzone e una riserva; il messaggio di spazio insufficiente conserva i dati esistenti.
- Copia finale MP4 con video H.264 e audio AAC, fino a 100 MiB (indicati come 100 MB nell’interfaccia). Il taglio ricodifica i fotogrammi, senza conservare parti precedenti nascoste tramite edit list. La qualità può differire dall’originale.
- Metadati originali, inclusa la posizione GPS, rimossi dal muxer. Nessun invio alle diagnostiche di piattaforma di Media3. Dimensioni, durata, SHA-256 e percorso privato sono salvati in Room.
- Preparazione annullabile, limite di cinque minuti per tentativo. Formati o codec non supportati dal dispositivo producono un errore recuperabile; nessuna promessa di supporto universale a HDR o formati proprietari.
- I file temporanei e le copie non collegate più vecchi di 24 ore possono essere rimossi al successivo accesso ai video. I video collegati sono esclusi dalla pulizia.

## Backup e compatibilità

Room 11 aggiunge `observation_videos` e `draft_videos` con una chiave primaria per ricordo e cancellazione a cascata; migrazione non distruttiva da Room 10. La conversione bozza → avvistamento trasferisce il video nella stessa transazione del ricordo e delle foto.

Il backup formato 3 include tutti i video collegati, con dimensione e hash. Il ripristino valida anche la leggibilità e i metadati del video, pubblica copie complete in directory nuove e sostituisce il database in una transazione. File mancanti o danneggiati impediscono un’esportazione incompleta. Restano leggibili formato 2/Room 10 e formato 1/Room 8–9; le prove dei reader precedenti usano fixture sintetiche, non trasferimenti reali da telefoni precedenti. Un backup con video richiede questa versione o una successiva compatibile. Il limite complessivo dell’archivio resta 2 GiB e il ZIP non è cifrato.

## Verifica

I test `MemoryVideoTest` verificano taglio all’interno di un GOP con fotogrammi sintetici colorati, audio, rimozione GPS, originale invariato, sostituzione, rollback, identificazione, riapertura, cancellazione, errori e backup. `MemoryVideoUiTest` esercita il selettore reale, i cursori, l’anteprima, la ricreazione e la rimozione. `VideoModelsTest` verifica intervalli, percorsi privati e dimensione massima.

Il gate completo è `verifyAll --no-daemon`, con esito e limiti registrati in [[35 - Video privati e taglio]]. Le prove Android usano un emulatore API 36; resta utile una prova sul telefono con i formati e le dimensioni dei propri video.

## Riferimenti tecnici

- [Media3 Transformer](https://developer.android.com/media/media3/transformer).
- [Taglio e trasformazioni](https://developer.android.com/media/media3/transformer/transformations).
- [API Media3 1.10.1](https://github.com/androidx/media/tree/1.10.1).
