# CORE-035 — Medical case, examination, injury, treatment and recovery

Status: **independently reviewable, BLOCKED for integration/merge**. Branch `feat/CORE-035-medical-case-treatment-recovery` remains uncommitted. This worktree was already dirty when work began: `README.md` and `pom.xml` change the Java target from 21 to 25. Those user edits were preserved. The module also compiles with `-Djava.version=21` without changing the POM.

## Source basis

- `database/schema.sql` DB-01, SHA256 `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`; unchanged.
- Read-only `origin/feat/CORE-006-flow3-medical-contract`: `flow3-medical.openapi.yaml`, review register, shared envelope and permission matrix. This is a review revision, not proof of final approval.
- Read-only `origin/feat/CORE-026-incident-presession-gate`: Incident entity shape, service, adapter conventions and implementation notes. No CORE-026 code was copied or changed.
- Read-only `origin/feat/CORE-014-vet-baseline-weight-health`: existing `VeterinaryExamination` JPA mapping. CORE-035 uses a narrow JDBC examination store so the future merged application has one JPA mapping of `veterinary_examination`. The store uses only canonical columns and remains part of the same Spring transaction.
- Local foundation response/error classes and application configuration. The Task Playbook, detailed DoD, approved decision records, Figma registry and ERDs were not in this checkout. CORE-006 review G1–G8 records the decision gaps.

## Implemented API

All success/error responses use the foundation `ApiResponse` envelope. IDs and actor/timestamps are server-generated. UTC timestamps are truncated to milliseconds for SQL `DATETIME2(3)`.

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/medical/cases` | Vet opens OPEN case for an existing horse; optional incident linking requires a trusted CORE-026 adapter and otherwise returns 409 before insert. |
| GET | `/api/medical/cases` | Paginated list; Vet gets clinical details, Club Manager/Head Trainer summary, Trainer summary limited by open assignment in SQL. |
| GET | `/api/medical/cases/{caseId}` | Same role and row scope; clinical case includes persisted incident IDs, exams, injuries, treatment plan and re-exams. |
| POST | `/api/medical/cases/{caseId}/close` | Vet closes OPEN case with a server timestamp; repeat closure and closed-case edits return 409. No restriction lift. |
| POST | `/api/medical/cases/{caseId}/examinations` | Vet records EXAMINATION and optional diagnosis, severity, body condition and horse health status. |
| POST | `/api/medical/cases/{caseId}/re-examinations` | Vet records RE_EXAMINATION with schema-approved recovery outcome. No automatic restriction change or derived availability change. |
| POST | `/api/medical/cases/{caseId}/injuries` | Vet records region, severity, recovery status, optional same-case exam reference, side and opaque ASCII position marker. |
| PUT / GET | `/api/medical/cases/{caseId}/treatment-plan` | Vet creates or updates the single schema-backed plan, including rest/care instructions and re-examination date. |
| GET | `/api/medical/cases/{caseId}/treatment-tasks` | Vet reads treatment-sourced care tasks. |
| GET | `/api/groom/treatment-tasks` | Groom reads only task IDs supplied by trusted assignment adapter; clinical fields are absent. |
| POST | `/api/groom/treatment-tasks/{taskId}/complete` | Assigned Groom completes a treatment task once; server records the actor and time together. Body must be `{}`. |

Case detail provides examination, injury and re-examination history. No additional endpoint names were invented. Pagination uses 0-based pages, size 1–100 and `totalElements`. Case-list trainer scope is applied in SQL before paging. Groom work-list adapter must return assigned IDs; no adapter means 403. In service tests only, injected identities and assignment IDs exercise these paths.

## Security and integration contracts

`MedicalPorts.ActorResolver` is the CORE-007 boundary. It must validate a real token, an ACTIVE account and current server-side role grants. No production resolver exists in this branch, so medical requests fail 401. Client role/user headers and request actor IDs are ignored or rejected. Vet is the sole clinical writer; Club Manager/Head Trainer/assigned Trainer see summary only. Groom gets a dedicated safe task projection and cannot read case diagnosis or medication instructions.

`MedicalPorts.IncidentLink` is the CORE-026 boundary. The future adapter must lock the Incident, check the same horse, enforce its review/link policy and update `incident.medical_case_id` in the case transaction. The case table has no `incident_id`. Until the adapter exists, a create request with `incidentId` fails 409 without persisting a case. No Incident API is implemented here.

`MedicalPorts.GroomTaskScope` must use an approved assignment source. DB-01 has no assigned Groom FK or work-list membership table on `care_task`. Without that adapter, Groom list/completion fail 403. This branch has no production adapter. Treatment plan PUT with nonempty `groomTasks` returns 409 because repeated PUT/materialization and assignment policy are unresolved; it does not create fake task rows. The canonical completion pair supports a single completion acknowledgement, but no separate acknowledgement event or note can be persisted.

CORE-019 owns MedicalRestriction and training availability. Recovery outcome is stored on the re-examination. It never lifts a restriction, sets a derived AVAILABLE state or writes CORE-019 tables. A future workflow must call CORE-019 explicitly after its business rules are approved.

## Tests and verification

- H2 service tests cover case persistence, missing horse, incident dependency, closure conflict, summary/Trainer scope, examination and re-examination persistence, injury validation, treatment upsert, Groom scope/safe projection/duplicate completion and unauthorized roles.
- MVC tests cover 401 without the CORE-007 adapter, 400 for actor fields, invalid enum and nonempty completion body.
- Final `mvn -B '-Djava.version=21' package`: **BUILD SUCCESS**, Java 21 target compilation, executable JAR and **25 tests passed, 0 failed, 0 errors, 0 skipped** (16 CORE-035 + 9 foundation). The pre-existing local POM edit still defaults to Java 25 if the override is omitted.
- H2 uses a focused test fixture, not the full SQL Server schema. No SQL Server JDBC integration test ran: no approved `DB_URL`/credentials were configured in this worktree. Do not label H2 as SQL Server verification.
- `git diff --check` passed at handoff. Canonical schema checksum stayed unchanged. No other branch or worktree was modified.

## Outstanding decisions and next steps

1. Merge/approve CORE-006 and confirm case close/incident review transition rules (G3), anatomy encoding and 2D/3D semantics (G4), and original DoD/ERD/Figma records (G8). DB-01 has no injury description column; diagnosis/findings belong to examination, and no injury-only description is written.
2. Merge CORE-007 and connect `ActorResolver`; rerun authorization tests with verified JWT and ACTIVE account data.
3. Merge CORE-026 and implement `IncidentLink` with same-horse validation, locking and its review policy in one transaction. Reconcile CORE-026 review action with CORE-006 G3/G6.
4. Merge CORE-014 and verify the examination JDBC store against its existing JPA mapping and SQL Server `DATETIME2(3)` behavior. No second examination JPA entity should be added.
5. Decide trusted Groom assignment, task materialization/update policy and completion-note/acknowledgement storage (G1/G2). Until then production Groom operations remain fail closed.
6. Merge CORE-019 and define the explicit restriction review/lift workflow after recovery. Do not infer availability from recovery status or `horse.health_status`.
7. Run service-level SQL Server tests with an approved isolated database and credentials, Hibernate `ddl-auto=validate`, transaction/concurrency checks, then rerun Maven and the merged contract suite. Integration and merge remain **BLOCKED** until these dependencies and decisions are resolved.
