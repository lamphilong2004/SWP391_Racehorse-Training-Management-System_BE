# Shared strict-TLS transport. Connection secrets are environment-only and never printed.
$ErrorActionPreference='Stop'
$LocalDbRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Initialize-LocalJdbc {
    foreach($name in @('RH_DATABASE','RH_SQL_HOST','RH_SQL_PORT','RH_SQL_CERT_HOST','RH_SQL_TRUSTSTORE','RH_SQL_TRUSTSTORE_PASSWORD','RH_JDBC_DRIVER')) {
        if(-not [Environment]::GetEnvironmentVariable($name)){throw "ENV_REQUIRED_$name"}
    }
    if($env:RH_DATABASE -cnotmatch '^racehorse_local_[a-z0-9_]{1,70}$'){throw 'NEW_LOCAL_DATABASE_NAME_REQUIRED'}
    if(-not(Test-Path -LiteralPath $env:RH_SQL_TRUSTSTORE -PathType Leaf) -or -not(Test-Path -LiteralPath $env:RH_JDBC_DRIVER -PathType Leaf)){throw 'DRIVER_AND_DEDICATED_TRUSTSTORE_REQUIRED'}
    $script:LocalDbClasses=Join-Path $LocalDbRoot 'target/local-db-tools'
    New-Item -ItemType Directory -Path $script:LocalDbClasses -Force|Out-Null
    & javac --release 21 -d $script:LocalDbClasses (Join-Path $PSScriptRoot 'LocalDatabase.java')
    if($LASTEXITCODE -ne 0){throw 'LOCAL_DB_TOOL_COMPILE_FAILED'}
}
function Invoke-LocalJdbc([string]$Operation,[string]$InputFile='') {
    $classpath=$script:LocalDbClasses+[IO.Path]::PathSeparator+$env:RH_JDBC_DRIVER
    $argsLocal=@('--enable-native-access=ALL-UNNAMED')
    if($env:RH_JDBC_NATIVE_DIR){$argsLocal+=('-Djava.library.path='+$env:RH_JDBC_NATIVE_DIR)}
    $argsLocal+=@('-cp',$classpath,'LocalDatabase',$Operation,$LocalDbRoot)
    if($InputFile){$argsLocal+=$InputFile}
    $output=@(& java @argsLocal)
    if($LASTEXITCODE -ne 0){throw "LOCAL_JDBC_$($Operation.ToUpperInvariant())_FAILED"}
    return $output
}
function Get-LocalReceiptPath {
    Join-Path $env:LOCALAPPDATA ('Racehorse/LocalDb/'+$env:RH_DATABASE+'/receipt.json')
}

function Assert-LocalDatabasePackage([object]$Manifest) {
    if($Manifest.schema -cne 'database/schema.sql' -or $Manifest.collation -cne 'SQL_Latin1_General_CP1_CI_AS'){throw 'CANONICAL_PACKAGE_IDENTITY_MISMATCH'}
    $seen=@{}
    foreach($file in $Manifest.files){
        if($seen.ContainsKey($file.path) -or $file.path -notmatch '^(database/|scripts/db/)[A-Za-z0-9_./-]+$' -or $file.path.Contains('..')){throw 'PACKAGE_PATH_INVALID_OR_DUPLICATED'}
        $seen[$file.path]=$file.sha256
        if((Get-FileHash -LiteralPath (Join-Path $LocalDbRoot $file.path) -Algorithm SHA256).Hash -ine $file.sha256){throw 'PACKAGE_CHECKSUM_MISMATCH'}
    }
    $plan=@(((Invoke-LocalJdbc 'plan') -join '')|ConvertFrom-Json)
    if(@($Manifest.migrations).Count -ne $plan.Count){throw 'MIGRATION_PLAN_COUNT_MISMATCH'}
    for($i=0;$i -lt $plan.Count;$i++){
        $actual=$Manifest.migrations[$i];$expected=$plan[$i]
        if($actual.order -ne $expected.order -or $actual.version -cne $expected.version -or $actual.path -cne $expected.path -or $seen[$actual.path] -ine $expected.sha256){throw 'MIGRATION_PLAN_MISMATCH'}
    }
    if($Manifest.verification.path -cne 'database/verification/expected-schema-v20261027.json' -or $seen[$Manifest.verification.path] -ine $Manifest.verification.sha256){throw 'VERIFICATION_MANIFEST_MISMATCH'}
}
