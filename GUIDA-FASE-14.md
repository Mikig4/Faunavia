# F14 — Modelli 3D e libreria personale

Build `0.14.0-f14` (21). Il 3D è un’illustrazione facoltativa: non identifica un animale e non aggiunge evidenze di presenza. La revisione umana del primo asset e la prova su telefono fisico restano necessarie per chiudere il gate F14.

## Installare l’APK

Apri `artifacts/Faunavia-f14-debug.apk` sul telefono e autorizza l’installazione per il gestore file scelto quando Android lo richiede. Minimo Android 8/API 26. La firma è identica a F13 e il version code aumenta a 21: puoi aggiornare l’app esistente mantenendo i dati, con migrazione aggiuntiva Room 9→10. È prudente esportare prima un backup locale da conservare; non occorre disinstallare Faunavia. Questa è una build debug personale, non una pubblicazione sullo store.

## Aprire e controllare un modello

1. Apri una scheda specie da Catalogo, Risultati, Viaggi o suggerimenti.
2. Tocca **Modello 3D e libreria personale**. Il modello viene letto solo qui; la mappa e le liste non lo caricano.
3. Trascina per ruotare, pizzica per ingrandire e usa **Ripristina vista** per tornare all’inquadratura iniziale.
4. Per un modello animato scegli una clip e premi **Riproduci** o **Pausa**. Un modello statico dichiara l’assenza di clip.
5. **Chiudi** torna alla scheda e al simbolo 2D. La chiusura rilascia viewer e risorse native; passando in background il rendering si ferma.

Per *Turdus merula* è incluso un merlo maschio stilizzato, con **Respiro** e **Guarda intorno**. Sono movimenti illustrativi. Geometria e materiali sono originali del progetto; la UI e il manifest dichiarano la revisione umana ancora da completare. Per le altre specie puoi importare un modello oppure usare il simbolo 2D già disponibile.

## Importare, sostituire e rimuovere

Apri **Importa GLB personale**, compila autore, fonte/origine, licenza/diritti d’uso e modifiche (scrivi “nessuna” quando appropriato). Conferma la provenienza e il diritto d’uso, poi **Scegli file GLB**. Il selettore Android apre il documento scelto; Faunavia salva una copia privata completa e la associa al taxon della scheda. I crediti dichiarati e la data di importazione restano visibili. Questa dichiarazione non equivale a una verifica indipendente della licenza o dell’identità zoologica.

È consentito un modello per taxon, fino a 100 modelli. **Sostituisci modello personale** ripete gli stessi controlli; file non valido, licenza vuota o spazio insufficiente conservano il modello precedente. **Rimuovi modello personale**, con conferma, elimina l’associazione: torna l’asset incluso se disponibile, altrimenti il 2D. Scheda, avvistamenti, foto, desideri e selezioni tassonomiche rimangono indipendenti dall’illustrazione.

Le copie non più referenziate sono recuperate dopo il periodo di sicurezza di 24 ore all’apertura dell’app, secondo le date dei file. La rimozione dall’interfaccia è immediata; non promette cancellazione fisica istantanea né un ulteriore giorno dopo ogni sostituzione.

## File supportati e limiti

Import GLB glTF 2.0 con risorse incorporate, massimo 20 MiB; geometria triangolare, massimo 60.000 triangoli di mesh e 120.000 triangoli considerando le istanze; 128 nodi, 32 materiali. Texture PNG/JPEG incorporate: massimo 2.048 pixel per lato, 16 immagini e 64 MiB decodificate totali. Fino a 32 clip con tempi finiti e crescenti, massimo un’ora. Trasformazioni TRS, skin e morph sono sottoposti ai controlli di struttura e del parser Filament; non tutti gli export glTF sono supportati.

Sono rifiutati riferimenti esterni, estensioni obbligatorie, accessor sparse e strutture fuori dai limiti. Per un export incompatibile prova un GLB standard con texture incorporate e senza compressioni/estensioni obbligatorie. L’app controlla header, buffer, accessor, indici, gerarchia, immagini e canali di animazione, poi usa il parser di risorse del renderer prima della pubblicazione. La validazione Khronos completa fa parte della pipeline dell’asset incluso; l’import personale usa il sottoinsieme mobile controllato dall’app.

