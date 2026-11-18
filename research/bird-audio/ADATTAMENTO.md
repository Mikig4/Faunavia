# Filtraggio, allenamento e separazione delle sorgenti

Esperimento offline del 18 novembre 2026. Il riferimento è BirdNET+ 3.0 FP32 con l'intero vocabolario e finestre di cinque secondi. La selezione delle finestre di test, il mix a 0 dB, le licenze, le annotazioni e le equivalenze tassonomiche sono quelli del [primo confronto](README.md). Il mix a 0 dB assegna al rumore aggiunto la stessa potenza dell'intero audio originale. Il rapporto fra il verso di un singolo uccello e il fondo varia.

## Risultati

L'allenamento su WABAD ha usato 1.400 finestre naturali di 117 registrazioni e ha adattato 71 specie. La versione con rumore aggiunge 1.400 copie disturbate. Il test comprende 1.019 finestre distinte, elaborate sia con l'audio naturale sia con rumore, da 91 registrazioni WABAD di 17 giornate-sito successive e da tutte le 147 registrazioni CEB. In questo esperimento il confronto WABAD si riferisce solo alle 91 registrazioni riservate al test.

La tabella riporta la percentuale media di specie annotate recuperate nei primi tre suggerimenti globali, su 75 finestre WABAD e 131 CEB con canti sovrapposti. Un valore più alto indica che il metodo recupera più specie per finestra.

| Metodo | WABAD naturale | WABAD con rumore | CEB naturale | CEB con rumore |
|---|---:|---:|---:|---:|
| BirdNET+ 3.0 | 56,5 | 30,6 | 78,3 | 61,3 |
| Solo passa-alto a 800 Hz | 58,2 | 33,4 | 78,0 | 62,0 |
| Media di audio e passa-alto | 57,5 | 31,7 | 78,3 | 62,1 |
| Sola correzione costante per specie | 66,8 | 43,0 | 77,7 | 60,8 |
| Adattamento acustico su audio naturale | 64,5 | 38,1 | 78,1 | 60,0 |
| Idem, media di audio e passa-alto | 66,1 | 41,2 | 78,1 | 60,3 |
| Adattamento acustico allenato anche con rumore | 63,8 | 47,7 | 76,6 | 58,8 |
| Idem, media di audio e passa-alto | 65,0 | 48,1 | 76,5 | 58,5 |

- **Quanto migliora.** L'ultima variante, rispetto a BirdNET di base, guadagna 17,6 punti percentuali su WABAD con rumore (IC appaiato al 95% da +13,4 a +22,3). Su CEB con rumore perde 2,8 punti (da −5,2 a −0,4). Il confronto è esplorativo su più varianti, senza correzione per confronti multipli. Anche una correzione costante per specie raggiunge il 43,0% su WABAD con rumore. Partire da registrazioni rappresentative dell'ambiente conta molto.
- **Quando cantano esattamente due specie.** Con rumore, entrambe compaiono nei primi tre in 12/46 finestre WABAD con l'ultima variante e in 6/46 con BirdNET di base. Su CEB i conteggi sono 27/55 e 29/55. Con una sola specie e rumore i rispettivi recuperi sono 72,5% contro 60,9% su WABAD e 67,5% contro 62,7% su CEB.
- **Falsi suggerimenti.** Sulle 273 finestre WABAD campionate uniformemente con rumore, consentendo 128 prime proposte per metodo, quelle corrette passano da 96 a 117 e quelle errate da 32 a 11. Su CEB, con 303 proposte su 441 finestre, le corrette scendono da 279 a 272. Il budget di ciascun confronto è il numero di proposte aviarie emesse dal modello di base a soglia 0,25. Per gli altri metodi si selezionano altrettante proposte aviarie in ordine di punteggio. Il [riepilogo numerico](adaptation-results.json) include anche i conteggi alla stessa soglia.
- **Variabilità geografica.** Dei sette siti WABAD, il recupero dell'ultima variante nei cori con rumore sale in quattro, è uguale in due e scende in uno. Cina (27 finestre) e Guinea-Bissau (21) contribuiscono quasi due terzi delle 75 finestre. Il sito spagnolo ha cinque finestre e passa dall'80% al 70%. Il test su audio da telefoni italiani resta aperto.

### Separazione prima del riconoscimento

Sul sottocampione distinto di 32 cori per dataset, Bird-MixIT divide il segnale in quattro tracce. Si classificano sia la miscela sia ciascuna traccia e si prende, per specie, il massimo fra cinque punteggi. Con il rumore, il recupero nei primi tre di BirdNET passa dal 60,3% al 49,9% su CEB e dal 41,7% al 36,4% su WABAD. Anche Perch scende, dal 48,1% al 44,3% e dal 25,4% al 23,3%. Queste percentuali di partenza sono calcolate sui medesimi 32 cori usati per il confronto con il separatore. [Metodo, intervalli e predizioni](METODI.md)

