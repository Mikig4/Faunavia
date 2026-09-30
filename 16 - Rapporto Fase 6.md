# Rapporto Fase 6

## In breve

F6 collega il corridoio deterministico di F5 a evidenze di occorrenza documentate, senza rendere la UI o Room dipendenti da GBIF o NNB. Non predice la presenza attuale e non calcola ancora plausibilità: F7 aggiungerà areale, habitat e stagione; F8 presenterà i risultati.

## Implementato

- Nuovo modulo puro Kotlin `:core:occurrence`, dipendente solo da `:core:domain` e `:core:route`.
- Contratto `DocumentedOccurrence` con provider, identificativo originario, nome scientifico, data originale, coordinate/precisione esattamente fornite, URL, licenza, attribuzione, qualità, versione, query e recupero.
- Adapter GBIF Occurrence API v1: richieste `PRESENT` Animalia con coordinate e senza issue geospaziali, pagine al massimo da 300 record, fino a tre pagine per area; poligono per porzione e fallback deterministico ai bounding box dei chunk.
- Adapter NNB WFS: bounding box EPSG:4326, `startIndex` e paginazione limitata. La licenza per record assente è registrata come limite, non sostituita da un'assunzione di riuso.
- Gateway con massimo tre tentativi per timeout, rete, 429 e 5xx. Il backoff è esponenziale, salvo `Retry-After`; risposte malformate e altri 4xx falliscono subito.
- Deduplicazione solo per coppia `(provider, providerRecordId)`, quindi porzioni sovrapposte non duplicano lo stesso record ma fonti distinte non perdono provenienza.
- Cache TTL di sei ore, chiave composta dal fingerprint F5 e dagli adapter attivi. Gli esiti sono `NETWORK`, `FRESH_CACHE` o `STALE_CACHE`; se tutti i provider falliscono senza cache, l'errore rimane recuperabile.
- Room v5: `occurrence_cache` conserva solo il JSON dei record normalizzati con timestamp e scadenza. La migrazione v4→v5 è additiva; cancellazione per chiave e completa sono esplicite nel repository.
- Task `verifyOccurrenceSmoke`, separato dai gate riproducibili: esegue soltanto richieste live di controllo e non salva payload.

## Verifica eseguita

- 6 test JVM diretti F6: cache fresh/stale, retry/backoff, rate limit, deduplicazione, paginazione GBIF/NNB, scelta poligono/bbox, provenienza e mancata invenzione della precisione.
- Compilati `:core:occurrence:testClasses`, `:app:compileDebugAndroidTestKotlin` e `:app:assembleDebug` con il workaround Gradle in-process.
- Formattazione, confini architetturali e secret scan: verdi.
- Smoke online: GBIF HTTP 200 (13.522 match nel campione di Parco Nord) e NNB WFS HTTP 200 (95 match).
- APK debug: `artifacts/Faunavia-f6-debug.apk`, versione `0.6.0-f6` (`versionCode 6`).
- Gate cumulativo `verifyAll`: verde con test JVM, lint, 25 test Android sul dispositivo gestito, controllo anti-omissione e golden visuale.

## Limiti intenzionali

- Le osservazioni NNB WFS restano utilizzabili come evidenza con provenienza completa, ma la loro licenza per-record non è esposta: nessun riuso pubblico/commerciale è implicato.
- Non c'è ancora UI di risultati o mappa: F8 consumerà gli stati espliciti del gateway.
- La cache non rende disponibili nuove ricerche offline; mostra solo risultati già ottenuti e marcati come stale quando necessario.
