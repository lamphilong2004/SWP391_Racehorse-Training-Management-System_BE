# CORE-019 — Exercise restriction decision rules

## Scope and source of truth

This note describes behavior currently implemented in:

- `src/main/java/com/racehorse/medical/restriction/RestrictionExercisePolicy.java`
- `src/main/java/com/racehorse/medical/restriction/MedicalRestrictionService.java`
- `src/test/java/com/racehorse/medical/restriction/RestrictionExercisePolicyTest.java`

This is explanatory documentation, not a proposal to change the rule engine.

## Decision sequence

`RestrictionExercisePolicy.allows(level, exerciseId, exerciseType, rules)` evaluates the current medical restriction:

1. `LOCKED` denies every exercise, regardless of any `ALLOW` rule.
2. A matching `PROHIBIT` rule denies the exercise, even if an `ALLOW` rule also matches.
3. When any `ALLOW` rules exist, at least one must match; otherwise the exercise is denied.
4. When no `ALLOW` rules exist and no matching `PROHIBIT` rule exists, the exercise is allowed.

Rules can target an `exerciseId`, or target an exercise type through `exerciseTypeNote`. Type matching trims whitespace and ignores case; the exercise ID comparison is exact.

## Service boundary

`MedicalRestrictionService.exerciseAllowed` checks that both the horse and exercise definition exist before evaluating the policy. If the horse has no active restriction, it returns `true` without calling the policy.

Creating a restriction has a pre-insert active-restriction check and translates a database duplicate-key failure into a conflict response, addressing concurrent attempts to create overlapping active restrictions.

## Maintenance notes

When changing policy behavior, update unit tests for the precedence of `LOCKED`, `PROHIBIT`, and `ALLOW`, plus tests for ID-based and case-insensitive type-based matching. This document describes the existing behavior and does not claim that any new tests were executed.
