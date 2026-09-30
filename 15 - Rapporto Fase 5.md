# Rapporto Fase 5

## In breve

F5 trasforma un GPX, un GeoJSON o una posizione WGS84 in una descrizione spaziale deterministica e riutilizzabile. Il risultato non è ancora una lista di animali e non disegna ancora una mappa: prepara lunghezza, campioni, corridoio, celle, chunk di query e fingerprint che F6 userà per interrogare i provider e F8 per mostrare la geometria.

La fase è **completata e verificata**. Il gate cumulativo `verifyAll` esegue con successo i test JVM, la suite Android sul dispositivo gestito e il golden visuale.

## Cosa è stato implementato

- Nuovo modulo puro Kotlin `:core:route`, senza dipendenze Android, Room, rete o MapLibre.
- Import GPX 1.1 con più `trkseg`, supporto `rte` come fallback e parser XML protetto da DTD/entità esterne.
- Import GeoJSON `Point`, `LineString`, `MultiLineString`, `Feature` e `FeatureCollection`; coordinate obbligatoriamente WGS84.
- Rimozione dei soli duplicati consecutivi con avviso; segmenti separati non vengono collegati artificialmente.
- Campionamento geodetico per distanza con intervallo configurabile, predefinito 500 m.
- Corridoio metrico configurabile, predefinito 1 km, suddiviso in porzioni chiuse.
- Proiezione ellissoidale ETRS89 / LAEA Europe (`EPSG:3035`) implementata con round-trip verificato.
- Celle metriche stabili da 1 km e chunk da 5 km, con bounding box WGS84 pronti per gli adapter di F6.
- Fingerprint SHA-256 di ricerca che include geometria normalizzata, raggio, campionamento, precisione e griglia.
- Identità separata della geometria per deduplicare lo stesso percorso anche quando cambia la configurazione di ricerca.
- Salvataggio Room retrocompatibile: la colonna testuale esistente conserva ora segmenti e metadati in un payload versionato; i record F2–F4 nel vecchio formato restano leggibili senza migrazione schema.
- Schermata Compose Percorsi con picker Android, parametri espliciti, coordinate manuali, errori testuali, riepilogo e lista offline.
- Nessun upload: il file originale viene letto localmente e scartato; si salva soltanto la geometria normalizzata.

## Decisione geografica

La griglia F0 in gradi (`0,01°`) era riproducibile ma non metrica: la larghezza reale cambia con la latitudine. F5 adotta EPSG:3035 per il pilot europeo, così raggio e celle restano espressi in metri. L'area supportata è dichiarata e controllata. Un percorso fuori Europa o attraverso l'antimeridiano fallisce con un errore specifico; non viene trasformato con una proiezione inadatta.

Questa scelta non promette ancora copertura mondiale. Se il prodotto uscirà dall'Europa servirà una strategia multi-proiezione o globale esplicita, con nuovi fingerprint di contratto.

## Flusso dati

```text
picker Android / coordinate WGS84
        ↓
parser GPX o GeoJSON + validazione
        ↓
segmenti normalizzati, senza collegamenti inventati
        ↓
campioni geodetici + proiezione EPSG:3035
        ↓
corridoio + celle 1 km + chunk 5 km
        ↓
fingerprint di ricerca
        ↓
salvataggio locale della geometria valida
```

Un errore avviene prima della scrittura nel repository; un file invalido non modifica Room. Reimportare la stessa geometria non crea duplicati, mentre cambiare raggio o intervallo produce correttamente un fingerprint di ricerca diverso.

## Verifica eseguita

- 13 test F0: verdi.
- 32 test JVM core complessivi: verdi; 13 appartengono direttamente a F5.
- Casi F5 coperti: punto, linea, più segmenti, duplicati, file vuoto/malformato, geometria e CRS non supportati, coordinate fuori intervallo, densità di campionamento, raggio, stabilità celle/chunk/fingerprint, round-trip EPSG:3035, antimeridiano, area extraeuropea, deduplicazione e assenza di scritture su errore.
- Compilazione app, test JVM e test Android: verde.
- Formattazione, confini architetturali, secret scan e lint Android: verdi.
- APK debug: `artifacts/Faunavia-f5-debug.apk`, versione `0.5.0-f5` (`versionCode 5`).
- Gate device/visual: verde nel `verifyAll` cumulativo; la suite Android include i flussi Percorsi e il golden visuale, senza test omessi.

## Confini intenzionali

- Nessuna richiesta GBIF/NNB: arriva in F6.
- Nessuna classificazione di presenza o plausibilità: arriva in F7.
- Nessuna basemap o rendering MapLibre: arriva in F8.
- Il motore accetta una posizione; la schermata F5 offre coordinate manuali. L'acquisizione della posizione corrente con permesso Android verrà collegata nel flusso cartografico, senza cambiare il contratto del motore.
- Il file originale non viene archiviato e non viene inviato a servizi esterni.
