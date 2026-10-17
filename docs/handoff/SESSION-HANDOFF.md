# SESSION HANDOFF — SWP391 Racehorse Training & Management System

Last updated: 2026-10-17 (Claude session, leader: Lâm Phi Long)
Purpose: let the next Claude/Codex session continue from the real repository state without redoing work.

> No passwords, tokens, hashes or signing keys belong in this file. Local secrets stay in ignored files
> (`.demo-runtime/demo.env.local`, `.preview-local/`), never in Git or chat.

## 0. How to resume

1. `git fetch origin --prune` in both repos. Re-check every HEAD below. Treat this file as a snapshot, not the truth.
2. Read: this file → `C:\Users\admin\Downloads\RACEHORSE_PROJECT_MASTER_CONTEXT.md` → `RACEHORSE_SESSION_BOOTSTRAP.md`
   → tracker `Racehorse_Team_Project_Tracking_Gated_PR (1).xlsx` (Task Tracker, Task Playbook, Screen Registry)
   → `project-docs/decisions/DEC-001…DEC-010` → BE `docs/api/*.openapi.yaml` + `permission-matrix.md`.
3. Rules that stay in force:
   - No merge into dev/main without a leader gate PASS **and** Long's approval.
   - No force-push, no reset, no overwriting uncommitted work, no rewriting authors.
   - No DB create/drop/migrate/seed/reset without a separate approval.
   - Do not change business contracts or Figma. Live Figma `tm7dpgAQbHReZ7tlQeQJlc` is the source of truth.
   - Fixture/sample tests are never integration PASS.
   - One PR = one task, one owner, dependencies merged first.
   - Demo/preview branches are never merged.

## 1. Status vocabulary

| Term | Meaning |
|---|---|
| **Done** | PR merged and the change is confirmed on `origin/dev` |
| **In Review** | PR open, not Draft, waiting for leader gate |
| **Draft** | PR open as Draft; not ready for gate |
| **In Progress** | Branch exists (maybe pushed), no reviewable PR |
| **Blocked** | A dependency or a decision is missing |
| **Gate PASS / FAIL** | Result of an independent leader gate on an exact HEAD |

## 2. Team (tracker `Racehorse_Team_Project_Tracking_Gated_PR (1).xlsx`)

| Member | Track | Tasks |
|---|---|---|
| Lâm Phi Long | FE + Integration / Leader | 003, 009, 013, 023, 027, 029, 033, 034, 038, 039, 057 |
| Trần Khải Hoàng (`wangf28`) | FE | 002, 008, 010, 015, 020, 022, 025, 031, 041, 044, 050, 054 |
| Nguyễn Đình Minh Đức (`minhduc1204`) | FE + Integration | 006, 017, 018, 036, 037, 042, 046, 048, 052, 056, 058 |
| Cao Thanh Dũng (`Dung-05`) | BE | 001, 004, 007, 011, 012, 014, 016, 047, 049, 051, 053, 055 |
| Ngô Minh Tiến (`minhtienngo300-del`, git name `Tiens`) | BE | 005, 019, 021, 024, 026, 028, 030, 032, 035, 040, 043, 045 |

`CLAUDE.md` at the workspace root still lists Đức as Backend (roster of 2026-09-22). The tracker is newer.

## 3. Official dev (checked 2026-10-17)

