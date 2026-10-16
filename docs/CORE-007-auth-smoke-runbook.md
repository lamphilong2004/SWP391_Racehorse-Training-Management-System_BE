# CORE-007 backend authentication smoke-test runbook

This runbook is for local testing with an **ACTIVE** account. It does not require the pending-registration flow. Use only an isolated local test database or another database explicitly approved for testing; never point it at `racehorse_dev`, `racehorse_demo`, or an online/shared database.

## Prerequisites

- Windows PowerShell.
- JDK 21 LTS (`java -version`).
- Maven 3.9.x (`mvn -version`). This verification used Maven 3.9.16 and Microsoft OpenJDK 21.0.12.1.
- SQL Server 2016 SP1 or later with TCP/IP enabled on the configured port.
- Permission to create a new, uniquely named local test database and apply the canonical schema plus the versioned registration-request migration.
- An ACTIVE `app_user` row with a role assignment and a password hash created through an approved local test setup. Store the user's email and password only in local shell variables or an approved secret store.

## Create and prepare an isolated database

Choose a new name, for example `racehorse_core007_smoke_<unique_suffix>`. Confirm that the name does not exist and that the target server is the intended local test instance before creating it. Do not drop, reset, or reuse an existing database.

The canonical schema script verifies its checksum and refuses a database containing tables. Run it with an explicit database name (not the script's default):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\db\setup-dev-db.ps1 `
  -Server "localhost" -Database "racehorse_core007_smoke_<unique_suffix>" -UseWindowsAuth
```

Then, after reviewing the SQL and confirming the selected database again, apply the approved migration only to that database:

```powershell
sqlcmd -S "localhost" -E -C -b `
  -d "racehorse_core007_smoke_<unique_suffix>" `
  -i "database\migrations\V20261016_01__create_registration_request.sql"
```

The migration is versioned but currently applied manually with `sqlcmd`; this repository does not configure Flyway or Liquibase. Do not apply it to a shared database.

## Legacy app_user email review

Do not inspect `racehorse_dev`, `racehorse_demo`, or an online/shared database for this task. In a separately approved, read-only environment, retrieve only the required `app_user.id` and `email` values, then compare each email off-line with the shared trim → Unicode NFC → lowercase (`Locale.ROOT`) normalizer. Keep raw email output out of shared logs and report affected row IDs/counts and collision groups, not passwords or tokens.

No automatic remediation is approved. A safe proposal is: perform a read-only dry run; identify duplicate normalized values and conflicts under the existing `app_user` unique constraint/collation; have the Leader approve a row-by-row mapping for those conflicts; take an authorized backup; then apply only the approved mappings in a controlled transaction and verify the changed IDs/count. Do not alter the `app_user` collation or bulk-update values without that separate approval.

## Configure and start

Copy the names and safe URL pattern from `docs/examples/auth-smoke.env.example`; set each value in the current PowerShell process. Never commit credentials or a JWT signing key.

Generate a temporary 256-bit signing key in memory without displaying it:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SIGNING_KEY = [Convert]::ToBase64String($bytes)
$rng.Dispose()
[Array]::Clear($bytes, 0, $bytes.Length)
Remove-Variable bytes, rng
```

Set `DB_URL` to the isolated database, `DB_USERNAME` / `DB_PASSWORD` only if using SQL authentication, and `JWT_ACCESS_TOKEN_TTL_SECONDS` to a positive test value. With Windows integrated authentication, install/use the Microsoft JDBC driver's compatible Windows authentication helper as documented by the local environment; do not place a credential or helper binary in Git.

Start from the backend repository root:

```powershell
mvn spring-boot:run
```

The default `dev` profile validates the canonical SQL Server schema. If configuration is missing or invalid, startup must fail rather than silently fall back to a test database.

## Smoke-test an ACTIVE account

In a second PowerShell window, set the same isolated `DB_URL`, database credentials (if applicable), and `JWT_SIGNING_KEY` for API calls only if required by local tooling; do not print these values.

```powershell
Invoke-RestMethod http://localhost:8080/api/health

$email = Read-Host "ACTIVE test account email"
$securePassword = Read-Host "ACTIVE test account password" -AsSecureString
$password = [Net.NetworkCredential]::new("", $securePassword).Password
$body = @{ email = $email; password = $password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body $body
$token = $login.data.accessToken
$authorizationScheme = "Bearer"
$authorizationValue = "$authorizationScheme $token"
$headers = @{ Authorization = $authorizationValue }
Remove-Variable password, body, securePassword

Invoke-RestMethod http://localhost:8080/api/auth/me `
  -Headers $headers

Invoke-WebRequest -Method Post -Uri http://localhost:8080/api/auth/logout `
  -Headers $headers
Remove-Variable token, login, authorizationScheme, authorizationValue, headers
```

Expected results: health returns `UP`; login returns a bearer token and current roles; `/api/auth/me` returns that same current ACTIVE account; logout returns `204` and persists `USER_LOGGED_OUT`. A deactivated account returns the contract-defined `403`.

## Troubleshooting and cleanup

- **Startup connection/schema failure:** verify `DB_URL` names the isolated database, canonical schema validation succeeds, and no shared database is selected.
- **Missing registration table:** verify the migration version and database context. Do not copy the historical demo DDL.
- **Login returns 401:** verify the account is ACTIVE, its stored email is normalized with trim + NFC + lowercase, it has a role assignment, and the supplied password matches the BCrypt hash. Do not log the password or hash.
- **Login returns 403:** inspect the contract error message; inactive accounts and pending registrations are intentionally distinct.
- **SQL Server login failure:** use a permitted local Windows-auth connection or request a dedicated isolated test login; do not use shared credentials.
- Leave the isolated database intact for diagnosis unless the Leader explicitly authorizes its removal. Never run a demo reset script.

## Pending-registration integration tests

The SQL Server integration suite requires a fresh isolated database with the canonical schema and `V20261016_01` migration. Its test properties are supplied through `CORE007_SQLSERVER_URL`, `CORE007_SQLSERVER_USERNAME`, and `CORE007_SQLSERVER_PASSWORD`; never put their values in the command line or source. See the test class and environment for the exact Maven invocation. H2 test results are not SQL Server evidence.

The SQL Server suite refuses to run unless the URL targets `localhost` or `127.0.0.1`, `CORE007_SQLSERVER_DATABASE` matches the connected database, and the database name has the form `racehorse_core007_test_YYYYMMDD_HHMM`. Tests create uniquely named rows and do not delete existing rows.

Example invocation (set these only in the current local shell; leave username/password empty for approved Windows integrated authentication):

```powershell
$env:CORE007_SQLSERVER_DATABASE = "racehorse_core007_test_20261016_2353"
$env:CORE007_SQLSERVER_URL = "jdbc:sqlserver://localhost:1433;databaseName=$env:CORE007_SQLSERVER_DATABASE;encrypt=true;trustServerCertificate=true;integratedSecurity=true"
$env:CORE007_SQLSERVER_USERNAME = ""
$env:CORE007_SQLSERVER_PASSWORD = ""
mvn "-Dcore007.sqlserver.it=true" verify
```

For SQL authentication, source the username/password from an approved local secret store and omit `integratedSecurity=true`. The optional tests are gated off unless `-Dcore007.sqlserver.it=true` is supplied.
