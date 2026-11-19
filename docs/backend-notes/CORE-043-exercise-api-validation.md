# CORE-043 - Exercise API access and validation

## Implementation reference

- `ExerciseController.java`
- `ExerciseService.java`

Base endpoint: /api/exercises

## Role permissions

Read operations permit:

- HEAD_TRAINER
- TRAINER
- WORK_RIDER
- VETERINARIAN
- CLUB_MANAGER

Create and update operations permit HEAD_TRAINER and TRAINER.

## Validation boundaries

| Field | Maximum length |
|---|---:|
| Search query | 100 |
| Exercise name | 150 |
| Exercise type | 50 |
| Description | 2000 |
| Target metric | 100 |

Exercise names must be non-blank.

Resource identifiers must be positive.

## Persistence behavior

List and detail operations use read-only transactions.

Create and update operations use write transactions.

Update verifies the affected-row count and reloads the
persisted exercise before returning the response.

Invalid inputs produce a validation error.

Missing exercises produce a not-found error.

## Verification checklist

- Verify read and write role restrictions independently.
- Verify required name and maximum-length boundaries.
- Verify invalid and nonexistent identifiers.
- Verify persistence after a successful update.
