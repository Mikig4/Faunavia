# Rapporto fase 8B su viaggi e diario collegato

La fase 8B introduce la schermata Viaggi e conserva localmente pianificazione e ricordi. È possibile creare, modificare ed eliminare viaggi con destinazione confermata, date, raggio di 0,05–20 km e gruppi animali di interesse. Risultati continua a funzionare senza un viaggio salvato. L'interfaccia mantiene il linguaggio visivo esistente e rende esplicite conferme, limiti di copertura e conseguenze delle cancellazioni.

## Pianificazione e risultati salvati

Le uscite possono usare un luogo manuale, un luogo salvato o un percorso già importato in Percorsi. La geometria del percorso viene copiata nell'uscita: rimuovere l'originale non distrugge la pianificazione. La data dell'uscita deve rientrare nelle date del viaggio; se queste vengono poi modificate, le uscite fuori intervallo restano conservate e segnalate.

Un'analisi si avvia soltanto con un ricalcolo esplicito. Usa le date del viaggio oppure la singola data dell'uscita e riutilizza F5–F7. I risultati scelti conservano area, periodo originale, data di salvataggio, livello di evidenza, spiegazioni, segnale stagionale, fonti e stati parziale/cache obsoleta. La chiave dell'analisi comprende area, date, raggio, interessi e geometria dell'uscita; cambiare questi input segnala la necessità di ricalcolare senza riscrivere la prova salvata. La semplice modifica del nome non invalida l'analisi.

Le schede riutilizzano i dettagli essenziali F8A, con fallback per profilo o immagine mancanti. Un risultato esterno non è un avvistamento personale. “L'ho visto” propone un taxon accettato solo quando la corrispondenza tassonomica è verificata; altrimenti richiede selezione. Precompila il viaggio e l'uscita del risultato, ma lascia vuote le coordinate personali e richiede conferma di data e posizione effettive.

## Diario e bozze

Il diario filtra per viaggio, uscita, specie e giorno. Il calendario mantiene anche gli avvistamenti senza coordinate; la mappa mostra soltanto posizioni personali inserite, senza collegarle con un percorso inventato. Il riepilogo delle specie e delle prime osservazioni usa il diario personale globale e viene ricalcolato dopo modifiche e cancellazioni.

Le bozze “da identificare” persistono separatamente con data, luogo opzionale, quantità, note e collegamenti. Sono modificabili ed eliminabili, ma non entrano nei conteggi delle specie identificate. La scelta di un taxon valido converte la bozza in un'unica transazione mantenendo identità e data di creazione: un errore ripristina la bozza e due conversioni concorrenti non duplicano il ricordo.

Eliminare un'uscita conserva i ricordi collegati al viaggio; eliminare il viaggio conserva avvistamenti, bozze e metadati foto, togliendo soltanto i collegamenti. La conferma descrive questa conseguenza prima dell'operazione.

## Persistenza e migrazione

Room passa da schema 5 a 6. La migrazione aggiunge colonne nullable al diario esistente e cinque nuove tabelle, senza ricostruire o svuotare le osservazioni. Trigger SQLite proteggono collegamenti e identità anche dalle scritture SQL dirette; le cancellazioni della pianificazione scollegano i ricordi prima delle cascade. Nessuna migrazione distruttiva è configurata.

I repository e le proiezioni personali sono contratti del dominio puro; Room e i mapper JSON rimangono in `core:local`. Le chiamate ai provider passano dai servizi già introdotti, mai da endpoint nel codice UI. Gli editor attendono il caricamento dei dati dopo il ripristino dello stato, preservando modifiche e identità ancora da salvare.

## Verifiche

`verifyAll --no-daemon --stacktrace` completato il 30 settembre 2026: 13 test F0, 71 JVM e 54 Android su Pixel 2 API 36, zero fallimenti, errori o test saltati. Passano anche build APK, lint, formattazione, confini architetturali, controllo dei test omessi e tre test golden versionati per home, esplorazione e viaggi/bozze.

Test nuovi F8B: 5 JVM nel dominio, 10 Android sulla persistenza, 11 Compose UI e 1 migrazione. Coprono validazione, riapertura del database, provenienza, percorso conservato, SQL invalido, cancellazioni non distruttive, rollback e concorrenza della conversione, riepiloghi, tassonomia verificata, coordinate personali, ricalcolo esplicito, indisponibilità provider, filtri, ripristino degli editor e retry di un salvataggio fallito.

Il test visivo viaggi/bozza è stato eseguito anche separatamente nell'emulatore e le due immagini sono state ispezionate. La golden automatica confronta una firma cromatica versionata, non ogni pixel del layout. Il renderer della mappa nei flussi UI è un fake deterministico; rimangono i test F8A del MapLibre reale e i test della geometria personale.

Report cumulativo nella directory esterna di build: `root/reports/verification/index.html`. Comandi e diagnosi in [[GUIDA-FASE-8B]]. Non sono stati eseguiti nuovi smoke sui provider live: i nuovi test usano fixture deterministiche.

## Limiti intenzionali

- Paesi e regioni rappresentano un campione attorno al punto confermato, non l'intero territorio; poter cercare un luogo non garantisce dati faunistici né copertura F5 extraeuropea.
- Gli interessi animali sono preferenze persistenti. Il contratto delle occorrenze non contiene una classificazione completa per gruppo: l'interfaccia richiede scelta manuale dei risultati invece di inventare un filtro tassonomico.
- I dati salvati sono disponibili offline, ma nuove ricerche e tile non sono garantite senza rete. Mappe e preparazione regionale offline restano F15.
- Foto delle bozze, notifiche, backup e schede complete restano rispettivamente F10, F11, F12 e F13. La scoperta di nuovi luoghi e sentieri resta F17; F9 non è inclusa.
