param([switch]$FullArchive)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$archive = Join-Path $root '.local\recovery'
$verification = Join-Path $root '.local\verification'
$manifestPath = Join-Path $verification 'migration-manifest.json'
$expected = (Get-Content (Join-Path $verification 'migration-manifest.sha256') -Raw).Trim()
if ((Get-FileHash $manifestPath -Algorithm SHA256).Hash -ne $expected) { throw 'Migration manifest SHA256 mismatch' }
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
if (-not $manifest.passed) { throw 'Migration was not completed' }
$count = 0
foreach ($file in $manifest.files) {
    $relative = [string]$file.relativePath
    $selected = $FullArchive -or $relative -match '^(git/|models/|installers/|sources/|technical/)' -or $relative -match '^toolchain/(jdk17/|android-sdk/|git/|gh/|android-user-home/)' -or $relative -match '^toolchain/gradle-home/(wrapper/|caches/modules-2/)'
    if (-not $selected) { continue }
    $path = [IO.Path]::GetFullPath((Join-Path $archive $relative))
    if (-not $path.StartsWith($archive + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Manifest path escapes archive' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing archive file: $relative" }
    if ((Get-Item -LiteralPath $path).Length -ne $file.bytes -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $file.sha256) { throw "Archive SHA256 mismatch: $relative" }
    # Live caches and SDK metadata are mutable; verify only immutable runtime inputs.
    if ($relative -match '^toolchain/(jdk17/|git/|gh/|android-user-home/)' -or $relative -match '^toolchain/android-sdk/(platforms/|build-tools/|licenses/)' -or $relative -match '^toolchain/gradle-home/wrapper/.*\.(zip|jar)$' -or $relative -match '^toolchain/gradle-home/caches/modules-2/files-2\.1/') {
        $runtime = Join-Path (Join-Path $root '.local') $relative
        if (-not (Test-Path -LiteralPath $runtime -PathType Leaf)) { throw "Missing runtime file: $relative" }
        if ((Get-FileHash -LiteralPath $runtime -Algorithm SHA256).Hash -ne $file.sha256) { throw "Runtime SHA256 mismatch: $relative" }
    }
    $count++
    if ($count % 5000 -eq 0) { Write-Output "Local assets verified: $count" }
}
$result = [ordered]@{checkedAt=[DateTimeOffset]::Now.ToString('o');fullArchive=[bool]$FullArchive;checkedArchiveFiles=$count;passed=$true;manifestSha256=$expected;archiveRoot=$archive}
$result | ConvertTo-Json | Set-Content (Join-Path $verification 'last-local-verify.json') -Encoding UTF8
Write-Output "PASS: $count local archive files; runtime inputs also match. No external backup needed."
