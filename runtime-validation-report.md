# Runtime Validation Report

**Generated**: 2026-10-20T14:12:43Z
**Target**: `C:\Users\ACER\Racehorse-Workspace\_integration-api-wave1-review-20261020`
**Validated code SHA**: `492dba71cbaa1e7ce5fbbe7d47300ca39bf2eca9`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | The Spring Boot random-port `SwaggerRuntimeSmokeTest` started the application and received HTTP 200 from `/swagger-ui.html` and `/v3/api-docs`. Startup duration was not separately measured. |
| Integration tests | PASS | 0 | Java 21 full `mvn verify`: 194 tests, 0 failures, 0 errors, 15 skipped; 179 passed. |
| API runtime smoke | PARTIAL | 0 | One HTTP smoke test verified Swagger UI/docs and six operation IDs. This is local H2-backed evidence, not a live Render or SQL Server test. |

**Overall**: NEEDS_SIGNOFF — SQL Server verification is pending, and several business/operational blockers remain.

## Environment

```text
environment:
  docker: UNAVAILABLE — `docker` command not found
  node: AVAILABLE — v24.20.0
  playwright: NOT REQUIRED — API-only backend; no browser-rendered UI
  infra-tier: FALLBACK(embedded/in-memory) — H2 test profile used because Docker is unavailable
  browser-tier: FRAMEWORK HTTP TEST — local Spring Boot HTTP smoke; browser E2E is not applicable
startup: PASS — Spring Boot random-port startup in SwaggerRuntimeSmokeTest; Swagger UI/docs returned HTTP 200; elapsed time not measured
integration: PASS — exit_code: 0, passed: 179, failed: 0, skipped: 15; full Maven verify on Java 21 using H2; SQL Server fidelity is not covered
e2e: PARTIAL — exit_code: 0, passed: 1 smoke test, failed: 0; Swagger UI/docs and selected operation IDs; no live Render or database-backed end-to-end writes
overall: NEEDS_SIGNOFF — SQL Server gate and business/deployment blockers remain
```

The recorded full Maven run completed successfully and its Surefire reports are under `target/surefire-reports/`. Maven is not on the current PowerShell `PATH`, and this report does not claim a second run from this shell. The test-fixture commit contains the exact source state used by the recorded full run.

## Test Evidence

- Full Maven result: 194 tests, 0 failures, 0 errors, 15 skipped; exit code 0.
- The 15 SQL Server-gated skips were:
  - 10 CORE-007 tests: missing `core007.sqlserver.it`.
  - 4 CORE-012 tests: missing `core012.sqlserver.it`.
  - 1 CORE-016 test: missing `core016.sqlserver.it`.
- The `SwaggerRuntimeSmokeTest` exercised the actual local Spring Boot server, Swagger UI, `/v3/api-docs`, and operation IDs for Horse update, baseline examination, weight, owner horse list, timeline, and owner training summary.
- Pinned Redocly CLI 2.63.0 lint passed for Flow 1, Flow 2, Flow 3, and shared OpenAPI.
- `git diff --check` passed.
- The eight test-only fixture changes align H2 table definitions and seed rows with the canonical production schema; `database/schema.sql` and production source were not modified. No assertions were removed.
- Docker was unavailable, so H2 was used for portable tests. H2 results are not SQL Server evidence.

## API Coverage and Gaps

The Draft integration PR includes an endpoint coverage snapshot and source PR ancestry. The detailed Flow 1 per-operation methods, roles, and H2 evidence are in [flow1-api-coverage.md](./docs/api/flow1-api-coverage.md).

Across the inspected Flow 1, Flow 2, and Flow 3 contract families, 73 of 79 operations have candidate controller mappings. Six contract operations are not registered:

| Method and path | Operation ID | Contract role | Status / dependency |
|---|---|---|---|
| `PATCH /api/training-programs/{id}/status` | `setProgramStatus` | HEAD_TRAINER | Missing from candidate controller mappings. |
| `POST /api/training-programs/{id}/adjustment-requests` | `createAdjustmentRequest` | TRAINER | CORE-040; not implemented in this candidate. |
| `POST /api/training-sessions/{id}/reduce-intensity` | `reduceSessionIntensity` | TRAINER | Missing from candidate controller mappings. |
| `POST /api/training-sessions/{id}/metrics` | `submitMetricSample` | WORK_RIDER | CORE-030; not implemented; no simulated metrics exposed. |
| `POST /api/training-sessions/{id}/evaluation` | `submitEvaluation` | TRAINER | CORE-032; not implemented. |
| `GET /api/training-sessions/{id}/result` | `getSessionResult` | HEAD_TRAINER, TRAINER, WORK_RIDER, CLUB_MANAGER, VETERINARIAN | CORE-032; not implemented. |

Other acceptance blockers:

- Owner Training Summary selection semantics have not received Leader approval.
- Horse photo local filesystem storage is development/test only; production-persistent storage/distribution remains unresolved.
- CORE-021's approved unordered recommendation behavior conflicts with the OpenAPI “best label first” wording.
- Groom assignment/notification mapping is absent; pre-session check remains fail-closed without a matching persisted notification.
- The 15 skipped SQL Server tests do not provide SQL Server evidence for the candidate APIs. No approved runner or out-of-band connection properties were available. No writes were made to `racehorse_demo` or `racehorse_integration_test`.
- Staging readiness was previously reported as HTTP 503; no deployment was performed.

## Legacy Test Assets

- Existing test assets: JUnit/Spring Boot API and integration tests in `src/test/`.
- Browser UI migration: not applicable to this API-only backend.
- The candidate work added no new user-journey tests; it aligned existing H2 fixtures so the existing tests could execute against required schema fields without weakening assertions.

## Issues

| # | Severity | Description | Escalated To |
|---|---|---|---|
| 1 | BLOCKER | SQL Server integration gate was not run; dedicated runner/connectivity and approved out-of-band test connection properties are needed. | Leader / database environment owner |
| 2 | BLOCKER | Owner Training Summary selection semantics lack approved decision. | Leader |
| 3 | BLOCKER | Production-persistent photo storage/distribution is unresolved. | Leader |
| 4 | BLOCKER | Groom assignment/notification mapping is missing; pre-check must remain fail-closed. | CORE owner / Leader |
| 5 | FOLLOW-UP | Program recommendation ordering differs between approved policy and OpenAPI wording. | Leader / contract owner |
