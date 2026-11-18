# Riconoscere le specie dal solo audio ripulito

Il [confronto precedente](METODI.md) lasciava competere, per ogni specie, il punteggio sull'audio originale e quelli su quattro sorgenti stimate da Bird-MixIT. In questa prova ciascuna delle tre varianti riceve esclusivamente l'audio elaborato. Le etichette umane entrano nel calcolo delle metriche dopo l'inferenza.

## Risultati sui cori

Percentuale media di specie annotate presenti nei primi tre suggerimenti. Ogni riga confronta gli stessi 32 cori. L'originale mantiene la condizione della riga, incluso il rumore quando previsto.

| Dataset e condizione | Modello | Originale | Solo quattro tracce, classificate una a una | Solo una traccia preselezionata | Solo audio con fondo attenuato |
|---|---|---:|---:|---:|---:|
| CEB, naturale | BirdNET 3.0 | 80,4% | 76,2% | 59,5% | 80,1% |
| CEB, naturale | Perch 2.0 | 67,2% | 67,1% | 46,9% | 66,9% |
| CEB, rumore aggiunto | BirdNET 3.0 | 60,3% | 44,4% | 44,1% | 63,4% |
| CEB, rumore aggiunto | Perch 2.0 | 48,1% | 45,9% | 37,0% | 50,8% |
| WABAD, naturale | BirdNET 3.0 | 58,7% | 55,1% | 37,1% | 60,3% |
| WABAD, naturale | Perch 2.0 | 43,2% | 37,9% | 23,3% | 43,7% |
| WABAD, rumore aggiunto | BirdNET 3.0 | 41,7% | 29,1% | 23,3% | 43,3% |
| WABAD, rumore aggiunto | Perch 2.0 | 25,4% | 21,7% | 16,2% | 26,4% |

Nei cori rumorosi, classificare le sole quattro tracce riduce il recupero di BirdNET rispetto all'originale in entrambi i campioni (IC appaiati al 95% in punti percentuali: da −24,4 a −7,8 su CEB e da −20,5 a −5,2 su WABAD). Attenuando il fondo, gli intervalli sono rispettivamente da 0,0 a +7,8 e da −4,7 a +7,9 punti. Il piccolo guadagno medio richiede una verifica su altri siti e telefoni. Fra le 16 finestre con esattamente due specie e rumore, BirdNET con il fondo attenuato le recupera entrambe in 11 casi su CEB e 4 su WABAD. L'originale ne recupera rispettivamente 10 e 4, e la traccia preselezionata 5 e 2.

## Una sola specie annotata

Primo suggerimento corretto nelle finestre con rumore aggiunto, su 16 registrazioni per dataset.

| Dataset e modello | Originale | Solo quattro tracce | Solo una traccia | Solo fondo attenuato |
|---|---:|---:|---:|---:|
| CEB, BirdNET 3.0 | 8/16 | 8/16 | 8/16 | 9/16 |
| CEB, Perch 2.0 | 8/16 | 8/16 | 8/16 | 8/16 |
| WABAD, BirdNET 3.0 | 6/16 | 5/16 | 3/16 | 5/16 |
| WABAD, Perch 2.0 | 3/16 | 4/16 | 2/16 | 4/16 |

Due esempi dalla prova a 0 dB con BirdNET mostrano come può cambiare la prima proposta.

- **CEB, TE001/000113_evening.flac, da 145 a 150 secondi.** Gli annotatori indicano il tordo bottaccio (*Turdus philomelos*). Sulla miscela BirdNET propone il merlo (*Turdus merula*), sulla traccia preselezionata propone la classe motore e sulla miscela con il fondo attenuato riconosce il tordo bottaccio.
- **WABAD, HUAP_20230505_101202.wav, da 10 a 15 secondi.** Sulla miscela BirdNET identifica la specie annotata, *Staphida torqueola*. Sulla traccia preselezionata passa a *Steatornis caripensis* e con il fondo attenuato a *Caprimulgus affinis*.

## Varianti

