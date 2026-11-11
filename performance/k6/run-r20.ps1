param([ValidateSet('smoke','reads','mixed','conflict')][string]$Profile='smoke')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$email = 'r20-' + [guid]::NewGuid().ToString('N') + '@example.test'
$company = 'FlowPay R20 disposable ' + [guid]::NewGuid().ToString('N')
$passwordBytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$password = [Convert]::ToHexString($passwordBytes)
$variables = @('FLOWPAY_BOOTSTRAP_ENABLED','FLOWPAY_BOOTSTRAP_COMPANY_NAME','FLOWPAY_BOOTSTRAP_EMAIL','FLOWPAY_BOOTSTRAP_PASSWORD','FLOWPAY_PERF_EMAIL','FLOWPAY_PERF_PASSWORD','FLOWPAY_PERF_HOST_BASE_URL','FLOWPAY_PERF_CUSTOMER_ID','FLOWPAY_PERF_DETAIL_PAYMENT_ID','FLOWPAY_R20_CREATED_FROM','FLOWPAY_R20_CREATED_TO','FLOWPAY_PERF_PROFILE')
$prior = @{}
foreach ($name in $variables) { $prior[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$provisionAttempted = $false
try {
    Push-Location $root
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Local .env is required; it will not be read or modified.' }
    $health = docker compose ps --format json
    if ($LASTEXITCODE -ne 0 -or -not $health) { throw 'Docker Compose is unavailable; start the local FlowPay stack first.' }
    $health = $health | ConvertFrom-Json
    if (@($health | Where-Object Health -ne 'healthy').Count -gt 0) { throw 'The local Compose stack is not healthy.' }
    $provisionAttempted = $true
    $env:FLOWPAY_BOOTSTRAP_ENABLED='true'
    $env:FLOWPAY_BOOTSTRAP_COMPANY_NAME=$company
    $env:FLOWPAY_BOOTSTRAP_EMAIL=$email
    $env:FLOWPAY_BOOTSTRAP_PASSWORD=$password
    docker compose --profile tools run --rm --no-deps bootstrap | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable local operator provisioning failed.' }

    $env:FLOWPAY_PERF_EMAIL=$email
    $env:FLOWPAY_PERF_PASSWORD=$password
    $env:FLOWPAY_PERF_HOST_BASE_URL='http://127.0.0.1:8080'
    $seedJson = node performance/k6/seed.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic tenant setup failed.' }
    $seed = $seedJson | ConvertFrom-Json -DateKind String
    $env:FLOWPAY_PERF_CUSTOMER_ID=[string]$seed.customerId
    $env:FLOWPAY_PERF_DETAIL_PAYMENT_ID=[string]$seed.detailPaymentId
    $env:FLOWPAY_R20_CREATED_FROM=[string]$seed.fixtureFrom
    $env:FLOWPAY_R20_CREATED_TO=[string]$seed.fixtureTo
    $env:FLOWPAY_PERF_PROFILE=$Profile

    docker run --rm --add-host=host.docker.internal:host-gateway --mount "type=bind,source=$PSScriptRoot,target=/scripts,readonly" --workdir /scripts `
      -e FLOWPAY_PERF_BASE_URL=http://host.docker.internal:8080 -e FLOWPAY_PERF_EMAIL -e FLOWPAY_PERF_PASSWORD `
      -e FLOWPAY_PERF_CUSTOMER_ID -e FLOWPAY_PERF_DETAIL_PAYMENT_ID -e FLOWPAY_R20_CREATED_FROM -e FLOWPAY_R20_CREATED_TO -e FLOWPAY_PERF_PROFILE `
      grafana/k6:2.3.0@sha256:2dd4756ec6e14e62778916e1eb850108e4b499304c02df56760fa0bdb0fba0b8 `
      run --summary-trend-stats 'avg,min,med,max,p(50),p(90),p(95),p(99)' api-baseline.js
    if ($LASTEXITCODE -ne 0) { throw "k6 profile '$Profile' reported unexpected failures." }
} finally {
    if (Get-Location | Where-Object { $_.Path -eq $root }) { Pop-Location }
    $cleanupError = $null
    if ($provisionAttempted) {
        $env:FLOWPAY_BOOTSTRAP_EMAIL=$email
        try { & (Join-Path $PSScriptRoot 'cleanup.ps1') -Email $email }
        catch { $cleanupError = 'CRITICAL: bounded R20 cleanup did not verify; preserve the database and report this exact cleanup failure.' }
    }
    foreach ($name in $variables) {
        if ($null -eq $prior[$name]) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        else { [Environment]::SetEnvironmentVariable($name, $prior[$name], 'Process') }
    }
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $password = $null
    if ($cleanupError) { throw $cleanupError }
}
