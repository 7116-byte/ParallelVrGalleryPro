param([switch]$Online, [switch]$Clean, [ValidateSet('Debug','Release')][string]$Variant = 'Debug')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$started = [DateTimeOffset]::Now
$run = Join-Path $projectRoot ('.local\verification\runs\' + $started.ToString('yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$variables = @('JAVA_HOME','ANDROID_SDK_ROOT','ANDROID_HOME','GRADLE_USER_HOME','ANDROID_USER_HOME','PVG_SIGNING_KEYSTORE','PATH')
$saved = @{}
foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$result = [ordered]@{ startedAt=$started.ToString('o'); projectRoot=$projectRoot; variant=$Variant; offline=(-not $Online); passed=$false; deviceAcceptance='NOT RUN'; error=$null }
try {
    . (Join-Path $PSScriptRoot 'Use-Environment.ps1') -ProjectRoot $projectRoot
    $result.environment = [ordered]@{javaHome=$env:JAVA_HOME;androidSdk=$env:ANDROID_SDK_ROOT;gradleUserHome=$env:GRADLE_USER_HOME;androidUserHome=$env:ANDROID_USER_HOME;signingKey=$env:PVG_SIGNING_KEYSTORE}
    $expectedSigner = 'c51c08290903e65d2e99fe35ceffd44b637f04cc5bbdd5ea9ecb8847d4ea7359'
    $archivedKey = Join-Path $projectRoot '.local\recovery\toolchain\android-user-home\debug.keystore'
    $keyHash = (Get-FileHash -LiteralPath $env:PVG_SIGNING_KEYSTORE -Algorithm SHA256).Hash
    if ($keyHash -ne (Get-FileHash -LiteralPath $archivedKey -Algorithm SHA256).Hash) { throw 'Signing key differs from preserved backup' }
    $keyInfo = & "$env:JAVA_HOME\bin\keytool.exe" '-J-Duser.language=en' -list -v -keystore $env:PVG_SIGNING_KEYSTORE -storepass android -alias androiddebugkey
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect original signing certificate' }
    $keyDigest = ($keyInfo | Select-String 'SHA256:\s*([0-9A-Fa-f:]+)').Matches.Groups[1].Value.Replace(':','').ToLowerInvariant()
    if ($keyDigest -ne $expectedSigner) { throw 'Original signing certificate mismatch' }
    $result.signingKeySha256 = $keyHash
    $config = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
    $expectedVersion = [regex]::Match($config, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $expectedCode = [regex]::Match($config, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    $arguments = @('--no-daemon','--console=plain','--no-build-cache','--init-script',(Join-Path $PSScriptRoot 'portable-signing.init.gradle'))
    if (-not $Online) { $arguments += '--offline' }
    if ($Clean) { $arguments += 'clean' }
    $arguments += @('testDebugUnitTest','lintDebug',('assemble' + $Variant))
    $result.gradleArguments = $arguments
    & git -C $projectRoot diff --binary | Set-Content (Join-Path $run 'working-tree.patch') -Encoding UTF8
    $sourceFiles = @(& git -C $projectRoot ls-files --cached --others --exclude-standard)
    $sourceManifest = foreach ($relative in $sourceFiles) {
        $sourcePath = Join-Path $projectRoot $relative
        if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
            [ordered]@{path=$relative;bytes=(Get-Item -LiteralPath $sourcePath).Length;sha256=(Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash}
        }
    }
    $sourceManifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $run 'source-manifest.json') -Encoding UTF8
    Push-Location $projectRoot
    try {
        $ErrorActionPreference = 'Continue'
        & '.\gradlew.bat' @arguments 2>&1 | Tee-Object -FilePath (Join-Path $run 'build.log') | Out-Host
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($buildExit -ne 0) { throw "Gradle failed: $buildExit" }
    } finally { Pop-Location; $ErrorActionPreference = 'Stop' }
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0; $suites = @()
    $xmlFiles = @(Get-ChildItem (Join-Path $projectRoot 'app\build\test-results\testDebugUnitTest') -Filter 'TEST-*.xml')
    if (-not $xmlFiles.Count) { throw 'No current JVM test reports found' }
    foreach ($file in $xmlFiles) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $suite = $xml.testsuite
        $tests += [int]$suite.tests; $failures += [int]$suite.failures; $errors += [int]$suite.errors; $skipped += [int]$suite.skipped
        $suites += [ordered]@{name=[string]$suite.name;tests=[int]$suite.tests;failures=[int]$suite.failures;errors=[int]$suite.errors;skipped=[int]$suite.skipped}
    }
    $result.jvm = [ordered]@{tests=$tests;failures=$failures;errors=$errors;skipped=$skipped;suites=$suites}
    if ($tests -lt 58 -or $failures -or $errors -or $skipped) { throw 'JVM baseline acceptance failed (58 minimum, zero failures/errors/skipped)' }
    [xml]$lint = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build\reports\lint-results-debug.xml') -Raw
    $issues = @($lint.issues.issue)
    $lintErrors = @($issues | Where-Object { $_.severity -in @('Error','Fatal') }).Count
    $result.lint = [ordered]@{errors=$lintErrors;warnings=@($issues | Where-Object severity -eq 'Warning').Count;information=@($issues | Where-Object severity -eq 'Information').Count}
    if ($lintErrors) { throw 'Lint contains errors' }
    $apk = Join-Path $projectRoot ('app\build\outputs\apk\' + $Variant.ToLowerInvariant() + '\app-' + $Variant.ToLowerInvariant() + '.apk')
    $badging = & "$env:ANDROID_SDK_ROOT\build-tools\35.0.0\aapt.exe" dump badging $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata extraction failed' }
    $packageLine = ($badging | Select-String '^package:').Line
    $debuggable = [bool]($badging | Select-String '^application-debuggable')
    $manifestTree = & "$env:ANDROID_SDK_ROOT\build-tools\35.0.0\aapt.exe" dump xmltree $apk AndroidManifest.xml
    if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed' }
    $hasInstrumentation = [bool]($manifestTree | Select-String 'E: instrumentation ')
    if ($Variant -eq 'Release' -and ($debuggable -or $hasInstrumentation)) { throw 'Production APK contains debug capabilities' }
    $packageLine | Set-Content (Join-Path $run 'apk-metadata.txt') -Encoding UTF8
    if ($packageLine -notmatch "name='com.local.parallelvrgallerypro'" -or $packageLine -notmatch "versionCode='$expectedCode'" -or $packageLine -notmatch "versionName='$([regex]::Escape($expectedVersion))'") { throw 'APK identity/version mismatch' }
    $signing = & "$env:ANDROID_SDK_ROOT\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $signing | Set-Content (Join-Path $run 'apk-signature.txt') -Encoding UTF8
    $digest = ($signing | Select-String '^Signer #1 certificate SHA-256 digest:\s*(\S+)').Matches.Groups[1].Value.ToLowerInvariant()
    if ($digest -ne $expectedSigner) { throw 'APK signed with wrong certificate' }
    $result.apk = [ordered]@{path=$apk;package='com.local.parallelvrgallerypro';versionName=$expectedVersion;versionCode=[int]$expectedCode;debuggable=$debuggable;instrumentation=$hasInstrumentation;sha256=(Get-FileHash $apk -Algorithm SHA256).Hash;certificateSha256=$digest}
    Copy-Item -LiteralPath $apk -Destination (Join-Path $run 'app-debug.apk')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\test-results\testDebugUnitTest') -Destination (Join-Path $run 'jvm-xml') -Recurse
    Copy-Item (Join-Path $projectRoot 'app\build\reports\lint-results-debug.*') -Destination $run
    & git -C $projectRoot rev-parse HEAD | Set-Content (Join-Path $run 'git-head.txt')
    & git -C $projectRoot status --short --branch | Set-Content (Join-Path $run 'git-status.txt')
    $result.passed = $true
    Write-Output "PASS: $tests JVM tests; lint errors $lintErrors; original signer verified. Evidence: $run"
} catch {
    $result.error = $_.Exception.Message
    throw
} finally {
    $result.finishedAt = [DateTimeOffset]::Now.ToString('o')
    $result | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $run 'result.json') -Encoding UTF8
    [IO.File]::WriteAllText((Join-Path $projectRoot '.local\verification\latest-run.txt'), $run, [Text.Encoding]::UTF8)
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
