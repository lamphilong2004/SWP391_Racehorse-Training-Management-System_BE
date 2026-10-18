# CORE-007 local authentication handoff

This runbook prepares a **new local SQL Server database** for Long/Hoàng to integrate and smoke-test frontend authentication without waiting for an online deployment. It never uses `HSF302`, `racehorse_demo`, `racehorse_dev`, or any shared/online database.

## Scope and revision

- Backend branch: `feat/CORE-007-auth-be`.
- Runtime authentication/pending-adapter revision: `022e02803178c968bfc299e33908e6b23a5ce2bb`; record the later handoff HEAD with `git rev-parse HEAD` before use.
- Canonical schema: `database/schema.sql`, 51 tables, SHA-256 `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`. This is the approved DB-01 physical schema; the repository pin is in `database/schema.sql.sha256`.
- The canonical schema file is present from commit `23179bfedf4fcef2e542c211f28d63163c4cedad`.
- Registration migration: `database/migrations/V20261016_01__create_registration_request.sql`, introduced in `6444a567ee71e2c33da8249354eb8ea3168b8944`, recorded as a new Leader approval in `docs/decisions/CORE-007-leader-decisions.md` (2026-10-16). SHA-256 `18A5D3EBA5AAD92967C6A6D2F6646410A98828D69E3C5BE8FA3C61A7EF418604`.
- Apply order: **create a new empty local DB → `database/schema.sql` → `V20261016_01` → local smoke-account seed**. The migration is manually applied; this repository has no Flyway/Liquibase runner.
- The migration and schema have been applied only to isolated local CORE-007 test databases. Never run these instructions against a shared, demo, HSF302, or online DB.
- No Render, Azure, or online SQL Server connection configuration was found in this repository or current environment. Hosting reachability is not verified; this local setup is independent of online deployment.

## Prerequisites and local SQL Server check

- Windows PowerShell, JDK 21 LTS, Maven 3.9.x, and Microsoft `sqlcmd`.
- SQL Server 2016 SP1 or later, running on this same workstation, with a local TCP endpoint. This handoff was verified with SQL Server `17.0.1000.7`, Microsoft OpenJDK `21.0.12.1`, and Maven `3.9.16`.
- SQL Server service check:

  ```powershell
  Get-Service -Name 'MSSQL*' | Select-Object Name, Status
  ```

  Confirm the selected local instance is `Running`, then verify the connection and physical host. Use Windows authentication for these local provisioning commands:

  ```powershell
  sqlcmd -S localhost -E -N -b -Q "SET NOCOUNT ON; SELECT @@SERVERNAME, CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128)), CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128));"
  ```

  `-N` encrypts the connection and leaves certificate and host-name validation enabled. The certificate chain must be trusted by the client; do not use `-C` or `TrustServerCertificate=true`.

  The physical machine name must match `$env:COMPUTERNAME`. If it does not, stop; do not proceed to create or modify a database. For a named local instance, substitute `localhost\INSTANCE` consistently.
- Verify the checkout before provisioning:

  ```powershell
  git branch --show-current
  git rev-parse HEAD
  git status --short
  ```

  Use the reviewed `feat/CORE-007-auth-be` branch. The migration file hash must match the value above.

## Create a new isolated DB and apply schema/migration/seed

Choose a fresh suffix. The scripts require `racehorse_core007_test_YYYYMMDD_HHMM`, check the local SQL Server machine identity, and refuse to drop/reset an existing database or reseed existing smoke accounts.

```powershell
$db = "racehorse_core007_test_$(Get-Date -Format yyyyMMdd_HHmm)"
sqlcmd -S localhost -E -N -b -h -1 -W -Q "SET NOCOUNT ON; SELECT DB_ID('$db');"
```

The query must return `NULL`. If it returns a database ID, choose a different suffix. Do not delete or reuse that DB.

