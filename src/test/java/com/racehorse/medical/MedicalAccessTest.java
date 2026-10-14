package com.racehorse.medical;

import com.racehorse.common.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MedicalAccessTest {
    private MedicalAccess access(Set<String> roles) {
        var factory = new DefaultListableBeanFactory();
        if (roles != null) factory.registerSingleton("identity", (MedicalIdentityProvider) () ->
                new MedicalIdentityProvider.Identity(42, roles));
        return new MedicalAccess(factory.getBeanProvider(MedicalIdentityProvider.class));
    }

    @Test void missingSecurityIntegrationFailsClosed() {
        var access = access(null);
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> access.requireIdentity(true)).code());
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> access.requireIdentity(false)).code());
    }

    @Test void onlyVeterinarianMayWrite() {
        for (String role : Set.of("CLUB_MANAGER", "HEAD_TRAINER", "TRAINER", "WORK_RIDER", "GROOM", "HORSE_OWNER"))
            assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                    () -> access(Set.of(role)).requireIdentity(true)).code());
        assertEquals(42, access(Set.of("VETERINARIAN")).requireIdentity(true).userId());
    }

    @Test void readPermissionsMatchMatrix() {
        for (String role : Set.of("CLUB_MANAGER", "HEAD_TRAINER", "TRAINER", "VETERINARIAN", "GROOM"))
            assertNotNull(access(Set.of(role)).requireIdentity(false));
        for (String role : Set.of("WORK_RIDER", "HORSE_OWNER"))
            assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                    () -> access(Set.of(role)).requireIdentity(false)).code());
    }

    @Test void trainerOnlyRequiresAssignmentWhileAdditionalRoleMayGrantClubAccess() {
        var access = access(Set.of("TRAINER"));
        assertTrue(access.needsTrainerScope(new MedicalIdentityProvider.Identity(42, Set.of("TRAINER"))));
        assertFalse(access.needsTrainerScope(new MedicalIdentityProvider.Identity(42, Set.of("TRAINER", "VETERINARIAN"))));
    }
}
