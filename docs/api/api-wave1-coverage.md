# API Wave 1 Endpoint Coverage

**Candidate:** `integration/api-wave1-review-20261020` | **Source:** Flow 1/2/3 OpenAPI `operationId` and `x-roles` values.

## Summary

| Contract | Operations | Controller mappings | Missing |
|---|---:|---:|---:|
| Flow 1 | 20 | 20 | 0 |
| Flow 2 | 51 | 49 | 2 |
| Flow 3 | 29 | 29 | 0 |
| **Total** | **100** | **98** | **2** |

Counts cover only the three Flow 1/2/3 contracts; `shared.openapi.yaml` is not included in the 100-operation denominator. `MAPPED` means a controller mapping exists and is registered by the local Spring Boot Swagger document. The H2 column identifies the relevant passing suite, not proof that every row has a dedicated behavior assertion. SQL evidence is uniformly unverified.

## Remaining contract blockers

| CORE | Operation | Status | Blocker |
|---|---|---|---|
| CORE-024 | `reduceSessionIntensity` | BLOCKED — needs contract confirmation | The Flow 2 contract marks this operation as proposed and says its signature needs confirmation. Do not expose an invented write path. |
| CORE-026 | `listGroomSessions` | BLOCKED — dependency unresolved | The Groom-to-session assignment/notification mapping is not approved or persisted. CORE-026 pre-session-check access remains fail-closed; the combined CORE-035/045 branch does not add session assignment. |

The other 98 operations have controller mappings. Existing partial/business blockers remain: Owner Training Summary selection semantics need Leader approval; Horse Photo storage is development/test filesystem only; CORE-021 recommendation ordering differs between approved policy and OpenAPI wording.

Java 21.0.12.1 / Maven 3.9.16 full `mvn -B verify`: **PASS**, 227 tests, 0 failures, 0 errors, 15 SQL Server-gated skips (212 passed). Redocly CLI 2.63.0: **valid**, 8 built-in warnings (missing `info.license` and localhost development server URLs). The local `SwaggerRuntimeSmokeTest` verified the additional Flow 2/3 mappings and operation IDs over HTTP.

SQL Server: **SQL_NOT_RUN — LEADER_RUNNER_PENDING**. The Leader will run the authorized SQL Server integration and E2E checks on the SQL host. No SQL database was modified here; H2 results are not SQL evidence.

## Flow 1

