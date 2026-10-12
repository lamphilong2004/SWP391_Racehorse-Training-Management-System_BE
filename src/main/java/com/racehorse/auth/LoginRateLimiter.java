package com.racehorse.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEC-008 §6 R8 — failed logins per client IP + normalised email in a sliding window (default 10 per 15 minutes).
 * In memory and per instance, as R8 allows for this increment; a shared store is needed before production.
 * The decision never depends on whether the account exists.
 */
@Component
public class LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginRateLimiter(SecurityProperties properties, Clock clock) {
        this.maxFailures = properties.loginRateLimit().maxFailures();
        this.window = properties.loginRateLimit().window();
        this.clock = clock;
    }

    public boolean isBlocked(String clientIp, String normalizedEmail) {
        Deque<Instant> attempts = failures.get(key(clientIp, normalizedEmail));
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            prune(attempts, clock.instant());
            return attempts.size() >= maxFailures;
        }
    }

    public void recordFailure(String clientIp, String normalizedEmail) {
        Instant now = clock.instant();
        Deque<Instant> attempts = failures.computeIfAbsent(key(clientIp, normalizedEmail), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now);
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    public void reset(String clientIp, String normalizedEmail) {
        failures.remove(key(clientIp, normalizedEmail));
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private static String key(String clientIp, String normalizedEmail) {
        return (clientIp == null ? "" : clientIp) + '|' + normalizedEmail;
    }
}
