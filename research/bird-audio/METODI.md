# Separare i canti e adattare il riconoscitore

## Cosa misura Bird-MixIT

Bird-MixIT riceve una registrazione mono a 22.050 Hz e stima quattro oppure otto tracce audio. L'ordine delle tracce può cambiare da una registrazione all'altra e una traccia può contenere più suoni o solo rumore. Il nome delle specie viene assegnato dal classificatore successivo. Google pubblica i checkpoint TensorFlow sotto Apache 2.0. Per l'esperimento Faunavia si usa la conversione PyTorch FP32 a quattro uscite pubblicata da matijama, che riporta un confronto numerico con TensorFlow su 40 registrazioni. [Checkpoint Google](https://github.com/google-research/sound-separation/tree/master/models/bird_mixit) · [Implementazione usata](https://github.com/matijama/bird-mixit-pytorch/tree/c49e56a4083d8d1cfc9e0aab3acb72c3b65cf1fa) · [Pesi usati](https://huggingface.co/matijama/bird-mixit-pytorch/tree/9abb2676ea68d969f4f9d585b3bdbc0380e125fe)

Nello studio originale la precisione media sulle specie (CMAP) passa da 0,304 a 0,306 a Sapsucker Woods, da 0,334 a 0,341 a Caples e da 0,527 a 0,560 nelle High Sierras. Gli autori classificano indipendentemente l'originale e le quattro tracce, poi tengono il massimo per specie. Usando solo le tracce, la CMAP scende nei primi due siti. Il risultato riguarda il loro ensemble EfficientNet-B0 e tre siti nordamericani. Un lavoro successivo con separatori diversi su Finlandia e Madagascar mostra che anche i punteggi di specie assenti possono aumentare e valuta un limite al contributo delle tracce basato sul punteggio nell'originale. [Denton et al., tabella 2](https://arxiv.org/pdf/2110.03209) · [Wang et al., sezioni 2.3 e 4](https://arxiv.org/html/2607.03221v1)

## Protocollo su CEB e WABAD

Per ciascun dataset si estraggono per hash, prima di esaminare i punteggi, 16 finestre con esattamente due specie annotate e 16 con almeno tre. Ogni finestra proviene da una registrazione diversa e contiene almeno due canti sovrapposti per 100 ms. Si estraggono per hash anche 12 controlli dal campione uniforme di ciascun dataset. Per ogni finestra si usano i medesimi cinque secondi, prima in originale e poi con vento, pioggia o motore ESC-50 a 0 dB rispetto all'intera finestra. Le due condizioni sono misure appaiate. I tre rumori provengono da nove registrazioni, descritte nel protocollo di base. [Campionamento e licenze](README.md) · [Selezione e inferenza](separate.py)

BirdNET+ 3.0 e Perch 2.0 ricevono l'audio a 32 kHz. Il ramo originale conserva la banda disponibile. Il ramo separato passa per 22.050 Hz e risale a 32 kHz, quindi le quattro tracce perdono le frequenze sopra circa 11 kHz. Per ciascuna specie il punteggio finale è il massimo fra originale e quattro tracce, senza normalizzare separatamente il volume. Le graduatorie comprendono tutte le classi native. Il codice controlla che le tracce sommate ricostruiscano l'audio dato al separatore e che i primi tre nomi ottenuti su ciascun audio non trattato coincidano con il benchmark precedente. [Benchmark di riferimento](predictions-v3.jsonl) · [Codice](separate.py)

## Risultati del separatore

Bird-MixIT con la regola del massimo riduce il recupero medio nei primi tre suggerimenti in tutte e otto le combinazioni provate. Il calo maggiore si osserva con BirdNET+ sul CEB disturbato. La tabella mostra il recupero medio delle specie annotate nelle 32 finestre con sovrapposizioni per dataset. Gli intervalli descrivono la differenza in punti percentuali, con 4.000 ricampionamenti appaiati delle registrazioni all'interno dei due strati. La colonna successiva conta le coppie identificate per intero fra i primi tre suggerimenti nelle 16 finestre con due specie. L'ultima conta i primi suggerimenti errati nei 12 controlli uniformi, emettendo sempre un suggerimento. [Predizioni complete per finestra](sep-predictions.jsonl)

| Dataset | Audio | Classificatore | Recupero@3 originale → con separatore | IC 95% differenza (pp) | Coppie recuperate /16 | Primi suggerimenti errati /12 |
|---|---|---|---:|---:|---:|---:|
| CEB | Originale | BirdNET+ | 80,4% → 75,7% | −9,4 a 0,0 | 14 → 13 | 2 → 2 |
| CEB | Originale | Perch | 67,2% → 67,1% | −8,6 a +7,7 | 12 → 10 | 2 → 2 |
| CEB | 0 dB | BirdNET+ | 60,3% → 49,9% | −16,9 a −4,3 | 10 → 7 | 2 → 3 |
| CEB | 0 dB | Perch | 48,1% → 44,3% | −12,0 a +4,4 | 8 → 6 | 4 → 3 |
| WABAD | Originale | BirdNET+ | 58,7% → 57,1% | −11,5 a +8,3 | 6 → 5 | 4 → 5 |
| WABAD | Originale | Perch | 43,2% → 42,0% | −7,4 a +4,7 | 3 → 4 | 7 → 5 |
| WABAD | 0 dB | BirdNET+ | 41,7% → 36,4% | −12,0 a +0,4 | 4 → 3 | 6 → 6 |
| WABAD | 0 dB | Perch | 25,4% → 23,3% | −6,8 a +2,1 | 2 → 2 | 6 → 9 |

Due casi illustrano l'effetto sul singolo ascolto. In Guinea-Bissau, al secondo 35 di `OIO_20220621_090000.wav`, Perch riconosce *Camaroptera brachyura* nell'originale e dopo la separazione recupera anche *Ploceus cucullatus*, portandolo al secondo posto. Nel CEB, al secondo 135 di `TE001/000015_morning.flac` con vento aggiunto, BirdNET+ colloca fra i primi tre sia il pigliamosche (*Muscicapa striata*) sia il pettirosso (*Erithacus rubecula*). Con la separazione *Polioptila caerulea*, assente dalle annotazioni, passa al primo posto e il pigliamosche esce dai primi tre. [Graduatorie e punteggi](sep-predictions.jsonl)

Il campione comprende 88 finestre distinte da 85 registrazioni. L'eguale numerosità dei due strati assegna loro lo stesso peso. I 24 controlli uniformi contengono tutti almeno una specie annotata, quindi i relativi conteggi misurano errori di identificazione in presenza di uccelli. La frequenza degli allarmi su silenzio o solo rumore richiede ulteriori controlli. La prova riguarda finestre di cinque secondi, un separatore a quattro tracce e una specifica regola di fusione. Ogni finestra costa una separazione e cinque inferenze per classificatore. [Protocollo e verifiche](separate.py)

## Addestrare per rumore e più specie

Nel progetto è stata predisposta anche una prova supervisionata con un adattatore BirdNET addestrato su registrazioni di date distinte dal test. Il relativo rapporto descrive la partizione temporale WABAD, il controllo esterno CEB e la gestione delle specie poco rappresentate. [Protocollo di adattamento](ADATTAMENTO.md)

BirdNET-Analyzer supporta un classificatore personalizzato sugli embedding della versione BirdNET scelta. La documentazione spiega come fornire più etichette per clip, esempi negativi verificati e suoni ambientali. Perch 2.0 restituisce un vettore medio di 1.536 elementi per cinque secondi. Durante il suo preaddestramento gli autori mescolano più segnali e attivano tutte le specie presenti nel bersaglio di training. Questi meccanismi consentono di adattare il riconoscimento anche se il dataset locale non contiene esempi di ciascuna delle migliaia di specie del modello globale. [BirdNET-Analyzer, training](https://birdnet-team.github.io/BirdNET-Analyzer/dev/best-practices/training.html) · [BirdNET-Analyzer, gestione delle etichette](https://birdnet-team.github.io/BirdNET-Analyzer/stable/usage/cli.html#birdnet-analyzer-train) · [Perch 2.0, sezioni 2.1 e 2.2](https://arxiv.org/html/2508.04665v1)

Per un adattamento locale, la proposta è annotare tutte le specie udibili per finestra, includere rumori ambientali verificati e addestrare un'uscita binaria per specie sugli embedding congelati. Le registrazioni con etichette incomplete richiedono revisione o esclusione dei bersagli ignoti dalla funzione di perdita. Si possono creare sovrapposizioni e rapporti segnale/rumore diversi soltanto nel training, dopo aver assegnato registrazioni, siti e sorgenti di rumore alle partizioni. Per misurare la trasferibilità servono test su nuovi siti e dispositivi, annotazioni indipendenti e soglie fissate su una validazione separata. La possibilità tecnica di addestrare questo strato e l'efficacia in tali condizioni sono domande diverse. [Studio su embedding bioacustici congelati](https://pmc.ncbi.nlm.nih.gov/articles/PMC10739890/) · [Studio sul mixup nel training di BirdNET](https://doi.org/10.1016/j.ecoinf.2021.101236)

## Riproduzione

Con gli input del benchmark di base, installare il pacchetto Python dal commit indicato e salvare `bird_mixit_4sources.safetensors` dalla revisione citata nella cartella input. Lo script verifica SHA-256 `2d6a57db5884e078953ffb9f2a9e2d00a2d1986fefb02115c677b6565e2088ff`. La prova usa il runtime locale PyTorch 2.12.0 CPU. Dalla cartella di questa nota si può eseguire l'intera prova o ricalcolare i risultati dalle predizioni.

```sh
python separate.py sep-predictions.jsonl --inputs CARTELLA_INPUT
python separate.py sep-predictions.jsonl
```
