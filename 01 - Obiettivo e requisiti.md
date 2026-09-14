# Obiettivo e requisiti

## Visione

Quando l'utente apre l'app, può usare la posizione corrente oppure importare un itinerario. L'app interpreta l'area attraversata e restituisce una lista ragionata di specie animali, con evidenze geografiche e temporali, habitat, stagionalità, peculiarità e modello 3D.

## Requisiti funzionali

### Ingresso

1. Usa la posizione corrente tramite geolocalizzazione del dispositivo.
2. Importa almeno GPX e GeoJSON; KML può arrivare dopo.
3. Accetta una ricerca manuale di un luogo quando la posizione non è disponibile.
4. Gestisce un percorso senza tappe: la geometria della traccia è sufficiente.

### Catalogo animali e selezione

1. L'utente scrive un nome comune o scientifico e riceve una lista di taxa animali selezionabili.
2. La ricerca comprende sinonimi e varianti ortografiche, ma salva sempre il taxon accettato e il suo identificativo stabile.
3. Il filtro principale è `kingdom = Animalia`; il selettore privilegia specie e sottospecie esistenti, non famiglie o generi astratti.
4. Un animale può essere selezionato anche se non appartiene ai suggerimenti peculiari del luogo.
5. Il catalogo può essere interrogato online e mantenere una cache locale degli animali già selezionati.
6. “Elenco completo” significa completo rispetto alla base tassonomica adottata e alla sua data di aggiornamento, non una promessa assoluta su ogni specie descritta dalla scienza.

### Analisi del percorso

1. Normalizza punti e coordinate nel sistema WGS84.
2. Campiona la traccia a intervalli configurabili, evitando di interrogare i provider per ogni singolo punto.
3. Costruisce un corridoio attorno alla traccia; il raggio va scelto in base al tipo di ambiente e all'uso dell'app.
4. Aggrega osservazioni duplicate e calcola un punteggio composto da distanza, recenza, qualità della coordinata, stagione e affidabilità della fonte.
5. Distingue almeno: documentato nell'area, plausibile nell'habitat, non sufficiente per concludere.

### Risultato

1. Mostra mappa, corridoio analizzato, punti di campionamento e specie associate.
2. Permette di filtrare per gruppo animale, periodo, habitat e livello di evidenza.
3. Apre una scheda specie con nomi comuni e scientifici, descrizione, dimensioni, dieta, comportamento, habitat, periodo di attività, distribuzione e note di sicurezza/conservazione.
4. Mostra un modello 3D locale in formato glTF/GLB, con fallback a immagine o silhouette.
5. Mantiene il link alla fonte e la data di aggiornamento del dato.
6. Nei risultati del catalogo mostra, quando disponibile, una miniatura dell'animale con fonte, autore e licenza prima della conferma.
7. Se la foto non è disponibile o non è riutilizzabile, mostra un placeholder senza impedire la selezione.

### Diario personale degli avvistamenti

1. L'utente può creare un avvistamento indipendentemente dai suggerimenti dell'app.
2. Può scegliere una specie suggerita oppure cercare qualunque specie esistente nel catalogo generale.
3. Ogni avvistamento può contenere data e ora locali, posizione scelta o corrente, note, numero di esemplari e una o più foto.
4. Le foto restano locali nell'MVP; l'utente può esportare un backup manuale insieme ai dati.
5. Gli avvistamenti dell'utente sono distinti dalle osservazioni importate da GBIF/iNaturalist.

### Notifica di fine giornata

1. L'utente sceglie un orario locale, ad esempio le 20:30.
2. L'app controlla gli avvistamenti inseriti nella giornata secondo il fuso orario del dispositivo.
3. Se esiste almeno un avvistamento, mostra una notifica locale con il numero di registrazioni e apre il riepilogo giornaliero.
4. Se non esistono avvistamenti, non invia una notifica vuota.
5. La funzione deve funzionare senza Firebase e senza connessione.

### Suggerimenti “peculiari del posto”

1. La lista suggerita non è una lista completa degli animali presenti.
2. Ogni area dispone di un elenco curato di specie distintive, interessanti o indicative dell'habitat.
3. Specie molto comuni e sinantropiche nell'area urbana, come il piccione a Milano, vengono escluse o de-prioritizzate tramite regole esplicite.
4. La specie suggerita non limita mai il diario: l'utente può aggiungere qualsiasi altro animale.

## Requisiti non funzionali

- Android-first: il primo prodotto distribuibile è un APK installabile senza obbligo di pubblicazione sul Play Store.
- Local-first: diario, foto, ricerche già effettuate e schede disponibili devono restare consultabili senza rete.
- Privacy: la posizione non viene inviata a un server personale; eventuali richieste esterne usano solo l'area minima necessaria.
- Prestazioni: la prima schermata deve funzionare su telefono medio senza caricare tutti i modelli 3D insieme.
- Notifiche: il riepilogo serale deve essere locale e legato al fuso orario corrente, con gestione del permesso Android.
- Trasparenza: l'app non deve presentare una presenza storica come avvistamento in tempo reale.
- Costi: nessun servizio a pagamento è indispensabile per il percorso MVP.

## Fuori ambito iniziale

- Identificazione automatica da foto o audio.
- Navigazione turn-by-turn e tracking continuo di precisione.
- Social network, account, sincronizzazione multiutente.
- Previsione certa della presenza di una specie.
- Copertura mondiale completa e modellazione 3D di ogni specie esistente.

## Definition of Done dell'MVP

Una persona può aprire l'app, consentire la posizione oppure importare un GPX, vedere il corridoio sulla mappa, ottenere almeno una lista di specie da una fonte documentata, aprire una scheda e visualizzare almeno un modello 3D locale senza account né costo ricorrente.
