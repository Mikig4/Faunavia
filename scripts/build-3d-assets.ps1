param([switch]$SkipRender)
$ErrorActionPreference = 'Stop'
$f14Project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$f14Tools = Join-Path $env:LOCALAPPDATA 'Faunavia/toolchains/blender'
$f14Python = Join-Path $f14Tools 'blender-4.5.3-windows-x64/4.5/python/bin/python.exe'
$env:FAUNAVIA_BPY_MODULES = Join-Path $f14Tools 'bpy-modules'
$env:PYTHONHASHSEED = '0'
if (-not (Test-Path -LiteralPath (Join-Path $env:FAUNAVIA_BPY_MODULES 'bpy'))) {
    throw 'Official bpy 4.5.3 module missing in the portable toolchain; see GUIDA-FASE-14.md.'
}
$f14Arguments = @((Join-Path $f14Project 'assets-3d/run-bpy.py'))
if ($SkipRender) { $f14Arguments += '--skip-render' }
& $f14Python @f14Arguments
if ($LASTEXITCODE -ne 0) { throw 'Blender asset generation failed.' }
& npm --prefix (Join-Path $f14Project 'assets-3d') run validate
if ($LASTEXITCODE -ne 0) { throw 'Khronos validation failed.' }
