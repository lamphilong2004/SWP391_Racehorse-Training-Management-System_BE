# API Wave 1 Endpoint Coverage

**Candidate:** `integration/api-wave1-review-20261020` | **Source:** Flow 1/2/3 OpenAPI `operationId` and `x-roles` values.

## Summary

| Contract | Operations | Controller mappings | Missing |
|---|---:|---:|---:|
| Flow 1 | 20 | 20 | 0 |
| Flow 2 | 51 | 37 | 14 |
| Flow 3 | 29 | 17 | 12 |
| **Total** | **100** | **74** | **26** |

Counts cover only the three Flow 1/2/3 contracts; `shared.openapi.yaml` is not included in the 100-operation denominator. `MAPPED` means a controller mapping exists. The H2 column identifies the area suite present in the successful full suite, not proof that every row has a dedicated assertion. SQL evidence is uniformly unverified.

## Ownership, priority, and deferral of the 26 missing operations

Tracker ownership/priority checked against `Racehorse_Team_Project_Tracking_Gated_PR.xlsx` on 2026-10-20. No missing operation below is assigned to Dũng for implementation; Dũng owns integration/release coordination. CORE-026 and CORE-035 are marked `Reassigned` in the tracker even though the Member Queues still list Tiến, so confirm their current assignees before assigning implementation.

| Priority | CORE | Missing operation IDs | Current tracker owner/state | Deferral |
|---|---|---|---|---|
| P0 | CORE-021 | `setProgramStatus` | Tiến; missing in PR #22 | Not marked deferrable by tracker priority |
| P0 | CORE-024 | `reduceSessionIntensity` | Tiến; missing in PR #21 | Not marked deferrable by tracker priority |
| P0 | CORE-026 | `listGroomSessions` | Reassigned; current owner needs confirmation | Groom assignment/notification mapping is also a dependency; no acceptance waiver recorded |
| P0 | CORE-030 | `submitMetricSample`, `startSimulator`, `stopSimulator`, `acknowledgeAlert` | Tiến | Not marked deferrable by tracker priority |
| P0 | CORE-032 | `submitEvaluation`, `getSessionResult`, `getHorsePerformance` | Tiến | Not marked deferrable by tracker priority |
| P0 | CORE-035 | `listMedicalCases`, `createMedicalCase`, `getMedicalCase`, `createMedicalExamination`, `createInjury`, `createReexamination`, `closeMedicalCase`, `getTreatmentPlan`, `putTreatmentPlan` | Reassigned; current owner needs confirmation | Not marked deferrable by tracker priority |
| P1 | CORE-040 | `createAdjustmentRequest`, `listAdjustmentRequests`, `approveAdjustmentRequest`, `rejectAdjustmentRequest` | Tiến | Lower priority than P0; sequencing later is possible, but no acceptance waiver is recorded |
| P1 | CORE-045 | `listTreatmentCaseTasks`, `getMyTreatmentTasks`, `completeTreatmentTask` | Tiến | Lower priority than P0; sequencing later is possible, but no acceptance waiver is recorded |

**Priority totals:** P0 = 19 missing operations; P1 = 7. P1 sequencing after P0 is not equivalent to declaring those APIs complete or formally removing them from the approved contract.

H2 verification on Java 21: `mvn -B -ntp verify` exit 0, 198 tests, 0 failures, 0 errors, 15 SQL Server-gated skips. Pinned Redocly 2.63.0 lint passed for the three flow contracts and shared contract.

SQL Server: **NOT RUN**. No Tailscale client or SQL environment variables are available here; the approved SQL endpoint was unreachable. Existing CORE-007/012/016 guards require per-CORE disposable local databases, so `racehorse_integration_test` cannot be substituted without approved runner/guard setup.

## Flow 1 ($(System.Collections.Hashtable.key))

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

