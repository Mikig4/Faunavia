# Valutazione architetturale

Data della revisione: 2026-09-14

## Giudizio sintetico

L'architettura è ora pronta a guidare l'avvio controllato dello sviluppo: local-first, prudente nell'interpretazione naturalistica, separata dai provider e costruita attorno a gate automatici. La valutazione passa da **7/10 a 8,5/10**. Non è ancora una prova di fattibilità: F0 deve validare fonti, query, area pilota e corridoio prima dello scaffold.

## Punti forti

- La specie è obbligatoria e identificata da un taxon Animalia accettato: il diario non può accumulare identità ambigue.
- “Documentato”, “plausibile” e “suggerito” sono concetti indipendenti e leggibili dall'utente.
- La plausibilità ha una regola conservativa: areale + habitat obbligatori, stagionalità come modificatore, dati mancanti → insufficiente.
- La query geografica ibrida combina precisione, compatibilità con i provider, cache stabile e deduplicazione.
- La dipendenza verso il dominio isola Android, Room, rete, mappa, notifiche e renderer 3D.
- Room/SQLite, foto locali, fallback e assenza di backend obbligatorio proteggono privacy, costi e resilienza.
- Il primo vertical slice naturalistico precede il 3D di produzione.
- Ogni fase ha test e gate; da F2 la non regressione è cumulativa e obbligatoria.

## Problemi prioritari chiusi

1. **Identità tassonomica:** `taxonId` obbligatorio; una ricerca irrisolta resta soltanto nello stato transitorio della UI.
2. **Fonti della plausibilità:** GBIF/NNB per occorrenze; Article 12/17 per distribuzione e stagionalità coperta; matrice EEA specie–habitat e CLCplus per compatibilità ambientale; Natura 2000 solo come contesto positivo.
3. **Query lungo il percorso:** porzioni di corridoio semplificate + celle stabili; poligono o bounding box scelto nell'adapter.
4. **Dipendenze:** UI/framework → adapter/repository → casi d'uso → dominio.
5. **Riproducibilità:** `SourceEvidence`, fingerprint, query, timestamp, licenza, qualità e versione sono parte del contratto.
6. **Offline-first:** capacità e fallback sono assegnati a fasi distinte, senza promettere nuove analisi offline nell'MVP iniziale.
7. **Ordine:** vertical slice in F8; fallback 2D in F13; primo GLB in F14.
8. **Notifica:** WorkManager e finestra flessibile, senza promessa di esecuzione al minuto.

## Rischi residui da validare

- Disponibilità, granularità, licenze e stabilità tecnica delle fonti istituzionali devono essere provate con fixture reali in F0.
- Prima area, gruppi curati e raggio di 1 km sono default, non conclusioni empiriche.
- Le versioni esatte dello stack Android e lo strumento screenshot golden vanno fissati in F1.
- La sorgente della mappa regionale offline resta una decisione post-MVP vincolata dalla licenza.
- La generazione 3D non è affidabile senza revisione umana anatomica e legale.
- La suite cumulativa deve essere mantenuta veloce separando test JVM, device, visuali e smoke online.

## Valutazione della roadmap

La divisione F0–F16 è adeguata perché ogni fase produce un solo incremento principale, esplicita le dipendenze e chiude con un risultato osservabile. I traguardi sono leggibili:

- F4: primo valore locale, diario offline;
- F8: primo vertical slice naturalistico;
- F14: MVP completo con un asset GLB e fallback;
- F15–F16: estensioni post-MVP.

Il gate di non regressione da F2 evita che nuove integrazioni — soprattutto provider, mappa, WorkManager e 3D — compromettano i percorsi locali già funzionanti.

## Decisione finale

Mantenere Android nativo, local-first e senza Firebase nell'MVP. Avviare F0, non direttamente F1: il primo lavoro di sviluppo è lo spike riproducibile su dati e geometria. Lo scaffold Android parte soltanto dopo il gate F0.
