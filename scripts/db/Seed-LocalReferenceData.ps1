# Idempotent local catalog (stable boxes, skills) for a verified NEW local database. No secrets involved.
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
Initialize-LocalJdbc
if(-not(Test-Path -LiteralPath (Get-LocalReceiptPath))){throw 'VERIFIED_NEW_LOCAL_INSTALL_REQUIRED'}
$sql=Get-Content -LiteralPath (Join-Path $LocalDbRoot 'database/seeds/local-reference-data.sql') -Raw
$sql=$sql.Replace('__DATABASE__',$env:RH_DATABASE).Replace('__LOCAL_MACHINE__',$env:COMPUTERNAME)
$rendered=Join-Path ([IO.Path]::GetTempPath()) ('racehorse-reference-'+[guid]::NewGuid().ToString('N')+'.sql')
try {
    [IO.File]::WriteAllText($rendered,$sql,[Text.UTF8Encoding]::new($false))
    $result=Invoke-LocalJdbc 'reference' $rendered
    if($result -notcontains 'LOCAL_REFERENCE_SEED_PASS'){throw 'LOCAL_REFERENCE_SEED_NOT_VERIFIED'}
    'LOCAL_REFERENCE_SEED_PASS; stable boxes and skill catalog present'
} finally { Remove-Item -LiteralPath $rendered -Force -ErrorAction SilentlyContinue }
