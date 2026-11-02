# Flow 4 local candidate — Stable Care & Nutrition

Leader reopened MF-10/11/12/13 on 2026-10-28. Source rules are BR-33 through BR-36 and the care-plan authority clarification in project-docs/analysis/business-flows.md. Canonical schema bytes remain unchanged. The reviewed additive V20261029_01 migration adds task-change history only. Stock is updated manually, as explicitly approved.

## API contract

All paths have prefix `/api/stable-care`, use the existing success/data/error envelope and canonical JWT/ACTIVE-account boundary. See `StableCareApi.java` for exact JSON fields and validation limits; Swagger generates the twenty-one operations from the controller.

| Method/path | Roles | Semantics |
|---|---|---|
| GET /horses | Manager, Head Trainer, Vet | Horse identities for care-plan selection |
| GET /grooms | Manager, Head Trainer | ACTIVE Groom directory |
| GET /care-plans?horseId | Manager, Head Trainer, Vet | Plans, items, explicit date range and assigned Groom |
| POST /care-plans | Manager | Create daily items and persist tasks in one transaction |
| PUT /care-plans/{id} | Manager | Edit only plans without historical/completed tasks; keep the horse unchanged |
| GET /nutrition-plans?horseId | Manager, Head Trainer, Vet | Nutrition plans and rations |
| POST /nutrition-plans | Manager, Head Trainer | Explicit from/to period, assigned Groom and meal/component/quantity/unit |
| PUT /nutrition-plans/{id} | Manager, Head Trainer | Same historical protection as care plans |
| GET /tasks?date | Manager, Head Trainer, Vet, Groom | Exact club-calendar day; Groom sees own assignments only; excludes treatment tasks |
| GET /daily-summary?date | Same | Actual totals derived from the same authorized tasks |
| POST /tasks/{id}/complete | Assigned Groom | Reject future tasks; actual user/time; repeated completion returns the committed task |
| POST /tasks/{id}/reassign | Manager for CARE; Manager/Head Trainer for NUTRITION | Pending today/future task only; expectedGroomUserId, ACTIVE replacement groomUserId and reason required; retain task ID/history |
| POST /tasks/{id}/cancel | Same task-source authority | Pending today/future task only; expectedGroomUserId and reason required; retain row, record cancellation and notify current Groom; retry returns committed cancellation |
| GET /tasks/{id}/changes | Manager, Head Trainer, Vet, current assigned Groom | Actual change history; previous Groom loses task/history scope after reassignment |
| GET /inventory | Manager, Vet, Groom | Actual quantities and metadata |
| POST /inventory | Manager | New metadata; initial quantity zero |
| PUT /inventory/{id} | Manager | Metadata only; no unit changes after ledger entries |
| POST /inventory/{id}/movements | Manager | Nonzero signed changeQuantity, expectedQuantity and note; atomic conditional balance update, ledger and audit |
| GET /movements?inventoryItemId | Manager, Vet, Groom | Persisted stock ledger |
| GET /replenishments | Manager, Groom | Manager sees requests; Groom sees own only |
| POST /replenishments | Groom | Positive quantity/reason; does not create stock or an approval lifecycle |

Errors: 401 unauthenticated, 403 role denial, 404 missing/scoped-out record, 400 validation, 409 overlap/history/date/stock conflict. No runtime fixture fallback exists.

## Invariants and deliberate bounds

- Dates use TrainingCalendar and Asia/Ho_Chi_Minh. Explicit plan generation is bounded to 366 days and twenty items per request. Reads do not materialize tasks.
- A horse must have exactly one stable assignment on every generated date. Care items cannot duplicate horse/date/type/slot. Nutrition periods cannot overlap for the same horse.
- Serializability protects generation/edit checks; unsuccessful edits roll back deleted future tasks as well as plan changes. Historical and completed items remain immutable.
- `care_task` source XOR is preserved. General care, nutrition, treatment and pre-session checks stay distinct. Task completion changes neither stock nor restrictions nor session state.
- Stock movements use expectedQuantity as optimistic concurrency control, not as a universal idempotency key. Refresh/review after a conflict or uncertain response. The UI prevents duplicate clicks and does not automatically retry a write.
- Stock ledger changes are manual; no guessed ration-to-inventory mapping, unit conversion or medication guidance is introduced.
- Complete, cancel and reassign serialize on the same task row. Stale expected assignment returns409; completed/past work is immutable. Notification failure rolls back task changes, audit and history. Cancelled tasks cannot be completed, reassigned or used for a new Groom incident. Cancellation does not undo an existing incident.
- Daily summary includes `cancelled`; `total = completed + remaining + cancelled`. Task responses add nullable `cancelledAt` and `cancellationReason`. Existing plans with changes cannot be regenerated/deleted by whole-plan editing; manage each pending task instead.
- History is insert-only through this service surface; no DB-level privilege/trigger claim is made. Migration uses restrictive FKs and a filtered unique index for one cancellation per task. Metadata version20261029 has56 tables/526columns; its legacy projection matches all eight sections of the prior approved55-table snapshot. Applying it to any other existing database requires that database's own reviewed migration procedure.
- Incidents reuse POST `/api/incidents`. The existing careTaskId branch now checks actual assigned_groom_user_id, horse identity and a due date no later than today. Other Groom assignments and future task contexts are denied. Existing incident/medical authority and review endpoints remain intact.

