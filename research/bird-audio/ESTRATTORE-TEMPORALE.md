# Estrattore temporale su nuovi telefoni

L'estrattore ha recuperato una seconda specie in più tenendo la soglia BirdNET a 0,25, al prezzo di 40 falsi suggerimenti aggiuntivi in 48 minuti. Quando abbiamo fissato la soglia su altri telefoni per pareggiare i falsi suggerimenti, il vantaggio è scomparso. Terremmo l'audio originale per il riconoscimento in Faunavia.

## Risultato su 48 registrazioni tenute da parte

| Misura | BirdNET originale | Originale + tracce, soglia calibrata | Originale + tracce, stessa soglia |
|---|---:|---:|---:|
| Soglia | 0,25 | 0,311949 circa | 0,25 |
| Seconda specie sopra soglia | 32/48 | 31/48 | 33/48 |
| Entrambe le specie sopra soglia | 24/48 | 24/48 | 26/48 |
| Suggerimenti corretti su tutte le finestre | 833 | 811 | 851 |
| Suggerimenti senza riscontro nella finestra | 263 | 267 | 303 |
| Suggerimenti senza riscontro al minuto | 5,48 | 5,56 | 6,31 |
| Di questi, specie senza riscontro nell'intero minuto, conteggio | 168 | 177 | 203 |

A soglie calibrate 31 seconde specie sono riconosciute da entrambi i sistemi, 16 da nessuno e una solo da BirdNET. In quest'ultimo file il punteggio di *Haemorhous mexicanus* resta 0,256 anche dopo l'estrazione e cade sotto la soglia calibrata. Guardando solo al rango, la seconda specie è nelle prime tre classi aviarie in 36/48 casi sull'originale e 37/48 dopo l'estrazione. Questo criterio accetta anche suggerimenti di punteggio molto basso. [Conteggi e soglie esatte](temporal-predictions.summary.json) · [Predizioni per finestra](temporal-predictions.jsonl)

## Audio e definizioni

- **Addestramento.** Abbiamo riusato il manifesto CEB-XC del progetto. Sono 402 registrazioni di 54 specie per addestrare la rete e 54 file diversi per controllare la ricostruzione. I 760 ritagli di addestramento e i 97 di controllo provengono da vocalizzazioni con annotazioni temporali. Possono includere ambiente o suoni di sfondo. Gli autori dei due gruppi CEB possono coincidere. [Provenienza, autori e licenze per file](target-references.json)
- **Calibrazione e test.** Dal progetto Soundscapes to Landscapes abbiamo selezionato file acquisiti da telefoni LG Android con microfono esterno, escludendo tutte le registrazioni già usate nel test telefonico Faunavia. Abbiamo richiesto almeno un secondo con due distinti inizi di vocalizzazione annotati. Dal gruppo dei quattro telefoni 001/004/006/007 abbiamo preso 20 file nuovi per la calibrazione. Dagli altri sei abbiamo preso 48 file per il test. In ogni gruppo l'ordine di selezione è lo SHA-256 di `temporal-sonoma-v1/` seguito dal nome del file. Il test comprende 40 combinazioni dispositivo/deployment. La selezione favorisce registrazioni con canto e limita la generalizzazione a un telefono tenuto in mano o a lunghi periodi di silenzio. [WAV pubblici di Clark e colleghi, CC BY 4.0](https://zenodo.org/records/6512218) · [Metodo di acquisizione e annotazione](https://doi.org/10.1016/j.ecoinf.2023.102065)
- **Seconda specie.** Per ciascun file si sceglie prima dell'inferenza il secondo con l'hash più basso di `pair/{file}/{secondo}` tra quelli con almeno due specie. Fra queste il bersaglio è la specie annotata nel minor numero di secondi dell'intero minuto, poi si prende la successiva come compagna. Le parità sono risolte per codice alfabetico. Il test copre 23 specie bersaglio. La frequenza di annotazione dà una regola riproducibile per scegliere la seconda specie. Il volume relativo rimane sconosciuto. Gli inizi risolti al secondo permettono di misurare la co-occorrenza senza stabilire la sovrapposizione esatta delle note. [Annotazioni degli esperti](https://github.com/pointblue/Soundscapes2Landscapes/blob/499e06bbb7e6a669b891325ccf48f3a3c2321348/CNN_Bird_Species/CNN_post-processing/data/gvData.RData) · [Finestre, dispositivi, bersagli e SHA-256](temporal-phones.json)
- **Falsi suggerimenti.** Valutiamo tutte le classi aviarie sopra soglia in ciascuna delle dodici finestre consecutive di 5 s, per l'intero primo minuto di ciascun file. Una specie conta al massimo una volta per finestra. Contiamo come errore un suggerimento senza inizio annotato in quella finestra. Se un canto iniziato prima prosegue o manca un'annotazione, anche una presenza reale può entrare nel conteggio. Per questo riportiamo anche i suggerimenti la cui specie manca nell'intero minuto. Nelle 32 finestre di test senza inizi annotati abbiamo contato rispettivamente 11 e 13 suggerimenti sopra le soglie calibrate.
- **Soglia.** BirdNET sull'originale usa 0,25. Per l'estrattore scegliamo sulla calibrazione la più bassa soglia almeno pari a 0,25 che rispetta il budget di errori di BirdNET. Entrambi producono 150 falsi suggerimenti su 240 finestre di calibrazione. I 576 intervalli di test sono stati elaborati dopo aver fissato la soglia. Non abbiamo regolato il modello sul test. Il preaddestramento del classificatore BirdNET è comune ai due rami. Non abbiamo verificato l'assenza dei singoli file dal suo corpus originario.

