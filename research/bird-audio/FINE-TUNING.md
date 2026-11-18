# Fine-tuning di BirdNET+ 12K e verifica su telefoni

Il fine-tuning non ha recuperato alcuna seconda specie che BirdNET mancava alla soglia operativa. Sui 41 file di installazioni nuove produce due suggerimenti senza riscontro in più. Per il riconoscimento ordinario manterremmo BirdNET originale.

| Pannello | Seconda specie, base → fine-tuning | Entrambe, base → fine-tuning | Falsi suggerimenti, base → fine-tuning |
|---|---:|---:|---:|
| 20 minuti LG di calibrazione | 10 → 10 | 8 → 8 | 150 → 150 |
| 48 minuti LG del benchmark precedente | 32 → 32 | 24 → 25 | 263 → 267 |
| 41 minuti LG di installazioni nuove | 22 → 22 | 17 → 17 | 235 → 237 |
| 39 registrazioni con sovrapposizione annotata | 30 → 30 | 26 → 26 | Etichette parziali |

Le soglie sono 0,25 per l'originale e 0,25094664 per il modello adattato. Anche a 0,25 per entrambi, il recupero della seconda specie sul nuovo pannello rimane 22/41, con 238 suggerimenti senza riscontro per il modello adattato. Contando solo il rango, la seconda specie è tra le prime tre in 28/41 casi per entrambi e in 34/39 sul pannello con sovrapposizione. Nei due casi italiani con sovrapposizione entrambi riconoscono sopra soglia la coppia capinera–colombaccio, mentre nessuno propone entrambe le specie della coppia prispolone–merlo. [Risultati per soglia e pannello](finetune-results.json) · [Predizioni appaiate](finetune-predictions.jsonl)

Abbiamo acquisito 876 elementi, 791 da iNaturalist e 85 da Xeno-canto. Dopo aver riunito i duplicati della stessa specie, il training comprende tutti gli **859 audio distinti**, con **863 associazioni audio–specie e 335 specie**. Per 13 MP3 il decoder ha saltato pacchetti illeggibili conservando il segnale valido. Ben 179 specie dispongono di un solo esempio. La seconda specie sul nuovo pannello aveva esempi di training in 16 casi su 41. Questo limita le occasioni per imparare i versi bersaglio, in particolare quando sono coperti da altri uccelli. Il campione non permette di escludere guadagni con un training più ricco di cori annotati. Il file ONNX finale, eseguito direttamente su audio di dieci telefoni, riproduce i punteggi calcolati dagli aggiornamenti di peso con scarto massimo inferiore a 0,000001.

## Quali dati potevano essere nuovi per BirdNET?

Abbiamo fissato il modello a BirdNET+ V3.0 Global 12K, pubblicato il 13 novembre 2026. Gli autori riportano la raccolta da Xeno-canto fino al 6 gennaio 2026, da iNaturalist fino al 12 gennaio e da Macaulay Library fino al 17 marzo. Citano anche materiale privato dei collaboratori. Non abbiamo trovato un inventario pubblico di tutti i file di preaddestramento. La data di pubblicazione di un *dataset* da sola non dimostra che gli audio sottostanti fossero inediti. Per la prova principale abbiamo cercato registrazioni *effettuate* dopo l'uscita dei pesi e abbiamo assunto che le date dichiarate dagli autori si riferiscano effettivamente al suono pubblicato. [Scheda del modello e checkpoint ufficiali](https://zenodo.org/records/24162518)

Il perimetro riproducibile è la fotografia del 18 novembre di due cataloghi pubblici. Da iNaturalist abbiamo selezionato osservazioni di uccelli di grado ricerca con audio, da Xeno-canto registrazioni con una specie focale identificata. Abbiamo richiesto una licenza Creative Commons compatibile con la prova, una specie associabile al vocabolario del modello e una data dal 14 al 18 novembre inclusi. Per il 14 novembre abbiamo richiesto un istante successivo alla mezzanotte UTC del 14, oppure su Xeno-canto un'ora locale dalle 14 in poi che renda conservativa la conversione di fuso. Abbiamo escluso gli identificativi e i nomi autore corrispondenti ai file Xeno-canto riservati ai telefoni. I dati di taratura e test sono esclusi dal training. Le etichette sono a livello di registrazione: eventuali altri uccelli sullo sfondo restano sconosciuti. Per iNaturalist, in particolare, la conferma della specie riguarda l'osservazione e può tenere conto anche di altre prove fornite dall'autore. [iNaturalist](https://www.inaturalist.org/observations) · [Xeno-canto](https://xeno-canto.org/explore) · [Fonti individuali, licenze, impronte e stato](finetune-sources.json)

Il corpus usato è circoscritto a questi due cataloghi e criteri. L'insieme mondiale dei dati mai visti dal modello resta sconosciuto. CEB, WABAD e le vecchie registrazioni Xeno-canto rimangono fuori dal training perché la loro indipendenza dal modello di base non è dimostrata. Anche la novità dei telefoni storici rispetto al preaddestramento originale resta sconosciuta. Entrambi i modelli partono dagli stessi pesi e ascoltano gli stessi file.

