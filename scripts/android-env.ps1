$toolchainRoot = if ($env:FAUNAVIA_TOOLCHAIN_ROOT) {
    $env:FAUNAVIA_TOOLCHAIN_ROOT
}
else {
    Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Faunavia/toolchains'
}

$jdkHome = Join-Path $toolchainRoot 'jdk/jdk-17.0.20.1+1'
if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin/java.exe'))) {
    throw 'Faunavia JDK not found. Run scripts/bootstrap-android.ps1 first.'
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = Join-Path $toolchainRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not $env:FAUNAVIA_BUILD_ROOT) {
    $env:FAUNAVIA_BUILD_ROOT = Join-Path $toolchainRoot 'builds/Faunavia'
}
New-Item -ItemType Directory -Force -Path $env:FAUNAVIA_BUILD_ROOT | Out-Null
if ($env:FAUNAVIA_SHORT_TEMP) {
    New-Item -ItemType Directory -Force -Path $env:FAUNAVIA_SHORT_TEMP | Out-Null
    $env:TEMP = $env:FAUNAVIA_SHORT_TEMP
    $env:TMP = $env:FAUNAVIA_SHORT_TEMP
}
$env:Path = @(
    (Join-Path $env:JAVA_HOME 'bin')
    (Join-Path $env:ANDROID_HOME 'platform-tools')
    (Join-Path $env:ANDROID_HOME 'emulator')
    $env:Path
) -join [IO.Path]::PathSeparator
