package com.racehorse.medical.restriction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictionExercisePolicyTest {

    private final RestrictionExercisePolicy policy = new RestrictionExercisePolicy();

    @Test
    void lockedDeniesEveryExercise() {
        assertFalse(policy.allows(RestrictionLevel.LOCKED, 5, "TROT", List.of()));
        assertFalse(policy.allows(RestrictionLevel.LOCKED, 5, "TROT",
                List.of(new ExerciseRule(5L, ExerciseRuleType.ALLOW, null))));
    }

    @Test
    void prohibitedRuleTakesPrecedenceOverAllowedRule() {
        List<ExerciseRule> rules = List.of(
                new ExerciseRule(5L, ExerciseRuleType.ALLOW, null),
                new ExerciseRule(5L, ExerciseRuleType.PROHIBIT, null));

        assertFalse(policy.allows(RestrictionLevel.LIMITED, 5, "TROT", rules));
    }

    @Test
    void allowRulesRequireMatchingExercise() {
        List<ExerciseRule> rules = List.of(new ExerciseRule(5L, ExerciseRuleType.ALLOW, null));

        assertTrue(policy.allows(RestrictionLevel.LIMITED, 5, "TROT", rules));
        assertFalse(policy.allows(RestrictionLevel.LIMITED, 6, "CANTER", rules));
    }

    @Test
    void noAllowRulesPermitUnlessProhibitedMatches() {
        List<ExerciseRule> rules = List.of(new ExerciseRule(null, ExerciseRuleType.PROHIBIT, "TROT"));

        assertFalse(policy.allows(RestrictionLevel.LIMITED, 5, "trot", rules));
        assertTrue(policy.allows(RestrictionLevel.LIMITED, 5, "CANTER", rules));
    }

    @Test
    void apiRuleTypesMapExplicitlyToAndFromDatabaseValues() {
        assertEquals("ALLOWED", ExerciseRuleType.ALLOW.databaseValue());
        assertEquals("PROHIBITED", ExerciseRuleType.PROHIBIT.databaseValue());
        assertEquals(ExerciseRuleType.ALLOW, ExerciseRuleType.fromDatabaseValue("ALLOWED"));
        assertEquals(ExerciseRuleType.PROHIBIT, ExerciseRuleType.fromDatabaseValue("PROHIBITED"));
        assertThrows(IllegalArgumentException.class, () -> ExerciseRuleType.fromDatabaseValue("ALLOW"));
    }

    @Test
    void publicJsonRuleTypesUseOnlyCanonicalValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        assertEquals("\"ALLOWED\"", objectMapper.writeValueAsString(ExerciseRuleType.ALLOW));
        assertEquals("\"PROHIBITED\"", objectMapper.writeValueAsString(ExerciseRuleType.PROHIBIT));
        assertEquals(ExerciseRuleType.ALLOW, objectMapper.readValue("\"ALLOWED\"", ExerciseRuleType.class));
        assertEquals(ExerciseRuleType.PROHIBIT, objectMapper.readValue("\"PROHIBITED\"", ExerciseRuleType.class));
        assertThrows(JsonMappingException.class,
                () -> objectMapper.readValue("\"ALLOW\"", ExerciseRuleType.class));
    }
}
