# Project handoff checkpoint — three-core-flow local release

Resumable checkpoint. Update after each milestone. Last update: 2026-10-22 15:15 (+07), real-environment browser run.

## CURRENT STATE (read first)

- **Real stack:** FE http://127.0.0.1:5173 (Vite dev, branch `fix/fe-full-suite-20261022` = FE main + #23 + #24) →
  BE http://127.0.0.1:8080 (branch `feat/three-flow-closure-20261022` = BE main + #33) → SQL Server DB
  **`racehorse_integration_test`** (Leader's original DB). Private launcher outside Git:
  `rh-main-20261022\runtime\RunOriginalBackend.ps1`. Backup before changes:
  `...\MSSQL\Backup\racehorse_integration_test_before_final_20261022.bak` (COPY_ONLY, CHECKSUM, verified).
- **Original accounts (unchanged):** clubmanager@ / headtrainer@ / trainer@ / workrider@ / veterinarian@ / groom@ /
  horseowner@gmail.com. The Leader signs in; the agent drives the browser (its policy blocks password handling).
- **Merges blocked on review:** BE #33 (agent merge without review denied by policy) and FE #24 (FE main ruleset needs
  a non-author approval; #24 contains #23 and disables Vercel main deploys, so merge #24 only).
- **Real reference data:** 5 horses with sourced identity/pedigree (docs/data/REAL-HORSE-REFERENCE-DATA.md), DRAFT
  until the club enters owner, box and Vet baseline. No operational/medical history invented for them.
- **Browser-verified Training journey (original DB, real APIs, SQL readback):** HT template/program pages, program
  pause/resume, reassign dialog, adjustment review → Trainer adjustment request, Walk exercise, session 7 wizard,
  Groom assignment → Groom pre-check ALL_NORMAL → HT approves request #1 → Work Rider start, manual sample, complete,
  feedback → Trainer evaluation MET → program Progress/History, Performance History.
- **Fixed during the run:** EF-4 Trainer candidates; adjustment/program-management screens; optional owner at pedigree
  step; record-linked parents; country of origin field; rider surface + start-gate message; header/breadcrumb
  overflow; a11y names; greeting; gait codes; feedback wording; evaluation time format; adjustment audit rows.
- **Open decisions/gaps:** distance aggregation semantics (contract OPEN); no UI to apply an approved phase change
  (PUT phases exists; program period cannot be extended); request #1 and its approval predate the audit fix, so
  Program 4 history lacks those two rows; Medical (Vet) and Horse Owner browser passes not yet run in this DB.

## Git state

| Repo | `main` | Notes |
|---|---|---|
| BE | `2d443b3d8c63955aaffa5cf873804086eb6b3563` | Merge commit of PR #32 (`6b60fc8`), approved by Tiến (incl. local-only precheck freshness waiver) and Đức on exact HEAD. Authored history preserved. |
| FE | `f3676d142d617087892531cbe74cc1f91d0408d4` (unchanged) | PR #23 `2f96bb9` approved (Đức, exact HEAD), CLEAN, 0 open threads. **Merge on hold**: FE `main` auto-deploys to the Vercel production project `racehorse-official-fe`; the Leader will disable that deploy first. |

- BE PR #25 (old CHANGES_REQUESTED) is superseded: its head `0fcba42` is an ancestor of #32; Tiến's #32 approval
  explicitly accepts the local-only freshness limitation. Not merged separately. A supersession comment on #25 still
  needs to be posted by the Leader (agent comment was blocked by the permission policy).
- BE fix branch: `feat/three-flow-closure-20261022` (this document's branch) — see "Changes" below.

## Verified (observed, not inferred)

Environment: Windows 11, SQL Server 16.0.1000.6, strict JDBC TLS, Java 21.0.12, Node 22.15.1.

1. **Fresh install from BE `main`** into new DB `racehorse_local_main_20261022`: `Install-LocalDatabase.ps1 -SeedTestAccounts`
   → `LOCAL_INSTALL_PASS`, seven role accounts seeded (DPAPI). BE `main` jar starts with JPA validate; `/api/health`,
   Swagger 200; FE (PR #23 build, API mode) `/login` 200 and `/api` proxy 200.
2. **Fresh-clone blocker found and fixed**: a new DB has no stable boxes or skills and there is no API to create them,
   so Flow 1 stable assignment and Flow 2 recommendations cannot complete. Added idempotent reference seed.
3. **Real-API three-flow journey** (`scripts/smoke/Run-FlowSmoke.ps1`, FE proxy → Spring Boot → SQL Server), final build:
   Flow 1 20/20, Flow 2 program 17/17, Flow 2 sessions 48/48, Flow 3 41/41 → **126/126 PASS**, `FLOW_SMOKE_PASS`.
   Covers 401/403 role denials, DRAFT → pedigree/owner/box → Vet baseline → auto ACTIVE, Head Trainer readiness review,
   template/version → recommendations → program + Trainer, session scheduling with suitability/rider/groom, Groom
   precheck gate (missing/invalid/duplicate), Work Rider start, Trainer pause/resume, duplicate-transition 409s,
   metrics, complete, rider feedback, Trainer evaluation; second session interrupted by Work Rider issue → incident →
   Vet OPEN_CASE → exam/injury/treatment plan + Groom task → LIMITED restriction with prohibited exercise (suitability
   hard block, create rejected, allowed exercise accepted) → lift → LOCKED (scheduling rejected) → Groom task complete →
   closure blocked until RECOVERED re-exam and incident RESOLVED → lift → close; Trainer/Head Trainer/Owner scoped reads.
4. **SQL readback** (sqlcmd, `-N` strict TLS, SELECT only): horses ACTIVE/FIT, sessions COMPLETED/INTERRUPTED with UTC
   `actual_start/end`, incidents RESOLVED, cases CLOSED. Incident `reported_at - observed_at`: 25 200 s before the fix,
   0 s after.
5. **BE tests** (H2, final tree): 308 run, 0 failures, 0 errors, 15 skipped (SQL Server-only ITs). H2 is not SQL Server proof.

6. **FE full suite** (first full run on FE #23 `2f96bb9`; earlier evidence was focused files only): 39 failed / 923
   passed. All causes were in tests or test config (stale selectors after the My Account/link UI change, route list,
   a `beforeEach` returning the mock, a wrong fixture shape, parallel timeouts). Fixed in FE draft PR #24
   (`673a62d`, stacked on #23): **962/962 PASS** (parallel ×2, single-worker), lint/typecheck/build PASS.
   Source audit: FE session actions match the backend roles (Rider: start/complete/report-issue/feedback/metrics;
   Trainer Live Monitoring: pause/resume/stop). The Trainer stop UI always sends `medicallyRelevant: false`. The
   medical path from a ride is the Rider's report-issue.
7. **Browser E2E, Head Trainer: PASS** (FE #23 build on 5174 → BE 8081; Leader signs in, the agent drives; the
   agent's policy blocks it from reading the DPAPI passwords). Template create (201 + list refresh) → 7-step
   program wizard → `POST /api/training-programs` 201 → program 9 ACTIVE, confirmed in SQL (Trainer assignment,
   phase LOW–MEDIUM) → Program Detail Overview/Progress/History → Program List → Training Calendar (local time,
   BLOCKED/COMPLETED/INTERRUPTED). No console errors.
   - **P0 found and fixed (BE, EF-4):** Trainer recommendations returned only skill-matched Trainers, and an empty
     list for templates without required skills. The wizard only allows picking a listed candidate, so the Head
     Trainer hit a dead end. Approved EF-4 (business-flows.md) allows force-assigning past a *warning*. Now every
     ACTIVE Trainer is listed: matched ones as SUITABLE, unmatched ones as NOT_RECOMMENDED with a warning. Create
     and reassign require `warningsAcknowledged` for an unmatched Trainer (400 otherwise). Verified in the browser:
     warning shown → Continue blocked until acknowledged → created. BE tests 310/0/15.
   - **Missing FE (approved Flow 2, BE APIs exist):** Program Adjustment Requests (Trainer submits; Head Trainer
     reviews and approves/rejects, SCR-TRAIN-024), Head Trainer program pause/complete (`PATCH status`) and Trainer
     reassignment (`PUT trainer-assignment`). Needs the Figma frames (Figma MCP needs `/mcp` auth) before building.
   - Minor: duplicate-name horses get identical radio labels ("Select <name>"); wizard draft is lost on reload;
     program-list summary cards show "Not provided" (no API). Template recommendations are empty unless the
     template's "Suitable horse profile" matches the horse's breed/sex (by design).

## Changes on `feat/three-flow-closure-20261022`

- **fix(time)**: raw SQL used `CURRENT_TIMESTAMP` (SQL Server local time, UTC+7 here) for instants while the schema and
  app treat DATETIME2 as UTC → `reported_at`, `actual_start/end`, `submitted_at`, `assigned_at`, `updated_at`, restriction
  effective checks skewed by 7 h. Replaced with `SYSUTCDATETIME()` in 11 files. `CAST(CURRENT_TIMESTAMP AS DATE)`
  date-scope checks intentionally unchanged (server-local date; see debt). H2 alias + `SqlUtcTimestampGuardTest`.
- **fix(openapi)**: 10 DTO simple-name collisions (`CreateRequest`, `ExerciseInput`, `Session`, `Page`, `UserRef`, …)
  made Swagger document wrong request bodies (e.g. `POST /api/incidents` showed the session-create body). Nested DTOs are
  now named `EnclosingType + Name` (e.g. `IncidentApiCreateRequest`). No runtime/API behavior change; no FE codegen uses names.
- **chore(local)**: `database/seeds/local-reference-data.sql` + `Seed-LocalReferenceData.ps1` + `Install-LocalDatabase.ps1
  -SeedReferenceData`; `scripts/local/Start-LocalBackend.ps1`; `scripts/smoke/*` (verified runner); docs in
  `docs/LOCAL-FIRST-DATABASE.md`.

## Contract facts confirmed at runtime (not changed)

- Readiness review: HEAD_TRAINER only, 204. Start: Trainer or assigned Work Rider, only at/after `scheduledStart`,
  requires ALL_NORMAL precheck. Pause/resume/stop: Trainer. Complete/report-issue/feedback: Work Rider. `report-issue`
  interrupts the session. One active medical restriction per horse (escalation = lift then create). Case closure needs a
  RECOVERED latest re-exam and all linked incidents resolved. `GET /api/groom/sessions` requires `date`;
  `GET /api/training-sessions` requires `from`/`to`; `GET /api/horses/{id}/trainer-recommendations` requires `templateVersionId`.

## Remaining (next executable steps)

1. Leader: disable Vercel Git deploy for FE `main` (or approve the deploy), then merge FE #23, then FE #24 (merge commits).
2. Independent review of BE #33; merge.
3. Browser E2E of the same journeys through the FE UI (unblock sign-in first, see item 7), fix FE gaps found.
4. Phase D UI modernization only after 3, with a design proposal for leader approval first.

## Known debt (do not claim production-ready)

- Precheck freshness: SCHEDULED-only, no TTL / PAUSED re-check — accepted for LOCAL DEVELOPMENT ONLY (Tiến, BE #32).
- `CAST(CURRENT_TIMESTAMP AS DATE)` date scopes use SQL Server local date; CORE-035 uses Java club-local date. Unify later.
- No management API for stable boxes / skills (seed only). Production email/reset delivery and photo storage are dev-only.
- Browser E2E of the three flows not yet run; FE `main` not yet updated; second-machine install not yet verified.

## Resume commands (Long's machine; private helper outside Git)

```powershell
. 'D:\FPT\Kì_9\SWP391\rh-main-20261022\runtime\Use-MainEnvironment.ps1'   # sets RH_* for racehorse_local_main_20261022
Set-Location 'D:\FPT\Kì_9\SWP391\rh-main-20261022\be'; mvn -B -ntp -DskipTests package
./scripts/local/Start-LocalBackend.ps1 -Port 8081 -FrontendOrigins 'http://localhost:5174','http://127.0.0.1:5174'
# FE (rh-main-20261022\fe): VITE_API_PROXY_TARGET=http://127.0.0.1:8081; vite preview --port 5174
./scripts/smoke/Run-FlowSmoke.ps1 -FrontendBase 'http://127.0.0.1:5174' -BackendBase 'http://127.0.0.1:8081'
```

The original baseline processes on 8080/5173 (PIDs 4780/22472, older release heads) were left running untouched.