## Che cosa ha imparato il modello

La rete produce due tracce mono senza ricevere l'identità delle specie. Su uno spettrogramma a 32 kHz usa cinque convoluzioni temporali con dilatazioni da 1 a 16 per stimare due maschere che si sommano a uno. Il campo locale delle convoluzioni è di 63 fotogrammi, circa mezzo secondo, e la normalizzazione usa anche statistiche dell'intero segmento. Applichiamo le maschere allo spettro complesso del segnale. BirdNET+ 3.0 valuta l'originale e ciascuna delle due tracce. Per ogni specie conserviamo il punteggio massimo sui tre ascolti. Questo consente di proporre una specie anche quando era lontana dai primi risultati dell'originale.

Abbiamo addestrato i 214.306 parametri su CPU con 800 aggiornamenti AdamW da dieci miscele sintetiche di due specie. Per ogni esempio variamo il ritaglio, il tempo relativo e il livello della seconda sorgente, da 0 a −12 dB. La funzione di errore confronta le magnitudini compresse e sceglie l'assegnazione delle due uscite alle sorgenti con errore più basso. Su 64 miscele fisse costruite con i file riservati, l'errore passa da 0,925 a 0,382. Abbiamo usato il checkpoint finale prestabilito di 800 aggiornamenti. L'addestramento vede coppie di specie, mentre una scena reale può contenere più uccelli, vento e altre sorgenti. [Curva di apprendimento e impronta dei pesi](temporal-training.json)

## Controllo su due iPhone

Abbiamo verificato altri due file Xeno-canto con dispositivo esplicito, annotatore identificabile e intervalli di specie diverse che si intersecano. Le soglie sono quelle già fissate in California. Nel file olandese BirdNET riconosce sia picchio rosso maggiore sia luì piccolo sull'originale e sulle tracce combinate. Il punteggio del luì sale da 0,706 a 0,773. Nel file cileno nessuno dei due metodi supera la soglia per le due specie bersaglio, *Mimus thenca* e *Zenaida auriculata*. Qui le annotazioni della coppia hanno gli stessi limiti di tre secondi. Il risultato resta separato dalle 48 registrazioni LG e non stima l'accuratezza degli iPhone. [Fonti, licenze, eventi e attribuzioni](temporal-xc.json) · [Risultati sui due iPhone](temporal-xc-results.json)

## Riproduzione

Dalla cartella `research/bird-audio` usare l'ambiente ONNX del benchmark con NumPy, SciPy, SoundFile e PyTorch CPU. `AUDIO_CEB` contiene `ceb-xc.csv` e `verified-ceb.tar.gz` del precedente esperimento. `TELEFONI` contiene i WAV di Sonoma con i nomi del manifesto. Per il controllo aggiuntivo salvare nella stessa directory i due originali di Xeno-canto con i nomi `XC1102885` e `XC1177210`. `MODELLI` contiene `bn3.onnx` e `bn3.csv`. Lo SHA-256 del classificatore è `e78c3b50e224cd79d549d3c3b5efde2436acd1d3fbd72694f2a0b8547fa18685`.

```text
python temporal_extract.py train AUDIO_CEB LAVORO
python temporal_phones.py TELEFONI LAVORO/temporal.pt MODELLI temporal-predictions.jsonl
python temporal_phones.py TELEFONI LAVORO/temporal.pt MODELLI temporal-xc-results.json --xc
python temporal_extract.py extract LAVORO/temporal.pt registrazione.wav TRACCE --start 5
```

L'ultimo comando scrive due WAV per un intervallo fino a cinque secondi. La numerazione delle sorgenti può cambiare tra finestre. I pesi allenati e gli audio restano nel workspace locale. Nel repository pubblichiamo codice, manifesti, identificativi dei pesi e risultati per finestra. L'esperimento gira fuori dall'app e l'inferenza usa solo file locali.
