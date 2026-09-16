# Rapporto Fase 2

Data: 2026-09-16.

## Esito

Fase 2 completata. `verifyAll` verde il 2026-09-16: 38 test superati, zero fallimenti, errori o test saltati. Tutti i 13 test strumentati dichiarati sono presenti nel report del dispositivo.

## Incremento realizzato

- Nove modelli di dominio: `Taxon`, `TaxonPreview`, `SpeciesProfile`, `SuggestionProfile`, `Observation`, `ObservationPhoto`, `Route`, `SourceEvidence` e `AppSettings`.
- Provenienza completa: fonte, record, query, data di acquisizione, licenza, attribuzione, qualità e versione. I mapper conservano anche la precisione dei timestamp e distinguono evidenze documentate, plausibili e insufficienti.
- Nuovo modulo Android `:core:local`, separato dal dominio Kotlin: entità Room, DAO, mapper espliciti e quattro repository per diario, catalogo, percorsi e impostazioni.
- Container locale creato dall'app con clock iniettabile. Operazioni su disco fuori dal thread chiamante e scritture transazionali.
- `taxonId` obbligatorio con chiave esterna; trigger SQLite rifiutano taxa mancanti, vuoti, non Animalia o non accettati anche nelle scritture SQL dirette. Un taxon già usato non può diventare non valido.
- CRUD del diario e metadati foto, cache di catalogo/profili/evidenze, percorsi e impostazioni. Nessun provider o backend necessario.
- Query per giorno locale con gestione corretta di mezzanotte, fusi e passaggi fra ora solare e legale.

## Integrità e cancellazioni

- Le letture di record mancanti restituiscono `null` o liste vuote; le impostazioni assenti restituiscono i valori predefiniti con promemoria disabilitato.
- Creazione duplicata, riferimento invalido e modifica di un avvistamento inesistente falliscono senza scritture parziali. Un errore durante l'inserimento delle foto annulla anche l'avvistamento e le foto già inserite nella stessa transazione.
- Un taxon usato dal diario non può essere eliminato. Per taxa non usati, la cancellazione rimuove i relativi metadati di catalogo, suggerimenti ed evidenze.
- Eliminare un avvistamento rimuove i metadati foto collegati e restituisce i riferimenti rimossi. Eliminare una singola foto lascia intatto l'avvistamento. Le cancellazioni ripetute sono innocue.
- La gestione dei file fotografici fisici resta F10: F2 salva solo metadati, senza importazione, cancellazione di file o upload.

## Schema e migrazione

Room 2.8.4 e KSP 2.3.6 sono fissati nel version catalog. Gli schemi esportati sono in `core/local/schemas/it.faunavia.local.FaunaviaDatabase/1.json` e `2.json`.

Lo schema v1 è la fixture iniziale di F2; F1 non distribuiva un database. La migrazione 1→2 aggiunge il fuso delle impostazioni con valore legacy deterministico `UTC` e conserva diario, foto e configurazione. Il database nuovo usa direttamente v2. Non è attivo alcun fallback che cancelli i dati in caso di migrazione mancante.

La copia degli schemi precede la preparazione degli asset dei test. I test di migrazione usano gli schemi Room esportati e verificano sia database vuoti sia popolati.

