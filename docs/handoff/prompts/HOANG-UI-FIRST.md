# Prompt cho Trần Khải Hoàng — UI-first, 3 Main Flow (2026-10-17)

**Tin nhắn Zalo (gửi kèm file này):**

> Nhóm chuyển sang chiến lược UI-first / API-later: làm xong UI chính thức theo Figma, chạy bằng Sample Adapter, khi BE xong thì chỉ thay adapter. Em có 6 màn đang thiếu: CORE-015 (Vet baseline), CORE-020 (Restrictions), CORE-025 (Trainer dashboard + session), CORE-041 (HT template/program detail), CORE-044 (Trainer support) và CORE-031 (Live monitoring, UI trước, STOMP sau). Thứ tự gợi ý: 025 → 015 → 041 → 020 → 044 → 031. Mỗi CORE một nhánh, một prompt bên dưới. Dùng `npm run dev:harness` để chạy và thao tác không cần BE. Không merge, không sửa code của người khác; xong CORE nào gửi Long link nhánh/PR và kết quả test.
>
> Ngoài ra có 3 điểm của CORE-008 (đã merge) cần em xác nhận nhận làm follow-up: role guard/403, `/dashboard` chuyển về đúng trang chủ từng role, và trang 404 public. Cuối file có thêm một ghi chú về design system (thiếu biến thể segmented tab).

---

## PHẦN CHUNG — dán kèm trước mọi prompt CORE bên dưới

```text
PROJECT: SWP391 Racehorse Training & Management System — FE
REPO: lamphilong2004/SWP391_Racehorse-Training-Management-System_FE
OWNER: Trần Khải Hoàng      STRATEGY: UI-first / API-later (Leader decision 2026-10-17)

BASE BRANCH
- Start from origin/feat/dev-ui-harness (Long). It contains the canonical app, the sample adapter
  (src/shared/data/*), and the development harness. origin/dev does NOT yet contain the sample infrastructure.
- Create your branch from it (name below). This is a stacked branch: do not open a PR into dev and do not merge
  anything. Push your branch and report. The leader decides the PR target once the dependencies land on dev.
- Never force-push and never rebase other people's commits.

RUN WITHOUT A BACKEND
- npm ci, then npm run dev:harness → http://localhost:5190. Choose a role in the yellow "DEV HARNESS — SAMPLE DATA" bar
  at the bottom. The identity is simulated, no JWT is issued, every /api call stays in the sample adapter.
- "Reset sample data" restores the starting scenario. Read docs/UI-COVERAGE-MATRIX.md for the current coverage
  and the 3-flow click script.

ARCHITECTURE (mandatory)
UI components → feature service/hooks (src/features/<feature>/api.ts) → requestApi (src/shared/auth/authApi.ts)
→ sample adapter (src/shared/data/*Samples.ts) OR the real backend.
- Components never hard-code business data. The real API and the sample adapter use the same contract types.
- Add sample handlers for your endpoints in a new src/shared/data/<feature>Samples.ts, dispatched from
  sampleTransport.ts, and register the route prefix in businessData.ts usesSampleData(). Follow the contract
  exactly: paths, fields, enums, status codes, role checks. API errors are shown as errors; never fall back to
  samples automatically. Sample mutations must persist in the shared sample store so other roles and screens see
  them. Do not compute server-side aggregates or invent formulas.

FIGMA FIDELITY
- Live Figma file tm7dpgAQbHReZ7tlQeQJlc only. For each frame, read the exact node (design context +
  screenshot) and build it with the canonical design system (src/design-system, src/layout,
  src/layout/navigation.ts, src/app/routePaths.ts). Do not redesign or duplicate components.
- Every button, form, validation, modal, table, status and navigation must work, with loading / error / empty /
  success states. A picture that cannot be operated is not done.

RULES
- 7 roles only, web only. No new business rules, endpoints, fields or schema. Unknown → mark NEEDS CLARIFICATION.
- Do not edit files owned by other tasks unless the prompt allows it (CORE-013/027/029/033 Long; CORE-017/036/046 Đức).
- No secrets, no tracker edits, no optional flows (Flow 4/5, Owner portal).

VERIFY BEFORE PUSH
node node_modules/eslint/bin/eslint.js . ; node node_modules/typescript/bin/tsc -b --noEmit ;
node node_modules/vitest/vitest.mjs run --maxWorkers=1 ; node node_modules/typescript/bin/tsc -b && node node_modules/vite/bin/vite.js build
(The repo path contains "&", so call the tools through node as above.) Add tests for your screens. Do not raise
timeouts, skip tests or weaken assertions. Then operate the screens in npm run dev:harness with the right roles.

REPORT (comment or message to Long)
branch + HEAD SHA; screens and Figma nodes done; sample endpoints added; tests/lint/typecheck/build output;
browser walkthrough steps; open NEEDS CLARIFICATION items.
```

---

