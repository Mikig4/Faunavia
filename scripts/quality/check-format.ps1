$ErrorActionPreference = 'Stop'
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$extensions = @('.kt', '.kts', '.toml', '.xml', '.properties')
$excludedParts = @('\build\', '\.gradle\', '\.toolchains\')
$violations = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -LiteralPath $projectRoot -Recurse -File |
    Where-Object {
        $filePath = $_.FullName
        $extensions -contains $_.Extension -and
        -not ($excludedParts | Where-Object { $filePath.Contains($_) })
    } |
    ForEach-Object {
        $content = Get-Content -Raw -LiteralPath $_.FullName
        if ($content.Contains("`t")) {
            $violations.Add("tab: $($_.FullName)")
        }
        if ($content -match '(?m)[ ]+$') {
            $violations.Add("trailing whitespace: $($_.FullName)")
        }
        if (-not ($content.EndsWith("`n"))) {
            $violations.Add("missing final newline: $($_.FullName)")
        }
    }

if ($violations.Count -gt 0) {
    throw "Format check failed:`n$($violations -join "`n")"
}

Write-Host 'PASS format invariants'
