# F8B: tracciato del viaggio e correzione Catalogo

La richiesta precisata il 1 ottobre 2026 comprende partenza, destinazione, date e tracciato completo. Il precedente collegamento al solo punto non soddisfaceva questo requisito. L'utente ha scelto la pianificazione sulla mappa interna, con apertura delle indicazioni anche in Google Maps, e ha autorizzato l'invio dei due estremi al servizio pubblico OSRM. F9 e F13 mantengono il perimetro concordato.

## Flusso implementato

1. Confermare partenza e destinazione tramite ricerca geografica o coordinate.
2. Scegliere le date e premere “Calcola percorso”. Il prototipo calcola percorsi in auto; mostra fino a tre alternative quando disponibili, con distanza, tempo indicativo e traccia sulla mappa.
3. Scegliere esplicitamente il percorso e salvare. Nome, raggio, interessi e uscite restano facoltativi.
4. Riaprire il viaggio con l'intera geometria conservata localmente. La ricerca degli animali usa il corridoio di quella traccia e le date del viaggio.
5. Aprire le indicazioni Google Maps fra partenza e destinazione, oppure aggiungere avvistamenti collegati al viaggio direttamente dal Diario.

Maps ricalcola le proprie indicazioni e non garantisce di seguire il tracciato scelto in Faunavia. Il suo normale collegamento non restituisce la geometria all'app. Date e traccia salvata rimangono in Faunavia. Fonte: [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).

Cambiare un estremo invalida la scelta; risposte tardive per estremi precedenti vengono ignorate. Una richiesta fallita conserva la traccia già salvata. I vecchi viaggi senza partenza/tracciato restano leggibili e modificabili, con apertura Maps del punto. Nessuna osservazione viene generata dal calcolo stradale.

## Catalogo

La ricerca comune usa GBIF Species Search con `qField=VERNACULAR`, backbone e filtro Animalia; l'autocomplete scientifico resta il fallback. La risposta reale usa `taxonomicStatus` e una lista di nomi vernacolari, invece dei soli campi della precedente fixture. Il nome italiano esatto ha priorità: “Merlo / Turdus merula”, identificativo `gbif:2490719`, precede alias uguali in altre lingue.

L'encoding usa API disponibili da Android 8; l'overload con Charset richiede API 33 e un controllo statico ora ne impedisce la reintroduzione nei moduli JVM. Non è stata accertata la versione Android del telefono segnalato, quindi non si attribuisce a quel difetto il suo errore specifico. Errori/retry sono espliciti, il caricamento termina anche dopo eccezioni e una richiesta superata non può togliere l'indicatore a quella corrente. Il timeout di lettura è limitato a 15 secondi; le selezioni locali restano utilizzabili.

## Stack tecnologico e persistenza

- OSRM Route API v1, servizio pubblico `router.project-osrm.org`, profilo auto. Endpoint esterni e parsing restano in `:core:exploration`, dietro `TripRouting`; nessun pacchetto o chiave aggiuntivi.
- MapLibre già presente: anteprima della geometria completa, distinta dalla successiva analisi del corridoio F5.
- Room schema 6 invariato: payload Trip JSON v2 con partenza e copia di geometria/metadati/provenienza, lettura compatibile v1. Le chiavi delle analisi legacy restano identiche; cambi di geometria invalidano quelle nuove.
- OSRM riceve soltanto gli estremi su richiesta, con limite di una richiesta al secondo, cache transitoria di dieci minuti e nessun retry automatico. La traccia confermata ha persistenza durevole distinta dalla cache.

Il server demo è per uso personale moderato, senza garanzie di disponibilità, latenza, aggiornamento o traffico attuale. La basemap regionale e il calcolo di nuove strade offline restano separati; F15 non viene dichiarata completata. La geometria è limitata a 10.000 punti e la risposta a 5 milioni di caratteri; richieste non gestibili falliscono senza sostituire la strada con una retta. L'analisi naturalistica F5 mantiene il pilot europeo. Fonti: [API OSRM](https://project-osrm.org/docs/v5.24.0/api/), [policy demo](https://github.com/Project-OSRM/osrm-backend/wiki/Demo-server).

I campi piccoli non salvati del form vengono ripristinati; una nuova scelta di traccia non ancora salvata richiede ricalcolo dopo ricreazione del processo, per evitare grandi geometrie nel bundle Android. Le tracce già salvate vengono rilette localmente.

## Verifiche

Gate completo `verifyAll --no-daemon --console=plain` verde il 1 ottobre 2026: 13 test F0, 82 JVM e 66 Android, senza errori, fallimenti o test saltati. Build, formato, confini, lint e controllo delle omissioni sono passati. I tre controlli visivi verificano le firme colore di Home, esplorazione e Viaggi/bozza, non un confronto completo di tutti i pixel. L'indice dei report è in `$env:FAUNAVIA_BUILD_ROOT/root/reports/verification/index.html`.

Smoke live GBIF nell'APK API 36: “merlo” mostra Turdus merula per primo e la selezione viene salvata; screenshot `artifacts/ux-preview/catalogo-merlo.png`. Nel flusso reale dell'app sono stati confermati Milano e Como, calcolato e scelto un percorso di 48,9 km (circa 44 minuti), salvato il viaggio e riaperto dopo arresto/riavvio senza ricalcolo; la mappa mostra l'intera traccia e i due estremi. Screenshot `artifacts/ux-preview/tracciato-milano-como.png`, acquisito prima dell'ultima correzione del contrasto della nota Maps. Una distinta chiamata dal computer con estremi sintetici Milano–Como ha restituito 1.008 vertici: non è la stessa geometria del test nell'app.

APK finale: `artifacts/Faunavia-f8b-viaggi-debug.apk`, versione `0.8.2-f8b`, versionCode 11, 55.811.950 byte. SHA-256 `46E7FF10B8AB309C43B4DB08CD91BEE7D1440B1C1B936C3C6698BCD604ED5C83`, identico all'output della build verificata. Gli APK precedenti conservano valore storico e non comprendono questo flusso completo.

Nessuna prova sul telefono fisico; non si presume risolto un problema specifico del dispositivo senza provarvi l'APK aggiornato. Le modifiche ai documenti e alla memoria MEX sono nel working tree e richiedono commit/push per essere condivise.
