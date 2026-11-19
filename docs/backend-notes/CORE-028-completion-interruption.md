# CORE-028 - Completion and interruption safeguards

## Implementation reference

`Core028Service.complete`, `stop` and `reportIssue`.

## Normal completion

Only the assigned Work Rider can complete an IN_PROGRESS session.

The update changes the status to COMPLETED and records the actual
end timestamp using the database UTC clock.

The conditional update checks the previous session status.
An unexpected affected-row count produces a conflict.

## Trainer stop

The stop endpoint requires the TRAINER role.

A medically relevant stop requires a supported incident type
and observed severity.

A stop reason must be non-blank and within 1000 characters.

## Incident interruption

Issue reporting is restricted to IN_PROGRESS and PAUSED sessions.

The service checks rider ownership or Trainer authorization.

An incident record is created and the session transitions
to INTERRUPTED.

These mutation methods use SERIALIZABLE transaction isolation.

## Verification checklist

- Reject completion of a PAUSED session.
- Reject interruption of a SCHEDULED session.
- Reject unauthorized session mutations.
- Reject a medically relevant stop with incomplete incident fields.
- Verify conflict handling when the session state changes.

No production behavior is modified by this document.
