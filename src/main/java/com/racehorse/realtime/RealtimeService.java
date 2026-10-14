package com.racehorse.realtime;

import com.racehorse.audit.AuditService;
import com.racehorse.auth.AuthenticatedUser;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.Db;
import com.racehorse.restriction.MonitoringThresholds;
import com.racehorse.training.SessionReadModel;
import com.racehorse.training.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * DEMO IMPLEMENTATION of the realtime pipeline (DEC-002 D-03, BR-D08…D11, BR-D23; CORE-030 owned by Tiến):
 * manual input or simulator → same ingestion → threshold evaluation → STOMP.
 * One CRITICAL monitoring_alert per threshold crossing (P-09), never one per sample. WARNING is published, not stored.
 * Unacknowledged CRITICAL alerts escalate to Veterinarians after the configured delay (P-08).
 */
@Service
public class RealtimeService {
    public record MetricInput(Instant recordedAt, Integer heartRate, BigDecimal speedKmh, BigDecimal distanceM) {}
    public record AckRequest(String action, String intensity, String stopReason, Boolean medicallyRelevant,
                             String incidentType, String observedSeverity) {}

    private final JdbcTemplate jdbc;
    private final MonitoringThresholds thresholds;
    private final SessionEvents events;
    private final SessionReadModel read;
    private final SessionService sessions;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Duration escalationDelay;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Long, ScheduledFuture<?>> simulators = new ConcurrentHashMap<>();

    public RealtimeService(JdbcTemplate jdbc, MonitoringThresholds thresholds, SessionEvents events, SessionReadModel read,
                           SessionService sessions, AuditService audit, TransactionTemplate tx,
                           @Value("${racehorse.monitoring.escalation-delay:PT120S}") Duration escalationDelay) {
        this.jdbc = jdbc;
        this.thresholds = thresholds;
        this.events = events;
        this.read = read;
        this.sessions = sessions;
        this.audit = audit;
        this.tx = tx;
        this.escalationDelay = escalationDelay;
    }

    @Transactional
    public Map<String, Object> submit(long sessionId, MetricInput in) {
        AuthenticatedUser u = Db.require(RoleCode.WORK_RIDER);
        var s = read.summaryOf(sessionId);
        if (s.workRider().id() != u.id()) throw new BusinessException(ErrorCode.FORBIDDEN, "Not your session.");
        return ingest(sessionId, in, "MANUAL");
    }

