# F0 — Chiusura perimetro e spike dati

Questa cartella contiene solo artefatti di fattibilità, contratti, fixture e test. Non contiene codice dell'app Android.

## Decisioni operative

- Area pilota: Lombardia, con Parco Nord Milano come itinerario principale.
- Itinerari di confronto: Parco Nord, Naviglio Grande, Parco del Ticino, Monte Barro e centro di Milano.
- Gruppi iniziali: uccelli, mammiferi, anfibi e lepidotteri.
- Corridoio predefinito: 1 km, configurabile; campionamento di riferimento ogni 500 m.
- Griglia di spike: celle WGS84 da 0,01° e unità di query da 0,05°. È un contratto F0 per cache e fixture, non l'algoritmo geografico definitivo di F5.

Le geometrie degli itinerari sono sintetiche e servono per test riproducibili; non sono tracce di navigazione.

## Esecuzione

Prerequisiti: Node.js 22.5+ e PowerShell 7.

```powershell
cd f0
npm test
```

Il comando valida gli schemi JSON, rilegge tutte le fixture senza rete e avvia i test deterministici Node.

Gli smoke test reali sono separati perché la disponibilità dei provider non è deterministica. Gli esiti osservati il 14 settembre 2026 sono conservati in `fixtures/smoke-results.json` e descritti nell'ADR.

## Contenuto

- `adr/0001-perimetro-fonti-e-strategia-geografica.md`: decisioni, fonti, licenze, fallback e rischi.
- `contracts/`: schema dei record normalizzati e degli involucri di fixture.
- `fixtures/providers/`: piccoli snapshot delle risposte ufficiali.
- `fixtures/normalized/`: esempi normalizzati con provenienza completa.
- `fixtures/routes/`: cinque itinerari sintetici WGS84.
- `fixtures/query-manifest.json`: query riproducibili e limiti interni.
- `fixtures/source-registry.json`: attribuzione, licenza e condizioni di riuso.
- `tests/`: gate offline della fase.

## Regola di interpretazione

Un record di occorrenza utilizzabile può produrre `documented`. `Plausible` richiede insieme areale e habitat; la stagione modifica soltanto la confidenza. Qualunque fonte obbligatoria mancante produce `insufficient`.
