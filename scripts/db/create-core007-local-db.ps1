param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^racehorse_core007_test_[0-9]{8}_[0-9]{4}$')]
    [string]$Database,
    [ValidatePattern('^(localhost|127\.0\.0\.1)(\\[A-Za-z0-9_$-]+|,[0-9]{1,5})?$')]
    [string]$Server = 'localhost'
)

$ErrorActionPreference = 'Stop'

foreach ($commandName in @('sqlcmd', 'powershell')) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' is unavailable."
    }
}
if (-not $env:COMPUTERNAME -or $env:COMPUTERNAME -notmatch '^[A-Za-z0-9-]+$') {
    throw 'Cannot establish a safe local SQL Server machine name.'
}

$serverMachine = & sqlcmd -S $Server -E -C -b -h -1 -W -Q "SET NOCOUNT ON; SELECT CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128));"
if ($LASTEXITCODE -ne 0 -or ($serverMachine | Select-Object -First 1).Trim() -ine $env:COMPUTERNAME) {
    throw 'The selected SQL Server is not confirmed to be on this local machine.'
}

$databaseExists = & sqlcmd -S $Server -E -C -b -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID('$Database') IS NULL THEN 0 ELSE 1 END;"
if ($LASTEXITCODE -ne 0) {
    throw "Could not check whether database '$Database' already exists."
}
if ([int]($databaseExists | Select-Object -First 1) -ne 0) {
    throw "Database '$Database' already exists. Choose a new unique name; this script never drops or resets a database."
}

$setupScript = Join-Path $PSScriptRoot 'setup-dev-db.ps1'
& powershell -NoProfile -ExecutionPolicy Bypass -File $setupScript -Server $Server -Database $Database -UseWindowsAuth
if ($LASTEXITCODE -ne 0) {
    throw "The approved schema setup failed for '$Database'."
}

$tableCount = & sqlcmd -S $Server -E -C -b -d $Database -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables;"
if ($LASTEXITCODE -ne 0 -or [int]($tableCount | Select-Object -First 1) -ne 51) {
    throw "Expected 51 canonical schema tables in '$Database'."
}

Write-Output "Created '$Database' on confirmed local server '$Server' and applied the checksum-verified canonical schema (51 tables)."
