package com.racehorse.training.realtime;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.racehorse.training.realtime.Core030Api.MetricRequest;

/** Development/demo-only deterministic sample source; each sample uses the normal CORE-030 pipeline. */
@Service
public class Core030Simulator {
    private static final Logger logger = LoggerFactory.getLogger(Core030Simulator.class);
    private final Core030Service service;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, task -> {
        Thread thread = new Thread(task, "core030-demo-simulator");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public Core030Simulator(Core030Service service) { this.service = service; }

    public synchronized void start(long sessionId) {
        service.authorizeSimulator(sessionId, true);
        if (jobs.containsKey(sessionId)) return;
        AtomicInteger tick = new AtomicInteger();
        ScheduledFuture<?> job = scheduler.scheduleAtFixedRate(() -> runTick(sessionId, tick), 0, 2, TimeUnit.SECONDS);
        jobs.put(sessionId, job);
    }

    public synchronized void stop(long sessionId) {
        service.authorizeSimulator(sessionId, false);
        cancel(sessionId);
    }

    private void runTick(long sessionId, AtomicInteger tick) {
        int value = tick.getAndIncrement();
        try {
            service.submitSimulated(sessionId, new MetricRequest(Instant.now(), 95 + value % 20,
                    BigDecimal.valueOf(20 + value % 4), null));
        } catch (RuntimeException failure) {
            synchronized (this) { cancel(sessionId); }
            logger.error("CORE-030 simulator stopped after a sample failed for session {}.", sessionId, failure);
        }
    }

    private void cancel(long sessionId) {
        ScheduledFuture<?> job = jobs.remove(sessionId);
        if (job != null) job.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        jobs.values().forEach(job -> job.cancel(false));
        jobs.clear();
        scheduler.shutdownNow();
    }
}
