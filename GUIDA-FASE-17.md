# Fase 17 — Ricerca online di uscite e foto Wikipedia

1. Apri il viaggio e seleziona **Intero viaggio** oppure una tappa. Il percorso scelto definisce l’area della ricerca; per i viaggi precedenti senza traccia si usa il luogo confermato.
2. In **Uscite**, premi **Richiedi aiuto**. Solo questo clic avvia la ricerca online di riserve, parchi, punti di osservazione e itinerari a piedi in OpenStreetMap.
3. Confronta le proposte entro il raggio del viaggio, fino a 20 km dalla traccia. La distanza è in linea d’aria dal percorso al punto rappresentativo del luogo; non è il tempo di trasferimento o la distanza a piedi.
4. Scegli il giorno e i filtri. Cambiare data, ordinamento o filtro lavora sui risultati già ricevuti. I filtri su durata, specie desiderate e periodo documentato escludono i dati mancanti. OpenStreetMap normalmente non documenta fauna e mesi consigliati: questi campi restano sconosciuti.
5. Consulta **Fonte dell’itinerario**, coordinate, licenza e data. **Sito indicato in OpenStreetMap** apre il collegamento del record, quando disponibile. **Approfondisci su Google** apre una ricerca nel browser con i nomi pubblici di partenza/arrivo; la traccia e il diario non sono inviati a Google.
6. **Salva nel viaggio** conserva proposta, fonti e informazioni per la consultazione offline. Un secondo salvataggio della stessa proposta nello stesso giorno riapre la precedente uscita senza modificarne lo snapshot.
7. Dall’uscita puoi aggiungere un avvistamento identificato o una bozza collegata, con conferma dei dati personali. Una proposta non diventa un avvistamento.

## Rete e limiti

Non esiste più un catalogo offline predefinito delle uscite. La ricerca richiede rete; uscite salvate, diario, backup e pianificazione manuale restano locali. La rotazione conserva richiesta e risultati; dopo la chiusura del processo una nuova ricerca richiede un clic esplicito. **Riprova ricerca** ripete la richiesta, senza tentativi automatici.

Il prototipo invia al provider solo riquadri aggregati del corridoio europeo F5, non l’intera traccia, GPX o dati del diario. Una richiesta ha limiti di tempo, dimensione e risultati; percorsi molto lunghi interrogano al massimo 12 aree distribuite lungo il corridoio e dichiarano la copertura parziale. Seleziona una tappa per restringere la ricerca. Sono mostrate al massimo 40 proposte dopo i filtri; il limite è esplicito.

Sono esclusi i record con accesso privato o vietato dichiarato. L’assenza di un divieto non certifica l’accesso: verifica chiusure, ingresso e condizioni con il gestore. I centri OSM sono punti rappresentativi e possono differire dall’ingresso. Difficoltà sac_scale, accessibilità wheelchair, durata e distanza sono riportate solo se disponibili nella fonte. Non viene inventata una traccia del sentiero dalle coordinate rappresentative.

Le istanze pubbliche Overpass sono utilizzate per questa prova personale, con richieste limitate e senza prefetch. Una distribuzione stabile richiede un endpoint dedicato o un fornitore appropriato. [Indicazioni del gestore](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html).

## Foto nelle schede specie

Aprire una scheda carica la foto principale Wikipedia, quando disponibile, dopo controllo dell’identità scientifica e dei diritti Commons. Sono visibili autore, licenza, fonti e collegamento alla voce. Immagini assenti, ambigue o senza diritti verificati mantengono il simbolo generico e permettono di riprovare.

La prima foto richiede rete; metadati e cache delle immagini sono espellibili. I dati datati sono segnalati. Foto Wikipedia e cache temporanee non entrano nel backup del diario.

Esiti e APK correnti: [[34 - Ricerca online delle uscite]]. Il precedente pilota incluso è documentato come storico in [[33 - Rapporto Fase 17]]. Smoke separati: scripts/verify-outing-search-smoke.ps1 e scripts/verify-species-photo-smoke.ps1.
