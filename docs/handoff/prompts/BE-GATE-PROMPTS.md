# BE gate follow-up prompts — 2026-10-17

## Cho Dũng — CORE-007 (PR #10, head `00de7ec`)

**Zalo:** PR #10 build và test H2 đều pass (39 test), nhưng chưa merge được. Còn thiếu bằng chứng SQL Server: 10 test SQL đang bị skip. PR description cũng đã cũ. Review: https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/10#pullrequestreview-5555948797

```text
CORE-007 READINESS — repo lamphilong2004/SWP391_Racehorse-Training-Management-System_BE, PR #10,
branch feat/CORE-007-auth-be, gated head 00de7ec (mvn verify: 39 run, 0 fail, 10 skipped = SQL suite).
1. Do NOT create/seed/migrate any database until Long approves an isolated test DB in writing (name + connection
   method). Never reuse racehorse_demo or a dev DB. No secrets in Git or chat.
2. After approval: apply database/schema.sql (byte-identical) + database/migrations/V20261016_01__create_registration_request.sql
   to that DB, run `mvn -B verify -Dcore007.sqlserver.it=true` with the env from docs/CORE-007-auth-smoke-runbook.md,
   and attach redacted Surefire output (counts, 0 skipped for the SQL suite), DB identity and timestamps.
3. Rewrite the PR description for the current head: test count, implemented pending-registration boundary
   (approved migration, docs/decisions/CORE-007-leader-decisions.md), what is verified on H2 vs SQL Server, remaining
   gaps. Remove "Draft for inspection only" wording when true.
4. Keep scope: no registration approval (CORE-047), no reset (CORE-049), no token blacklist. No force-push.
5. Mark ready for review and comment the new head SHA. Do not merge.
```

## Cho Tiến — CORE-019 (PR #11, head `d8ec5bf`)

**Zalo:** PR #11 test H2 pass (44 test). Còn 3 blocker. Một là xung đột `CurrentActorProvider` với CORE-007. Hai là tự thêm endpoint vào contract Flow 3 mà không có quyết định. Ba là chưa test trên SQL Server. Review: https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/11#pullrequestreview-5555949150

```text
CORE-019 FIX — repo lamphilong2004/SWP391_Racehorse-Training-Management-System_BE, PR #11,
branch feat/CORE-019-restriction-be, gated head d8ec5bf (mvn verify H2: 44 run, 0 fail, 0 skipped).
A. Auth integration (do after CORE-007 PR #10 is merged into dev):
   - git fetch; git merge origin/dev into your branch (no rebase, no force-push).
   - Resolve the add/add conflicts by DELETING your src/main/java/com/racehorse/auth/{ActorContext,RoleCode,
     CurrentActorProvider}.java and using CORE-007's versions (CurrentActorProvider is a Spring @Component class;
     same method `Optional<ActorContext> currentActor()`). Do not add a second provider bean or interface.
   - Keep @Mock CurrentActorProvider in tests (works with the class). Re-run mvn verify.
B. Contract: your diff adds GET /api/horses/{horseId}/availability (alias), POST /api/horses/availability/batch and
   changes rule-type wording in docs/api/flow3-medical.openapi.yaml (CORE-006, merged, contract owner Đức).
   Remove these contract edits from PR #11 OR move them to a separate contract PR that cites a leader-approved
   decision. Implement only endpoints in the approved contract. Same for verify-core006.cjs / CORE-006-review.md.
C. SQL Server: the UPDLOCK/HOLDLOCK and OUTPUT INSERTED.id paths never ran (H2 uses FOR UPDATE). After Long approves
   an isolated test DB, run the repository + concurrency tests against SQL Server and attach redacted evidence.
   Do not count results from integration/CORE-007-CORE-019-v2 as evidence for this PR.
D. Leave the scheduling/start interlock to CORE-024/CORE-028 (they must call RestrictionExercisePolicy); say so in
   the PR description. Update the description to the new head, then mark ready. Do not merge.
```

## Cho Tiến — Decision G1 migration (CHỈ gửi khi đủ điều kiện)

Điều kiện gửi:
- CORE-007 đã merge, vì nó lập quy ước `database/migrations/`.
- Long chỉ định owner: đề xuất **CORE-045 (Tiến)**, Dũng review với vai trò owner thiết kế DB.
- Có một DB test riêng đã được duyệt.

```text
G1 MIGRATION — Leader-approved schema addition (docs/api/CORE-006-review.md §G1, flow3-medical.openapi.yaml
schemaAdditionApproved): care_task.assigned_groom_user_id BIGINT NULL, FK → dbo.app_user(id).
- Add ONE new file database/migrations/V<yyyymmdd>_01__add_care_task_assigned_groom_user_id.sql:
  ALTER TABLE dbo.care_task ADD assigned_groom_user_id BIGINT NULL;
  ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__assigned_groom_user_id FOREIGN KEY (assigned_groom_user_id)
    REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
  CREATE INDEX ix_care_task__assigned_groom_user_id ON dbo.care_task (assigned_groom_user_id)
    WHERE assigned_groom_user_id IS NOT NULL;
  (idempotency guards per the existing V20261016_01 style). NULL allowed: existing rows stay valid.
- Do NOT edit database/schema.sql (canonical, byte-identical) or any other table. No data UPDATE/backfill.
- Application rule (contract): the Vet chooses an ACTIVE GROOM; GET /api/groom/treatment-tasks returns only rows where
  assigned_groom_user_id = authenticated user. Session-level Groom assignment for pre-checks is NOT covered by G1
  (still a model gap — do not add a training_session column without a new decision).
- Verify on the approved isolated DB only; attach redacted evidence. Separate PR from CORE-019.
```

## Thứ tự merge an toàn

1. **CORE-004 #9**: chỉ merge sau khi có re-gate PASS trên `1b84b59` hoặc head mới hơn. Hiện chưa re-gate.
2. **CORE-007 #10**: cần bằng chứng SQL, description đã cập nhật và PR chuyển sang Ready.
3. **CORE-019 #11**: merge `origin/dev` vào nhánh (đã có CORE-007), xóa 3 file auth trùng, giải quyết phần sửa contract, có bằng chứng SQL.
4. **G1 migration**: PR riêng (đề xuất thuộc CORE-045).
5. Rồi mới đến CORE-021, CORE-024, CORE-026, CORE-035 theo dependency trong Playbook:
   - CORE-021 cần 007 + 019
   - CORE-026 cần 007 + 024
   - CORE-035 cần 007 + 026
   Hiện chưa task nào Ready.
