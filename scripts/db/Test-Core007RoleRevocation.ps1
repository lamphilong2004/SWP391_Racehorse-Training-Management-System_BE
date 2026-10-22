param(
    [ValidatePattern('^racehorse_core007_test_[0-9]{8}_[0-9]{4}$')]
    [string]$Database = 'racehorse_core007_test_20261017_0041',
    [ValidatePattern('^(localhost|127\.0\.0\.1)(\\[A-Za-z0-9_$-]+|,[0-9]{1,5})?$')]
    [string]$Server = 'localhost',
    [Parameter(Mandatory = $true)]
    [uri]$ApiBaseUri,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[^@\s]+@[^@\s]+$')]
    [string]$Email,
    [ValidateSet('CLUB_MANAGER', 'HEAD_TRAINER', 'TRAINER', 'WORK_RIDER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER')]
    [string]$RoleToRevoke = 'CLUB_MANAGER',
    [ValidateSet('CLUB_MANAGER', 'HEAD_TRAINER', 'TRAINER', 'WORK_RIDER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER')]
    [string]$RemainingRole = 'TRAINER',
    [string]$CredentialFile,
    [string]$ReportPath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$schemaPath = Join-Path $repoRoot 'database\schema.sql'
$migrationPath = Join-Path $repoRoot 'database\migrations\V20261016_01__create_registration_request.sql'
$expectedSchemaHash = '416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141'
$expectedMigrationHash = '18A5D3EBA5AAD92967C6A6D2F6646410A98828D69E3C5BE8FA3C61A7EF418604'
$roleCodes = @('CLUB_MANAGER', 'HEAD_TRAINER', 'TRAINER', 'WORK_RIDER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER')
$timestamp = [DateTime]::UtcNow
if (-not $ReportPath) {
    $ReportPath = Join-Path $repoRoot ("docs\evidence\CORE-007-role-revocation-{0}.json" -f $timestamp.ToString('yyyyMMdd-HHmmss'))
}
elseif (-not [IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $repoRoot $ReportPath
}

if (-not $CredentialFile) {
    $CredentialFile = Join-Path $env:LOCALAPPDATA "Racehorse\Core007\AuthSmokeCredentials-$Database.dpapi.json"
}
if ($ApiBaseUri.Scheme -ne 'http' -or -not $ApiBaseUri.IsLoopback) {
    throw 'The API URL must use HTTP on localhost or a loopback IP for this isolated local test.'
}
if ($RoleToRevoke -ne 'CLUB_MANAGER' -or $RemainingRole -eq $RoleToRevoke) {
    throw 'This check exercises the implemented CLUB_MANAGER matcher and requires a different remaining role.'
}
if (-not $env:COMPUTERNAME) {
    throw 'Cannot establish the local workstation identity.'
}

function New-SqlParameter {
    param(
        [string]$Name,
        [System.Data.SqlDbType]$Type,
        [object]$Value,
        [int]$Size = 0
    )
    $parameter = [System.Data.SqlClient.SqlParameter]::new()
    $parameter.ParameterName = $Name
    $parameter.SqlDbType = $Type
    if ($Size -gt 0) {
        $parameter.Size = $Size
    }
    if ($Type -eq [System.Data.SqlDbType]::DateTime2) {
        $parameter.Scale = 3
    }
    if ($null -eq $Value) {
        $parameter.Value = [DBNull]::Value
    }
    else {
        $parameter.Value = $Value
    }
    return $parameter
}

function New-SqlCommand {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Text,
        [System.Data.SqlClient.SqlParameter[]]$Parameters = @()
    )
    $command = $Connection.CreateCommand()
    $command.CommandText = $Text
    $command.CommandTimeout = 15
    foreach ($parameter in $Parameters) {
        if ($parameter -isnot [System.Data.SqlClient.SqlParameter]) {
            throw 'The SQL command received an untyped parameter.'
        }
        [void]$command.Parameters.Add([object]$parameter)
    }
    return $command
}

function Invoke-SqlRows {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Text,
        [System.Data.SqlClient.SqlParameter[]]$Parameters = @()
    )
    $command = New-SqlCommand -Connection $Connection -Text $Text -Parameters $Parameters
    try {
        $reader = $command.ExecuteReader()
        try {
            $rows = [Collections.Generic.List[object]]::new()
            while ($reader.Read()) {
                if ($reader.IsDBNull($reader.GetOrdinal('assigned_by_user_id'))) {
                    $assignedByUserId = $null
                }
                else {
                    $assignedByUserId = [long]$reader['assigned_by_user_id']
                }
                if ($reader.IsDBNull($reader.GetOrdinal('updated_at'))) {
                    $updatedAt = $null
                }
                else {
                    $updatedAt = [DateTime]$reader['updated_at']
                }
                if ($reader.IsDBNull($reader.GetOrdinal('assigned_at'))) {
                    $assignedAt = $null
                }
                else {
                    $assignedAt = [DateTime]$reader['assigned_at']
                }
                if ($reader.IsDBNull($reader.GetOrdinal('created_at'))) {
                    $createdAt = $null
                }
                else {
                    $createdAt = [DateTime]$reader['created_at']
                }
                $rows.Add([pscustomobject]@{
                    Id = [long]$reader['id']
                    UserId = [long]$reader['user_id']
                    RoleId = [long]$reader['role_id']
                    RoleCode = [string]$reader['role_code']
                    AssignedByUserId = $assignedByUserId
                    AssignedAt = $assignedAt
                    CreatedAt = $createdAt
                    UpdatedAt = $updatedAt
                })
            }
            return $rows.ToArray()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $command.Dispose()
    }
}

function Invoke-SqlScalar {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Text,
        [System.Data.SqlClient.SqlParameter[]]$Parameters = @()
    )
    $command = New-SqlCommand -Connection $Connection -Text $Text -Parameters $Parameters
    try {
        return $command.ExecuteScalar()
    }
    finally {
        $command.Dispose()
    }
}

