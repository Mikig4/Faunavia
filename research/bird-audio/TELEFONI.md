# BirdNET su registrazioni annotate da telefono

Prova del 18 novembre 2026 con BirdNET+ 3.0 FP32 globale. Per ogni confronto si usa la stessa finestra mono a 32 kHz, prima con il suono pubblicato dall'autore e poi con il [fondo attenuato](SOLO-VERSO.md). Nessun disturbo aggiunto e nessun filtro geografico o stagionale. Alcuni autori avevano già normalizzato o filtrato il file pubblicato. La misura riguarda il beneficio aggiuntivo del nostro filtro su questo materiale.

## Risultati

Il campione principale copre 18 paesi, 33 autori e 44 specie distinte nelle coppie. In questa selezione due registrazioni sono italiane. Il filtro ha guadagnato posizioni per la seconda specie in 3 registrazioni, le ha perse in 5 e ha mantenuto lo stesso rango in 31. In nessuna registrazione ha portato la seconda specie da fuori a dentro i primi tre suggerimenti. I due passaggi in senso opposto, in Italia e Svizzera, riguardano entrambi il merlo al confine tra terza e quarta posizione.

| Esito nei primi tre | BirdNET originale | Fondo attenuato | Adattatore WABAD su originale |
|---|---:|---:|---:|
| Seconda specie, 39 registrazioni | 34/39 (87,2%) | 32/39 (82,1%) | 34/39 (87,2%) |
| Entrambe le specie nella stessa finestra | 29/39 (74,4%) | 27/39 (69,2%) | 29/39 (74,4%) |
| Entrambe, solo le 31 registrazioni con annotazioni CEB | 26/31 | 24/31 | 26/31 |
| Entrambe, altre 8 con annotazioni XC | 3/8 | 3/8 | 3/8 |

La differenza per la seconda specie è di −5,1 punti percentuali (intervallo bootstrap appaiato per autore al 95% da −13,5 a 0). I casi discordanti sono solo due: il test esatto bilaterale di McNemar dà p = 0,50. Il campione non mostra un vantaggio del filtro e la dimensione dell'effetto rimane incerta. Le annotazioni e il campionamento mirato non consentono una stima dei falsi allarmi per minuto nell'uso libero.

