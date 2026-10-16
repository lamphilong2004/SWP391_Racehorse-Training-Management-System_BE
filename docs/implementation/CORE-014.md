# CORE-014: veterinarian baseline, weights, health

## Behavior

- POST /api/horses/{horseId}/baseline-examinations: 201 envelope.
  Body: weightKg (positive DECIMAL(6,2)), healthStatus
  (FIT, MONITORING, INJURED, QUARANTINED), optional clinicalNotes.
  Saves veterinary_examination (BASELINE, findings = clinicalNotes),
  weight_record (context = BASELINE), and horse.health_status/updated_at atomically.
  The canonical filtered index permits one baseline per horse. Duplicate baseline returns 409 CONFLICT.
  Does not activate the horse; activation belongs to CORE-016.
- POST /api/horses/{horseId}/weights: 201 envelope.
  Body: weightKg, optional recordedAt (not future; defaults to server UTC time).
  Persists measured_at = recordedAt and recorded_by_user_id = authenticated actor.
  Does not alter health status.
- GET /api/horses/{horseId}/weights?page=0&size=20: 200 envelope.
  Data: items, page, size, totalElements. Size 1–100, page >= 0.
  Ordered by measured_at DESC, id DESC.

IDs use SQL BIGINT / JSON integers. Timestamps are UTC with millisecond precision.
Only the schema-backed one-baseline rule is imposed; no undocumented lifecycle
restriction is introduced for horses lacking a baseline.

## Security integration required (CORE-007)

The foundation has no security implementation or authenticated principal type.
MedicalIdentityProvider is an explicit integration interface, not an authentication
implementation. CORE-007 must supply exactly one bean that reads its verified
security context and returns the authenticated database user id and verified role codes,
or null for an unauthenticated caller. CORE-014 does not parse JWTs, accept identity
headers, hardcode an actor, or provide a default identity.

Without that bean, all three service operations deny access with 401 UNAUTHORIZED.
Writes require VETERINARIAN. Reads permit CLUB_MANAGER, HEAD_TRAINER, TRAINER,
VETERINARIAN, GROOM. Trainer-only access checks an open trainer_assignment joined
through horse_training_program, effective_from <= current UTC date. Out-of-scope
access returns 403 before retrieving horse or weight data. A caller with another
permitted role has that role's broader read grant.

## Ownership and schema

Entities map dbo.veterinary_examination and dbo.weight_record. Unused nullable
examination columns remain null. No canonical SQL or foundation files are changed.
MedicalHorseRepository performs narrow native SQL operations against existing horse
and assignment tables, avoiding a duplicate partial Horse entity or implementation
of CORE-012. SQL Server UPDLOCK/HOLDLOCK serializes writes to a horse for the duration
of the service transaction. The canonical unique baseline index remains authoritative.

The local foundation lacks docs/api/shared.openapi.yaml and permission-matrix.md.
They were read from origin/dev and the CORE-006 contract branch.
origin/feat/CORE-006-flow3-medical-contract marks its medical contract DRAFT and
its persistence mapping UNVERIFIED. This implementation follows the user's explicit
endpoint authorization and actual schema. Draft discrepancies: BIGINT IDs replace
unconfirmed string IDs, clinicalNotes maps to findings, and business errors use the
foundation's 409 BUSINESS_RULE_VIOLATION convention rather than the draft's 422.
The weight page follows the medical contract's flat items/page/size/totalElements shape.

## Verification and outstanding integration checks

Automated tests exercise service rules, authorization, HTTP envelopes, real JPA
persistence, deterministic pagination, assignment SQL and transaction rollback.
The H2 persistence fixture is deliberately isolated and replaces SQL Server lock
hints with FOR UPDATE in test code only. It is not a canonical schema migration.

Not yet verified:
- Real JWT authentication and inactive-account enforcement: CORE-007 is absent.
- Full horse-profile API integration: CORE-012 is absent.
- Canonical SQL Server schema validation, FK constraints, filtered unique index and
  concurrent SQL Server locking: requires a configured SQL Server test database.
No successful results are claimed for these checks.

The machine's default Java is 17 and Maven is not on PATH. Verification used the
VS Code Red Hat Java extension's bundled JDK 25 with Maven compiling for release 21,
and Apache Maven 3.9.9 downloaded into the OS temporary directory.