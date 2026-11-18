# Estrarre il canto di una specie da un solo microfono

Il prototipo produce una traccia WAV per ogni specie candidata. Sulle miscele costruite in laboratorio migliora la fedeltà mediana del verso più debole. Sulle 39 registrazioni da telefono con canti sovrapposti, usare la traccia per confermare la specie riduce i riconoscimenti corretti. Per Faunavia manterremmo BirdNET sull'originale come prima analisi.

## Come funziona

1. Dai dati CEB-XC scegliamo registrazioni le cui annotazioni valide appartengono tutte alla specie focale. Escludiamo gli ID Xeno-canto e i nomi degli autori dell'intero pannello telefonico. Per ciascuna specie riserviamo un file per le miscele e usiamo fino a otto altri file per impararne i profili. Il banco contiene 54 specie e 402 file di riferimento. Il manifesto riporta ID, autore, licenza e ruolo di ognuno dei 456 file.
2. Per ogni riferimento usiamo fino a quattro vocalizzazioni annotate, limitate alle frequenze della rispettiva annotazione. Su audio mono a 32 kHz calcoliamo spettri con finestra Hann di 1.024 campioni e passo di 256. Con MiniBatchKMeans ricaviamo fino a 12 profili per specie.
3. Sul telefono BirdNET+ 3.0 analizza cinque secondi di originale. Per le specie tra i primi 12 risultati di cui abbiamo riferimenti, stimiamo congiuntamente quanto ogni profilo contribuisce a ogni istante, con 50 iterazioni di un adattamento non negativo. Un dizionario supplementare rappresenta il fondo. Una maschera basata sulla potenza stimata di ciascuna sorgente restituisce una traccia audio con la fase della registrazione. L'annotazione serve per scegliere la finestra di verifica e valutare il risultato. I candidati vengono dall'audio.
4. BirdNET riascolta ciascuna traccia. Valutiamo due regole per ogni candidata coperta. La prima calcola la media geometrica dei punteggi della specie su originale e traccia. La seconda prende il maggiore dei due. Le altre classi conservano il punteggio originale. La copertura parziale può quindi spostare la classifica in favore delle specie prive di riferimenti con la prima regola. Il massimo è una verifica esplorativa aggiunta dopo aver esaminato la prima regola.

L'uso di dizionari e maschere è ispirato a Sun, Yen e Lin. Il caso di separazione neurale mirata a un solo uccello di Sasek e colleghi e il confronto tra originale e tracce di Bird MixIT hanno guidato la valutazione. L'implementazione è specifica di questo esperimento. [Paper e risultati degli autori](ESTRAZIONE-FONTI.md)

## Quanto si recupera del verso debole?

Abbiamo miscelato 27 coppie di specie, usando 54 file esclusi dal banco. Per ogni coppia le stesse due registrazioni sono state combinate a tre livelli. In questa prova le due identità sono fornite all'estrattore. Misuriamo il guadagno SI-SDR rispetto all'originale, ossia quanto la forma d'onda assomiglia di più al riferimento dopo l'estrazione, a meno di un fattore di volume.

| Energia del bersaglio rispetto all'altro uccello | Guadagno mediano | Coppie migliorate |
|---|---:|---:|
| Uguale | −1,30 dB | 13/27 |
| −6 dB | +2,98 dB | 16/27 |
| −12 dB | +3,59 dB | 17/27 |

Anche a −12 dB dieci coppie su 27 peggiorano. I riferimenti possono contenere ambiente e gli autori delle 54 registrazioni riservate possono comparire nel banco. Quando presentiamo il solo interferente, la traccia richiesta per la seconda specie ha comunque un livello mediano di −19,22 dB rispetto all'ingresso. L'esistenza della traccia è quindi insufficiente per confermare la presenza. [Risultati per coppia](target-mixtures.json)

## Telefoni con due specie annotate nello stesso istante

Riprendiamo le stesse finestre di cinque secondi del [test telefonico](TELEFONI.md), 31 CEB-XC e 8 Xeno-canto. La seconda specie è quella annotata come secondaria e può anche essere la più forte. I tre controlli italiani con annotazioni a livello di clip e i quattro controlli LG sono conservati nei dati e restano fuori dal denominatore 39. Sui telefoni manca la forma d'onda di riferimento per ciascuna sorgente, quindi valutiamo il riconoscimento.