Create the database and apply the canonical 51-table schema. The setup script validates the pinned checksum and refuses a non-empty database:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\db\create-core007-local-db.ps1 -Server localhost -Database $db
```

Confirm the exact database and host, then apply the approved migration:

```powershell
sqlcmd -S localhost -E -N -b -d $db -Q "SET NOCOUNT ON; SELECT DB_NAME(), CAST(SERVERPROPERTY('ComputerNamePhysicalNetBIOS') AS nvarchar(128));"
Get-FileHash -Algorithm SHA256 database\migrations\V20261016_01__create_registration_request.sql
sqlcmd -S localhost -E -N -b -d $db -i database\migrations\V20261016_01__create_registration_request.sql
```

Check the migration table and column:

```powershell
sqlcmd -S localhost -E -N -b -d $db -Q "SET NOCOUNT ON; SELECT COUNT(*) AS registration_request_table_count FROM sys.tables WHERE name = 'registration_request' AND schema_id = SCHEMA_ID('dbo'); SELECT name, collation_name FROM sys.columns WHERE object_id = OBJECT_ID('dbo.registration_request') AND name = 'email_normalized';"
```

The first result must be `1`, and `email_normalized` must use `Latin1_General_100_BIN2`.

Seed one fresh **ACTIVE** account for each approved role. The script compiles a small helper against this project's BCrypt dependency, generates distinct random passwords, stores BCrypt hashes in the DB and writes the plaintext passwords only to a DPAPI-protected file for the current Windows user:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\db\seed-core007-auth.ps1 -Database $db -Server localhost
```

The protected file is under `%LOCALAPPDATA%\Racehorse\Core007\AuthSmokeCredentials-<database>.dpapi.json`, outside Git. It can be decrypted only by the same Windows user on this machine. Do not copy it to chat, commit it, or put it in shared logs. If Long/Hoàng use another Windows account or workstation, transfer the generated passwords only through an approved private secret manager. Do not rerun the seed on an existing DB; the script intentionally refuses that.

The fixed emails and role mapping are:

| Role | Test email |
|---|---|
| `CLUB_MANAGER` | `core007.club.manager@example.test` |
| `HEAD_TRAINER` | `core007.head.trainer@example.test` |
| `TRAINER` | `core007.trainer@example.test` |
| `WORK_RIDER` | `core007.work.rider@example.test` |
| `VETERINARIAN` | `core007.veterinarian@example.test` |
| `GROOM` | `core007.groom@example.test` |
| `HORSE_OWNER` | `core007.horse.owner@example.test` |

To load a password for one local login without printing it, in the same Windows user session:

```powershell
$credentialFile = Join-Path $env:LOCALAPPDATA "Racehorse\Core007\AuthSmokeCredentials-$db.dpapi.json"
$accounts = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $credentialFile -Raw)
$account = $accounts | Where-Object role -eq 'TRAINER'
$securePassword = ConvertTo-SecureString $account.encryptedPassword
$password = [Net.NetworkCredential]::new('', $securePassword).Password
$email = $account.email
```

Repeat with the required role. Do not run `Write-Output $password`, print the login response object, or capture screenshots containing either.

## Backend environment and startup

The names below are bound in `src/main/resources/application.yml`, `JwtSettings`, and `CorsProperties`:

| Environment variable | Meaning |
|---|---|
| `DB_URL` | JDBC SQL Server URL. Must name the new local DB. |
| `DB_USERNAME`, `DB_PASSWORD` | SQL authentication credentials, if used. Leave empty for integrated authentication. |
| `JWT_SIGNING_KEY` | Base64 encoding of at least 32 random bytes; required at startup. |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | Positive integer token lifetime in seconds; `3600` is suitable for local smoke testing. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact browser origins; local Vite default is `http://localhost:5173`. No wildcard is configured. |

Set local-only values in the current PowerShell process; do not write values into Git, command transcripts, screenshots, or shared logs. Example for Windows integrated authentication:

```powershell
$env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=$db;encrypt=true;trustServerCertificate=false;integratedSecurity=true"
$env:DB_USERNAME = ''
$env:DB_PASSWORD = ''
$env:JWT_ACCESS_TOKEN_TTL_SECONDS = '3600'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
$env:SERVER_PORT = '8080'

$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SIGNING_KEY = [Convert]::ToBase64String($bytes)
$rng.Dispose()
[Array]::Clear($bytes, 0, $bytes.Length)
Remove-Variable bytes, rng
```

If port 8080 is already occupied, choose a free local port such as 18080 in `SERVER_PORT` and use that port in `$api` below. The port override is Spring Boot's standard server setting, not an application-specific secret/configuration property.

The JDBC driver uses a matching Microsoft Windows integrated-auth DLL when `integratedSecurity=true`. Keep it outside the repository and verify the pinned local artifact before use:

