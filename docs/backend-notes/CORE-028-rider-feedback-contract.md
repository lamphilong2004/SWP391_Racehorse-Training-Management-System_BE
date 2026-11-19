# CORE-028 - Rider feedback contract

## Implementation reference

`Core028Controller.feedback` and `Core028Service.feedback`.

Feedback is submitted through:

POST /api/training-sessions/{id}/rider-feedback

## Submission eligibility

- The caller must be a WORK_RIDER.
- The caller must be the assigned rider.
- The session must be COMPLETED or INTERRUPTED.

## Supported response fields

- horseResponse
- balance
- breathing
- strideFeeling
- abnormalBehavior
- preInterruptionDescription
- notes

Unsupported keys are rejected.

Non-null response values must be strings within the
corresponding field limits.

## Duplicate submissions

The service checks for an existing feedback record before insertion.

A database integrity violation during insertion is also translated
into a duplicate-submission conflict.

The feedback response includes its identifier, session identifier,
submitter, submission timestamp and submitted responses.

## Verification checklist

- Reject feedback for an active session.
- Reject feedback from another rider.
- Reject unsupported response fields.
- Reject a second submission for the same session.
