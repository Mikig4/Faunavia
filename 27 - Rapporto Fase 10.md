# Fase 10 — Foto locali e privacy

F10 aggiunge gallerie fotografiche private agli avvistamenti del Diario e alle bozze da identificare, anche nel Diario aperto da un viaggio. Il gate cumulativo finale è verde. APK `artifacts/Faunavia-f10-debug.apk`, **0.10.0-f10**, versionCode **16**, Room **8**. Le funzionalità F9 precedenti restano disponibili.

## Funzionalità aggiunte

- Photo Picker Android, fino a cinque immagini per selezione, selettore documenti di fallback e nessun nuovo permesso di accesso generale alla galleria/fotocamera.
- Galleria con contatore, miniature, apertura della copia più grande, rimozione confermata di una singola immagine e cancellazione degli allegati quando viene eliminato il ricordo.
- Foto anche nelle bozze da identificare: modifica e identificazione conservano i file; la conversione trasferisce i metadati nella stessa transazione mantenendo ID e creazione originali. Un errore ripristina bozza e relazioni.
- Copie private e offline con JPEG fino a 2048 pixel, miniatura fino a 320 pixel, otto orientamenti EXIF applicati ai pixel e metadati originali GPS/data/fotocamera non copiati. Originali invariati.
- SHA-256, byte, MIME, dimensioni, orientamento normalizzato e percorsi privati registrati in Room. Il riferimento all’URI originale non è necessario per rileggere la copia. File mancanti/corrotti hanno un fallback visibile; il testo del ricordo resta disponibile.
- Gestione degli errori di accesso, dimensione, decoding, spazio e scrittura, pulizia dei file parziali, recupero dei residui vecchi non collegati e avviso persistente quando la cancellazione fisica non riesce. Eliminare un viaggio conserva le foto dei ricordi scollegati.

Uso e recupero: [[GUIDA-FASE-10]]. Le foto si allegano a un ricordo già salvato: un’importazione fallita non può compromettere specie, data e note.

## Confini e persistenza

`:core:domain` mantiene Kotlin puro e aggiunge solo metadati fotografici e porte per le foto delle bozze. `:core:local` aggiunge le dimensioni/orientamento/miniatura ai metadati esistenti e `draft_photos` con foreign key; la migrazione 7→8 conserva gli ID e i riferimenti precedenti con dimensioni legacy sconosciute, senza inventarle.

Android `PrivatePhotoStore` e `MemoryPhotos` possiedono IO e file in `noBackupFilesDir/photos`. L’import legge al massimo 32 MiB, controlla fino a 100 MP e una riserva di 48 MiB, decodifica in modo campionato e scrive una coppia completa prima dei metadati. Un errore Room pulisce i file nuovi. La cancellazione dei metadati precede quella dei file, e segnala una pulizia incompleta; il ricaricamento del Diario conserva l’avviso. Recovery sotto mutex: soltanto file non referenziati più vecchi di 24 ore, mantenendo copie referenziate e recenti.

`PhotoGalleryModel` conserva i lavori durante la ricreazione dell’Activity e l’ID della galleria è saveable. Dopo una nuova apertura del processo i file completati si rileggono da Room e dall’area privata. Un import interrotto dalla terminazione del processo deve essere ripetuto. Le prove verificano ricreazione reale dell’Activity e riapertura file-backed senza originale; non dichiarano una prova reale di force-stop durante l’importazione.

AndroidX ExifInterface **1.4.2** sostituisce l’API EXIF di sistema segnalata da lint, senza disabilitare la regola. Photo Picker riusa Activity **1.12.3**. API verificate su fonti primarie: [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface). Tecnologie registrate nello stack Obsidian.

## Verifica e APK

Gate finale **`verifyAll --no-daemon` PASS** sulla build consegnata: **13 F0, 103 JVM, 109 Android**, zero failure/error/skipped e nessuna omissione. Passati build, lint, formattazione, confini e tre firme visive preesistenti. Nuova copertura: un test JVM dei metadati, dieci test Android IO/Room e cinque test UI; il caso CRUD storico del Diario scorre esplicitamente fino ai pulsanti Modifica/Elimina, ora più in basso dopo Foto.

I test F10 coprono zero/una/più foto, tutti gli orientamenti legali, hash/EXIF, immagini grandi e ruotate, URI revocato, input corrotto o oltre limite, spazio insufficiente, copia/miniatura mancanti, file alterato, rollback dei metadati, migrazione popolata, riapertura senza originale, conversione/eliminazione di bozze, eliminazione del viaggio, recovery e cancellazione fisica negata. UI Automator importa una foto sintetica dal **Photo Picker reale API 36**; la prova verifica anche pixel effettivamente disegnati, permessi dichiarati e lettura dopo rimozione dell’originale. I test di galleria ricreano realmente l’Activity e confermano la conservazione del testo dopo rimozione della foto.

Evidenze: `artifacts/f10-final-all.log`, `artifacts/f10-android-results.xml`, `artifacts/f10-visual-summary.txt`, `artifacts/f10-verification.json`. Indice completo nella build root esterna `root/reports/verification/index.html`. I log dei tentativi precedenti sono diagnostici storici; l’esito finale è quello sopra. Il gate anti-omissione conferma l’esecuzione di tutti i 109 test dichiarati.

APK: **56.295.856 byte**, package `it.faunavia.app`, minSdk **26**, targetSdk **37**. SHA-256 **CAAB0E63C8D3AC89C91A84EB0B91AF09E771FEA5B8EEE182ECE65CB67A3565BF**. Firma v2 verificata, certificato SHA-256 **96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24**, identico a `Faunavia-f9-mappe-debug.apk`: aggiornamento senza disinstallazione, con migrazione additiva verificata. Nessuna prova su telefono fisico o nuova verifica live GBIF/NNB.

Checklist delle convenzioni:

1. **PASS** — UI senza endpoint dei provider; selezione locale e IO fotografico nel solo adattatore Android.
2. **PASS** — Provenienze delle evidenze esterne inalterate; metadati della copia e data/fuso del ricordo conservati. Gli EXIF sensibili sono rimossi intenzionalmente dalle copie personali.
3. **PASS** — URI/file/spazio/scrittura hanno errori recuperabili; copie già importate si aprono senza originale e il Diario testuale resta disponibile.
4. **PASS** — Osservazioni personali, bozze ed evidenze/plausibilità restano distinte; le foto non identificano automaticamente una specie e le bozze non contano come specie osservate.
5. **PASS** — Nessun nuovo asset fotografico esterno nell’APK: fixture sintetiche generate soltanto nei test; immagini personali restano private e non sono caricate online.
6. **PASS** — Geometria e ranking invariati, regressioni cumulative deterministiche passate; nuove prove di file, orientamento e migrazione aggiunte.

## Limiti e fase successiva

F10 conserva copie controllate, non gli originali ad alta risoluzione. Acquisizione diretta da fotocamera, export/import e backup non sono inclusi; backup e relazioni fotografiche sono previsti in F12, notifica locale in F11. Nessun server/account nuovo. La disinstallazione elimina l’archivio privato. Lo stato MEX è aggiornato nel working tree e richiede commit/push per essere condiviso; nessun commit/push effettuato.
