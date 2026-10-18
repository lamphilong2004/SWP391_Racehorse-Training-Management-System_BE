[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
Initialize-LocalJdbc
$manifest=Get-Content -LiteralPath (Join-Path $LocalDbRoot 'database/migrations/manifest.json') -Raw|ConvertFrom-Json
foreach($file in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $LocalDbRoot $file.path) -Algorithm SHA256).Hash -ine $file.sha256){throw 'PACKAGE_CHECKSUM_MISMATCH'}}
Invoke-LocalJdbc 'verify'
$receiptPath=Get-LocalReceiptPath
if(-not(Test-Path -LiteralPath $receiptPath)){throw 'LOCAL_RECEIPT_MISSING_STRUCTURAL_RESULT_ONLY'}
$receipt=Get-Content -LiteralPath $receiptPath -Raw|ConvertFrom-Json
if($receipt.database -cne $env:RH_DATABASE -or $receipt.machine -ine $env:COMPUTERNAME -or $receipt.status -cne 'INSTALLED_AND_VERIFIED' -or $receipt.manifestVersion -cne $manifest.version){throw 'LOCAL_RECEIPT_IDENTITY_OR_VERSION_MISMATCH'}
if(($receipt.applied|ConvertTo-Json -Compress) -cne ($manifest.files|ConvertTo-Json -Compress)){throw 'LOCAL_RECEIPT_MANIFEST_MISMATCH'}
'LOCAL_RECEIPT_AND_LIVE_SCHEMA_PASS; no upgrade inferred'
Invoke-LocalJdbc 'identity'
