# Recupero rapido: errore loopback di Gradle

Se Gradle fallisce con `Unable to establish loopback connection`, apri PowerShell nella radice del progetto ed esegui:

```powershell
. .\scripts\android-env.ps1
.\gradlew.bat help --no-daemon
.\gradlew.bat verifyAll --no-daemon
```

Lo script allinea la JVM del wrapper con `gradle.properties`, così `--no-daemon` resta nello stesso processo. Inoltre forza il JDK su Windows a usare il fallback TCP per il canale interno dei worker Gradle, perché il socket Unix locale è la parte rifiutata nell'ambiente ristretto.

Se l'errore resta:

1. esegui di nuovo `android-env.ps1` nella sessione corrente;
2. usa una PowerShell normale o autorizza l'esecuzione fuori dalla sandbox: la sandbox può negare socket e accesso in scrittura alla toolchain;
3. prova prima `help --no-daemon --stacktrace` per distinguere l'avvio dai test;
4. non considerare compilazione manuale o JUnit diretto come sostituti di `verifyAll`.

La correzione dipende da queste impostazioni, che vanno mantenute sincronizzate:

- `JAVA_OPTS=-Xmx3g -Dfile.encoding=UTF-8`, impostata da `scripts/android-env.ps1`;
- `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=NUL`, ereditata anche dai worker Java;
- `org.gradle.jvmargs=-Xms64m -Xmx3g -Dfile.encoding=UTF-8 -Djdk.net.unixdomain.tmpdir=NUL`;
- `org.gradle.internal.instrumentation.agent=false`.
