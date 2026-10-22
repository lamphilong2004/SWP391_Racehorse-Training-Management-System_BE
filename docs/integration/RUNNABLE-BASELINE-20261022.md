# Runnable local release baseline — 22 October 2026

The Leader's runnable-baseline directive supersedes the earlier five-phase execution
sequence. Missing features, UI redesign and full suites are deferred. Existing
mandatory independent reviews remain required. This is a **release-head local run**,
not evidence that either main branch was promoted.

## Git and review holds

| Repository | Tested executable source | Main, unchanged | Release PR |
|---|---|---|---|
| BE | `23cd849d0a88b22cc6d969ca760427e5b6f2505c` | `89991c1281edd8f2421b5ee57d6e1a283a70683d` | [32](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/32) |
| FE | `e48f8383c19464d01d2d45fc288d8a393ec52bc4` | `f3676d142d617087892531cbe74cc1f91d0408d4` | [23](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_FE/pull/23) |

Subsequent documentation-only commits do not change these tested executable trees.
BE PR32 inherits PR25's CHANGES_REQUESTED. The Leader retained the current precheck
contract; [reviewer confirmation of the requested local-release waiver](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/25#issuecomment-6329695684)
is still pending, alongside acceptance of the other fixes/evidence. A CLEAN merge
status is not approval. FE PR23/PR22 still require independent approval; FE main also
requires last-push approval and resolved conversations. Neither release was merged.

BE PR17–24,27,29–31 are ancestors of the candidate. PR26/28 are separate standalone
Medical/Groom versions; the combined PR29 is already present. PR28's inspected
assignment-repository differences are exact-count versus positive-count validation
and private versus public migration guard, not a missing endpoint. Do not overlay a
standalone version that removes combined integration. PR15 has a separate Horse
smoke helper but older DB tooling; its whole branch must not replace the current
installer. Legacy PR4/5/6/11 are not integration prerequisites to merge again.

FE PR22 is an ancestor. Registration PR9 and clean Medical PR8 source files are
already present through prior integration, without their branch tips as ancestors.
PR5's legacy `features/core046` pages are not mounted: current GroomTasks/Incidents
implement these routes through canonical auth. Their UX differences are not declared
fully equivalent; preserve the legacy branch for focused reconciliation later.
Do not add a competing provider/router or merge legacy PR2.

No source worktree was reset or overwritten, no branches deleted and no commits
squashed/re-authored. The original demo WIP remains separate.

## What actually ran

- SQL Server Running; existing local listener and certificate configuration unchanged.
- Existing isolated DB `racehorse_local_api128_20261021_163300`: strict JDBC TLS and
  full structural verifier PASS, SQL Server `16.0.1000.6`, JDBC `13.4.0.0`, collation
  `SQL_Latin1_General_CP1_CI_AS`, compatibility160, 53 tables/512 columns.
- FE API-only production bundle at `http://localhost:5173/login`, BE Java21 at
  `http://localhost:8080`; simulator OFF, JPA validate, SQL init never.
- 42 real HTTP requests **through FE /api proxy**, 56 assertions PASS: login and /me
  for all seven roles, 401, unauthorized-role403 retaining session, seven logout204,
  Horse DRAFT create/list/detail/second read, bounded Training/Medical/admin reads.
- One retained Horse `id=2`, code `RB-3BAC3DBB6D`. Independent SELECT-only JDBC
  readback: five assertions PASS, including persisted DRAFT and manager login/logout
  audit. No demo DB/reset/migration/seed was used in this batch.
- Backend `/api/health`, `/v3/api-docs` and `/swagger-ui/index.html` return200;
  OpenAPI contains110 distinct paths (not the operation count). Swagger URL is
  `http://localhost:8080/swagger-ui/index.html`. FE's SPA fallback200 at a Swagger
  path is not Swagger evidence; the backend origin was checked separately.
- CORS OPTIONS login returns200, exact allowed origin `http://localhost:5173` and
  `authorization, content-type`. No hosted endpoint or public SQL exposure added.
- Built-in browser confirms Login renders. Authenticated browser click journeys,
  session restoration and complete three-flow execution were **not run this batch**.
- Compile/package, FE lint/typecheck/build and bundle guard passed before this run.
  No executable changes followed those checks. Full suites deliberately deferred.

See `RUNNABLE-BASELINE-EVIDENCE-20261022.json` for sanitized request/assertion evidence.
Health200 alone is not the SQL proof; the separate JDBC readback supplies that proof.

## Windows startup for another member

Main remains the old baseline while reviews are pending. Do not tell members that
pulling main yields this release yet. Use separate clean clones/worktrees at the
reviewed release commits above if the Leader permits candidate testing. After
promotion, record the actual main merge SHAs before team rollout.

Prerequisites: PowerShell7, Java21, Maven, the Node version specified by FE, local
SQL Server and matching JDBC integrated-auth DLL when using Windows auth. Follow
`../LOCAL-FIRST-DATABASE.md`; `database/migrations/manifest.json` pins the order and
checksums. Never reuse an existing DB as a fresh install target. A complete fresh
installer rerun after the batch fix is still unverified; no new DB was created here.

In a **private local** PowerShell environment, populate the placeholders in
`../examples/local-first.env.example`. It is documentation, not an env-file loader.
Use your own DB, certificate SAN/CA truststore, database-scoped identity and random
JWT key. Do not copy Long's private key/PFX, passwords, JWT key or DPAPI credentials.

```powershell
# Backend terminal, repository root. Existing verified DB: verifier only, no installer/seed.
./scripts/db/Verify-LocalDatabase.ps1
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'
$env:REALTIME_SIMULATOR_ENABLED = 'false'
# DB_URL must explicitly include encrypt=true;trustServerCertificate=false and the valid SAN.
# Set dedicated truststore path/password via Hikari env properties from the template.
mvn -B -ntp -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'BE_PACKAGE_FAILED' }
java "-Djava.library.path=$env:RH_JDBC_NATIVE_DIR" -jar target/racehorse-backend-0.1.0-SNAPSHOT.jar
```

Do not package into a target folder used by a different running demo JAR. Use an
isolated clone or `-Dracehorse.build.directory=<separate-output>` and run that JAR.
Keep `ddl-auto=validate`; startup must not apply schema. Stop on schema/TLS errors
instead of bypassing validation or migrating an existing database.

```powershell
# Frontend terminal, FE repository root. Process-local shell handles Windows paths containing &.
$env:npm_config_script_shell = (Get-Command pwsh).Source
$env:VITE_API_BASE_URL = '/'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
$env:VITE_BUSINESS_DATA_SOURCE = 'api'
$env:VITE_ROLE_TEST_LOGIN = 'false'
npm ci
if ($LASTEXITCODE -ne 0) { throw 'FE_INSTALL_FAILED' }
npm run build
if ($LASTEXITCODE -ne 0) { throw 'FE_BUILD_FAILED' }
node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5173 --strictPort
```

FE `/api` and `/ws` use the local proxy; API failures do not use sample data. A normal
production bundle excludes developer galleries and role-test sign-in. This local
Vite proxy is not a deployable Internet API origin.

Seed accounts, when explicitly authorized on a new member DB, are generated by the
existing seed helper and encrypted to that Windows user at
`%LOCALAPPDATA%/Racehorse/LocalDb/<database>/accounts.dpapi.json`. Do not log credentials
or send them to chat/Git. Existing accounts require no seed. Another machine has
**not** yet passed startup/browser/SQL acceptance.

## Gap report and next work

`RUNNABLE-BASELINE-API-GAPS-20261022.json` reuses the128-operation inventory:
18 have an observed positive runtime path; 110 remain PARTIAL. WORKING means only
the recorded request passed, not exhaustive operation COMPLETE. None is marked
formally API COMPLETE. FE `docs/RUNNABLE-BASELINE-SCREEN-GAPS-20261022.json` records
70 mounted routes/aliases, guards and API-family references. All remain PARTIAL for
full browser interaction acceptance; public render/SPA200 is not a business PASS.

Next iteration priority is Training: reviewer disposition of precheck finding;
schedule/assigned Groom precheck/Rider start-pause-resume-finish; monitoring and
terminal evaluation on exact reviewed source, then Program adjustments and history.
Notification header realtime/reconnect and production reset delivery remain known
gaps. Do not implement them as part of this runnable-baseline batch.

## Stop/recovery

Keep SQL service, schema, migration bytes and existing DBs unchanged. Stop only the
FE/BE processes started for this baseline if needed; preserve the test Horse/audit
and diagnostic evidence. No drop/reset/restore, service restart, firewall change,
main force push or branch deletion is a recovery action.
