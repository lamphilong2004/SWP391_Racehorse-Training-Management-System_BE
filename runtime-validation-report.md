# Runtime Validation Report

**Updated**: 2026-10-21
**Target**: `C:\Users\ACER\Racehorse-Workspace\_integration-api-wave1-review-20261020`
**Validated candidate commit**: `3f13087423c49b07189ace4f516cbd7123e42ae6`
**Validation scope**: working tree at that commit, including uncommitted Swagger operation-ID annotations, smoke assertions, and API coverage updates

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Full Maven verify started the Spring Boot tests; the random-port Swagger runtime test passed. |
| Integration tests | PASS (H2/local) | 0 | Java 21 full `mvn verify`: 227 tests, 0 failures, 0 errors, 15 SQL Server-gated skips; 212 passed. |
| API runtime smoke | PASS (local only) | 0 | `SwaggerRuntimeSmokeTest` verified Swagger UI/docs and the newly integrated operation IDs. This is not live Render or SQL Server evidence. |
| OpenAPI lint | PASS | 0 | Pinned Redocly CLI 2.63.0 validated Flow 1, Flow 2, Flow 3, and shared OpenAPI files. |

**Overall**: NEEDS_SIGNOFF — SQL Server verification, independent review, and business/operational blockers remain. No deployment was performed.

## Environment

```text
environment:
  java: Microsoft OpenJDK 21.0.12.1 LTS
  maven: Apache Maven 3.9.16 (temporary local install)
  docker: UNAVAILABLE — `docker` command not found
  node: AVAILABLE — v24.20.0
  playwright: NOT REQUIRED — API-only backend; no browser-rendered UI
  infra-tier: FALLBACK(embedded/in-memory) — H2 test profile
  browser-tier: FRAMEWORK HTTP TEST — local Spring Boot HTTP smoke; browser E2E is not applicable
startup: PASS — full Maven verify and random-port Swagger runtime test passed
integration: PASS — 212 passed, 0 failed, 15 SQL Server-gated skipped; H2/local tests do not establish SQL Server fidelity
e2e: UNVERIFIED — no live Render or SQL-backed end-to-end writes; local Swagger runtime smoke is recorded separately
overall: NEEDS_SIGNOFF — SQL Server gate, independent review, and business blockers remain
```

The full Maven run completed successfully with Apache Maven 3.9.16 on Java 21.0.12.1. Maven was installed under the local temporary directory because it was not on `PATH`. Surefire reports are under `target/surefire-reports/`.

## Test Evidence

- Full Maven result on the working tree at candidate commit `3f13087423c49b07189ace4f516cbd7123e42ae6`: 227 tests, 0 failures, 0 errors, 15 skipped; exit code 0.
- The 15 skipped SQL Server-gated cases were 10 `PendingRegistrationSqlServerIntegrationTest` cases, 1 `HorseActivationSqlServerIntegrationTest` case, and 4 `StableBox*SqlServerIntegrationTest` cases. These skips are not SQL Server evidence.
- `SwaggerRuntimeSmokeTest` exercised the actual local Spring Boot server, Swagger UI, `/v3/api-docs`, and operation IDs for Flow 1 and the newly integrated Flow 2/3 endpoints.
- `GeneratedIdentityTest` covers Microsoft JDBC generated-key labels (`GENERATED_KEYS` and `id`) and invalid/missing/ambiguous key results.
- Pinned Redocly CLI 2.63.0 lint passed for Flow 1, Flow 2, Flow 3, and shared OpenAPI with no validation errors.
- `git diff --check` passed on the working tree.
- The five test-only fixture changes align H2 table definitions and seed rows with the canonical production schema; `database/schema.sql` and production source were not modified. No assertions were removed.
- Docker was unavailable, so H2 was used for portable tests. H2 results are not SQL Server evidence.

## API Coverage and Gaps

The three Flow 1/2/3 contracts contain **100 operations** (Flow 1: 20, Flow 2: 51, Flow 3: 29). Source-to-controller reconciliation finds **98 controller mappings and 2 operations with no route**. The per-operation matrix separately marks 93 as `MAPPED`, 2 photo operations as `PARTIAL`, 3 mapped operations as blocked by business/dependency decisions, and 2 operations as not implemented. The full method/path/operationId/role/status/H2/SQL/dependency matrix is [api-wave1-coverage.md](./docs/api/api-wave1-coverage.md); detailed Flow 1 test evidence is also in [flow1-api-coverage.md](./docs/api/flow1-api-coverage.md).

