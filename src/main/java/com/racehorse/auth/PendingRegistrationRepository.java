package com.racehorse.auth;

import java.util.Optional;

/**
 * Authentication boundary for pending registrations.
 *
 * No production adapter is provided until the physical registration_request schema is approved.
 */
public interface PendingRegistrationRepository {

    /**
     * Returns only PENDING_APPROVAL credentials. Rejected and approved requests are absent.
     */
    Optional<PendingCredentials> findPendingByNormalizedEmail(String normalizedEmail);

    record PendingCredentials(String passwordHash) {
    }
}
