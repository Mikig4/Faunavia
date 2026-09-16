# Asset 3D

## Obiettivo visivo

Un atlante personale non deve iniziare con un modello fotorealistico per ogni specie. L'obiettivo dell'MVP è una rappresentazione riconoscibile, leggera e coerente, accompagnata da una scheda affidabile. Meglio pochi modelli curati che una libreria enorme e incoerente.

## Strategia a costo zero

### Asset originali

Creare in Blender modelli low-poly stilizzati per un piccolo gruppo di specie locali. Il file sorgente `.blend` resta archiviato; per l'app si esporta `.glb` con mesh, materiali e animazione minima se realmente utile. Blender è gratuito e open source; la pipeline deve restare utilizzabile senza servizi cloud a pagamento.

### Asset open-licensed

Si possono usare modelli già disponibili solo quando la licenza consente l'uso previsto. Per ogni asset registrare URL, autore, licenza, data di acquisizione, modifiche e obblighi di attribuzione. Non importare modelli “free” senza una licenza verificabile.

### Fallback

Ogni specie deve funzionare anche senza 3D: illustrazione o silhouette SVG/PNG, testo e icone di habitat. Il modello 3D è un arricchimento, non una dipendenza funzionale.

## Pipeline consigliata

```text
specie e riferimenti scientifici
        ↓
blocco low-poly in Blender
        ↓
controllo scala, orientamento, pivot e materiali
        ↓
export GLB/glTF
        ↓
ottimizzazione e controllo dimensione
        ↓
manifest asset + licenza + preview
        ↓
caricamento lazy nella scheda specie
```

La pipeline è semi-automatizzata e riproducibile: script Blender Python headless generano o trasformano scene, applicano convenzioni, esportano GLB e producono anteprime; il Khronos glTF Validator controlla il file; una revisione umana verifica anatomia, leggibilità e licenze prima dell'inclusione. La generazione image-to-3D può preparare un prototipo, ma non costituisce da sola un asset approvato.

## Regole tecniche iniziali

- Una specie = un asset principale e una preview leggera.
- Nessuna texture incorporata enorme; preferire dimensioni adatte a uno schermo mobile.
- Limitare poligoni, materiali e luci; la scena 3D deve avere illuminazione controllata dall'app.
- Definire una convenzione unica per unità, asse verticale, nome file e origine del modello.
- Testare sempre su telefono: peso del GLB, tempo di caricamento, memoria e comportamento touch.
- Aggiungere `asset-manifest.json` con hash, versione, autore, licenza e specie collegata.
- Rendere deterministici e versionati gli script Blender; non affidare l'asset finale a passaggi manuali non registrati.
- Validare automaticamente formato, scala, assi, pivot, materiali, texture, peso e collegamento al taxon prima del test Android.

## Primo set suggerito

Scegliere 5–10 specie realmente pertinenti alla zona di test, distribuite tra gruppi diversi. Il set esatto dipende dalla geografia dell'utente e va deciso prima di modellare; non va scelto solo in base a quanto è facile trovare un modello online.

## Blender con ausilio dell'AI

L'AI può aiutare molto, ma non dovrebbe essere la fonte finale della biologia del modello.

### Usi consigliati dell'AI

- generare una checklist anatomica e confrontarla con fonti affidabili;
- creare reference board e varianti stilistiche da usare come ispirazione;
- scrivere script Blender Python per rinominare oggetti, creare LOD, applicare materiali e controllare la scena;
- suggerire palette, pose e impostazioni di esportazione;
- fare una revisione tecnica preliminare di scala, poligoni e texture.

### Usi da trattare con cautela

- image-to-3D o modelli generativi: spesso producono anatomia, zampe, occhi e topologia incoerenti;
- texture generate senza verificare licenza e somiglianza a materiale protetto;
- ricostruzione di specie rare sulla base di una sola immagine.

### Pipeline personale consigliata

1. Raccolta di riferimenti con licenza verificabile.
2. Blockout manuale in Blender.
3. Uso dell'AI per script, controlli e varianti, non per sostituire la verifica scientifica.
4. Rifinitura low-poly e materiali semplici.
5. Export GLB, test su Android e registrazione di licenza/versione nel manifest.

Per il primo set sceglierei modelli statici o con rotazione, senza animazioni complesse. La fedeltà informativa della scheda conta più del realismo cinematografico.

## Strumenti di automazione approvati

- Blender in modalità headless con Python come pipeline primaria e ripetibile.
- Khronos glTF Validator come gate automatico dell'export.
- Screenshot/render di riferimento e test su emulatore o dispositivo per regressioni visive e prestazionali.
- Un eventuale Blender MCP è un acceleratore interattivo, non una dipendenza della build; va collegato solo dopo verifica di origine, permessi e telemetria.
- La verifica scientifica e delle licenze resta umana e obbligatoria.
