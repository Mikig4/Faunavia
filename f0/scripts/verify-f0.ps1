$ErrorActionPreference = 'Stop'
$f0Root = Split-Path -Parent $PSScriptRoot

function Assert-Schema {
    param(
        [Parameter(Mandatory)] [string] $Schema,
        [Parameter(Mandatory)] [string[]] $Files
    )

    foreach ($file in $Files) {
        $json = Get-Content -Raw -LiteralPath $file
        if (-not ($json | Test-Json -SchemaFile $Schema)) {
            throw "Schema non valido: $file"
        }
        Write-Host "PASS schema: $file"
    }
}

$fixtureSchema = Join-Path $f0Root 'contracts/fixture-envelope.schema.json'
$providerFixtures = Get-ChildItem -File -LiteralPath (Join-Path $f0Root 'fixtures/providers') -Filter '*.json' |
    Select-Object -ExpandProperty FullName
Assert-Schema -Schema $fixtureSchema -Files $providerFixtures

$normalizedSchema = Join-Path $f0Root 'contracts/normalized-record.schema.json'
$normalizedFixtures = Get-ChildItem -File -LiteralPath (Join-Path $f0Root 'fixtures/normalized') -Filter '*.json' |
    Select-Object -ExpandProperty FullName
Assert-Schema -Schema $normalizedSchema -Files $normalizedFixtures

$routesSchema = Join-Path $f0Root 'contracts/routes.schema.json'
Assert-Schema -Schema $routesSchema -Files @((Join-Path $f0Root 'fixtures/routes/pilot-routes.geojson'))

Push-Location $f0Root
try {
    node --test ./tests/*.test.mjs
    if ($LASTEXITCODE -ne 0) {
        throw "Test Node F0 falliti con exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
