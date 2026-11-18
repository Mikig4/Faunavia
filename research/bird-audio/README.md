# Riconoscimento di specie su CEB e WABAD

[Tabelle dei risultati](RISULTATI.md). Esperimento del 18 novembre 2026, eseguito su CPU Windows. [Risultati BirdNET+ 3.0 e Perch 2.0](results-v3.json) · [Predizioni](predictions-v3.jsonl) · [Risultati BirdNET 2.4 e Perch 2.0](results.json) · [Predizioni](predictions.jsonl). Il campo bn identifica la versione BirdNET della relativa esecuzione. Ogni riga identifica un file e un intervallo di cinque secondi, le specie annotate, i primi tre suggerimenti di ogni modello sull'audio originale e sulla copia disturbata, e l'identificatore del rumore utilizzato.

## Registrazioni

- [Central European Birds, Martin e collaboratori](https://zenodo.org/records/21821133). Archivio e CSV di test con annotazioni forti. Sono utilizzate le righe con stato valid. Audio ambientale e annotazioni CC BY 4.0.
- [WABAD, Pérez-Granados e collaboratori](https://zenodo.org/records/20513304). Campione geografico di convenienza scelto prima dell'inferenza per dimensione scaricabile e varietà di ambienti. COU (Francia), MILLAN (Spagna), SD (Canada), EMP (Brasile), HUAP (Cina), DEVA (Nuova Caledonia), OIO (Guinea-Bissau). La selezione dei file richiede almeno una riga nel CSV aggregato per la registrazione. Audio e annotazioni CC BY 4.0.
- [ESC-50, Karol J. Piczak e contributori Freesound](https://github.com/karolpiczak/ESC-50). Per vento, pioggia e motori si usano i primi tre file in ordine lessicografico di ciascuna categoria. Identificatori e impronte in [inputs.json](inputs.json). La raccolta è distribuita sotto CC BY-NC 3.0. Il repository di Faunavia contiene solo predizioni e riferimenti ai file.

## Confronto

Confronto principale tra BirdNET+ v3.0 Global 12K ([ONNX FP32 ufficiale, 13 novembre 2026](https://zenodo.org/records/24162518)) e Google Perch v2 ([conversione ONNX FP32](https://huggingface.co/tphakala/Perch-v2)). Entrambi ricevono gli stessi campioni mono a 32 kHz, cinque secondi per inferenza. Il confronto storico usa BirdNET GLOBAL 6K v2.4, Cornell e Chemnitz ([conversione ONNX FP32](https://huggingface.co/tphakala/BirdNET-v2.4)). Questa versione riceve i primi e gli ultimi tre secondi a 48 kHz, con un secondo in comune. Per ogni classe viene preso il massimo dei due punteggi. Tutti i classificatori sono globali, senza adattamento, filtro locale o stagionale.

Una griglia di cinque secondi senza sovrapposizione definisce le finestre. Per ciascun file e ciascuna delle due condizioni presenti si sceglie per hash una finestra: una sola specie annotata oppure almeno due specie con versi sovrapposti per almeno 100 ms. La verità di riferimento comprende tutte le specie che vocalizzano nella finestra. Separatamente, tre finestre per file sono scelte per hash senza guardare le etichette, anche in assenza di eventi. Le finestre comuni ai due campioni vengono elaborate una volta. Le porzioni finali più brevi di cinque secondi sono escluse.

Per ogni finestra, un hash sceglie un rumore da ESC-50. Dopo la conversione in mono e il ricampionamento, il rumore aggiunto ha lo stesso valore efficace dell'intera registrazione originale (rapporto di potenza 0 dB). Se necessario si riduce il livello della miscela per evitare saturazione. La voce del singolo uccello può essere più debole o più forte rispetto al fondo. Il protocollo misura la risposta a questi nove esempi di disturbo.

L'esito top-1 è corretto quando il primo suggerimento è tra le specie annotate. Il recupero nei primi tre è la quota di specie annotate presenti nei primi tre suggerimenti, mediata per finestra. Il confronto nativo mantiene tutte le classi di ciascun modello, compresi suoni ambientali e altri animali. Il controllo a vocabolario comune limita la graduatoria alle specie identificabili per nome scientifico in entrambi i modelli e nella [tassonomia eBird](https://github.com/CornellLabofOrnithology/auk). Le sostituzioni ortografiche e tassonomiche prima della graduatoria sono elencate in evaluate.py. Nel conteggio finale report.py riconcilia anche i nomi pertinenti con lo stesso codice di specie eBird nelle edizioni storiche consultate, elencati in [evaluation-names.json](evaluation-names.json). Le graduatorie JSONL rimangono quelle registrate in inferenza. Il controllo a vocabolario comune usa la lista fissata prima del confronto, che comprende 9.474 specie per BirdNET 3.0 e Perch o 6.133 per BirdNET 2.4 e Perch. Nel controllo stratificato tutte le specie della finestra devono appartenere al vocabolario comune. Gli intervalli al 95% descrivono la differenza Perch meno BirdNET su finestre appaiate, con 4.000 ricampionamenti per registrazione entro ciascuno strato. Siti, repertori e rumori restano quelli del campione.

Per il confronto dei falsi allarmi, ogni finestra del campione uniforme può produrre una proposta tra le specie comuni, oppure nessuna se il punteggio massimo è sotto soglia. Separatamente per dataset, modello e condizione, si conta quante proposte corrette si ottengono permettendo al massimo una proposta errata al minuto di audio campionato. La soglia viene ricavata sul campione stesso per descrivere un punto della curva. Per scegliere una soglia operativa servirebbe una calibrazione su registrazioni distinte. Un errore tassonomico, una specie presente ma omessa dagli annotatori o udibile solo nella traccia ESC-50 possono risultare come falsi positivi.

## Riproduzione

La [prova sui telefoni](TELEFONI.md) confronta BirdNET con e senza attenuazione del fondo su vocalizzazioni naturalmente sovrapposte, con risultati per la seconda specie e il dettaglio dei casi italiani.

L'[esperimento di estrazione per specie](ESTRAZIONE.md) ricostruisce una traccia mono per ogni candidata e misura sia la fedeltà del suono su miscele controllate, sia il riconoscimento su telefoni e registrazioni ambientali.

Il [secondo esperimento](ADATTAMENTO.md) misura filtraggio, adattamento degli embedding di BirdNET+ 3.0 e separazione a quattro sorgenti. Il rapporto contiene i comandi per riprodurlo e distingue i campioni usati nei due confronti.

Scaricare gli URL di inputs.json in una cartella, conservando i nomi locali e verificando gli SHA-256. Il test è stato eseguito con Python 3.13.12, NumPy 2.3.5, SciPy 1.17.1, SoundFile 0.14.0 e ONNX Runtime 1.27.0.

```sh
python evaluate.py CARTELLA_INPUT predictions.jsonl
python report.py predictions.jsonl
python evaluate.py CARTELLA_INPUT predictions-v3.jsonl --birdnet 3.0
python report.py predictions-v3.jsonl
```

Lo script report.py ricalcola il rapporto dai risultati versionati, senza audio, pesi o rete. Le fonti documentano le etichette umane. La completa indipendenza delle registrazioni dal preaddestramento dei pesi distribuiti rimane da verificare.
