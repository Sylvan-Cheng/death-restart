#requires -Version 7.0

[CmdletBinding()]
param(
    [string[]]$MinecraftVersion
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$matrix = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle/minecraft-versions.json') -Raw | ConvertFrom-Json -AsHashtable
$targets = if ($MinecraftVersion.Count -gt 0) {
    $MinecraftVersion
} else {
    $matrix.Keys | Sort-Object { [version]$_ }
}

foreach ($target in $targets) {
    if (-not ($matrix.Keys -contains $target)) {
        throw "Unsupported Minecraft version '$target'. Supported targets: $($matrix.Keys -join ', ')"
    }
}

Push-Location -LiteralPath $projectRoot
try {
    foreach ($target in $targets) {
        Write-Host "Building Death Restart for Minecraft $target"
        & .\gradlew.bat --no-daemon build compileIntegrationJava "-Pminecraft_version=$target"
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for Minecraft $target (exit code $LASTEXITCODE)."
        }
    }
} finally {
    Pop-Location
}

Write-Host "All selected Java 25 targets passed. JARs are in build/versions/<Minecraft version>/libs."
