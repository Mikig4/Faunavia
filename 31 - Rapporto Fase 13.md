# F13 — Scheda specie e fallback 2D

Data: 2026-10-03. Richiesta: stesso incremento verificato e documentato della F12, applicato alla F13 del piano. Nessuna nuova libreria esterna, nessun commit/push.

## Comportamento implementato

**Apri scheda specie** è disponibile dai risultati del Catalogo e dalle schede condivise di Risultati, Viaggi e suggerimenti/desideri. Il dettaglio comprende nomi, habitat e stagionalità generali, dimensioni, dieta, comportamento, conservazione e indicazioni di rispetto della fauna. La scheda iniziale resta compatta; curiosità e provenienza si aprono su richiesta. Ogni fatto conserva la propria fonte con tutti gli otto campi di `Provenance`. I dati mancanti restano espliciti.

Il nome ricevuto dal Catalogo o dalla presentazione F9 conserva anche la propria provenienza nel dettaglio; può essere distinta dalla fonte naturalistica del nome pilota. Se nessuna fonte del nome è disponibile, la UI lo dichiara. La consultazione non produce una selezione implicita in Room.

Dodici profili pilota sono inclusi nella versione `natural-history-2026-10-03-v1`, con schema di presentazione 1. Il matching scientifico conserva le sottospecie distinte. Per gli altri taxa si mostra il profilo locale o un fallback leggibile. Le informazioni naturalistiche non vengono promosse a evidenza di presenza o compatibilità locale. La distribuzione continua a usare gli adapter F9 e la sua legenda, senza feed IUCN o nuove inferenze F7.

Un simbolo generico originale 2D è sempre disponibile, con descrizione accessibile e dichiarazione che non è identificativo. Un disegno Canvas di riserva gestisce la risorsa assente/corrotta. Metadata, origine, licenza, versione e hash sono in `app/src/main/assets/asset-manifest.json`.

## Fonti e riuso

Le schede Lipu per i nove uccelli e quelle del Parco Nazionale Gran Paradiso per i tre mammiferi sono state consultate il 3 ottobre 2026. Ogni campo e curiosità collega la pagina specifica; l'ente Parco fornisce anche le indicazioni per osservare senza disturbare. Si conservano la data di consultazione e la mancanza di una data di pubblicazione verificata.

Sono inclusi brevi fatti in formulazione originale, senza importazione di paragrafi, fotografie o strutture delle pagine. Lipu indica CC BY-NC-ND 4.0 per il sito, con fotografie escluse. Le pagine del Parco non forniscono una licenza specifica di riuso verificabile: questa limitazione resta nella provenienza. Non si presume una licenza aperta per immagini o testi. Si omettono valori contraddittori nelle pagine consultate: ad esempio l'apertura alare della garzetta nell'intestazione e le lunghezze discordanti del picchio nero. Non si assegnano automaticamente categorie IUCN aggiornate. Riferimenti:

- [Lipu: martin pescatore](https://www.lipu.it/uccelli/conoscerli-proteggerli/martin-pescatore), [merlo](https://www.lipu.it/uccelli/conoscerli-proteggerli/merlo), [picchio nero](https://www.lipu.it/uccelli/conoscerli-proteggerli/picchio-nero).
- [Lipu: airone cenerino](https://www.lipu.it/uccelli/conoscerli-proteggerli/airone-cenerino), [garzetta](https://www.lipu.it/uccelli/conoscerli-proteggerli/garzetta), [svasso maggiore](https://www.lipu.it/uccelli/conoscerli-proteggerli/svasso-maggiore).
- [Lipu: upupa](https://www.lipu.it/uccelli/conoscerli-proteggerli/upupa), [aquila reale](https://www.lipu.it/uccelli/conoscerli-proteggerli/aquila-reale), [falco pellegrino](https://www.lipu.it/uccelli/conoscerli-proteggerli/falco-pellegrino).
- [Parco: stambecco](https://www.pngp.it/natura-e-ricerca/fauna/praterie-e-ambienti-rocciosi/lo-stambecco), [camoscio](https://www.pngp.it/natura-e-ricerca/fauna/praterie-e-ambienti-rocciosi/il-camoscio), [marmotta](https://www.pngp.it/natura-e-ricerca/fauna/praterie-e-ambienti-rocciosi/la-marmotta), [rispetto della fauna](https://www.pngp.it/visita-il-parco/come-comportarsi).
- [Creative Commons FAQ sui fatti e contenuti delle banche dati](https://creativecommons.org/faq/): riferimento alla distinzione fra fatti e espressione protetta; nessuna autorizzazione implicita a copiare testi/foto.

## Confini tecnici e dati

Modello, catalogo dei fatti e codec normalizzato vivono in `:core:exploration`; UI e cache Android vivono in `:app`. Si riusa il codec di provenienza della presentazione F9. La consultazione non salva taxa, desideri o evidenze e non modifica le 19 tabelle durevoli. Room resta 9, backup ZIP formato 1 e firme dei payload precedenti invariati.

La cache SharedPreferences conserva al massimo 64 profili da 128 KiB, con commit controllato e avviso in caso di scrittura fallita. I contenuti inclusi/locali sono disponibili senza rete. La copia precedente è fallback quando la lettura locale fallisce, con avviso; una lettura riuscita prevale sempre e una rimozione locale non fa risorgere dati vecchi dopo il restore. La cache è esclusa dal backup; il profilo Room resta incluso. Solo URL HTTPS validi senza credenziali o porte anomale raggiungono il browser, con errore visibile se manca un gestore.

## Verifiche e consegna

`verifyAll --no-daemon --stacktrace` **PASS**, in 11 minuti e 1 secondo: **13 F0, 123 JVM, 144 Android**, zero failure/error/skipped/omissioni. Build, lint, confini dei moduli, formattazione e quattro firme visive verdi. I tre goldens precedenti rimangono invariati; il quarto verifica la scheda, il simbolo 2D e contrasto del titolo ≥4,5:1. Incremento F13: otto test JVM e otto Android (sei UI, due storage). Semantica, fattore di font realmente renderizzato a 1,8× e screenshot controllati su emulatore API 36; nessun telefono fisico, sessione TalkBack reale o nuovo smoke dei provider. La cattura PNG letta prima della fine della scrittura è stata recuperata con esecuzione mirata verde, senza cambiamenti al codice, verificando il marker IEND e la visualizzazione completa.

APK `artifacts/Faunavia-f13-debug.apk`, **0.13.0-f13 (20)**, **56.935.930 byte**. Min SDK 26, target 37, Room 9, backup formato 1. SHA-256 APK: `E1961A025CB78E1D8E6D9A164DA0573C8364B3E8052DDBCC8D164222FE84C397`. Certificato SHA-256: `96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24`, uguale a F12 e alle versioni precedenti.

Evidenze archiviate: `artifacts/f13-all.log`, `f13-verification.json`, `f13-android-results.xml`, `f13-android-report/index.html`, `f13-visual-summary.txt`, `f13-signature.txt`, `f13-apk-metadata.txt` e schermate sintetiche `f13-profile.png`, `f13-curiosity.png`, `f13-large-text.png`. I contatori del gate completo sono archiviati prima delle eventuali esecuzioni mirate che sostituiscono il report esterno di Gradle.

Checklist MEX verificata:

1. **PASS** — nessuna chiamata a provider dalla UI; fatti inclusi o proiettati dai repository, distribuzione attraverso gli adapter esistenti.
2. **PASS** — fonte, data, licenza e qualità preservate nei campi e curiosità; provenienza del nome conservata o mancanza dichiarata.
3. **PASS** — dati mancanti, cache/lettura/scrittura fallite, immagine illeggibile e browser assente hanno fallback/avviso o retry visibili.
4. **PASS** — documentato/plausibile/insufficiente restano distinti; il sentinel torna a evidenze e mappa senza ricalcolare l'analisi.
5. **PASS** — asset originale con metadata, versione, attribuzione e SHA-256 coerenti; nessuna foto o GLB importati.
6. **PASS** — regressioni deterministiche di geometria/ranking F5/F7/F9 incluse nel gate cumulativo.

MEX aggiorna stato, confini, cache/provenienza, asset e runbook nel working tree; serve commit/push per condividerli, nessun commit/push eseguito. Obsidian registra milestone e uso di cache/vector/semantica nelle note Android e Compose. Guida: [[GUIDA-FASE-13]]. Prossima fase: **F14**, pipeline 3D e primo GLB opzionale. F15–F17 invariati.

Il primo gate completo ha eseguito tutti i 144 test Android con un solo fallimento del test F11 dopo il salvataggio dell'orario: `UiDevice.pressBack()` poteva navigare fuori dalle Impostazioni quando la tastiera si era già chiusa. Il test usa ora Espresso `closeSoftKeyboard()` e conserva l'asserzione sul vero dialogo Android di permesso negato. Il recupero mirato F11, insieme a `verifyFast`, è verde. Nessun test è stato disabilitato e le tre firme visive precedenti sono conservate.

La verifica del testo ingrandito misura il `fontScale` nel `TextLayoutResult` effettivamente renderizzato. Il dialogo ripropone al contenuto la densità del genitore, perché la finestra Android installa una propria `LocalDensity`; la nuova asserzione conferma 1,8× e il test conserva chiusura fissa, scorrimento e fallback leggibile. I sei test UI mirati passano con questa misura. Una seconda esecuzione cumulativa è stata interrotta per includere la correzione; il risultato definitivo sopra è quello dell'ultima esecuzione completa. Non è una prova del font scaler non lineare di un telefono reale o di una sessione TalkBack.
