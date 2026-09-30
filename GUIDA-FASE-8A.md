# Nota breve: ripetere o riparare i test 8A

1. In PowerShell, dalla radice del repository, caricare `. scripts/android-env.ps1`.
2. Eseguire `& "$env:LOCALAPPDATA/Faunavia/toolchains/gradle/gradle-9.6.0/bin/gradle.bat" verifyAll --no-daemon`. Il Gradle locale evita il download del wrapper se la rete del sandbox è bloccata; se compare `AccessDeniedException` sui JAR della toolchain, ripetere con approvazione fuori dal sandbox, senza alterare i test.
3. Leggere `root/reports/verification/index.html` sotto `$env:FAUNAVIA_BUILD_ROOT`. Per un errore Android, leggere `app/outputs/androidTest-results/managedDevice/debug/pixel2Api36/TEST-pixel2Api36.xml` nella stessa radice. `verifyDevice` controlla che nessun test dichiarato sia stato saltato.
4. Se fallisce Compose su un elemento lontano, chiudere la tastiera e usare `performScrollToNode(hasTestTag(...))` sul contenitore `explore-content`; non affidarsi alla presenza immediata di un item lazy fuori schermo.
5. Se fallisce il provider luoghi, distinguere `Matches(emptyList())` da `Unavailable`; nessuna ricerca parte durante la digitazione. Se fallisce la mappa, verificare prima che elenco, attribuzione e dati locali rimangano leggibili senza tile. Non disabilitare lint o golden per ottenere un verde apparente.
6. Nel test di ripristino 8A non basta controllare che il provider sia stato chiamato: quella chiamata potrebbe risalire a prima della ricreazione. Dopo `emulateSavedInstanceStateRestore()`, attendere e verificare di nuovo titolo dei risultati, mappa/attribuzione e scheda filtrata.

Per i problemi di loopback/JVM già risolti consultare anche `GUIDA-GRADLE-LOOPBACK.md`.
