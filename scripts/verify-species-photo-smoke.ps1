param([string]$ScientificName = 'Turdus merula')
$ErrorActionPreference = 'Stop'
$headers = @{ 'User-Agent' = 'Faunavia/0.17 (controlled metadata smoke)' }
function Get-Json([string]$Url) { Invoke-RestMethod -Headers $headers -Uri $Url -TimeoutSec 20 }
$search = Get-Json ('https://www.wikidata.org/w/api.php?action=wbsearchentities&language=en&type=item&limit=5&format=json&search=' + [uri]::EscapeDataString($ScientificName))
$ids = ($search.search | ForEach-Object { $_.id }) -join '|'
if (-not $ids) { throw 'No Wikidata identity' }
$entities = Get-Json ('https://www.wikidata.org/w/api.php?action=wbgetentities&props=claims%7Csitelinks&format=json&ids=' + [uri]::EscapeDataString($ids))
$exact = @($entities.entities.PSObject.Properties | Where-Object {
    $names = @($_.Value.claims.P225 | Where-Object { $_.rank -ne 'deprecated' -and $_.mainsnak.datavalue.value } | ForEach-Object { $_.mainsnak.datavalue.value })
    $names -contains $ScientificName
})
if ($exact.Count -ne 1) { throw 'Scientific identity ambiguous/missing' }
$entity = $exact[0]
$title = $entity.Value.sitelinks.itwiki.title
if (-not $title) { throw 'Italian Wikipedia article absent in this smoke' }
$wiki = Get-Json ('https://it.wikipedia.org/w/api.php?action=query&prop=pageimages%7Cpageprops&piprop=name&pilicense=free&format=json&titles=' + [uri]::EscapeDataString($title))
$page = $wiki.query.pages.PSObject.Properties.Value | Select-Object -First 1
if ($page.pageprops.wikibase_item -ne $entity.Name) { throw 'Wikipedia identity mismatch' }
$commons = Get-Json ('https://commons.wikimedia.org/w/api.php?action=query&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=1600&format=json&titles=' + [uri]::EscapeDataString('File:' + $page.pageimage))
$info = ($commons.query.pages.PSObject.Properties.Value | Select-Object -First 1).imageinfo[0]
$license = $info.extmetadata.LicenseShortName.value
if ($license -notmatch '^(CC BY(-SA)? (1\.0|2\.0|2\.5|3\.0|4\.0)|CC0( 1\.0)?|Public domain)$') { throw 'Unsupported media rights' }
if (-not $info.extmetadata.Artist.value) { throw 'Missing photographer' }
if (([uri]$info.thumburl).Host -notin @('thumb.wikimedia.org', 'upload.wikimedia.org')) { throw 'Unexpected media origin' }
$media = Invoke-WebRequest -Method Head -Headers $headers -Uri $info.thumburl -UseBasicParsing -TimeoutSec 20
$report = [pscustomobject]@{
    checkedAt = [DateTimeOffset]::UtcNow.ToString('o')
    scientificName = $ScientificName
    wikidataItem = $entity.Name
    article = $title
    file = $page.pageimage
    mediaOrigin = ([uri]$info.thumburl).Host
    mime = $info.mime
    license = $license
    artistPresent = [bool]$info.extmetadata.Artist.value
    mediaStatus = [int]$media.StatusCode
}
$report | ConvertTo-Json
