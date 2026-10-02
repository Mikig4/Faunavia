# Atlante faunistico personale

Stato: Fasi 0–8B e rifiniture verificate. Il gate completo è verde a 13 F0, 84 JVM e 70 Android, con lint, confini e tre controlli visivi. Viaggi permette di scegliere partenza, destinazione, date e tracciato completo in auto sulla mappa interna e organizzarlo in tappe libere con giorni separati. La singola tappa usa il proprio tratto e giorno per l'analisi e apre le relative indicazioni Google Maps. Il Catalogo cerca anche i nomi comuni italiani; il Diario cerca nel catalogo generale e collega i ricordi al viaggio anche senza coordinate. Le evidenze esterne restano separate dagli avvistamenti personali; eliminare un viaggio non cancella i ricordi. F9 e F13 mantengono il perimetro pianificato. APK `artifacts/Faunavia-f8b-tappe-debug.apk`, versione `0.8.3-f8b` (12); verifiche e limiti in [[23 - Tappe e giorni del viaggio]].

## Obiettivo

Un'app Android personale centrata sul viaggio: partenza, destinazione, date e tracciato scelto sulla mappa interna → animali pertinenti → Diario. Google Maps apre le indicazioni fra gli estremi e le ricalcola autonomamente; Faunavia conserva la propria traccia. La ricerca delle specie avviene direttamente nel Diario. Esplorazione senza viaggio e percorsi importati sono facoltativi. Suggerimenti personali, foto, schede complete e 3D arrivano nelle fasi già pianificate.

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
- [[19 - Rapporto Fase 8A]] — esplorazione per luogo, percorso e periodo, scheda essenziale e mappa.
- [[20 - Rapporto Fase 8B]] — viaggi, uscite, diario collegato, bozze persistenti e test cumulativi.
- [[GUIDA-FASE-8B]] — verifica e recupero dei flussi viaggio e diario senza perdere dati.
- [[21 - Rifinitura Viaggi e Diario]] — ingresso semplificato, collegamento a Maps e catalogo generale nel Diario.
- [[22 - Tracciato viaggio e Catalogo]] — precisazione di partenza e traccia completa, calcolo OSRM autorizzato e correzione della ricerca comune.
- [[GUIDA-GRADLE-LOOPBACK]] — recupero rapido se Gradle non riesce a stabilire la connessione loopback.

## Memoria tecnica

La memoria persistente del progetto è gestita da MEX nella cartella `.mex/`. Prima di lavorare sul codice o prendere decisioni tecniche, consultare `.mex/ROUTER.md` e il relativo contesto.
