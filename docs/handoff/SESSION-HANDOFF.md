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

_Section is updated by the CORE-004 task below._

## 8. CORE-033 — leader gate

_Section is updated by the CORE-033 task below._

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

- 2026-10-17: context recovered from live repos; this file created.
