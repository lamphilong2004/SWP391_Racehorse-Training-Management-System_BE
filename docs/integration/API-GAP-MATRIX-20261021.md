# API release coverage — 22 October 2026

All 128 required operations have exactly one static controller mapping. IMPLEMENTED does not mean verified COMPLETE.
Prior baseline: 108 PARTIAL / 20 MISSING. Current formal verification: 128 PARTIAL / 0 COMPLETE; Phase 4 is pending.
Prior SQL-backed WIP positive HTTP coverage: 77/128; request-specific readback and release regression are separate.
Phase 1 tests intentionally skipped by Leader. PR25 review remains CHANGES_REQUESTED; no main merge is authorized over an unresolved required finding.

| Operation | CORE | Implementation | Prior positive HTTP | Remaining gate |
|---|---|---|---|---|
| GET /api/horses | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses | CORE-012 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{id} | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/horses/{id} | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/horses/{id}/pedigree | CORE-012 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{id}/ownerships | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{id}/ownerships | CORE-012 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{id}/photos | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{id}/photos | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/horses/{id}/timeline | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/stable-boxes | CORE-012 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{id}/stable-assignments | CORE-012 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{id}/stable-assignments | CORE-012 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/pending-activation | CORE-016 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/horses/{id}/activation-status | CORE-016 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/horses/{id}/readiness-review | CORE-016 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/owner/horses | CORE-055 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/owner/horses/{id}/profile | CORE-055 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/owner/horses/{id}/health-summary | CORE-055 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/owner/horses/{id}/training-summary | CORE-055 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/program-templates | CORE-021,CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/program-templates | CORE-021,CORE-040 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/program-templates/{id} | CORE-021 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/program-templates/{id}/versions | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/program-templates/{id}/duplicate | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/program-templates/{id}/archive | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/horses/{id}/template-recommendations | CORE-021 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/training-programs | CORE-021 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-programs | CORE-021 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-programs/{id} | CORE-040 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-programs/{id}/progress | CORE-040 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-programs/{id}/history | CORE-040 | IMPLEMENTED | YES | Phase 4 + review |
| PATCH /api/training-programs/{id}/status | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/training-programs/{id}/trainer-assignment | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/training-programs/{id}/phases | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/horses/{id}/trainer-recommendations | CORE-021 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-programs/{id}/adjustment-requests | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/adjustment-requests | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/adjustment-requests/{id}/approve | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/adjustment-requests/{id}/reject | CORE-040 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/exercises | CORE-043 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/exercises | CORE-043 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/exercises/{id} | CORE-043 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/exercises/{id} | CORE-043 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/training-sessions | CORE-024 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions | CORE-024 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-sessions/{id} | CORE-024 | IMPLEMENTED | YES | Phase 4 + review |
| PATCH /api/training-sessions/{id} | CORE-024 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/training-sessions/{id}/exercises | CORE-024 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-sessions/suitability-check | CORE-024 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-sessions/work-rider-recommendations | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/training-sessions/{id}/work-rider-recommendations | CORE-024 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-sessions/{id}/start | CORE-028 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/pause | CORE-024 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/reduce-intensity | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/resume | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/stop | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-sessions/{id}/complete | CORE-028 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/report-issue | CORE-028 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/groom/sessions | CORE-026 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/pre-session-check | CORE-026 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/work-rider/assignments | CORE-028 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/work-rider/history | CORE-028 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/training-sessions/{id}/rider-feedback | CORE-028 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/metrics | CORE-030 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/simulator/sessions/{id}/start | CORE-030 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/simulator/sessions/{id}/stop | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/monitoring-alerts/{id}/acknowledge | CORE-030 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/evaluation | CORE-032 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-sessions/{id}/result | CORE-032 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{id}/performance | CORE-032 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{horseId}/baseline-examinations | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{horseId}/weights | CORE-014 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{horseId}/weights | CORE-014 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/incidents | CORE-026 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/incidents | CORE-026 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/incidents/{incidentId} | CORE-026 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/incidents/{incidentId}/review | CORE-026 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/cases | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/medical/cases | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/medical/cases/{caseId} | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/cases/{caseId}/examinations | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/cases/{caseId}/injuries | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/cases/{caseId}/re-examinations | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/cases/{caseId}/close | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/medical/cases/{caseId}/treatment-plan | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| PUT /api/medical/cases/{caseId}/treatment-plan | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/medical/cases/{caseId}/treatment-tasks | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/groom/treatment-tasks | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/groom/treatment-tasks/{taskId}/complete | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/horses/{horseId}/training-availability | CORE-019 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/medical/restrictions | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{horseId}/medical-restrictions | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/medical/restrictions/{restrictionId}/lift | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/medical/thresholds | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| GET /api/horses/{horseId}/medical-thresholds | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| PUT /api/horses/{horseId}/medical-thresholds | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/medical/herd-health | CORE-035 | IMPLEMENTED | YES | Phase 4 + review |
| PUT /api/medical/restrictions/{restrictionId}/exercise-rules | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/horses/{horseId}/medical-thresholds/end | MANUAL_TRACE_REQUIRED | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/auth/login | CORE-007 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/auth/me | CORE-007 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/auth/logout | CORE-007 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/auth/register | CORE-047 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/auth/forgot-password | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/auth/reset-password | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/account | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| PUT /api/account | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| PUT /api/account/password | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/users | CORE-049 | IMPLEMENTED | NOT RECORDED | Phase 4 + review |
| POST /api/admin/users | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/users/{id} | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| PUT /api/admin/users/{id} | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| PATCH /api/admin/users/{id}/status | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/skills | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/roles | CORE-049 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/registrations | CORE-047 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/admin/registrations/{id}/approve | CORE-047 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/admin/registrations/{id}/reject | CORE-047 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/admin/audit-log | CORE-051 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/dashboard/club-manager | CORE-051 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/notifications | CORE-053 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/notifications/unread-count | CORE-053 | IMPLEMENTED | YES | Phase 4 + review |
| PATCH /api/notifications/{id}/read | CORE-053 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/notifications/read-all | CORE-053 | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/program-templates/trainer-specializations | MANUAL_TRACE_REQUIRED | IMPLEMENTED | YES | Phase 4 + review |
| GET /api/training-sessions/{id}/groom-candidates | CORE-026 | IMPLEMENTED | YES | Phase 4 + review |
| POST /api/training-sessions/{id}/groom-assignment | CORE-026 | IMPLEMENTED | YES | Phase 4 + review |
