package com.racehorse.medical.restriction;

import java.util.List;

public final class RestrictionExercisePolicy {

    public boolean allows(RestrictionLevel level, long exerciseId, String exerciseType, List<ExerciseRule> rules) {
        if (level == RestrictionLevel.LOCKED) {
            return false;
        }

        List<ExerciseRule> exerciseRules = List.copyOf(rules);
        if (exerciseRules.stream().anyMatch(rule -> rule.ruleType() == ExerciseRuleType.PROHIBIT
                && matches(rule, exerciseId, exerciseType))) {
            return false;
        }

        boolean hasAllowRules = exerciseRules.stream().anyMatch(rule -> rule.ruleType() == ExerciseRuleType.ALLOW);
        return !hasAllowRules || exerciseRules.stream().anyMatch(rule -> rule.ruleType() == ExerciseRuleType.ALLOW
                && matches(rule, exerciseId, exerciseType));
    }

    private boolean matches(ExerciseRule rule, long exerciseId, String exerciseType) {
        return rule.exerciseId() != null && rule.exerciseId() == exerciseId
                || rule.exerciseTypeNote() != null && exerciseType != null
                && rule.exerciseTypeNote().trim().equalsIgnoreCase(exerciseType.trim());
    }
}