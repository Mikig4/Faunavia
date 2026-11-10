# Video privati e taglio nel Diario

Aggiornamento del 2026-10-26, versione **0.17.2-video (24)**. Guida operativa: [[GUIDA-VIDEO]].

## Risultato

Ogni avvistamento o ricordo da identificare può contenere **un solo video, oltre alle foto**. **Foto e video → Aggiungi video** apre il selettore Android; prima di salvare si scelgono inizio/fine e si prova lo spezzone nell’app. Il risultato è una copia privata MP4 fino a 100 MiB, con audio se presente, senza metadati originali/GPS. L’originale resta intatto.

Sostituzione e rimozione sono esplicite; il video precedente resta collegato finché la nuova copia è completa e i metadati sono salvati. Errori e annullamento mantengono il ricordo. Identificare una bozza trasferisce anche il video in una transazione; cancellare la pianificazione conserva i ricordi. La selezione sopravvive alla rotazione; dopo una morte del processo restano soltanto i salvataggi completati.

## Implementazione

- Dominio: `MemoryVideo`, `VideoTrim`, limite dei byte e repository senza dipendenze Android.
- Room 11: `observation_videos` e `draft_videos`, chiave primaria per proprietario, foreign key a cascata, migrazione additiva 10→11. Conversione aggiornata senza perdere foto, creazione o identità.
- Android: `PrivateVideoStore` e `MemoryVideos` separano import temporaneo, esportazione, file completi, metadati e pulizia. Il commit condivide mutex/generation con foto e ripristino; l’export non blocca quel mutex durante l’encoding.
- Editor: `MemoryVideoSection` e ViewModel per sorgente/cursori/progresso/errori. VideoView/MediaPlayer offrono anteprima facoltativa, pausa alla fine del tratto e in background.
- Nuova dipendenza: AndroidX Media3 Transformer 1.10.1, versionata nel catalogo Gradle. Ricodifica H.264/AAC anziché approssimare al fotogramma chiave precedente o lasciare pre-roll nascosto. Muxer con metadati rimossi e diagnostiche di piattaforma disattivate.
- Backup 3: 22 tabelle, inventario completo dei video, controllo SHA-256/dimensioni/durata/leggibilità, pubblicazione di copie nuove prima della sostituzione atomica del database. Reader per formato 2/schema 10 e formato 1/schema 8–9 mantenuti.

## Verifiche

**Gate finale `verifyAll --no-daemon`: PASS in 14m49s.** 13 test F0, 150 JVM e 174 Android, zero failure/error/skipped/omissioni; lint, confini, formattazione e cinque firme visive verdi. Sono inclusi sette nuovi test Android di storage/taglio/backup, due test UI e due test di dominio.

Evidenze: `artifacts/video-verification.json`, `artifacts/video-final-verify-all.log`, `artifacts/video-android-results.xml` e `artifacts/video-editor.png`. Il digest SHA-256 dell’APK consegnato coincide con il file della build verificata.

La fixture è originale e generata sul dispositivo: sei secondi rosso/verde/blu più tono AAC. Il test taglia da 2.350 a 3.650 secondi, tra fotogrammi chiave: primi/ultimi frame devono essere verdi, la durata corretta, l’audio presente, il GPS assente e l’originale invariato. Altri scenari coprono un solo allegato, sostituzione, annullamento encoding, rollback, identificazione/riapertura, spazio insufficiente, file invalido/mancante, generazione precedente al ripristino, migrazione popolata e round-trip dei due proprietari. Il reader formato 2 è verificato con fixture sintetica, non con un archivio trasferito da un telefono precedente.

Il test UI seleziona un video reale dal Photo Picker, muove i due cursori tramite semantica, riproduce l’anteprima, ricrea l’Activity, salva lo spezzone e lo riapre senza originale. Verifica anche annullamento selezione e rimozione senza perdita del testo.

La verifica visiva richiede effettivi pixel verdi dello spezzone dopo la ricreazione, poi chiusura del player e recupero del focus. Il primo tentativo ha rivelato una cattura prematura nera e successivi errori di focus Espresso; quel run è stato interrotto, i controlli rafforzati e la regressione completa rieseguita con esito verde. Il contatore obsoleto del dispositivo gestito è stato recuperato soltanto dopo aver verificato l’assenza di emulatori/build attivi, con copia e recovery audit; il run finale ha restituito regolarmente `MDLockCount 0`.

Checklist delle convenzioni:

1. **PASS — Nessun provider chiamato direttamente dalla UI:** `staticAnalysis` verde; il video usa esclusivamente API locali.
2. **PASS — Provenienza dei dati importati conservata:** nessuna normalizzazione dei provider modificata; test esistenti verdi. La rimozione dei metadati dal video personale è esplicita e non altera le fonti faunistiche.
3. **PASS — Errore visibile e recuperabile:** errori di accesso/file/spazio/encoding e annullamento conservano il ricordo e l’allegato precedente; fallback/retry dei provider verificati dalla regressione.
4. **PASS — Osservato e plausibile distinti:** nessuna modifica alla classificazione; test F7/F9 e UI esistenti verdi.
5. **N/A — Nuovi asset con manifest/licenza:** nessun nuovo media esterno incluso nell’app. Fixture originali generate localmente e cattura sintetica; Media3 proviene dall’artefatto AndroidX ufficiale.
6. **PASS — Test deterministici geometria/ranking:** nessuna modifica alle rispettive logiche; le suite esistenti sono incluse nel gate verde.

## APK e limiti

`artifacts/Faunavia-video-debug.apk`: 90.064.608 byte, versione 0.17.2-video (24), certificato SHA-256 `96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24`, verificato uguale alla build F17 online.

Originali fino a 2 GiB; copia finale fino a 100 MiB; durata sorgente leggibile fino a sette giorni, lato fino a 8192 pixel; cinque minuti per tentativo di encoding. Richiesto spazio per staging e copia, con riserva. Codec/HDR non supportati producono errori recuperabili. Backup complessivo ancora limitato a 2 GiB, non cifrato.

Prove su emulatore API 36; nessun telefono fisico, sorgente da 2 GiB o matrice completa di codec/HDR verificati. F14 revisione umana/fisica, F15 e F16 restano aperte. Nessun backend, cloud editing, sincronizzazione, commit o push introdotto da questa iterazione.

Aggiornati guida, README, contesti e runbook MEX, più il recovery audit in `.mex/events/decisions.jsonl`. Questi artefatti canonici sono nel working tree e richiedono commit/push per essere condivisi. Lo stack e lo stato sintetico Obsidian sono aggiornati nel vault esistente, senza duplicare il dettaglio tecnico della repository.