1. **Quattro tracce separate.** Ciascuna delle quattro tracce mono di Bird-MixIT entra da sola nel classificatore. Per ogni classe viene tenuto il massimo dei quattro punteggi. Ciò consente di proporre specie udibili su tracce diverse.
2. **Una traccia preselezionata.** Prima di identificare la specie si sceglie, fra le quattro tracce, quella con più energia variabile tra 500 Hz e 9 kHz. Per ogni frequenza si sottrae il triplo della potenza mediana nel tempo e si sommano i residui positivi. Si classifica solo la traccia scelta. La regola può favorire anche altri suoni intermittenti o uccelli più forti di quelli sullo sfondo.
3. **Sola miscela con il fondo attenuato.** Su una finestra di cinque secondi a 32 kHz si stima il fondo al 20° percentile della potenza temporale per ciascuna frequenza. Si sottrae 1,5 volte questa stima dalla potenza istantanea, limitando al 10% l'ampiezza minima relativa. Si ricostruisce una sola traccia con la fase originale e si classifica quella. La trasformata usa una finestra Hann di 1.024 campioni e passo 256. Questa variante può preservare più canti quando condividono lo stesso istante.

I tre metodi lavorano sulla stessa registrazione da un solo microfono e ignorano la specie, il luogo, le annotazioni e il file del disturbo aggiunto. L'originale serve da controllo. I parametri sono stati fissati prima di esaminare gli esiti. Le sorgenti reali isolate sul campo sono sconosciute. Le annotazioni consentono di misurare il riconoscimento delle specie, mentre la fedeltà dei singoli versi separati resta indeterminata.

## Campione e riproduzione

Sono le stesse 88 finestre di cinque secondi da 85 registrazioni del [test di separazione](METODI.md): per ciascun dataset, 16 finestre con due specie sovrapposte, 16 con almeno tre specie e 12 controlli estratti su una griglia uniforme. Ogni finestra è elaborata al naturale e con vento, pioggia o motore aggiunto a 0 dB rispetto all'intera miscela. Le metriche si calcolano anche sul segnale originale della stessa finestra. Il recupero nei primi tre è la quota delle specie annotate che compaiono nei primi tre suggerimenti, mediata per finestra. Il primo suggerimento è corretto se coincide con almeno una delle specie annotate. Nei cori con esattamente due specie si conta inoltre quante volte sono recuperate entrambe.

Con i pesi, le dipendenze e gli input del [protocollo Bird-MixIT](METODI.md), dalla cartella del rapporto:

```sh
python separate.py clean-predictions.jsonl --inputs CARTELLA_INPUT --clean-only
python separate.py clean-predictions.jsonl
python separate.py clean-single.jsonl --inputs CARTELLA_INPUT --clean-only --single
python separate.py clean-single.jsonl
```

Il secondo comando legge le [predizioni](clean-predictions.jsonl) e produce conteggi e intervalli al 95% appaiati, ricampionando separatamente le finestre con due specie e con almeno tre. Per ogni dataset ciascuna delle 32 finestre stratificate viene da una registrazione diversa. Gli intervalli descrivono questo campione e questi nove rumori. I controlli uniformi contengono tutti almeno un uccello annotato.

Il [campione aggiuntivo](clean-single.jsonl) contiene 16 finestre per dataset con una sola specie annotata, estratte per hash dalle finestre stratificate senza consultare le predizioni. Ogni file contribuisce al massimo una finestra per questo confronto. Le posizioni uno e tre misurano, rispettivamente, se la specie è la prima proposta e se compare in una delle tre proposte.

## Verifiche

1. UI e provider — esperimento offline.
2. Provenienza, data, licenza e qualità — input del benchmark precedente e peso verificato con SHA-256.
3. Errori di rete — inferenza eseguita su file locali.
4. Osservazioni e ipotesi — etichette degli annotatori e predizioni salvate separatamente.
5. Asset distribuiti — pubblicati codice e risultati numerici.
6. Ranking — riconfrontate le graduatorie di controllo col benchmark precedente. Controlli sintetici eseguiti su silenzio, rumore tonale stazionario e un segnale breve in banda.
