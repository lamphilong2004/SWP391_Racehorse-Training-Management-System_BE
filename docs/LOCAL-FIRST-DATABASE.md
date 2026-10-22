> RELEASE CANDIDATE — 22 October 2026: byte handling, ordered migration execution and the versioned structural snapshot are reconciled in this candidate. Full fresh-install verification after the SQL Server batch fix remains a Phase 4 gate. The prior new test database was resumed after an installer failure; this is not proof of a fresh install from this release. Never install/seed over an existing database.

# Racehorse local-first database handoff — release candidate

Leader approved DB-01 and the CORE-007 technical migration on 2026-10-18.
This package is pending clean-install evidence and Leader approval before team rollout.
Release source: `release/be-main-api128-20261022`; use the exact reviewed commit from its PR, not a moving branch for a team rollout.
CORE-001/011 ownership and original contribution history remain Dung's. This work
reuses the CORE-007 seed SQL and BCrypt generator; it does not replace the demo seeder.

## Prerequisites on each member's machine

- SQL Server 2022 Developer (non-production development only), Java 21+, Maven,
  PowerShell 7 and the FE's required Node version. Long's verified SQL version is
  16.0.1000.6; other machines must report their actual version. SQL 2016 compatibility
  mentioned in old DB-01 notes is not a tested installer support claim.
- A running local instance and its existing local TCP endpoint. This installer never
  changes instance services, certificates, protocols, firewall or public exposure.
- The operator needs permission to create a new local database and read its metadata.
  BE runtime credentials should be separate and scoped to that member's database.
- A SQL service certificate with Server Authentication, valid lifetime, SAN matching
  the configured certificate hostname and service-account read access to its private key.
  A member/administrator must configure their instance certificate if needed under a
  separate local maintenance decision. Never copy Long's SQL private key or PFX.
- Verify the public issuing CA/certificate fingerprint through an approved channel.
  Import only that public CA chain into a dedicated Java PKCS12 truststore using
  `keytool -importcert` (interactive password). Do not alter global JDK cacerts or
  disable certificate validation. Match JDBC `hostNameInCertificate` to a valid SAN.
- Resolve the JDBC jar and, for Windows integrated auth, the matching architecture/version
  native auth DLL. Set their absolute local paths; do not copy machine-specific paths.

## Install a new database

Read `docs/examples/local-first.env.example` and set the RH_* variables in a private
shell. It is a template, not an automatically sourced configuration. Secrets stay in
the process or an OS-protected local secret file; do not paste them into transcripts.
No password is passed in Java or sqlcmd command arguments.

```powershell
# Choose a unique name once. Never point this at an existing database.
$env:RH_DATABASE = 'racehorse_local_<member>_<unique_timestamp>'
./scripts/db/Install-LocalDatabase.ps1 -SeedTestAccounts -SeedReferenceData
./scripts/db/Verify-LocalDatabase.ps1
```

`-SeedTestAccounts` runs `Seed-LocalDatabase.ps1` (seven role accounts, see below).
`-SeedReferenceData` runs `Seed-LocalReferenceData.ps1`: 12 stable boxes and the trainer
specialization / rider skill catalog. These rows have no management API, so without them a
fresh database cannot complete Flow 1 stable assignment or Flow 2 recommendations. The
reference seed is idempotent (insert-missing only) and can be rerun on its own.

Apply order: DB with `SQL_Latin1_General_CP1_CI_AS` -> unchanged DB-01 (51 business
tables /493 columns) -> unchanged V20261016_01 (registration_request,14 columns)
-> V20261020_01 (nullable care-task Groom assignment) -> V20261021_01 (session Groom assignment).
The expected result is 53 tables/512 columns, verified against the versioned structural
snapshot pinned by `database/migrations/manifest.json`. The normalized email column
retains `Latin1_General_100_BIN2`; currency CHECK expressions retain their binary
collation. Optional modules' tables remain in the canonical51; no optional feature
implementation or sample business records are added.

The installer refuses any existing target, including an empty or partly installed DB.
It validates approved file hashes and compares live structural metadata, including
columns/types/nullability/identity,keys,FK actions,CHECK/default definitions,index
columns/filter/uniqueness and collations. Disabled or untrusted constraints are drift.
The read-only verifier does not execute any DDL/DML or treat a table count as sufficient.

Receipt: `%LOCALAPPDATA%/Racehorse/LocalDb/<database>/receipt.json`. This is local
installation evidence, not a SQL-resident migration ledger or authorization to upgrade
an existing DB. No migration history table is created. Failed/partial installations
are preserved for diagnosis; no automatic drop/reset/restore or cleanup occurs.

