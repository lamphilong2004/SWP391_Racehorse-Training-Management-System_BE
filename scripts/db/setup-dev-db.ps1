<#
  Creates the local development database "racehorse_dev" and applies the approved schema (DB-01, 51 tables).

  - The schema file is verified against database/schema.sql.sha256 before it runs, so the applied schema
    is byte-identical to the approved canonical file (project-docs/database/schema.sql).
  - Credentials are never stored here. Use Windows authentication (-UseWindowsAuth) or pass a SQL login
    through the environment variables DB_USERNAME / DB_PASSWORD.
  - Refuses to run if racehorse_dev already contains tables (the schema script must run on an empty database).

  Usage (from the repository root):
    powershell -ExecutionPolicy Bypass -File scripts/db/setup-dev-db.ps1 -UseWindowsAuth
    powershell -ExecutionPolicy Bypass -File scripts/db/setup-dev-db.ps1 -Server "localhost,1433"
#>
param(
    [string]$Server = "localhost",
    [string]$Database = "racehorse_dev",
    [switch]$UseWindowsAuth
)
$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$schema = Join-Path $repoRoot 'database\schema.sql'
$expected = ((Get-Content (Join-Path $repoRoot 'database\schema.sql.sha256')) -split '\s+')[0]
$actual = (Get-FileHash -Algorithm SHA256 $schema).Hash.ToLower()
if ($actual -ne $expected) { throw "schema.sql checksum mismatch (expected $expected, got $actual). Do not edit the approved schema." }

if ($UseWindowsAuth) { $auth = @('-E') }
else {
    if (-not $env:DB_USERNAME -or -not $env:DB_PASSWORD) { throw "Set DB_USERNAME and DB_PASSWORD, or use -UseWindowsAuth." }
    $auth = @('-U', $env:DB_USERNAME, '-P', $env:DB_PASSWORD)
}
$common = @('-S', $Server, '-C', '-b') + $auth

& sqlcmd @common -Q "IF DB_ID('$Database') IS NULL CREATE DATABASE [$Database];"
if ($LASTEXITCODE -ne 0) { throw "Could not create database $Database." }

$count = & sqlcmd @common -d $Database -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables;"
if ([int]($count | Select-Object -First 1) -gt 0) { throw "$Database already has tables; the approved schema must be applied to an empty database." }

& sqlcmd @common -d $Database -i $schema
if ($LASTEXITCODE -ne 0) { throw "Applying schema.sql failed." }

$tables = & sqlcmd @common -d $Database -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables;"
"Applied approved schema to $Database on $Server. Tables: $($tables | Select-Object -First 1) (expected 51)."