## CORE-025 — Trainer Dashboard + Create Session + Session Detail (làm trước)

```text
TASK: CORE-025 · BRANCH: feat/CORE-025-session-fe · DEPENDENCIES: CORE-002, CORE-005 (both merged)
FIGMA: Trainer Dashboard 94:286; Create Session and Session Detail states (Scheduled / Blocked / In Progress /
Paused / Completed / Interrupted), Page 04 mapped frames. Routes: /dashboard (TRAINER content),
/training/sessions/new, /training/sessions/:id (routePaths.ts already has the Training paths; add only what is missing).
CONTRACT: BE docs/api/flow2-training.openapi.yaml (dev 8b2e22a):
- GET/POST /api/training-sessions
- GET/PATCH /api/training-sessions/{id}, /api/training-sessions/{id}/exercises
- POST /api/training-sessions/suitability-check (categories A–D; LIMITED prohibited exercise = Medical Hard Block,
  Remove or Replace only, never override; LOCKED blocks the whole session)
- POST /api/training-sessions/work-rider-recommendations (labels + reasons, no scores)
- GET /api/training-programs/{id}, GET /api/exercises
- Permission matrix: docs/api/permission-matrix.md (Trainer = sessions of ASSIGNED programs only).
RULES: P-03 no early start; P-05 intensity LOW/MEDIUM/MEDIUM_HIGH/HIGH, within the phase range; the Trainer adapts
session-level items only (DEC-005). The existing Groom check, Work Rider and evaluation screens are other tasks: link
to them, do not rewrite them. Session states come from the server; never derive BLOCKED in the UI beyond what the
contract returns.
SAMPLE: extend the shared sample store so a session you create appears in Groom Today (CORE-027) and the Work Rider
assignments (CORE-029) with the same session id.
DONE WHEN: a Trainer can open the dashboard, create a session on the sample program 1001 / horse 7, pass the
suitability check, pick a Work Rider, schedule it, and see it in detail; it then appears for the Groom and the Work Rider.
```

## CORE-015 — Vet baseline examination + weight / health

```text
TASK: CORE-015 · BRANCH: feat/CORE-015-vet-baseline-fe · DEPENDENCIES: CORE-002, CORE-006 (merged)
FIGMA: Vet Baseline Examination + Weight/Health states (Page 04 mapped frames). Entry from the Vet's horse context
and from DRAFT horses awaiting baseline (Pending Activation shows "Initial health baseline — Pending — Veterinarian").
CONTRACT (flow3-medical.openapi.yaml, CORE-006):
- POST /api/horses/{horseId}/baseline-examinations: BaselineExaminationRequest { weightKg, healthStatus, clinicalNotes? }
  → BaselineExamination; VETERINARIAN only; duplicate → 409.
- GET/POST /api/horses/{horseId}/weights (WeightRecordRequest)
SAMPLE: feat/dev-ui-harness already answers the baseline POST in src/demo/demoSamples.ts (harness-only). Move or
replicate it into a proper src/shared/data/ handler for your feature. Keep the contract shape. After recording,
the sample store re-evaluates the DEC-009 §1 automatic activation (identity, pedigree + owner, free stable box,
Vet baseline). No approve button anywhere.
DONE WHEN: Club Manager registers a horse (CORE-013, existing) → Vet records the baseline in your screen → the horse
becomes ACTIVE automatically and Pending Activation no longer lists it.
```

## CORE-041 — Head Trainer: Template Library, Program Detail / Progress / History, Program Adjustment

```text
TASK: CORE-041 · BRANCH: feat/CORE-041-ht-support-fe · DEPENDENCIES: CORE-002, CORE-005 (merged)
FIGMA: Page 03 C1 supporting frames: HT Template Library, Program Detail Overview / Progress / History, Program
Adjustment, Trainer Comparison. Also the HT landing "Horse Training Programs" 77:317 (/training/programs).
Coordinate with your own CORE-022 wizard (already in the integration branch).
CONTRACT (flow2): /api/program-templates (list, detail, versions, duplicate, archive); /api/training-programs
(list, detail, progress, history, status, trainer-assignment, phases); /api/training-programs/{id}/adjustment-requests,
/api/adjustment-requests/{id}/approve|reject.
RULES (DEC-005): a Template is never modified by customizing a program; editing a Template saves a new version and
never changes existing programs; a used template is never hard-deleted (ARCHIVED). Recommendations show labels +
reasons only.
DONE WHEN: HT lands on /training/programs, opens the program created by the wizard, sees progress/history, browses
templates, and reviews an adjustment request.
```

## CORE-020 — Restrictions (Vet editor; HT / Trainer read-only) + HT Medical Summary

