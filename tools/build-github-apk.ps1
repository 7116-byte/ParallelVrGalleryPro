[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot "gradlew.bat"
$sourceApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$outputDir = Join-Path $repoRoot "_workspace_artifacts\github-release"
$outputApk = Join-Path $outputDir "app-debug.apk"

Push-Location $repoRoot
try {
    & $gradle assembleDebug -PgithubRelease=true
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    Copy-Item -LiteralPath $sourceApk -Destination $outputApk -Force

    $file = Get-Item -LiteralPath $outputApk
    $hash = Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
    [pscustomobject]@{
        Apk = $file.FullName
        SizeMB = [math]::Round($file.Length / 1MB, 2)
        SHA256 = $hash.Hash.ToLowerInvariant()
        ABI = "arm64-v8a"
    } | Format-List
}
finally {
    Pop-Location
}
