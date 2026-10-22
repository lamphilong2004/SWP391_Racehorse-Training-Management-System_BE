# Starts the packaged backend against YOUR new local database with strict JDBC TLS and JPA validate.
# Requires the RH_* variables from docs/examples/local-first.env.example in the current shell.
# The JWT signing key is generated once and kept DPAPI-protected beside the install receipt; never printed.
[CmdletBinding()]
param(
    [int]$Port = 8080,
    [string[]]$FrontendOrigins = @('http://localhost:5173', 'http://127.0.0.1:5173'),
    [string]$Jar = ''
)
$ErrorActionPreference = 'Stop'
foreach ($name in 'RH_DATABASE', 'RH_SQL_HOST', 'RH_SQL_PORT', 'RH_SQL_CERT_HOST', 'RH_SQL_TRUSTSTORE', 'RH_SQL_TRUSTSTORE_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "ENV_REQUIRED_$name" }
}
if ($env:RH_DATABASE -cnotmatch '^racehorse_local_[a-z0-9_]{1,70}$') { throw 'NEW_LOCAL_DATABASE_NAME_REQUIRED' }
$localDir = Join-Path $env:LOCALAPPDATA "Racehorse/LocalDb/$env:RH_DATABASE"
if (-not (Test-Path -LiteralPath (Join-Path $localDir 'receipt.json'))) { throw 'VERIFIED_NEW_LOCAL_INSTALL_REQUIRED' }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "PORT_${Port}_IN_USE_NO_PROCESS_STOPPED" }
if (-not $Jar) { $Jar = Join-Path $PSScriptRoot '../../target/racehorse-backend-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path -LiteralPath $Jar)) { throw 'PACKAGED_JAR_REQUIRED_RUN_mvn_-DskipTests_package' }

$keyPath = Join-Path $localDir 'jwt-key.dpapi'
if (-not (Test-Path -LiteralPath $keyPath)) {
    $bytes = [byte[]]::new(48); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    ConvertFrom-SecureString (ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force) | Set-Content -LiteralPath $keyPath
}
$private = Join-Path $localDir 'runtime'
$auth = if ($env:DB_USERNAME) { '' } else { ';integratedSecurity=true' }
$env:JWT_SIGNING_KEY = [Net.NetworkCredential]::new('', ((Get-Content -LiteralPath $keyPath -Raw).Trim() | ConvertTo-SecureString)).Password
$env:JWT_ACCESS_TOKEN_TTL_SECONDS = if ($env:JWT_ACCESS_TOKEN_TTL_SECONDS) { $env:JWT_ACCESS_TOKEN_TTL_SECONDS } else { '14400' }
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = "jdbc:sqlserver://$($env:RH_SQL_HOST):$($env:RH_SQL_PORT);databaseName=$env:RH_DATABASE;encrypt=true;trustServerCertificate=false;hostNameInCertificate=$env:RH_SQL_CERT_HOST$auth"
$env:CORS_ALLOWED_ORIGINS = $FrontendOrigins -join ','
$env:SPRING_APPLICATION_JSON = @{
    server = @{ address = '127.0.0.1'; port = $Port }
    spring = @{ jpa = @{ hibernate = @{ 'ddl-auto' = 'validate' } }; sql = @{ init = @{ mode = 'never' } }
        datasource = @{ hikari = @{ 'maximum-pool-size' = 4; 'data-source-properties' = @{ trustStore = $env:RH_SQL_TRUSTSTORE; trustStoreType = 'PKCS12'; trustStorePassword = $env:RH_SQL_TRUSTSTORE_PASSWORD } } } }
    app = @{ account = @{ 'reset-outbox-directory' = (Join-Path $private 'password-reset-outbox') }; realtime = @{ simulator = @{ enabled = $false } }
        storage = @{ local = @{ directory = (Join-Path $private 'horse-photos') } } }
} | ConvertTo-Json -Depth 12 -Compress
$javaArgs = @('-Xmx384m', '-Duser.timezone=UTC', '--enable-native-access=ALL-UNNAMED')
if ($env:RH_JDBC_NATIVE_DIR) { $javaArgs += "-Djava.library.path=$env:RH_JDBC_NATIVE_DIR" }
try {
    & java @javaArgs -jar $Jar
} finally {
    $env:JWT_SIGNING_KEY = $null; $env:SPRING_APPLICATION_JSON = $null
}
