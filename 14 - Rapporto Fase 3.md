# Rapporto Fase 3

Data: 2026-09-18.

## Esito

Fase 3 completata. Il gate `verifyAll` è verde con 51 test: 13 F0, 21 JVM e 17 test strumentati Android sul Pixel 2 API 36. L'APK debug è versione `0.3.0-f3`, version code 3.

## Incremento realizzato

- Nuovo modulo JVM `:core:taxonomy`: porta `TaxonomyProvider`, adapter GBIF Species v1, normalizzazione della query, filtro a soli taxa Animalia accettati, preferenza per specie/sottospecie e deduplicazione per ID accettato.
- L'adapter converte un suggerimento sinonimo nel record accettato tramite `acceptedKey`. Errori di timeout, rete e payload malformato diventano stati recuperabili e non eccezioni della UI.
- I risultati mantengono l'ID stabile `gbif:<usageKey>` e la provenienza completa: fonte, query, data di recupero, licenza, attribuzione, qualità e versione dell'API.
- Cache remota in memoria con TTL di cinque minuti, verificata con clock iniettabile. Il TTL non si applica ai taxa esplicitamente scelti: quelli sono il cache offline persistente.
- Room è alla versione 3. La tabella `taxon_aliases` salva e indicizza i nomi comuni, scientifici e sinonimi; selezione e alias vengono scritti nella stessa transazione. La migrazione v2→v3 è non distruttiva e conserva il diario esistente.
- La schermata **Catalogo** esegue debounce a 350 ms, mostra caricamento, vuoto, errore con fallback locale, identificativo della fonte e conferma la selezione disponibile offline. Un'anteprima senza licenza o non disponibile è dichiarata esplicitamente e non blocca il taxon.
- Non sono state copiate immagini del provider. Una futura integrazione media dovrà conservare autore, titolare dei diritti, licenza, URL sorgente e scadenza prima di sostituire il placeholder.

## Verifica

- F0: **13 PASS**. Le fixture, la provenienza e le regole tassonomiche del pilot restano riproducibili offline.
- JVM: **21 PASS** — 8 dominio, 2 fake condivisi, 2 app e 9 taxonomy. I test taxonomy coprono successo, query minima, sinonimo→accettato, filtro non Animalia, deduplica, preferenza di rango, vuoto, timeout, payload malformato, scadenza e cache offline della selezione.
- Android Pixel 2 API 36: **17 PASS**, zero fallimenti, errori o test saltati. Coprono schema v3, migrazione popolata e vuota, ricerca alias dopo riapertura, integrità F2, avvio, navigazione, golden Home e gli stati loading/empty/error del Catalogo.
- `verifyFast`: **PASS** — formattazione, controllo confini, scansione segreti, test host e lint.
- `verifyDevice` e `verifyVisual`: **PASS** — nessun test dichiarato è stato omesso dal runner.
- `verifyAll`: **PASS** — F0–F3, report centralizzato e golden eseguito.

La documentazione tecnica GBIF conferma gli endpoint Species v1 e che gli ID della Backbone restano supportati dall'API, pur con il Catalogue of Life XR come tassonomia primaria in evoluzione: [Species API](https://techdocs.gbif.org/en/openapi/v1/species), [interpretazione tassonomica](https://techdocs.gbif.org/en/data-processing/taxonomy-interpretation).

## APK e report

```powershell
$env:FAUNAVIA_SHORT_TEMP='C:\ftmp'
. scripts/android-env.ps1
.\gradlew.bat verifyAll --no-daemon
```

Output sotto `%LOCALAPPDATA%\Faunavia\toolchains\builds\Faunavia`:

- APK F3: `app\outputs\apk\debug\app-debug.apk`.
- Indice di verifica: `root\reports\verification\index.html`.
- Report Android: `app\reports\androidTests\managedDevice\debug\allDevices\index.html`.
- XML dispositivo: `app\outputs\androidTest-results\managedDevice\debug\pixel2Api36\TEST-pixel2Api36.xml`.

## Perimetro residuo

L'adapter reale è coperto con finti deterministici e la UI gestisce l'assenza di rete; non è stato aggiunto un test live dipendente dalla disponibilità del provider. Il database conserva solo i taxa selezionati, non un catalogo globale scaricato. Diario interattivo, anteprime con licenza verificata e prova su telefono fisico restano rispettivamente F4 e lavori successivi.

Le fonti MEX e questo rapporto sono artefatti canonici nel working tree: richiedono commit e push per essere condivisi.
