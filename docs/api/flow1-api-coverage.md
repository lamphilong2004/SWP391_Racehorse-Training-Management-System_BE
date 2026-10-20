# Flow 1 Backend API Coverage

Audit snapshot: 2026-10-20. Branch: `integration/flow1-backend-completion`.

Status is based on implementation and checked test evidence in this branch, not on route declarations alone:

- **DONE** — implementation and relevant behavior tests are present; SQL Server compatibility remains subject to the separate SQL gate.
- **PARTIAL** — implementation exists, but direct behavioral coverage or deployment availability is incomplete.
- **BLOCKED** — implementation exists, but an unapproved decision prevents acceptance.
- **MISSING** — no implementation exists.

## Flow 1 contract operations

The Flow 1 OpenAPI contract declares 20 operations.

| # | Method and path | Operation | Allowed roles | Implementation | Test evidence | Status |
|---|---|---|---|---|---|---|
| 1 | `GET /api/horses` | `listHorses` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | HorseController → HorseService → HorseRepository | No direct list-route behavioral test found in this audit | PARTIAL |
| 2 | `POST /api/horses` | `createHorse` | CLUB_MANAGER | HorseController → HorseService → HorseRepository | HorseRegistrationApiTest exercises successful creation through its helper; HorseServiceTest covers duplicate-key mapping | DONE |
| 3 | `GET /api/horses/{id}` | `getHorse` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | HorseController → HorseService → HorseRepository | No direct detail-route behavioral test found in this audit | PARTIAL |
| 4 | `PUT /api/horses/{id}` | `updateHorse` | CLUB_MANAGER | HorseController → HorseService → HorseRepository | `clubManagerCanUpdateHorseIdentificationWithoutChangingServerOwnedFields` | DONE |
| 5 | `PUT /api/horses/{id}/pedigree` | `setPedigree` | CLUB_MANAGER | HorseController → HorseService → HorseRepository; reevaluates shared activation service | `clubManagerSetsPedigreeAndReadbackMatchesPersistedReferences`, `pedigreeRejectsSelfParentAndMissingHorseReturnsNotFound`, activation and rollback cases | DONE |
| 6 | `GET /api/horses/{id}/ownerships` | `listOwnerships` | CLUB_MANAGER, HEAD_TRAINER | HorseController → HorseService → HorseRepository | `ownershipWriteRequiresActiveHorseOwnerAndHistoryIsRoleScoped` | DONE |
| 7 | `POST /api/horses/{id}/ownerships` | `addOwnership` | CLUB_MANAGER | HorseController → HorseService → HorseRepository; reevaluates shared activation service | Ownership validation, persistence/readback, and activation cases in HorseRegistrationApiTest and HorseBaselineApiTest | DONE |
| 8 | `GET /api/horses/{id}/photos` | `listHorsePhotos` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | HorsePhotoController → HorsePhotoService → HorsePhotoRepository; Trainer assignment scope enforced for metadata and image retrieval | HorsePhotoApiTest: newest-first, unassigned Trainer denied, assigned Trainer allowed, image retrieval | PARTIAL |
| 9 | `POST /api/horses/{id}/photos` | `uploadHorsePhoto` | CLUB_MANAGER | HorsePhotoController → HorsePhotoService → HorsePhotoRepository and local storage | HorsePhotoApiTest: JPEG/PNG/WebP, invalid content, size limit, authorization, cleanup/error cases | PARTIAL |
| 10 | `GET /api/horses/{id}/timeline` | `getHorseTimeline` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | HorseTimelineController → HorseTimelineService → TimelineRepository | HorseTimelineApiTest: event sources, activation boundary, date representation, ordering, authorization and redaction | DONE |
| 11 | `GET /api/stable-boxes` | `listStableBoxes` | CLUB_MANAGER, HEAD_TRAINER | StableBoxController → StableBoxService → StableBoxReadRepository | StableBoxApiTest: occupancy, filtering, authentication, role denial, validation | DONE |
| 12 | `GET /api/horses/{id}/stable-assignments` | `listStableAssignments` | CLUB_MANAGER, HEAD_TRAINER | HorseController → HorseService → HorseRepository | HorseRegistrationApiTest: assignment history, current-first ordering and role checks | DONE |
| 13 | `POST /api/horses/{id}/stable-assignments` | `assignStableBox` | CLUB_MANAGER | HorseController → HorseService → HorseRepository; reevaluates shared activation service | HorseRegistrationApiTest: move, overlap/boundary validation, rollback; HorseBaselineApiTest activation case | DONE |
| 14 | `GET /api/horses/pending-activation` | `listPendingActivation` | CLUB_MANAGER, HEAD_TRAINER, VETERINARIAN | HorseActivationController → HorseActivationService → HorseActivationRepository | ActivationStatusApiTest: checklist and paging route behavior | DONE |
| 15 | `GET /api/horses/{id}/activation-status` | `getActivationStatus` | CLUB_MANAGER, HEAD_TRAINER, VETERINARIAN | HorseActivationController → HorseActivationService → HorseActivationRepository | ActivationStatusApiTest and ReadinessReviewApiTest | DONE |
| 16 | `POST /api/horses/{id}/readiness-review` | `acknowledgeReadinessReview` | HEAD_TRAINER | HorseActivationController → HorseActivationService → audit repository | ReadinessReviewApiTest: repeated audit records and non-blocking behavior | DONE |
| 17 | `GET /api/owner/horses` | `listMyHorses` | HORSE_OWNER | OwnerHorseController → OwnerHorseService → OwnerHorseRepository | OwnerHorseListApiIntegrationTest: row scope, open ownership, auth/role and OpenAPI | DONE |
| 18 | `GET /api/owner/horses/{id}/profile` | `getMyHorseProfile` | HORSE_OWNER | OwnerHorseController → OwnerHorseService → OwnerHorseRepository | OwnerHorseProfileApiIntegrationTest: owned row projection and access scope | DONE |
| 19 | `GET /api/owner/horses/{id}/health-summary` | `getMyHorseHealthSummary` | HORSE_OWNER | OwnerHorseController → OwnerHorseService → OwnerHorseRepository | OwnerHorseHealthSummaryApiIntegrationTest: non-clinical projection and weights | DONE |
| 20 | `GET /api/owner/horses/{id}/training-summary` | `getMyHorseTrainingSummary` | HORSE_OWNER | OwnerHorseController → OwnerHorseService → OwnerHorseRepository | OwnerHorseTrainingSummaryApiIntegrationTest: selected program, sessions, redaction and row scope | BLOCKED |

