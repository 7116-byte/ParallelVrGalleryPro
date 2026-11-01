param([string]$SourceRoot, [Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if([string]::IsNullOrWhiteSpace($SourceRoot)){$SourceRoot=$projectRoot}
. (Join-Path $PSScriptRoot 'Use-Environment.ps1') -ProjectRoot $projectRoot
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$out = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $out | Out-Null
if(Test-Path -LiteralPath (Join-Path $out 'app-benchmark.apk')){throw 'Use a new output directory to retain the previous benchmark'}
$inputs=@()
foreach($folder in @('app/src/main','app/src/debug')) {
    $inputs+=@(Get-ChildItem -LiteralPath (Join-Path $source $folder) -File -Recurse)
}
$inputs+=Get-Item -LiteralPath (Join-Path $source 'app/build.gradle.kts')
$manifest=foreach($item in $inputs){@{path=$item.FullName.Substring($source.Length+1).Replace([char]92,[char]47);sha256=(Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash}}
$manifest | ConvertTo-Json -Depth 3 | Set-Content -Encoding utf8 (Join-Path $out 'source-manifest.json')
$sdkLine='sdk.dir='+$env:ANDROID_SDK_ROOT.Replace([char]92,[char]47)
[IO.File]::WriteAllText((Join-Path $source 'local.properties'), $sdkLine+[Environment]::NewLine)
Push-Location $source
try {
    $ErrorActionPreference='Continue'
    & .\gradlew.bat --no-daemon --offline --console=plain --init-script (Join-Path $projectRoot 'scripts/portable-signing.init.gradle') assembleBenchmark 2>&1 | Tee-Object (Join-Path $out 'build.log') | Out-Host
    $buildExit=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($buildExit -ne 0){throw 'Benchmark build failed'}
    $apk=Join-Path $source 'app/build/outputs/apk/benchmark/app-benchmark.apk'
    $tools=Join-Path $env:ANDROID_SDK_ROOT 'build-tools/35.0.0'
    $metadata=& "$tools/aapt.exe" dump badging $apk
    if($LASTEXITCODE -ne 0 -or ($metadata | Select-String '^application-debuggable')){throw 'Benchmark must be non-debuggable'}
    if(!($metadata | Select-String "^package: name='com.local.parallelvrgallerypro'")){throw 'Unexpected benchmark package'}
    $tree=& "$tools/aapt.exe" dump xmltree $apk AndroidManifest.xml
    if($LASTEXITCODE -ne 0 -or !($tree | Select-String 'E: instrumentation ')){throw 'Explicit QA instrumentation missing'}
    $cert=& "$tools/apksigner.bat" verify --print-certs $apk
    if($LASTEXITCODE -ne 0 -or !($cert | Select-String 'c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359')){throw 'Original certificate required'}
    Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'app-benchmark.apk')
    $metadata | Set-Content -Encoding utf8 (Join-Path $out 'metadata.txt')
    $cert | Set-Content -Encoding utf8 (Join-Path $out 'signature.txt')
    @{passed=$true;debuggable=$false;instrumentation=$true;qaOnly=$true;source=$source;sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $out 'result.json')
}finally {Pop-Location}
