package com.racehorse.medical;

import java.util.Set;

/**
 * Integration boundary for CORE-007. Implement using the verified security context,
 * never request headers, request parameters or a default user. Return null if anonymous.
 * No implementation is supplied by CORE-014.
 */
public interface MedicalIdentityProvider {
    Identity currentIdentity();

    record Identity(long userId, Set<String> roles) {
        public Identity {
            if (userId <= 0) throw new IllegalArgumentException("Invalid authenticated user id");
            roles = Set.copyOf(roles);
        }
    }
}
