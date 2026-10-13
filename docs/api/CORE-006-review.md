# CORE-006 contract review revision

This revision updates the existing Flow 3 contract for review. It does not declare the contract approved, implement the backend, change DB-01, or unblock runtime work that depends on missing decisions. API validation and the explicit contract checks are separate from those integration gates.

## Sources and provenance

- `docs/api/shared.openapi.yaml` and `docs/api/permission-matrix.md`, present on merged `origin/dev`: binding shared error codes/envelope and all role grants, including summary and assignment scopes. The revision copies the shared error schemas unchanged.
- `origin/feat/CORE-001-be-foundation:README.md` explicitly identifies `database/schema.sql` as a byte-identical copy of canonical `project-docs/database/schema.sql`, DB-01, 51 tables. Its recorded SHA256 and the local schema both equal `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`. Foundation commit `9531ca2` explicitly preserves the approved schema bytes. SQL is used on that documented provenance, not merely because an unmerged branch exists. Independent access to the original project-docs copy, physical-schema.md, domain ERDs and approval decisions was unavailable; schema-copy provenance is not a claim that CORE-001 was merged.
- Read-only `project-context/RACEHORSE_AI_PROMPT_DUC.md` and `RACEHORSE_AI_TEAM_EXECUTION_GUIDE_48H.md`: restriction-derived availability, Vet-only medical work, treatment-only Groom scope, source hierarchy and no schema edits.
- Read-only `project-context/Racehorse_Core_Flow_Execution.xlsx`: only **Core Flow Execution** is present. Task Playbook, Figma Screen Registry, Role Coverage Matrix and detailed DoD are absent. CORE-006 has no listed dependency. Implementation ownership: CORE-014 baseline/weight/health = Đức; CORE-019 restrictions/interlock = Long; CORE-026 Incident = Đức; CORE-035 case/exam/injury/treatment/re-exam = Đức; CORE-036 FE = Đức. CORE-030 monitoring = Tiến; CORE-011 reference defaults = Dũng. No explicit threshold override service owner or separate herd-health implementation task is assigned; herd-health remains part of the medical contract, with owner confirmation required.
- Read-only CORE-014 implementation notes and CORE-036 handoff: consumer impact evidence, not new approved business rules. Existing medical URLs and operation IDs remain stable. The baseline and weight DTOs match the current CORE-014 implementation. FE remains untouched.
- PR #3: open, head `feat/CORE-006-flow3-medical-contract`, base `dev`; connector returned no review comments. The supplied user request is the correction checklist. No independent approved URL registry was available, so new exercise-rule and override-end URLs are submitted resource-based contract choices for review, not claimed previously approved endpoints.

## Corrected contract and persistence mappings

| Capability | Persistence and invariants |
|---|---|
| IDs | Positive JSON integer/int64, SQL BIGINT for every entity and FK identifier |
| Baseline | `veterinary_examination` with BASELINE and NULL case; `weight_record` context BASELINE; `horse.health_status`; atomic write; filtered baseline uniqueness returns 409 |
| Weight | `weightKg` → `weight_record.weight` DECIMAL(6,2); `recordedAt` → `measured_at`; JWT actor → `recorded_by_user_id`; deterministic descending history |
| Health update | Optional `healthStatus` on Veterinarian examinations and re-examinations atomically sets `horse.health_status` plus exam `health_status_set`; FIT/MONITORING/INJURED/QUARANTINED; no new health table or competing availability field |
| Incident | `incident`: type OFF_FEED/COLIC_SIGNS/FEVER/HOOF_OR_LEG_PROBLEM/OTHER; observed severity LOW/MEDIUM/HIGH; status REPORTED/UNDER_REVIEW/RESOLVED; optional source FKs, at most one; unique non-NULL pre-check FK; server code/actor/report timestamp |
| Case | `medical_case`: OPEN/CLOSED; `reason` → `condition_summary`; `incidentId` links `incident.medical_case_id`, not a nonexistent case incident column; close sets timestamp and never implicitly lifts |
| Exam | `veterinary_examination`: EXAMINATION/RE_EXAMINATION require same-horse case; symptoms/findings map `findings`; nullable diagnosis; clinical severity MILD/MODERATE/SEVERE; re-exam outcome NOT_RECOVERED/IMPROVING/RECOVERED |
| Injury | `injury`: required region/severity/recovery; optional same-case exam; region NVARCHAR(100), side NVARCHAR(20), opaque position_3d VARCHAR(100); no invented coordinates table or clinical_notes column |
| Treatment | Single `treatment_plan` per case; medication instructions NVARCHAR(MAX), rest_period NVARCHAR(100), care_instructions NVARCHAR(2000), re_examination_date DATE; no fictional plan status |
| Groom work | `care_task` with treatment_plan_id only (XOR source rule); task_date, scheduled_slot and explicitly safe instruction_snapshot; PENDING/COMPLETED derived from paired completed_at/completed_by_user_id; no clinical plan response |
| Restriction | `medical_restriction`: LIMITED/LOCKED; active slot = lifted_at IS NULL; unique active-per-horse index; competing creation returns 409, including races; review_date is not an expiry; no AVAILABLE row |
| Exercise rules | PUT `/api/medical/restrictions/{restrictionId}/exercise-rules`; replace set atomically; ALLOWED/PROHIBITED; FK and/or note required per SQL; lifted restriction returns 409 |
| Availability | GET `/api/horses/{horseId}/training-availability`; no unlifted row → AVAILABLE, otherwise LIMITED/LOCKED; no extra horse availability field |
| Thresholds | Global configured HEART_RATE defaults with optional effective horse overrides; metric/critical_value/reason/effective interval map `horse_threshold_override`; DECIMAL(7,2), positive; no demo defaults; explicit end operation uses existing effective_to |
| Herd health | Retained Veterinarian-only GET `/api/medical/herd-health`; counts from health_status with NULL counted separately as unassessed |
| Errors | Exact shared success/error conventions; code/message/details, no fieldErrors; 400/401/403/404/409 on all operations; business conflicts use 409, no unsupported 422 |