| Method | Path | operationId | Allowed roles | Status | H2 evidence | SQL evidence | Known dependency / blocker |
|---|---|---|---|---|---|---|---|
| GET | `/api/horses` | `listHorses` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses` | `createHorse` | CLUB_MANAGER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}` | `getHorse` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/horses/{id}` | `updateHorse` | CLUB_MANAGER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/horses/{id}/pedigree` | `setPedigree` | CLUB_MANAGER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/ownerships` | `listOwnerships` | CLUB_MANAGER, HEAD_TRAINER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{id}/ownerships` | `addOwnership` | CLUB_MANAGER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/photos` | `listHorsePhotos` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | PARTIAL | HorsePhotoApiTest (PASS) | NOT RUN (SQL gate unverified) | Development/test filesystem only; production-persistent storage/distribution unresolved |
| POST | `/api/horses/{id}/photos` | `uploadHorsePhoto` | CLUB_MANAGER | PARTIAL | HorsePhotoApiTest (PASS) | NOT RUN (SQL gate unverified) | Development/test filesystem only; production-persistent storage/distribution unresolved |
| GET | `/api/horses/{id}/timeline` | `getHorseTimeline` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/stable-boxes` | `listStableBoxes` | CLUB_MANAGER, HEAD_TRAINER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/stable-assignments` | `listStableAssignments` | CLUB_MANAGER, HEAD_TRAINER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{id}/stable-assignments` | `assignStableBox` | CLUB_MANAGER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/pending-activation` | `listPendingActivation` | CLUB_MANAGER, HEAD_TRAINER, VETERINARIAN | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/activation-status` | `getActivationStatus` | CLUB_MANAGER, HEAD_TRAINER, VETERINARIAN | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{id}/readiness-review` | `acknowledgeReadinessReview` | HEAD_TRAINER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/owner/horses` | `listMyHorses` | HORSE_OWNER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/owner/horses/{id}/profile` | `getMyHorseProfile` | HORSE_OWNER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/owner/horses/{id}/health-summary` | `getMyHorseHealthSummary` | HORSE_OWNER | MAPPED | See flow1-api-coverage.md (per-operation test evidence) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/owner/horses/{id}/training-summary` | `getMyHorseTrainingSummary` | HORSE_OWNER | BLOCKED | OwnerHorseTrainingSummaryApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | Leader approval required for selection semantics |

## Flow 2

| Method | Path | operationId | Allowed roles | Status | H2 evidence | SQL evidence | Known dependency / blocker |
|---|---|---|---|---|---|---|---|
| GET | `/api/program-templates` | `listProgramTemplates` | HEAD_TRAINER, TRAINER, CLUB_MANAGER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/program-templates` | `createProgramTemplate` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/program-templates/{id}` | `getProgramTemplate` | HEAD_TRAINER, TRAINER, CLUB_MANAGER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/program-templates/{id}/versions` | `saveTemplateVersion` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/program-templates/{id}/duplicate` | `duplicateProgramTemplate` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/program-templates/{id}/archive` | `archiveProgramTemplate` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/template-recommendations` | `getTemplateRecommendations` | HEAD_TRAINER | BLOCKED (decision conflict) | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | Resolve approved unordered recommendations vs OpenAPI best-label-first wording |
| GET | `/api/training-programs` | `listTrainingPrograms` | HEAD_TRAINER, TRAINER, CLUB_MANAGER, VETERINARIAN, GROOM | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-programs` | `createTrainingProgram` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-programs/{id}` | `getTrainingProgram` | HEAD_TRAINER, TRAINER, CLUB_MANAGER, VETERINARIAN, GROOM | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-programs/{id}/progress` | `getProgramProgress` | HEAD_TRAINER, TRAINER, CLUB_MANAGER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-programs/{id}/history` | `getProgramHistory` | HEAD_TRAINER, TRAINER, CLUB_MANAGER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PATCH | `/api/training-programs/{id}/status` | `setProgramStatus` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| PUT | `/api/training-programs/{id}/trainer-assignment` | `reassignTrainer` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/training-programs/{id}/phases` | `adjustProgramPhases` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/trainer-recommendations` | `getTrainerRecommendations` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-programs/{id}/adjustment-requests` | `createAdjustmentRequest` | TRAINER | MAPPED | Core040ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/adjustment-requests` | `listAdjustmentRequests` | HEAD_TRAINER, TRAINER | MAPPED | Core040ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/adjustment-requests/{id}/approve` | `approveAdjustmentRequest` | HEAD_TRAINER | MAPPED | Core040ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/adjustment-requests/{id}/reject` | `rejectAdjustmentRequest` | HEAD_TRAINER | MAPPED | Core040ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/exercises` | `listExercises` | HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, CLUB_MANAGER | MAPPED | ExerciseSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/exercises` | `createExercise` | HEAD_TRAINER, TRAINER | MAPPED | ExerciseSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/exercises/{id}` | `getExercise` | HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, CLUB_MANAGER | MAPPED | ExerciseSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/exercises/{id}` | `updateExercise` | HEAD_TRAINER, TRAINER | MAPPED | ExerciseSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-sessions` | `listTrainingSessions` | HEAD_TRAINER, TRAINER, WORK_RIDER, CLUB_MANAGER, VETERINARIAN | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions` | `createTrainingSession` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-sessions/{id}` | `getTrainingSession` | HEAD_TRAINER, TRAINER, WORK_RIDER, GROOM, CLUB_MANAGER, VETERINARIAN | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PATCH | `/api/training-sessions/{id}` | `updateSessionBasics` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/training-sessions/{id}/exercises` | `replaceSessionExercises` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/suitability-check` | `checkSessionSuitability` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/work-rider-recommendations` | `recommendWorkRidersForDraft` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/training-sessions/{id}/work-rider-recommendations` | `recommendWorkRiders` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/start` | `startSession` | WORK_RIDER, TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/pause` | `pauseSession` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/reduce-intensity` | `reduceSessionIntensity` | TRAINER | BLOCKED (contract confirmation required) | No route; contract marks signature proposed | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Do not expose until the operation signature and behavior are approved |
| POST | `/api/training-sessions/{id}/resume` | `resumeSession` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/stop` | `stopSession` | TRAINER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/complete` | `completeSession` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/report-issue` | `reportSessionIssue` | WORK_RIDER, TRAINER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/groom/sessions` | `listGroomSessions` | GROOM | BLOCKED (dependency unresolved) | No route; no persisted Groom-to-session assignment | SQL_NOT_RUN — LEADER_RUNNER_PENDING | CORE-026 assignment/notification mapping must be approved; pre-session check remains fail-closed |
| POST | `/api/training-sessions/{id}/pre-session-check` | `submitPreSessionCheck` | GROOM | BLOCKED (fail-closed) | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | Approved Groom assignment/notification mapping |
| GET | `/api/work-rider/assignments` | `listMyAssignments` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/work-rider/history` | `listMyRidingHistory` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/rider-feedback` | `submitRiderFeedback` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/metrics` | `submitMetricSample` | WORK_RIDER | MAPPED | Core030ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/simulator/sessions/{id}/start` | `startSimulator` | TRAINER | MAPPED | Core030ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Dev/demo profiles only; simulator disabled by default |
| POST | `/api/simulator/sessions/{id}/stop` | `stopSimulator` | TRAINER | MAPPED | Core030ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Dev/demo profiles only; simulator disabled by default |
| POST | `/api/monitoring-alerts/{id}/acknowledge` | `acknowledgeAlert` | TRAINER, VETERINARIAN | MAPPED | Core030ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/training-sessions/{id}/evaluation` | `submitEvaluation` | TRAINER | MAPPED | Core032ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/training-sessions/{id}/result` | `getSessionResult` | HEAD_TRAINER, TRAINER, WORK_RIDER, CLUB_MANAGER, VETERINARIAN | MAPPED | Core032ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/horses/{id}/performance` | `getHorsePerformance` | HEAD_TRAINER, TRAINER, CLUB_MANAGER, VETERINARIAN | MAPPED | Core032ApiIntegrationTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |

