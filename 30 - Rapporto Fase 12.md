# Fase 12 — Backup locale, export e ripristino

F12 aggiunge **Backup e ripristino** nelle Impostazioni. Un archivio unico conserva diario, bozze, foto private, viaggi con tappe e tracciati, uscite, luoghi e risultati salvati, desideri, catalogo, provenienza e impostazioni. La build è **0.12.0-f12** (versionCode **19**); Room rimane **9**, senza migrazione o nuove librerie esterne.

## Uso e comportamento

**Esporta backup** apre il picker Android per creare un documento ZIP nella destinazione scelta. Il riepilogo mostra i conteggi senza registrare note, coordinate o immagini nei log. L’esportazione verifica le foto prima di scriverle: una copia mancante o corrotta produce un errore comprensibile, senza omettere silenziosamente un ricordo. Se annullata o fallita, prova a rimuovere il documento incompleto e segnala quando il provider non consente la pulizia.

**Importa backup** apre un documento esistente, controlla l’intero archivio in staging e mostra data, avvistamenti, bozze, viaggi, uscite e foto. L’annullamento conserva i dati correnti. **Sostituisci i dati** conferma una sostituzione completa: per conservare anche lo stato corrente occorre prima esportarlo separatamente. La transazione finale non è annullabile dall’interfaccia.

Operazione e anteprima sopravvivono alla ricreazione dell’Activity. Dopo il commit, un nuovo task dell’Activity elimina editor e analisi trattenuti sul vecchio archivio e mostra il rapporto di completamento. Il processo dell’app e i servizi Room restano aperti. Le preferenze di notifica sono ripianificate rispetto a permessi e fuso del dispositivo ricevente; un errore di scheduling viene mostrato come riprova dopo un ripristino riuscito.

Guida, limiti ed errori: [[GUIDA-FASE-12]]. Le foto sono le copie controllate F10, non gli originali ad alta risoluzione. L’archivio **non è cifrato**: contiene ricordi e posizioni personali. Faunavia non introduce upload o un backend; un provider di documenti cloud scelto dall’utente può richiedere rete per leggere o conservare il file.

## Formato e sostituzione

Il formato **1** usa `manifest.json` come prima voce, con identificativo dell’app, versione, data, schema del database, conteggi e inventario di percorso/dimensione/SHA-256. Seguono `database.json` e tutti i file immagine/miniatura referenziati. Il database è una fotografia transazionale delle **19 tabelle** e dei loro tipi/colonne; non viene copiato il file SQLite aperto, evitando uno snapshot incompleto rispetto al WAL.

La lettura tramite `ZipFile` richiede anche la directory centrale completa: la sola lettura sequenziale delle voci poteva accettare un archivio troncato dopo le voci locali. Vengono rifiutati versioni future, file extra/mancanti/duplicati, traversal, limiti superati, dimensioni/hash/conteggi discordanti e payload incompatibili. Un database Room separato con gli stessi vincoli, trigger e mapper verifica taxon, date, coordinate, quantità, collegamenti, chiavi esterne e integrità; le immagini devono corrispondere a hash, dimensioni e metadati registrati.

Le foto vengono copiate e sincronizzate in una directory privata nuova e univoca. Solo dopo copie complete, una transazione Room sostituisce tutte le righe, inserendo genitori prima dei figli e riscrivendo soltanto i percorsi foto. Un errore prima del commit esegue rollback e rimuove le nuove copie; i file precedenti non vengono sovrascritti. Database e file non condividono una transazione del filesystem: la pubblicazione append-only mantiene riferimenti vecchi o nuovi completi dopo una normale interruzione del processo. Non è una prova di resistenza a guasti fisici del supporto.

Le generazioni dei repository e delle operazioni foto rifiutano modifiche accodate prima del ripristino. Un mutex serializza ripristino e preferenze del riepilogo. Il registro delle notifiche consegnate è incluso; bozze ed evidenze restano distinte dagli avvistamenti identificati. File foto precedenti ormai orfani e staging abbandonati hanno recupero con grazia di **24 ore**; la pulizia incompleta resta visibile.

Compatibilità: schema **9** corrente; schema **8** accettato con registro notifiche assente e quindi vuoto. La prova precedente è una **fixture sintetica**: non esiste un archivio pubblicato prima di F12. Futuri GLB e pacchetti mappa richiedono un’estensione esplicita del contratto, preservando la lettura degli archivi validi rilasciati.

Limiti: **2 GiB** di dati, ZIP con margine di 16 MiB per il contenitore, **10.000 file immagine comprese miniature**, **64 MiB** di payload database, **100.000 righe**, **32 MiB** per immagine. Dopo la copia del ZIP, l’import richiede ulteriore spazio libero pari a tre volte i dati dichiarati più **64 MiB** di riserva. Dimensioni ridotte e errori di spazio sono testati; il massimo teorico non è una misura delle prestazioni su telefono. Cache mappe/immagini/geocoder, permessi Android, URI originali e analisi non completate sono esclusi.

