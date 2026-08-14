param(
    [ValidateSet("Release", "Debug")]
    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $Root "..\.."))
$BuildDir = Join-Path $Root "build"
$DistDir = Join-Path $Root "dist"
$DistDll = Join-Path $DistDir "MediaPlayerInfo.dll"
$ResourceDir = Join-Path $ProjectRoot "src\main\resources\mediaplayerinfo\natives\win"
$ResourceDll = Join-Path $ResourceDir "MediaPlayerInfo.dll"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set. Point it to a JDK installation."
}

Write-Host "Configuring MediaPlayerInfo.dll ($Configuration, x64)..."
cmake -S $Root -B $BuildDir -A x64
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

cmake --build $BuildDir --config $Configuration --parallel
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Candidates = @(
    (Join-Path $BuildDir "out\$Configuration\MediaPlayerInfo.dll"),
    (Join-Path $BuildDir "out\MediaPlayerInfo.dll"),
    (Join-Path $BuildDir "$Configuration\MediaPlayerInfo.dll")
)
$BuiltDll = $Candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $BuiltDll) {
    throw "Build succeeded but MediaPlayerInfo.dll was not found."
}

New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
Copy-Item $BuiltDll $DistDll -Force
Write-Host "Built DLL: $DistDll"

$ResourcesRoot = Join-Path $ProjectRoot "src\main\resources"
if (Test-Path $ResourcesRoot) {
    New-Item -ItemType Directory -Path $ResourceDir -Force | Out-Null
    Copy-Item $BuiltDll $ResourceDll -Force
    Write-Host "Installed into project: $ResourceDll"
} else {
    Write-Host "Standalone native source detected; project resources copy skipped."
}

$Dumpbin = Get-Command dumpbin.exe -ErrorAction SilentlyContinue
if ($Dumpbin) {
    Write-Host "JNI exports:"
    & $Dumpbin.Source /nologo /exports $DistDll | Select-String "Java_dev_redstones_mediaplayerinfo|JNI_OnLoad|JNI_OnUnload"
}
