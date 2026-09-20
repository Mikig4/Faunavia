$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Deliberately separate from verifyFast: this checks the live endpoints and stores no response body.
$requests = @(
    [ordered]@{
        name = 'GBIF occurrence search'
        uri = 'https://api.gbif.org/v1/occurrence/search?taxon_key=1&occurrence_status=present&has_coordinate=true&has_geospatial_issue=false&geometry=POLYGON((9.171%2045.511,9.210%2045.511,9.210%2045.540,9.171%2045.540,9.171%2045.511))&limit=1'
        countProperty = 'count'
    },
    [ordered]@{
        name = 'NNB WFS occurrence search'
        uri = 'https://geoserver.nnb.isprambiente.it/geoserver/nnb/ows?service=WFS&version=2.0.0&request=GetFeature&typeNames=nnb%3AOsservazioni_puntuali&bbox=9.171%2C45.511%2C9.210%2C45.540%2CEPSG%3A4326&count=1&outputFormat=application%2Fjson'
        countProperty = 'numberMatched'
    }
)

foreach ($request in $requests) {
    try {
        $response = Invoke-WebRequest -Uri $request.uri -UseBasicParsing -TimeoutSec 15
        $payload = $response.Content | ConvertFrom-Json
        $count = $payload.($request.countProperty)
        if ($response.StatusCode -ne 200 -or $null -eq $count) {
            throw "unexpected HTTP $($response.StatusCode) or missing $($request.countProperty)"
        }
        Write-Host "PASS $($request.name): HTTP $($response.StatusCode), matches=$count"
    }
    catch {
        throw "FAIL $($request.name): $($_.Exception.Message)"
    }
}
