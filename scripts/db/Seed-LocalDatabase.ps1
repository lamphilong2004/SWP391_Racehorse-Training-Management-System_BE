# Reuses Dung's CORE-007 SQL and BCrypt generator; no CORE-011/demo seeder changes.
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'LocalDatabase.Common.ps1')
Initialize-LocalJdbc
$receiptPath=Get-LocalReceiptPath
if(-not(Test-Path -LiteralPath $receiptPath)){throw 'VERIFIED_NEW_LOCAL_INSTALL_REQUIRED'}
& (Join-Path $PSScriptRoot 'Verify-LocalDatabase.ps1')|Out-Null
$credentialFile=Join-Path (Split-Path $receiptPath) 'accounts.dpapi.json'
if(Test-Path -LiteralPath $credentialFile){throw 'LOCAL_CREDENTIALS_EXIST_NO_RESEED'}
$temp=Join-Path ([IO.Path]::GetTempPath()) ('racehorse-auth-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp|Out-Null
try {
    $cpFile=Join-Path $temp 'classpath.txt'
    & mvn -q -f (Join-Path $LocalDbRoot 'pom.xml') '-DincludeScope=compile' "-Dmdep.outputFile=$cpFile" dependency:build-classpath
    if($LASTEXITCODE -ne 0){throw 'BCRYPT_DEPENDENCIES_FAILED'}
    $cp=(Get-Content -LiteralPath $cpFile -Raw).Trim()
    & javac --release 21 -cp $cp -d $temp (Join-Path $PSScriptRoot 'GenerateCore007SmokeCredentials.java')
    if($LASTEXITCODE -ne 0){throw 'BCRYPT_GENERATOR_COMPILE_FAILED'}
    $records=@(& java -cp ($temp+[IO.Path]::PathSeparator+$cp) com.racehorse.tools.GenerateCore007SmokeCredentials)
    if($LASTEXITCODE -ne 0 -or $records.Count -ne 7){throw 'SEVEN_LOCAL_CREDENTIALS_REQUIRED'}
    $sql=Get-Content -LiteralPath (Join-Path $LocalDbRoot 'database/seeds/core007-auth-smoke.sql') -Raw
    $sql=$sql.Replace('__DATABASE__',$env:RH_DATABASE).Replace('__LOCAL_MACHINE__',$env:COMPUTERNAME)
    $accounts=@()
    foreach($record in $records){
        $parts=$record.Split('|')
        if($parts.Count -ne 4 -or $parts[0] -notmatch '^[A-Z_]+$' -or $parts[3] -notmatch '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'){throw 'BCRYPT_RECORD_INVALID'}
        $password=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[2]))
        $accounts+=@{role=$parts[0];email=$parts[1];encryptedPassword=(ConvertFrom-SecureString (ConvertTo-SecureString $password -AsPlainText -Force))}
        $sql=$sql.Replace(('__HASH_'+$parts[0]+'__'),$parts[3]);$password=$null
    }
    if($sql -match '__HASH_|__DATABASE__|__LOCAL_MACHINE__'){throw 'SEED_PLACEHOLDER_UNRESOLVED'}
    $rendered=Join-Path $temp 'seed.sql';[IO.File]::WriteAllText($rendered,$sql,[Text.UTF8Encoding]::new($false))
    Invoke-LocalJdbc 'seed' $rendered|Out-Null
    $accounts|ConvertTo-Json|Set-Content -LiteralPath $credentialFile -Encoding utf8
    $receipt=Get-Content -LiteralPath $receiptPath -Raw|ConvertFrom-Json;$receipt.seeded=$true
    $receipt|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $receiptPath -Encoding utf8
    'OPTIONAL_TEST_ACCOUNTS_SEEDED; DPAPI current-user credentials outside Git; no passwords printed'
} finally {
    $password=$null;$records=$null;$accounts=$null;$sql=$null
    $resolved=[IO.Path]::GetFullPath($temp);$tempRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if($resolved.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolved -Leaf) -match '^racehorse-auth-[a-f0-9]{32}$'){Remove-Item -LiteralPath $resolved -Recurse -Force}
}
