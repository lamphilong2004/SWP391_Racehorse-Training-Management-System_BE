# Runtime Validation Report

**Updated**: 2026-10-20
**Target**: `C:\Users\ACER\Racehorse-Workspace\_integration-api-wave1-review-20261020`
**Validated code SHA**: `3df9e6d38c60e947dba5de01032754877d8d1cbf`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | The Spring Boot random-port `SwaggerRuntimeSmokeTest` started the application and received HTTP 200 from `/swagger-ui.html` and `/v3/api-docs`. Startup duration was not separately measured. |
| Integration tests | PASS | 0 | Java 21 full `mvn verify`: 198 tests, 0 failures, 0 errors, 15 skipped; 183 passed. |
| API runtime smoke | PASS (local only) | 0 | `SwaggerRuntimeSmokeTest` verified Swagger UI/docs and selected operation IDs. This is not live Render or SQL Server evidence. |

**Overall**: NEEDS_SIGNOFF — SQL Server verification, independent review, live deployment checks, and several business/operational blockers remain.

## Environment

```text
environment:
  docker: UNAVAILABLE — `docker` command not found
  node: AVAILABLE — v24.20.0
  playwright: NOT REQUIRED — API-only backend; no browser-rendered UI
  infra-tier: FALLBACK(embedded/in-memory) — H2 test profile used because Docker is unavailable
  browser-tier: FRAMEWORK HTTP TEST — local Spring Boot HTTP smoke; browser E2E is not applicable
startup: PASS — exit_code: 0, Spring Boot random-port startup in SwaggerRuntimeSmokeTest; Swagger UI/docs returned HTTP 200; elapsed time not measured
integration: PASS — exit_code: 0, passed: 183, failed: 0, skipped: 15; full Maven verify on Java 21 using H2; SQL Server fidelity is not covered
e2e: UNVERIFIED — exit_code: n/a, no live Render or SQL-backed end-to-end writes; API-only local runtime smoke is recorded separately
overall: NEEDS_SIGNOFF — SQL Server gate, independent review, live deployment checks, and business blockers remain
```

The full Maven run completed successfully with Apache Maven 3.9.16 on Java 21.0.12.1; Maven was run from a temporary, checksum-verified installation rather than the system `PATH`. Surefire reports are under `target/surefire-reports/`.

## Test Evidence

- Full Maven result on candidate SHA `3df9e6d38c60e947dba5de01032754877d8d1cbf`: 198 tests, 0 failures, 0 errors, 15 skipped; exit code 0.
- The 15 SQL Server-gated skips were:
  - 10 CORE-007 tests: missing `core007.sqlserver.it`.
  - 4 CORE-012 tests: missing `core012.sqlserver.it`.
  - 1 CORE-016 test: missing `core016.sqlserver.it`.
- The `SwaggerRuntimeSmokeTest` exercised the actual local Spring Boot server, Swagger UI, `/v3/api-docs`, and operation IDs for Horse update, baseline examination, weight, owner horse list, timeline, and owner training summary.
- `GeneratedIdentityTest` covers Microsoft JDBC generated-key labels (`GENERATED_KEYS` and `id`) and invalid/missing/ambiguous key results.
- Pinned Redocly CLI 2.63.0 lint passed for Flow 1, Flow 2, Flow 3, and shared OpenAPI.
- `git diff --check` passed.
- The five test-only fixture changes align H2 table definitions and seed rows with the canonical production schema; `database/schema.sql` and production source were not modified. No assertions were removed.
- Docker was unavailable, so H2 was used for portable tests. H2 results are not SQL Server evidence.

## API Coverage and Gaps

The three Flow 1/2/3 contracts contain **100 operations** (Flow 1: 20, Flow 2: 51, Flow 3: 29). Source-to-controller reconciliation finds **74 mapped and 26 missing**. The complete per-operation method/path/operationId/role/status/H2/SQL/dependency matrix is [api-wave1-coverage.md](./docs/api/api-wave1-coverage.md); detailed Flow 1 test evidence is also in [flow1-api-coverage.md](./docs/api/flow1-api-coverage.md). The previous 73/79 denominator and six-missing list were incomplete and are superseded by that matrix.

Other acceptance blockers:

