# Fase 17 — Scoperta di uscite e foto Wikipedia

Verifica del 2026-10-03. F17 è stata anticipata su richiesta utilizzando il catalogo curato incluso e la persistenza/backup esistenti. F15 mappe/gazetteer regionali, F16 decisione sul sync e il gate umano/fisico F14 rimangono aperti.

## Risultato

**Viaggi → Uscite → Scopri luoghi e sentieri** propone cinque uscite documentate in tre luoghi pubblici della Lombardia. Destinazione, giorno, raggio, gruppi di interesse e desideri determinano confronto e pertinenza; ordinamento per distanza/durata e filtri su dati documentati. Fonti, data, accessi, coordinate rappresentative e dati mancanti sono consultabili. Le raccomandazioni stagionali dei gestori non diventano presenza attuale, livello F7 o probabilità.

Il salvataggio copia dettagli e fonti nel payload dell’uscita, consultabile offline e incluso nel backup. Identità stabile per viaggio/proposta/giorno; un secondo salvataggio riapre la stessa uscita senza sovrascrivere il precedente snapshot. Uscite di giorni diversi restano distinte. Diario e bozze conservano il collegamento; cancellare la pianificazione conserva i ricordi. Cambiare luogo/traccia rimuove i dettagli della proposta non più pertinenti, modificare nome/data li conserva.

Room rimane 10; outing payload v2 legge v1 e verifica i nuovi campi. Backup formato 2 comprende tutte le fonti della guida senza una nuova tabella; gli archivi precedenti restano leggibili attraverso i reader già disponibili.

**Scheda specie** ora carica la foto principale Wikipedia dopo controllo dell’identità scientifica Wikidata P225, della voce italiana/inglese e dei diritti/autore Commons. Foto, crediti, licenza, voce originale e fonti sono accessibili; assenza/errori conservano simbolo e retry. Cache normalizzata 30 giorni, stale esplicita, caricamento immagini limitato tramite adapter esistente. Nessuna nuova libreria, account o selezione tassonomica implicita.

## Fonti e confini