```powershell
$authDirectory = Join-Path $env:LOCALAPPDATA 'Racehorse\jdbc-auth'
New-Item -ItemType Directory -Path $authDirectory -Force | Out-Null
$authDll = Join-Path $authDirectory 'mssql-jdbc_auth-13.4.0.x64.dll'
curl.exe -fL 'https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.4.0.x64/mssql-jdbc_auth-13.4.0.x64.dll' -o $authDll
if ((Get-FileHash -Algorithm SHA256 $authDll).Hash -ne '759D0D189B5A8D1AE21A32B10C1D2FFD476C22476FF0705CE758D8E4BE860C81') { throw 'JDBC auth DLL checksum mismatch.' }
$env:JAVA_TOOL_OPTIONS = "-Djava.library.path=$authDirectory"
```

Alternatively, use a dedicated local SQL login. Enter it through the secure credential prompt and keep the password only in the current process environment:

```powershell
$dbCredential = Get-Credential -Message 'Dedicated local SQL test login'
$env:DB_USERNAME = $dbCredential.UserName
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
Remove-Variable dbCredential
```

Do not use shared credentials.

From the backend root on the `feat/CORE-007-auth-be` revision:

```powershell
$api = "http://localhost:$env:SERVER_PORT"
mvn spring-boot:run
```

The default profile is `dev`; Hibernate validates the approved schema and does not create or modify tables. The app listens on port 8080 by default. When finished, stop the process and clear local values:

```powershell
'DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SIGNING_KEY','JWT_ACCESS_TOKEN_TTL_SECONDS','CORS_ALLOWED_ORIGINS','JAVA_TOOL_OPTIONS','SERVER_PORT' | ForEach-Object { Remove-Item "Env:\$_" -ErrorAction SilentlyContinue }
```

## Auth/API smoke flow

Use a second PowerShell window with the same local DB, `CORS_ALLOWED_ORIGINS`, and `SERVER_PORT` configured; set `$api = "http://localhost:$env:SERVER_PORT"` there too. `Invoke-RestMethod` holds the token in memory. Never print `$login`, `$token`, or the `Authorization` header.

1. In the API window, set `$api = "http://localhost:$env:SERVER_PORT"` (or the same chosen local port). Check `GET $api/api/health` returns HTTP 200 and `UP`.
2. Load an account/password from the DPAPI file as above, then login:

   ```powershell
   $body = @{ email = $email; password = $password } | ConvertTo-Json
   $login = Invoke-RestMethod -Method Post -Uri "$api/api/auth/login" -ContentType 'application/json' -Body $body
   $token = $login.data.accessToken
   $headers = @{ Authorization = "Bearer $token" }
   Remove-Variable body, password, securePassword
   ```

   Success is HTTP 200 with `tokenType = Bearer`, positive `expiresIn`, and the matching current `user` and role. The bearer token is never printed.
3. Call `/api/auth/me` and verify the returned account ID, email, ACTIVE status, and current role:

   ```powershell
   $me = Invoke-RestMethod -Uri "$api/api/auth/me" -Headers $headers
   $actorId = $me.data.id
   $me.data | Select-Object id, userCode, fullName, email, roles, accountStatus
   ```

   If account field serialization differs, inspect only safe user fields; never include the token.
4. Logout:

   ```powershell
   Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$api/api/auth/logout" -Headers $headers
   ```

   Expected HTTP status is `204`. Confirm audit in this isolated DB:

   ```powershell
   sqlcmd -S localhost -E -N -b -d $db -Q "SET NOCOUNT ON; SELECT TOP (1) actor_user_id, actor_role, action, safe_summary, source FROM dbo.audit_log WHERE actor_user_id = $actorId AND action = 'USER_LOGGED_OUT' ORDER BY id DESC;"
   ```

   Expected action is `USER_LOGGED_OUT`, correct actor ID, and source `WEB`. The schema has no token/password fields in this audit record; the safe summary is fixed by the backend. Do not query or print request headers, JWTs, or passwords.
