param(
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')]
    [string[]]$Abi = @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'),
    [switch]$SkipAndroid,
    [switch]$Online,
    [string]$RustToolchain,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $projectRoot '.local/toolchain'
$manifest = Join-Path $projectRoot 'rust/Cargo.toml'
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot '.local/rust-core' }
$hostOutDir = Join-Path $OutputDirectory 'host'
$androidOutDir = Join-Path $OutputDirectory 'android'
New-Item -ItemType Directory -Path $hostOutDir -Force | Out-Null
New-Item -ItemType Directory -Path $androidOutDir -Force | Out-Null

$localCargo = Join-Path $toolchain 'cargo/bin/cargo.exe'
$useLocalCargo = Test-Path -LiteralPath $localCargo -PathType Leaf
$cargo = if ($useLocalCargo) { $localCargo } else { (Get-Command cargo -CommandType Application).Source }

$savedEnv = @{}
$envVars = @('CARGO_HOME', 'RUSTUP_HOME', 'RUSTUP_TOOLCHAIN', 'CARGO_TARGET_DIR', 'CARGO_TARGET_X86_64_PC_WINDOWS_GNULLVM_LINKER')
foreach ($name in $envVars) { $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

$sharedExports = @(
    'Java_com_local_parallelvrgallerypro_RustCore_nativeAbiInfo',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectComfy',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectReference',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectPhoneStereo',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeFilterPhoneDepth',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeNormalizeModelDepth',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeReadNormalizedDirect',
    'Java_com_local_parallelvrgallerypro_RustCore_nativePackTfliteRgbDirect',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeRotateDepthCcw90',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeExtractQnnDepthDirect',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeDisparitySpan',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeResizeDepth',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeDepthTargetSize',
    'Java_com_local_parallelvrgallerypro_RustCore_nativePostProcessDepth',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeSmoothTemporal',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeMapDepthDirection',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeQnnInputGeometry',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeSortVideoTimeline',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeCheckSegmentCoverage',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeValidateSegmentTimestamp',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeSegmentRange',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeWriteDepthCheckpoint',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeReadDepthCheckpoint',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeReclaimFrameJpegs',
    'Java_com_local_parallelvrgallerypro_RustCore_nativePublishImage'
)
$androidExports = $sharedExports + @(
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectComfyBitmap',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectPhoneBitmap',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeProjectReferenceBitmapToBgr',
    'Java_com_local_parallelvrgallerypro_RustCore_nativeWriteBgrToBitmap',
    'Java_com_local_parallelvrgallerypro_RustCore_nativePackQnnRgbBitmap'
)

$result = [ordered]@{
    passed = $false
    offline = (-not $Online)
    hostLibrary = $null
    androidJniDir = $androidOutDir
    hostJniDir = $hostOutDir
    verifiedLibraries = @()
    error = $null
}

try {
    if ($useLocalCargo) {
        $env:CARGO_HOME = Join-Path $toolchain 'cargo'
        $env:RUSTUP_HOME = Join-Path $toolchain 'rustup'
        if (!$RustToolchain) { $RustToolchain = 'stable-x86_64-pc-windows-gnullvm' }
    }
    if ($RustToolchain) { $env:RUSTUP_TOOLCHAIN = $RustToolchain }
    if ($env:RUSTUP_TOOLCHAIN -like '*-windows-gnullvm' -and !$env:CARGO_TARGET_X86_64_PC_WINDOWS_GNULLVM_LINKER) {
        $env:CARGO_TARGET_X86_64_PC_WINDOWS_GNULLVM_LINKER = 'rust-lld'
    }
    $env:CARGO_TARGET_DIR = Join-Path $projectRoot 'rust/target'

    $common = @('--manifest-path', $manifest, '--locked')
    if (!$Online) { $common += '--offline' }

    Write-Output "=== 1. Running Rust unit tests (pvg-core + pvg-jni) ==="
    $ErrorActionPreference = 'Continue'
    & $cargo test @common --workspace --all-targets
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw "Rust workspace tests failed ($code)" }

    Write-Output "=== 2. Building Host JNI library for JVM unit tests ==="
    $ErrorActionPreference = 'Continue'
    & $cargo build @common --package pvg-jni --lib --release
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0) { throw "Host pvg-jni build failed ($code)" }

    $hostLibName = if ($IsLinux) { 'libpvg_core.so' } elseif ($IsMacOS) { 'libpvg_core.dylib' } else { 'pvg_core.dll' }
    $hostBuilt = Join-Path $env:CARGO_TARGET_DIR "release/$hostLibName"
    if (!(Test-Path -LiteralPath $hostBuilt -PathType Leaf)) { throw "Missing host JNI library: $hostBuilt" }
    $hostDest = Join-Path $hostOutDir $hostLibName
    Copy-Item -LiteralPath $hostBuilt -Destination $hostDest -Force
    $result.hostLibrary = [ordered]@{
        path = $hostDest
        sha256 = (Get-FileHash -LiteralPath $hostDest -Algorithm SHA256).Hash
    }
    Write-Output "Host JNI ready: $hostDest"

    if (!$SkipAndroid) {
        $ndkRoot = Join-Path $toolchain 'android-sdk/ndk/30.0.16248370'
        if (!(Test-Path -LiteralPath $ndkRoot -PathType Container)) {
            $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }
            foreach ($sdk in $sdkRoots) {
                $ndkDir = Join-Path $sdk 'ndk'
                if (Test-Path -LiteralPath $ndkDir -PathType Container) {
                    $ndkCandidates = Get-ChildItem -Path $ndkDir -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
                    if ($ndkCandidates) { $ndkRoot = $ndkCandidates[0].FullName; break }
                }
            }
            if (!(Test-Path -LiteralPath $ndkRoot -PathType Container)) {
                $ndkEnv = @($env:ANDROID_NDK_ROOT, $env:ANDROID_NDK_HOME) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) } | Select-Object -First 1
                if ($ndkEnv) { $ndkRoot = $ndkEnv }
            }
        }
        $hostTag = if ($IsLinux) { 'linux-x86_64' } elseif ($IsMacOS) { 'darwin-x86_64' } else { 'windows-x86_64' }
        $exeSuffix = if ($IsLinux -or $IsMacOS) { '' } else { '.exe' }
        $ndkBin = Join-Path $ndkRoot "toolchains/llvm/prebuilt/$hostTag/bin"
        $clang = Join-Path $ndkBin "clang$exeSuffix"
        $readelf = Join-Path $ndkBin "llvm-readelf$exeSuffix"
        foreach ($p in @($clang, $readelf)) {
            if (!(Test-Path -LiteralPath $p -PathType Leaf)) { throw "Missing NDK tool: $p" }
        }

        $targets = @{
            'arm64-v8a'   = @{ Rust = 'aarch64-linux-android'; Clang = 'aarch64-linux-android'; Machine = 183; Class = 2 }
            'armeabi-v7a' = @{ Rust = 'armv7-linux-androideabi'; Clang = 'armv7a-linux-androideabi'; Machine = 40; Class = 1 }
            'x86'         = @{ Rust = 'i686-linux-android'; Clang = 'i686-linux-android'; Machine = 3; Class = 1 }
            'x86_64'      = @{ Rust = 'x86_64-linux-android'; Clang = 'x86_64-linux-android'; Machine = 62; Class = 2 }
        }
        $apiLevel = 30
        $pageSize = 16384

        Write-Output ("=== 3. Cross-compiling Android libpvg_core.so ({0}) ===" -f ($Abi -join ', '))
        foreach ($currentAbi in $Abi) {
            $t = $targets[$currentAbi]
            $clangTarget = "--target=$($t.Clang)$apiLevel"
            $ErrorActionPreference = 'Continue'
            & $cargo rustc @common --package pvg-jni --lib --target $t.Rust --release -- `
                -C "linker=$clang" -C "link-arg=$clangTarget" -C 'link-arg=-Wl,-z,max-page-size=16384'
            $code = $LASTEXITCODE
            $ErrorActionPreference = 'Stop'
            if ($code -ne 0) { throw "Android cross-compile failed for $currentAbi ($code)" }

            $builtSo = Join-Path $env:CARGO_TARGET_DIR "$($t.Rust)/release/libpvg_core.so"
            $elf = [IO.File]::ReadAllBytes($builtSo)
            if ($elf.Length -lt 20 -or $elf[0] -ne 0x7F -or $elf[1] -ne 69 -or $elf[2] -ne 76 -or $elf[3] -ne 70 -or
                $elf[4] -ne $t.Class -or $elf[5] -ne 1 -or [BitConverter]::ToUInt16($elf, 16) -ne 3 -or
                [BitConverter]::ToUInt16($elf, 18) -ne $t.Machine) {
                throw "Invalid ELF binary for $currentAbi"
            }

            $notes = & $readelf --notes $builtSo
            $apiBytes = '{0:x2} 00 00 00' -f $apiLevel
            if ($LASTEXITCODE -ne 0 -or ($notes -join "`n") -notmatch "(?s)\.note\.android\.ident.*?description data:\s+$apiBytes\b") {
                throw "Android API $apiLevel note missing for $currentAbi"
            }

            $symbols = (& $readelf --dyn-syms --wide $builtSo) -join "`n"
            if ($LASTEXITCODE -ne 0) { throw "Failed reading dynamic symbols for $currentAbi" }
            foreach ($sym in $androidExports) {
                if ($symbols -notmatch "(?m)\bGLOBAL\s+DEFAULT\s+\d+\s+$([regex]::Escape($sym))\s*$") {
                    throw "Missing JNI export $sym in $currentAbi"
                }
            }

            $segments = & $readelf --program-headers --wide $builtSo
            if ($LASTEXITCODE -ne 0) { throw "Failed reading ELF program headers for $currentAbi" }
            $aligns = @(foreach ($line in $segments) {
                if ($line -match '^\s*LOAD\s+.+\s+0x([a-f0-9]+)\s*$') { [Convert]::ToUInt64($Matches[1], 16) }
            })
            if (!$aligns.Count -or ($aligns | Where-Object { $_ -lt $pageSize })) {
                throw "16 KB LOAD alignment check failed for $currentAbi"
            }

            $abiDir = Join-Path $androidOutDir $currentAbi
            New-Item -ItemType Directory -Path $abiDir -Force | Out-Null
            $destSo = Join-Path $abiDir 'libpvg_core.so'
            Copy-Item -LiteralPath $builtSo -Destination $destSo -Force
            $result.verifiedLibraries += [ordered]@{
                abi = $currentAbi
                target = $t.Rust
                path = $destSo
                bytes = $elf.Length
                sha256 = (Get-FileHash -LiteralPath $destSo -Algorithm SHA256).Hash
            }
            Write-Output ("Verified {0}: {1:N0} bytes (API 30, 16KB LOAD, {2} exports)" -f $currentAbi, $elf.Length, $androidExports.Count)
        }
    }
    $result.passed = $true
} catch {
    $result.error = $_.Exception.Message
    throw
} finally {
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'result.json') -Encoding UTF8
    foreach ($name in $envVars) { [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process') }
}
