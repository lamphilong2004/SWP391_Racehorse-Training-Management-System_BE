param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^racehorse_core007_test_[0-9]{8}_[0-9]{4}$')]
    [string]$Database,
    [ValidatePattern('^(localhost|127\.0\.0\.1)(\\[A-Za-z0-9_$-]+|,[0-9]{1,5})?$')]
    [string]$Server = 'localhost'
)

$ErrorActionPreference = 'Stop'
$seedSql = Join-Path $PSScriptRoot '..\..\database\seeds\core007-auth-smoke.sql'
$generatorSource = Join-Path $PSScriptRoot 'GenerateCore007SmokeCredentials.java'
$projectPom = Join-Path $PSScriptRoot '..\..\pom.xml'
$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("core007-auth-seed-" + [guid]::NewGuid().ToString('N'))
$credentialDirectory = Join-Path $env:LOCALAPPDATA 'Racehorse\Core007'
$credentialFile = Join-Path $credentialDirectory "AuthSmokeCredentials-$Database.dpapi.json"

foreach ($commandName in @('sqlcmd', 'mvn', 'javac', 'java')) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' is unavailable."
    }
}
if (-not $env:COMPUTERNAME -or $env:COMPUTERNAME -notmatch '^[A-Za-z0-9-]+$') {
    throw 'Cannot establish a safe local SQL Server machine name.'
}
if (Test-Path -LiteralPath $credentialFile) {
    throw "Protected credential file already exists: $credentialFile. Choose a new isolated database name."
}
New-Item -ItemType Directory -Path $credentialDirectory -Force | Out-Null

$databaseExists = & sqlcmd -S $Server -E -C -b -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID('$Database') IS NULL THEN 0 ELSE 1 END;"
if ($LASTEXITCODE -ne 0 -or [int]($databaseExists | Select-Object -First 1) -ne 1) {
    throw "Database '$Database' does not exist on the local SQL Server."
}

$identity = & sqlcmd -S $Server -E -C -b -d $Database -h -1 -W -s '|' -Q "SET NOCOUNT ON; SELECT DB_NAME(), CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128));"
if ($LASTEXITCODE -ne 0) {
    throw "Could not verify local SQL Server identity for '$Database'."
}
$identityParts = ($identity | Select-Object -First 1).Split('|')
if ($identityParts.Count -ne 2 -or $identityParts[0] -cne $Database -or
    $identityParts[1] -ine $env:COMPUTERNAME) {
    throw 'Connected database or physical SQL Server machine did not match this local seed target.'
}

New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
try {
    $classpathFile = Join-Path $temporaryDirectory 'classpath.txt'
    & mvn -q -f $projectPom '-DincludeScope=compile' "-Dmdep.outputFile=$classpathFile" dependency:build-classpath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $classpathFile)) {
        throw 'Maven could not resolve the compile classpath needed to generate BCrypt hashes.'
    }

    $classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $classesDirectory = Join-Path $temporaryDirectory 'classes'
    New-Item -ItemType Directory -Path $classesDirectory | Out-Null
    & javac -cp $classpath -d $classesDirectory $generatorSource
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not compile the temporary BCrypt credential generator.'
    }

    $javaClasspath = $classesDirectory + [System.IO.Path]::PathSeparator + $classpath
    $credentialLines = @(& java -cp $javaClasspath com.racehorse.tools.GenerateCore007SmokeCredentials)
    if ($LASTEXITCODE -ne 0 -or $credentialLines.Count -ne 7) {
        throw 'Could not generate all seven local test credentials.'
    }

    $hashes = @{}
    $credentialOutput = [System.Collections.Generic.List[object]]::new()
    foreach ($line in $credentialLines) {
        $parts = $line.Split('|')
        if ($parts.Count -ne 4 -or $parts[3] -notmatch '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$') {
            throw 'The BCrypt credential generator returned an invalid record.'
        }
        $password = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($parts[2]))
        if ($password.Length -ne 24 -or $password -notmatch '^[A-Za-z0-9]+$') {
            throw 'The generated smoke password did not meet its expected format.'
        }
        $hashes[$parts[0]] = $parts[3]
        $credentialOutput.Add([pscustomobject]@{
            role = $parts[0]
            email = $parts[1]
            encryptedPassword = (ConvertFrom-SecureString (ConvertTo-SecureString $password -AsPlainText -Force))
        })
        $password = $null
    }

    $seedContent = Get-Content -LiteralPath $seedSql -Raw
    $seedContent = $seedContent.Replace('__DATABASE__', $Database)
    $seedContent = $seedContent.Replace('__LOCAL_MACHINE__', $env:COMPUTERNAME)
    foreach ($role in $hashes.Keys) {
        $seedContent = $seedContent.Replace("__HASH_${role}__", $hashes[$role])
    }
    if ($seedContent -match '__HASH_[A-Z_]+__|__DATABASE__|__LOCAL_MACHINE__') {
        throw 'A required seed placeholder was not populated.'
    }

    $renderedSeed = Join-Path $temporaryDirectory 'core007-auth-smoke.sql'
    Set-Content -LiteralPath $renderedSeed -Value $seedContent -Encoding utf8
    & sqlcmd -S $Server -E -C -b -d $Database -i $renderedSeed
    if ($LASTEXITCODE -ne 0) {
        throw "The CORE-007 seed failed for '$Database'."
    }

    $credentialOutput | ConvertTo-Json | Set-Content -LiteralPath $credentialFile -Encoding utf8
    Write-Output "Seeded seven ACTIVE local accounts, one for each approved role."
    Write-Output "Passwords are DPAPI-protected for the current Windows user: $credentialFile"
    Write-Output 'Decrypt them only in that user session; transfer them to teammates only through an approved private secret manager.'
}
finally {
    if ($credentialLines) {
        [Array]::Clear($credentialLines, 0, $credentialLines.Count)
    }
    if ($hashes) {
        $hashes.Clear()
    }
    Remove-Variable credentialLines, password, line, parts, seedContent -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