## Flow 3

| Method | Path | operationId | Allowed roles | Status | H2 evidence | SQL evidence | Known dependency / blocker |
|---|---|---|---|---|---|---|---|
| POST | `/api/horses/{horseId}/baseline-examinations` | `recordBaselineExamination` | VETERINARIAN | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{horseId}/weights` | `listHorseWeightRecords` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{horseId}/weights` | `recordHorseWeight` | VETERINARIAN | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/incidents` | `listIncidents` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/incidents` | `reportIncident` | HEAD_TRAINER, TRAINER, WORK_RIDER, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/incidents/{incidentId}` | `getIncident` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/incidents/{incidentId}/review` | `reviewIncident` | VETERINARIAN | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/medical/cases` | `listMedicalCases` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN | MAPPED | MedicalApiSecurityTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/medical/cases` | `createMedicalCase` | VETERINARIAN | MAPPED | MedicalServiceTest + MedicalApiSecurityTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/medical/cases/{caseId}` | `getMedicalCase` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN | MAPPED | MedicalApiSecurityTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/medical/cases/{caseId}/examinations` | `createMedicalExamination` | VETERINARIAN | MAPPED | MedicalServiceTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/medical/cases/{caseId}/injuries` | `createInjury` | VETERINARIAN | MAPPED | MedicalServiceTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/medical/cases/{caseId}/re-examinations` | `createReexamination` | VETERINARIAN | MAPPED | MedicalServiceTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| POST | `/api/medical/cases/{caseId}/close` | `closeMedicalCase` | VETERINARIAN | MAPPED | MedicalServiceTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/medical/cases/{caseId}/treatment-plan` | `getTreatmentPlan` | VETERINARIAN | MAPPED | MedicalTreatmentPlanFlowTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| PUT | `/api/medical/cases/{caseId}/treatment-plan` | `putTreatmentPlan` | VETERINARIAN | MAPPED | MedicalTreatmentPlanFlowTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | — |
| GET | `/api/medical/cases/{caseId}/treatment-tasks` | `listTreatmentCaseTasks` | VETERINARIAN | MAPPED | GroomTreatmentTaskApiTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Groom assignment migration must be applied to target database |
| GET | `/api/groom/treatment-tasks` | `getMyTreatmentTasks` | GROOM | MAPPED | GroomTreatmentTaskApiTest + MedicalTreatmentPlanFlowTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Groom assignment migration must be applied to target database |
| POST | `/api/groom/treatment-tasks/{taskId}/complete` | `completeTreatmentTask` | GROOM | MAPPED | GroomTreatmentTaskApiTest + MedicalTreatmentPlanFlowTest + SwaggerRuntimeSmokeTest (PASS) | SQL_NOT_RUN — LEADER_RUNNER_PENDING | Groom assignment migration must be applied to target database |
| GET | `/api/horses/{horseId}/training-availability` | `getHorseTrainingAvailability` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/medical/restrictions` | `listMedicalRestrictions` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{horseId}/medical-restrictions` | `createMedicalRestriction` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/medical/restrictions/{restrictionId}/lift` | `liftMedicalRestriction` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/medical/thresholds` | `listGlobalMedicalThresholds` | HEAD_TRAINER, TRAINER, VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{horseId}/medical-thresholds` | `getHorseThresholdOverrides` | HEAD_TRAINER, TRAINER, VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/horses/{horseId}/medical-thresholds` | `setHorseThresholdOverride` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/medical/herd-health` | `getHerdHealthOverview` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/medical/restrictions/{restrictionId}/exercise-rules` | `replaceRestrictionExerciseRules` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{horseId}/medical-thresholds/end` | `endHorseThresholdOverride` | VETERINARIAN | MAPPED | MedicalRestrictionSecurityIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
