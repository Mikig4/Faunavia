# Nome comune personale negli avvistamenti

## Uso previsto

Aprire **Diario → Modifica** sull'avvistamento e compilare **Nome comune personale (facoltativo)**, quindi salvare. Il campo è disponibile anche creando un nuovo avvistamento o identificando una bozza, dopo aver selezionato la specie.

Il nome è una nota strutturata del singolo avvistamento: non corregge il catalogo, non cambia il nome scientifico o il taxon accettato e non si applica automaticamente agli altri avvistamenti della stessa specie. Funziona anche quando il catalogo non fornisce un nome comune.

- Il Diario mostra il nome con l'indicazione **nome personale**; il riepilogo giornaliero lo distingue dal nome scientifico.
- Il filtro delle specie nel Diario cerca anche questo nome.
- Il campo è facoltativo, con massimo 160 caratteri e senza caratteri di controllo. Gli spazi esterni sono rimossi; svuotarlo ripristina il nome del catalogo, se presente.
- Cambiare la specie nell'editor cancella il nome personale ancora in modifica, evitando di trasferirlo a un altro animale.
- Non viene aggiunta alcuna chiamata di rete al salvataggio o alla modifica. Fonti, licenze e qualità dei dati del catalogo restano inalterate.

## Persistenza e compatibilità

`Observation` e `ObservationDraft` portano `personalCommonName`. Le transazioni di creazione, modifica e identificazione lo salvano senza sostituire la riga tassonomica, le foto, il video o la data di creazione.

Il codice porta Room a **schema 12** con migrazione additiva 11→12: una colonna nullable `observations.personalCommonName`, inizialmente NULL per tutti gli avvistamenti esistenti. Gli schemi precedenti non vengono modificati. Il 2026-11-10 `12.json` è stato verificato con il compilatore Room: l'identity hash coincide con quello generato in `FaunaviaDatabase_Impl`, e il test Android della migrazione popolata passa.

Il contenitore ZIP resta formato 3: il suo layout non cambia e il manifest dichiara il nuovo schema del database. Il writer salva schema 12; il reader conserva gli inventari esatti degli schemi 8–11, omette soltanto la nuova colonna nelle relative INSERT e lascia che SQLite assegni NULL. Un backup corrente senza la colonna, con colonne inattese o con un nome personale non valido viene rifiutato. Un'app precedente non è tenuta a leggere un backup schema 12.

## Regressioni aggiunte

- `LocalModelsTest`: normalizzazione, campo facoltativo, limite, controllo delle stringhe e identità distinta.
- `LocalPersistenceTest`: aggiunta, modifica, rimozione, riapertura, indipendenza fra avvistamenti e catalogo; identificazione di una bozza mantenendo la foto.
- `DiaryScreenUiTest`: inserimento successivo, visualizzazione, filtro, cancellazione, validazione, cambio specie e riepilogo giornaliero.
- `PersonalCommonNameMigrationTest`: migrazione popolata 11→12 con foto/video e successiva riapertura.
- `F12BackupTest`: round-trip del nuovo campo, lettura del precedente schema 11 e rifiuto del nome non valido senza alterare il diario di destinazione.
- Le fixture sintetiche schema 8 e 10 rimuovono la nuova colonna per rappresentare davvero i vecchi inventari.

## Verifica effettiva e limiti — 2026-11-10

- PASS: gate canonico `verifyAll --no-daemon --stacktrace`, senza filtri, in 17m37s: 13 test F0 con validazione degli schemi, 152 JVM e 174 Android; zero errori, fallimenti, test saltati o omessi; quattro firme visive, lint, formato e confini architetturali.
- PASS: migrazione Room popolata 11→12, conservazione di foto/video, riapertura, CRUD, UI del nome personale, riepilogo e backup correnti/precedenti.
- Risolti due ostacoli dell'ambiente: output Gradle non eliminabile (nuova cartella di build) e snapshot dell'emulatore con Launcher3 bloccato (backup dello snapshot e rigenerazione). I tre test di analisi sono passati senza modifiche dopo il recupero, poi è passato l'intero gate. Registro: `artifacts/pending-emulator-recovery.json`.

APK verificato: `artifacts/Faunavia-verificata-debug.apk`, stessa firma del precedente APK e versione dichiarata invariata 0.17.4-diary (26); contiene lo schema Room 12. Report: `artifacts/pending-verification.json` e `artifacts/pending-final-android-results.xml`. Nessun test su telefono fisico o runtime API 26 è stato eseguito. Codice, schema e documentazione restano nel working tree: richiedono commit/push per la condivisione.
