[CmdletBinding()]
param([switch]$SeedTestAccounts,[switch]$SeedReferenceData)
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
Initialize-LocalJdbc
$receiptPath=Get-LocalReceiptPath
if(Test-Path -LiteralPath $receiptPath){throw 'INSTALL_RECEIPT_EXISTS_NO_OVERWRITE'}
$manifest=Get-Content -LiteralPath (Join-Path $LocalDbRoot 'database/migrations/manifest.json') -Raw|ConvertFrom-Json
Assert-LocalDatabasePackage $manifest
$result=Invoke-LocalJdbc 'install'
if($result -notcontains 'INSTALL_AND_STRUCTURAL_VERIFY_PASS'){throw 'INSTALL_NOT_VERIFIED'}
$identity=((Invoke-LocalJdbc 'identity') -join '')|ConvertFrom-Json
$receipt=[ordered]@{formatVersion=2;status='INSTALLED_AND_VERIFIED';database=$env:RH_DATABASE;machine=$env:COMPUTERNAME;identity=$identity;installedAt=[DateTimeOffset]::UtcNow.ToString('o');manifestVersion=$manifest.version;applied=$manifest.files;appliedMigrations=$manifest.migrations;historyMeaning='Local installer receipt, not SQL-resident history or upgrade authorization';seeded=$false}
New-Item -ItemType Directory -Path (Split-Path $receiptPath) -Force|Out-Null
$receipt|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $receiptPath -Encoding utf8
if($SeedTestAccounts){& (Join-Path $PSScriptRoot 'Seed-LocalDatabase.ps1');if($LASTEXITCODE -ne 0){throw 'OPTIONAL_SEED_FAILED'}}
if($SeedReferenceData){& (Join-Path $PSScriptRoot 'Seed-LocalReferenceData.ps1');if($LASTEXITCODE -ne 0){throw 'OPTIONAL_REFERENCE_SEED_FAILED'}}
'LOCAL_INSTALL_PASS; receipt stored outside repository; existing databases untouched'