    /** Shared by manual input and the simulator (BR-D09: one pipeline). */
    Map<String, Object> ingest(long sessionId, MetricInput in, String source) {
        if (in == null || (in.heartRate() == null && in.speedKmh() == null && in.distanceM() == null))
            throw Db.invalid("At least one of heartRate, speedKmh, distanceM is required.");
        var s = read.summaryOf(sessionId);
        if (!"IN_PROGRESS".equals(s.status()) && !"PAUSED".equals(s.status()))
            throw Db.conflict("Metrics are accepted only for an IN_PROGRESS or PAUSED session.");
        Instant at = in.recordedAt() == null ? Instant.now() : in.recordedAt();
        Integer previousHr = jdbc.queryForList("SELECT TOP 1 heart_rate FROM dbo.training_metric_sample WHERE training_session_id=? "
                + "AND heart_rate IS NOT NULL ORDER BY recorded_at DESC, id DESC", Integer.class, sessionId).stream().findFirst().orElse(null);
        long sampleId = Db.insert(jdbc, "INSERT INTO dbo.training_metric_sample (training_session_id, recorded_at, heart_rate, speed, "
                + "distance, source, created_at) VALUES (?,?,?,?,?,?,?)", sessionId, Db.utc(at), in.heartRate(), in.speedKmh(),
                in.distanceM(), source, Db.nowUtc());
        var t = thresholds.effective(s.horse().id());
        String state = "NORMAL";
        Long alertId = null;
        boolean simulated = "SIMULATOR".equals(source);
        if (in.heartRate() != null) {
            BigDecimal hr = BigDecimal.valueOf(in.heartRate());
            if (hr.compareTo(t.critical()) >= 0) {
                state = "CRITICAL";
                boolean crossing = previousHr == null || BigDecimal.valueOf(previousHr).compareTo(t.critical()) < 0;
                if (crossing) alertId = raiseCritical(s, hr, t.critical(), at, simulated);
            } else if (hr.compareTo(t.warning()) >= 0) {
                state = "WARNING";
                boolean entering = previousHr == null || BigDecimal.valueOf(previousHr).compareTo(t.warning()) < 0
                        || BigDecimal.valueOf(previousHr).compareTo(t.critical()) >= 0;
                if (entering) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("type", "WARNING");
                    w.put("sessionId", sessionId);
                    w.put("alertId", null);
                    w.put("severity", "WARNING");
                    w.put("metric", "HEART_RATE");
                    w.put("metricValue", hr);
                    w.put("thresholdApplied", t.warning());
                    w.put("raisedAt", at.toString());
                    w.put("suggestedActions", List.of("REDUCE_INTENSITY"));
                    w.put("simulated", simulated);
                    events.alert(sessionId, w);
                }
            }
        }
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("sessionId", sessionId);
        frame.put("sampleId", sampleId);
        frame.put("recordedAt", at.toString());
        frame.put("heartRate", in.heartRate());
        frame.put("speedKmh", in.speedKmh());
        frame.put("distanceM", in.distanceM());
        frame.put("source", source);
        frame.put("simulated", simulated);
        frame.put("realtimeState", state);
        frame.put("thresholds", Map.of("metric", "HEART_RATE", "warning", t.warning(), "critical", t.critical(), "overridden", t.overridden()));
        events.metrics(sessionId, frame);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampleId", sampleId);
        out.put("realtimeState", state);
        out.put("alertId", alertId);
        return out;
    }

    private long raiseCritical(SessionReadModel.SessionSummary s, BigDecimal hr, BigDecimal threshold, Instant at, boolean simulated) {
        long id = Db.insert(jdbc, "INSERT INTO dbo.monitoring_alert (training_session_id, metric, metric_value, threshold_applied, "
                + "severity, raised_at, created_at) VALUES (?,?,?,?,?,?,?)", s.id(), "HEART_RATE", hr, threshold, "CRITICAL",
                Db.utc(at), Db.nowUtc());
        List<Long> recipients = new java.util.ArrayList<>(jdbc.queryForList("SELECT ur.user_id FROM dbo.user_role ur JOIN dbo.role r "
                + "ON r.id=ur.role_id WHERE r.role_code='VETERINARIAN'", Long.class));
        if (s.trainer() != null) recipients.add(s.trainer().id());
        for (Long r : recipients)
            jdbc.update("INSERT INTO dbo.notification (recipient_user_id, category, notification_type, title, message, target_type, "
                            + "target_id, is_simulated, created_at) VALUES (?,?,?,?,?,?,?,?,?)", r, "ALERTS", "MONITORING_ALERT_CRITICAL",
                    "CRITICAL heart rate — " + s.horse().name(), "Heart rate " + hr + " bpm ≥ " + threshold + " bpm in " + s.sessionCode(),
                    "monitoring_alert", id, simulated, Db.nowUtc());
        events.alert(s.id(), alertFrame("CRITICAL_RAISED", id, simulated));
        return id;
    }

    @Transactional
    public Map<String, Object> acknowledge(long alertId, AckRequest req) {
        AuthenticatedUser u = Db.require(RoleCode.TRAINER, RoleCode.VETERINARIAN);
        var rows = jdbc.queryForList("SELECT * FROM dbo.monitoring_alert WITH (UPDLOCK) WHERE id=?", alertId);
        if (rows.isEmpty()) throw Db.notFound("Alert");
        var a = rows.getFirst();
        if (a.get("acknowledged_at") != null) throw Db.conflict("The alert is already acknowledged.");
        String action = req == null || req.action() == null ? "ACKNOWLEDGE" : req.action();
        if (!List.of("ACKNOWLEDGE", "REDUCE_INTENSITY", "PAUSE", "STOP").contains(action)) throw Db.invalid("Unknown action.");
        if (u.hasRole(RoleCode.VETERINARIAN) && !u.hasRole(RoleCode.TRAINER) && !"ACKNOWLEDGE".equals(action))
            throw new BusinessException(ErrorCode.FORBIDDEN, "A Veterinarian may only acknowledge.");
        long sessionId = Db.lng(a, "training_session_id");
        switch (action) {
            case "REDUCE_INTENSITY" -> sessions.reduceIntensity(sessionId, new SessionService.IntensityRequest(req.intensity(), null));
            case "PAUSE" -> sessions.pause(sessionId);
            case "STOP" -> sessions.stop(sessionId, new SessionService.StopRequest(req.stopReason(), req.medicallyRelevant(),
                    req.incidentType(), req.observedSeverity()));
            default -> { }
        }
        jdbc.update("UPDATE dbo.monitoring_alert SET acknowledged_by_user_id=?, acknowledged_at=?, action_taken=?, updated_at=? WHERE id=?",
                u.id(), Db.nowUtc(), action, Db.nowUtc(), alertId);
        audit.record("MONITORING_ALERT_ACKNOWLEDGED", "monitoring_alert", alertId, action);
        events.alert(sessionId, alertFrame("CRITICAL_ACKNOWLEDGED", alertId, false));
        return alertFrame("CRITICAL_ACKNOWLEDGED", alertId, false);
    }

    /** P-08: escalation sweep (every 15 s). */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void escalate() {
        try {
            tx.executeWithoutResult(st -> {
                var due = jdbc.queryForList("SELECT id, training_session_id FROM dbo.monitoring_alert WHERE acknowledged_at IS NULL "
                        + "AND escalated_to_vet_at IS NULL AND raised_at<=?", Db.utc(Instant.now().minus(escalationDelay)));
                for (var a : due) {
                    long id = Db.lng(a, "id");
                    jdbc.update("UPDATE dbo.monitoring_alert SET escalated_to_vet_at=?, updated_at=? WHERE id=?", Db.nowUtc(), Db.nowUtc(), id);
                    for (Long vet : jdbc.queryForList("SELECT ur.user_id FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id "
                            + "WHERE r.role_code='VETERINARIAN'", Long.class))
                        jdbc.update("INSERT INTO dbo.notification (recipient_user_id, category, notification_type, title, message, "
                                        + "target_type, target_id, is_simulated, created_at) VALUES (?,?,?,?,?,?,?,0,?)", vet, "ALERTS",
                                "MONITORING_ALERT_ESCALATED", "Unacknowledged CRITICAL alert escalated",
                                "A CRITICAL heart-rate alert was not acknowledged in time.", "monitoring_alert", id, Db.nowUtc());
                    events.alert(Db.lng(a, "training_session_id"), alertFrame("CRITICAL_ESCALATED", id, false));
                }
            });
        } catch (RuntimeException ignored) {
            // The sweep retries on the next tick; a database outage must not kill the scheduler thread.
        }
    }

    // ---------------- simulator (demo only, same pipeline) ----------------

    public void startSimulator(long sessionId, String profile) {
        AuthenticatedUser u = Db.require(RoleCode.TRAINER);
        var s = read.summaryOf(sessionId);
        if (s.trainer() == null || s.trainer().id() != u.id()) throw new BusinessException(ErrorCode.FORBIDDEN, "Not your session.");
        if (!"IN_PROGRESS".equals(s.status())) throw Db.conflict("The simulator runs only for an IN_PROGRESS session.");
        stopSimulator(sessionId, false);
        var t = thresholds.effective(s.horse().id());
        final int[] tick = {0};
        final double[] distance = {jdbc.queryForObject("SELECT COALESCE(MAX(distance),0) FROM dbo.training_metric_sample "
                + "WHERE training_session_id=?", BigDecimal.class, sessionId).doubleValue()};
        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(() -> {
            try {
                int i = tick[0]++;
                int hr = simulatedHr(i, t.warning().intValue(), t.critical().intValue(), profile);
                double speed = 30 + (i % 10);
                distance[0] += speed / 3.6 * 2;
                tx.executeWithoutResult(st -> ingest(sessionId, new MetricInput(Instant.now(), hr,
                        BigDecimal.valueOf(speed), BigDecimal.valueOf(Math.round(distance[0]))), "SIMULATOR"));
            } catch (RuntimeException e) {
                stopSimulator(sessionId, false);
            }
        }, 0, 2, TimeUnit.SECONDS);
        simulators.put(sessionId, f);
    }

    public void stopSimulator(long sessionId, boolean requireRole) {
        if (requireRole) Db.require(RoleCode.TRAINER);
        var f = simulators.remove(sessionId);
        if (f != null) f.cancel(false);
    }

    /** Scripted ramp: NORMAL → WARNING → CRITICAL (crossing once) → recovery; profile "normal" never exceeds warning. */
    static int simulatedHr(int i, int warning, int critical, String profile) {
        if ("normal".equalsIgnoreCase(profile)) return warning - 25 + (i % 6);
        if (i < 5) return warning - 30 + i * 3;
        if (i < 10) return warning + (i - 5);
        if (i < 18) return critical + 2 + (i % 4);
        return Math.max(warning - 20, critical + 2 - (i - 18) * 4);
    }

    private Map<String, Object> alertFrame(String type, long alertId, boolean simulated) {
        var a = jdbc.queryForMap("SELECT a.*, u.full_name AS ack_name FROM dbo.monitoring_alert a LEFT JOIN dbo.app_user u "
                + "ON u.id=a.acknowledged_by_user_id WHERE a.id=?", alertId);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        f.put("sessionId", a.get("training_session_id"));
        f.put("alertId", alertId);
        f.put("severity", "CRITICAL");
        f.put("metric", "HEART_RATE");
        f.put("metricValue", a.get("metric_value"));
        f.put("thresholdApplied", a.get("threshold_applied"));
        f.put("raisedAt", String.valueOf(Db.instant(a.get("raised_at"))));
        f.put("suggestedActions", List.of("ACKNOWLEDGE", "REDUCE_INTENSITY", "PAUSE", "STOP"));
        f.put("acknowledgedAt", a.get("acknowledged_at") == null ? null : String.valueOf(Db.instant(a.get("acknowledged_at"))));
        f.put("acknowledgedBy", a.get("ack_name"));
        f.put("actionTaken", a.get("action_taken"));
        f.put("escalatedToVetAt", a.get("escalated_to_vet_at") == null ? null : String.valueOf(Db.instant(a.get("escalated_to_vet_at"))));
        f.put("simulated", simulated);
        return f;
    }
}
