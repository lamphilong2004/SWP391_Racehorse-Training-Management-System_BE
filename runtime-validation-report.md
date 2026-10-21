# Runtime Validation Report

**Updated**: 2026-10-21
**Target**: `C:\Users\ACER\Racehorse-Workspace\_integration-api-wave1-review-20261020`
**Validated code/test SHA**: `4a76a4511b395405d83d2652a1ddc168ee240866`
**Validation scope**: full Maven verify, focused Swagger smoke and pinned Redocly lint were run with this exact candidate HEAD; no production or test source changes followed

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Startup | PASS | 0 | Full Maven verify started the Spring Boot tests; the random-port Swagger runtime test passed. |
| Integration tests | PASS (H2/local) | 0 | Java 21 full `mvn -B -ntp verify`: 227 tests, 0 failures, 0 errors, 15 SQL Server-gated skips; 212 passed. |
| API runtime smoke | PASS (local only) | 0 | Focused `SwaggerRuntimeSmokeTest`: 1 test, 0 failures/errors/skips; verifies Swagger UI/docs and integrated operation IDs. Not live Render or SQL Server evidence. |
| OpenAPI lint | PASS | 0 | Pinned Redocly CLI 2.63.0 validated Flow 1, Flow 2, Flow 3, and shared OpenAPI files. |

