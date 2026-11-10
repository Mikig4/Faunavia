# F17 — Ricerca online su richiesta lungo il percorso

Aggiornamento del 2026-10-03, versione 0.17.1-f17 (23). Sostituisce il catalogo incluso di [[33 - Rapporto Fase 17]]. Gate finale completo: PASS.

## Comportamento

**Viaggi → selezione percorso/tappa → Uscite → Richiedi aiuto** avvia la ricerca online. Selezionare il viaggio, cambiare tappa, modificare filtri o ripristinare lo schermo non avvia una richiesta. Per i viaggi precedenti senza traccia si usa il luogo confermato. Il dialogo mostra caricamento, errore/retry, risultati vuoti, fonti e limiti della copertura.

Le proposte provengono da OpenStreetMap: riserve, parchi naturali, punti di osservazione e relazioni di itinerari a piedi. La pertinenza geografica usa il punto più vicino della traccia, rispettando i segmenti separati, entro il raggio del viaggio. La distanza mostrata è dal percorso al centro rappresentativo del luogo; non è un trasferimento stradale o un ingresso verificato. Un percorso OSM individuato non diventa automaticamente una nuova traccia percorribile.

Durata, distanza dell’itinerario, difficoltà sac_scale, accessibilità wheelchair e note di accesso/orario sono riportati solo quando disponibili nella fonte. Record con access/foot private o no sono esclusi. Specie e mesi consigliati non sono dedotti dai tag geografici; i filtri espliciti su questi dati escludono i record privi di documentazione. Il gruppo di interesse del viaggio non nasconde un luogo che non ha ancora dati faunistici.

**Sito indicato in OpenStreetMap** apre il link registrato nel record, senza dichiararlo una fonte ufficiale verificata. **Approfondisci su Google** apre una ricerca nel browser usando nomi pubblici di partenza/arrivo; non richiede una chiave Search/Places e non invia geometria, desideri o diario. Nessuna API a pagamento o nuova dipendenza è introdotta.

**Salva nel viaggio** mantiene una copia immutabile della fonte e dei dettagli, consultabile offline e inclusa nel backup. Identità viaggio/proposta/giorno e comportamento di diario/bozze restano invariati; lo stesso salvataggio riapre il precedente snapshot. Le uscite salvate con la precedente versione restano leggibili. Room 10, outing payload 2 con reader 1 e backup formato 2 sono invariati. Foto Wikipedia e relativi crediti/fallback restano disponibili nelle schede specie.

## Implementazione e confini

- `OutingSearch` e `OverpassOutingSearch` isolano richieste e normalizzazione in exploration. `OutingDiscovery` filtra e ordina una lista già ricevuta; il vecchio PilotOutingCatalogue esiste solo nelle fixture JVM/Android, escluso dall’APK.
- F5 produce riquadri aggregati di 20 km che coprono il corridoio europeo. Una richiesta POST contiene al massimo 12 riquadri distribuiti lungo l’area, senza traccia completa o dati personali. Se il corridoio ne richiede altri, la UI dichiara copertura parziale e suggerisce la selezione di una tappa.
- Limiti: timeout Overpass 20 s, connessione 5 s, lettura 30 s, risposta 2 MB, massimo 600 oggetti normalizzati e 40 proposte visibili dopo il filtro. Limite risultati, remark del server e record incompleti sono espliciti. Nessun prefetch o retry automatico; richieste seriali distanziate almeno 3 s e pausa dopo HTTP 429.
- `OutingSearchViewModel` conserva lavoro e risultati durante la ricreazione Activity; token e cancellazione impediscono a una richiesta superata di sostituire quella attiva. Dopo morte del processo la ricerca richiede un nuovo clic.
- Identità OSM, record/versione, timestamp del database e della consultazione, query, ODbL e attribuzione sono normalizzati prima della UI. Sito indicato e note OSM rimangono fatti distinti da accesso verificato o evidenza faunistica F7.