function Invoke-SqlNonQuery {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Text,
        [System.Data.SqlClient.SqlParameter[]]$Parameters = @()
    )
    $command = New-SqlCommand -Connection $Connection -Text $Text -Parameters $Parameters
    try {
        return $command.ExecuteNonQuery()
    }
    finally {
        $command.Dispose()
    }
}

function Get-MembershipSignature {
    param([object[]]$Rows)
    $signatures = [Collections.Generic.List[string]]::new()
    foreach ($row in $Rows) {
        $assignedByValue = @($row.AssignedByUserId)
        $updatedAtValue = @($row.UpdatedAt)
        $assignedBy = if ($assignedByValue.Count -eq 0 -or $null -eq $assignedByValue[0]) {
            'NULL'
        }
        else {
            ([long]$assignedByValue[0]).ToString([Globalization.CultureInfo]::InvariantCulture)
        }
        $updatedAt = if ($updatedAtValue.Count -eq 0 -or $null -eq $updatedAtValue[0]) {
            'NULL'
        }
        else {
            ([DateTime]$updatedAtValue[0]).Ticks
        }
        $signatures.Add(('{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}' -f `
            $row.Id, $row.UserId, $row.RoleId, $row.RoleCode, $assignedBy, `
            $row.AssignedAt.Ticks, $row.CreatedAt.Ticks, $updatedAt))
    }
    return [string]::Join("`n", $signatures.ToArray())
}

function Invoke-LocalApi {
    param(
        [System.Net.Http.HttpClient]$Client,
        [string]$Method,
        [string]$Path,
        [string]$Body,
        [string]$BearerToken
    )
    $uri = [uri]::new($ApiBaseUri, $Path.TrimStart('/'))
    $httpMethod = New-Object System.Net.Http.HttpMethod($Method)
    $request = New-Object System.Net.Http.HttpRequestMessage($httpMethod, $uri)
    if ($Body) {
        $request.Content = New-Object System.Net.Http.StringContent($Body, [Text.Encoding]::UTF8, 'application/json')
    }
    if ($BearerToken) {
        $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $BearerToken)
    }
    try {
        $response = $Client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            return [pscustomobject]@{
                StatusCode = [int]$response.StatusCode
                Content = $content
            }
        }
        finally {
            $response.Dispose()
        }
    }
    finally {
        $request.Dispose()
    }
}

