$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$domainRoot = Join-Path $projectRoot 'core/domain/src'
$uiRoot = Join-Path $projectRoot 'app/src/main'

$androidImports = Get-ChildItem -LiteralPath $domainRoot -Recurse -File -Filter '*.kt' |
    Select-String -Pattern '^import (android\.|androidx\.|it\.faunavia\.local\.)'
if ($androidImports) {
    throw "Domain module imports Android APIs:`n$($androidImports -join "`n")"
}

$providerCalls = Get-ChildItem -LiteralPath $uiRoot -Recurse -File -Filter '*.kt' |
    Select-String -Pattern 'api\.gbif\.org|nnb\.isprambiente|discomap\.eea|https?://'
if ($providerCalls) {
    throw "UI contains a direct provider/network endpoint:`n$($providerCalls -join "`n")"
}

$unsupportedEncoding = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'core') -Recurse -File -Filter '*.kt' |
    Where-Object { $_.FullName.Contains('\src\main\') } |
    Select-String -Pattern 'URLEncoder\.encode\([^,\r\n]+,\s*(Charsets|StandardCharsets)\.UTF_8\s*\)'
if ($unsupportedEncoding) {
    throw "URLEncoder Charset overload requires Android 13; use the encoding name for minSdk 26:`n$($unsupportedEncoding -join "`n")"
}

$secretPatterns = Get-ChildItem -LiteralPath $projectRoot -Recurse -File -Include '*.kt','*.kts','*.properties','*.toml' |
    Where-Object { -not $_.FullName.Contains('\build\') -and -not $_.FullName.Contains('\.gradle\') } |
    Select-String -Pattern '(?i)(api[_-]?key|secret|token)\s*=\s*["''][A-Za-z0-9_\-]{16,}'
if ($secretPatterns) {
    throw "Possible committed secret:`n$($secretPatterns -join "`n")"
}

Write-Host 'PASS architectural boundaries and secret scan'