## Protocollo di allenamento

Per ciascuno dei sette siti WABAD ordiniamo le date e riserviamo al test l'ultimo terzo, arrotondato per eccesso. CEB rimane interamente nel test. Il codice usa un massimo di dodici finestre di cinque secondi per file di allenamento, scelte per hash sulla griglia senza consultare le etichette. Entra nella verità di riferimento ogni specie con un evento che interseca la finestra. Una specie può avere un adattatore se compare in almeno cinque finestre distribuite su almeno due registrazioni del training. Tutte le altre classi mantengono il loro punteggio BirdNET. Il modello riceve solo audio. La suddivisione temporale simula il ritorno in siti conosciuti e CEB controlla il trasferimento a un'altra raccolta. Gli stessi corpora erano già stati esaminati nel confronto precedente. La sovrapposizione con il preaddestramento di BirdNET è sconosciuta.

Il classificatore impara una correzione lineare dei log-odds nativi per ogni specie idonea, usando i 1.280 valori dell'embedding BirdNET con norma riportata a otto, più un'intercetta. Minimizza la somma delle perdite logistiche binarie con penalità L2 unitaria, intercetta inclusa. Il controllo con la sola intercetta misura quanto si guadagni correggendo la frequenza delle specie. L'allenamento aumentato usa una copia originale e una a 0 dB per esempio, con penalità raddoppiata per mantenere lo stesso rapporto con il numero degli esempi. I sei rumori sono i primi due file per categoria con registrazione sorgente distinta nel secondo fold ESC-50, scelti in ordine lessicografico. I nove disturbi di test appartengono al primo fold. Non usiamo etichette di test per scegliere classi, parametri o soglie. La scelta del miglior metodo a posteriori resta esplorativa.

Il filtro è un Butterworth passa-alto causale di quarto ordine a 800 Hz su audio a 32 kHz, applicato separatamente a ogni finestra. Nel metodo combinato il modello elabora entrambe le versioni e le probabilità per classe sono mediate con peso uguale. Abbiamo controllato che i primi tre suggerimenti ricalcolati sul segnale di riferimento coincidano con l'archivio del primo confronto per ogni finestra e condizione.

## Riproduzione

Usare gli input e l'ambiente del [protocollo precedente](README.md), più threadpoolctl. I sei rumori di allenamento e le relative impronte SHA-256 sono in [training-noises.json](training-noises.json). Si trovano nella [cartella audio ESC-50 alla revisione utilizzata](https://github.com/karolpiczak/ESC-50/tree/33c8ce9eb2cf0b1c2f8bcf322eb349b6be34dbb6/audio) e vanno nella stessa cartella degli input. Per una nuova configurazione, usare una cartella di cache dedicata e vuota. Il primo comando salva anche i pesi dell'adattatore sotto `CACHE/head/wab-residual.npz`.

```sh
python adapt.py CARTELLA_INPUT CACHE adaptation-predictions.jsonl
python adapt_report.py adaptation-predictions.jsonl
```

Le [predizioni per finestra](adaptation-predictions.jsonl) contengono i primi tre nomi e punteggi globali di ogni metodo. Lo script di riepilogo calcola la sensibilità media nei primi tre, il primo suggerimento corretto, il recupero di entrambe le specie nelle coppie e gli esiti su finestre uniformi con soglia fissa. Gli intervalli appaiati al 95% usano 4.000 ricampionamenti per registrazione su CEB e per giornata entro ciascun sito su WABAD. Descrivono l'incertezza entro il campione di siti e rumori. La prova con Bird-MixIT e i suoi comandi sono in [Separazione delle sorgenti](METODI.md).

## Verifiche del progetto

1. Accesso ai provider dalla UI — fuori ambito, esperimento offline.
2. Provenienza dei dati — versioni, licenze, hash e protocollo collegati. Verificate le impronte dei sei nuovi rumori.
3. Guasti dei provider — fuori ambito, inferenza e riepiloghi usano file locali.
4. Osservato e plausibile — verità annotata e proposte del modello restano distinte in ogni riga.
5. Asset dell'app — nessun asset aggiunto al pacchetto Android.
6. Riproducibilità del ranking — corrispondenza dei primi tre di riferimento su tutte le 2.038 righe del nuovo test. Riepilogo rigenerato identico e separazione verificata fra le 26 giornate-sito di allenamento e le 17 di test WABAD.
