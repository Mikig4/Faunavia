# Atlante faunistico personale

Stato: Fasi 0–9 e rifiniture verificate. Il gate completo è verde a 13 F0, 102 JVM e 94 Android, con lint, confini, formattazione, controllo omissioni e tre firme visive. APK corrente: `artifacts/Faunavia-f9-mappe-debug.apk`, versione `0.9.2-f9` (15), con firma compatibile con F9/F8B.

Viaggi conserva partenza, destinazione, tappe con giorni e tracciati scelti; **Suggerimenti e desideri** aggiunge profili curati del pilota, viste tipici/più facili/mai osservati e lista desideri offline. La vista più facili dichiara la mancanza di stime confrontabili; non inventa un ranking. Il Catalogo permette di aggiungere un taxon selezionato ai desideri; il diario resta indipendente dai suggerimenti. Fonti, livelli di evidenza e ricordi personali rimangono distinti. Guida [[GUIDA-FASE-9]], verifiche e sole evoluzioni dei suggerimenti/desideri in [[24 - Rapporto Fase 9]]. Le funzionalità F8B precedenti restano documentate in [[23 - Tappe e giorni del viaggio]].

La ricerca parte da **Animali tipici**, con dodici profili pilota e vista completa secondaria. Schede compatte: nomi leggibili, habitat cliccabile e stagionalità; evidenze/fonti espandibili. La rotazione conserva risultati e analisi in corso. La distribuzione si consulta **nell’app per tutte le specie**: areale illustrato Commons quando disponibile e mappa navigabile delle segnalazioni GBIF, esplicitamente distinta dall’areale. Nomi italiani fuori dal pilota da identità GBIF esatte, altrimenti nome scientifico. **Curiosità cliccabili** restano F13. Correzioni, limiti e verifica in [[26 - Nomi, rotazione e mappe interne]]; rifinitura precedente in [[25 - Animali tipici e schede compatte]].

## Obiettivo

Un'app Android personale centrata sul viaggio: partenza, destinazione, date e tracciato scelto sulla mappa interna → animali pertinenti → Diario. Google Maps apre le indicazioni fra gli estremi e le ricalcola autonomamente; Faunavia conserva la propria traccia. La ricerca delle specie avviene direttamente nel Diario. Esplorazione senza viaggio e percorsi importati sono facoltativi. Foto, schede complete e 3D arrivano nelle fasi già pianificate.

## Vincoli guida

- Nessun costo obbligatorio durante l'uso quotidiano o nella creazione degli asset.
- Nessun account e nessun backend proprietario nel primo rilascio.
- Funzionamento utile anche senza tappe esplicite: il percorso viene campionato e trasformato in un corridoio geografico.
- Ogni risultato deve rendere consultabili fonte, data, area di osservazione e livello di attendibilità.
- La prima versione deve essere abbastanza piccola da poter essere mantenuta da una sola persona.

## Navigazione

- [[01 - Obiettivo e requisiti]] — cosa deve fare l'app e cosa è fuori ambito.
- [[02 - Architettura proposta]] — componenti, flussi e modello concettuale.
- [[03 - Dati e fonti]] — fonti gratuite, licenze e qualità del dato.
- [[04 - Asset 3D]] — pipeline per modelli e schede degli animali.
- [[05 - Roadmap]] — fasi di realizzazione e criteri di completamento.
- [[06 - Domande aperte]] — decisioni da prendere prima dell'implementazione.
- [[07 - Strategia mappa e database]] — cosa salvare localmente e quando valutare Firebase.
- [[08 - Diario avvistamenti e notifiche]] — inserimento libero, foto e riepilogo serale.
- [[09 - Piano di sviluppo dettagliato]] — attività ordinate, dipendenze e criteri di completamento.
- [[10 - Valutazione architetturale]] — giudizio aggiornato, problemi chiusi e rischi residui.
- [[11 - Rapporto Fase 0]] — decisioni del pilot, fixture, test ed esiti delle fonti.
- [[12 - Rapporto Fase 1]] — scaffold Android, toolchain, gate automatici e risultati dei test.
- [[13 - Rapporto Fase 2]] — dominio, Room, migrazioni, integrità del diario e 38 test di regressione.
- [[14 - Rapporto Fase 3]] — ricerca tassonomica, sinonimi, cache offline, schermata Catalogo e 51 test di regressione.
- [[15 - Rapporto Fase 5]] — import GPX/GeoJSON, proiezione metrica, corridoio, celle, fingerprint e stato dei gate.
- [[16 - Rapporto Fase 6]] — gateway GBIF/NNB, cache TTL/stale, provenienza, retry e verifiche.
- [[19 - Rapporto Fase 8A]] — esplorazione per luogo, percorso e periodo, scheda essenziale e mappa.
- [[20 - Rapporto Fase 8B]] — viaggi, uscite, diario collegato, bozze persistenti e test cumulativi.
- [[GUIDA-FASE-8B]] — verifica e recupero dei flussi viaggio e diario senza perdere dati.
- [[21 - Rifinitura Viaggi e Diario]] — ingresso semplificato, collegamento a Maps e catalogo generale nel Diario.
- [[22 - Tracciato viaggio e Catalogo]] — precisazione di partenza e traccia completa, calcolo OSRM autorizzato e correzione della ricerca comune.
- [[GUIDA-GRADLE-LOOPBACK]] — recupero rapido se Gradle non riesce a stabilire la connessione loopback.

## Memoria tecnica

La memoria persistente del progetto è gestita da MEX nella cartella `.mex/`. Prima di lavorare sul codice o prendere decisioni tecniche, consultare `.mex/ROUTER.md` e il relativo contesto.