The existing CORE-007 helper generates seven random BCrypt-backed ACTIVE test accounts.
Passwords remain DPAPI-protected for the current Windows user in `accounts.dpapi.json`
beside the receipt. Generate your own accounts locally; never share another member's
DPAPI file or commit credentials. Seed refuses existing users or credentials. Seed
failure does not change an already completed schema receipt into a test PASS.

## Run the real stack

Quick path (same shell that holds the RH_* variables; verified 2026-10-22 on Long's machine):

```powershell
mvn -B -ntp -DskipTests package
./scripts/local/Start-LocalBackend.ps1            # 127.0.0.1:8080, dev profile, strict TLS, JPA validate
# second terminal, FE repository root: VITE_API_BASE_URL='/', VITE_API_PROXY_TARGET='http://127.0.0.1:8080',
# npm ci; npm run build; node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5173 --strictPort
# third terminal (Python 3 on PATH), same RH_* variables:
./scripts/smoke/Run-FlowSmoke.ps1                 # Horse -> Training -> Medical real-API journey, prints FLOW_SMOKE_PASS
```

`Start-LocalBackend.ps1` generates a JWT key once and keeps it DPAPI-protected beside the install
receipt. `Run-FlowSmoke.ps1` logs in the seeded accounts through the FE proxy and writes test
horses/programs/sessions/cases into **your** new database; evidence JSON goes to
`%LOCALAPPDATA%/Racehorse/smoke/<database>/<timestamp>/`. Never point it at a shared or demo DB.

Build BE with `mvn -DskipTests package` during release Phases 1–2; run `mvn verify`
in Phase 4. Build into an isolated output path when an existing demo is running.
Set DB_URL to the exact new DB,
`encrypt=true;trustServerCertificate=false`, the certificate hostname and auth mode.
Pass truststore path/password through Hikari data-source environment properties from
the template. Keep `spring.jpa.hibernate.ddl-auto=validate`. Generate JWT signing key
locally; never print or commit it. Use profile dev, not demo, and bind BE to127.0.0.1.

Set FE `VITE_API_BASE_URL` to the loopback BE and CORS to the exact FE origin.
Use API mode with role-test/sample flags disabled. Run FE from the existing official
implementation, not a new demo app. Missing business APIs must remain honest errors.
There is no `scripts/db/Test-LocalStack.ps1` in this release; do not rely on the old
handoff reference. Use `docs/integration/RUNNABLE-BASELINE-20261022.md` for the actual
startup commands and the recorded scoped JWT/me/401/403/readback/logout evidence.
Authenticated browser login, workspace, refresh and logout remain a separate gate.
H2/Maven test results must be reported separately from SQL Server/browser results.

## Team verification and rollback

Each member reports SQL version,DB/manifest version,hashes,collation,live structural
verification,JDBC strict TLS,BE startup and actual FE/API/SQL evidence. A PASS on Long's
machine does not prove another machine. No CORE status or Independent Gate is changed.
Cloudflare remains paused; online/Vercel integration is a separate future acceptance.

If installation or stack startup fails, stop only this task's newly started processes,
preserve the new DB and sanitized diagnostics, and restore private launcher variables.
Do not drop/reset it or touch HSF302,racehorse_demo or other existing databases.
Running the installer a second time against the same target must refuse without writes.

## CORE-007 SQL Server integration suite (gate only)

`PendingRegistrationSqlServerIntegrationTest` writes rows, so it runs only against a **dedicated, disposable**
database named `racehorse_local_core007_test_YYYYMMDD_HHMMSS`. Create that database with this installer and do
**not** seed it. `Core007TestDatabaseGuard` rejects anything else before the Spring context connects:

- any other name, including local development, staging, demo and other members' databases
- a non-localhost URL, `failoverPartner` / `serverName`, or a `databaseName` that differs from the declared name
- a URL without explicit `encrypt=true;trustServerCertificate=false` (no TLS bypass)
- a missing, mismatched or seeded installer receipt
- a live session that is not the declared database on this machine over encrypted TCP

Run it with `-Dcore007.sqlserver.it=true` and these variables:
- `CORE007_SQLSERVER_DATABASE`
- `CORE007_SQLSERVER_URL` (strict TLS, integrated security)
- the truststore through `SPRING_DATASOURCE_HIKARI_DATASOURCEPROPERTIES_TRUSTSTORE` / `...TRUSTSTOREPASSWORD`. Never put the truststore on the command line.

The tests never delete, truncate or reset anything. They use unique `@example.test` identities. After the gate,
the whole test database is retired instead of being cleaned in place, and it is never reused for development.
