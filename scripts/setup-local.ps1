$flowpayEnvPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
if (Test-Path -LiteralPath $flowpayEnvPath) {
    Write-Output 'Existing .env preserved. See docs/development/local-development.md for configuration.'
    return
}

$flowpayLocalPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$flowpayEnvContent = @(
    "FLOWPAY_DATABASE_PASSWORD=$flowpayLocalPassword"
    'FLOWPAY_HTTP_PORT=8080'
    'FLOWPAY_LOGIN_RATE=5r/m'
    'FLOWPAY_DEMO_RATE=2r/m'
) -join "`n"
[IO.File]::WriteAllText($flowpayEnvPath, $flowpayEnvContent + "`n", (New-Object Text.UTF8Encoding($false)))
Write-Output 'Created ignored .env with a random local database password. No password was printed.'
