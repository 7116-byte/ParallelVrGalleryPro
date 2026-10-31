@echo off
setlocal
rem Avoid inheriting incompatible PowerShell 7 modules when launched from pwsh.
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules"
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Verify-LocalAssets.ps1"
if errorlevel 1 exit /b %errorlevel%
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Build.ps1" -Clean
exit /b %errorlevel%