| Repo | `origin/dev` | Merged tasks |
|---|---|---|
| FE `lamphilong2004/SWP391_Racehorse-Training-Management-System_FE` | `f7c290d` | CORE-002 (#1 → `6c5f1af`), CORE-046 **modal only, partial** (#4 → `501ae11`), shared TextArea/RadioGroup (#6 → `79ce689`), CORE-008 (#7 → `f7c290d`) |
| BE `lamphilong2004/SWP391_Racehorse-Training-Management-System_BE` | `8b2e22a` | CORE-003 (#1 → `84729d8`), CORE-006 (#3 → `fca8ddb`), CORE-001 (#8 → `9baa6a6`), CORE-005 (#2 → `8b2e22a`) |

### CORE-008 follow-up (merged with gaps; owner must confirm)

Verified on FE dev `f7c290d`:
1. No role guard. `RequireAuth` only checks authentication; there is no `allowedRoles` predicate and no 403 route experience.
2. `/dashboard` renders one generic role-labelled workspace. There is no per-role landing redirect. Screen Registry landings:
   CM `/dashboard`, HT `/training/programs`, TR `/dashboard`, WR `/rider`, Vet `/medical`, Groom `/groom/today`,
   Owner `/my-horses` (placeholder, DEC-008).
3. `path: '*'` is wrapped in `RequireAuth`, so an unknown public path redirects to login instead of the public 404.

Proposal: Hoàng fixes these as a CORE-008 follow-up (they are CORE-008 scope). **Not yet confirmed by the owner. Not moved to CORE-009.**

## 4. Backend — open work

| Item | Owner | State | HEAD | Notes |
|---|---|---|---|---|
| PR #10 CORE-007 Auth BE | Dũng | **Draft**, no reviews | `00de7ec` (2026-10-17 01:22) | 8 commits, 44 files. Adds migration `database/migrations/V20261016_01__create_registration_request.sql` (approved by Long 2026-10-16 per `docs/decisions/CORE-007-leader-decisions.md`) and `database/seeds/core007-auth-smoke.sql` (hash placeholders `__HASH_*__`, no secrets). JWT key from `JWT_SIGNING_KEY` env. **No independent gate on `00de7ec` yet.** Blocks CORE-009/011/012/019/021/047/049/051 and more. |
| PR #9 CORE-004 Flow 1 contract | Dũng | Open | `ac4e3a0` | Gate (Codex, 2026-10-17) = **FAIL semantic source alignment**: contract marks activation prerequisites / `activatedAt` timeline as NEEDS CLARIFICATION although DEC-009 §1 decides them. See §7. |
| CORE-019 restrictions BE | Tiến | In Progress, **no PR** | `07aa934` | Depends on CORE-007 (not merged). |
| PR #4/#5/#6 (CORE-014/026/035), PR #7 (CORE-037) | Đức (legacy) | Open / supersede | — | Do not merge. New owners port. |

## 5. Frontend — open work

| Item | Owner | State | HEAD | Notes |
|---|---|---|---|---|
| `feat/CORE-033-evaluation-fe` | Long | In Progress, see §8 | `324d511` | 1 commit ahead of dev `f7c290d`. Deps CORE-002 + CORE-005 are Done. |
| `feat/long-core013-027-029-ui` (worktree `long-main-ui`) | Long | Integration review branch, **no PR, never merge as a whole** | `8c19ac0` | 19 commits ahead of dev, including 10 by Hoàng (CORE-022) and 4 by Đức (CORE-036 PR #8, CORE-046 PR #5), all unmerged. Doc: `docs/LONG-MAIN-WEBSITE-UI.md`. Author tests 243/243 with fixtures and an explicit sample adapter (`VITE_BUSINESS_DATA_SOURCE=sample`). No independent gate. Real login not verified (SQL Server was stopped). Split into per-task PRs later: CORE-013 after CORE-004; CORE-027/029 after full CORE-046 (+ CORE-007/009 for real data). |
| `feat/CORE-022-program-creation` | Hoàng | In Progress, no PR | `4373a07` | 5 commits ahead of dev |
| `integration/CORE-022-033-036` | mixed | Integration branch, no PR | `18e3f29` | Never merge |
| FE PR #8 CORE-036 clean | Đức | **Draft** | `51525af` | Needs delta review |
| FE PR #5 CORE-046 Groom pages | Đức | **Draft** | `228e6d5` | Wrong base (`feat/CORE-046-incident-report-modal-shared`, not dev); router conflict with dev |
| FE PR #2 (CORE-036 old), #3 (CORE-037) | Đức | Legacy / blocked | — | Do not merge |
| CORE-025 | Hoàng | Not started (no branch/PR) | — | Do not implement in Long's branches |

## 6. Dependencies of Long's tasks

| Task | Needs | Now |
|---|---|---|
| CORE-033 | 002, 005 | **Ready** |
| CORE-013 | 002, 004 | Waiting for PR #9 |
| CORE-027 / CORE-029 | 002, 005, 046 | Waiting for full CORE-046 (PR #5) |
| CORE-009 | 007, 008 | Waiting for PR #10 + an approved isolated SQL Server DB and test accounts |
| CORE-023, 034, 038, 039, 057 | many | Blocked |

## 7. CORE-004 (PR #9) — leader gate

| Field | Value |
|---|---|
| Owner | Cao Thanh Dũng |
| Prior reviewed HEAD | `ac4e3a0` (base dev `8b2e22a`) |
| Prior gate | **FAIL — CHANGES REQUESTED** (2026-10-17) |
| Prior review | https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/9#pullrequestreview-5555422802 |
| Current re-gate HEAD | `1b84b591ceaf25276cc574018c783b1673e05221` (base dev `8b2e22a`) |
| Current gate | **BLOCKED** — official source documents are unavailable; see current re-gate evidence below. |
| Current review | Not posted; re-gate is BLOCKED. |
| Status | Waiting for Leader source/contract clarifications. **Not Merge Ready; not Done.** |

### Prior findings on `ac4e3a0` (historical review)

The prior review cited DEC-009 §1 and BR-05 (`business-rules.md` L20); the original source files were not available in the independent re-gate workspace.
1. L18-21: activation conditions and the `activatedAt` timeline start are marked NEEDS CLARIFICATION. L19-20 also says "recording the baseline does not activate the horse", which contradicts DEC-009.
2. L338-341 (timeline) and L788 (`HorseDetail.activatedAt`) keep the decided timeline-start question.
3. Pedigree L181, ownership L232-233 and box L420 leave the lifecycle effect as NEEDS CLARIFICATION and are inconsistent with L19-20.
4. Activation checklist L488-489 and L911-914: contents marked unspecified, items untyped (`items: {}`).

Valid open questions (not findings):
- audit action names
- timeline `eventType` vocabulary
- readiness review on INACTIVE horses
- `sex` value list
- Q-18 photo storage, Q-7 distance, Q-28 multiple owners
- ownership shares not enforced to 100 %

Prior technical checks at the old gate (historical; not reused as current evidence):
- Redocly lint 0/0
- dereferenced bundle, 0 `$ref`
- 20 operations' `x-roles` match `permission-matrix.md`
- status codes
- Flow 3 baseline path exists on dev
- `horse.lifecycle` / `activated_at` mappings

Blocks: CORE-012 (BE Flow 1), CORE-013 (Long FE), CORE-017 (Đức FE).
Prior next step (completed): Dũng pushed the correction commit; this re-gate checked the exact new HEAD above.

### Current independent re-gate on `1b84b591ceaf25276cc574018c783b1673e05221` (2026-10-17)

- **Gate: BLOCKED.** PR #9 remains open and unmerged. The PR head and `origin/feat/CORE-004-contract-flow1-owner` both resolve to the required SHA; `origin/dev` is `8b2e22a2aee710cc2ac9b3d1c372cb444ac9e21a`.
- **Official sources missing:** `project-docs/decisions/DEC-009-core-execution-scope-resolutions.md` and `project-docs/analysis/business-rules.md` were not found in the mounted workspace, project-control directory, backend repo, local refs/tags/history, or the corresponding GitHub paths on `dev` and the feature branch. Workspace copies of the master context and handoff are summaries, not replacements for these source documents.
- **P1 contract text:** the five activation conditions, conditional DRAFT → ACTIVE wording, CORE-016 ownership, timeline start, conditional re-evaluation descriptions, read-back guidance, and typed six-item checklist are present in the contract. Source-backed verification of those business rules remains blocked.
- **Baseline wording needs Leader clarification:** Flow 3 says “Does not activate the horse; CORE-016 owns activation.” Flow 1 says a successful baseline write is followed by condition re-evaluation and the horse transitions if all conditions are satisfied. Clarify whether Flow 3 means the endpoint does not own/perform the transition, or that a baseline write must never lead to activation as the final satisfied condition.
- **Technical checks rerun in a clean detached worktree at the required SHA:** Redocly lint passed (0 errors, 0 warnings); dereferenced bundle succeeded (0 remaining `$ref`); `git diff --check` passed; 20 operations and 20 unique `operationId`s; response-code inventory 401 (20), 403 (20), 404 (15), 409 (3); no manual activation/approve endpoint; checklist schema/example assertions passed; activation-related database columns and baseline constraints are present.
- **Scope:** base-to-head diff contains only `docs/api/flow1-horse.openapi.yaml`. Merge commit `037cc292198dcc4fc36552272e045d6a73713d25` has `8b2e22a2aee710cc2ac9b3d1c372cb444ac9e21a` as its second parent; no additional base-branch files appear in the PR diff.
- **Permission matrix gap:** 19 of 20 operation role sets match explicit matrix groups. `GET /api/horses/{id}/ownerships` is assigned `CLUB_MANAGER, HEAD_TRAINER`, but the matrix only lists ownerships in a write group and has no explicit read/history row; role mapping for this operation is unverified.
- **FE:** the contract describes reading `/activation-status` after mutation, but a search of the FE workspace found no matching activation-status/readiness/baseline API reference. FE integration is not verified.
- **Leader decisions needed:** provide the official DEC-009 §1 and BR-05 documents; resolve the Flow 1/Flow 3 baseline wording; specify or confirm the permission-matrix row for ownership-history GET. Until resolved, do not mark Merge Ready or unlock CORE-013.
- No current-head GitHub review was posted because the gate is BLOCKED. The linked review above applies only to the prior `ac4e3a0` HEAD.

## 8. CORE-033 — leader gate (investigation started, interrupted by the UI-first pivot)

- Branch `feat/CORE-033-evaluation-fe` `324d511`: 1 commit (Long), 11 files, scope clean, deps (002, 005) Done. **No PR yet.**
- Timeout investigation (clean worktree `scratchpad/w33`):
  - Calm run: 118/118.
  - Under artificial load (3 suites + `tsc -b` in parallel): failures spread across tests that already exist on dev — router, CORE-008 AuthProvider, CORE-046 IncidentReportModal — plus one CORE-033 test (anonymous redirect, 1 s `findBy`).
  - The two originally reported timeouts are "submits … COMPLETED" (normally ~0.5 s) and IncidentReportModal "requires horse…", which is CORE-046 code on dev.
  - Conclusion so far: load-sensitive suite, not a CORE-033 logic defect.
  - The dev-baseline stress comparison was **not completed** (the session ended). Re-run it before claiming a root cause.
- Fixed in `feat/dev-ui-harness` (`4721604`): duplicate chart x-labels for a single sample (React duplicate-key error).
- Next: finish the dev-baseline comparison, open a Draft PR from a clean CORE-033 branch, independent gate.

## 8b. UI-first / API-later (Leader decision 2026-10-17)

- Branch **`feat/dev-ui-harness`** (FE, pushed, HEAD `d06bf5b`), worktree `ui-demo/`.
  - Stacked on the integration branch `8c19ac0`. **Never PR the whole branch into dev**: split it per task once the dependencies merge.
- Commits:
  - `23937a0` chore(dev-harness): development-only harness. `npm run dev:harness` runs on :5190, simulated identity (no JWT), all `/api` calls go to the sample adapter, plus the DEV HARNESS bar (role switch, reset). Default off; verified absent from the production build.
  - `3257280` feat(CORE-029): Figma WR-D4 (426:10287) assignments and WR-D5 (427:10371) history tables.
  - `4721604` fix(CORE-033): chart label dedupe.
  - `d06bf5b` docs: `docs/UI-COVERAGE-MATRIX.md`, with the coverage matrix and the 3-flow click script.
- Verification on `4721604`: 249/249 tests (65 files, `--maxWorkers=1`), lint PASS, typecheck PASS, build PASS, diff-check PASS, dist contains no harness code.
- Browser walkthrough (sample adapter, NOT integration): Flow 1 CORE-013 register → pedigree/owner → box → pending activation; Flow 2 HT wizard → Groom normal + abnormal precheck + incident → WR start/sample/finish/feedback + interrupted → Trainer evaluation; Flow 3 Vet incident → case → exam → injury → treatment plan → Groom task complete → re-exam RECOVERED → resolve → close.
- Ownership:
  - Long: 013, 027, 029, 033.
  - Hoàng: 015, 020, 025, 031, 041, 044 (all missing).
  - Đức: 017 (`HorsePages.tsx` list/profile is CORE-017 code, handed over, not merged into 017).
- Prompts:
  - `project-context/handoffs/prompts-2026-10-17/HOANG-UI-FIRST.md` (+ CORE-008 follow-up and design-system segmented-tabs note)
  - `project-context/handoffs/prompts-2026-10-17/DUC-UI-FIRST.md` (CORE-017 + HorsePages handover + CORE-036/046 UX notes)
- A sample-aggregate attempt (max/avg HR, speed, duration) was **reverted**: an existing test asserts the adapter must not invent aggregates.
- Restart after reboot: `cd ui-demo && npm run dev:harness`, or `preview_start fe-harness` from `.claude/launch.json`. Then open http://localhost:5190/login.

## 9. CORE-007 (PR #10) — gate checklist for HEAD `00de7ec` (prepared, not executed)

Keep the PR as Draft until Dũng marks it ready. Then:

1. Re-verify HEAD and base and the 8-commit history. All authors are Dũng.
2. Scope matches tracker row CORE-007 (login, `/me`, JWT issue/validate, password encoder, 7-role RBAC, audit actor context) plus the approved pending-registration login boundary. No approval flow (CORE-047), no reset (CORE-049).
3. Contract: implement `docs/api/shared.openapi.yaml` + `permission-matrix.md` exactly (envelope, error codes, 401 vs 403 messages).
4. Decisions (`docs/decisions/CORE-007-leader-decisions.md`):
   - DB roles, not JWT claims, decide authorities on every request
   - inactive user → 403
   - logout 204 only after a `USER_LOGGED_OUT` audit row
   - no token blacklist
   - `app_user` is checked before `registration_request`
   - a pending credential never issues a JWT
5. Migration: `registration_request` must match the approved 14-column definition and must not change the canonical `database/schema.sql` (sha256 `416eb579…a9141`).
6. Secrets: none committed. JWT key and TTL come from env with empty defaults. The seed contains only placeholders.
7. `mvn verify` on a clean worktree: all tests pass, including the H2 `AuthenticationApiTest`. Do NOT point `AuthenticationApiTest` at SQL Server, because its setup deletes rows.
8. Live SQL check **only after separate approval**: an isolated throwaway DB, canonical schema + approved migration, placeholder-hash seed with locally generated hashes. Checks:
   - login
   - `/me`
   - wrong password → 401
   - non-ACTIVE user → 403
   - role revoke takes effect on the next request
   - logout → 204 plus the audit row
   Drop the DB afterwards.
9. Then a CORE-009 FE↔BE live run (runner prepared: `_gate-monitor/core009-live-auth-check.cjs`, preflight-only so far).

## 10. Local state (not on GitHub)

| Path | State |
|---|---|
| `SWP391_..._BE` main checkout | On `demo/main-flow-hour1` `f1f386d` with **4 uncommitted demo-hardening edits** (MedicalDtos/Ports/Service, WorkScopeAdapters) + untracked `CLAUDE.md`. Do not touch without Long. |
| `SWP391_..._FE` main checkout | On `demo/main-flow-hour1` `33fce51`, clean |
| `preview-20261017-fe` | Local branch `preview/local-20261017-fe` `821f69a`, **uncommitted** preview edits; not pushed |
| `preview-20261017-be` | Local branch `preview/local-20261017-be` `132531f` (CORE-007 + CORE-019 combined); not pushed |
| `core009-auth-integration-fe/-be` | Detached checkouts for CORE-009 (FE `f7c290d`, BE `022e028`) |
| `_gate-monitor/` | Codex gate evidence (`monitor-state.json`, CORE004 findings, CORE009 preflight, CORE033 checkpoint). `be-pr10-8c6adde` has staged files from an old check; leave it. |
| `.demo-runtime/` | Demo scripts + local secrets file (never commit) |

Codex's GitHub connector got HTTP 403 on writes, so its reviews and PRs were never published. The `gh` CLI used by Claude can write.

## 11. Tracker

`Downloads\Racehorse_Team_Project_Tracking_Gated_PR (1).xlsx` was last updated on 2026-10-15. It does NOT yet reflect:
- CORE-005 Done
- CORE-008 Done (with follow-up)
- CORE-046 partial merge
- the PR #9/#10 states

## 12. Next actions (ordered)

1. CORE-004: publish the gate result on PR #9 (this session).
2. CORE-033: investigate the timeouts, open a Draft PR, run the gate (this session).
3. CORE-007: gate per §9 when ready; ask for DB approval before any live check.
4. Review FE PR #8 (CORE-036). Ask Đức to retarget PR #5 to dev and resolve conflicts.
5. CORE-008 follow-up: confirm the owner (proposed: Hoàng).
6. Split `long-main-ui` into per-task PRs once dependencies merge.
7. Update the tracker from GitHub.

## 13. Session log

- 2026-10-17: context recovered from live repos; this file created. Shared copy pushed to BE branch
  `docs/session-handoff` → `docs/handoff/SESSION-HANDOFF.md` (no PR, never merge). `project-context/` and `project-docs/`
  are NOT under Git; this branch is the shareable copy.
- 2026-10-17: CORE-004 gate on `ac4e3a0` = FAIL, Request Changes review posted (§7).
- 2026-10-17: Presentation mode (`6a7b6f4`, pushed).
  - `npm run dev:present` runs on :5190 with the official Login, 7 local `.test` accounts (any non-empty password), no dev bar and no technical banner. The hidden `/presenter-tools` page resets data. `dev:harness` moved to :5192.
  - Guide: `docs/PRESENTATION-GUIDE.md`.
  - Re-verified all 3 flows in presentation mode with real sign-out/sign-in between 6 roles. A fresh tab has 0 console errors.
  - 249/249 tests, lint, typecheck and build pass. Dist is free of harness code.
- 2026-10-17: UI-first pivot. `feat/dev-ui-harness` created and pushed; 3-flow sample walkthrough done; CORE-029 Figma fixes; coverage matrix and prompts for Hoàng/Đức (§8b).
