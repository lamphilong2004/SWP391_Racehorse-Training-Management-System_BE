# CORE-024 — Training session lifecycle and scheduling guards

## Scope and source of truth

Primary implementation: `src/main/java/com/racehorse/training/session/TrainingSessionService.java`.

This document describes the current lifecycle checks and scheduling constraints. No code or state transitions are changed.

## Scheduling a session

`create` requires the `TRAINER` role and validates the proposed schedule and intensity. It uses a `SERIALIZABLE` transaction, verifies that the selected Work Rider is active, and locks the rider schedule before checking for overlap.

Both rider scheduling conflicts and horse session overlap are checked before insertion. The service then evaluates rider warning acknowledgement and medical/exercise suitability. A medically blocked session or an exercise with a hard-block result (`A`) cannot be scheduled.

After validation, the service inserts the training session and its exercise records, records notification and audit information, and returns the session details.

## Lifecycle checks

The service exposes operations such as `start`, `transition`, `resume`, and `reduceIntensity`. These are not interchangeable operations: each checks the current session state and its applicable actor or medical gates.

In particular, `resume` requires a fresh `ALL_NORMAL` Groom pre-session check and re-evaluates the applicable medical restriction/exercise suitability before moving a paused session to `IN_PROGRESS`.

## Important invariants

- Role and assignment checks are required; knowing a session ID alone is not authorization.
- Schedule checks should remain within the appropriate transaction/locking scope.
- Medical restriction changes may invalidate a previously suitable exercise plan, which is why resumption re-checks suitability.
- A state transition can fail because another request changed the row; callers should reload the current state instead of retrying blindly.

## Verification boundaries

This is a maintainer's reference based on the current service implementation, not a new API specification or evidence of a successful test run.
