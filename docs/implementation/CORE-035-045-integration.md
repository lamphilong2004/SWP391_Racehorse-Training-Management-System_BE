# CORE-035 + CORE-045 local integration candidate

Branch: `integration/CORE-035-CORE-045`, based on BE `dev` at `5867f07`. This is a combined verification branch, not a replacement for either per-task PR.

CORE-035 owns the medical case and treatment-plan APIs and exposes a `TreatmentTaskMaterializer` port. CORE-045 implements that port after its approved `care_task.assigned_groom_user_id` migration is present. The integration keeps the Groom list and completion routes solely in CORE-045, so Spring starts with one mapping for each endpoint.

Materialization is idempotent. Repeating the same requested task set reuses existing task rows, new tasks append, and every existing task must remain represented with the same assignment and content. A request that removes or changes an existing task returns 409; no care-task row or completed history is deleted or rewritten. Plan updates and task writes share the service transaction.

Verification on Java 21: `mvn -B test` passed with 98 tests, 0 failures, 0 errors and 14 SQL Server integration tests skipped. The end-to-end H2 test covers Vet plan creation, assigned Groom list/completion, repeated PUT, appending a task and rejection of task removal with the plan update rolled back. H2 does not verify SQL Server identity, timestamp, locking or migration behavior.

The migration was not applied to any database. Before release, run SQL Server integration only against the approved `racehorse_integration_test` database. For separate PRs, merge/review CORE-035 first, then apply this adapter to the CORE-045 feature branch and rerun the combined suite. CORE-032 and CORE-040 remain unscoped here: their task descriptions and contracts are not present in the checked out repository or accessible task tracker.
