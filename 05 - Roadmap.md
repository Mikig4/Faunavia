# Roadmap

La roadmap è una sequenza di fasi chiuse da gate verificabili. Una fase produce un incremento utilizzabile e non si apre la successiva finché build, test propri e gate di non regressione richiesti non sono verdi.

| Fase | Incremento | Gate principale |
|---|---|---|
| F0 | decisioni, spike dati e fixture | fonti e query riproducibili, nessun codice prodotto |
| F1 | scaffold Android e automazione | APK installabile + `verifyFast`, `verifyDevice`, `verifyVisual`, `verifyAll` |
| F2 | dominio, Room e repository | vincoli, CRUD, migrazioni e persistenza dopo riavvio |
| F3 | ricerca tassonomica | taxon Animalia accettato, sinonimi, cache e fallback offline |
| F4 | diario manuale | creazione/modifica/eliminazione offline con specie obbligatoria |
| F5 | route engine | GPX/GeoJSON → corridoio, porzioni e celle stabili |
| F6 | gateway occorrenze | adapter, cache, provenienza, retry e deduplicazione |
| F7 | motore di plausibilità | areale + habitat obbligatori, stagione come modificatore |
| F8 | risultati e mappa online | percorso completo import → risultati spiegati → mappa |
| F9 | suggerimenti peculiari | distinzione dai taxa comuni e dal catalogo generale |
| F10 | foto locali | Photo Picker, copie controllate, privacy e gestione errori |
| F11 | notifica flessibile | WorkManager, fuso, permessi, zero notifiche vuote |
| F12 | export/import | archivio verificato, ripristino e rollback sicuro |
| F13 | scheda specie e fallback 2D | contenuto tracciabile, accessibile e disponibile dalla cache |
| F14 | pipeline e primo asset 3D | GLB validato, manifest, budget mobile e fallback integro |
| F15 | mappa regionale offline | licenza, pacchetto versionato, import/cancellazione |
| F16 | sincronizzazione opzionale | decisione architetturale motivata prima di qualsiasi backend |

## Politica dei test

- F0 valida dati e assunzioni con fixture riproducibili.
- F1 costruisce l'infrastruttura di test e i gate automatici.
- Da F2 in poi ogni fase aggiunge i propri test e riesegue l'intera suite accumulata come non regressione.
- I provider reali vengono verificati con smoke test controllati; i test deterministici usano fake e fixture versionate.
- I flussi critici Android vengono provati su emulatore gestito con Compose UI/UI Automator; screenshot e artefatti di test restano consultabili.
- Playwright entra solo con una futura superficie browser/WebView. Il 3D usa Blender headless, glTF Validator, render golden e prova su dispositivo.

## Criteri per non espandere troppo il progetto

Una funzione entra nel backlog solo se migliora direttamente: (a) trovare specie lungo un percorso, (b) capire perché sono state mostrate, oppure (c) esplorare la scheda dell'animale. Account, social, AI di riconoscimento e copertura mondiale restano fuori finché l'MVP non è piacevole e affidabile.

Per la sequenza operativa dettagliata vedere [[09 - Piano di sviluppo dettagliato]].
