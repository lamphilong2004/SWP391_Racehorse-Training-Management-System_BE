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
