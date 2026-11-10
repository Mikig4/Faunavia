# Diario per specie

## Flusso

Il Diario apre una scheda per ogni specie incontrata. Gli avvistamenti esistenti sono raggruppati automaticamente tramite l'identità tassonomica salvata: nomi personali diversi non separano una specie, mentre specie diverse con lo stesso nome comune restano distinte. Non si tratta di identificare il singolo individuo.

Aprire una scheda mostra gli incontri dal più recente, con data, ora, quantità, posizione, note, nome personale e galleria propri. **Aggiungi avvistamento** preseleziona la specie e l'eventuale contesto del viaggio/uscita: data e ora partono da adesso, quantità da 1, posizione e note vuote. Non copia i dati dell'incontro precedente e non richiede una nuova ricerca online. Salvare torna allo storico della specie; **Tutti gli animali** torna alle schede.

La copertina usa l'incontro più recente che abbia foto o video, anche quando l'ultimo avvistamento non ha allegati. Aprirla entra nello storico; da ciascun incontro si apre la sua galleria. Modifica ed eliminazione restano operazioni sul singolo avvistamento. Eliminare l'ultimo incontro rimuove la scheda dalla lista. Le bozze da identificare rimangono separate.

**Filtri e viste** apre i controlli di viaggio, uscita, specie, giorno, calendario e mappa. Dentro una scheda, calendario e mappa riguardano quella specie. Un avviso segnala eventuali filtri attivi. La specie aperta è conservata nel ripristino dello stato salvato.

## Dati e compatibilità

Nessuna migrazione: Room resta 12 e il backup resta formato 3. Le schede sono una vista dei record esistenti; ID degli avvistamenti, proprietà dei media, nomi personali, provenienza e collegamenti ai viaggi restano invariati. Nessuna nuova dipendenza, chiamata a provider o trasmissione di dati personali.

## Verifica — 2026-11-10

- `verifyAll --no-daemon --stacktrace` senza filtri: PASS in 15m24s.
- 13 test F0, 152 JVM, 177 Android: zero fallimenti, errori, test saltati o omessi; quattro firme visive; build, lint, formato e confini architetturali superati.
- Nuove regressioni: raggruppamento per identità, specie omonime separate, nuovo incontro offline senza copia dei vecchi dati, ripristino della scheda, eliminazione indipendente e copertina da media precedenti.
- Test esistenti aggiornati al percorso scheda → incontro → galleria e al pannello filtri; confermate foto/video, backup, notifiche e viaggi.
- Controllo visivo su emulatore Pixel 2/API 36 con dati sintetici. Nessuna prova su telefono fisico o runtime API 26; il ripristino della nuova selezione è verificato tramite il registro saved-state di Compose.

APK: `artifacts/Faunavia-specie-debug.apk`, **0.17.5-species-diary (27)**, stessa firma dell'APK precedente. Esiti: `artifacts/species-diary-verification.json`, `artifacts/species-diary-android-results.xml` e `artifacts/species-diary-verify-all.log`. Le modifiche rimangono nel working tree e richiedono commit/push per la condivisione.
