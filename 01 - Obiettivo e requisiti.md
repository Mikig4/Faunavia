# Obiettivo e requisiti

## Visione

Quando l'utente apre l'app, sceglie partenza, destinazione, date e tracciato del viaggio sulla mappa interna. Nello stesso viaggio consulta gli animali pertinenti e registra i ricordi nel Diario. Può aprire le indicazioni anche in Google Maps, che le ricalcola autonomamente. Esplorazione senza viaggio, posizione corrente e importazione di un itinerario sono ingressi facoltativi. L'app distingue evidenze documentate, plausibilità e suggerimenti personali; schede e 3D arrivano nelle rispettive fasi.

## Requisiti funzionali

### Ingresso

Il viaggio può essere organizzato in tappe libere ordinate, con un giorno e un tracciato scelto per ogni tratto. Località, ordine e date sono modificabili; la vista e l'analisi possono riguardare l'intero viaggio oppure la singola tappa nel suo giorno. Sono ammessi più tratti nello stesso giorno e giorni di sosta. Specifica F8B in [[23 - Tappe e giorni del viaggio]].

1. Apre Viaggi: partenza e destinazione confermate, date e scelta del percorso sulla mappa. Salva l'intero tracciato scelto; nome personalizzato, raggio, interessi e uscite sono facoltativi. I vecchi viaggi senza partenza/traccia rimangono leggibili e modificabili.
2. Offre un collegamento alle indicazioni Google Maps con partenza e destinazione, solo su richiesta. Maps ricalcola le proprie indicazioni e non restituisce automaticamente la traccia scelta; date e percorso salvato restano gestiti da Faunavia. Le uscite mantengono il collegamento al loro luogo.
3. Consente esplorazione senza viaggio e ricerca per nome di paese, regione o città; risolve il nome in un luogo canonico da confermare.
4. Mantiene importazione GPX/GeoJSON e analisi della traccia senza tappe come opzioni; KML può arrivare dopo.
5. L'adapter della posizione corrente rimane un'estensione da completare; le coordinate manuali sono già supportate.

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
6. Suddivide il corridoio in porzioni semplificate e celle di griglia stabili; ogni adapter può interrogare il provider con poligoni quando supportati o con bounding box contenuti come fallback.
7. Classifica una specie come plausibile solo quando esistono insieme un'evidenza di distribuzione e una compatibilità di habitat; la stagionalità modifica la confidenza ma non sostituisce questi due requisiti.

### Risultato

1. Mostra i risultati dentro il viaggio; con un percorso può mostrare anche corridoio e punti di campionamento. L'esplorazione senza viaggio resta accessibile come azione facoltativa.
2. Permette di cercare e selezionare un paese, una regione o una città per nome, con gestione dei risultati ambigui e dei nomi non trovati.
3. Permette di filtrare per gruppo animale, periodo, habitat e livello di evidenza.
4. Apre una scheda specie compatta con nome comune quando disponibile, nome scientifico, habitat e stagionalità. Habitat è cliccabile e apre una mappa generale di distribuzione verificata, distinta dai punti di avvistamento o dal corridoio del viaggio. Descrizione, dimensioni, dieta, comportamento e note di sicurezza/conservazione restano approfondimenti su richiesta; in F13 la sezione **Curiosità** deve essere cliccabile/espandibile e ogni fatto deve avere una fonte verificabile.
5. Mostra un modello 3D locale in formato glTF/GLB, con fallback a immagine o silhouette.
6. Mantiene il link alla fonte e la data di aggiornamento del dato.
7. Nei risultati del catalogo mostra, quando disponibile, una miniatura dell'animale con fonte, autore e licenza prima della conferma.
8. Se la foto non è disponibile o non è riutilizzabile, mostra un placeholder senza impedire la selezione.

### Diario personale degli avvistamenti

