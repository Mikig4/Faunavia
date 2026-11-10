# Rimozione della funzionalità 3D

Rimozione richiesta dall’utente il 2026-10-26, verifica finale il 2026-10-27. Build **0.17.3-no3d (25)**.

## Risultato

Eliminati pulsante e dialogo del modello dalla scheda specie, importazione e libreria personale, parser GLB, renderer Filament/gltfio e relative dipendenze. Rimossi il merlo incluso, sorgenti/preview Blender, pipeline di generazione e validazione Khronos, task Gradle `verifyGlbAssets` e metadati 3D del manifest.

Eliminate `F14ModelStoreTest`, `F14ModelUiTest` e la firma `model-v1.json`: **11 test Android dedicati** in meno. `verifyVisual` richiede le quattro firme rimaste per home, esplorazione, viaggi e scheda specie. I rapporti e le evidenze delle vecchie fasi sono conservati come storico.

Schede specie, fotografie con crediti, fallback 2D e video privati con taglio in app restano disponibili. Requisiti, roadmap, guide e contesto MEX sono aggiornati: non resta un gate umano 3D da completare.

## Compatibilità dei dati

Room **11** e backup **3** restano invariati. La tabella storica `personal_models` è inerte e viene letta solo dal backup; non esistono più repository di dominio o API applicative per importare/modificare/mostrare modelli.

Gli archivi conservano metadati e file integri come allegati opachi. Percorsi confinati, limite di 20 MiB/file e 100 allegati, inventario, dimensioni e SHA-256 restano controllati. Il ripristino pubblica file con nuovi UUID prima del commit atomico e pulisce le nuove copie se fallisce. Nessun file GLB viene interpretato o renderizzato. Questa scelta evita una migrazione distruttiva o la cancellazione implicita di copie personali.

Se un vecchio allegato è mancante o danneggiato, l’esportazione lo esclude soltanto dal nuovo archivio e avvisa quante copie mancano; l’avviso è visibile anche prima del ripristino. Database e file sul dispositivo restano invariati. Errori I/O diversi da file assenti o danneggiati restano espliciti.

Il test generale di round-trip verifica byte e metadati integri, omissioni segnalate e dati locali invariati; non introduce test di rendering o importazione 3D.

## Verifiche

`verifyAll --no-daemon`: **PASS in 13m 38s** il 2026-10-27. 13 test F0, 150 JVM e 163 Android, zero errori, fallimenti, test saltati o omessi; lint, confini, formato e quattro firme visive. Inclusi i nove test video e il round-trip dei backup con allegati storici integri, assenti e danneggiati. Il test delle notifiche verifica la chiusura effettiva del dialogo di sistema e il permesso negato. Log e risultati: `artifacts/no3d-final-gate-retry.log`, `artifacts/no3d-android-results.xml`, `artifacts/no3d-verification.json`.

APK `artifacts/Faunavia-no3d-debug.apk`: **60.353.351 byte**, SHA-256 `cc17326e888da26e79d90086645775f2fac8e50a4066c6c0ca1b5e4d09bb2f52`. Mantiene la firma precedente e non contiene asset GLB o librerie Filament/gltfio. Le verifiche Android sono state eseguite su emulatore API 36, non su telefono fisico; la fixture degli allegati storici usa byte sintetici.

## Controlli architetturali

1. PASS — Nessuna chiamata diretta ai provider introdotta nella UI.
2. PASS — Provenienza e colonne degli archivi conservate per i dati integri.
3. PASS — Foto, fallback 2D e retry della scheda mantenuti.
4. PASS — Avvistamenti, plausibilità e suggerimenti restano distinti.
5. NON APPLICABILE — Nessun nuovo asset; restano gli asset 2D con metadati e licenza.
6. PASS — Test deterministici di geometria e ranking nel gate; rimosse le suite dedicate ai modelli.

## Condivisione

Codice, documentazione e MEX sono nel working tree. Servono commit/push per condividerli; non sono stati eseguiti. L’APK debug mantiene la firma precedente per consentire l’aggiornamento locale. Le prove su telefono fisico restano da effettuare per la build corrente.
