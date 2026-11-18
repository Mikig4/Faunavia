# Risultati del confronto acustico

CEB comprende 147 registrazioni con annotazioni e WABAD 208 registrazioni di sette siti. In totale sono state elaborate 1.508 finestre distinte di cinque secondi, in versione originale e con rumore aggiunto. La selezione bilanciata per registrazione e tipo di canto contiene 551 finestre. La selezione uniforme per misurare i falsi allarmi ne contiene 1.065. Alcune finestre appartengono ad entrambe.

La prima cifra di ogni cella è la percentuale di primi suggerimenti corretti. La seconda è la quota media delle specie annotate presente nei primi tre suggerimenti. I modelli usano il proprio vocabolario completo, senza filtri spaziali. Le etichette sono confrontate dopo la riconciliazione tassonomica documentata in [evaluation-names.json](evaluation-names.json).

| Dataset | Specie nella finestra | Audio | Finestre | BirdNET 2.4 | BirdNET+ 3.0 | Perch 2.0 |
|---|---|---|---:|---:|---:|---:|
| CEB | Una | Originale | 83 | 49.4% / 73.5% | 74.7% / 85.5% | 65.1% / 80.7% |
| CEB | Una | Rumore aggiunto | 83 | 34.9% / 51.8% | 54.2% / 62.6% | 48.2% / 60.2% |
| CEB | Sovrapposte | Originale | 131 | 77.1% / 53.4% | 97.0% / 78.3% | 89.3% / 64.8% |
| CEB | Sovrapposte | Rumore aggiunto | 131 | 51.1% / 31.9% | 79.4% / 61.3% | 65.7% / 45.7% |
| WABAD | Una | Originale | 170 | 31.2% / 40.0% | 55.3% / 70.0% | 41.2% / 52.9% |
| WABAD | Una | Rumore aggiunto | 170 | 15.3% / 25.3% | 42.4% / 50.0% | 21.8% / 30.6% |
| WABAD | Sovrapposte | Originale | 167 | 37.1% / 24.9% | 72.5% / 56.1% | 50.3% / 37.3% |
| WABAD | Sovrapposte | Rumore aggiunto | 167 | 17.4% / 13.7% | 49.1% / 32.8% | 26.9% / 19.1% |

Nel campione con esattamente due specie sovrapposte, contiamo le finestre in cui entrambi i nomi compaiono fra i primi tre.

| Dataset | Audio | Finestre | BirdNET 2.4 | BirdNET+ 3.0 | Perch 2.0 |
|---|---|---:|---:|---:|---:|
| CEB | Originale | 55 | 22 | 48 | 33 |
| CEB | Rumore aggiunto | 55 | 7 | 29 | 17 |
| WABAD | Originale | 102 | 4 | 42 | 14 |
| WABAD | Rumore aggiunto | 102 | 4 | 14 | 6 |

Proposte corrette a parità di budget di falsi allarmi sul campione uniforme con BirdNET+ 3.0 e Perch 2.0. Ogni modello sceglie tra le stesse 9.474 specie, con soglia stimata su questo campione.

| Dataset | Audio | Finestre | Errori consentiti | Corrette / errate BirdNET+ | Corrette / errate Perch |
|---|---|---:|---:|---:|---:|
| CEB | Originale | 441 | 36 | 360 / 36 | 315 / 36 |
| CEB | Rumore aggiunto | 441 | 36 | 285 / 36 | 251 / 36 |
| WABAD | Originale | 624 | 52 | 279 / 52 | 167 / 52 |
| WABAD | Rumore aggiunto | 624 | 52 | 186 / 52 | 96 / 52 |

Prima proposta corretta dopo l'aggiunta di ciascun disturbo nelle finestre stratificate (una specie oppure sovrapposizione). Ogni finestra riceve un solo disturbo, quindi i gruppi dei tre rumori contengono finestre diverse.

| Dataset | Disturbo | Finestre | BirdNET+ 3.0 | Perch 2.0 |
|---|---|---:|---:|---:|
| CEB | Vento | 66 | 78.8% | 66.7% |
| CEB | Pioggia | 76 | 51.3% | 40.8% |
| CEB | Motore | 72 | 80.6% | 70.8% |
| WABAD | Vento | 113 | 52.2% | 27.4% |
| WABAD | Pioggia | 115 | 28.7% | 15.7% |
| WABAD | Motore | 109 | 56.9% | 30.3% |

Per distribuzione del rumore, controllo con vocabolario comune e intervalli bootstrap appaiati, consultare [le statistiche 3.0](results-v3.json) e [quelle 2.4](results.json). Il [protocollo](README.md) descrive selezione, limiti, provenienza e riproduzione.
