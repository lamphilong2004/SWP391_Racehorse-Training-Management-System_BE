# CORE-035 — Medical case, examination, injury, treatment and recovery

Status: implementation candidate on `feat/CORE-035-medical-case-treatment-recovery-v2`, based on BE `dev` at `5867f07`. It is not ready to merge until the build and tests run and the CORE-045 treatment-task adapter is integrated.

## Implemented scope

- Adds veterinarian-only independent case creation, role-filtered case reads, examinations, re-examinations, injuries and the single treatment-plan API.
- Resolves the actor from CORE-007's authenticated `CurrentActorProvider`; the account-status filter has checked the account is ACTIVE and loaded current role grants. Client identity and role headers are never trusted.
- Keeps case summaries free of clinical fields. Trainer case list count and page selection apply current horse assignment in SQL before pagination.
- Closes a case only after all approved gates pass: a clinical examination exists; the latest re-examination is RECOVERED when an injury or plan exists; all treatment tasks are complete; directly linked restrictions are lifted; and linked incidents are RESOLVED. Closure does not change those resources.
- Uses the existing veterinary-examination table through a narrow JDBC store. SQL Server inserts return identity via `OUTPUT INSERTED.id`; timestamps are UTC and truncated to DATETIME2(3) precision.
- Case create accepts only `horseId` and `reason`. Incident linkage remains exclusively in CORE-026's Incident Review flow.
- Retains only the Vet case treatment-task read endpoint from CORE-035. Groom list and completion routes are owned by CORE-045 and are not duplicated here.

## CORE-045 boundary

The plan request carries an `assignedGroomUserId` per requested task. A `TreatmentTaskMaterializer` port lets CORE-045 validate the active GROOM assignment and reconcile care-task rows in the same transaction. Until that adapter and its approved migration are integrated, nonempty `groomTasks` requests return 409 before saving a plan; no fake tasks are created. The adapter must be idempotent, preserve completed history and reject ambiguous task replacement instead of deleting rows.

CORE-026 still owns Incident Review transitions and linking. CORE-019 still owns restriction creation/lift and derived training availability. Recovery never changes restrictions or availability by itself.

## Verification and remaining gates

- No database schema file is changed and no migration is executed.
- H2 tests cover persistence, actor/role scope, examination history, injury validation, closure gates, plan behavior and fail-closed task materialization. The full backend suite passed on Java 21 with 93 tests, 0 failures, 0 errors and 14 SQL Server integration tests skipped.
- The CORE-035 H2 fixture uses its own in-memory database so its schema setup cannot affect other test classes. No SQL Server integration ran; it remains pending and must use only the approved `racehorse_integration_test` database with separately supplied credentials.
- Before merge, run the Maven suite, test the JDBC identity/timestamp path on SQL Server, integrate the CORE-045 materializer, and verify closure races against CORE-026 review and CORE-019 restriction updates.
