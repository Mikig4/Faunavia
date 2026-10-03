# Atlante faunistico personale

Stato: Fasi 0–13 e rifiniture verificate; F14 implementata, con revisione umana e prova su telefono necessarie per il completamento formale. Gate cumulativo automatizzato verde: 13 F0, 123 JVM, 155 Android, zero errori/skipped/omissioni, lint/confini/formattazione e cinque firme visive. APK corrente F14: `artifacts/Faunavia-f14-debug.apk`, versione `0.14.0-f14` (21), 86.635.610 byte, stessa firma precedente; Room 10, backup ZIP 2. Esiti e limiti in [[32 - Rapporto Fase 14]].

**3D e libreria personale:** dalla scheda specie apri **Modello 3D e libreria personale**. Merlo originale offline, rotazione/zoom e due clip con play/pausa; import, sostituzione e rimozione di GLB personali con crediti e copie private. Un file invalido conserva il precedente; simbolo 2D e diario restano disponibili. Guida [[GUIDA-FASE-14]]. Il modello è illustrativo, con revisione umana ancora da completare.

**Backup locale:** Impostazioni → **Backup e ripristino** esporta diario, bozze, foto, viaggi, uscite, desideri, impostazioni e GLB personali/crediti in un ZIP nella destinazione scelta. Formato 2, con lettura dei backup formato 1 precedenti. L’import verifica file e relazioni, mostra i conteggi e sostituisce i dati soltanto dopo conferma; un errore prima del commit conserva lo stato precedente. Il file non è cifrato. Guida [[GUIDA-FASE-12]] e aggiornamento [[GUIDA-FASE-14]]. Il riepilogo giornaliero locale F11 resta configurabile dalle Impostazioni.

**Foto private:** dopo aver salvato un avvistamento o una bozza, apri **Foto (n)** per aggiungere immagini dal Photo Picker. Miniature e copie controllate funzionano offline, senza upload o permessi generali sulla galleria; i metadati EXIF sensibili sono rimossi e l’orientamento viene applicato ai pixel. Una foto si può eliminare mantenendo il ricordo; identificare una bozza conserva tutte le immagini. Guida [[GUIDA-FASE-10]], dettagli e verifiche in [[27 - Rapporto Fase 10]].

Viaggi conserva partenza, destinazione, tappe con giorni e tracciati scelti; **Suggerimenti e desideri** aggiunge profili curati del pilota, viste tipici/più facili/mai osservati e lista desideri offline. La vista più facili dichiara la mancanza di stime confrontabili; non inventa un ranking. Il Catalogo permette di aggiungere un taxon selezionato ai desideri; il diario resta indipendente dai suggerimenti. Fonti, livelli di evidenza e ricordi personali rimangono distinti. Guida [[GUIDA-FASE-9]], verifiche e sole evoluzioni dei suggerimenti/desideri in [[24 - Rapporto Fase 9]]. Le funzionalità F8B precedenti restano documentate in [[23 - Tappe e giorni del viaggio]].

La ricerca parte da **Animali tipici**, con dodici profili pilota e vista completa secondaria. Schede compatte: nomi leggibili, habitat cliccabile e stagionalità; evidenze/fonti espandibili. La rotazione conserva risultati e analisi in corso. La distribuzione si consulta **nell’app per tutte le specie**: areale illustrato Commons quando disponibile e mappa navigabile delle segnalazioni GBIF, esplicitamente distinta dall’areale. Nomi italiani fuori dal pilota da identità GBIF esatte, altrimenti nome scientifico. **Curiosità cliccabili** e fonti per campo sono disponibili nelle schede F13, con dati mancanti espliciti e fallback 2D. Dettagli in [[GUIDA-FASE-13]] e [[31 - Rapporto Fase 13]].

## Obiettivo

Un'app Android personale centrata sul viaggio: partenza, destinazione, date e tracciato scelto sulla mappa interna → animali pertinenti → Diario con foto private. Google Maps apre le indicazioni fra gli estremi e le ricalcola autonomamente; Faunavia conserva la propria traccia. La ricerca delle specie avviene direttamente nel Diario. Esplorazione senza viaggio e percorsi importati sono facoltativi. Schede offline, curiosità con fonti, fallback 2D e illustrazioni 3D personali completano la consultazione.

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
- [[GUIDA-FASE-10]] — foto private, bozze, rimozione e gestione degli errori.
- [[27 - Rapporto Fase 10]] — incremento foto, migrazione, verifiche e APK.
- [[28 - Semplificazione del codice]] — residui rimossi, validazione/ricerca condivise e trasporto HTTP comune.
- [[GUIDA-FASE-11]] — attivazione, orario flessibile, permessi e riepilogo locale.
- [[29 - Rapporto Fase 11]] — notifiche giornaliere, migrazione Room e verifiche.
- [[GUIDA-FASE-12]] — export, import, sostituzione del diario e recupero dagli errori.
- [[30 - Rapporto Fase 12]] — formato ZIP, staging, rollback, verifiche e APK.
- [[GUIDA-FASE-13]] — schede, curiosità, fonti, fallback e consultazione offline.
- [[31 - Rapporto Fase 13]] — profili versionati, provenienza, 1,8×, quattro firme visive e APK.
- [[GUIDA-FASE-14]] — viewer, GLB personali, backup compatibili, pipeline e gate manuali.
- [[32 - Rapporto Fase 14]] — migrazione, validazione, ripetibilità, rendering e APK.

## Memoria tecnica

La memoria persistente del progetto è gestita da MEX nella cartella `.mex/`. Prima di lavorare sul codice o prendere decisioni tecniche, consultare `.mex/ROUTER.md` e il relativo contesto.