function Add-ExactMembership {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [object]$Membership
    )
    $parameters = @(
        (New-SqlParameter '@id' ([System.Data.SqlDbType]::BigInt) $Membership.Id),
        (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $Membership.UserId),
        (New-SqlParameter '@roleId' ([System.Data.SqlDbType]::BigInt) $Membership.RoleId),
        (New-SqlParameter '@assignedBy' ([System.Data.SqlDbType]::BigInt) $Membership.AssignedByUserId),
        (New-SqlParameter '@assignedAt' ([System.Data.SqlDbType]::DateTime2) $Membership.AssignedAt),
        (New-SqlParameter '@createdAt' ([System.Data.SqlDbType]::DateTime2) $Membership.CreatedAt),
        (New-SqlParameter '@updatedAt' ([System.Data.SqlDbType]::DateTime2) $Membership.UpdatedAt)
    )
    $insert = @'
SET IDENTITY_INSERT dbo.user_role ON;
INSERT INTO dbo.user_role
    (id, user_id, role_id, assigned_by_user_id, assigned_at, created_at, updated_at)
VALUES
    (@id, @userId, @roleId, @assignedBy, @assignedAt, @createdAt, @updatedAt);
SET IDENTITY_INSERT dbo.user_role OFF;
'@
    [void](Invoke-SqlNonQuery -Connection $Connection -Text $insert -Parameters $parameters)
}

$report = [ordered]@{
    task = 'CORE-007'
    test = 'live-jwt-role-revocation-and-exact-restore'
    startedAtUtc = $timestamp.ToString('o')
    finishedAtUtc = $null
    branch = $null
    head = $null
    database = $Database
    sqlServerPhysicalHost = $null
    sqlServerName = $null
    sqlServerVersion = $null
    accountId = $null
    roleRevoked = $RoleToRevoke
    remainingRole = $RemainingRole
    schemaSha256 = $null
    migrationSha256 = $null
    initialMembershipRows = $null
    temporaryRemainingRoleAdded = $false
    clubManagerAccessBeforeStatus = $null
    revokeDeletedRows = 0
    revokedRoleAdminProbeStatus = $null
    remainingRoleMeStatus = $null
    remainingRolePresentAfterRevoke = $false
    targetRoleRestoreInsertedRows = 0
    restoredRoleAdminProbeStatus = $null
    restoredRoleMeStatus = $null
    temporaryRoleCleanupRows = 0
    finalMembershipRows = $null
    snapshotExactlyRestored = $false
    outcome = 'INCOMPLETE'
    failurePhase = $null
    failureType = $null
    failureLine = $null
    cleanupFailureType = $null
    cleanupFailureLine = $null
    businessEndpointLimitation = 'No role-protected business controller exists. The /api/admin/__core007-role-revocation-probe path is deliberately unmapped; 403 versus 404 verifies only the existing CLUB_MANAGER security matcher. /api/auth/me verifies the retained role in the DB-backed current-user response, not a role-exclusive business permission.'
}

$connection = $null
$httpClient = $null
$snapshot = @()
$userId = $null
$targetRoleId = $null
$remainingRoleId = $null
$remainingRoleInitiallyPresent = $false
$snapshotLoaded = $false
$targetRoleWasDeleted = $false
$token = $null
$password = $null
$securePassword = $null
$failurePhase = 'preflight'
$mainFailure = $null
$cleanupFailure = $null
$probePath = '/api/admin/__core007-role-revocation-probe'
$dbParameters = @()