5. **Logout is stateless.** The API writes the audit event and the client must discard its token. There is no token blacklist/revocation table or logout-time JWT revocation. To verify that distinction while this test account remains ACTIVE, the same local test may call `/api/auth/me` once with the still-held token and observe HTTP 200. Do not claim logout invalidated an otherwise-valid token.
6. Verify deactivation only in this disposable local database. Before the update, record the selected account ID from `/me`; then:

   ```powershell
   sqlcmd -S localhost -E -N -b -d $db -Q "SET NOCOUNT ON; UPDATE dbo.app_user SET account_status = 'INACTIVE', updated_at = SYSUTCDATETIME() WHERE id = $actorId AND email = N'$email'; SELECT @@ROWCOUNT AS changed_rows;"
   ```

   Confirm `changed_rows = 1`. Reuse the still-valid bearer token against `"$api/api/auth/me"`; expected result is HTTP 403 with `Your account is deactivated.` Then restore this one local test account so another tester can log in:

   ```powershell
   sqlcmd -S localhost -E -N -b -d $db -Q "SET NOCOUNT ON; UPDATE dbo.app_user SET account_status = 'ACTIVE', updated_at = SYSUTCDATETIME() WHERE id = $actorId AND email = N'$email'; SELECT @@ROWCOUNT AS restored_rows;"
   ```

   Confirm `restored_rows = 1`. Do not perform either SQL update outside the newly created local DB.
7. Clear token and user data from the shell after the smoke test:

   ```powershell
   Remove-Variable token, headers, login, me, actorId, email, password, securePassword, account, accounts -ErrorAction SilentlyContinue
   ```

## RBAC and CORS boundaries

- `/api/admin/**` is protected to `CLUB_MANAGER`; application routes require a valid JWT except explicitly public health/login and development-only API docs. The seed gives each test user exactly one current role. `GET /api/auth/me` verifies role resolution.
- `CurrentAccountStatusFilter` reloads `app_user` and the current `user_role` rows for every authenticated request, then replaces JWT-claim authorities with those current DB roles. A role change therefore affects an already-issued JWT on its next request; it does not require token re-issuance.
- This backend currently exposes `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`, and `/api/health`. The shared OpenAPI file also describes registration, forgot-password, and reset-password flows; those operations are **not implemented by this backend increment**. Do not treat those paths as ready for FE calls.
- CORS allows only the exact origins in `CORS_ALLOWED_ORIGINS`, methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, and headers `Authorization, Content-Type, Accept`. For browser testing, send an `OPTIONS` preflight with the exact FE `Origin`; check `Access-Control-Allow-Origin` matches that origin. If blocked, confirm scheme/host/port exactly and that the env variable was set before starting the app; do not use `*` as a workaround.
- The `/api/admin/**` authorization rule is active, but the current backend has no admin business controller. There is not yet a real protected business operation to prove a successful CLUB_MANAGER action versus a non-manager `403`; only login and `/me` can presently confirm which role the account has.

### Existing-token role revoke / exact restore check

Use the live SQL Server test script only against the isolated local database and a loopback API started against that same database. It verifies `DB_NAME()`, `SERVERPROPERTY('ComputerNamePhysicalNetBIOS')`, the active account, and the approved schema/migration checksums before changing data. Every user/role value is passed through typed `System.Data.SqlClient.SqlParameter` bindings; the script does not build a DELETE/INSERT statement by interpolating user input.

The test snapshots **all** `user_role` memberships for the selected account, including `id`, `user_id`, `role_id`, `assigned_by_user_id`, `assigned_at`, `created_at`, and `updated_at`. If the account lacks the selected remaining role, the script adds that one temporary membership before login and removes it during cleanup. It deletes only the selected `CLUB_MANAGER` membership, then restores its original identity and all original metadata with `IDENTITY_INSERT`. A `finally` cleanup restores the snapshot after an assertion failure; restoration errors are surfaced and are not swallowed.

The only implemented role-specific matcher is `/api/admin/**` → `CLUB_MANAGER`; there is no mapped admin business controller. Therefore the script uses the deliberately **unmapped** `/api/admin/__core007-role-revocation-probe` only as a Spring Security boundary check, not as a real endpoint or successful business action. Expected responses are:

| Step | Admin matcher probe | `/api/auth/me` | Meaning |
|---|---:|---:|---|
| Before revoke, existing JWT | `404` | `200` | CLUB_MANAGER passes authorization; no handler is mapped |
| After revoke, same JWT | `403` | `200` | Current DB roles removed CLUB_MANAGER; the retained role remains in `/me` |
| After exact restore, same JWT | `404` | `200` | Restored CLUB_MANAGER again passes the matcher |

