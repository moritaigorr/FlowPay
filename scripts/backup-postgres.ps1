param(
    [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory
} else {
    Join-Path $repositoryRoot $OutputDirectory
}
$null = New-Item -ItemType Directory -Path $outputPath -Force
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $outputPath "flowpay-$timestamp.dump"
$partialPath = "$backupPath.partial"
$containerPath = "/tmp/flowpay-backup-$([guid]::NewGuid().ToString('N')).dump"

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $result = & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
    return $result
}

function Assert-LocalDockerContext {
    $endpoint = & docker context inspect --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0) {
        throw "Could not determine the active Docker context."
    }
    if ($endpoint -notmatch '^(npipe|unix)://') {
        throw "Backup is restricted to a local Docker context; the active Docker endpoint is not a local socket."
    }
}

try {
    Assert-LocalDockerContext
    $running = Invoke-Compose -Arguments @("ps", "--status", "running", "-q", "db")
    if (-not $running) {
        throw "The FlowPay Compose database is not running. Start it with 'docker compose up -d db'."
    }

    $databaseCheck = 'test "$POSTGRES_DB" = "flowpay"'
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $databaseCheck)

    $dumpCommand = 'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file="' + $containerPath + '"'
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $dumpCommand)
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('test -s "' + $containerPath + '"'))
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "pg_restore", "--list", $containerPath)
    $null = Invoke-Compose -Arguments @("cp", "db:$containerPath", $partialPath)

    $backupInfo = Get-Item -LiteralPath $partialPath
    if ($backupInfo.Length -eq 0) {
        throw "PostgreSQL produced an empty backup."
    }
    Move-Item -LiteralPath $partialPath -Destination $backupPath
    Write-Output "Backup created: $backupPath ($($backupInfo.Length) bytes)"
} finally {
    Remove-Item -LiteralPath $partialPath -Force -ErrorAction SilentlyContinue
    try {
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('rm -f "' + $containerPath + '"'))
    } catch {
        # The container may have stopped while the backup was running.
    }
}