Fonti primarie delle API: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [ZipFile](https://developer.android.com/reference/java/util/zip/ZipFile). Il resto del contratto deriva dal codice e dalle prove del progetto.

## Verifica

La nuova copertura contiene **11 test Android di archivio/persistenza** e **2 UI**. Include database vuoto e popolato su ricevente nuovo, tutte le tabelle/provenienze/relazioni, timestamp con precisione nanosecondi, tappe ordinate, foto identificate e delle bozze, riapertura file-backed, identificazione di bozze e cancellazione del planning dopo ripristino. Una prova nega realmente il permesso di scrittura nella directory di staging: il ripristino resta riuscito e l’avviso di pulizia viene conservato per la nuova Activity.

Le prove negative coprono corruzione, hash errato, file mancanti/inattesi/duplicati, ZIP troncato anche dopo voci complete, versione futura, traversal, conteggi/dimensioni incompatibili, payload con hash validi ma taxon/date/quantità/collegamenti errati, schema 8 sintetico, annullamento, spazio insufficiente, foto sorgente mancante/corrotta, modifiche vecchie accodate e rollback con errore iniettato immediatamente prima del commit.

La prova UI usa entrambi i picker SAF reali su API 36: salva il ZIP, lo riapre, ricrea l’Activity sull’anteprima, conferma, verifica foto e preferenze, nuova pianificazione WorkManager e rapporto nel nuovo task. La seconda prova verifica errore recuperabile e annullamento dell’anteprima senza perdita del diario.

Un tentativo UI intermedio ha usato un oggetto DocumentsUI diventato obsoleto durante la transizione; ora attende e recupera il nodo corrente. Un altro ha bloccato il dispatcher main simulato di Compose v2 mentre una modifica delle preferenze aspettava il mutex trattenuto da una coroutine UI: le mutazioni fixture concorrenti ora girano su IO mentre `waitUntil` fa avanzare la UI. Queste correzioni riguardano il test, senza aggirare il flusso produttivo. Su Windows il filtro di più classi separate da virgola esegueva soltanto la prima: UI mirata verificata separatamente e gate finale senza filtri.

**`verifyAll --no-daemon` PASS sulla build finale:** **13 F0, 115 JVM, 136 Android**, zero failure/error/skipped e nessuna omissione; build, lint, formattazione, confini e tre firme visive precedenti passati. Il gate è stato rieseguito dopo la correzione dell’avviso di pulizia, senza filtri. Evidenze: `artifacts/f12-all.log`, `artifacts/f12-android-results.xml`, `artifacts/f12-visual-summary.txt`, `artifacts/f12-verification.json` e rapporto completo `artifacts/f12-android-report/index.html`. Log mirati/intermedi sono diagnostici, non sostituiscono l’esito cumulativo finale.

Anteprima controllata visivamente in `artifacts/f12-backup-preview.png`, prodotta dalla prova SAF nella suite finale e conservata prima della pulizia dell’orchestratore: conteggi, avviso di sostituzione e pulsanti leggibili sull’emulatore. Le tre firme visive esistenti restano il gate di regressione.

APK `artifacts/Faunavia-f12-debug.apk`: **56.854.475 byte**, package `it.faunavia.app`, minSdk **26**, targetSdk **37**, versione **0.12.0-f12** (19). SHA-256 **3EB9FEA6567B391A48C8B79F1610FFFB26D13FB5F21838AC5B37F74B6E01A43B**. Firma v2 verificata; certificato SHA-256 **96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24**, uguale alle versioni precedenti. Metadati e firma in `artifacts/f12-apk-metadata.txt` e `artifacts/f12-signature.txt`.

Checklist delle convenzioni:

1. **PASS** — UI senza chiamate provider; backup e ripristino dipendono da archivio locale e API Android.
2. **PASS** — Provenienza, licenze, payload e timestamp sono conservati; hash/inventario e controlli di dominio proteggono l’import.
3. **PASS** — Errori, annullamento e riprova sono visibili; import fallito conserva lo stato precedente, scheduling fallito dopo commit richiede riprova esplicita.
4. **PASS** — Osservazioni personali, bozze, occorrenze e livelli di plausibilità conservano tabelle e contratti distinti.
5. **PASS** — Nessun nuovo asset esterno; immagini dei test sintetiche. Metadati/licenze degli asset salvati restano nei rispettivi payload.
6. **PASS** — Gate cumulativo, geometria/ranking deterministici, tre firme visive e controllo anti-omissione.

## Seguito e limiti

Nessun test su telefono fisico, guasto del supporto, archivio al volume massimo o nuove chiamate live ai provider. Il trasferimento è verificato con archivio su database ricevente nuovo e ripristino attraverso SAF nell’emulatore, non fra due telefoni fisici. La terminazione ordinaria durante staging lascia solo temporanei recuperabili; il tratto database/file usa la pubblicazione descritta sopra.

La fase successiva è **F13: scheda specie più ricca, fallback 2D e curiosità cliccabili con fonti**. F14–F17 mantengono numerazione e perimetro già approvati.

MEX registra confini, compatibilità e runbook nel working tree; commit/push necessari per condividerli. Nessun commit/push eseguito. Stato e tecnologie effettivamente usate sono aggiornati nelle note Obsidian con rimando a questo rapporto canonico.
