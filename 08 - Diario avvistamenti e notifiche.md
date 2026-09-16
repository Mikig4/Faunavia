# Diario avvistamenti e notifiche

## Flusso di inserimento

1. L'utente tocca “Nuovo avvistamento”.
2. Può scegliere una specie suggerita oppure cercare nel catalogo completo per nome comune/scientifico.
3. Dopo aver selezionato un animale esistente, aggiunge eventualmente foto, posizione, data/ora, quantità e note.
4. L'app salva subito in Room, senza attendere rete o risposta da un provider.
5. La selezione di un taxon animale accettato è obbligatoria prima del salvataggio. Una ricerca incompleta può restare solo nello stato transitorio della schermata e non genera un avvistamento persistito.

## Modello di dominio

```text
Observation
 ├─ observedAt: Instant
 ├─ localDate: LocalDate
 ├─ location: coordinate opzionali
 ├─ taxonId: obbligatorio
 ├─ photoIds: zero o più
 ├─ notes
 ├─ count: opzionale
 └─ origin: manual | imported
```

Le osservazioni manuali e quelle importate non devono essere mischiate nel calcolo delle specie suggerite. Il nome della specie non è un campo libero: viene risolto dal catalogo tassonomico prima del salvataggio.

## Regola dei suggerimenti

I suggerimenti arrivano da `SuggestionProfile`, non dall'elenco indiscriminato di tutti i record GBIF. Un profilo è suggeribile se:

- è associato all'area/habitat corrente;
- ha un punteggio di peculiarità sufficiente;
- non è marcato come animale urbano comune;
- ha almeno una fonte o una motivazione curatoriale;
- non è escluso da una regola di sicurezza o di privacy.

L'utente può comunque registrare qualunque altro animale.

## Notifica serale flessibile

L'utente imposta un orario preferito, ad esempio 20:30. L'app programma con WorkManager un controllo locale giornaliero entro una finestra flessibile. Al momento del controllo:

1. determina la data nel fuso orario corrente;
2. conta gli avvistamenti manuali di quella data;
3. se il conteggio è zero, non mostra nulla;
4. se il conteggio è maggiore di zero, mostra “Hai registrato N avvistamenti oggi”;
5. il tap apre il riepilogo con foto e specie.

La notifica non deve interrogare Firebase né chiamare GBIF. Non viene richiesto un allarme esatto: WorkManager è adatto al lavoro periodico ma non garantisce l'esecuzione al minuto. Riavvio, cambio di fuso, risparmio energetico e permesso negato sono casi previsti. Android 13+ richiede inoltre il permesso `POST_NOTIFICATIONS`. Riferimenti: [WorkManager periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) e [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Foto e privacy

- Usare il Photo Picker Android per scegliere immagini senza chiedere accesso esteso alla galleria quando possibile.
- Copiare una versione ridotta nell'area privata dell'app e conservare l'originale solo se l'utente lo desidera.
- Rimuovere o rendere opzionale la condivisione dei metadati EXIF, soprattutto coordinate e dispositivo.
- Non caricare foto online nell'MVP.
