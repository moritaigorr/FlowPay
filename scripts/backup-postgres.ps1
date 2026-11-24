param(
    [string]$OutputDirectory = "backups",
    [ValidateSet("Compose", "Local")]
    [string]$ConnectionMode = "Compose",
    [string]$PostgresBinDirectory = "",
    [string]$DatabaseHost = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 5432,
    [string]$DatabaseUsername = "flowpay_dev",
    [string]$DatabaseName = "flowpay"
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
        throw "Backup is restricted to a local Docker context; the active Docker endpoint is not a local socket."
    }
}

function Assert-LocalDatabase {
    if ($DatabaseName -ne "flowpay") {
        throw "Backup is restricted to the local FlowPay database named 'flowpay'."
    }
    if ($DatabaseHost -notin @("localhost", "127.0.0.1", "::1")) {
        throw "Local PostgreSQL backup is restricted to loopback addresses."
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

        $databaseCheck = 'test "$POSTGRES_DB" = "flowpay"'
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $databaseCheck)

        $dumpCommand = 'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --file="' + $containerPath + '"'
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", $dumpCommand)
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('test -s "' + $containerPath + '"'))
        $null = Invoke-Compose -Arguments @("exec", "-T", "db", "pg_restore", "--list", $containerPath)
        $null = Invoke-Compose -Arguments @("cp", "db:$containerPath", $partialPath)
    } else {
        Assert-LocalDatabase
        $null = Invoke-Postgres -Name "pg_dump" -Arguments @(
            "--format=custom", "--no-owner", "--no-acl", "--host=$DatabaseHost",
            "--port=$DatabasePort", "--username=$DatabaseUsername", "--dbname=$DatabaseName",
            "--file=$partialPath"
        )
        $null = Invoke-Postgres -Name "pg_restore" -Arguments @("--list", $partialPath)
    }

    $backupInfo = Get-Item -LiteralPath $partialPath
    if ($backupInfo.Length -eq 0) {
        throw "PostgreSQL produced an empty backup."
    }
    Move-Item -LiteralPath $partialPath -Destination $backupPath
    Write-Output "Backup created: $backupPath ($($backupInfo.Length) bytes)"
} finally {
    Remove-Item -LiteralPath $partialPath -Force -ErrorAction SilentlyContinue
    if ($ConnectionMode -eq "Compose") {
        try {
            $null = Invoke-Compose -Arguments @("exec", "-T", "db", "sh", "-lc", ('rm -f "' + $containerPath + '"'))
        } catch {
            # The container may have stopped while the backup was running.
        }
    }
}
