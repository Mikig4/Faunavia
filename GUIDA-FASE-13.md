# F13 — Schede specie, curiosità e consultazione offline

## Uso

1. In Catalogo cerca una specie e premi **Apri scheda specie** sotto il risultato. Aprire la scheda non equivale a selezionare il taxon per il diario o ad aggiungerlo ai desideri.
2. Da Risultati, Viaggi e suggerimenti lo stesso pulsante apre il dettaglio della scheda compatta. Nomi, habitat e stagionalità del risultato restano visibili nella lista.
3. Nel dettaglio trovi habitat generale, stagionalità generale, dimensioni, alimentazione, comportamento, conservazione e indicazioni per osservare senza disturbare. **Dato documentato non disponibile** significa che non abbiamo un dato verificato; non indica assenza della specie.
4. Premi **Curiosità** per aprire i fatti verificati. Ogni curiosità riporta ente e data di consultazione; **Fonte e licenza** mostra provenienza completa e **Apri fonte originale** apre il browser. La sezione resta chiusa finché non la apri.
5. **Apri distribuzione nell’app** conserva il dettaglio sottostante. Chiudi la mappa per tornare alla scheda; **Chiudi** torna alla lista senza ricalcolare la ricerca.
6. Per le prove nel luogo e nel periodo ricercati usa **Evidenze e fonti** nel risultato. I fatti generali della scheda non cambiano il livello documentato/plausibile/insufficiente.

## Copertura e immagini

Le dodici schede pilota sono martin pescatore, picchio nero, stambecco, merlo, airone cenerino, garzetta, svasso maggiore, upupa, aquila reale, falco pellegrino, camoscio e marmotta alpina. Le fonti primarie sono Lipu e Parco Nazionale Gran Paradiso, consultate il 3 ottobre 2026. Si usano brevi note fattuali originali; testi e fotografie delle fonti non sono importati. La data di consultazione non è la data di pubblicazione o di un avvistamento. Le note di conservazione descrivono pressioni e tutela, senza attribuire automaticamente uno stato IUCN attuale.

Per le altre specie si leggono gli eventuali profili locali e si dichiarano i campi mancanti. Nomi scientifici con autore riconosciuto mantengono l'associazione; una sottospecie non eredita automaticamente la scheda della specie nominale.

Il simbolo 2D generico è incluso nell'APK ed è etichettato come **non identificativo**. Non va usato per riconoscere l'animale. Se la risorsa non è leggibile, compare una rappresentazione disegnata dalla UI con avviso. Il 3D viene affrontato in F14.

## Offline, errori e backup

I dodici profili inclusi funzionano anche alla prima apertura senza rete. I profili locali sono già persistiti in Room; una copia normalizzata delle schede viste è conservata separatamente, fino a 64 schede da 128 KiB ciascuna. Nessun nuovo provider viene contattato per recuperare curiosità. Browser e distribuzione online mantengono i propri limiti di connettività.

Se una lettura locale fallisce, la scheda usa il contenuto incluso o la copia precedente con avviso e **Riprova scheda**. Le copie precedenti non prevalgono sui dati locali correnti: dopo un ripristino riuscito non devono ricomparire fatti eliminati. Una cache illeggibile produce un fallback esplicito. Se manca un browser, l'indirizzo della fonte rimane leggibile.

Il backup F12 continua a includere i profili locali persistiti, il diario, le foto e le relazioni. La cache di consultazione è esclusa dal ZIP, come le altre cache di presentazione; i profili pilota sono ricreati dall'APK. Nessuna migrazione: Room resta alla versione 9 e l'archivio al formato 1.

## Verifica e installazione

```powershell
. ./scripts/android-env.ps1
./gradlew.bat verifyAll --no-daemon --stacktrace
```

Il gate cumulativo include F0–F13, lint, confini dei moduli, formattazione, controllo delle omissioni e quattro firme visive. I test nuovi coprono profili completi/parziali, fonti mancanti, cache, errori di lettura/scrittura, link, testi lunghi con accenti, caratteri ingranditi, semantica accessibile, contrasto e ritorno ai risultati/mappa. Diario e backup vengono confrontati prima/dopo la consultazione e il ripristino.

APK: `artifacts/Faunavia-f13-debug.apk`, versione `0.13.0-f13` (20). Usare l'installazione come aggiornamento delle versioni precedenti per conservare i dati. Rapporto ed esiti effettivi: [[31 - Rapporto Fase 13]]. Il test su telefono fisico e una sessione reale con TalkBack restano da eseguire; i test automatici verificano la semantica esposta.
