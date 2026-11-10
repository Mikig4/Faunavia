param([string]$OutputPath = 'artifacts/f17-online-overpass-smoke.json')
$ErrorActionPreference = 'Stop'
# Known public Lombardia area; no personal itinerary or raw provider response is saved.
$box = '45.70,8.65,45.90,8.90'
$query = '[out:json][timeout:20][maxsize:16777216];(nwr[leisure=nature_reserve](' + $box + ');nwr[boundary=national_park](' + $box + ');nwr[leisure=bird_hide](' + $box + ');rel[route=hiking](' + $box + ');rel[route=foot](' + $box + '););out meta center 601;'
$response = Invoke-WebRequest -Uri 'https://overpass-api.de/api/interpreter' -Method Post -Body @{ data = $query } -Headers @{ 'User-Agent' = 'Faunavia/0.17.1 (+https://github.com/Mikig4/Faunavia)' } -TimeoutSec 45
$parsed = $response.Content | ConvertFrom-Json
if ($null -eq $parsed.elements) { throw 'Missing Overpass elements array.' }
$elements = @($parsed.elements)
if ($elements.Count -eq 0) { throw 'Known public test area returned no objects.' }
$sample = $elements | Where-Object { $_.type -in @('node', 'way', 'relation') -and $_.id -and $_.tags } | Select-Object -First 1
if ($null -eq $sample) { throw 'No valid OSM identity/tags.' }
$summary = [ordered]@{
    checkedAt = [DateTimeOffset]::UtcNow.ToString('o')
    endpoint = 'https://overpass-api.de/api/interpreter'
    status = $response.StatusCode
    publicTestArea = $box
    count = $elements.Count
    databaseTimestamp = $parsed.osm3s.timestamp_osm_base
    partial = [bool]$parsed.remark
    exampleType = $sample.type
    exampleId = $sample.id
    exampleName = $sample.tags.name
    license = 'ODbL 1.0'
    attribution = '© OpenStreetMap contributors'
    rawResponseSaved = $false
}
$summary | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding utf8
$summary | ConvertTo-Json
