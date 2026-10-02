# Rifinitura F8B: Viaggi, Maps e catalogo nel Diario

> Rapporto storico della prima rifinitura. La successiva precisazione richiede anche partenza e tracciato completo: il solo punto Maps non soddisfaceva il viaggio richiesto. Il comportamento aggiornato e la correzione del Catalogo sono in [[22 - Tracciato viaggio e Catalogo]]. Gli esiti di test qui sotto riguardano la versione 0.8.1.

L'iterazione del 1 ottobre 2026 concentra l'uso ordinario su destinazione e date del viaggio e risolve tre problemi dell'interfaccia F8B. Le fasi F9 e F13 mantengono i requisiti già concordati.

## Comportamento

- L'app apre Viaggi. La navigazione principale contiene Viaggi, Diario, Catalogo e Impostazioni; esplorazione senza viaggio e importazione di percorsi sono strumenti facoltativi con ritorno al viaggio.
- La barra inferiore riserva lo spazio della navigazione Android; la zona di stato mantiene il verde del marchio. Da API 27 la barra di sistema inferiore chiara usa icone scure; su API 26 resta verde con icone chiare. La verifica visiva ha intercettato e corretto una sovrapposizione sui nuovi Android.
- Il viaggio richiede destinazione confermata e date. Nome personalizzato, raggio/interessi e coordinate manuali sono opzioni; se il nome è vuoto si usa la destinazione, con il raggio iniziale esistente di 1 km.
- “Cerca animali per queste date” riutilizza le evidenze F5–F8 senza richiedere un percorso o un'uscita. I suggerimenti personali restano F9. “Aggiungi avvistamento” apre il Diario con viaggio/uscita precompilati, anche senza un risultato scelto o coordinate.
- “Apri in Google Maps” usa il punto della destinazione o dell'uscita confermata. Il collegamento include soltanto coordinate e parametri Maps, parte su richiesta e lascia disponibili i dati locali se manca un gestore. Non importa itinerari/date da Maps e non richiede una chiave API.
- Il Diario cerca nel catalogo generale con debounce, stato di ricerca, errori e retry. Alla selezione conserva taxon accettato, sinonimi e provenienza; il salvataggio dell'avvistamento resta locale. Se la rete manca, le specie già salvate rimangono utilizzabili. Una selezione fallita non crea un avvistamento e conserva i campi per il retry.

## Coerenza dei requisiti

Requisiti, architettura, strategia mappa, Diario, roadmap e piano F8B ora descrivono il medesimo flusso. Il collegamento viaggio/avvistamento è indipendente dalla posizione. Le bozze persistenti da identificare restano distinte dagli avvistamenti identificati e dai conteggi. La limitazione storica F4 alle specie già scelte è superata da questa iterazione. Foto F10, notifiche F11, backup F12, schede F13, 3D F14, preparazione offline F15 e scoperta dei luoghi F17 conservano l'assegnazione precedente.

| Richiesta della discussione | Fase competente e stato |
|---|---|
| Preparare il viaggio con destinazione e date, senza dover importare un percorso | F8B, disponibile nella rifinitura |
| Aprire la destinazione o l'uscita in Maps | F8B, collegamento esterno disponibile; le date si gestiscono in Faunavia |
| Registrare una specie del catalogo direttamente nel Diario | F8B, disponibile online anche per specie mai selezionate; scelte salvate utilizzabili offline |
| Collegare l'avvistamento al viaggio oltre al luogo effettivo | F8B, disponibile anche senza coordinate |
| Ricevere una selezione personale degli animali pertinenti | F9, già pianificata con desideri e viste personali; nessun requisito aggiunto |
| Consultare schede complete delle specie | F13, già pianificata; nessun requisito aggiunto |

Le richieste espresse hanno quindi una fase assegnata. La ricerca attuale offre evidenze documentate/plausibili spiegate; la selezione personale F9 e le schede complete F13 rimangono da realizzare.

## Meccanismo dei risultati e dei futuri suggerimenti

Il viaggio fornisce destinazione, date e raggio (inizialmente 1 km, modificabile nelle opzioni). F5 prepara l'area di ricerca; F6 recupera segnalazioni GBIF/NNB con fonte e data; F7 distingue presenza documentata da plausibilità e insufficienza dei dati. Areale e habitat sono entrambi necessari per dichiarare plausibilità: una specie nel catalogo, da sola, non prova che si possa vedere in quel luogo. Nella ricerca live attuale manca ancora un feed istituzionale di areale/habitat, quindi senza segnalazioni utilizzabili il risultato resta insufficiente.

F9 aggiungerà sopra queste evidenze la selezione già pianificata: animali peculiari, desideri e viste “tipici del luogo”, “più facili da osservare” e “mai osservati da me”, con motivazioni e limiti espliciti. Presenza e facilità di avvistamento restano informazioni diverse, senza probabilità inventate. F13 approfondirà la scheda dell'animale; non sostituisce il lavoro di selezione della F9.

## Verifica

`verifyAll --no-daemon` completato il 1 ottobre 2026: 13 test F0, 73 JVM e 60 Android su Pixel 2 API 36, zero fallimenti, errori o test saltati. Passano build APK, lint, formato, confini architetturali, rilevamento dei test omessi e tutti e tre i golden. Riferimento storico precedente: 13 F0, 71 JVM e 54 Android.

I nuovi casi coprono ingresso Viaggi e strumenti facoltativi, creazione con soli destinazione/date, inserimento diretto collegato al viaggio, ricerca di una specie mai selezionata, sinonimi/provenienza, salvataggio offline dopo selezione, retry di ricerca e selezione fallita, punto Maps corretto e gestore Android mancante. I test usano provider e apertura Maps controllati, senza inviare dati reali a servizi esterni.

Revisione manuale dell'APK su emulatore temporaneo API 36: ingresso Viaggi, nuovo viaggio e editor Diario verificati visivamente. I quattro pulsanti sono sopra la barra Android; destinazione/date sono visibili e le opzioni aggiuntive sono nascoste. Screenshot conservati in `artifacts/ux-preview/viaggi.png`, `nuovo-viaggio.png` e `diario.png`. Il dispositivo temporaneo è stato chiuso; nessuna prova su dispositivo fisico o nuovo smoke dei provider live.

Nessuna modifica a Room v6 o alle dipendenze; APK disponibile in `artifacts/Faunavia-f8b-ux-debug.apk`, versione `0.8.1-f8b` (versionCode 10). Le conversioni della data nel form e nell'analisi usano API disponibili da Android 8; le risorse delle icone scure sono limitate ad API 27+. Le modifiche restano nel working tree e richiedono commit/push per essere condivise.