L’endpoint pubblico è adatto alla prova personale limitata; una distribuzione stabile richiede un endpoint dedicato o un fornitore appropriato. [Regole del gestore Overpass](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html). [Geometria e centri OSM](https://dev.overpass-api.de/overpass-doc/en/full_data/osm_types.html), [itinerari hiking](https://wiki.openstreetmap.org/wiki/Tag:route%3Dhiking).

## Verifiche

Gate finale non filtrato verifyAll --no-daemon: PASS in 12m43s, con nuova esecuzione Android sulla revisione che rispetta anche HTTP 429. Il precedente gate intermedio era passato in 12m53s.

- F0: 13 test PASS.
- JVM: 148, zero failures/errors/skipped; 9 nuovi test Overpass e il test POST si aggiungono alla suite precedente.
- Android API 36: 165, zero failures/errors/skipped/omissioni. Include selezione senza richiesta, filtri/restoration senza richieste aggiuntive, scope di tappa e intero viaggio e vera ricreazione Activity durante/dopo la ricerca.
- Build, lint, confini, controllo segreti, formato e cinque firme visive: PASS.
- Persistenza, lettura delle vecchie uscite e backup popolato: PASS nella suite completa.
- Smoke online separato Overpass: HTTP 200, 178 oggetti; nessun payload salvato.

Checklist convenzioni:

1. UI senza chiamate dirette al provider: PASS, gate dei confini e adapter exploration.
2. Fonte, data, licenza e qualità: PASS, normalizzazione OSM e persistenza/backup esistenti.
3. Failure con retry/fallback visibile: PASS, rate limit/cooldown, errore/vuoto e test UI.
4. Osservato e plausibile distinti: PASS, nessuna promozione F7 o inserimento nel diario dalle proposte.
5. Asset/licenze: PASS, nessun nuovo asset incluso e manifest precedente validato; OSM ODbL attribuito.
6. Geometria/ranking deterministici: PASS, midpoint del percorso, segmenti separati, filtri e limiti della ricerca.

APK: artifacts/Faunavia-f17-online-debug.apk, 0.17.1-f17 (23), 86.056.242 byte, min SDK 26/target 37. SHA-256 696C7F29819414367217C47308CD1B45EEF17731C2DE5BBB48603AB602DC0BBC. Firma identica alla precedente: certificato SHA-256 96e00fb74386cd7383852249ea80bb4acbd5daf99424c0fd5836c11cadc50f24. L'hash della copia consegnata coincide con l'APK prodotto dal gate finale.

Evidenze: artifacts/f17-online-final-verify-all.log, artifacts/f17-online-verification.json, artifacts/f17-online-android-results.xml, artifacts/f17-online-android-report/index.html e artifacts/f17-online-overpass-smoke.json. MEX wiki validata: 30 file, zero errori/warning; indice ricostruito dopo materializzazione dei Markdown OneDrive. Il grafo di codice era stale e non risolveva il target Kotlin: impatto/callers verificati direttamente con rg.

La prova HTTP separata su un’area pubblica Lombardia ha risposto 200 con 178 oggetti e nessun remark parziale. `artifacts/f17-online-overpass-smoke.json` conserva solo stato, conteggio, fonte e timestamp, senza payload o geometrie personali. Il test remoto dimostra l’accessibilità del servizio in quella prova, non la completezza di ogni itinerario o la disponibilità futura.

La checklist convenzioni comprende confine UI/provider, provenienza completa, retry/fallback visibili, separazione tra suggerimenti/avvistamenti/evidenze, licenze e test deterministici di geometria/ranking. Nessun nuovo asset o dipendenza; nessuna prova su telefono fisico. F14 revisione umana/fisica, F15 pacchetti regionali e F16 decisione sync rimangono aperti.

Guida: [[GUIDA-FASE-17]]. Codice e documentazione MEX sono nel working tree; commit/push necessari per condividerli, non eseguiti.
