$ErrorActionPreference = 'Stop'
$Repetitions = 3
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N')
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$email = 'r21-' + [guid]::NewGuid().ToString('N') + '@example.test'
$company = 'FlowPay R21 disposable ' + [guid]::NewGuid().ToString('N')
$passwordBytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$password = [Convert]::ToHexString($passwordBytes)
$variables = @('FLOWPAY_BOOTSTRAP_ENABLED','FLOWPAY_BOOTSTRAP_COMPANY_NAME','FLOWPAY_BOOTSTRAP_EMAIL','FLOWPAY_BOOTSTRAP_PASSWORD','FLOWPAY_PERF_EMAIL','FLOWPAY_PERF_PASSWORD','FLOWPAY_PERF_HOST_BASE_URL')
$prior = @{}
foreach ($name in $variables) { $prior[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$provisionAttempted = $false
$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ('flowpay-r21-' + [guid]::NewGuid().ToString('N'))
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
    if ($LASTEXITCODE -ne 0) { throw 'Disposable R21 operator provisioning failed.' }

    $env:FLOWPAY_PERF_EMAIL=$email
    $env:FLOWPAY_PERF_PASSWORD=$password
    $env:FLOWPAY_PERF_HOST_BASE_URL='http://127.0.0.1:8080'
    $seedJson = node performance/k6/seed.mjs
    if ($LASTEXITCODE -ne 0) { throw 'R21 synthetic screen dataset setup failed.' }
    $seed = $seedJson | ConvertFrom-Json -DateKind String
    $authJson = node performance/lighthouse/auth.mjs
    if ($LASTEXITCODE -ne 0) { throw 'R21 authenticated browser session setup failed.' }
    $auth = $authJson | ConvertFrom-Json

    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    $headersPath = Join-Path $temporaryDirectory 'headers.json'
    [IO.File]::WriteAllText($headersPath, (@{ Cookie = [string]$auth.cookieHeader } | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
    $resultsPath = Join-Path $PSScriptRoot ('results-' + $runId + '.json')
    $medianPath = Join-Path $PSScriptRoot ('medians-' + $runId + '.json')
    if ((Test-Path -LiteralPath $resultsPath) -or (Test-Path -LiteralPath $medianPath)) { throw 'R21 run output already exists; refusing to overwrite prior performance evidence.' }
    $results = [Collections.Generic.List[object]]::new()
    $scenarios = @(
        @{ name='overview'; path='/overview' },
        @{ name='payments'; path='/payments' },
        @{ name='payment-detail'; path="/payments/$($auth.paymentId)" }
    )
    $devices = @('mobile','desktop')
    $runNumber = 0
    foreach ($device in $devices) {
        foreach ($scenario in $scenarios) {
            for ($repetition = 1; $repetition -le $Repetitions; $repetition++) {
                $runNumber++
                $rawPath = Join-Path $temporaryDirectory ('report-' + $runNumber + '.json')
                $url = 'http://127.0.0.1:8080' + $scenario.path
                Write-Output "Running Lighthouse $device $($scenario.name) repetition $repetition/$Repetitions."
                $lighthouseArgs = @($url,'--quiet','--only-categories=performance','--output=json',"--output-path=$rawPath", "--extra-headers=$headersPath", '--chrome-flags=--headless --no-sandbox')
                if ($device -eq 'desktop') { $lighthouseArgs += '--preset=desktop' }
                else { $lighthouseArgs += '--preset=perf' }
                & (Join-Path $PSScriptRoot '..\..\frontend\node_modules\.bin\lighthouse.cmd') @lighthouseArgs
                if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $rawPath)) { throw "Lighthouse failed for $device/$($scenario.name), repetition $repetition." }
                $summaryPath = Join-Path $temporaryDirectory ('summary-' + $runNumber + '.json')
                $summary = node performance/lighthouse/summarize.mjs $rawPath $summaryPath $scenario.name $device $repetition | ConvertFrom-Json
                if ($LASTEXITCODE -ne 0) { throw 'Lighthouse summary validation failed.' }
                $results.Add($summary)
                Remove-Item -LiteralPath $rawPath,$summaryPath -Force
            }
        }
    }
    $results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultsPath -Encoding utf8
    Write-Output "Saved $($results.Count) sanitized Lighthouse result summaries to performance/lighthouse/$(Split-Path -Leaf $resultsPath)."
    $medianReport = node performance/lighthouse/aggregate.mjs $resultsPath $medianPath | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Lighthouse median aggregation failed.' }
    Write-Output "Saved six verified three-run medians to performance/lighthouse/$(Split-Path -Leaf $medianPath)."
} finally {
    if (Get-Location | Where-Object { $_.Path -eq $root }) { Pop-Location }
    $cleanupError = $null
    if ($provisionAttempted) {
        $env:FLOWPAY_BOOTSTRAP_EMAIL=$email
        try { & (Join-Path $root 'performance\k6\cleanup.ps1') -Email $email }
        catch { $cleanupError = 'CRITICAL: bounded R21 cleanup did not verify; preserve the database and report this exact cleanup failure.' }
    }
    foreach ($name in $variables) {
        if ($null -eq $prior[$name]) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        else { [Environment]::SetEnvironmentVariable($name, $prior[$name], 'Process') }
    }
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $password = $null
    $auth = $null
    $authJson = $null
    $resolvedTemporaryDirectory = [IO.Path]::GetFullPath($temporaryDirectory)
    $resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $resolvedParent = [IO.Path]::GetFullPath((Split-Path -Parent $resolvedTemporaryDirectory)).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedParent -eq $resolvedTempRoot -and (Split-Path -Leaf $resolvedTemporaryDirectory) -match '^flowpay-r21-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $resolvedTemporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($cleanupError) { throw $cleanupError }
}
