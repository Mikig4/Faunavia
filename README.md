# Atlante faunistico personale

Stato: Fasi 0–7 completate e verificate. Il motore F7 distingue evidenza documentata, plausibile e insufficiente con una spiegazione tracciabile, senza usare un CLCplus 2021 non equivalente o un crosswalk CLCplus→MAES non curato per promuovere una specie. Il gate cumulativo è verde: 13 test F0, 55 test JVM (15 F7), 25 test su dispositivo gestito, lint, controlli statici e golden visuale. La configurazione JVM evita il daemon monouso e il canale Unix che causavano l'errore loopback.

## Obiettivo

Un'app Android personale che, partendo dalla posizione corrente oppure da un itinerario importato, suggerisce specie peculiari dell'area e permette di registrare qualsiasi avvistamento con foto, diario, scheda informativa e modello tridimensionale.

## Vincoli guida

- Nessun costo obbligatorio durante l'uso quotidiano o nella creazione degli asset.
- Nessun account e nessun backend proprietario nel primo rilascio.
- Funzionamento utile anche senza tappe esplicite: il percorso viene campionato e trasformato in un corridoio geografico.
- Ogni risultato deve mostrare fonte, data, area di osservazione e livello di attendibilità.
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
- [[GUIDA-GRADLE-LOOPBACK]] — recupero rapido se Gradle non riesce a stabilire la connessione loopback.

## Memoria tecnica

La memoria persistente del progetto è gestita da MEX nella cartella `.mex/`. Prima di lavorare sul codice o prendere decisioni tecniche, consultare `.mex/ROUTER.md` e il relativo contesto.
