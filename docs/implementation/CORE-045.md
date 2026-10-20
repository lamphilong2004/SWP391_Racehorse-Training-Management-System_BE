# CORE-045 — Assigned Groom treatment tasks

Status: implementation started on `feat/CORE-045-groom-treatment-be`, based on BE `dev` `5867f07`. This is a review candidate; no test suite or SQL Server database was run in this work session.

## Scope

- Adds the leader-approved nullable `care_task.assigned_groom_user_id BIGINT` foreign key to `app_user`, with a filtered index. The migration does not edit the canonical `database/schema.sql`, rewrite existing rows, seed data, or run against any database.
- Adds a repository boundary for CORE-035 to validate that the selected Groom account is ACTIVE and currently has the GROOM role, and to materialize a task only for an OPEN treatment plan belonging to the same horse.
- Adds the contract's paginated `GET /api/groom/treatment-tasks` and empty-body `POST /api/groom/treatment-tasks/{taskId}/complete` operations.
- Groom reads are filtered by authenticated JWT user ID and `treatment_plan_id` before count and pagination. The returned projection contains operational fields only. Completion atomically records UTC time and the JWT actor. Repeating completion returns 409.
- Extends the fresh local-database installer and expected schema snapshot to include the approved migration. Existing databases are still refused by the installer; this does not authorize an upgrade.

## Integration boundary

CORE-035 must call `GroomTreatmentTaskAssignmentRepository` when materializing treatment tasks and use its active-Groom validation before persisting the selected `assignedGroomUserId`. Treatment-plan replacement/reconciliation, including preservation of completed task history, remains CORE-035 work. This branch does not add a second treatment-plan or case API.

## Pending gates

- Unit/API tests and full Java build have not been run for this candidate.
- The migration has not been applied. SQL Server verification requires the approved isolated integration database and credentials.
- End-to-end Groom assignment depends on integration with CORE-035, which is currently an older unmerged PR and must be ported to current `dev`.
