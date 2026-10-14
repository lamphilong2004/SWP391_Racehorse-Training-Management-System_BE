# CORE-037 Flow 3 integration preparation

Status on 14 Oct 2026: **BLOCKED for full integration**. BE and FE worktrees were clean and on `feat/CORE-037-flow3-integration`. Only this BE CORE-037 worktree was edited; FE worktree was read only. BE inspected `origin/dev` is CORE-003 contract-only; FE inspected `origin/dev` is initial commit. Sources, dependency evidence and exact gaps are in the companion matrix and compatibility report.

At final read-only status check, the FE worktree had newly untracked `.env.core037.example` and `scripts/core037/` files that were absent at initial verification. This preparation did not write or remove them. Their provenance and suitability are unverified; they are not treated as approved CORE-036 or CORE-037 interfaces.

## Runnable checks

Requires Node.js 20+ and Git; no npm install, POM edit or application source edit. All modes are read-only. They do not create data or silently use mocks.

```powershell
node --test scripts/integration/core037.test.mjs
node scripts/integration/core037.mjs contract
node scripts/integration/core037.mjs live
node scripts/integration/core037.mjs sql
```

`contract` compares actual operation/method paths from unmerged BE OpenAPI and controller Git refs, plus FE model/service source. Known mismatches and missing dependency endpoints make it exit nonzero. It uses command-scoped Git safe-directory handling for the FE worktree, with no global Git config change.

`live` requires `CORE037_TEST_ENV=isolated-test`, `CORE037_BACKEND_URL` (HTTPS or loopback HTTP), ACTIVE Vet/Groom JWTs in environment variables, and a responding `/api/health` and `/api/auth/me`. It checks real authenticated case/task reads, missing/invalid JWT, wrong role, the Groom projection, optional approved fixture readbacks and optional cross-Groom list isolation. It never sends write requests. `CORE037_FRONTEND_URL` only checks that the route serves a page; browser route protection is a separate blocked case. A non-JSON/unavailable service blocks preflight. No endpoint or database absence can be counted as PASS.

`sql` requires the same test marker, `CORE037_SQL_READONLY=yes`, a SQL Server login supplied through `CORE037_SQL_*` and `SQLCMDPASSWORD`, and four positive fixture IDs. It runs only SELECT counts against real SQL Server tables via `sqlcmd`; use a database account with SELECT-only permissions. Its eight checks require an Incident linked to the case, examination, injury, plan, completed task, re-examination and restriction row. It checks record presence/links; compare clinical values and availability through API/browser in D01/P07/P10. It cannot prove the full workflow by itself.

Copy variable names from [`core037.env.example`](../../scripts/integration/core037.env.example) into your test runner's secret store. Never commit actual JWTs, passwords or `.env` files. `CORE037_CASE_ID`, `CORE037_INCIDENT_ID`, `CORE037_TASK_ID_OWNED_BY_GROOM1` and `CORE037_RESTRICTION_ID` must come from approved fixture data, not mock IDs. Re-run live and SQL modes after each dependency merges into `dev`; inspect all failures rather than overriding expectations.

## Decision and handoff gates

1. Merge and approve the listed official dependencies into both `dev` branches. Also integrate CORE-007/008 real auth, CORE-001 foundation, CORE-006 approved Flow 3 contract and the Flow 2/1 gates needed by CORE-026 and training availability.
2. Contract owners reconcile CORE-005 automatic abnormal Incident/notification against CORE-026 explicit Incident and approved BR-23/DEC-007. Until then C01 is blocked.
3. Medical/DB owners decide Groom assignment and repeated task materialization (G1), completion note/lift-reason persistence (G2), incident review transitions (G3), position3d encoding and model frame (G4), and restriction effective-time/exercise policy (G5). Do not alter canonical schema here.
4. CORE-026/035/019 owners provide the required production actor, IncidentLink, Groom scope, review and restriction adapters without copying their unmerged source into CORE-037. FE CORE-036 owner provides a real medical HTTP adapter and DTO mapping after approval. CORE-020 supplies restriction UI.
5. Provision an isolated SQL Server test environment and centrally approved demo data. Run contract, harness, actual BE/FE browser tests and SQL readback. Capture response IDs, role evidence, screenshots/HAR and SQL row evidence before declaring Flow 3 complete.

Current independently verified result: harness unit checks run; source contract comparison runs and exposes blockers. No real Backend/Frontend/SQL Server integration was exercised in this worktree. Passing branch unit tests or PR review cannot change that status.
