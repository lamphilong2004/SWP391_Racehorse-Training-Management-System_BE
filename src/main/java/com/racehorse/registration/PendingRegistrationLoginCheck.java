package com.racehorse.registration;

import com.racehorse.auth.RegistrationLoginCheck;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORE-047 implementation of the CORE-007 login hook (DEC-008 §6 R5 step 2). Exactly one BCrypt verification when a
 * PENDING_APPROVAL request exists, none otherwise (the login service adds the dummy check). A REJECTED request has no
 * hash (R4), so it is "no pending request" and the login gets the generic 401. Never issues a token.
 */
@Component
public class PendingRegistrationLoginCheck implements RegistrationLoginCheck {

    private final RegistrationRequestRepository requests;
    private final PasswordEncoder passwordEncoder;

    public PendingRegistrationLoginCheck(RegistrationRequestRepository requests, PasswordEncoder passwordEncoder) {
        this.requests = requests;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Outcome check(String normalizedEmail, String rawPassword) {
        return requests.findFirstByEmailNormalizedAndStatus(normalizedEmail, RegistrationStatus.PENDING_APPROVAL)
                .map(r -> passwordEncoder.matches(rawPassword, r.getPasswordHash())
                        ? Outcome.PENDING_PASSWORD_MATCHES : Outcome.PENDING_PASSWORD_MISMATCH)
                .orElse(Outcome.NO_PENDING_REQUEST);
    }
}