**Overall**: BLOCKED — PR #25 has `CHANGES_REQUESTED`; SQL Server verification and approved business decisions remain open. No deployment was performed.

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
overall: BLOCKED — PR #25 has CHANGES_REQUESTED; SQL Server gate and approved business decisions remain open
```

The full Maven run completed successfully with Apache Maven 3.9.16 on Java 21.0.12.1. Maven was installed under the local temporary directory because it was not on `PATH`. Surefire reports are under `target/surefire-reports/`.

## Test Evidence

- Full Maven result on candidate SHA `4a76a4511b395405d83d2652a1ddc168ee240866`: 227 tests, 0 failures, 0 errors, 15 skipped; exit code 0.
- Focused Swagger runtime result on the same code/test tree: 1 test, 0 failures, 0 errors, 0 skipped; exit code 0.
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
- CORE-026 Groom/session assignment and revocation mapping is not approved or persisted; the pre-session route requires a notification row and is not a canonical assignment lifecycle.
- CORE-026 resume freshness and paused-session re-check rules have no approved freshness window or re-check state transition. The current schema allows one pre-session check per session.
- CORE-035 Trainer medical-case list/detail filters differ: list requires `effective_to IS NULL`, while detail also accepts `effective_to >= today`. The Flow 2 contract says Trainer actions require an open `trainer_assignment`; no CORE-035-specific boundary decision was found.
- `reduceSessionIntensity` is explicitly marked proposed and needs contract confirmation; `listGroomSessions` has no route because the Groom/session mapping is unresolved.
- The 15 skipped SQL Server tests do not provide SQL Server evidence. Status is `SQL_NOT_RUN — BLOCKED`: this runner lacks Tailscale, required SQL/JDBC configuration, and network access to `100.83.34.4:1433`; the existing CORE-007/012/016 guards reject the shared `racehorse_integration_test` name and require their dedicated per-CORE test database identities. No guard was bypassed and no writes were made to either database.
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
| 4 | BLOCKER | CORE-026 Groom-to-session assignment/revocation lifecycle and paused-session pre-check freshness/state rules are not approved; do not widen access or invent a freshness duration. | Tiến (CORE-026 owner) / Leader |
| 5 | BLOCKER | CORE-035 list/detail Trainer assignment boundary is inconsistent; Flow 2 says open assignment, but no medical-specific boundary decision exists. | Tiến (CORE-035 owner) / Leader |
| 6 | BLOCKER | `reduceSessionIntensity` signature is proposed and unconfirmed; `listGroomSessions` depends on the unresolved assignment mapping. | Tiến / Leader |
| 7 | FOLLOW-UP | Program recommendation ordering differs between approved policy and OpenAPI wording. | Leader / contract owner |
| 8 | NOT RUN | Render deployment and live endpoint verification are outside this review-fix task. | Release owner |
| 9 | NOT RUN | Seven-role account provisioning is separate; no accounts or credentials were created or handled. | Leader / Auth owner |

## Review handoff and ownership

The current tracker assigns CORE-026 and CORE-035 to Tiến (`Reassigned` status); no competing implementation was started. The review was submitted on candidate SHA `4a76a4511b395405d83d2652a1ddc168ee240866`.

| Scope | Current owner and latest evidence | Finding / required handoff |
|---|---|---|
| CORE-026 Groom/session checks | Tiến; PR #23 head `c01274f53f74e96a3213da955616e60cbaeff00b`, included in candidate ancestry. | `Core026Repository.hasGroomSessionNotice` checks a notification tuple, not a canonical persisted Groom-to-session assignment with revocation. Flow 2 marks assignment persistence as a model gap and forbids club-wide access. Request Tiến's approved assignment/notice/revocation lifecycle and latest fix branch/commit; add assigned, unassigned, revoked authorization tests. Keep access fail-closed meanwhile. |
| CORE-026 pre-check freshness/resume | Tiến; same ownership. | `Core026Service.submitPrecheck` only accepts SCHEDULED sessions; `training_session_id` is unique in the pre-check schema; `TrainingSessionService.resume` checks `ALL_NORMAL` but not `performed_at`. No approved freshness duration or paused-session re-check transition was found. Request Tiến/Leader decision on permitted states, freshness criteria and re-check lifecycle; then test stale ALL_NORMAL, ABNORMAL, fresh, paused/resume and disallowed states. Do not invent a timeout. |
| CORE-035 medical-case scope | Tiến; standalone PR #26 head `43d50ae784780ad97ce0aae211c487863d97d962` is not an ancestor; combined PR #29 head `ab00863ba4369d7816820e5d4ec5f2820746619e` is an ancestor. | `MedicalService.list` requires `effective_to IS NULL`; detail allows null or `>= today`. Flow 2 defines Trainer actions through an open `trainer_assignment`; the inclusive-range CORE-012 decision found is for stable assignments, not medical reads. Ask Tiến/Leader to confirm the intended predicate and align list/detail without expanding access; test end date null/today/future/past, inactive/revoked assignment and unauthorized resource enumeration. |
| CORE-024 / `reduceSessionIntensity` | Tiến; PR #21 head `3aa2ba24a9d7b6cb18b8288c378dd977905985e5`, included in candidate ancestry. | `reduceSessionIntensity` is marked proposed/needs confirmation. Do not implement the proposed write route until its contract signature and business rule are approved. |
| CORE-026 / `listGroomSessions` | Tiến; PR #23 head above. | No route exists because the approved persisted Groom/session mapping is missing. Implement only after the mapping and ownership are approved. |
| Other Tiến-owned scopes | CORE-030 PR #27 `0f13fa4528fd46b1e14138b0c442ea6f371d2770`; CORE-032 PR #30 `e6a33b278a1973295996d4fdd0b8634118812b75`; CORE-040 PR #31 `d6437cc469dc44492e3e80f7df6d492bcf64bc35`; CORE-045 standalone PR #28 `86b0ed88d02172994332b2ea6cea2e9c481a0f60` and combined PR #29 above. | Preserve Tiến's assigned scope. PR #29 is included; do not state that standalone PR #26/#28 are superseded without a direct diff reconciliation. |

**Requested next handoff:** Tiến to provide the latest CORE-026/035 fix branch/PR/commit and identify approved decisions governing Groom assignment/revocation, pre-check freshness/re-check states, and Trainer `effective_to` semantics. Leader to resolve any missing business decision. Integrate only after inspecting the owner's actual diff.

## Release continuation status

### SQL Server gate

- SQL Server remains blocked on this runner: `tailscale` is unavailable, no SQL/JDBC environment variable names are configured, and `100.83.34.4:1433` is unreachable. `sqlcmd` is installed but has no supplied connection configuration.
- The gated suite skips 10 CORE-007 tests, 4 CORE-012 tests, and 1 CORE-016 test. Its current guards require loopback SQL Server targets and per-CORE database names; CORE-007 additionally requires its strict-TLS installer receipt. They do not accept the approved shared `racehorse_integration_test` name. No database was created/reset, no guard was weakened, no credentials were requested or exposed, and no SQL writes were made.
- Required next step: provide an authorized SQL-host runner and an approved, guard-compatible test target/configuration for the existing database, or obtain an explicit reviewed guard change that preserves exact server/database identity checks, strict TLS and a hard deny for `racehorse_demo`. No SQL PASS is claimed.

### Seven-role accounts

- Account provisioning is outside this validation phase. No accounts were created, passwords generated, or credentials transmitted. Existing local auth tests do not prove that seven real Render accounts can log in.

### Current PR and live endpoints

- Candidate branch `integration/api-wave1-review-20261020` was at `4a76a4511b395405d83d2652a1ddc168ee240866` at verification start, 69 commits ahead of `origin/dev` `5867f07ad521f2862eb80d5aa85c0bd615826855`. The worktree was clean before this report update.
- PR #25 is open, **not Draft**, unmerged, and has Tiến's `CHANGES_REQUESTED` review on `4a76a4511b395405d83d2652a1ddc168ee240866`. The `image` workflow completed successfully on that SHA. The PR description is stale and must be refreshed after this documentation update is pushed. Do not merge or claim ready.
- Current fetched source PR heads are verified as ancestors for #17-24, #27, #30, and #31; combined PR #29 head `ab00863ba4369d7816820e5d4ec5f2820746619e` is also an ancestor. Standalone PR #26 head `43d50ae784780ad97ce0aae211c487863d97d962` and PR #28 head `86b0ed88d02172994332b2ea6cea2e9c481a0f60` are not direct ancestors. PR #29 is included, but #26/#28 have not been proven identical to it and must not be claimed superseded without an explicit source diff review.
- Current remote refs observed: `dev` `5867f07ad521f2862eb80d5aa85c0bd615826855`; `main` `89991c1281edd8f2421b5ee57d6e1a283a70683`. Nothing was merged.
- Render and Vercel live endpoint checks were not run in this validation phase. No deployment occurred.
