param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$toolchain = Join-Path $ProjectRoot '.local\toolchain'
$env:JAVA_HOME = Join-Path $toolchain 'jdk17'
$env:ANDROID_SDK_ROOT = Join-Path $toolchain 'android-sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:GRADLE_USER_HOME = Join-Path $toolchain 'gradle-home'
$env:ANDROID_USER_HOME = Join-Path $toolchain 'android-user-home'
$env:PVG_SIGNING_KEYSTORE = Join-Path $toolchain 'android-user-home\debug.keystore'
foreach ($relative in @('jdk17\bin\java.exe', 'android-sdk\platforms\android-36\android.jar', 'android-sdk\build-tools\34.0.0\aapt.exe', 'android-sdk\build-tools\35.0.0\apksigner.bat', 'android-user-home\debug.keystore', 'git\cmd\git.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $toolchain $relative) -PathType Leaf)) { throw "Local recovery asset missing: $relative" }
}
$env:PATH = "$env:JAVA_HOME\bin;$toolchain\git\cmd;$toolchain\gh;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"
$sdkValue = $env:ANDROID_SDK_ROOT.Replace('\', '/')
$encoded = New-Object Text.StringBuilder
foreach ($character in $sdkValue.ToCharArray()) {
    if ([int]$character -gt 127) { [void]$encoded.Append(('\u{0:x4}' -f [int]$character)) }
    else { [void]$encoded.Append($character) }
}
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'local.properties'), ('sdk.dir=' + $encoded.ToString() + "`r`n"), [Text.Encoding]::ASCII)