## Addestramento

Partiamo dall'ONNX FP32 ufficiale, verificato tramite SHA-256. Aggiorniamo direttamente righe e intercette dello strato nativo `head`, che riceve 1.280 caratteristiche acustiche. Secondo il checkpoint questa testa pesa 0,40 nel punteggio prima della sigmoide. Le altre due teste e l'encoder sono congelati. La prova verifica quindi un fine-tuning parziale, eseguibile su CPU. Il file risultante accetta direttamente l'audio mono a 32 kHz come il modello originario.

Per ogni audio decodificato valutiamo fino a tre finestre equidistanti di cinque secondi, con padding per gli audio più brevi. Per l'aggiornamento scegliamo quella cui il modello di base assegna il punteggio massimo per la specie focale. Ciò consente di usare etichette prive di tempi, ma può privilegiare i versi già facili per BirdNET. Deduplichiamo sul segnale PCM a 32 kHz e sulla specie. Conserviamo due specie annotate sullo stesso audio come due esempi supervisionati. La funzione obiettivo premia la specie dichiarata e penalizza lo scostamento dai punteggi originari, evitando di trattare tutte le altre specie come assenti. Usiamo otto epoche, batch 48, AdamW a 0,0004, con seme 14026. Numero di epoche, peso della penalità e regola per i ritagli sono fissati prima della valutazione. [Implementazione](finetune_birdnet.py)

## Protocollo telefonico

- Calibriamo la soglia esclusivamente sui 20 file LG già designati, per avvicinare il numero di suggerimenti senza riscontro di BirdNET a 0,25. Applichiamo la soglia ottenuta agli altri pannelli. Conserviamo anche il confronto con entrambi a 0,25.
- Sui 48 file LG di confronto ripetiamo il protocollo precedente. Per una verifica indipendente da quegli esperimenti prendiamo tutte le ulteriori registrazioni LG della stessa raccolta con una coppia di inizi di vocalizzazione nello stesso secondo, deployment assente da tutti i precedenti pannelli LG e codici identificabili. Un file con un codice privo di corrispondenza verificata resta escluso. Otteniamo 41 file da 34 ulteriori installazioni. Alcuni telefoni fisici sono riutilizzati. Questi campioni non sono stati impiegati per decidere né pesi né soglie. [Nuove registrazioni, impronte ed etichette](finetune-new-phones.json) · [Audio LG di Sonoma](https://zenodo.org/records/6512218) · [Annotazioni degli autori](https://github.com/pointblue/Soundscapes2Landscapes/blob/499e06bbb7e6a669b891325ccf48f3a3c2321348/CNN_Bird_Species/CNN_post-processing/data/gvData.RData)
- Per i pannelli LG contiamo come secondaria la specie meno ricorrente nella registrazione fra quelle con un inizio annotato nello stesso secondo selezionato prima dell'inferenza. Misuriamo il recupero nella relativa finestra. Per i falsi suggerimenti valutiamo tutte le 12 finestre consecutive da cinque secondi per minuto. Ogni specie aviare segnalata senza inizio annotato nella finestra conta una volta. Una nota iniziata nella finestra precedente o un'etichetta mancante possono far contare come errore anche una presenza reale. Gli LG sono telefoni fissi con microfono esterno.
- Ripetiamo anche il test sui 39 audio con intersezione temporale annotata, inclusi due italiani, e sugli altri controlli telefonici storici. L'identità della seconda specie viene dall'annotazione. Su questi pannelli le specie etichettate sono parziali, quindi evitiamo di contare ogni altra specie come falso positivo. [Finestre telefoniche storiche](TELEFONI.md) · [Protocollo LG precedente](ESTRATTORE-TEMPORALE.md)

## Riproduzione

Nella cartella `research/bird-audio` servono Python, NumPy, SciPy, PyTorch, ONNX, ONNX Runtime, SoundFile, PyAV e le dipendenze del benchmark. La raccolta usa requests e per una nuova ricerca Xeno-canto anche Beautiful Soup. `MODELLI` contiene `bn3.onnx` e `bn3.csv` del manifesto originale. `TELEFONI` contiene i file originali dei pannelli Sonoma e i due file iPhone aggiuntivi. `STORICI` indica la directory della precedente prova XC/LG. I manifesti contengono gli identificativi per recuperare i file da ciascuna fonte e verificarne le impronte.

```text
python finetune_sources.py MODELLI LAVORO --manifest finetune-sources.json
python finetune_birdnet.py phones MODELLI LAVORO TELEFONI STORICI
python finetune_birdnet.py new-phones MODELLI LAVORO TELEFONI STORICI
python finetune_birdnet.py fit MODELLI LAVORO TELEFONI STORICI
```

Lo script salva `birdnet-finetuned.onnx`, utilizzabile con gli stessi nomi di input, output e classi del modello globale, e `head-delta.npz` con i soli aggiornamenti. Il checkpoint, gli audio e la cache rimangono nel workspace locale. Nel repository sono condivisi codice, fonti, finestre, predizioni e risultati. L'inferenza si esegue localmente e la raccolta scarica solo i dati pubblici.
