# Fase 14 — Pipeline GLB e libreria personale

Data: 2026-10-03. Build `0.14.0-f14` (21), Room 10, backup ZIP 2. L’implementazione introduce il primo modello originale e la libreria personale prevista dal piano. Il gate formale F14 rimane aperto: occorrono revisione umana anatomica/visiva/dei termini di distribuzione e almeno un telefono fisico, come richiesto dal [[09 - Piano di sviluppo dettagliato]].

## Cambiamenti funzionali

- Nella scheda specie condivisa compare **Modello 3D e libreria personale**; il viewer viene caricato su richiesta e si chiude tornando alla scheda 2D.
- Un merlo maschio stilizzato originale per *Turdus merula*, disponibile offline. Rotazione con trascinamento, zoom con pinch, ripristino dell’inquadratura, clip **Respiro** e **Guarda intorno** con riproduzione/pausa. Sono illustrazioni, senza valore identificativo o etologico.
- Import GLB personale con autore, origine, diritti/licenza e modifiche dichiarati dall’utente; copia privata, data, hash e associazione al taxon visualizzato. Un modello per taxon, fino a 100. Nessuna selezione implicita nel Catalogo o creazione di avvistamenti.
- Sostituzione controllata; input invalido o spazio insufficiente conservano il precedente. Rimozione confermata conserva scheda/diario/foto/desideri e torna all’asset incluso o al 2D. File mancanti/corrotti e problemi del renderer espongono errore, retry e recupero.
- Backup completo dei GLB personali e relativi crediti. Nuovo ZIP 2/schema 10, lettura dei backup ZIP 1/schema 9 F12/F13 e compatibilità sintetica schema 8. Ripristino mantiene staging, anteprima/conferma, pubblicazione di file unici e rollback prima del commit. Un vecchio backup ripristina una libreria personale vuota.

Uso, limiti e recupero: [[GUIDA-FASE-14]].

## Implementazione e pipeline

`PersonalModel` e repository sono nel dominio puro; `personal_models` è l’unica tabella aggiunta da migrazione Room 9→10. Non ha una foreign key alla selezione tassonomica personale: un’illustrazione non è una scelta di specie nel diario. Crediti e riferimenti restano validati dai mapper e dal backup. Lo snapshot corrente copre 20 tabelle.

`PersonalModelStore` usa `noBackupFilesDir`, UUID per ogni pubblicazione, file completi sincronizzati prima dei metadati e lo stesso mutex/generation delle foto. Sostituzione/rimozione lasciano le copie orfane al recupero dopo 24 ore secondo le date dei file. Il ripristino verifica modelli in staging, prepara percorsi nuovi e sostituisce le righe in una transazione; errori prima del commit rimuovono soltanto i nuovi file.

`GlbInspection` controlla GLB glTF 2.0 autosufficienti: <=20 MiB, JSON <=1 MiB, buffer/accessor/indici limitati, gerarchia senza cicli/parent multipli, numeri finiti, trasformazioni, skin/morph e animazioni coerenti. Limiti: 60.000 triangoli mesh/120.000 istanziati, 128 nodi, 32 materiali; massimo 16 immagini PNG/JPEG da 2.048 pixel per lato e 64 MiB decodificate; 32 clip, tempi crescenti <=un’ora. Riferimenti esterni, required extensions e sparse accessor sono rifiutati. Segue il parser nativo delle risorse Filament prima della pubblicazione. L’import personale non esegue il validatore Khronos completo e non certifica i diritti dichiarati.

Filament, gltfio e filament-utils **1.77.1** forniscono rendering e gestione GLB. Viewer e byte vengono richiesti soltanto dal dialogo 3D. Lifecycle pause/resume controlla le frame callback; detach rilascia luce/skybox e lascia a ModelViewer la distruzione unica di risorse/engine. Visibilità del dialogo e comandi salvabili appartengono alla composizione della scheda, per conservare clip e riproduzione durante la ricreazione. Vulkan viene scelto dove Android lo dichiara supportato; negli altri casi OpenGL. Sul presente emulatore Windows SwiftShader il rendering OpenGL aveva causato un access violation di qemu; Vulkan ha passato la prova nativa. Il parser senza surface non aveva esposto quel problema.

Authoring con **Blender/bpy 4.5.3** ufficiale, Python 3.11 portatile. Il launcher Windows Blender scaricato fallisce SideBySide; il modulo bpy esegue lo stesso script headless senza installazione globale. Conservati `create.py`, `.blend`, reference testuale e preview. L’exporter originale del solo studio canonizza geometria e valori: due esportazioni consecutive hanno SHA-256 identico. Non è un exporter generico di qualsiasi contenuto Blender.

