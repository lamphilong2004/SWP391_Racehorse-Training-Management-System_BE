# CORE-026 — Pre-session checks and incident workflow

## Scope and source of truth

Primary implementation: `src/main/java/com/racehorse/medical/incident/Core026Service.java`.

This is a behavior reference for maintainers; it does not change code, persistence or API contracts.

## Groom pre-session check

`submitPrecheck(sessionId, request)` runs with `SERIALIZABLE` isolation and locks the training session before validating and writing a checklist.

The service requires:

- An authenticated actor with the `GROOM` role, assigned or notified for the session.
- Session status `SCHEDULED`.
- No earlier pre-session check for that session.
- Current time no earlier than 12 hours before scheduled start.
- Current time no later than scheduled end, falling back to start plus 12 hours when the end is absent.
- A valid checklist that can be serialized within the documented persistence capacity (4,000 characters).

The recorded result is `ALL_NORMAL` only if every checklist item is `NORMAL`; otherwise it is `ABNORMAL`. An abnormal result triggers persisted medical notifications to the appropriate recipients. A database integrity error on insert is returned as a conflict to handle duplicate attempts.

## Reporting an incident

`create(request)` permits authorized operational roles to submit an incident. At most one source reference can be supplied from `preSessionCheckId`, `trainingSessionId`, or `careTaskId`. A supplied source must refer to the same horse as `horseId`.

A request also passes authorization for the actor's scope. The service checks for an existing incident associated with the source and handles concurrent uniqueness violations as conflicts. After creation it notifies medical recipients, except the reporter.

## Veterinary review

Only the `VETERINARIAN` role can review an incident. The review workflow supports opening a new medical case or linking an eligible existing case, with restrictions on incident state and cross-horse case associations.

## Verification boundaries

The points above are read from existing service code. They are not evidence of a new test run, nor a claim that every edge case is already covered by integration tests.
