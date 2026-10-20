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
| 7 | BLOCKER | The PR description still needs correction to the 100-operation denominator; no authorized GitHub PR-body write tool is available in this session. | PR author / GitHub maintainer |
