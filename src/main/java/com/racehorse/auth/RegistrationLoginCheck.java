package com.racehorse.auth;

/**
 * Extension point for DEC-008 §6 R5 step 2, implemented by CORE-047 (registration). Called only when no app_user
 * exists for the email. Without an implementation bean every such login gets the generic 401.
 *
 * <p>Timing contract (R5 equal timing): an implementation performs exactly one BCrypt verification when a
 * PENDING_APPROVAL request exists for the email, and none otherwise. The login service then runs the dummy
 * verification only for {@link Outcome#NO_PENDING_REQUEST}. REJECTED requests have no hash (R4) and count as
 * {@link Outcome#NO_PENDING_REQUEST}. Implementations must never log the password or the email unmasked.
 */
public interface RegistrationLoginCheck {

    enum Outcome {
        /** No PENDING_APPROVAL request for the email: generic 401. No BCrypt check was done. */
        NO_PENDING_REQUEST,
        /** PENDING_APPROVAL request and the password matches its hash: 403 "waiting for approval", no token. */
        PENDING_PASSWORD_MATCHES,
        /** PENDING_APPROVAL request but the password does not match: generic 401. */
        PENDING_PASSWORD_MISMATCH
    }

    Outcome check(String normalizedEmail, String rawPassword);
}
