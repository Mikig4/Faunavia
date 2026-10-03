# Fase 17 — Luoghi, sentieri e foto Wikipedia

## Usare la scoperta

1. Apri o crea un viaggio con destinazione e date confermate.
2. In **Uscite**, scegli **Scopri luoghi e sentieri**. Il raggio è quello del viaggio, fino a 20 km dalla base; modifica il viaggio per cambiarlo.
3. Scegli un giorno del viaggio. Confronta distanza in linea d’aria, lunghezza, durata indicativa, difficoltà e accessi. Puoi ordinare per pertinenza, distanza o durata e filtrare per durata documentata, desideri e periodo consigliato documentato.
4. Apri le fonti per controllare condizioni aggiornate. Le specie sono riportate nella documentazione generale del luogo: non sono avvistamenti in tempo reale e non cambiano il livello delle evidenze F7.
5. **Salva nel viaggio** conserva proposta, fonti e dettagli. Se la stessa proposta è già salvata nello stesso giorno, l’azione la riapre senza sostituire il precedente snapshot. Giorni diversi possono avere uscite distinte.
6. Dall’uscita selezionata, **Aggiungi avvistamento** o **Ricordo da identificare** conserva il collegamento. Conferma specie, data e posizione effettive: le coordinate pubbliche del luogo non vengono copiate nel ricordo.

## Copertura e dati mancanti

Il catalogo incluso contiene cinque proposte in tre luoghi pubblici della Lombardia: Torbiere del Sebino (percorso per tutti, nord-sud, nord-centrale), Palude Brabbia e Bosco WWF di Vanzago. È una selezione pilota, non una ricerca mondiale o esaustiva.

Lunghezza e durata sono quelle documentate dai gestori. Difficoltà e accessibilità non documentate restano esplicite. Il filtro sulla durata esclude le proposte prive del dato. La distanza dalla base è geografica, verso un punto pubblico rappresentativo; non è la distanza stradale o il tempo di trasferimento. L’indirizzo di partenza, quando disponibile, è riportato separatamente.

Le tracce di questi cinque itinerari non sono incorporate: diritti di riuso e geometrie dei singoli sentieri non sono stati acquisiti. Nessuna linea viene inventata dai punti fauna. Le uscite con una traccia conservata mostrano uno schema offline dei segmenti, senza creare connessioni tra segmenti separati. Importazione GPX/GeoJSON e pianificazione manuale restano disponibili fuori copertura o in assenza di proposte.

Gli accessi vanno verificati prima di partire: il centrale del Sebino risulta riaperto solo parzialmente dopo la chiusura stagionale 2026; Vanzago richiede visita guidata ed esclude agosto dalle visite ordinarie. Gli avvisi e le fonti restano nella proposta, senza una promessa di apertura futura. Dopo 90 giorni la fonte conservata è segnalata come datata.

## Offline e backup

Il catalogo è incluso nell’APK. Proposte salvate, fonti, dettagli, eventuali geometrie e ricordi restano locali. Backup e ripristino F12/F14 includono automaticamente i nuovi dettagli dell’uscita nel payload versione 2; le precedenti uscite versione 1 restano leggibili, Room rimane 10 e il formato ZIP rimane 2.

Le pagine dei gestori e le mappe di base online richiedono rete. F15 (pacchetti regionali di mappe/gazetteer) e F16 (decisione sulla sincronizzazione opzionale) rimangono aperte: questo incremento F17 è stato anticipato su richiesta e usa il catalogo incluso e la persistenza già disponibile.

## Foto Wikipedia

Aprire **Scheda specie** avvia il recupero della foto principale della voce Wikipedia associata all’identità scientifica esatta. La voce italiana ha priorità; se manca la sua immagine, si verifica la voce inglese. Commons fornisce miniatura, autore, licenza e data. Sono accettate soltanto le licenze libere supportate e i domini media ufficiali.

Foto e crediti appaiono nella scheda, con **Fonte e licenza della foto** e **Apri voce Wikipedia**. Un’identità ambigua, diritti non supportati, una foto assente o un errore di rete mantengono il simbolo generico e **Riprova foto Wikipedia**. Non viene selezionato automaticamente alcun taxon per il diario.

La prima foto richiede rete. I metadati hanno scadenza di 30 giorni; la cache HTTP delle immagini è limitata e può essere rimossa dal sistema. Una foto in cache non aggiornata viene dichiarata. Foto Wikipedia e cache temporanee non entrano nel backup del diario.

## Verifiche e artefatti

Esiti cumulativi, APK, hash e limiti della verifica sono in [[33 - Rapporto Fase 17]]. Lo smoke online separato si ripete con `pwsh -NoProfile -File scripts/verify-species-photo-smoke.ps1`; non fa parte dei test deterministici.