Other acceptance blockers:

- Owner Training Summary selection semantics have not received Leader approval.
- Horse photo local filesystem storage is development/test only; production-persistent storage/distribution remains unresolved.
- CORE-021's approved unordered recommendation behavior conflicts with the OpenAPI “best label first” wording.
- Groom assignment/notification mapping is absent; pre-session check remains fail-closed without a matching persisted notification.
- The 15 skipped SQL Server tests do not provide SQL Server evidence for the candidate APIs. Per the current workflow direction, the Leader will run SQL Server integration/E2E on the authorized SQL host; status is `SQL_NOT_RUN — LEADER_RUNNER_PENDING`. No writes were made to `racehorse_demo` or `racehorse_integration_test`.
- Live Render readiness, Swagger/docs, CORS, and deployed SHA were not checked in this validation phase. No deployment was performed.

## Legacy Test Assets

- Existing test assets: JUnit/Spring Boot API and integration tests in `src/test/`.
- Browser UI migration: not applicable to this API-only backend.
- The candidate adds `GeneratedIdentityTest` for the generated-key regression and aligns existing H2 fixtures so the existing integration tests execute against required schema fields without weakening assertions. It does not add three new multi-step user-journey test files.

## Issues

| # | Severity | Description | Escalated To |
|---|---|---|---|
| 1 | BLOCKER | SQL Server integration gate was not run; the authorized Leader SQL runner remains pending. | Leader / database environment owner |
| 2 | BLOCKER | Owner Training Summary selection semantics lack approved decision. | Leader |
| 3 | BLOCKER | Production-persistent photo storage/distribution is unresolved. | Leader |
| 4 | BLOCKER | Groom assignment/notification mapping is missing; pre-check must remain fail-closed. | CORE owner / Leader |
| 5 | FOLLOW-UP | Program recommendation ordering differs between approved policy and OpenAPI wording. | Leader / contract owner |
| 6 | NOT RUN | Render deployment and live endpoint verification are outside this validation phase. | Release owner |
| 7 | NOT RUN | Seven-role account provisioning is outside this validation phase; no accounts or credentials were created or handled. | Leader / Auth owner |

## Release continuation status

### SQL Server gate

- SQL Server tests remain unrun in this phase. The Leader will run the approved integration/E2E checks on the SQL host. No credentials were requested or exposed; no SQL guards were weakened and no database writes were made.

### Seven-role accounts

- Account provisioning is outside this validation phase. No accounts were created, passwords generated, or credentials transmitted. Existing local auth tests do not prove that seven real Render accounts can log in.

### Current PR and live endpoints

- Local candidate branch `integration/api-wave1-review-20261020` is at commit `3f13087423c49b07189ace4f516cbd7123e42ae6`; it is 67 commits ahead of `origin/dev` `5867f07ad521f2862eb80d5aa85c0bd615826855`. The working tree has uncommitted changes in the endpoint coverage/report, controller operation-ID annotations, and Swagger smoke assertions. These exact local changes passed full Maven verify and Redocly lint; they are not yet pushed.
- Remote PR #25 remains Draft at `d19d3321fbc9ddbfb66b937bad5ffb18aae5c1f7`. It is open against `dev`, with Tiến requested as reviewer and no reviews recorded. Its existing PR description is stale (74/26 coverage and old test counts); the current local report has 98 mapped and 2 blocked operations. The only reported check run on the remote PR head is `image` (success), which does not satisfy SQL, security, or independent-review gates.
- Candidate ancestry includes current fetched heads for PRs #17-24, #27, #30, and #31, plus combined PR #29 head `ab00863ba4369d7816820e5d4ec5f2820746619e`. The latest standalone PR #26 and #28 heads are not direct ancestors; the combined PR #29 is included, but the standalone heads have not been proven identical to it and must not be claimed superseded without a source diff review.
- Current remote refs observed: `dev` `5867f07ad521f2862eb80d5aa85c0bd615826855`; `main` `89991c1281edd8f2421b5ee57d6e1a283a70683`. Nothing was merged.
- Render and Vercel live endpoint checks were not run in this validation phase. No deployment occurred.
