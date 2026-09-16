# ADR 0001 — Perimetro, fonti e strategia geografica F0

- Stato: accettato per lo spike F0
- Data: 2026-09-14
- Ambito: fattibilità e contratti; nessun codice prodotto

## Decisione

La prima area di prova è la Lombardia e il percorso pilota è una geometria sintetica nel Parco Nord Milano. Il confronto usa altri quattro contesti lombardi: canale periurbano, corridoio fluviale, rilievo prealpino e centro urbano. I primi gruppi sono uccelli, mammiferi, anfibi e lepidotteri: coprono Article 12, Article 17 e il dataset MAES senza promettere un catalogo curato definitivo.

Il raggio predefinito resta 1 km ed è sempre modificabile. Su cinque tracce brevi lo spike usa campioni ogni 500 m, celle stabili WGS84 da 0,01° e inviluppi di query da 0,05°. Questa griglia è una scelta riproducibile per lo spike; F5 dovrà confrontarla con una griglia metrica europea prima di renderla definitiva.

## Strategia per provider

| Fonte | Modalità verificata | Limite interno F0 | Fallback | Esito 2026-09-14 |
|---|---|---:|---|---|
| GBIF Occurrence | poligono WKT | 50 record/pagina | bounding box/celle | HTTP 200 |
| NNB GeoAPI | bounding box documentato | 50 record | WFS 2.0 `GetFeature` con bbox | HTTP 503 |
| NNB WFS | bounding box | 50 record | cache stale esplicita | HTTP 200 |
| EEA Article 12 | envelope ArcGIS | 50 record | fixture/cache | HTTP 200 |
| EEA Article 17 | envelope ArcGIS | 50 record | fixture/cache | HTTP 200 |
| EEA MAES 2014 | snapshot tabellare | sottoinsieme versionato | nessuna inferenza se manca | HTTP 200 |
| CLCplus 2021 | `ImageServer/identify` puntuale | 25 punti per batch applicativo | cache con anno visibile | HTTP 200 |
| CLCplus 2023 | download a tile; servizio puntuale non trovato | n/d | 2021 solo come dato datato, mai equivalente silenzioso | ImageServer provato: 404 |

GBIF dichiara un massimo di 300 record per pagina di ricerca e un rate limit dinamico. F0 impone 50 per ridurre carico e dimensione della cache. Article 12/17 pubblicano `MaxRecordCount = 2000`; anche qui F0 usa 50. I limiti interni sono più restrittivi dei massimi tecnici e non sono dichiarazioni sui contratti di servizio.

## Provenienza e licenze

- Ogni record normalizzato conserva provider, dataset/versione, query, data di recupero, identificativo originale, licenza, attribuzione, qualità e percorso della fixture grezza.
- Le licenze GBIF si applicano per dataset/record. Gli esempi acquisiti sono CC BY-NC 4.0 e non autorizzano automaticamente riuso commerciale.
- Il layer NNB WFS non restituisce una licenza per record. Le note legali NNB consentono per default uso personale/non commerciale con citazione; un'eventuale distribuzione pubblica richiede verifica specifica del dataset o permesso.
- I dati EEA seguono la policy di riuso e richiedono riconoscimento della fonte; eventuali vincoli specifici del dataset prevalgono.
- CLCplus è gratuito e aperto con attribuzione alla fonte e indicazione delle modifiche.

## Compatibilità habitat

Il dataset MAES 2014 usa le associazioni `P` (ecosistema preferito), `S` (adatto) e `O` (occasionale). La fixture include esempi della regione biogeografica continentale per `Turdus merula`, `Triturus carnifex`, `Lycaena dispar` e `Sciurus vulgaris`.

CLCplus 2021 fornisce classi di copertura del suolo, non una corrispondenza scientifica diretta con MAES. F0 conserva quindi il codice raster grezzo e la nomenclatura. La tabella CLCplus → MAES resta un contratto curatoriale da validare prima di F7; in sua assenza il motore deve restituire `insufficient`.

## Criteri di evidenza

1. `documented`: esiste almeno un'occorrenza pertinente, licenziata e con qualità sufficiente.
2. `plausible`: l'areale interseca il corridoio e almeno un habitat è compatibile.
3. `insufficient`: manca areale o habitat, una fonte obbligatoria non è disponibile, la licenza non consente l'uso previsto o la qualità è insufficiente.
4. Stagione e trend modificano confidenza e spiegazione, non sostituiscono areale e habitat.
5. Una osservazione storica non viene mai presentata come presenza attuale.

## Rischi accettati e azioni successive

- NNB GeoAPI era indisponibile durante lo spike. F6 deve implementare health check, WFS fallback, timeout e cache stale senza retry aggressivo.
- La licenza NNB deve essere risolta per dataset prima di una distribuzione oltre l'uso personale.
- Il servizio puntuale CLCplus pubblico arriva al 2021; il percorso di accesso 2023 va chiuso prima di F7. Il 2021 può solo dimostrare il contratto tecnico.
- MAES 2014 è utile ma datato. Il dataset EEA 2022 degli ecological groups va valutato come aggiornamento, senza sostituire automaticamente la semantica MAES.
- La griglia in gradi non è metrica. F5 deve testare distorsione, confini di cella e antimeridiano e decidere se migrare a EPSG:3035.
- Le fixture GBIF possono contenere dati citizen-science e coordinate generalizzate; i flag di qualità restano obbligatori.

## Fonti primarie

- GBIF API: https://techdocs.gbif.org/en/openapi/
- GBIF data user agreement: https://www.gbif.org/terms/data-user
- NNB API e servizi OGC: https://www.nnb.isprambiente.it/it/usa-i-dati
- NNB note legali: https://www.nnb.isprambiente.it/it/note-legali
- EEA Article 12: https://bio.discomap.eea.europa.eu/arcgis/rest/services/Article_12/ART12_birds_2019_2024_public/MapServer
- EEA Article 17: https://bio.discomap.eea.europa.eu/arcgis/rest/services/Article17/ART17_2019_2024_public/MapServer
- EEA MAES 2014: https://sdi.eea.europa.eu/data/f5194384-83a2-4b9a-b7f6-c106a4b6308b
- EEA data policy: https://www.eea.europa.eu/en/datahub/eea-data-policy
- CLCplus Backbone: https://land.copernicus.eu/en/products/clc-backbone
- CLCplus data policy: https://land.copernicus.eu/en/data-policy
