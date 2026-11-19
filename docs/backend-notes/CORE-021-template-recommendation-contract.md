# CORE-021 - Template recommendation criteria

## Implementation reference

`TrainingProgramService.templateRecommendations`.

The recommendation endpoint requires the HEAD_TRAINER role.

## Candidate selection

Only active program templates are considered.

The query selects the latest version of each active template.

The service reads the horse's breed, sex and declared strengths.

When no relevant horse profile fields or strengths are available,
the recommendation result is empty.

## Matching behavior

Breed and sex are compared against the template's suitable
horse profile using case-insensitive text matching.

Declared horse strengths are compared with template strength focus.

Templates without a matching reason are omitted.

Matching declared strengths are presented with a warning that
the declaration has not been confirmed by the Head Trainer.

Recommendations with more matching strengths are listed first.

## Interpretation

SUITABLE is a recommendation label, not a medical clearance,
a probability of success or a numerical suitability score.

The recommendation is not a substitute for the wider
training and medical eligibility workflow.
