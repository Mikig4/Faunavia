# Semplificazione del codice dopo F10

Il refactoring autorizzato introduce Android `0.10.1-f10` (17). Room resta 8; i payload JSON mantengono chiavi, tipi e precisione degli istanti.

## Modifiche

- Rimossi i residui F1: `LocationSource`, `SpeciesProvider`, `SpeciesSummary`, `FakeLocationSource`, `FakeSpeciesProvider` e il test del solo fake. `FakeClock` rimane utilizzato.
- `parseDiaryInput` condivide data, ora e quantità fra avvistamenti e bozze, sostituendo la coppia anonima con un risultato nominato. Le bozze accettano ora quantità con spazi esterni e mostrano lo stesso errore dettagliato degli avvistamenti.
- `FaunaviaHeader` condivide quattro intestazioni e `FaunaviaColors` raccoglie sei colori ricorrenti, mantenendo dimensioni, colori e tag dei test.
- `rememberTaxonomyLookup` condivide debounce, loading, retry, cancellazione e generazioni fra Catalogo e Diario. Risultati, errori e completamento obsoleti non sovrascrivono la ricerca corrente. Selezione e messaggi restano specifici delle schermate.
- Provenienza JSON condivisa dai mapper di cache, viaggi e snapshot; validazione comune per percorsi di foto e miniature.
- `:core:network` centralizza GET JSON e chiusura di stream/connessioni per sei client adapter, senza librerie esterne aggiuntive.

## Politiche HTTP conservate

| Adapter | Connessione / lettura | Limite | Errore HTTP |
|---|---|---|---|
| GBIF tassonomia | 5 / 15 secondi | come prima, nessuno esplicito | `GbifHttpException` prima della lettura |
| Occorrenze | 5 / 5 secondi | come prima, nessuno esplicito | status/body e `Retry-After` al gateway |
| Plausibilità | 5 / 5 secondi | come prima, nessuno esplicito | status/body all'adapter |
| Nominatim | 5 / 5 secondi | come prima, nessuno esplicito | status/body all'adapter |
| OSRM | 5 / 15 secondi | 5 milioni di caratteri | `IOException` prima della lettura |
| Metadati specie | 5 / 10 secondi | 1 MiB di byte UTF-8 | `IOException` prima della lettura |

Nominatim, OSRM e metadati conservano il loro User-Agent. Il client della plausibilità rimane disponibile per l'integrazione futura, attualmente non composto nell'app.

## Integrità e verifica

Le tabelle foto mantengono relazioni distinte per bozze e osservazioni. La cancellazione tenta tutti i file, senza il cortocircuito di `all { delete(...) }`.

Regressioni aggiunte: quantità con spazi nel Diario reale, parsing/errori, ricerca obsoleta, UTF-8, timeout/header, errore senza stream, rifiuto dello status prima della lettura, limiti byte/caratteri e chiusura dopo errori IO.

`verifyAll` passato in 9 minuti e 2 secondi: 13 F0, 111 JVM e 110 Android, zero failure/error/skipped/omissioni, lint, confini, formattazione e tre firme visive. Validazione MEX: 28 file, zero errori e avvisi.

Codice Kotlin di produzione: da 51 file / 9.261 righe a 56 file / 9.194 righe, 67 righe in meno includendo i componenti condivisi. I nuovi test sono esclusi dal confronto.

APK `artifacts/Faunavia-f10-refactor-debug.apk`, 55.794.007 byte, `0.10.1-f10` (17), stessa identità di firma F9/F10. Evidenze: `artifacts/refactor-all.log`, `refactor-android-results.xml`, `refactor-visual-summary.txt` e `refactor-verification.json`.

Checklist delle convenzioni:

1. PASS — le schermate invocano porte/adattatori; il gate confini non trova endpoint di provider nella UI.
2. PASS — provenienza completa e precisione degli istanti conservate dai mapper; persistenza e snapshot verificati.
3. PASS — fallback/retry per errori di rete nei test esistenti di Catalogo, Diario e provider.
4. PASS — livelli documentato/plausibile distinti; regressioni di plausibilità e presentazione verdi.
5. NON APPLICABILE — nessun nuovo asset fotografico o 3D distribuito; immagini di test sintetiche.
6. PASS — test deterministici di geometria e ranking eseguiti dal gate completo.

Codice, rapporto e note MEX sono nel working tree; commit/push restano necessari per condividerli. Nessun commit o push eseguito in questo intervento.

Nessun nuovo smoke live dei provider o test su telefono fisico; controlli deterministici con fixture, fake HTTP e dispositivo Android gestito.