## Local evidence and limitations

Sixteen focused fixture tests pass; these are separate from SQL Server integration. A completion whose task was replaced concurrently returns CONFLICT rather than an unhandled server error. The private local run verifies real JWTs, all seven role boundaries, actual SQL persistence, concurrent stock updates and cancel/complete races. The task-change batch has59 real HTTP requests and9 assertions, including a separately approved backup Groom. Independent static technical review passed for the extension and migration; this is not GitHub approval or a main release. Earlier browser evidence covers seven-role route/auth smoke, original Flow4 forms, forty viewports and four logout checks. Current extension evidence is recorded separately. Large-history performance and every exception remain outside exhaustive verification; filters label their loaded-data scope.

## Publication checkpoint — 2026-11-02

The previous paragraph records earlier, scoped local evidence. It is not a fresh SQL/browser Gate for the publication HEAD. FE PR33 depends on this Backend implementation. Flow5 remains a separate contract-only draft and is not included.

- Found and reproduced a DTO validation gap: `items: [null]` was accepted by Bean Validation and could reach service dereferencing. Both care and nutrition item lists now reject null elements using the existing validation boundary. Valid payloads, role rules, dates, quantities and contracts are retained.
- Red regression:4 validation tests ran, the2 null-element cases failed as expected before the fix. Green verification:20/20 focused tests pass (15 service/transaction H2 fixtures,1 incident mock fixture,4 DTO validation tests). This is not SQL Server integration proof.
- Main sources compile for Java21. Focused tests used bounded256MB Maven/test heaps. On this machine Mockito self-attachment stalled; only the owned Maven/test/helper processes were stopped. The successful run preloaded the existing Mockito5.23.0 agent, matching the Spring Boot4.1.1 dependency BOM. No test assertion, application authorization or system setting was relaxed.
- A separate-output Spring Boot JAR was packaged successfully after the focused tests (`mvn -B -DskipTests -Dracehorse.build.directory=target/flow4-publication-check package`). Compilation/package success does not imply that SQL startup passed. The previous runtime artifact was preserved; no application or SQL service was restarted by packaging.
- Canonical schema SHA256 remains `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`. All manifest file checksums and migration ordering pass. The new expected snapshot has56 tables/526columns; projecting out `care_task_change` matches every section of the previous approved snapshot exactly. These are offline package checks, not execution of a migration.
- The migration has not been applied to any database during this publication batch. Existing databases need their own approved migration procedure; a manifest/receipt does not grant overwrite or upgrade permission. The new-database installer retains its refusal to overwrite.
- Initial runtime verification was BLOCKED by SQL701/low memory. After the Leader authorized closing Claude and other personal apps, the pinned JAR started on8080 with a private256MB/SerialGC local JVM option. Strict TLS/JPA validate/JWT/RBAC remain enabled. Real API readback passed34 events across seven accounts; additional API boundary checks passed36 events, including401/403, own-Groom task scope and null-item care/nutrition requests rejected with400/VALIDATION_ERROR/item field details before service execution. No domain records were created by these negative/read probes; normal auth timestamp/audit side effects remain. This is not a new full SQL concurrency or business-write Gate.
- The paired FE's fresh real-browser audit passed103 role/route renderings/412 viewport checks with0 findings; form/modal checks passed48 checks/35 actual requests with0 business mutations. These results are distinct from the earlier HTTP/SQL write evidence and H2 fixtures. No SQL service restart, memory reconfiguration, migration, cache clearing or reset was used to recover startup.
- Publish for independent review only. Preserve authored history and require normal merge-commit approval; no main merge, cloud deployment, SQL reconfiguration or database changes are included.
