$flowpayProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $flowpayProjectRoot '.env'))) {
    throw 'Run scripts/setup-local.ps1 first, then configure the three FLOWPAY_BOOTSTRAP values in .env.'
}

Push-Location -LiteralPath $flowpayProjectRoot
try {
    docker compose up --detach --wait database
    if ($LASTEXITCODE -ne 0) { throw 'The local database did not become ready.' }
    docker compose build backend
    if ($LASTEXITCODE -ne 0) { throw 'The backend image build failed.' }
    docker compose --profile tools run --rm --no-deps bootstrap
    if ($LASTEXITCODE -ne 0) {
        throw 'Local provisioning failed. Check the safe application error and your .env configuration; existing credentials are never reset.'
    }
    Write-Output 'Local company/operator provisioned. Login is available through the API; no credentials were printed.'
} finally {
    Pop-Location
}
