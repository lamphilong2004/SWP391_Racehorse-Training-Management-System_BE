# Prompt cho Cao Thanh Dũng — sửa CORE-004 theo leader gate (2026-10-17)

**Tin nhắn Zalo (gửi kèm file này):**

> CORE-004 (PR #9, head ac4e3a0) chưa qua leader gate: có 4 lỗi P1. Contract vẫn ghi NEEDS CLARIFICATION cho điều kiện kích hoạt tự động, mốc timeline và checklist, trong khi DEC-009 §1 và BR-05 đã chốt hết. Lint và role đều ổn. Review chi tiết ở đây: https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/9#pullrequestreview-5555422802 . Em dán prompt dưới vào AI, sửa bằng commit của em, push lên cùng nhánh rồi comment head mới trên PR. Không force-push, không merge.

```text
PROJECT FIX — CORE-004 (Flow 1 Horse API contract)

REPO:   lamphilong2004/SWP391_Racehorse-Training-Management-System_BE
PR:     #9  https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/9
BRANCH: feat/CORE-004-contract-flow1-owner        BASE: dev
GATED HEAD: ac4e3a09979203062e290bd6401990a000771e63  → leader gate = FAIL (CHANGES REQUESTED)
OWNER: Cao Thanh Dũng
FILE:  docs/api/flow1-horse.openapi.yaml (the only file you should need to change)

SOURCES YOU MUST FOLLOW (read them first)
1. DEC-009 §1 (APPROVED 2026-10-10), project-docs/decisions/DEC-009-core-execution-scope-resolutions.md:
   - Activation is AUTOMATIC (BR-05; Figma SCR-HORSE-005 285:2786).
   - DRAFT → ACTIVE happens when the System validates: identity (1.1), pedigree + owner + free stable box (1.2) and the
     Veterinarian baseline (1.3). Then horse.lifecycle = ACTIVE and horse.activated_at is set = TIMELINE START.
   - Head Trainer readiness review (1.4) is NON-BLOCKING. It is not an approval and is traced only as audit event
     HEAD_TRAINER_READINESS_REVIEWED. SCR-HORSE-005 stays a Club Manager status screen and shows the readiness review as
     "Recommended — does not block activation".
2. BR-05 (CONFIRMED), project-docs/analysis/business-rules.md line 20: DRAFT → ACTIVE only when the data is valid, and the
   horse timeline starts at that point.
3. Do not contradict the contracts already on dev: docs/api/shared.openapi.yaml + docs/api/permission-matrix.md (CORE-003),
   docs/api/flow3-medical.openapi.yaml (CORE-006: POST /api/horses/{horseId}/baseline-examinations is defined there),
   docs/api/flow2-training.openapi.yaml (CORE-005), and database/schema.sql (horse.lifecycle DRAFT/ACTIVE/INACTIVE,
   horse.activated_at DATETIME2(3) NULL).

VERIFIED FINDINGS (line numbers refer to ac4e3a0)
P1-1  info.description L18-21
      - L20-21 marks the exact activation conditions and whether activatedAt starts the timeline as NEEDS CLARIFICATION.
        Both are decided by DEC-009 §1 + BR-05.
      - L19-20 says "recording the baseline does not activate the horse". This is wrong: the Vet baseline is one of the
        conditions, and when it is the last one satisfied, the horse becomes ACTIVE.
      FIX: state the four approved conditions and activated_at = timeline start, citing DEC-009 §1 and BR-05. Keep
      "no role has an approve/activate endpoint" and "CORE-016 implements the transition".
P1-2  Timeline + activatedAt
      - getHorseTimeline description L338-341 keeps "whether the timeline starts at activatedAt … NEEDS CLARIFICATION".
      - HorseDetail.activatedAt L788 keeps "whether it defines the timeline start is NEEDS CLARIFICATION".
      FIX: both state that the timeline starts at activated_at (set by the automatic transition).
P1-3  Lifecycle effect of the write operations is inconsistent
      - setPedigree L181, addOwnership L232-233 and assignStableBox L420 each say "whether … triggers the automatic
        lifecycle transition is NEEDS CLARIFICATION".
      - The baseline line L19-20 says the opposite.
      FIX: one consistent rule. After a successful write that satisfies a condition (pedigree, ownership, free box
      assignment, or the Flow 3 baseline), the System re-evaluates the conditions. If a DRAFT horse now meets all of them,
      it becomes ACTIVE and activated_at is set (implemented by CORE-016). Document what the client observes: either the
      response carries the resulting lifecycle/activatedAt, or the client re-reads GET /api/horses/{id}/activation-status.
      Pick one and write it down.
P1-4  Activation checklist
      - getActivationStatus L488-489 says the mandatory items are NEEDS CLARIFICATION.
      - ActivationStatus.checklist L911-914 is `items: {}` (untyped).
      FIX: list the approved conditions (identity; pedigree + owner; free stable box; Vet baseline = mandatory; HT readiness
      review = recommended, non-blocking) and define a TYPED item schema (for example a key enum + satisfied/pending state
      + whether it blocks activation). You choose the key names and granularity, but they must cover exactly the DEC-009
      conditions and nothing invented. CORE-013 (Long) and CORE-017 (Đức) consume this.

KEEP AS VALID OPEN QUESTIONS (do NOT invent answers)
- Audit action names for create/update/pedigree/ownership/box (no approved vocabulary).
- TimelineEvent.eventType vocabulary (only the activation start is decided).
- Readiness review on INACTIVE horses; the `sex` value list; Q-18 photo storage; Q-7 distance; Q-28 multiple owners;
  ownership shares not enforced to 100 %.

DO NOT CHANGE
- x-roles (they already match permission-matrix.md), the Owner row-scope rules, other contracts, database/schema.sql.
- No new endpoints, no approve/activate endpoint, no new fields that do not map to existing columns. No Flow 3 baseline
  definition inside Flow 1 (reference only).
- Do not rebase, do not force-push, do not rewrite the history or the original author of 6976c77.

STEPS
1. git fetch origin; git checkout feat/CORE-004-contract-flow1-owner; git merge origin/dev (the branch is 6 commits behind;
   a test merge is clean).
2. Apply only P1-1…P1-4 in docs/api/flow1-horse.openapi.yaml.
3. Verify:
   - npx @redocly/cli lint docs/api/flow1-horse.openapi.yaml           → 0 errors, 0 warnings
   - npx @redocly/cli bundle docs/api/flow1-horse.openapi.yaml --dereferenced -o /tmp/flow1.json  → succeeds
   - grep -n "NEEDS CLARIFICATION" docs/api/flow1-horse.openapi.yaml → only the valid open questions listed above remain
4. Commit: fix(CORE-004): align activation, timeline and checklist with DEC-009 §1
5. git push origin feat/CORE-004-contract-flow1-owner (normal push).
6. Comment on PR #9 with: the new head SHA, a list of what changed per finding (P1-1…P1-4), the lint/bundle output, and
   the remaining NEEDS CLARIFICATION lines. Do not merge. Do not edit the tracker.

GATE PASS CONDITIONS (what the leader will re-check on the new head)
- P1-1…P1-4 fixed with DEC-009 §1 / BR-05 cited; no decided point left as NEEDS CLARIFICATION.
- Typed checklist item schema covering exactly the approved conditions.
- Lint 0/0, dereferenced bundle OK, roles unchanged and matching permission-matrix.md, DB mappings valid.
- Diff limited to CORE-004; merged with current origin/dev; no force-push.
```
