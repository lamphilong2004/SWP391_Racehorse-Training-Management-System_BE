package com.racehorse.registration;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** DEC-008 per-instance limits. Use a shared store before running multiple instances. */
@Component
public class RegistrationRateLimiter {
    private final Map<String, Deque<Instant>> ips = new HashMap<>();
    private final Map<String, Deque<Instant>> emails = new HashMap<>();
    public synchronized void consume(String ip, String email, Instant now) {
        prune(ips, now.minus(Duration.ofMinutes(15)));
        prune(emails, now.minus(Duration.ofDays(1)));
        if ((!ips.containsKey(ip) && ips.size() >= 100_000)
                || (!emails.containsKey(email) && emails.size() >= 100_000)
                || ips.getOrDefault(ip, new ArrayDeque<>()).size() >= 5
                || emails.getOrDefault(email, new ArrayDeque<>()).size() >= 3) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
        }
        ips.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(now);
        emails.computeIfAbsent(email, k -> new ArrayDeque<>()).addLast(now);
    }
    private void prune(Map<String, Deque<Instant>> windows, Instant cutoff) {
        windows.values().forEach(q -> { while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) q.removeFirst(); });
        windows.values().removeIf(Deque::isEmpty);
    }
}