Required restriction capabilities remain: training availability GET, horse restriction POST, explicit lift POST, exercise rules PUT, club restrictions GET. Restriction writers and clinical writers are VETERINARIAN only. The only other writes are authorized explicit Incident reporting and scoped Groom completion. Owner summaries remain in CORE-004 `/api/owner/horses/**`, never clinical endpoints.

Case list/detail use role-selected clinical versus summary schemas. Training-facing restriction/availability reasons must contain operational explanations only. Groom work-list and completion responses use their own strict projection: task/horse identifiers, operational instruction/date/slot, derived status and completion timestamp. They do not contain case diagnoses, prescriptions, the full TreatmentPlan or clinical histories. Backend scoping happens before loading/mutating rows, including list pagination/counts. JWT claims supply actors; request bodies never accept actor IDs.

## Remaining decisions and schema gaps

| Gap | Exact missing evidence or schema support | Required owner action / effect |
|---|---|---|
| G1 | `care_task` has no assigned/responsible Groom FK or work-list membership table. No approved alternative assignment source was supplied. Treatment PUT repeat/materialization policy is also unspecified. | Medical/work-list owner and DB decision owner must define trusted assignment scope and how editing a plan schedules tasks without duplicating or destroying completed work. Until then Groom access fails closed. No arbitrary Groom selection, all-task exposure or new field/table is authorized. |
| G2 | No care_task completion-note column, no restriction lift-reason or lifted-by business column, no incident review comment column. | Preserve these requirements in this register; obtain a storage decision before adding request fields that cannot persist. Current completion/lift contracts acknowledge the action using existing timestamps/actor pair. Technical audit may trace actions but cannot substitute for missing clinical business records. |
| G3 | Approved Incident action transition table, re-review/idempotency policy, additional case closure/closed-case editing criteria were not supplied. SQL defines valid states, not the full business transition rules. | Leader/medical decision owner supplies transitions. Current schema enums and action field dependencies are explicit; implementation must not infer a new clinical state machine. |
| G4 | SQL position_3d is VARCHAR(100), not a coordinate object; no approved serialization, coordinate frame, model reference or body-region taxonomy supplied. | Medical/3D owner supplies encoding and model semantics. Opaque marker preserves existing SQL capability; interactive coordinate handling remains an integration gate, with region text providing 2D fallback. |
| G5 | SQL permits future effective_from on an unlifted restriction and permits both rule target fields. Approved matching/allow-vs-prohibit precedence is unavailable. | CORE-019 owner confirms effective-time and exercise matching policy. Unique unlifted slot and conservative derived level remain enforced; no automatic expiry or competing AVAILABLE record. |
| G6 | Workbook CORE-026 says abnormal pre-check raises Incident/notification. Unmerged CORE-005 cites BR-23/DEC-007 and also creates an Incident. The original approved business-rule text was not available. User requires explicit creation unless that rule is confirmed. | Leader/CORE-026/CORE-005 owner supplies approved BR-23/DEC-007 and reconciles the contracts. This revision defines explicit Incident POST and does not independently enable automatic creation from abnormal checks. Do not edit CORE-005 or the FE as part of CORE-006. |
| G7 | DB-01 explicitly omits global threshold technical storage. No durable global administration model, complete interval-overlap policy, or override-service owner assignment was supplied. | Global reads can use managed configuration; CORE-030 consumes effective settings and CORE-011 supplies reference defaults. Leader assigns override service owner and durable administration requirements if needed. No new table or universal demonstration threshold. |
| G8 | Original approved path registry, Playbook/DoD, domain ERDs, physical-schema.md, and current decision records unavailable. `incident_photo` exists, but upload/reference API and attachment lifecycle are not supplied. | Obtain source documents and photo capability contract before full implementation sign-off. Existing paths retained; meaningful photo requirement stays recorded rather than pretending photoIds map to an approved upload API. No new out-of-scope upload module. |

These are implementation/approval gates, not unresolved placeholder DTO types. Removing unsupported fields from executable schemas prevents false persistence promises; their requirements remain explicit here. No preventive-care, nutrition or unrelated module is introduced.

## Verification

Run `npx @redocly/cli lint docs/api/flow3-medical.openapi.yaml` (Windows uses `npx.cmd` to avoid disabled PowerShell script execution). Then bundle both actual contracts and run the verifier:

```text
npx @redocly/cli bundle docs/api/flow3-medical.openapi.yaml -o <temporary-directory>/medical.json
npx @redocly/cli bundle docs/api/shared.openapi.yaml -o <temporary-directory>/shared.json
node docs/api/verify-core006.cjs <temporary-directory>/medical.json <temporary-directory>/shared.json
```

Redocly CLI 2.63.0: **0 errors, 0 warnings**. Contract verifier: **483 checks passed, 29 operations**. The verifier checks the actual Redocly bundles, endpoint/RBAC coverage, shared errors, context XOR, active restriction semantics, safe Groom and summary projections, enum/type coverage, and absence of unresolved placeholder language. Git scope checks confirm only the medical contract, this review register and its verifier are changed; no schema, FE or CORE-014 application files are changed. No runtime JWT, SQL concurrency or full business-decision verification is claimed. G1–G8 remain implementation/approval gates; passing contract validation does not close those gaps.