La seconda specie compariva tra i 12 candidati iniziali in 36/39 casi. In 29/39 è stata estratta e rivalutata. In 28 di questi 29 era già tra i primi tre risultati sull'originale. Ascoltando solo la traccia destinata alla seconda specie, BirdNET la colloca tra le prime tre appena **5 volte su 29**. Con la media geometrica nessuna seconda specie viene recuperata e tre vengono perse. Per la coppia completa si guadagna un caso e se ne perdono otto. [Predizioni per file e rango su ogni traccia](target-predictions.jsonl)

In Italia, su XC785326 BirdNET mette al primo posto il colombaccio anche sulla traccia estratta per lui. La capinera è seconda nell'originale e scende al 125º posto sulla propria traccia. Su XC807426 il prispolone passa dal primo posto al 376º sulla sua traccia e il merlo dal terzo al decimo. Le due registrazioni consentono di ispezionare gli errori, ma sono troppo poche per stimare un'accuratezza nazionale.

| Audio dato a BirdNET e regola di decisione | Seconda specie tra le prime 3 | Entrambe tra le prime 3 |
|---|---:|---:|
| Originale | 34/39 | 29/39 |
| Originale e traccia, media geometrica | 31/39 | 22/39 |
| Originale e traccia, punteggio maggiore | 34/39 | 29/39 |

## Controllo su registrazioni ambientali

Da CEB e WABAD prendiamo 48 finestre di cinque secondi per dataset, una per registrazione. Le scegliamo dal pannello a campionamento uniforme con un ordinamento deterministico indipendente dalle predizioni. Sette su 96 non contengono specie annotate. Valutiamo il primo suggerimento se è un uccello e il punteggio raggiunge 0,25. Un suggerimento conta come corretto se la specie compare nell'annotazione della finestra.

| Regola | CEB corretti / errati | WABAD corretti / errati |
|---|---:|---:|
| Originale | 41 / 1 | 24 / 13 |
| Media geometrica | 12 / 0 | 24 / 12 |
| Punteggio maggiore | 41 / 1 | 24 / 13 |

Per controllare l'effetto della soglia, ordiniamo i primi suggerimenti per punteggio fino a un massimo di 14 errori sull'intero pannello. L'originale raggiunge 66 suggerimenti corretti, la media geometrica 39 e il punteggio maggiore 66. Questo confronto retrospettivo descrive il pannello e non calibra una soglia di produzione. [Risultati ambientali per finestra](target-field.jsonl)

## Sviluppo successivo

Questa configurazione usa profili spettrali statici e una lista iniziale di candidati suggerita da BirdNET. Sarebbe fattibile addestrare un estrattore che usi anche l'andamento temporale di trilli e frasi, con miscele di sorgenti note, rumore registrato da telefoni e prove in cui il bersaglio è assente. Per decidere se integrarlo servirebbe un guadagno sulle registrazioni telefoniche tenute da parte, soprattutto per il secondo uccello, a parità di falsi suggerimenti. Le 39 finestre già ispezionate servono ormai per lo sviluppo.

## Riproduzione locale

Eseguire dalla cartella `research/bird-audio` nell'ambiente del [benchmark](README.md). Occorrono Python, NumPy, SciPy, scikit-learn, threadpoolctl e SoundFile. Per classificare servono anche il runtime e i pesi del benchmark BirdNET. `AUDIO` indica la directory della prova telefonica con i file verificati, `ceb-xc.csv` e `verified-ceb.tar.gz`. `MODELLI` è la directory degli asset del benchmark, inclusi gli archivi ambientali per il comando `field`. `LAVORO` è una directory locale per banco, sonde e tracce.

```text
python target_extract.py build AUDIO LAVORO
python target_extract.py sim AUDIO LAVORO --output target-mixtures.json
python target_extract.py test AUDIO LAVORO --model MODELLI --output target-predictions.jsonl
python target_extract.py field AUDIO LAVORO --model MODELLI --output target-field.jsonl
python target_extract.py extract AUDIO LAVORO --input-wav registrazione.wav --start 0 --species "Columba palumbus" "Sylvia atricapilla" --output tracce
```

Il comando manuale estrae fino a cinque secondi dall'istante indicato. I nomi richiesti devono essere coperti dal banco. Il test automatico salva in `LAVORO` l'originale e le tracce dei due esempi italiani con sovrapposizione verificata. Per ripetere un ascolto o confrontare un diverso estrattore si può sempre ripartire dal file sorgente. I WAV, i pesi del classificatore e gli array di riferimento restano locali. Il repository contiene codice, risultati e [provenienza dei riferimenti](target-references.json). Il prototipo gira fuori dall'app, senza inviare registrazioni a un servizio.