| Proposta | Dati documentati | Fonte |
|---|---|---|
| Sebino, percorso per tutti | 2 km andata/ritorno, circa 1 ora | [Gestore, sentieri](https://torbieresebino.it/sentieri/) |
| Sebino, nord-sud | 8 km, circa 3 ore | [Gestore, sentieri](https://torbieresebino.it/sentieri/) |
| Sebino, nord-centrale | 5 km, circa 2 ore per l’itinerario completo; riapertura parziale 2026 | [Sentieri](https://torbieresebino.it/sentieri/), [avviso centrale](https://torbieresebino.it/percorso-centrale-3/) |
| Palude Brabbia | centro visite e sentieri pubblici; torretta/riserva integrale con accompagnamento | [Lipu, fruizione](https://www.lipupaludebrabbia.it/oasi/fruizione-e-servizi/) |
| Bosco WWF Vanzago | visita guidata circa 2 ore, accessi ordinari nel fine settimana escluso agosto | [Visita](https://www.boscowwfdivanzago.it/visita-loasi/), [WWF, sito/fauna](https://www.wwf.it/dove-interveniamo/il-nostro-lavoro-in-italia/oasi/oasi-bosco-di-vanzago/) |

Fauna/periodi hanno fonti separate: [martin pescatore del Sebino](https://torbieresebino.it/riserva-naturale/martin-pescatore/), [avifauna Brabbia](https://www.lipupaludebrabbia.it/oasi/fauna/avifauna/) e la scheda WWF sopra. Il catalogo conserva sintesi fattuali originali: testi, fotografie e cartografia delle pagine non sono incorporati. I punti pubblici conservano fonte [OSM Brabbia](https://www.openstreetmap.org/node/3832743702), [OSM Vanzago](https://www.openstreetmap.org/way/254680883) con ODbL e precisione metrica non dichiarata, e [Wikidata Sebino](https://www.wikidata.org/wiki/Q3936730) con CC0 e precisione angolare dichiarata. Centro rappresentativo e indirizzo di partenza restano distinti.

Le tracce dei cinque itinerari non sono acquisite: la proposta senza geometria è esplicita. Tracce salvate/importate mostrano uno schema offline dei segmenti separati; non si generano sentieri dai punti fauna. Difficoltà/accessibilità mancanti restano tali, trasferimento non stimato. Mancanza di copertura e outage lasciano uscite salvate, import e pianificazione manuale disponibili. Non vengono distribuite mappe regionali F15.

API immagini: [PageImages](https://www.mediawiki.org/wiki/API:Pageimages) e [imageinfo](https://www.mediawiki.org/wiki/API:Imageinfo). Smoke controllato del merlo: identità Q25234, voce `Turdus merula`, file `Turdus_merula_Nesting.jpg`, JPEG, autore presente, CC BY-SA 3.0, miniatura HTTP 200. `artifacts/f17-wikipedia-smoke.json` conserva soltanto il riepilogo, senza bytes media. Lo smoke iniziale con ID errato è stato corretto con il resolver P225; il codice e i test rifiutano identità discordanti o ambigue.

## Verifiche

Gate completo non filtrato: `verifyAll --no-daemon --stacktrace`, 12m44s, PASS. Dopo il rafforzamento della fixture JVM per due identità Wikidata ambigue, `verifyAll --no-daemon` è passato in 1m23s: i test Android invariati riutilizzano il precedente esito completo. APK e codice prodotto coincidono.

- F0: **13** test PASS.
- JVM: **138**, zero failures/errors/skipped, incluse 8 verifiche scoperta e 7 foto Wikipedia.
- Android API 36: **162**, zero failures/errors/skipped/omissioni; 7 nuovi test verificano scoperta/salvataggio/restoration/bozza, outage/retry/snapshot datato, metriche assenti/date invalide, persistenza reale/v1/campi invalidi e foto/crediti/fallback/retry/restoration. Il round-trip ZIP esistente conserva anche la guida completa dopo riapertura.
- Build, lint, formattazione, confini e controllo segreti: PASS.
- Cinque firme visive precedenti, incluse profilo e pixel GLB/lifecycle: PASS.
- Smoke Wikipedia/Commons separato dai test deterministici: PASS, miniatura HTTP 200.
- Firma APK: identica alla F14, certificato SHA-256 `96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24`.

Checklist convenzioni:

1. Chiamate provider fuori dalla UI: PASS, boundary gate.
2. Fonte/data/licenza/qualità conservate: PASS, dominio/mapper/backup e crediti immagini.
3. Fallback/retry visibili per failure: PASS, UI e provider tests.
4. Avvistamenti e plausibilità distinti: PASS, F7 invariato e guide esplicitamente generali.
5. Asset/licenze: PASS, asset inclusi invariati/manifest validato, media remoti solo con diritti/autore verificati.
6. Geometria/ordinamento deterministici: PASS, distanze, filtri, tie-break, segmenti e località sensibili.

Report: `artifacts/f17-verify-all.log`, `artifacts/f17-final-verify-all.log`, `artifacts/f17-android-results.xml`, `artifacts/f17-android-report/index.html`, `artifacts/f17-verification.json`. Guida: [[GUIDA-FASE-17]].

Nessuna prova su telefono fisico; la restoration dei nuovi modali è una simulazione saveable-state e la riapertura Room usa database su file. Le foto UI sono sintetiche; il controllo remoto verifica metadati e HEAD della miniatura, non un download/render su telefono. Il catalogo è delimitato, senza verifica di accesso futuro o per ogni visitatore. Foto Wikipedia/cache sono espellibili e non entrano nel backup del diario.

## APK

`artifacts/Faunavia-f17-debug.apk` — **0.17.0-f17 (22)**, 86.030.530 byte, aggiornabile sopra la F14 grazie alla stessa firma.

SHA-256: `ADFE8B8A1C3B3A9C6C5B8CE4739F981A624E3D5155800E39F457EF583EAFD743`.

MEX: Router, architettura, offline, provenienza, stack e runbook sono aggiornati nel working tree; occorre commit/push per condividerli. Nessun commit/push eseguito. L’indice wiki è stato ricostruito e la validazione dei 30 file canonici passa senza errori/warning. Il primo tentativo aveva segnalato DB occupato e letture OneDrive; la materializzazione nativa dei file e il retry hanno risolto il problema senza modificare contenuti.
