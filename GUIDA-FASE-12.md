# Backup locale — Fase 12

## Esportare

1. Apri **Impostazioni** e scorri fino a **Backup e ripristino**.
2. Premi **Esporta backup**. Il selettore Android propone `Faunavia-backup-AAAA-MM-GG.zip`.
3. Scegli cartella e nome, poi salva. Attendi il messaggio **Backup creato**, con i conteggi.
4. Conserva il file fuori dallo spazio privato dell'app, per esempio in Download o su un supporto personale. Puoi copiarlo su un altro dispositivo.

L'archivio contiene avvistamenti identificati, bozze, copie fotografiche controllate e miniature, viaggi e tappe, tracciati, uscite, luoghi e risultati salvati, collegamenti, desideri, catalogo locale e provenienza, impostazioni e registro degli invii del riepilogo. Include anche le tabelle della cache delle occorrenze: le scadenze originali rimangono valide.

Il file contiene dati personali e posizioni ed è **non cifrato**. La destinazione è quella scelta nel selettore Android; se scegli un provider cloud, il salvataggio dipende da quel provider. Faunavia non richiede account o un servizio remoto. Le immagini esportate sono le copie F10, non gli originali del Photo Picker con i loro EXIF.

## Ripristinare

1. Per conservare lo stato attuale, esporta prima un backup.
2. Premi **Importa backup** e scegli un archivio Faunavia.
3. Attendi i controlli di versione, inventario, dimensioni, hash, foto, dati e collegamenti. Nessun dato attuale viene sostituito in questa fase.
4. Controlla data e conteggi nella finestra. **Annulla** mantiene il diario attuale.
5. **Sostituisci i dati** avvia il ripristino completo: non unisce o deduplica due diari. Attendi fino alla conferma finale.
6. L'app riapre Viaggi con il report del ripristino; editor e analisi precedenti vengono chiusi. Apri Diario per ritrovare i ricordi e le foto.

Le preferenze delle notifiche vengono riallineate al fuso e ai permessi del dispositivo ricevente. Android non trasferisce il proprio permesso tramite il backup: se è negato, il riepilogo resta disattivato e puoi riabilitarlo dalle Impostazioni. Il registro degli invii presente nell'archivio viene conservato; i lavori WorkManager vengono ricreati dalle preferenze.

## Compatibilità e limiti

- Formato archivio **1**, con snapshot delle tabelle Room **9**; Room non cambia schema in F12.
- È accettato anche uno snapshot compatibile con Room **8**, senza inventare lo storico delle notifiche assente. La compatibilità è verificata con una fixture sintetica: prima di F12 non esisteva un formato backup distribuito.
- Versioni future o precedenti allo schema 8 vengono rifiutate con un messaggio. Il formato non include modelli GLB o pacchetti mappa delle fasi future.
- Fino a **2 GiB** di dati non compressi, **10.000 file foto** incluse le miniature, **100.000 righe** totali, database JSON fino a **64 MiB**, singolo file immagine fino a **32 MiB**. Una foto F10 produce normalmente due file.
- Per importare serve spazio per il ZIP, la verifica e le nuove copie: dopo aver copiato il ZIP, il controllo richiede almeno tre volte la dimensione non compressa più **64 MiB** liberi. La richiesta è conservativa.
- Non sono inclusi permessi Android, account, database interno WorkManager, tile e cache HTTP, metadati esterni nelle SharedPreferences o lavori di importazione non ancora completati. I dati personali durevoli restano nelle tabelle e nei file inclusi.

## Errori e recupero

| Messaggio o situazione | Azione |
|---|---|
| Archivio corrotto, incompleto, hash o dimensione errati | Seleziona una copia integra oppure genera un nuovo export sul dispositivo originale. |
| Versione non supportata | Usa una versione di Faunavia compatibile. L'app conserva il diario attuale. |
| Una foto originale dell'archivio locale manca | Recupera la copia dal backup originale, oppure rimuovi esplicitamente quel riferimento dalla galleria prima di esportare. Nessuna foto viene saltata in silenzio. |
| Spazio insufficiente | Libera spazio sul dispositivo e nella destinazione scelta, poi riprova. |
| URI o destinazione non accessibile | Seleziona nuovamente il documento; dopo la chiusura del processo la selezione va ripetuta. |
| Esportazione annullata o fallita | Il diario resta intatto. Faunavia prova a eliminare il documento parziale; se il provider non lo consente, il messaggio chiede di eliminarlo dalla destinazione. |
| Ripristino fallito | La transazione ripristina lo stato precedente; le nuove copie non pubblicate vengono eliminate. Puoi riprovare o annullare la finestra. |
| Pulizia temporanea incompleta | Libera spazio e riprova un backup. I file temporanei vecchi vengono controllati dopo 24 ore. |

La preparazione e l'export possono essere annullati. Dopo la conferma il breve passaggio di pubblicazione dei dati viene completato anche se la coroutine della schermata viene cancellata. Rotazione mantiene lavoro e anteprima tramite ViewModel; dopo la terminazione del processo si riseleziona il file.

Le vecchie foto sostituite restano come copie private non collegate; il recupero F10 elimina i file non referenziati con ultima modifica più vecchia di 24 ore, includendo le sottocartelle create dal ripristino. Se erano già vecchi, possono essere rimossi alla prima pulizia. Non sono un secondo diario consultabile.

Dettagli tecnici ed evidenze: [[30 - Rapporto Fase 12]]. La sincronizzazione opzionale rimane F16.