| Registrazione italiana | Intersezione annotata | Rango della specie principale, originale → filtro | Rango della seconda specie, originale → filtro |
|---|---:|---|---|
| [XC785326](https://xeno-canto.org/785326), Android con RecForge2 e microfono integrato | 1,06 s | Capinera, 2 → 2 | Colombaccio, 1 → 1 |
| [XC807426](https://xeno-canto.org/807426), Redmi Note 11 Pro+, microfono non specificato | 0,76 s | Prispolone, 1 → 1 | Merlo, 3 → 4 |

Negli altri tre brevi file italiani, con co-presenza annotata ma simultaneità non verificata, i conteggi restano 2/3 per la specie sullo sfondo e 1/3 per entrambe. Nel controllo californiano con microfono esterno, entrambe rientrano nei primi tre in 2/3 con e senza filtro; anche il caso separato sotto i 100 ms mantiene entrambe le specie (1/1). Questi controlli restano fuori dalle 39 registrazioni principali.

## Valutazione dell'allenamento

Per Faunavia terrei l'audio originale come impostazione standard e investirei in un adattamento multi-specie su telefoni rappresentativi dell'uso previsto. Il nostro adattatore congelato WABAD aveva già mostrato un guadagno su nuove giornate dei siti in cui era stato allenato. Qui conserva esattamente gli esiti top-3 del modello di base. Aveva imparato una correzione per la specie secondaria in appena 8 delle 39 registrazioni e per entrambi i bersagli in una sola. Questo risultato limita l'uso diretto di quei pesi su nuovi ambienti. [Confronto e addestramento WABAD](ADATTAMENTO.md)

Il primo esperimento fattibile su CPU è allenare un piccolo classificatore multi-etichetta sugli embedding congelati di BirdNET, mantenendo il modello globale per le altre specie. Raccoglierei cori naturali con annotazioni temporali di tutti gli uccelli udibili e intervalli di solo rumore; userei miscele a livelli relativi noti per aumentare il training. Per scegliere modello e soglie separerei addestramento, validazione e test per sito, sessione e dispositivo, mantenendo un test italiano con modelli di telefono e località mai visti. La misura decisionale sarebbe quante seconde specie e coppie recuperiamo a parità di falsi suggerimenti al minuto. Il test va ampliato prima di usarlo per scegliere pesi o parametri. Solo dopo un guadagno ripetibile su quel test prenderei in considerazione un nuovo esperimento con strati interni addestrabili e un altro test intatto. Un modello addestrato da zero richiederebbe copertura di specie e ambienti molto maggiore.

Un precedente italiano supporta la fattibilità di un adattamento locale. Schiavo e colleghi hanno studiato 16 specie alpine con registratori AudioMoth e materiale Xeno-canto. Il micro-F1 passa da 0,278 a 0,647 con adattamento e aumento dei dati, mentre la precisione passa da 0,796 a 0,641. Il recupero della seconda specie sui telefoni è una misura diversa e va verificato direttamente. [Studio sulla foresta della Val Tovanella, metodi e tabella 1](https://www.mdpi.com/2078-2489/16/8/628)

## Come abbiamo identificato il secondo uccello

Il campione principale comprende 39 registrazioni Xeno-canto. Per ciascuna abbiamo verificato un telefono dichiarato nel campo dispositivo o nelle note dell'autore, una specie principale indicata dal recordista e un intervallo annotato in cui vocalizza contemporaneamente un'altra specie per almeno 100 ms. Fra le coppie idonee abbiamo scelto, prima dell'inferenza, la sovrapposizione più lunga con la specie principale, a parità quella più precoce. La seconda specie è quindi una specie ulteriore nello stesso istante. Il suo livello sonoro individuale rimane sconosciuto. La coppia può condividere la finestra con altre specie. I risultati riguardano vocalizzazioni sia di canto sia di richiamo.

Per 31 registrazioni le annotazioni temporali provengono dalla raccolta CEB. Abbiamo usato i FLAC effettivamente annotati, contenuti nell'archivio `train_xenocanto.tar.gz`. Per altre otto abbiamo usato l'audio Xeno-canto e i segmenti attribuiti a un annotatore o al creatore del set nel catalogo Naturalis. Sono state escluse indicazioni ambigue sul dispositivo, co-presenze prive di tempi e annotazioni in cui l'identità della seconda specie era incerta. I microfoni dichiarati sono riportati nel manifesto: alcuni telefoni montavano un microfono esterno, per altri il campo è vuoto. [CEB e licenze per registrazione](https://zenodo.org/records/21821133) · [Xeno-canto/GBIF, uccelli](https://www.gbif.org/dataset/b1047888-ae52-4179-9dd5-5448ea342a24) · [Manifesto, tempi, dispositivi e fonti individuali](phone-xc.json)

La finestra di cinque secondi è centrata sull'intersezione e limitata alla durata del file. Per i file più brevi si attenua il fondo sui soli campioni reali e si aggiungono zeri in coda a entrambe le versioni fino a cinque secondi. Le specie vengono cercate nei primi tre suggerimenti tra tutte le classi native. Il rango completo è conservato per ciascuno dei due bersagli. Una specie focale, *Upupa africana*, manca come etichetta esatta dalle classi del modello; rimane nel denominatore. I dati disponibili non consentono di escludere sovrapposizioni con il preaddestramento di BirdNET.

Due controlli sono riportati separatamente. Tre registrazioni italiane da telefono durano al massimo cinque secondi e gli autori citano una sola specie di sfondo oltre alla principale. Questo permette di verificare la stessa finestra ma lascia aperta la simultaneità. Nella contea di Sonoma tre registrazioni da telefoni LG fissi con microfono esterno hanno riquadri convalidati che si sovrappongono per almeno 100 ms. Un quarto caso con 57 ms rimane disponibile come controllo di sensibilità. Le due specie a Sonoma non hanno ruoli di primo piano e sfondo assegnati. [Fonti e verifica Sonoma](FONTI-TELEFONO.md) · [Manifesto LG](phone-lg.json)

## Riproduzione

Usare l'ambiente e il modello del [benchmark](README.md). Estrarre nella cartella audio i membri CEB indicati in [phone-xc.json](phone-xc.json); l'MD5 dell'archivio CEB pubblicato dagli autori è `15bea4f2496241a08a49aa392203b6c4`. Per le righe XC e Italy-clip, scaricare `source_url` con il nome dell'identificativo XC, senza estensione. Per gli LG salvare i WAV originali sotto `lg/source` come indicato nel [secondo manifesto](phone-lg.json). Ogni file viene verificato con SHA-256 prima dell'inferenza.

```sh
python phone.py phone-predictions.jsonl --audio CARTELLA_AUDIO --model CARTELLA_BIRDNET --head CACHE/head/wab-residual.npz
python phone.py phone-predictions.jsonl
```

L'opzione `--head` aggiunge al confronto il classificatore già allenato con disturbi su WABAD. Usa l'embedding dell'audio originale e gli stessi 71 correttivi di classe dell'[esperimento precedente](ADATTAMENTO.md), senza alcun aggiornamento su questi telefoni. Per il solo confronto fra originale e filtro si può omettere l'opzione. Il secondo comando rigenera le metriche dalle [predizioni archiviate](phone-predictions.jsonl). Gli intervalli esplorativi al 95% sulla differenza usano 10.000 ricampionamenti appaiati per autore. La selezione di convenienza dei siti e la rappresentatività dei telefoni italiani restano limiti distinti dall'incertezza di campionamento.

## Verifiche

1. UI e provider — prova offline fuori dall'app.
2. Provenienza — manifesti per singolo file, licenze, date, evidenza del dispositivo, tempi annotati e impronte dei file di inferenza.
3. Rete — inferenza e riepilogo riproducibili dopo aver scaricato le fonti pubbliche.
4. Osservazioni e suggerimenti — bersagli annotati e graduatorie dei modelli in campi distinti.
5. Asset dell'app — nel repository sono condivisi codice, riferimenti, risultati numerici e rapporti.
6. Ranking e filtro — controllo di identità di file, finestre e coppie; rigenerazione dei conteggi e confronto numerico del filtro con la sua versione precedente.
