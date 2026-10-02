# Fase 9 — Suggerimenti e desideri

1. Installa `artifacts/Faunavia-f9-mappe-debug.apk` (0.9.2-f9) sopra la versione precedente: stessa app e firma debug; conserva i dati. Non disinstallare l'app per aggiornarla.
2. Nel **Catalogo**, cerca e seleziona un animale, poi premi **Vorrei vederlo**. **Lista: vorrei vederlo** apre i desideri locali e permette di rimuoverli.
3. In **Viaggi**, seleziona viaggio, tappa o uscita; esegui l'analisi con le date e l'area corrette. Salva i risultati interessanti per ritrovarli offline.
4. Premi **Suggerimenti e desideri**. I **Tipici del luogo** selezionano i profili curati presenti nei risultati. **Mai osservati da me** usa tutto il diario identificato; le bozze non nascondono una specie. **Più facili da osservare** dichiara la mancanza di una base confrontabile e mantiene l'ordine alfabetico.
5. **Vorrei vederlo** aggiunge il taxon accettato; **L'ho visto** apre la conferma del diario con il viaggio/uscita. Conferma data e luogo reali: il punto dell'evidenza esterna non viene copiato.

La ricerca libera e l'analisi del viaggio si aprono su **Animali tipici**; **Tutte le specie documentate** permette di consultare anche gli altri risultati e i loro eventuali dati insufficienti. Se la selezione tipica è vuota, non significa assenza di fauna. I risultati già scelti e salvati personalmente restano nell'elenco offline.

Le schede iniziali mostrano nomi, habitat e stagionalità. Il titolo ha contrasto esplicito; senza nome comune italiano disponibile mostra subito quello scientifico. Le specie fuori dal pilota possono recuperare il nome italiano da una corrispondenza tassonomica esatta GBIF. **Evidenze e fonti** apre spiegazioni, provenienza e limiti.

Il clic su **Habitat → Apri distribuzione nell’app** è disponibile per tutte le specie e apre una schermata interna. L’areale illustrato, quando associato esattamente alla specie e riutilizzabile, proviene da Wikimedia Commons: puoi ingrandirlo con due dita, leggere legenda, autore, licenza e data. **Segnalazioni GBIF** apre una mappa navigabile della densità di osservazioni archiviate; è anche l’alternativa per le specie senza un areale illustrato. Non è un confine di areale né una probabilità d’incontro. Nessun dato cartografico disponibile viene dichiarato esplicitamente; una zona vuota non prova assenza. Non serve aprire Wikipedia o un browser. Immagine e metadati hanno retry separati.

Ruotare il telefono mantiene il risultato e l’analisi eventualmente in corso nella ricerca libera e nei viaggi. La terminazione del processo da parte di Android è diversa dalla rotazione: i risultati live non vengono salvati nel bundle; gli snapshot salvati personalmente restano in Room e la cache delle evidenze segue il contratto F6. Le nuove mappe richiedono rete; metadati e immagini già consultati possono essere recuperati dalle cache locali, senza promettere un pacchetto di mappe offline.

La lista curata è non esaustiva. Include undici specie caratteristiche e un controllo urbano (merlo), con habitat, motivazione e fonte. Il controllo urbano è escluso dai tipici ma disponibile nelle altre viste e nel diario. Il rettangolo curatoriale del pilota non è un confine amministrativo o un areale faunistico. Nessun profilo genera nuove evidenze di presenza o habitat locale. I mesi ricavati dalle osservazioni sono un segnale debole, non una stagionalità biologica completa.

I desideri si leggono e rimuovono offline. Aggiungere una specie già selezionata è locale; risolvere un'identità esterna non ancora selezionata può richiedere il Catalogo online. Se fallisce, il desiderio non viene inventato e la UI mostra l'errore. Le tile e le nuove analisi mantengono i limiti F8.

Una voce dei desideri è pertinente solo se nei risultati disponibili dell'area e del periodo selezionati. Gli snapshot con date, area o tappa modificate vengono conservati, ma non usati per dichiarare pertinenza corrente. Nessuna evidenza disponibile significa **pertinenza non valutabile**, non assenza dell'animale. I dati parziali o da cache non aggiornata sono dichiarati.

Room passa da schema 6 a 7: desideri nuovi, versionamento dei profili esistenti, senza cancellare diario, viaggi, bozze, impostazioni o cache. Non effettuare downgrade dell'APK dopo l'aggiornamento dello schema.

Verifica ripetibile:

```powershell
. scripts/android-env.ps1
.\gradlew.bat verifyAll --no-daemon
```

Il gate include F0, test JVM, integrità/migrazioni/riapertura Room, interfaccia Compose, lint, confini, formattazione, omissioni del runner e firme visive. Foto F10, notifiche F11, backup F12, schede F13, GLB F14, preparazione offline F15 e scoperta di sentieri F17 restano fasi successive.

In F13 **Curiosità** sarà una sezione cliccabile con fatti e fonti verificati, secondo [[03 - Dati e fonti]] e [[09 - Piano di sviluppo dettagliato]].
