# Rapporto Fase 1

Data di verifica: 2026-09-14.

## Esito

Fase 1 completata. Il progetto produce un APK debug installabile e il task `verifyAll` esegue l'intera regressione F0+F1 senza passaggi manuali dopo il caricamento dell'ambiente.

## Fondazione realizzata

- Moduli `:app`, `:core:domain` e `:core:testing`, con dominio Kotlin indipendente da Android.
- Sei destinazioni Compose placeholder: Home, Percorsi, Risultati, Diario, Catalogo e Impostazioni.
- Porte di dominio per clock, posizione e provider specie; fake deterministici condivisi.
- Toolchain portabile e verificata tramite checksum: Temurin JDK 17.0.20.1+1, Gradle 9.6.0, AGP 9.4.0, build tools 36.0.0, compile SDK 37.2, target SDK 37 e min SDK 26.
- Pixel 2 gestito da Gradle su API 36 x86_64; output Gradle esterno alla cartella OneDrive.
- Gate `verifyFast`, `verifyDevice`, `verifyVisual` e `verifyAll`, più una prova di pubblicazione del report in caso di fallimento.

## Risultati automatici

- `verifyAll`: PASS, 101 task Gradle, 25 eseguiti, 1 da cache e 75 aggiornati; durata 31 secondi a toolchain calda.
- Regressione F0: 13 test superati, 0 falliti.
- Test JVM F1: 7 superati, 0 falliti (2 app, 3 dominio, 2 fake condivisi).
- Test strumentati: 3 superati, 0 falliti su Pixel 2 API 36.
- Navigazione: tutte le sei schermate placeholder raggiunte.
- Installazione/avvio: launcher verificato tramite UI Automator.
- Golden visuale: firma cromatica versionata della Home verificata e bitmap effettiva catturata durante il test.
- Lint, formattazione, confini architetturali e scansione segreti: PASS.
- Failure probe: fallimento intenzionale rilevato e report HTML presente prima dell'uscita non-zero.
- Build ripetibile: `verifyFast` e la successiva esecuzione completa `verifyAll` entrambe verdi.

Totale test automatici con asserzioni: 23 (13 F0, 7 JVM F1, 3 strumentati F1), tutti superati.

## Comandi

```powershell
pwsh -NoProfile -File scripts/bootstrap-android.ps1
. scripts/android-env.ps1
.\gradlew.bat verifyAll --no-daemon
```

Nel sandbox Windows usato durante la verifica è stato necessario impostare prima `$env:FAUNAVIA_SHORT_TEMP='C:\ftmp'`; su una normale shell locale non è richiesto.

## Report e artefatti locali

Gli output sono sotto `%LOCALAPPDATA%\Faunavia\toolchains\builds\Faunavia`:

- APK: `app\outputs\apk\debug\app-debug.apk` (11.896.219 byte).
- Indice: `root\reports\verification\index.html`.
- Test dispositivo: `app\reports\androidTests\managedDevice\debug\allDevices\index.html`.
- Lint: `app\reports\lint-results-debug.html`.
- Failure probe: `root\reports\verification\failure-probe.html`.

## Rischio residuo

AGP 9.4 emette ancora un advisory sul futuro default ABI di AGP 10 anche con `testedAbi = "x86_64"` esplicito. Non influenza il gate attuale, ma va ricontrollato prima di aggiornare AGP.