Photo operations are implemented only with the `dev` and `test` Spring profiles. The configured local filesystem is not production-persistent storage; these routes are not available in profiles outside `dev`/`test`. Q-18 production storage and distribution remain unresolved.

The Owner Training Summary currently implements an ACTIVE program covering the current UTC date, latest activation then highest ID for ties, a phase only when exactly one phase covers today, and up to 10 newest sessions across all programs/statuses. Tests pass for that behavior, but this audit did not find a separate Leader approval of those selection rules. Do not treat the operation as accepted until that decision is confirmed.

## Flow 3 medical operations included in the Flow 1 delivery

These three operations are defined in `flow3-medical.openapi.yaml` and owned by CORE-014; they are reported separately and are not included in the 20-operation Flow 1 count.

| # | Method and path | Operation | Allowed roles | Implementation | Test evidence | Status |
|---|---|---|---|---|---|---|
| 21 | `POST /api/horses/{horseId}/baseline-examinations` | `recordBaselineExamination` | VETERINARIAN | HorseBaselineController → HorseBaselineService → HorseBaselineRepository; reevaluates shared activation service | HorseBaselineApiTest: atomic persistence, activation, duplicate baseline and rollback | DONE |
| 22 | `GET /api/horses/{horseId}/weights` | `listHorseWeightRecords` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | HorseBaselineController → HorseBaselineService → HorseBaselineRepository | HorseBaselineApiTest: persisted readback; Trainer scope follows shared horse authorization | DONE |
| 23 | `POST /api/horses/{horseId}/weights` | `recordHorseWeight` | VETERINARIAN | HorseBaselineController → HorseBaselineService → HorseBaselineRepository | HorseBaselineApiTest: validation and persisted readback | DONE |

## Verification and remaining gates

- Flow 1 operations: 20 total — 15 DONE, 4 PARTIAL, 1 BLOCKED.
- Flow 3 baseline/weight operations: 3 total — 3 DONE.
- Combined: 23 operations — 18 DONE, 4 PARTIAL, 1 BLOCKED.
- Java 21 `mvn -B verify`: 130 tests, 0 failures, 0 errors, 15 skipped.
- The 15 skips are SQL Server integration tests gated on absent `core007.sqlserver.it`, `core012.sqlserver.it`, and `core016.sqlserver.it` properties. No approved isolated SQL Server test database was available; SQL Server persistence, generated keys, transaction behavior, constraints, rollback, and real-database RBAC remain NOT VERIFIED.
- Swagger runtime smoke passed for `/swagger-ui.html` and `/v3/api-docs`.
- Redocly lint passed for `flow1-horse.openapi.yaml` and `flow3-medical.openapi.yaml`.
- `git diff --check` passed.
- The PR's current GitHub check run was successful before this audit commit; the new commit requires its own CI run.