## Flow 2 ($(System.Collections.Hashtable.key))

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
| PATCH | `/api/training-programs/{id}/status` | `setProgramStatus` | HEAD_TRAINER | MISSING | No route/test | NOT RUN | CORE-021 implementation |
| PUT | `/api/training-programs/{id}/trainer-assignment` | `reassignTrainer` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| PUT | `/api/training-programs/{id}/phases` | `adjustProgramPhases` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{id}/trainer-recommendations` | `getTrainerRecommendations` | HEAD_TRAINER | MAPPED | TrainingProgramApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-programs/{id}/adjustment-requests` | `createAdjustmentRequest` | TRAINER | MISSING | No route/test | NOT RUN | CORE-040 implementation |
| GET | `/api/adjustment-requests` | `listAdjustmentRequests` | HEAD_TRAINER, TRAINER | MISSING | No route/test | NOT RUN | CORE-040 implementation |
| POST | `/api/adjustment-requests/{id}/approve` | `approveAdjustmentRequest` | HEAD_TRAINER | MISSING | No route/test | NOT RUN | CORE-040 implementation |
| POST | `/api/adjustment-requests/{id}/reject` | `rejectAdjustmentRequest` | HEAD_TRAINER | MISSING | No route/test | NOT RUN | CORE-040 implementation |
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
| POST | `/api/training-sessions/{id}/reduce-intensity` | `reduceSessionIntensity` | TRAINER | MISSING | No route/test | NOT RUN | CORE-024 implementation |
| POST | `/api/training-sessions/{id}/resume` | `resumeSession` | TRAINER | MAPPED | TrainingSessionApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/stop` | `stopSession` | TRAINER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/complete` | `completeSession` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/report-issue` | `reportSessionIssue` | WORK_RIDER, TRAINER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/groom/sessions` | `listGroomSessions` | GROOM | MISSING | No route/test | NOT RUN | CORE-026 Groom assignment/notification mapping |
| POST | `/api/training-sessions/{id}/pre-session-check` | `submitPreSessionCheck` | GROOM | BLOCKED (fail-closed) | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | Approved Groom assignment/notification mapping |
| GET | `/api/work-rider/assignments` | `listMyAssignments` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/work-rider/history` | `listMyRidingHistory` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/rider-feedback` | `submitRiderFeedback` | WORK_RIDER | MAPPED | Core028ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/training-sessions/{id}/metrics` | `submitMetricSample` | WORK_RIDER | MISSING | No route/test | NOT RUN | CORE-030 implementation |
| POST | `/api/simulator/sessions/{id}/start` | `startSimulator` | TRAINER | MISSING | No route/test | NOT RUN | CORE-030 implementation |
| POST | `/api/simulator/sessions/{id}/stop` | `stopSimulator` | TRAINER | MISSING | No route/test | NOT RUN | CORE-030 implementation |
| POST | `/api/monitoring-alerts/{id}/acknowledge` | `acknowledgeAlert` | TRAINER, VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-030 implementation |
| POST | `/api/training-sessions/{id}/evaluation` | `submitEvaluation` | TRAINER | MISSING | No route/test | NOT RUN | CORE-032 implementation |
| GET | `/api/training-sessions/{id}/result` | `getSessionResult` | HEAD_TRAINER, TRAINER, WORK_RIDER, CLUB_MANAGER, VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-032 implementation |
| GET | `/api/horses/{id}/performance` | `getHorsePerformance` | HEAD_TRAINER, TRAINER, CLUB_MANAGER, VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-032 implementation |

## Flow 3 ($(System.Collections.Hashtable.key))

| Method | Path | operationId | Allowed roles | Status | H2 evidence | SQL evidence | Known dependency / blocker |
|---|---|---|---|---|---|---|---|
| POST | `/api/horses/{horseId}/baseline-examinations` | `recordBaselineExamination` | VETERINARIAN | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/horses/{horseId}/weights` | `listHorseWeightRecords` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/horses/{horseId}/weights` | `recordHorseWeight` | VETERINARIAN | MAPPED | HorseBaselineApiTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/incidents` | `listIncidents` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/incidents` | `reportIncident` | HEAD_TRAINER, TRAINER, WORK_RIDER, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/incidents/{incidentId}` | `getIncident` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| POST | `/api/incidents/{incidentId}/review` | `reviewIncident` | VETERINARIAN | MAPPED | Core026ApiIntegrationTest (PASS) | NOT RUN (SQL gate unverified) | — |
| GET | `/api/medical/cases` | `listMedicalCases` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| POST | `/api/medical/cases` | `createMedicalCase` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| GET | `/api/medical/cases/{caseId}` | `getMedicalCase` | CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| POST | `/api/medical/cases/{caseId}/examinations` | `createMedicalExamination` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| POST | `/api/medical/cases/{caseId}/injuries` | `createInjury` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| POST | `/api/medical/cases/{caseId}/re-examinations` | `createReexamination` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| POST | `/api/medical/cases/{caseId}/close` | `closeMedicalCase` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| GET | `/api/medical/cases/{caseId}/treatment-plan` | `getTreatmentPlan` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| PUT | `/api/medical/cases/{caseId}/treatment-plan` | `putTreatmentPlan` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-035 medical-case/treatment implementation |
| GET | `/api/medical/cases/{caseId}/treatment-tasks` | `listTreatmentCaseTasks` | VETERINARIAN | MISSING | No route/test | NOT RUN | CORE-045 treatment-task implementation |
| GET | `/api/groom/treatment-tasks` | `getMyTreatmentTasks` | GROOM | MISSING | No route/test | NOT RUN | CORE-045 treatment-task implementation |
| POST | `/api/groom/treatment-tasks/{taskId}/complete` | `completeTreatmentTask` | GROOM | MISSING | No route/test | NOT RUN | CORE-045 treatment-task implementation |
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
