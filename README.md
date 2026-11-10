# Atlante faunistico personale

**Diario per specie:** una scheda per animale incontrato, con tutti gli avvistamenti ordinati per data. Apri la scheda e scegli **Aggiungi avvistamento** per registrare un nuovo luogo, quantità e ricordo con la specie già selezionata. Foto, video e nomi personali restano associati al singolo incontro. Dettagli in [[39 - Diario per specie]].

Build corrente: `artifacts/Faunavia-specie-debug.apk`, **0.17.5-species-diary (27)**, Room 12/backup 3 e firma precedente. `verifyAll` del 2026-11-10: 13 test F0, 152 JVM, 177 Android, quattro firme visive, zero errori/test saltati/omessi. Prove su emulatore API 36; nessuna verifica su telefono fisico.

**Modelli 3D rimossi:** visualizzatore, importazione, asset, dipendenze e test dedicati eliminati su richiesta. Restano schede specie, foto e video con taglio in app. Compatibilità dei backup precedenti conservata. Dettagli in [[36 - Rimozione modelli 3D]].

Verifica storica della rimozione 3D: 13 test F0, 150 JVM, 163 Android e quattro firme visive, senza errori, test saltati o omessi. APK `artifacts/Faunavia-no3d-debug.apk`, 0.17.3-no3d (25), con la firma precedente; prove Android su emulatore API 36.

**Video privati:** dal Diario apri **Foto e video → Aggiungi video**, scegli inizio/fine con i cursori, prova lo spezzone e salvalo. Un video per ricordo, oltre alle foto, anche per le bozze da identificare; copia finale fino a 100 MB e originale intatto. Sostituzione, rimozione, conservazione durante l’identificazione e backup sono inclusi. Guida [[GUIDA-VIDEO]], implementazione/verifiche [[35 - Video privati e taglio]]. La baseline senza 3D era `artifacts/Faunavia-no3d-debug.apk`, 0.17.3-no3d (25), Room 11 e backup 3 con reader precedenti.

Gate della precedente build video verde: 13 F0, 150 JVM e 174 Android, zero errori/skipped/omissioni, lint/confini/formato e cinque firme visive. Anteprima reale e selezione dopo rotazione verificate su emulatore; resta la prova con i propri video sul telefono. Evidenze in `artifacts/video-verification.json`.

Baseline precedente (F17): ricerca online lungo percorso/tappa solo dal pulsante Richiedi aiuto; nessun catalogo offline predefinito di uscite. Foto Wikipedia nelle schede specie con crediti e fallback. Gate finale verde: 13 F0, 148 JVM, 165 Android, zero errori/skipped/omissioni, lint/confini/formato e cinque firme visive. APK artifacts/Faunavia-f17-online-debug.apk, 0.17.1-f17 (23), stessa firma precedente. Room 10 e backup 2 invariati. Guida [[GUIDA-FASE-17]], esiti/limiti [[34 - Ricerca online delle uscite]]. F14 è stata rimossa; F15 e F16 restano aperte.

**Backup locale:** Impostazioni → **Backup e ripristino** esporta diario, bozze, foto, video, viaggi, uscite, desideri, impostazioni in un ZIP nella destinazione scelta. Formato 3, con lettura dei backup formato 1 e 2 precedenti. L’import verifica file e relazioni, mostra i conteggi e sostituisce i dati soltanto dopo conferma; un errore prima del commit conserva lo stato precedente. Il file non è cifrato. Guide [[GUIDA-FASE-12]] e [[GUIDA-VIDEO]]. Gli allegati integri delle vecchie versioni restano conservati come file opachi. Se uno manca o è danneggiato, il backup lo segnala e lo esclude senza modificare i dati sul dispositivo. Il riepilogo giornaliero locale F11 resta configurabile dalle Impostazioni.

**Foto private:** dopo aver salvato un avvistamento o una bozza, apri **Foto e video** per aggiungere immagini dal Photo Picker. Miniature e copie controllate funzionano offline, senza upload o permessi generali sulla galleria; i metadati EXIF sensibili sono rimossi e l’orientamento viene applicato ai pixel. Una foto si può eliminare mantenendo il ricordo; identificare una bozza conserva tutte le immagini. Guida [[GUIDA-FASE-10]], dettagli e verifiche in [[27 - Rapporto Fase 10]].

Viaggi conserva partenza, destinazione, tappe con giorni e tracciati scelti; **Suggerimenti e desideri** aggiunge profili curati del pilota, viste tipici/più facili/mai osservati e lista desideri offline. La vista più facili dichiara la mancanza di stime confrontabili; non inventa un ranking. Il Catalogo permette di aggiungere un taxon selezionato ai desideri; il diario resta indipendente dai suggerimenti. Fonti, livelli di evidenza e ricordi personali rimangono distinti. Guida [[GUIDA-FASE-9]], verifiche e sole evoluzioni dei suggerimenti/desideri in [[24 - Rapporto Fase 9]]. Le funzionalità F8B precedenti restano documentate in [[23 - Tappe e giorni del viaggio]].

La ricerca parte da **Animali tipici**, con dodici profili pilota e vista completa secondaria. Schede compatte: nomi leggibili, habitat cliccabile e stagionalità; evidenze/fonti espandibili. La rotazione conserva risultati e analisi in corso. La distribuzione si consulta **nell’app per tutte le specie**: areale illustrato Commons quando disponibile e mappa navigabile delle segnalazioni GBIF, esplicitamente distinta dall’areale. Nomi italiani fuori dal pilota da identità GBIF esatte, altrimenti nome scientifico. **Curiosità cliccabili** e fonti per campo sono disponibili nelle schede F13, con dati mancanti espliciti e fallback 2D. Dettagli in [[GUIDA-FASE-13]] e [[31 - Rapporto Fase 13]].

## Obiettivo

Un'app Android personale centrata sul viaggio: destinazione confermata e date valide → animali pertinenti → Diario con foto private. Partenza e itinerario sono facoltativi e si possono aggiungere anche in seguito; senza tracciato, la ricerca usa l'area della destinazione. Quando presente, Faunavia conserva la traccia scelta sulla mappa interna. Google Maps apre le indicazioni fra gli estremi confermati e le ricalcola autonomamente. La ricerca delle specie avviene direttamente nel Diario. Esplorazione senza viaggio e percorsi importati sono facoltativi. Schede offline, curiosità con fonti, fallback 2D e foto Wikipedia con crediti completano la consultazione. Modifica e limiti di verifica: [[37 - Viaggi senza itinerario]].

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
- [[04 - Asset 3D]] — nota storica della funzionalità rimossa.
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

- [[GUIDA-FASE-17]] — scoperta pilota, uscite offline, diario collegato e foto Wikipedia.
- [[33 - Rapporto Fase 17]] — fonti, verifiche cumulative e APK 0.17.0-f17.
