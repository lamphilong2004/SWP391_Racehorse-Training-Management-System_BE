package com.racehorse.registration;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEC-008 §6 R8 registration limits: 5 submissions per 15 min per client IP and 3 per 24 h per normalised email.
 * In memory, per instance (allowed for this increment; a shared store is needed before production). The answer is
 * the same whether or not the email belongs to an account.
 */
@Component
public class RegistrationRateLimiter {

    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();
    private final RegistrationProperties properties;
    private final Clock clock;

    public RegistrationRateLimiter(RegistrationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Records the attempt and returns true when it is within both limits; false means 429. */
    public boolean tryAcquire(String clientIp, String normalizedEmail) {
        Instant now = clock.instant();
        Deque<Instant> ip = byIp.computeIfAbsent(clientIp == null ? "" : clientIp, k -> new ArrayDeque<>());
        Deque<Instant> email = byEmail.computeIfAbsent(normalizedEmail, k -> new ArrayDeque<>());
        synchronized (this) {
            prune(ip, now, properties.ipWindow());
            prune(email, now, properties.emailWindow());
            if (ip.size() >= properties.ipMaxAttempts() || email.size() >= properties.emailMaxAttempts()) {
                return false;
            }
            ip.addLast(now);
            email.addLast(now);
            return true;
        }
    }

    private static void prune(Deque<Instant> attempts, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }
}