try {
    $branch = (& git -C $repoRoot branch --show-current).Trim()
    $head = (& git -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'feat/CORE-007-auth-be') {
        throw 'The expected CORE-007 branch could not be confirmed.'
    }
    $report.branch = $branch
    $report.head = $head

    $schemaHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $schemaPath).Hash.ToLowerInvariant()
    $migrationHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $migrationPath).Hash.ToUpperInvariant()
    if ($schemaHash -ne $expectedSchemaHash -or $migrationHash -ne $expectedMigrationHash) {
        throw 'The approved schema or migration checksum does not match.'
    }
    $report.schemaSha256 = $schemaHash
    $report.migrationSha256 = $migrationHash

    $connectionString = "Data Source=$Server;Initial Catalog=$Database;Integrated Security=SSPI;Encrypt=True;TrustServerCertificate=False;Application Name=CORE007-Role-Revocation"
    $connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)
    $connection.Open()
    $identityCommand = $connection.CreateCommand()
    $identityCommand.CommandText = @'
SELECT CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128)) AS physical_host,
       CAST(@@SERVERNAME AS nvarchar(128)) AS server_name,
       CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
       CAST(DB_NAME() AS nvarchar(128)) AS database_name
'@
    try {
        $identityReader = $identityCommand.ExecuteReader()
        try {
            if (-not $identityReader.Read()) {
                throw 'SQL Server did not return its physical host and database identity.'
            }
            $physicalHost = [string]$identityReader['physical_host']
            $serverName = [string]$identityReader['server_name']
            $productVersion = [string]$identityReader['product_version']
            $databaseName = [string]$identityReader['database_name']
        }
        finally {
            $identityReader.Dispose()
        }
    }
    finally {
        $identityCommand.Dispose()
    }
    if ($physicalHost -ine $env:COMPUTERNAME -or
        $databaseName -cne $Database -or
        $Database -in @('HSF302', 'racehorse_demo', 'racehorse_dev')) {
        throw 'SQL Server physical host or active database does not match the isolated local target.'
    }
    $report.sqlServerPhysicalHost = $physicalHost
    $report.sqlServerVersion = $productVersion
    $report.sqlServerName = $serverName

    $userRows = @(Invoke-SqlRows -Connection $connection -Text @'
SELECT
    u.id,
    CAST(0 AS bigint) AS user_id,
    CAST(0 AS bigint) AS role_id,
    CAST('' AS varchar(30)) AS role_code,
    CAST(NULL AS bigint) AS assigned_by_user_id,
    CAST(NULL AS datetime2(3)) AS assigned_at,
    CAST(NULL AS datetime2(3)) AS created_at,
    CAST(NULL AS datetime2(3)) AS updated_at
FROM dbo.app_user u
WHERE u.email = @email AND u.account_status = 'ACTIVE'
'@ -Parameters @((New-SqlParameter '@email' ([System.Data.SqlDbType]::NVarChar) $Email 254)))
    if ($userRows.Count -ne 1) {
        throw 'The selected active account was not uniquely found in the isolated database.'
    }
    $userId = $userRows[0].Id
    $report.accountId = $userId

    $roleRows = @(Invoke-SqlRows -Connection $connection -Text @'
SELECT
    r.id,
    CAST(0 AS bigint) AS user_id,
    CAST(0 AS bigint) AS role_id,
    r.role_code,
    CAST(NULL AS bigint) AS assigned_by_user_id,
    CAST(NULL AS datetime2(3)) AS assigned_at,
    CAST(NULL AS datetime2(3)) AS created_at,
    CAST(NULL AS datetime2(3)) AS updated_at
FROM dbo.role r
WHERE r.role_code IN (@revokeRole, @remainingRole)
'@ -Parameters @(
        (New-SqlParameter '@revokeRole' ([System.Data.SqlDbType]::VarChar) $RoleToRevoke 30),
        (New-SqlParameter '@remainingRole' ([System.Data.SqlDbType]::VarChar) $RemainingRole 30)
    ))
    $targetRole = $roleRows | Where-Object RoleCode -eq $RoleToRevoke | Select-Object -First 1
    $remainingRoleRow = $roleRows | Where-Object RoleCode -eq $RemainingRole | Select-Object -First 1
    if (-not $targetRole -or -not $remainingRoleRow) {
        throw 'The selected approved roles are not both present in the isolated database.'
    }
    $targetRoleId = $targetRole.Id
    $remainingRoleId = $remainingRoleRow.Id

    $membershipQuery = @'
