# Decisioni e domande aperte

Le decisioni approvate non vengono riaperte durante l'implementazione senza una nuova scelta esplicita. Le domande ancora aperte hanno un default operativo, così non bloccano le prime fasi.

1. **[Chiusa] Piattaforma primaria:** Android nativo; desktop/web solo in futuro.
2. **[Chiusa] Area iniziale:** Lombardia; percorso sintetico principale al Parco Nord Milano, con altri quattro contesti regionali di confronto.
3. **[Chiusa] Gruppi animali:** catalogo Animalia completo; set iniziale di lavoro: uccelli, mammiferi, anfibi e lepidotteri.
4. **[Chiusa] Significato di “ci sono”:** evidenze documentate e plausibilità sono mostrate separatamente; “plausibile” richiede areale + habitat e usa la stagione come modificatore.
5. **[Chiusa] Distanza dal percorso:** default modificabile di 1 km, validato nello spike F0 su cinque tracce sintetiche; F5 ne implementa la geometria metrica definitiva.
6. **[Chiusa] Uso della posizione:** aggiornamento su richiesta, non tracking continuo.
7. **[Chiusa] Stile 3D:** low-poly illustrato, con pipeline semi-automatizzata e revisione umana.
8. **[Chiusa] Percorsi:** GPX e GeoJSON nell'MVP; KML successivo.
9. **[Chiusa] Offline:** diario, foto, cache e schede già viste subito; analisi offline di nuovi percorsi e mappa regionale in F15.
10. **[Aperta] Specie delicate:** default conservativo: non aumentare la precisione pubblicata e applicare regole di oscuramento configurabili.

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

- Ogni avvistamento persistito richiede una specie Animalia accettata; nessun nome libero o taxon irrisolto viene salvato.
- Il diario è manuale: i suggerimenti non limitano ciò che si può registrare.
- Le foto sono locali nell'MVP.
- La notifica serale è locale, usa una finestra oraria flessibile e viene generata solo se esiste almeno un avvistamento.
- Android è la piattaforma primaria; l'APK può essere installato senza Play Store.
- Firebase/Firestore è una possibile fase successiva, non una dipendenza iniziale.
- Il route engine adotta la strategia ibrida: porzioni di corridoio + celle stabili; gli adapter scelgono poligoni o bounding box.
- La logica di dominio non dipende da Android, Room, rete, mappa o renderer 3D.
- Il primo vertical slice completo viene realizzato prima degli asset 3D di produzione.
- Lo sviluppo e la verifica sono automatizzati con Gradle, test JVM/Room/Compose/UI Automator, emulatori gestiti, ADB e screenshot. Playwright è riservato a una futura superficie web/WebView.
- La pipeline 3D usa Blender Python headless e glTF Validator; l'AI accelera blockout e script, ma non sostituisce controllo scientifico e licenze.

## Nuove domande da chiudere

11. **[Chiusa] Orario del riepilogo:** orario locale scelto dall'utente con finestra flessibile gestita da Android.
12. **[Aperta] Foto:** default Photo Picker/galleria in F10; acquisizione diretta da fotocamera successiva se utile.
13. **[Aperta] Backup:** default export/import manuale su PC/OneDrive in F12, senza sincronizzazione automatica.
14. **[Aperta] Mappa offline:** default mappa online con diario offline; pacchetto regionale in F15.
15. **[Aperta] Distribuzione Android:** default APK installabile manualmente; Play Store solo dopo una decisione su costi e pubblicazione.
16. **[Aperta] Catalogo offline:** default ricerca online con cache locale dei taxa già selezionati; catalogo completo offline soltanto se l'uso reale lo richiede.