- Owner Training Summary selection semantics have not received Leader approval.
- Horse photo local filesystem storage is development/test only; production-persistent storage/distribution remains unresolved.
- CORE-021's approved unordered recommendation behavior conflicts with the OpenAPI “best label first” wording.
- Groom assignment/notification mapping is absent; pre-session check remains fail-closed without a matching persisted notification.
- The 15 skipped SQL Server tests do not provide SQL Server evidence for the candidate APIs. No approved runner or out-of-band connection properties were available. No writes were made to `racehorse_demo` or `racehorse_integration_test`.
- The latest live Render probes timed out (HTTP 000); current readiness, Swagger/docs, CORS, and deployed SHA are unverified. No deployment was performed.

## Legacy Test Assets

- Existing test assets: JUnit/Spring Boot API and integration tests in `src/test/`.
- Browser UI migration: not applicable to this API-only backend.
- The candidate adds `GeneratedIdentityTest` for the generated-key regression and aligns existing H2 fixtures so the existing integration tests execute against required schema fields without weakening assertions. It does not add three new multi-step user-journey test files.

## Issues

| # | Severity | Description | Escalated To |
|---|---|---|---|
| 1 | BLOCKER | SQL Server integration gate was not run; dedicated runner/connectivity and approved out-of-band test connection properties are needed. | Leader / database environment owner |
| 2 | BLOCKER | Owner Training Summary selection semantics lack approved decision. | Leader |
| 3 | BLOCKER | Production-persistent photo storage/distribution is unresolved. | Leader |
| 4 | BLOCKER | Groom assignment/notification mapping is missing; pre-check must remain fail-closed. | CORE owner / Leader |
| 5 | FOLLOW-UP | Program recommendation ordering differs between approved policy and OpenAPI wording. | Leader / contract owner |
| 6 | BLOCKER | Live Render endpoints and deployed SHA could not be verified because probes timed out. | Leader / Render owner |
| 7 | BLOCKER | Seven-account provisioning has no verified ownership for each proposed mailbox, no approved account-creation path in the current backend, and no secure credential-delivery channel available to this session. | Leader / Auth owner |

## Release continuation status

### SQL Server gate

- Current workstation has no `tailscale` executable. `Test-NetConnection 100.83.34.4:1433` returned `False`; the SQL endpoint is unreachable from this runner.
- `sqlcmd` is installed, but cannot reach the SQL host. No SQL/JDBC environment variable names are configured, and Maven is not on `PATH` (`mvnw.cmd` is absent).
- The leader-approved `racehorse_integration_test` target is not accepted by the existing CORE-007/012/016 guards, which currently require per-CORE local test database identity checks. No guard was weakened, no database was created, and no SQL writes were made.
- Required next step: run on an approved Tailscale-connected runner with the test DB credentials supplied through a secret channel, or explicitly approve a narrowly scoped guard allowlist for this exact test database while retaining server identity, strict TLS, and a hard deny for `racehorse_demo`.

### Seven-role accounts

- The supplied manifest was reviewed. Its seven role names include `HORSE_OWNER`, which matches the candidate's `RoleCode`; the manifest also requires mailbox ownership verification before provisioning.
- No accounts were created, no passwords were generated, and no credentials were written or sent. The current Auth controller supports login/logout; `/api/auth/register` is documented but its registration/approval flow is not implemented (CORE-047 owns it). The manifest prohibits arbitrary direct SQL provisioning without an approved audited method.
- The latest request states that the Gmail addresses are group-managed, but mailbox control was not independently verified here. No approved password-manager share or phone-based passphrase delivery channel is available through this session. Existing H2 auth tests are not evidence that seven real Render accounts can log in.

### Current PR and live endpoints

- Candidate branch `integration/api-wave1-review-20261020` is at `6aa88dd1719414eb928b7a42edfd0c3a6ef65da5`; `origin` matches. The only commit after code validation SHA `3df9e6d38c60e947dba5de01032754877d8d1cbf` is documentation-only.
- PR #25 remains Draft. Tiến is the requested reviewer and was @mentioned for an independent review; no review has been recorded. The latest `image` check completed successfully, but this does not satisfy SQL, security, or review gates.
- Current remote refs observed: `dev` `5867f07ad521f2862eb80d5aa85c0bd615826855`; `main` `89991c1281edd8f2421b5ee57d6e1a283a70683d`. Nothing was merged.
- Latest requests to Render health, readiness, Swagger/docs, and the Vercel CORS preflight timed out (HTTP 000). The FE Vercel root returned HTTP 200. No Render database name or deployed SHA could be verified; no deployment occurred.
- The 26 missing operations are classified in [api-wave1-coverage.md](./docs/api/api-wave1-coverage.md): 19 P0 and 7 P1 according to the tracker. CORE-026 and CORE-035 are marked `Reassigned`; confirm their current assignees before taking or assigning implementation work.