Riferimenti tecnici: [release Room](https://developer.android.com/jetpack/androidx/releases/room), [migrazioni e schemi esportati](https://developer.android.com/training/data-storage/room/migrating-db-versions), [KSP 2.3.6](https://github.com/google/ksp/releases/tag/2.3.6).

## Verifica

- `verifyAll`: **PASS**, 144 task Gradle, 132 eseguiti, 8 da cache e 4 aggiornati; durata 5 minuti e 41 secondi nell'ambiente di verifica.
- F0: **13 test PASS**, inclusi fixture, provenienza, regole tassonomiche e geometria deterministica.
- JVM: **12 test PASS** — 8 dominio, 2 fake condivisi e 2 app; cinque nuovi test riguardano i modelli F2.
- Android Pixel 2 API 36: **13 test PASS**, zero errori e zero test saltati — 8 di persistenza, 2 di migrazione, avvio UI Automator, navigazione Compose e golden Home.
- Migrazione: database vuoto e popolato aggiornati da v1 a v2, schema validato, impostazioni e relazioni conservate, trigger ancora efficaci.
- Vincoli: rifiuto di taxon nullo, vuoto, inesistente, non Animalia o non accettato; modifica e cancellazione di taxa referenziati protette anche tramite SQL diretto.
- Persistenza: CRUD, rollback dopo errore foto, cancellazioni, round-trip di tutti i modelli, riapertura del database popolato e query nei cambi di ora legale.
- Lint app/storage, formattazione, confini architetturali e scansione segreti: **PASS**.
- Totale: **38 test**, di cui 15 aggiunti in F2; regressione F0–F1 preservata.

Un primo giro ha evidenziato un'omissione del runner: un metodo JUnit Kotlin con ritorno inferito dall'ultima `assertThrows` causava l'esclusione della classe di migrazione dal riepilogo, pur lasciando Gradle verde. La firma ora usa `runBlocking<Unit>`; `verifyDevice` confronta i nomi dei test dichiarati con quelli eseguiti e fallisce se ne manca uno.

I test di migrazione hanno inoltre esposto una combinazione incompatibile di dipendenze: JSON 1.8.1 nel test e serialization-core 1.7.3 vincolato dal runtime dell'app. L'app ora dichiara serialization JSON 1.8.1, allineando anche core e l'APK strumentato. Non sono stati rimossi test né ridotti i controlli di schema.

## Checklist di progetto

1. **Nessuna chiamata diretta a provider dalla UI — PASS.** Verifica statica dei confini; F2 usa solo storage locale.
2. **Fonte, timestamp, licenza e qualità conservati — PASS.** Campi obbligatori di provenienza e round-trip dei record persistiti.
3. **Fallback in caso di errore provider/rete — non applicabile a F2.** Nessun nuovo accesso di rete; gli errori di storage e di integrità vengono propagati senza perdita transazionale. La UI del diario resta F4.
4. **Osservato e plausibile distinti — PASS.** Enum ed evidenze persistite separati dal diario; regressione F0 e test di round-trip.
5. **Manifest e licenza per nuovi asset — non applicabile.** Nessun asset multimediale aggiunto; i dati dei test sono sintetici.
6. **Geometria/ranking con test deterministici — PASS per il perimetro corrente.** Nessun nuovo algoritmo geometrico o di ranking; regressione F0 mantenuta e validazione WGS84 invariata.

## Comando e artefatti

```powershell
$env:FAUNAVIA_SHORT_TEMP='C:\ftmp' # necessario solo nell'ambiente Windows con problema Java di loopback
. scripts/android-env.ps1
.\gradlew.bat verifyAll --no-daemon
```

Output sotto `%LOCALAPPDATA%\Faunavia\toolchains\builds\Faunavia`:

- APK F2: `app\outputs\apk\debug\app-debug.apk`, versione `0.2.0-f2`, version code 2.
- Indice: `root\reports\verification\index.html`.
- Test Room, migrazione e UI: `app\reports\androidTests\managedDevice\debug\allDevices\index.html`.
- XML dispositivo: `app\outputs\androidTest-results\managedDevice\debug\pixel2Api36\TEST-pixel2Api36.xml`.
- Test dominio: `core\domain\reports\tests\test\index.html`.
- Lint storage: `core\local\reports\lint-results-debug.html`.

## Perimetro residuo

Le schermate restano quelle placeholder di F1: ricerca tassonomica e diario interattivo sono rispettivamente F3 e F4. La persistenza è verificata chiudendo e riaprendo il database su file; questo sentinel non simula un riavvio fisico del telefono. I test usano l'emulatore gestito Pixel 2 API 36; non è stata eseguita una prova su telefono fisico. Il computer use non è necessario per questi test nativi automatizzati.

Restano gli advisory della toolchain su ABI del dispositivo e analisi Android lint del modulo JVM; il dominio viene compilato, controllato staticamente e testato su JVM. La fonte MEX è aggiornata nel working tree e richiede commit/push per essere condivisa.
