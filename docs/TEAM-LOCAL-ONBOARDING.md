# Team local setup

Long, Dũng, Tiến, Hoàng and Đức use independent local databases with the same approved structure. Business records created on each machine are not synchronized. SQL online, Azure, Render deployment and tunnels are paused. Keep the accepted Render configuration unchanged; use the dev profile for local development.

## Pin both repositories

Use a new checkout/worktree and preserve existing branches/WIP. Baseline: BE dev `5867f07ad521f2862eb80d5aa85c0bd615826855` plus this reviewed onboarding PR; FE Integration `9fb747db7f985c7002373099fbae341f40d59e82`. The PR's final HEAD identifies the exact tool revision. Never distribute an uncommitted folder or edit a private schema variant. Later schema changes require reviewed migrations.

Manifest version: `DB-01+CORE007-V20261016_01-local-v1`. Schema SHA256 `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`; migration SHA256 `18a5d3eba5aad92967c6a6d2f6646410a98828d69e3c5be8fa3c61a7ef418604`. Existing schema, migration, seed and manifest bytes remain unchanged. Apply order: new DB with `SQL_Latin1_General_CP1_CI_AS`, DB-01, then `V20261016_01__create_registration_request.sql`. Result:51business+1technical tables,507columns,105FK,97CHECK; normalized email retains `Latin1_General_100_BIN2`.

## Prerequisites

- Windows x64, PowerShell7, SQL Server2022 Developer or compatible newer version, Java21+, Maven, Node>=22.13 and npm. Installer checks local Windows machine identity and uses current-user DPAPI; Linux/macOS are not claimed supported. Developer edition is for non-production development/test.
- SQL Server already running on its approved local TCP endpoint. Operator needs permission to create a new DB/read metadata; normal BE runtime should use a separate least-privilege identity. Installer does not start/restart services or change protocols/firewall/certificates.
- SQL service certificate with Server Authentication EKU, valid chain/lifetime and SAN matching the configured hostname; SQL service must have private-key Read. Obtain owner/admin agreement before binding/ACL/service changes. Never share Long's private key/PFX.
- A dedicated local Java truststore containing the verified **public** CA/certificate. Verify the fingerprint through an approved channel, then use `keytool -importcert -keystore <private-local-path>.p12 -storetype PKCS12 -alias local-sql-ca -file <public-ca.cer>`. Password is prompted, never passed in arguments. Do not edit global cacerts or disable TLS validation.
- SQL JDBC jar plus matching x64 native auth DLL for Windows integrated auth. Resolve versions from the pinned BE dependencies, or use an approved private local SQL login. Do not copy machine paths/secrets from another member.

## Install and verify

Set your `RH_*` variables privately using [local-first.env.example](examples/local-first.env.example). It is a template, not a shell script. Each member supplies their host/port/SAN/truststore/driver paths and credentials.

```powershell
$env:RH_DATABASE = 'racehorse_local_dung_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
# Set the remaining RH_* variables privately before continuing.
./scripts/db/Install-LocalDatabase.ps1 -SeedTestAccounts
./scripts/db/Verify-LocalDatabase.ps1
```

Retain the chosen name. Existing targets, even empty/partial DBs, are refused. Never reset/drop to retry. Receipt and seven locally generated BCrypt-backed test accounts live under `%LOCALAPPDATA%/Racehorse/LocalDb/<database>/`, outside Git. Passwords are random per machine and DPAPI-protected. The unchanged CORE-007 seed joins role/email rather than fixed identity IDs; consistent test emails are fictional references, not shared credentials. Do not run the demo seeder.

Verifier checks strict encrypted JDBC/TCP, manifest hashes, live tables/columns/types/nullability/identity,PK/UNIQUE,FK actions/trust,CHECK/default definitions,index columns/order/filter/uniqueness and collations. Counts alone are insufficient. A receipt is local evidence, never permission to upgrade an existing DB.

## Start the real local stack

In the BE shell set the same new DB/auth/TLS values from the template. For Windows integrated auth, DB_USERNAME/DB_PASSWORD stay empty and DB_URL includes `integratedSecurity=true`. Keep `encrypt=true;trustServerCertificate=false;hostNameInCertificate=<matching-SAN>` and dedicated Hikari truststore/path/password properties. Supply native DLL directory to Java when required.

```powershell
$env:JWT_SIGNING_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:JWT_ACCESS_TOKEN_TTL_SECONDS = '3600'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5173'
mvn -B -DskipTests package
java "-Djava.library.path=$env:RH_JDBC_NATIVE_DIR" -jar target/racehorse-backend-0.1.0-SNAPSHOT.jar
```

Confirm the process targets your new DB before business writes. Hibernate stays `validate`; no migration/seed runs at startup. This package build does not repeat accepted full Gates. In the pinned official FE checkout, run `npm ci`, then:

```powershell
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080'
$env:VITE_BUSINESS_DATA_SOURCE = 'api'
$env:VITE_ROLE_TEST_LOGIN = 'false'
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

These localhost URLs are for the member's own browser, never Internet Preview. API failures remain errors; no runtime sample fallback.

## One small verification journey

1. Verify schema. Sign into FE with your own generated CLUB_MANAGER account. Obtain its password from your private DPAPI/operator context; do not echo it or include it in transcripts/screenshots/messages.
2. Register a DRAFT using [team-horse-draft.json](examples/team-horse-draft.json), fictional identity fields without credentials or fixed IDs. Save, return to Horses, open Detail and refresh. Missing setup APIs remain errors; this does not prove DRAFT to ACTIVE.
3. Optional automated API/SQL check on **your operator-started new stack**:

```powershell
./scripts/db/Test-LocalHorse.ps1 -ApiBaseUri http://127.0.0.1:8080 -CreateDraft
```

Runner reuses local DPAPI credentials, checks login/me and independent SQL login-timestamp readback **before a business write**, then creates the agreed Horse only when its code is absent. It reads List/Detail, SQL by business code, refreshes and logs out. IDs come from server/SQL. Existing fixture data is verified, never overwritten. Without `-CreateDraft`, an absent Horse is refused. No SQL DML, reset, schema change or cleanup is issued. A unique local report contains statuses/paths and Horse identity; `browserVerified=false` stays explicit. Run the browser journey separately and inspect/sanitize diagnostics before sharing.

## Second-machine handoff

Dũng is the proposed first operator. Running installer/service changes on his machine requires his agreement; no remote access or second-machine PASS is assumed. Return only machine alias, BE/FE SHAs, actual SQL version, manifest/hashes/collation, verifier/TLS/startup results, FE Login/Create/List/Detail/Refresh, Horse code/actual ID and sanitized failure code. No passwords/JWT/Authorization/private key/PFX/DPAPI/HAR. No full suite rerun required.

Failure | Action
---|---
PKIX or hostname mismatch | Verify local public chain/SAN/truststore; owner/admin fixes binding if required. Never bypass TLS.
Native auth DLL missing/wrong architecture | Resolve the JDBC-matching x64 DLL or approved local SQL login.
Login/create/metadata permission denied | Owner grants minimum local permission; do not try other credentials.
Existing DB/receipt | Stop and retain it; choose a different newly approved target, never overwrite/reset.
Schema drift/checksum mismatch | Stop and compare pinned sources/live metadata; no automatic repair.
API_DATABASE_LOGIN_READBACK_MISMATCH | Stop before Horse creation; API must point to the same new DB.
Business API404 | Keep the honest UI error and hand off to its owner; no runtime fixtures.

Second-machine status stays PENDING until actual output and browser evidence arrive. Long's accepted install/TLS/Horse results are historical evidence for Long only.
