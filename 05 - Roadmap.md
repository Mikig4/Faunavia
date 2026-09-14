# Roadmap

## Fase 0 — Decisioni e prova dati

- Confermare piattaforma, area geografica iniziale e gruppi animali.
- Scegliere la definizione di “presenza” e il raggio del corridoio.
- Provare 3–5 query GBIF su un percorso reale.
- Definire il formato del record normalizzato e la politica di attribuzione.

Risultato: una nota di decisione e un piccolo dataset di test riproducibile.

## Fase 1 — MVP Android e diario

- Scaffold Android Kotlin/Jetpack Compose e build APK locale.
- Database Room/SQLite per diario, cache, catalogo e preferenze.
- Autocomplete tassonomico per selezionare qualunque animale esistente, non solo i suggeriti.
- Import GPX/GeoJSON e posizione corrente.
- Visualizzazione mappa della traccia e del corridoio.
- Adapter GBIF con cache e gestione errori/rate limit.
- Lista di specie peculiari, con regole che escludono animali urbani comuni.
- Una scheda specie con fallback 2D e un primo modello 3D.
- Inserimento manuale di qualsiasi animale del catalogo, anche non suggerito.
- Foto locali associate all'avvistamento.
- Notifica locale serale solo se sono presenti avvistamenti nella giornata.

Risultato: l'APK produce una lista verificabile e permette di costruire un diario personale senza backend, account o connessione obbligatoria.

## Fase 2 — Qualità dell'esperienza

- Campionamento adattivo per lunghezza e habitat.
- Ranking spiegabile e distinzione chiara tra documentato/plausibile.
- Schede con stagionalità, habitat, sicurezza e conservazione.
- Cache invalidabile, export/import locale dei dati e gestione privacy.
- Test con più percorsi, inclusi percorsi senza tappe.
- Backup manuale di database e foto.

## Fase 3 — Libreria 3D coerente

- Manifest asset e pipeline Blender → GLB.
- 5–10 specie curate con licenze verificabili.
- Lazy loading, placeholder, preview e controllo peso.
- Test su dispositivo reale e fallback accessibile.

## Fase 4 — Offline e sincronizzazione opzionale

- Adapter iNaturalist dopo revisione licenze e limiti.
- Pacchetto mappa offline regionale, generato da dati OSM compatibili e con attribuzione.
- Dataset locali più ricchi per uso offline reale.
- Firestore solo per sincronizzare dati strutturati, dopo aver dimostrato che serve.
- Eventuale backup foto esterno solo dopo decisione esplicita sui costi.

## Criteri per non espandere troppo il progetto

Una funzione entra nel backlog solo se migliora direttamente: (a) trovare specie lungo un percorso, (b) capire perché sono state mostrate, oppure (c) esplorare la scheda dell'animale. Account, social, AI di riconoscimento e copertura mondiale restano fuori finché l'MVP non è piacevole e affidabile.

Per la sequenza operativa dettagliata vedere [[09 - Piano di sviluppo dettagliato]].
