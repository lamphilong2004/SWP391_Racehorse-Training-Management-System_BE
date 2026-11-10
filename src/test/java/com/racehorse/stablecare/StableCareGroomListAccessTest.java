package com.racehorse.stablecare;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;

/** BR-32: the Veterinarian assigns treatment tasks to a Groom, so it must read the active Groom list (names only). */
class StableCareGroomListAccessTest {
    @Test
    void veterinarianMayReadActiveGroomsButGroomAndRiderMayNot() throws Exception {
        String rule = StableCareController.class.getMethod("grooms").getAnnotation(PreAuthorize.class).value();
        assertTrue(rule.contains("'VETERINARIAN'"), rule);
        assertTrue(rule.contains("'CLUB_MANAGER'") && rule.contains("'HEAD_TRAINER'"), rule);
        assertFalse(rule.contains("'GROOM'") || rule.contains("'WORK_RIDER'") || rule.contains("'HORSE_OWNER'"), rule);
    }
}
