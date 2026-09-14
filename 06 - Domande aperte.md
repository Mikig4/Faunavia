# Domande aperte

Queste risposte determineranno le prossime decisioni tecniche. Le domande sono ordinate per impatto sul progetto.

1. **Piattaforma primaria:** confermi Android nativo come prima piattaforma, con eventuale versione desktop/web solo in futuro?
2. **Area iniziale:** partiamo dall'Italia, da una regione/provincia precisa o da un altro territorio?
3. **Gruppi animali:** includiamo subito uccelli, mammiferi, rettili, anfibi, insetti e fauna marina, oppure definiamo un sottoinsieme?
4. **Significato di “ci sono”:** vuoi soprattutto evidenze di osservazioni reali, specie attese per habitat/stagione, oppure entrambe separate chiaramente?
5. **Distanza dal percorso:** quale fascia avrebbe senso per te: ad esempio 500 m, 1 km, 5 km, oppure un valore modificabile?
6. **Uso della posizione:** vuoi un aggiornamento occasionale quando apri l'app oppure un aggiornamento continuo durante una passeggiata?
7. **Stile 3D:** preferisci modelli realistici, naturalistici semplificati o low-poly illustrati?
8. **Percorsi:** quali formati possiedi già e vuoi importare: GPX, GeoJSON, KML, link a mappe, testo, altro?
9. **Offline:** ti basta consultare offline i risultati già caricati oppure vuoi poter analizzare nuovi percorsi senza rete?
10. **Specie delicate:** vuoi oscurare coordinate e dettagli per specie rare/protette/pericolose anche se la fonte li espone?

## Ipotesi con cui partire se non viene indicato diversamente

- Android nativo mobile-first; eventuale desktop/web solo dopo il primo rilascio.
- Italia come prima area di test.
- GBIF come fonte primaria e dati locali curati come fallback.
- Evidenze osservate e specie plausibili mostrate in sezioni diverse.
- Corridoio modificabile, inizialmente 1 km.
- Aggiornamento su richiesta, non tracking continuo.
- Modelli low-poly stilizzati.
- GPX e GeoJSON nel primo MVP.
- Offline per diario, foto, cache e schede già viste; pacchetto mappa regionale solo in una fase dedicata.

## Decisioni emerse nella discussione

- Il diario è manuale: i suggerimenti non limitano ciò che si può registrare.
- Le foto sono locali nell'MVP.
- La notifica serale è locale e viene generata solo se esiste almeno un avvistamento.
- Android è la piattaforma primaria; l'APK può essere installato senza Play Store.
- Firebase/Firestore è una possibile fase successiva, non una dipendenza iniziale.

## Nuove domande da chiudere

11. **Orario del riepilogo:** preferisci un orario fisso, come 20:30, oppure “tramonto + margine”?
12. **Foto:** vuoi usare soprattutto la fotocamera, la galleria, o entrambe?
13. **Backup:** accetti un export manuale su PC/OneDrive, senza sincronizzazione automatica?
14. **Mappa offline:** ti serve subito una regione scaricabile oppure va bene una mappa online con diario sempre offline?
15. **Distribuzione Android:** ti basta installare un APK generato da noi oppure vuoi pubblicare sul Play Store? La pubblicazione può introdurre costi.
16. **Catalogo offline:** per il primo rilascio va bene cercare nuovi animali con internet e conservare offline quelli già selezionati? È la soluzione più leggera.