`/me` is available to any authenticated role and **does not prove a role-exclusive business permission**. A real retained-role business operation remains unverified until the corresponding controller exists. The SQL snapshot comparison proves that the original membership rows and metadata are restored, and that no other original roles were deleted or rewritten.

From the backend root, keep the API running against the test DB on a local port. The script reads the selected account's password from that Windows user's DPAPI-protected file; it never prints or writes the password or JWT:

```powershell
$db = 'racehorse_core007_test_20261017_0041'
$api = 'http://localhost:18081/'
$report = "docs\evidence\CORE-007-role-revocation-$(Get-Date -Format yyyyMMdd-HHmmss).json"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\db\Test-Core007RoleRevocation.ps1 `
  -Server localhost -Database $db -ApiBaseUri $api `
  -Email 'core007.club.manager@example.test' `
  -RoleToRevoke CLUB_MANAGER -RemainingRole TRAINER -ReportPath $report
```

Expected output is `ROLE_REVOCATION_OUTCOME=PASS`. The sanitized JSON evidence records UTC timestamps, exact branch/HEAD, SQL Server and DB identity, account ID, HTTP statuses, migration/schema hashes, snapshot/delete/restore counts, and exact-snapshot comparison. It contains no email, password, JWT, or signing key. Do not rerun concurrently for the same account; the membership changes are intentionally temporary.

## Pending-registration adapter

The pending-login adapter is **implemented and wired**, not a warning-only fallback: `AuthenticationService` queries `app_user` first and only queries the SQL Server `registration_request` adapter when no account exists. The adapter uses bound `email_normalized` and `PENDING_APPROVAL` parameters; BCrypt verification remains in Java. Pending-correct-password returns the contract's 403 without a JWT; incorrect/rejected/unknown cases return the same neutral 401. Migration V20261016_01 is required before starting the app against this schema.

The SQL Server integration tests require the same isolated schema and migration:

```powershell
$env:CORE007_SQLSERVER_DATABASE = $db
$env:CORE007_SQLSERVER_URL = "jdbc:sqlserver://localhost:1433;databaseName=$db;encrypt=true;trustServerCertificate=false;integratedSecurity=true"
$env:CORE007_SQLSERVER_USERNAME = ''
$env:CORE007_SQLSERVER_PASSWORD = ''
$env:JAVA_TOOL_OPTIONS = '-Djava.library.path=<directory-containing-mssql-jdbc_auth-13.4.0.x64.dll>'
mvn "-Dcore007.sqlserver.it=true" verify
```

The tests refuse remote hosts, JDBC failover/serverName overrides, a mismatched database, and a server whose physical machine name does not match the local workstation. They insert unique rows and do not perform table-wide deletion. H2-only tests are not evidence of SQL Server behavior.

The `/api/auth/register` contract is documented in OpenAPI but its controller/persistence flow is not implemented in this increment. CORE-047 still owns the approval transaction that creates the user/role and updates the registration request.

## Troubleshooting

- **SQL Server unavailable:** check the service status, `sqlcmd` authentication, local host name, TCP/IP, and the exact instance/port. Do not substitute a shared DB.
- **Database creation refused:** confirm the name is new and matches the required test pattern, Windows identity has local `CREATE DATABASE` permission, and target SQL Server physical host matches this PC.
- **Schema checksum mismatch:** stop and use the approved revision; do not edit or bypass the pin.
- **Migration says table exists:** stop and choose a newly created database; never drop or reset the existing one.
- **Application DB login failure:** verify `DB_URL` names exactly `$db`, credentials are for a dedicated local test login (or configure the matching integrated-auth DLL), and `JAVA_TOOL_OPTIONS` is set before `mvn spring-boot:run`.
- **JWT startup error:** `JWT_SIGNING_KEY` must Base64-decode to at least 32 bytes and `JWT_ACCESS_TOKEN_TTL_SECONDS` must be a positive integer. Generate a new local key in memory; never paste it into source/docs.
- **Login returns 401:** confirm the account email/password from the protected local credential file; do not log the request body or hash.
- **Login returns 403:** the account may be inactive or the pending registration credentials may be correct; inspect the contract-defined error only.
- **CORS preflight rejected:** check the exact frontend origin and configured allowlist; restart after changing the environment.
- Keep the local test database for diagnosis. No cleanup step drops it. Any future removal requires explicit approval and a separate exact database identity check.
