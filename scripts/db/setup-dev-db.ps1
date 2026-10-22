# Compatibility entry point; CORE-007 seed SQL/generator ownership stays with Dung.
param([string]$Database=$env:RH_DATABASE,[string]$Server=$env:RH_SQL_HOST,[switch]$UseWindowsAuth)
$ErrorActionPreference='Stop'
if($Database -cnotmatch '^racehorse_local_[a-z0-9_]{1,70}$'){throw 'Use a NEW racehorse_local_* database; see docs/LOCAL-FIRST-DATABASE.md'}
if($Server -and $Server -cne $env:RH_SQL_HOST){throw 'Server must match explicit RH_SQL_HOST environment'}
$env:RH_DATABASE=$Database
& (Join-Path $PSScriptRoot 'Install-LocalDatabase.ps1')
