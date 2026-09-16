param(
    [string] $ToolchainRoot = $(
        if ($env:FAUNAVIA_TOOLCHAIN_ROOT) {
            $env:FAUNAVIA_TOOLCHAIN_ROOT
        }
        else {
            Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Faunavia/toolchains'
        }
    )
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$shortTemp = $env:FAUNAVIA_SHORT_TEMP
if ($shortTemp) {
    New-Item -ItemType Directory -Force -Path $shortTemp | Out-Null
    $env:TEMP = $shortTemp
    $env:TMP = $shortTemp
}
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$downloads = Join-Path $ToolchainRoot 'downloads'
$jdkRoot = Join-Path $ToolchainRoot 'jdk'
$gradleRoot = Join-Path $ToolchainRoot 'gradle'
$androidRoot = Join-Path $ToolchainRoot 'android-sdk'
$commandLineToolsVersion = '15859902'
$commandLineToolsSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
$gradleVersion = '9.6.0'
$jdkVersion = '17.0.20.1+1'
$jdkArchiveName = 'OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip'
$jdkSha256 = 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'

function Get-VerifiedDownload {
    param(
        [Parameter(Mandatory)] [string] $Uri,
        [Parameter(Mandatory)] [string] $Destination,
        [Parameter(Mandatory)] [string] $Sha256
    )

    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Host "Downloading $Uri"
        Invoke-WebRequest -Uri $Uri -OutFile $Destination -UseBasicParsing
    }
    $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Sha256.ToLowerInvariant()) {
        throw "Checksum mismatch for $Destination. Expected $Sha256, got $actual."
    }
}

New-Item -ItemType Directory -Force -Path $downloads, $jdkRoot, $gradleRoot, $androidRoot | Out-Null

$jdkHome = Join-Path $jdkRoot "jdk-$jdkVersion"
if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin/java.exe'))) {
    $jdkArchive = Join-Path $downloads $jdkArchiveName
    $jdkUri = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/$jdkArchiveName"
    Get-VerifiedDownload -Uri $jdkUri -Destination $jdkArchive -Sha256 $jdkSha256
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkRoot
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$(Join-Path $jdkHome 'bin')$([IO.Path]::PathSeparator)$env:Path"

$gradleHome = Join-Path $gradleRoot "gradle-$gradleVersion"
if (-not (Test-Path -LiteralPath (Join-Path $gradleHome 'bin/gradle.bat'))) {
    $gradleArchive = Join-Path $downloads "gradle-$gradleVersion-bin.zip"
    $gradleSha = (Invoke-RestMethod -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip.sha256" -UseBasicParsing).Trim()
    Get-VerifiedDownload -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -Destination $gradleArchive -Sha256 $gradleSha
    Expand-Archive -LiteralPath $gradleArchive -DestinationPath $gradleRoot
}

$sdkManager = Join-Path $androidRoot 'cmdline-tools/latest/bin/sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    $toolsArchive = Join-Path $downloads "commandlinetools-win-$commandLineToolsVersion`_latest.zip"
    Get-VerifiedDownload `
        -Uri "https://dl.google.com/android/repository/commandlinetools-win-$commandLineToolsVersion`_latest.zip" `
        -Destination $toolsArchive `
        -Sha256 $commandLineToolsSha256
    $staging = Join-Path $ToolchainRoot "command-line-tools-$commandLineToolsVersion"
    if (Test-Path -LiteralPath $staging) {
        throw "Unexpected existing staging directory: $staging"
    }
    Expand-Archive -LiteralPath $toolsArchive -DestinationPath $staging
    $latest = Join-Path $androidRoot 'cmdline-tools/latest'
    New-Item -ItemType Directory -Force -Path $latest | Out-Null
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools/bin') -Destination $latest
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools/lib') -Destination $latest
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools/NOTICE.txt') -Destination $latest
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools/source.properties') -Destination $latest
}

$env:ANDROID_HOME = $androidRoot
$env:ANDROID_SDK_ROOT = $androidRoot
$licenses = 1..100 | ForEach-Object { 'y' }
$licenses | & $sdkManager --sdk_root=$androidRoot --licenses | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager license acceptance failed with exit code $LASTEXITCODE."
}

& $sdkManager --sdk_root=$androidRoot `
    'platform-tools' `
    'emulator' `
    'platforms;android-36' `
    'build-tools;36.0.0' `
    'system-images;android-36;default;x86_64'
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager package installation failed with exit code $LASTEXITCODE."
}

$androidCli = Join-Path $androidRoot 'cmdline-tools/latest/bin/android.exe'
& $androidCli --sdk=$androidRoot sdk install 'platforms/android-37.2'
$compileSdkJar = Join-Path $androidRoot 'platforms/android-37.2/android.jar'
if (-not (Test-Path -LiteralPath $compileSdkJar)) {
    throw "Android SDK Platform 37.2 installation failed; missing $compileSdkJar."
}

$localPropertiesPath = Join-Path $projectRoot 'local.properties'
$escapedSdk = $androidRoot.Replace('\', '/').Replace(':', '\:')
Set-Content -LiteralPath $localPropertiesPath -Value "sdk.dir=$escapedSdk" -Encoding utf8NoBOM

$gradle = Join-Path $gradleHome 'bin/gradle.bat'
Push-Location $projectRoot
try {
    & $gradle wrapper --gradle-version $gradleVersion --distribution-type bin --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle wrapper generation failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$manifest = [ordered]@{
    generatedAt = (Get-Date -AsUTC -Format o)
    javaHome = $jdkHome
    javaVersion = $jdkVersion
    androidSdkRoot = $androidRoot
    gradleVersion = $gradleVersion
    commandLineToolsVersion = $commandLineToolsVersion
    androidPackages = @(
        'platform-tools',
        'emulator',
        'platforms;android-36',
        'platforms/android-37.2',
        'build-tools;36.0.0',
        'system-images;android-36;default;x86_64'
    )
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $ToolchainRoot 'toolchain.json') -Encoding utf8NoBOM

Write-Host "PASS Android toolchain ready at $ToolchainRoot"
Write-Host "Run: . scripts/android-env.ps1; ./gradlew.bat verifyAll"
