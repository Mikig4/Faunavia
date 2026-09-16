# Rapporto Fase 0

Data di chiusura: 14 settembre 2026  
Esito: **gate superato**

## Perimetro chiuso

- Area pilota: Lombardia.
- Traccia principale: percorso sintetico nel Parco Nord Milano.
- Confronto: Naviglio Grande, Parco del Ticino, Monte Barro e centro di Milano, tutti sintetici e non destinati alla navigazione.
- Gruppi iniziali: uccelli, mammiferi, anfibi e lepidotteri; il catalogo generale resta Animalia.
- Corridoio: raggio predefinito di 1 km, configurabile; campionamento ogni 500 m.
- Partizione dello spike: celle WGS84 da 0,01° raggruppate in chunk di query da 0,05°. È provvisoria e va confrontata con EPSG:3035 in F5.

## Artefatti prodotti

- ADR su fonti, licenze, fallback e strategia geografica: `f0/adr/0001-perimetro-fonti-e-strategia-geografica.md`.
- Schemi JSON per fixture provider, record normalizzati e itinerari.
- Sette fixture provider versionate: GBIF occurrence/taxonomy, NNB WFS, EEA Article 12, EEA Article 17, EEA MAES e CLCplus.
- Sei esempi normalizzati: taxon, occorrenza GBIF, occorrenza NNB, areale, habitat e stagione.
- Registro di fonte/licenza/attribuzione/qualità, manifest delle query e hash SHA-256 delle fixture.
- Cinque tracce WGS84 con output atteso, fingerprint stabile e replay completamente offline.

## Esiti delle fonti

| Fonte | Esito | Decisione F0 |
|---|---|---|
| GBIF occurrence | HTTP 200, 13.522 risultati nel poligono al momento dello smoke test; fixture limitata a 5 | Provider primario, limite interno 50 e qualità/licenza per record |
| GBIF Species match | HTTP 200 per quattro taxa iniziali | Taxon GBIF accettato come identità persistibile |
| NNB GeoAPI | HTTP 503 in due tentativi | Health check e nessun retry aggressivo |
| NNB WFS | HTTP 200, 95 corrispondenze; fixture limitata a 3 | Fallback tecnico; riuso bloccato finché la licenza specifica non è risolta |
| EEA Article 12/17 | HTTP 200 | Envelope supportato; dati di reporting distinti dalle osservazioni |
| EEA MAES 2014 | HTTP 200, archivio tabellare leggibile | Crosswalk di prova P/S/O, esplicitamente datato |
| CLCplus 2021 | HTTP 200 su cinque punti | Contratto tecnico sul codice grezzo di copertura |
| CLCplus 2023 point service | ImageServer tentato: HTTP 404 | 2021 non è equivalente; accesso 2023 da chiudere prima di F7 |

## Validazione del corridoio

| Itinerario | Lunghezza km | Area stimata km² | Celle | Chunk query |
|---|---:|---:|---:|---:|
| Parco Nord Milano | 4,162 | 11,466 | 17 | 2 |
| Naviglio Grande | 5,986 | 15,113 | 31 | 5 |
| Parco del Ticino | 3,554 | 10,249 | 22 | 4 |
| Monte Barro | 2,192 | 7,525 | 16 | 1 |
| Centro di Milano | 2,965 | 9,071 | 21 | 3 |

Tutte le query restano sotto il limite interno di 50 record e i chunk non superano 0,05° per asse.

## Test

Comando riproducibile:

```powershell
npm --prefix f0 test
```

Esito finale:

- 14 validazioni schema: 7 fixture provider, 6 record normalizzati, 1 raccolta di itinerari.
- 13 test Node superati, 0 falliti, 0 ignorati.
- Copertura: WGS84, limite query, replay e hash offline, stabilità di celle/fingerprint, normalizzazione GBIF/NNB, taxon Animalia accettato, provenienza, fallback 503/404, non-equivalenza temporale e matrice `documented`/`plausible`/`insufficient`.

## Gate e rischi residui

Il gate F0 è superato: dataset di prova, ADR, rischi e criteri di evidenza sono leggibili e versionati senza codice prodotto Android.

Restano tre vincoli espliciti per le fasi successive:

1. F5 deve sostituire o confermare la griglia provvisoria dopo un confronto metrico EPSG:3035 e test dei bordi.
2. F6 deve implementare timeout, health check, WFS fallback e cache stale per NNB; l'uso pubblico/commerciale richiede una licenza specifica.
3. F7 deve chiudere l'accesso al prodotto CLCplus 2023 e validare scientificamente il crosswalk verso MAES. Senza areale e habitat compatibili il risultato resta `insufficient`.