1. L'utente può creare un avvistamento indipendentemente dai suggerimenti dell'app.
2. Può scegliere una specie suggerita oppure cercare direttamente dal Diario qualunque specie selezionabile del catalogo generale, senza una selezione preventiva nella schermata Catalogo. Senza rete rimangono disponibili le specie già salvate.
3. Ogni avvistamento può contenere data e ora locali, posizione scelta o corrente, note, numero di esemplari e una o più foto.
4. Le foto restano locali nell'MVP; l'utente può esportare un backup manuale insieme ai dati.
5. Gli avvistamenti dell'utente sono distinti dalle osservazioni importate da GBIF/iNaturalist.
6. La specie è obbligatoria per un avvistamento identificato: deve riferirsi a un taxon animale accettato e stabile. Una ricerca incompleta non viene salvata come avvistamento identificato.
7. Ogni avvistamento può essere collegato a un viaggio e, facoltativamente, a un'uscita; il collegamento non richiede coordinate. Eliminare la pianificazione conserva i ricordi.
8. Le bozze persistenti da identificare conservano appunti e collegamenti separatamente dalle osservazioni identificate e dai conteggi delle specie.

### Notifica di fine giornata

1. L'utente sceglie un orario locale preferito, ad esempio le 20:30, accettando una finestra di esecuzione flessibile gestita da Android.
2. L'app controlla gli avvistamenti inseriti nella giornata secondo il fuso orario del dispositivo.
3. Se esiste almeno un avvistamento, mostra una notifica locale con il numero di registrazioni e apre il riepilogo giornaliero.
4. Se non esistono avvistamenti, non invia una notifica vuota.
5. La funzione deve funzionare senza Firebase e senza connessione.
6. Non richiede un allarme esatto: ritardi ragionevoli dovuti a risparmio energetico, riavvio o pianificazione del sistema sono previsti e verificati.

### Suggerimenti “peculiari del posto”

1. La lista suggerita non è una lista completa degli animali presenti.
2. Ogni area dispone di un elenco curato di specie distintive, interessanti o indicative dell'habitat.
3. Specie molto comuni e sinantropiche nell'area urbana, come il piccione a Milano, vengono escluse o de-prioritizzate tramite regole esplicite.
4. La specie suggerita non limita mai il diario: l'utente può aggiungere qualsiasi altro animale.

## Requisiti non funzionali

- Android-first: il primo prodotto distribuibile è un APK installabile senza obbligo di pubblicazione sul Play Store.
- Local-first: diario, foto, ricerche geografiche già effettuate e schede disponibili devono restare consultabili senza rete.
- Privacy: la posizione non viene inviata a un server personale; eventuali richieste esterne usano solo l'area minima necessaria.
- Prestazioni: la prima schermata deve funzionare su telefono medio senza caricare tutti i modelli 3D insieme.
- Notifiche: il riepilogo serale deve essere locale e legato al fuso orario corrente, con gestione del permesso Android.
- Trasparenza: l'app non deve presentare una presenza storica come avvistamento in tempo reale.
- Costi: nessun servizio a pagamento è indispensabile per il percorso MVP.
- Verificabilità: ogni fase di sviluppo deve produrre test automatizzati; dalla seconda fase in poi deve rieseguire anche l'intera suite di non regressione accumulata.

## Fuori ambito iniziale

- Identificazione automatica da foto o audio.
- Navigazione turn-by-turn e tracking continuo di precisione.
- Social network, account, sincronizzazione multiutente.
- Previsione certa della presenza di una specie.
- Copertura mondiale completa e modellazione 3D di ogni specie esistente.

## Definition of Done dell'MVP

Una persona può aprire l'app, confermare partenza e destinazione, scegliere date e tracciato sulla mappa interna, salvare il viaggio e consultare evidenze e suggerimenti motivati lungo il percorso. Può aprire le indicazioni in Maps e registrare dal catalogo generale un avvistamento collegato al viaggio, anche senza coordinate. Può aprire una scheda e visualizzare almeno un modello 3D locale senza account né costo ricorrente; percorsi importati ed esplorazione senza viaggio restano facoltativi.
