param(
    [Parameter(Mandatory)]
    [string]$BackupPath,

    [string]$TargetDatabase = "flowpay_restore_$(Get-Date -Format 'yyyyMMddHHmmss')"
)

$ErrorActionPreference = "Stop"

if ($TargetDatabase -notmatch '^flowpay_restore_[a-z0-9_]+$') {
    throw "TargetDatabase must start with 'flowpay_restore_' and contain only lowercase letters, digits, or underscores."
}

$resolvedBackupPath = (Resolve-Path -LiteralPath $BackupPath).Path
$containerPath = "/tmp/flowpay-restore-$([guid]::NewGuid().ToString('N')).dump"
$createdTarget = $false

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
        throw "Restore is restricted to a local Docker context; the active Docker endpoint is not a local socket."
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

    $null = Invoke-Compose -Arguments @("cp", $resolvedBackupPath, "db:$containerPath")
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "pg_restore", "--list", $containerPath)

    $existenceCheck = 'psql --username="$POSTGRES_USER" --dbname=postgres --tuples-only --no-align --set=ON_ERROR_STOP=1 --command="SELECT 1 FROM pg_database WHERE datname = ''' + $TargetDatabase + '''"'
    $existing = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $existenceCheck)
    if ($existing -match '\S') {
        throw "Target database '$TargetDatabase' already exists. Choose a new flowpay_restore_* name; existing databases are never overwritten."
    }

    $createDatabase = 'psql --username="$POSTGRES_USER" --dbname=postgres --set=ON_ERROR_STOP=1 --command="CREATE DATABASE ' + $TargetDatabase + ' TEMPLATE template0"'
    $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $createDatabase)
    $createdTarget = $true

    $null = Invoke-Compose -Arguments @(
        "exec", "-T", "db", "sh", "-lc",
        ('pg_restore --exit-on-error --no-owner --no-acl --username="$POSTGRES_USER" --dbname="' + $TargetDatabase + '" "' + $containerPath + '"')
    )
    $migrationQuery = 'psql --username="$POSTGRES_USER" --dbname="' + $TargetDatabase + '" --tuples-only --no-align --set=ON_ERROR_STOP=1 --command="SELECT COUNT(*) FROM flyway_schema_history WHERE success"'
    $migrationCount = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $migrationQuery)
    $migrationCountText = ($migrationCount | Select-Object -Last 1).ToString().Trim()
    $migrationCountValue = 0
    if (-not [int]::TryParse($migrationCountText, [ref]$migrationCountValue) -or $migrationCountValue -lt 1) {
        throw "Restore completed, but Flyway migration history could not be verified in '$TargetDatabase'. The database was retained for inspection."
    }

    Write-Output "Restore verified in new database '$TargetDatabase' with $migrationCountValue successful Flyway migrations."
} catch {
    if ($createdTarget) {
        Write-Warning "Restore failed after creating '$TargetDatabase'. The new database was retained for inspection; the source database was not modified."
    }
    throw
} finally {
    try {
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('rm -f "' + $containerPath + '"'))
    } catch {
        # The container may have stopped while the restore was running.
    }
}