SELECT ur.id, ur.user_id, ur.role_id, r.role_code, ur.assigned_by_user_id,
       ur.assigned_at, ur.created_at, ur.updated_at
FROM dbo.user_role ur
JOIN dbo.role r ON r.id = ur.role_id
WHERE ur.user_id = @userId
ORDER BY r.role_code
'@
    $snapshot = @(Invoke-SqlRows -Connection $connection -Text $membershipQuery -Parameters @(
        (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId)
    ))
    $snapshotLoaded = $true
    if (-not ($snapshot | Where-Object { $_.RoleId -eq $targetRoleId })) {
        throw 'The selected account does not currently hold the role being revoked.'
    }
    $remainingRoleInitiallyPresent = [bool]($snapshot | Where-Object { $_.RoleId -eq $remainingRoleId })
    $report.initialMembershipRows = $snapshot.Count

    $httpClient = New-Object System.Net.Http.HttpClient
    $health = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path '/api/health'
    if ($health.StatusCode -ne 200) {
        throw 'The loopback backend health endpoint did not return HTTP 200.'
    }

    if (-not $remainingRoleInitiallyPresent) {
        $failurePhase = 'temporary-remaining-role-setup'
        $addedAt = [DateTime]::UtcNow
        $addParameters = @(
            (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId),
            (New-SqlParameter '@roleId' ([System.Data.SqlDbType]::BigInt) $remainingRoleId),
            (New-SqlParameter '@assignedAt' ([System.Data.SqlDbType]::DateTime2) $addedAt)
        )
        $added = Invoke-SqlNonQuery -Connection $connection -Text @'
INSERT INTO dbo.user_role (user_id, role_id, assigned_by_user_id, assigned_at)
VALUES (@userId, @roleId, NULL, @assignedAt)
'@ -Parameters $addParameters
        if ($added -ne 1) {
            throw 'The temporary remaining-role setup did not insert exactly one row.'
        }
        $report.temporaryRemainingRoleAdded = $true
    }

    $failurePhase = 'credential-load'
    if (-not (Test-Path -LiteralPath $CredentialFile -PathType Leaf)) {
        throw 'The DPAPI-protected credential file for this isolated database was not found.'
    }
    $credentialJson = Get-Content -LiteralPath $CredentialFile -Raw
    $credentialEntries = ConvertFrom-Json -InputObject $credentialJson
    $credential = $credentialEntries | Where-Object { $_.email -ceq $Email } | Select-Object -First 1
    if (-not $credential) {
        throw 'The credential file does not contain the selected account.'
    }
    $securePassword = ConvertTo-SecureString $credential.encryptedPassword
    $password = [Net.NetworkCredential]::new('', $securePassword).Password
    Remove-Variable credentialEntries, credential, credentialJson -ErrorAction SilentlyContinue

    $failurePhase = 'login-before-revoke'
    $loginBody = @{ email = $Email; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-LocalApi -Client $httpClient -Method 'Post' -Path '/api/auth/login' -Body $loginBody
    $loginData = $login.Content | ConvertFrom-Json
    if ($login.StatusCode -ne 200 -or -not $loginData.data.accessToken) {
        throw 'Login did not return HTTP 200 with a bearer token.'
    }
    $token = [string]$loginData.data.accessToken
    $loginData = $null
    $loginBody = $null
    $password = $null
    $securePassword.Dispose()
    $securePassword = $null

    $failurePhase = 'pre-revoke-authorization'
    $beforeMe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path '/api/auth/me' -BearerToken $token
    $beforeMeData = $beforeMe.Content | ConvertFrom-Json
    if ($beforeMe.StatusCode -ne 200 -or
        [long]$beforeMeData.data.id -ne $userId -or
        $RoleToRevoke -notin @($beforeMeData.data.roles) -or
        $RemainingRole -notin @($beforeMeData.data.roles)) {
        throw 'Pre-revoke /me did not confirm the expected account and both roles.'
    }
    $beforeProbe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path $probePath -BearerToken $token
    $report.clubManagerAccessBeforeStatus = $beforeProbe.StatusCode
    if ($beforeProbe.StatusCode -ne 404) {
        throw 'The unmapped admin security probe did not pass the CLUB_MANAGER matcher before revocation.'
    }

    $failurePhase = 'role-revocation'
    $deleteParameters = @(
        (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId),
        (New-SqlParameter '@roleId' ([System.Data.SqlDbType]::BigInt) $targetRoleId)
    )
    $report.revokeDeletedRows = Invoke-SqlNonQuery -Connection $connection -Text @'
DELETE FROM dbo.user_role
WHERE user_id = @userId AND role_id = @roleId
'@ -Parameters $deleteParameters
    if ($report.revokeDeletedRows -ne 1) {
        throw 'Revocation did not delete exactly the selected user-role membership.'
    }
    $targetRoleWasDeleted = $true

    $failurePhase = 'post-revoke-role-check'
    $revokedProbe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path $probePath -BearerToken $token
    $report.revokedRoleAdminProbeStatus = $revokedProbe.StatusCode
    $remainingMe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path '/api/auth/me' -BearerToken $token
    $report.remainingRoleMeStatus = $remainingMe.StatusCode
    $remainingMeData = $remainingMe.Content | ConvertFrom-Json
    $remainingRoles = @($remainingMeData.data.roles)
    $report.remainingRolePresentAfterRevoke = $RemainingRole -in $remainingRoles -and $RoleToRevoke -notin $remainingRoles
    if ($revokedProbe.StatusCode -ne 403 -or
        $remainingMe.StatusCode -ne 200 -or
        [long]$remainingMeData.data.id -ne $userId -or
        -not $report.remainingRolePresentAfterRevoke) {
        throw 'Post-revoke HTTP and current-role assertions did not pass.'
    }

    $failurePhase = 'exact-role-restore'
    $targetSnapshot = $snapshot | Where-Object { $_.RoleId -eq $targetRoleId } | Select-Object -First 1
    $remainingTarget = @(Invoke-SqlRows -Connection $connection -Text $membershipQuery -Parameters @(
        (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId)
    ) |
        Where-Object { $_.RoleId -eq $targetRoleId })
    if ($remainingTarget.Count -eq 0) {
        Add-ExactMembership -Connection $connection -Membership $targetSnapshot
        $report.targetRoleRestoreInsertedRows = 1
    }
    elseif ($remainingTarget.Count -ne 1 -or
        (Get-MembershipSignature $remainingTarget) -ne (Get-MembershipSignature @($targetSnapshot))) {
        throw 'The target membership is not in a state that can be restored without risking another row.'
    }

    $failurePhase = 'post-restore-role-check'
    $restoredProbe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path $probePath -BearerToken $token
    $report.restoredRoleAdminProbeStatus = $restoredProbe.StatusCode
    $restoredMe = Invoke-LocalApi -Client $httpClient -Method 'Get' -Path '/api/auth/me' -BearerToken $token
    $report.restoredRoleMeStatus = $restoredMe.StatusCode
    $restoredMeData = $restoredMe.Content | ConvertFrom-Json
    if ($restoredProbe.StatusCode -ne 404 -or
        $restoredMe.StatusCode -ne 200 -or
        $RoleToRevoke -notin @($restoredMeData.data.roles) -or
        $RemainingRole -notin @($restoredMeData.data.roles)) {
        throw 'Post-restore authorization and role assertions did not pass.'
    }
    $report.outcome = 'PASS'
}
catch {
    $mainFailure = $_
    $report.outcome = 'FAIL'
    $report.failurePhase = $failurePhase
    $report.failureType = $_.Exception.GetType().FullName
    $report.failureLine = $_.InvocationInfo.ScriptLineNumber
    $report.failureMessage = $_.Exception.Message
}
finally {
    if ($connection -and $connection.State -eq [System.Data.ConnectionState]::Open -and $snapshotLoaded) {
        try {
            $targetSnapshot = $snapshot | Where-Object { $_.RoleId -eq $targetRoleId } | Select-Object -First 1
            if ($targetSnapshot) {
                $currentTarget = @(Invoke-SqlRows -Connection $connection -Text $membershipQuery -Parameters @(
                    (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId)
                ) |
                    Where-Object { $_.RoleId -eq $targetRoleId })
                if ($currentTarget.Count -eq 0) {
                    Add-ExactMembership -Connection $connection -Membership $targetSnapshot
                    $report.targetRoleRestoreInsertedRows++
                }
                elseif ($currentTarget.Count -ne 1 -or
                    (Get-MembershipSignature $currentTarget) -ne (Get-MembershipSignature @($targetSnapshot))) {
                    throw 'Cleanup found unexpected target membership state; exact restoration cannot proceed safely.'
                }
            }

            if (-not $remainingRoleInitiallyPresent -and $remainingRoleId) {
                $cleanupParameters = @(
                    (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId),
                    (New-SqlParameter '@roleId' ([System.Data.SqlDbType]::BigInt) $remainingRoleId)
                )
                $report.temporaryRoleCleanupRows = Invoke-SqlNonQuery -Connection $connection -Text @'
DELETE FROM dbo.user_role
WHERE user_id = @userId AND role_id = @roleId
'@ -Parameters $cleanupParameters
                if ($report.temporaryRoleCleanupRows -gt 1) {
                    throw 'Cleanup attempted to remove more than the one temporary role row.'
                }
            }

            $finalRows = @(Invoke-SqlRows -Connection $connection -Text $membershipQuery -Parameters @(
                (New-SqlParameter '@userId' ([System.Data.SqlDbType]::BigInt) $userId)
            ))
            $report.finalMembershipRows = $finalRows.Count
            $report.snapshotExactlyRestored = (Get-MembershipSignature $finalRows) -ceq (Get-MembershipSignature $snapshot)
            if (-not $report.snapshotExactlyRestored) {
                throw 'Final user-role rows do not match the full initial snapshot.'
            }
        }
        catch {
            $cleanupFailure = $_
            $report.outcome = 'FAIL'
            $report.cleanupFailureType = $_.Exception.GetType().FullName
            $report.cleanupFailureLine = $_.InvocationInfo.ScriptLineNumber
            $report.cleanupFailureMessage = $_.Exception.Message
        }
    }
    elseif ($snapshotLoaded) {
        $cleanupFailure = [InvalidOperationException]::new('The isolated SQL connection closed before membership restoration.')
        $report.outcome = 'FAIL'
        $report.cleanupFailureType = $cleanupFailure.GetType().FullName
    }

    $report.finishedAtUtc = [DateTime]::UtcNow.ToString('o')
    $reportDirectory = Split-Path -Parent $ReportPath
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
    if ($httpClient) {
        $httpClient.Dispose()
    }
    if ($connection) {
        $connection.Dispose()
    }
    $token = $null
    $password = $null
    if ($securePassword) {
        $securePassword.Dispose()
    }
    Remove-Variable login, loginData, loginBody, beforeMeData, remainingMeData, restoredMeData, beforeMe, beforeProbe, revokedProbe, remainingMe, restoredProbe, restoredMe -ErrorAction SilentlyContinue
}

Write-Output "ROLE_REVOCATION_OUTCOME=$($report.outcome)"
Write-Output "REPORT=$ReportPath"
if ($mainFailure) {
    throw "Role revocation check failed during '$($report.failurePhase)' ($($report.failureType)); sanitized evidence was written."
}
if ($cleanupFailure) {
    throw "Membership restoration failed ($($report.cleanupFailureType)); inspect sanitized evidence at '$ReportPath' and restore only after confirming the isolated database identity."
}
