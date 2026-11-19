# CORE-021 - Trainer specialization matching

## Implementation reference

`TrainingProgramService.trainerRecommendations`.

The endpoint requires the HEAD_TRAINER role.

The horse and active template version must exist.

## Candidate scope

Every active user with the TRAINER role is considered.

Trainer specialization skills are compared with the
required skills of the selected template version.

## Recommendation labels

SUITABLE is returned when no specialization is required
or at least one required specialization matches.

NOT_RECOMMENDED is returned when specialization requirements
exist but the candidate has no matching skill.

A specialization mismatch does not remove an otherwise
active Trainer from the candidate list.

Matching candidates appear before candidates with warnings.

## Empty-state interpretation

The service distinguishes between no active Trainer accounts
and active Trainers without matching specializations.

The recommendation list must not be interpreted as an
automatic assignment or a guarantee of authorization.
