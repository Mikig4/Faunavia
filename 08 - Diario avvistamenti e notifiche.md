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

## Implementazione F4 verificata

- APK debug generato: `artifacts/Faunavia-f4-debug.apk`, versione `0.4.0-f4` (`versionCode 4`). Build, lint, test JVM e flussi Android del Diario sono verdi nel gate cumulativo `verifyAll`.
- Il diario salva e rilegge solo record locali Room; crea, modifica ed elimina con conferma esplicita dell'eliminazione.
- La specie è scelta tra i taxa Animalia già selezionati nel Catalogo: la ricerca del diario è esplicitamente offline e una query incompleta non è persistita.
- Ogni record conserva data e ora nel fuso locale, quantità da 1 a 9.999, note fino a 2.000 caratteri e coordinate opzionali complete.
- La migrazione Room v3→v4 aggiunge `quantity` con valore predefinito `1`, senza alterare gli avvistamenti già salvati.
- Foto locali e notifica giornaliera restano rispettivamente F10 e F11.

## Rifinitura F8B: catalogo nel Diario

La limitazione ai taxa già selezionati descritta nell'esito storico F4 è superata dalla rifinitura F8B: il Diario cerca direttamente nel catalogo generale con debounce, stato di ricerca, errore e retry. La selezione esplicita salva localmente il taxon accettato e gli alias prima di associarlo all'editor; un errore mantiene i campi e consente un nuovo tentativo. La registrazione del ricordo resta locale e non richiede rete. Se il catalogo remoto non risponde, le specie già salvate restano selezionabili.

È possibile associare un viaggio e un'uscita anche senza posizione; “Aggiungi avvistamento” nel viaggio precompila questi collegamenti senza richiedere un risultato esterno. Foto, notifiche e schede complete restano nelle fasi già previste.

## Foto F10 verificate

Il Diario e le bozze da identificare ora dispongono di una galleria privata. Salva il ricordo, poi apri **Foto (n)** per selezionare fino a cinque immagini per volta. Copie JPEG e miniature vengono normalizzate e conservate offline, senza upload né permessi generali sulla galleria; gli originali restano invariati e gli EXIF sensibili non vengono copiati. La rimozione di una singola foto conserva il ricordo; identificare una bozza trasferisce tutte le foto atomicamente.

Room 8 conserva metadati e relazioni con migrazione additiva da F9. Il gate completo è verde a 13 F0, 103 JVM e 109 Android, senza errori/skipped/omissioni; include Photo Picker reale con immagini sintetiche e ricreazione dell’Activity. APK `artifacts/Faunavia-f10-debug.apk`, 0.10.0-f10 (16), stessa firma F9. Recupero e limiti in [[GUIDA-FASE-10]], rapporto [[27 - Rapporto Fase 10]]. Notifiche e backup restano F11/F12; nessuna prova su telefono fisico.

## Evoluzioni possibili

Le animazioni decorative possono essere valutate dopo il flusso operativo, ma non sono un requisito del diario. Dovranno essere brevi, non bloccare salvataggio o lettura e rispettare la preferenza di movimento ridotto.