```text
TASK: CORE-020 · BRANCH: feat/CORE-020-restriction-fe · DEPENDENCIES: CORE-002, CORE-006 (merged)
FIGMA: Restriction Status / editor 275:9512 (/medical/restrictions); Head Trainer Medical Summary 274:9405;
read-only availability reason for HT and Trainer.
CONTRACT (flow3): GET /api/medical/restrictions; POST /api/horses/{horseId}/medical-restrictions;
POST /api/medical/restrictions/{restrictionId}/lift; /api/medical/restrictions/{restrictionId}/exercise-rules;
GET /api/horses/{horseId}/training-availability.
RULES: only the Veterinarian writes; one active restriction per horse (409); availability is derived (AVAILABLE /
LIMITED / LOCKED), never stored; HT sees medical summaries only, not clinical records (DEC-002 Q-04).
SAMPLE: when a horse is LOCKED or LIMITED in the sample store, the program wizard, session creation and Work Rider
start must reflect it (shared store), without inventing rules beyond the contract.
DONE WHEN: Vet sets LOCKED on horse 7 → the Trainer/HT see the reason read-only and the start/scheduling gates
show it → Vet lifts it → AVAILABLE again.
```

## CORE-044 — Trainer: Assigned Programs, Calendar, Exercise Library / Detail / Builder

```text
TASK: CORE-044 · BRANCH: feat/CORE-044-trainer-support-fe · DEPENDENCIES: CORE-002, CORE-005 (merged)
FIGMA: Page 03 C2: My Assigned Programs, Calendar, Exercise Library / Detail / Builder, Rider Comparison,
Request Adjustment. Routes in routePaths.ts: /training/programs (assigned for TRAINER), /training/calendar,
/training/exercises.
CONTRACT (flow2): GET/POST /api/exercises, /api/exercises/{id}; GET /api/training-sessions (Trainer = assigned
programs only, P-04); /api/training-programs/{id}/adjustment-requests (Trainer creates, HT decides).
DONE WHEN: the Trainer sees only assigned programs, the calendar shows the sample sessions, can browse and create an
exercise, and can send an adjustment request that the HT sees in CORE-041.
```

## CORE-031 — Trainer Live Monitoring (UI now, STOMP later)

```text
TASK: CORE-031 · BRANCH: feat/CORE-031-realtime-fe · DEPENDENCIES: CORE-002, CORE-005 (merged); CORE-030 BE (Tiến) NOT
merged → build the UI and the sample adapter now; real STOMP wiring later.
FIGMA: Trainer Live Monitoring + paused / manual-input supporting states (Page 03 C2). The Work Rider paused view
425:10068 already exists (CORE-029): your Pause/Resume must drive it through the shared session state.
CONTRACT: flow2-training.openapi.yaml: POST /api/training-sessions/{id}/pause|resume|stop|reduce-intensity
(reduce-intensity is PROPOSED — NEEDS CONFIRMATION), POST /api/monitoring-alerts/{id}/acknowledge;
flow2-stomp.md topics /topic/sessions/{id}/metrics|alerts|state.
RULES: P-01 only the Trainer resumes; P-02 no Work Rider pause/resume/stop; P-07 no universal warning = critical − 10;
P-08 no fixed 120 s escalation; P-09 one CRITICAL alert per crossing; P-12 HT read-only.
Do NOT present REST polling as WebSocket. Put the realtime source behind an interface (sample/polling now, STOMP
later) and label the connection state honestly.
DONE WHEN: Trainer opens live monitoring for the IN_PROGRESS sample session, pauses it (the Work Rider screen shows
PAUSED — BY YOUR TRAINER), resumes it, and stops it with a reason (→ INTERRUPTED).
```

---

## CORE-008 follow-up — cần em xác nhận nhận làm (đề xuất của leader, chưa tự chuyển task)

```text
CORE-008 was merged with three gaps (verified on dev f7c290d):
1. No role guard / 403 experience. RequireAuth only checks authentication. (The integration branch has an
   allowedRoles prop: reuse it.)
2. /dashboard shows one generic workspace instead of redirecting each role to its Screen Registry landing:
   CLUB_MANAGER /dashboard, HEAD_TRAINER /training/programs, TRAINER /dashboard, WORK_RIDER /rider,
   VETERINARIAN /medical, GROOM /groom/today, HORSE_OWNER placeholder (DEC-008).
3. path '*' is wrapped in RequireAuth, so an unknown public path redirects to login instead of the public 404.
Reply to Long whether you take this as a CORE-008 follow-up (branch fix/CORE-008-role-guard-landing from origin/dev).
```

## Design-system note (CORE-002, em là owner)

Figma vẽ "Filter tabs" dạng segmented pill (ví dụ `426:10370`, `427:10454`, trên màn Groom Treatment Tasks). Hiện design system chỉ có `Tabs` kiểu gạch chân, nên CORE-029 và CORE-046 đang dùng tạm loại đó. Nếu em đồng ý, thêm biến thể `variant="segmented"` vào `src/design-system/components/Tabs`. Việc này chỉ thêm biến thể mới, không đổi API hiện có.