File mancante/corrotto o errore del renderer mostrano una spiegazione e **Riprova 3D**. Puoi reimportare/rimuovere il personale e chiudere il 3D per usare il 2D. Un avviso di pulizia incompleta resta consultabile nel dialogo. Un GLB non può rendere un risultato “plausibile” o confermare un avvistamento.

## Backup e ripristino

Impostazioni → **Backup e ripristino** ora produce formato ZIP **2**, schema Room **10**, con tutte le 20 tabelle durevoli e i GLB personali completi insieme ai crediti, hash e associazioni. L’anteprima mostra anche il numero di modelli. Il modello incluso proviene dall’APK e non viene duplicato nell’archivio.

I backup formato 1 delle build F12/F13 (schema 9) restano importabili; lo schema 8 ha una fixture di compatibilità sintetica. Ripristinare un vecchio backup sostituisce anche la libreria personale, che in quel formato è vuota: esporta prima lo stato corrente se vuoi conservarlo. Le vecchie build non possono leggere il formato 2.

Il ripristino verifica inventario, percorsi, dimensioni, hash, righe e modelli in staging; prepara nuove copie complete e sostituisce le righe in una transazione. Errori prima del commit conservano i riferimenti precedenti. Modelli e foto condividono la protezione contro modifiche accodate durante il ripristino. L’archivio è locale e non cifrato; un document provider cloud può richiedere rete. Limiti comuni: 2 GiB totali, 10.000 file di risorse complessivi, 64 MiB di JSON e 100.000 righe. Vedi anche [[GUIDA-FASE-12]].

## Rigenerare l’asset incluso

Sorgenti in `assets-3d/blackbird/`: `create.py`, `export_glb.py`, `blackbird-v1.blend`, `preview.png` e `REFERENCE.md`. Delivery in `app/src/main/assets/models/blackbird-v1.glb`; metadati in `app/src/main/assets/asset-manifest.json`.

Il generatore usa Blender/bpy **4.5.3**. Su questa macchina il launcher portatile Blender ha un errore Windows SideBySide; il modulo ufficiale bpy esegue lo stesso script headless usando il Python 3.11 incluso, senza installazione globale. Il percorso è `%LOCALAPPDATA%/Faunavia/toolchains/blender/`.

```powershell
$f14Tools = Join-Path $env:LOCALAPPDATA 'Faunavia/toolchains/blender'
$f14Python = Join-Path $f14Tools 'blender-4.5.3-windows-x64/4.5/python/bin/python.exe'
& $f14Python -m pip install --target (Join-Path $f14Tools 'bpy-modules') 'bpy==4.5.3'
npm --prefix assets-3d ci
./scripts/build-3d-assets.ps1
```

Per la ripetibilità del GLB ripeti con `-SkipRender` e confronta SHA-256 del delivery. L’export originale è ordinato e canonico; il `.blend` e la preview sono artefatti di authoring, non byte-identici per definizione. Le due clip esportate corrispondono alle azioni dimostrative nel sorgente. L’exporter è specifico di questo studio con nodi rigidi/materiali senza texture: non sostituisce un exporter generico per asset personali.

`npm --prefix assets-3d run validate` esegue Khronos glTF Validator **2.0.0-dev.3.10** e aggiorna il manifest. `verifyFast` usa `--check`, richiede zero errori/warning e verifica che hash/peso/licenza/autore registrati corrispondano. Per ogni nuovo asset rifare reference, crediti, preview, limiti, verifiche mobile e revisione umana; non estendere automaticamente lo studio a nuove specie.

Fonti tecniche: [Blender Python](https://docs.blender.org/api/4.5/), [bpy ufficiale](https://pypi.org/project/bpy/4.5.3/), [Filament](https://github.com/google/filament), [Khronos glTF Validator](https://github.com/KhronosGroup/glTF-Validator). Riferimento naturalistico: [RSPB — Blackbird](https://www.rspb.org.uk/birds-and-wildlife/blackbird).

## Verifiche manuali per chiudere F14

Installare l’APK su almeno un telefono e provare offline: apertura/chiusura ripetuta, rotazione e zoom, clip/pausa, background/ripresa, import/sostituzione/rimozione, backup e ritorno alla scheda 2D. Misurare avvio e memoria sul dispositivo, controllare orientamento/pivot/materiali e accessibilità dei comandi. Completare la revisione umana anatomica, visiva e dei termini di distribuzione del merlo, registrando l’esito nel manifest/report. L’emulatore automatizzato non sostituisce questi due gate.
