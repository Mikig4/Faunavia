$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$gradle = Join-Path $projectRoot 'gradlew.bat'
. (Join-Path $PSScriptRoot 'android-env.ps1')
$report = Join-Path $env:FAUNAVIA_BUILD_ROOT 'root/reports/verification/failure-probe.html'

Push-Location $projectRoot
try {
    & $gradle failureProbe --stacktrace --no-daemon
    if ($LASTEXITCODE -eq 0) {
        throw 'failureProbe unexpectedly succeeded.'
    }
    if (-not (Test-Path -LiteralPath $report)) {
        throw "failureProbe did not publish $report"
    }
    Write-Host "PASS expected failure published: $report"
}
finally {
    Pop-Location
}
