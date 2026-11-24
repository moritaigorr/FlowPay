param(
    [Parameter(Mandatory)]
    [string]$BackupPath,

    [string]$TargetDatabase = "flowpay_restore_$(Get-Date -Format 'yyyyMMddHHmmss')",

    [ValidateSet("Compose", "Local")]
    [string]$ConnectionMode = "Compose",

    [string]$PostgresBinDirectory = "",
    [string]$DatabaseHost = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 5432,
    [string]$DatabaseUsername = "flowpay_dev"
)

$ErrorActionPreference = "Stop"

if ($TargetDatabase -notmatch '^flowpay_restore_[a-z0-9_]+$') {
    throw "TargetDatabase must start with 'flowpay_restore_' and contain only lowercase letters, digits, or underscores."
}

$resolvedBackupPath = (Resolve-Path -LiteralPath $BackupPath).Path
$containerPath = "/tmp/flowpay-restore-$([guid]::NewGuid().ToString('N')).dump"
$createdTarget = $false
$postgresExecutables = @{}

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

function Assert-LocalDatabase {
    if ($DatabaseHost -notin @("localhost", "127.0.0.1", "::1")) {
        throw "Local PostgreSQL restore is restricted to loopback addresses."
    }
}

function Get-PostgresExecutable {
    param([Parameter(Mandatory)][string]$Name)
    if ($postgresExecutables.ContainsKey($Name)) { return $postgresExecutables[$Name] }
    $executableName = if ($env:OS -eq "Windows_NT") { "$Name.exe" } else { $Name }
    $executablePath = if ($PostgresBinDirectory) {
        Join-Path $PostgresBinDirectory $executableName
    } else {
        (Get-Command $executableName -ErrorAction Stop).Source
    }
    if (-not (Test-Path -LiteralPath $executablePath -PathType Leaf)) {
        throw "PostgreSQL executable '$executableName' was not found at '$executablePath'."
    }
    $postgresExecutables[$Name] = $executablePath
    return $executablePath
}

function Invoke-Postgres {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    $executablePath = Get-PostgresExecutable -Name $Name
    $result = & $executablePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
    return $result
}

try {
    if ($ConnectionMode -eq "Compose") {
        Assert-LocalDockerContext
        $running = Invoke-Compose -Arguments @("ps", "--status", "running", "-q", "db")
        if (-not $running) {
            throw "The FlowPay Compose database is not running. Start it with 'docker compose up -d db'."
        }

        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", 'test "$POSTGRES_DB" = "flowpay"')
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
    } else {
        Assert-LocalDatabase
        $connectionArgs = @("--host=$DatabaseHost", "--port=$DatabasePort", "--username=$DatabaseUsername")
        $null = Invoke-Postgres -Name "pg_restore" -Arguments @("--list", $resolvedBackupPath)
        $existing = Invoke-Postgres -Name "psql" -Arguments ($connectionArgs + @(
            "--dbname=postgres", "--tuples-only", "--no-align", "--set=ON_ERROR_STOP=1",
            "--command=SELECT 1 FROM pg_database WHERE datname = '$TargetDatabase'"
        ))
        if ($existing -match '\S') {
            throw "Target database '$TargetDatabase' already exists. Choose a new flowpay_restore_* name; existing databases are never overwritten."
        }

        $null = Invoke-Postgres -Name "createdb" -Arguments ($connectionArgs + @("--template=template0", $TargetDatabase))
        $createdTarget = $true
        $null = Invoke-Postgres -Name "pg_restore" -Arguments @(
            "--exit-on-error", "--no-owner", "--no-acl", "--host=$DatabaseHost",
            "--port=$DatabasePort", "--username=$DatabaseUsername", "--dbname=$TargetDatabase", $resolvedBackupPath
        )
        $migrationCount = Invoke-Postgres -Name "psql" -Arguments ($connectionArgs + @(
            "--dbname=$TargetDatabase", "--tuples-only", "--no-align", "--set=ON_ERROR_STOP=1",
            "--command=SELECT COUNT(*) FROM flyway_schema_history WHERE success"
        ))
    }

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
    if ($ConnectionMode -eq "Compose") {
        try {
            $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('rm -f "' + $containerPath + '"'))
        } catch {
            # The container may have stopped while the restore was running.
        }
    }
}