Khronos **glTF Validator 2.0.0-dev.3.10**, versione npm bloccata: zero errori e warning. `build-3d-assets.ps1` genera e aggiorna `asset-manifest.json`; `verifyFast` valida e controlla l’inventario senza modificarlo. Delivery `blackbird-v1`: **172.764 byte**, **2.564 triangoli**, due clip, materiali senza texture, metri, glTF Y-up e pivot al suolo. SHA-256 `8f6aef194841bd5f5d2107d0b55e00ea472a5e8d05da187a6cb240f338033532`.

## Verifica automatica

`verifyAll --no-daemon` finale **verde in 10m 31s**: **13 F0, 123 JVM, 155 Android**, zero failure/error/skipped/omissioni; build APK, lint, confini, formattazione, validazione GLB e **cinque firme visive**. Tutti gli 11 test nuovi F14 sono presenti nel report cumulativo; il controllo anti-omissione è passato. Evidenze: `artifacts/f14-all-final.log`, `f14-verification.json`, `f14-android-results.xml`, `f14-android-report/index.html`, `f14-visual-summary.txt`, `f14-gltf-validation.json`, `f14-reproducible.txt`, `f14-model.png` e `f14-model-metrics.json`.

Test nuovi: migrazione popolata 9→10; statico/animato, sostituzione errata, metadati e riapertura; input troncato, URI esterno, limiti/indici/cicli/sparse, crediti mancanti; file mancante/corrotto/spazio insufficiente; round-trip completo e failure prima del commit; reader formato 1. UI: pixel reali del GLB, marker becco/anelli oculari, gesti, clip/pausa, rilascio, ricreazione, fallback/rimozione e import dal selettore SAF reale.

Prova nativa focalizzata: primo frame catturabile in **1.421 ms**, heap nativo totale del processo **90.815.920 byte**; 310 campioni arancio e frazione scura 0,02665. È una misura dell’emulatore software, comprende le altre allocazioni del processo e non equivale a memoria incrementale del modello o prestazione di un telefono. La cattura aspetta più frame per evitare di misurare il buffer iniziale ancora nero. La quinta firma visiva affianca quelle di home, esplorazione, viaggi e scheda.

Il primo run completo ha individuato una fixture F12 che assumeva positive tutte le tabelle, lo stato di un dialogo annidato perduto alla ricreazione e un nodo SAF obsoleto. Corretti i contratti delle fixture/stato e la preparazione del documento sintetico; i controlli restano nel gate. Non sono accettati test omessi come successi.

### Checklist MEX, voce per voce

1. **Nessuna chiamata provider dalla UI — PASS:** il percorso 3D è interamente locale; i confini esistenti dei provider restano verificati.
2. **Fonte, timestamp, licenza e qualità dei dati importati — PASS sul contratto:** crediti/hash/data sono conservati e ripristinati; diritti personali esplicitamente dichiarati dall’utente, modello illustrativo. Non si attribuisce una verifica indipendente inesistente.
3. **Fallback o retry visibile — PASS:** file/renderer indisponibili espongono recupero; scheda 2D e flussi precedenti rimangono accessibili.
4. **Osservato e plausibile distinti — PASS:** modelli e biologia non entrano nel motore di evidenza, né creano osservazioni o taxa selezionati.
5. **Manifest e licenza degli asset — PARZIALE:** autore/origine/modifiche/hash/versione/assi/peso/validazione presenti; geometria e materiali originali senza media esterni. Revisione umana dei termini di distribuzione, anatomia e resa ancora **PENDING**, registrata anche in manifest e UI.
6. **Geometria/ranking deterministici — PASS automatizzato:** export GLB ripetibile e controlli strutturali; regressioni deterministiche F5/F7 mantenute nel gate cumulativo.

## APK e limiti residui

APK: `artifacts/Faunavia-f14-debug.apk`, versione **0.14.0-f14 (21)**, **86.635.610 byte**. SHA-256 `0b99d2de51411adbb15f953ba0baef84ebdbfa287bb8bdf2cfaa12f2b6470380`. Firma verificata e identica a F13: certificato SHA-256 `96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24`. Metadata e verifica archiviati in `artifacts/f14-apk.json`, `f14-apk-metadata.txt` e `f14-signature.txt`. È una build debug per installazione personale; nessun servizio a pagamento o backend è stato aggiunto. L’aumento rispetto a F13 deriva dalle librerie native del viewer incluse per le ABI supportate.

Review umana anatomica/visiva/legale e prova su almeno un telefono fisico restano obbligatorie per dichiarare F14 completa. Verificare anche memoria/tempo sul dispositivo, sfondo/ripresa, apertura/chiusura ripetuta, orientamento/pivot/materiali e accessibilità reale. La copertura dei modelli inclusi è un’unica specie nominale; GLB personali fuori dal sottoinsieme documentato vengono rifiutati. Nessuna modellazione/authoring nell’app, nuovo provider naturalistico o test live delle fonti.

MEX aggiorna architettura, stack, storage, pipeline e runbook nel working tree; commit/push sono necessari per condividerli. Obsidian registra milestone e tecnologie effettivamente usate. Nessun commit/push eseguito e nessun gate umano è stato approvato automaticamente.
