param([string]$JavaHome)

$flowpayRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    $JavaHome = Join-Path $flowpayRoot '.tools\jdk-21.0.13+9'
}
$flowpayJava = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $flowpayJava)) {
    throw 'Java 21 was not found. Supply -JavaHome with a JDK 21 directory; see docs/development/dependencies.md.'
}
$flowpayStart = New-Object System.Diagnostics.ProcessStartInfo
$flowpayStart.FileName = $flowpayJava
$flowpayStart.Arguments = '-version'
$flowpayStart.UseShellExecute = $false
$flowpayStart.CreateNoWindow = $true
$flowpayStart.RedirectStandardError = $true
$flowpayProcess = [System.Diagnostics.Process]::Start($flowpayStart)
$flowpayVersion = $flowpayProcess.StandardError.ReadToEnd()
$flowpayProcess.WaitForExit()
$flowpayExitCode = $flowpayProcess.ExitCode
$flowpayProcess.Dispose()
if ($flowpayExitCode -ne 0 -or $flowpayVersion -notmatch 'version "21\.') {
    throw 'The selected runtime is not Java 21. No environment settings were changed.'
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
Write-Output ('Using Java 21 for this PowerShell process: ' + $env:JAVA_HOME)
