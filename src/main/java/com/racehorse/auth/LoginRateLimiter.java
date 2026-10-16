package com.racehorse.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LoginRateLimiter {

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Cache<AttemptKey, AttemptWindow> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(WINDOW)
            .build();

    public Permit reserve(String remoteAddress, String email, Instant now) {
        AttemptKey key = new AttemptKey(remoteAddress, normalizeEmail(email));
        AttemptWindow window = windows.get(key, ignored -> new AttemptWindow());
        synchronized (window) {
            prune(window, now);
            if (window.failures.size() + window.inFlight >= MAX_FAILED_ATTEMPTS) {
                return null;
            }
            window.inFlight++;
            return new Permit(window);
        }
    }

    public void recordFailure(Permit permit, Instant now) {
        complete(permit, now, true);
    }

    public void recordSuccess(Permit permit) {
        complete(permit, Instant.now(), false);
    }

    public void release(Permit permit) {
        complete(permit, Instant.now(), false);
    }

    private void complete(Permit permit, Instant now, boolean failed) {
        if (!permit.completed.compareAndSet(false, true)) {
            return;
        }
        synchronized (permit.window) {
            permit.window.inFlight--;
            if (failed) {
                prune(permit.window, now);
                permit.window.failures.addLast(now);
            }
        }
    }

    private void prune(AttemptWindow window, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!window.failures.isEmpty() && window.failures.peekFirst().isBefore(cutoff)) {
            window.failures.removeFirst();
        }
    }

    private String normalizeEmail(String email) {
        return EmailAddressNormalizer.normalize(email);
    }

    static final class Permit {

        private final AttemptWindow window;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Permit(AttemptWindow window) {
            this.window = window;
        }
    }

    private record AttemptKey(String remoteAddress, String email) {
    }

    private static final class AttemptWindow {

        private final Deque<Instant> failures = new ArrayDeque<>();
        private int inFlight;
    }
}
