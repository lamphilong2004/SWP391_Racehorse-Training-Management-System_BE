package com.racehorse.training.evaluation;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static com.racehorse.training.evaluation.EvaluationApi.*;

@Service
public class EvaluationService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final JsonMapper json;
    private final boolean sqlServer;

    public EvaluationService(JdbcTemplate jdbc, CurrentActorProvider actors, JsonMapper json, DataSource dataSource) {
        this.jdbc = jdbc; this.actors = actors; this.json = json;
        boolean server = false;
        try (var connection = dataSource.getConnection()) {
            server = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("microsoft");
        } catch (Exception ignored) { }
        this.sqlServer = server;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String, Object> evaluate(long sessionId, EvaluationRequest request) {
        ActorContext actor = require(RoleCode.TRAINER);
        positive(sessionId);
        validate(request);
        Session session = session(sessionId);
        if (!Objects.equals(session.trainerId(), actor.userId())) throw forbidden("Session is outside the Trainer's assignment scope.");
        if (!Set.of("COMPLETED", "INTERRUPTED").contains(session.status()))
            throw conflict("Trainer evaluation is available only after a session is COMPLETED or INTERRUPTED.");
        long resultId;
        try {
            resultId = insertId("INSERT INTO dbo.session_result(training_session_id,evaluated_by_user_id,evaluated_summary," +
                    "evaluation_label,evaluation_notes,recommendation,evaluated_at) VALUES (?,?,?,?,?,?,?)", ps -> {
                ps.setLong(1, sessionId); ps.setLong(2, actor.userId()); ps.setString(3, request.evaluatedSummary());
                ps.setString(4, request.result()); ps.setString(5, request.evaluationNotes()); ps.setString(6, request.recommendation());
                ps.setTimestamp(7, Timestamp.from(Instant.now()));
            });
        } catch (DataIntegrityViolationException ex) {
            throw conflict("A Trainer evaluation already exists for this session.");
        }
        return evaluation(resultId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> result(long sessionId) {
        ActorContext actor = actor();
        positive(sessionId);
        Session session = session(sessionId);
        authorizeRead(actor, session);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("session", sessionDetail(session));
        view.put("actual", actual(session));
        view.put("samples", samples(sessionId));
        view.put("alerts", alerts(sessionId));
        view.put("riderFeedback", riderFeedback(sessionId));
        view.put("evaluation", latestEvaluation(sessionId));
        return view;
    }

    @Transactional(readOnly = true)
    public List<PerformancePoint> performance(long horseId, LocalDate from, LocalDate to) {
        ActorContext actor = actor(); positive(horseId);
        if (from != null && to != null && from.isAfter(to)) throw validation("from must be on or before to.");
        if (!horseExists(horseId)) throw notFound("Horse was not found.");
        if (actor.roles().contains(RoleCode.TRAINER) && !trainerAssignedToHorse(actor.userId(), horseId))
            throw forbidden("Horse is outside the Trainer's active assignment scope.");
        if (!(actor.roles().contains(RoleCode.HEAD_TRAINER) || actor.roles().contains(RoleCode.TRAINER)
                || actor.roles().contains(RoleCode.CLUB_MANAGER) || actor.roles().contains(RoleCode.VETERINARIAN)))
            throw forbidden("Horse performance access denied.");
        StringBuilder where = new StringBuilder(" WHERE p.horse_id=?");
        List<Object> args = new ArrayList<>(); args.add(horseId);
        if (from != null) { where.append(" AND CAST(s.scheduled_start AS DATE)>=?"); args.add(java.sql.Date.valueOf(from)); }
        if (to != null) { where.append(" AND CAST(s.scheduled_start AS DATE)<=?"); args.add(java.sql.Date.valueOf(to)); }
        List<PerfSession> sessions = jdbc.query("""
                SELECT s.id,s.session_code,s.status,s.actual_start,s.actual_end,s.scheduled_start
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id
                """ + where + " ORDER BY s.scheduled_start ASC,s.id ASC", (rs, n) -> new PerfSession(rs.getLong("id"),
                rs.getString("session_code"), rs.getString("status"), instant(rs.getTimestamp("actual_start")),
                instant(rs.getTimestamp("actual_end")), instant(rs.getTimestamp("scheduled_start"))), args.toArray());
        return sessions.stream().map(s -> {
            Metrics metrics = metrics(s.id());
            return new PerformancePoint(s.id(), s.code(), s.scheduledStart().atZone(ZoneOffset.UTC).toLocalDate(), s.status(),
                    null, duration(s.actualStart(), s.actualEnd()), metrics.maxHeartRate(), metrics.averageSpeed(), evaluationLabel(s.id()));
        }).toList();
    }

    private Session session(long id) {
        List<Session> rows = jdbc.query("""
                SELECT s.id,s.session_code,s.status,s.actual_start,s.actual_end,s.scheduled_start,s.scheduled_end,
                       s.surface,s.focus,s.intensity,s.interruption_reason,s.horse_program_phase_id,s.created_by_user_id,
                       s.work_rider_user_id,p.id program_id,p.horse_id,p.status program_status,ph.name phase_name,
                       h.horse_code,h.name horse_name,r.user_code rider_code,r.full_name rider_name,
                       (SELECT TOP 1 ta.trainer_user_id FROM dbo.trainer_assignment ta
                        WHERE ta.horse_training_program_id=p.id AND ta.effective_from<=CAST(s.scheduled_start AS DATE)
                          AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(s.scheduled_start AS DATE))
                        ORDER BY ta.effective_from DESC,ta.id DESC) trainer_id,
                       (SELECT TOP 1 tu.user_code FROM dbo.trainer_assignment ta JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id
                        WHERE ta.horse_training_program_id=p.id AND ta.effective_from<=CAST(s.scheduled_start AS DATE)
                          AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(s.scheduled_start AS DATE))
                        ORDER BY ta.effective_from DESC,ta.id DESC) trainer_code,
                       (SELECT TOP 1 tu.full_name FROM dbo.trainer_assignment ta JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id
                        WHERE ta.horse_training_program_id=p.id AND ta.effective_from<=CAST(s.scheduled_start AS DATE)
                          AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(s.scheduled_start AS DATE))
                        ORDER BY ta.effective_from DESC,ta.id DESC) trainer_name,
                       (SELECT TOP 1 pc.overall_result FROM dbo.pre_session_check pc WHERE pc.training_session_id=s.id) precheck,
                       (SELECT TOP 1 pc.observations FROM dbo.pre_session_check pc WHERE pc.training_session_id=s.id) observations
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id
                JOIN dbo.horse h ON h.id=p.horse_id
                JOIN dbo.app_user r ON r.id=s.work_rider_user_id
                WHERE s.id=?
                """, (rs, n) -> {
            Long trainerId = nullableLong(rs, "trainer_id");
            return new Session(rs.getLong("id"), rs.getString("session_code"), rs.getString("status"),
                    instant(rs.getTimestamp("actual_start")), instant(rs.getTimestamp("actual_end")),
                    instant(rs.getTimestamp("scheduled_start")), instant(rs.getTimestamp("scheduled_end")),
                    rs.getString("surface"), rs.getString("focus"), rs.getString("intensity"), rs.getString("interruption_reason"),
                    rs.getLong("horse_program_phase_id"), rs.getLong("created_by_user_id"), rs.getLong("work_rider_user_id"),
                    rs.getLong("program_id"), rs.getLong("horse_id"), rs.getString("program_status"), rs.getString("phase_name"),
                    rs.getString("horse_code"), rs.getString("horse_name"), rs.getString("rider_code"), rs.getString("rider_name"),
                    trainerId, rs.getString("trainer_code"), rs.getString("trainer_name"), rs.getString("precheck"), rs.getString("observations"));
        }, id);
        if (rows.isEmpty()) throw notFound("Training session was not found.");
        return rows.getFirst();
    }

    private Map<String, Object> sessionDetail(Session s) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", s.id()); data.put("sessionCode", s.code());
        data.put("horse", map("id", s.horseId(), "horseCode", s.horseCode(), "name", s.horseName()));
        data.put("programId", s.programId()); data.put("phaseName", s.phaseName()); data.put("scheduledStart", s.scheduledStart());
        data.put("scheduledEnd", s.scheduledEnd()); data.put("status", s.status());
        data.put("workRider", map("id", s.riderId(), "userCode", s.riderCode(), "fullName", s.riderName()));
        data.put("trainer", s.trainerId() == null ? null : map("id", s.trainerId(), "userCode", s.trainerCode(), "fullName", s.trainerName()));
        data.put("preCheckResult", s.precheck());
        List<String> blocked = new ArrayList<>(); String availability = availability(s.horseId());
        String blockedMessage = null;
        if ("SCHEDULED".equals(s.status())) {
            if ("LOCKED".equals(availability)) { blocked.add("AVAILABILITY_LOCKED"); blockedMessage = restrictionReason(s.horseId()); }
            if (s.precheck() == null) blocked.add("PRE_CHECK_MISSING");
            else if ("ABNORMAL".equals(s.precheck())) blocked.add("PRE_CHECK_ABNORMAL");
            if (!"ACTIVE".equals(s.programStatus())) blocked.add("PROGRAM_NOT_ACTIVE");
        }
        data.put("blocked", !blocked.isEmpty()); data.put("blockedReasons", blocked); data.put("blockedMessage", blockedMessage);
        data.put("horseProgramPhaseId", s.phaseId()); data.put("surface", s.surface()); data.put("focus", s.focus());
        data.put("intensity", s.intensity()); data.put("actualStart", s.actualStart()); data.put("actualEnd", s.actualEnd());
        data.put("interruptionReason", s.interruptionReason()); data.put("trainingAvailability", availability);
        data.put("exercises", exercises(s.id())); data.put("preSessionCheck", precheck(s));
        data.put("linkedIncidentIds", jdbc.queryForList("SELECT id FROM dbo.incident WHERE training_session_id=? ORDER BY id", Long.class, s.id()));
        data.put("hasRiderFeedback", riderFeedback(s.id()) != null); data.put("hasEvaluation", latestEvaluation(s.id()) != null);
        data.put("createdBy", userRef(s.createdById()));
        return data;
    }

    private Map<String, Object> precheck(Session s) {
        if (s.precheck() == null) return null;
        JsonNode checks;
        try { checks = s.observations() == null ? json.createObjectNode() : json.readTree(s.observations()); }
        catch (Exception ignored) { checks = json.createObjectNode(); }
        return map("id", jdbc.queryForObject("SELECT id FROM dbo.pre_session_check WHERE training_session_id=?", Long.class, s.id()),
                "sessionId", s.id(), "checks", checks, "overallResult", "ABNORMAL".equals(s.precheck()) ? "ABNORMAL" : "ALL_NORMAL",
                "performedBy", userRef(jdbc.queryForObject("SELECT performed_by_user_id FROM dbo.pre_session_check WHERE training_session_id=?", Long.class, s.id())),
                "performedAt", instant(jdbc.queryForObject("SELECT performed_at FROM dbo.pre_session_check WHERE training_session_id=?", Timestamp.class, s.id())));
    }

    private List<Map<String, Object>> exercises(long sessionId) {
        return jdbc.query("""
                SELECT se.id,se.exercise_order,se.distance,se.duration,se.repetitions,se.rest_interval,se.intensity,se.surface,
                       se.suitability_category,se.acknowledgement_note,ed.id exercise_definition_id,ed.name exercise_name,ed.exercise_type
                FROM dbo.session_exercise se JOIN dbo.exercise_definition ed ON ed.id=se.exercise_definition_id
                WHERE se.training_session_id=? ORDER BY se.exercise_order,se.id
                """, (rs,n) -> map("id",rs.getLong("id"),"exerciseDefinitionId",rs.getLong("exercise_definition_id"),
                "exerciseOrder",rs.getInt("exercise_order"),"exerciseName",rs.getString("exercise_name"),"exerciseType",rs.getString("exercise_type"),
                "distanceM",rs.getBigDecimal("distance"),"durationMin",rs.getBigDecimal("duration"),"repetitions",nullableInt(rs,"repetitions"),
                "restIntervalMin",rs.getBigDecimal("rest_interval"),"intensity",rs.getString("intensity"),"surface",rs.getString("surface"),
                "suitabilityCategory",rs.getString("suitability_category"),"acknowledgementNote",rs.getString("acknowledgement_note")), sessionId);
    }

    private Map<String, Object> actual(Session s) {
        Metrics m = metrics(s.id());
        return map("durationMin", duration(s), "totalDistanceM", null, "maxHeartRate", m.maxHeartRate(),
                "avgHeartRate", m.averageHeartRate(), "maxSpeedKmh", m.maxSpeed(), "avgSpeedKmh", m.averageSpeed(),
                "sampleCount", m.sampleCount(), "simulated", m.simulated());
    }
    private Metrics metrics(long sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) sample_count,MAX(heart_rate) max_hr,AVG(heart_rate) avg_hr,MAX(speed) max_speed,AVG(speed) avg_speed, " +
                        "SUM(CASE WHEN source='SIMULATOR' THEN 1 ELSE 0 END) simulated_count FROM dbo.training_metric_sample WHERE training_session_id=?",
                (rs,n) -> new Metrics(rs.getLong("sample_count"),nullableInt(rs,"max_hr"),rs.getBigDecimal("avg_hr"),
                        rs.getBigDecimal("max_speed"),rs.getBigDecimal("avg_speed"),rs.getInt("simulated_count")>0), sessionId);
    }
    private List<Map<String, Object>> samples(long sessionId) {
        return jdbc.query("SELECT recorded_at,heart_rate,speed,distance,source FROM dbo.training_metric_sample WHERE training_session_id=? ORDER BY recorded_at,id",
                (rs,n) -> map("recordedAt",instant(rs.getTimestamp("recorded_at")),"heartRate",nullableInt(rs,"heart_rate"),
                        "speedKmh",rs.getBigDecimal("speed"),"distanceM",rs.getBigDecimal("distance"),"source",rs.getString("source")), sessionId);
    }
    private List<Map<String, Object>> alerts(long sessionId) {
        return jdbc.query("""
                SELECT a.id,a.training_session_id,a.metric,a.metric_value,a.threshold_applied,a.severity,a.raised_at,
                       a.acknowledged_at,a.acknowledged_by_user_id,u.user_code,u.full_name,a.action_taken,a.escalated_to_vet_at
                FROM dbo.monitoring_alert a LEFT JOIN dbo.app_user u ON u.id=a.acknowledged_by_user_id
                WHERE a.training_session_id=? ORDER BY a.raised_at,a.id
                """, (rs,n) -> {
            Long by = nullableLong(rs,"acknowledged_by_user_id");
            return map("id",rs.getLong("id"),"sessionId",rs.getLong("training_session_id"),"metric",rs.getString("metric"),
                    "metricValue",rs.getBigDecimal("metric_value"),"thresholdApplied",rs.getBigDecimal("threshold_applied"),
                    "severity",rs.getString("severity"),"raisedAt",instant(rs.getTimestamp("raised_at")),
                    "acknowledgedAt",instant(rs.getTimestamp("acknowledged_at")),"acknowledgedBy",by==null?null:map("id",by,"userCode",rs.getString("user_code"),"fullName",rs.getString("full_name")),
                    "actionTaken",rs.getString("action_taken"),"escalatedToVetAt",instant(rs.getTimestamp("escalated_to_vet_at")));
        }, sessionId);
    }
    private Map<String, Object> riderFeedback(long sessionId) {
        List<Map<String,Object>> rows = jdbc.query("SELECT f.id,f.submitted_by_user_id,u.user_code,u.full_name,f.submitted_at,f.responses FROM dbo.rider_feedback f " +
                        "JOIN dbo.app_user u ON u.id=f.submitted_by_user_id WHERE f.training_session_id=?",
                (rs,n) -> { JsonNode responses; try { responses=json.readTree(rs.getString("responses")); } catch(Exception ex) { responses=json.createObjectNode(); }
                    return map("id",rs.getLong("id"),"sessionId",sessionId,"submittedBy",map("id",rs.getLong("submitted_by_user_id"),"userCode",rs.getString("user_code"),"fullName",rs.getString("full_name")),
                            "submittedAt",instant(rs.getTimestamp("submitted_at")),"responses",responses); }, sessionId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private Map<String, Object> latestEvaluation(long sessionId) {
        List<Long> ids=jdbc.queryForList("SELECT id FROM dbo.session_result WHERE training_session_id=?",Long.class,sessionId);
        return ids.isEmpty()?null:evaluation(ids.getFirst());
    }
    private Map<String, Object> evaluation(long id) {
        List<Map<String,Object>> rows=jdbc.query("SELECT sr.id,sr.training_session_id,sr.evaluated_by_user_id,u.user_code,u.full_name,sr.evaluation_label,sr.evaluated_summary,sr.evaluation_notes,sr.recommendation,sr.evaluated_at " +
                "FROM dbo.session_result sr JOIN dbo.app_user u ON u.id=sr.evaluated_by_user_id WHERE sr.id=?",(rs,n)->map("id",rs.getLong("id"),
                "sessionId",rs.getLong("training_session_id"),"result",rs.getString("evaluation_label"),"evaluatedSummary",rs.getString("evaluated_summary"),
                "evaluationNotes",rs.getString("evaluation_notes"),"recommendation",rs.getString("recommendation"),"evaluatedBy",map("id",rs.getLong("evaluated_by_user_id"),"userCode",rs.getString("user_code"),"fullName",rs.getString("full_name")),
                "evaluatedAt",instant(rs.getTimestamp("evaluated_at"))),id);
        if(rows.isEmpty())throw notFound("Evaluation was not found."); return rows.getFirst();
    }
    private String evaluationLabel(long sessionId) {
        List<String> labels=jdbc.queryForList("SELECT evaluation_label FROM dbo.session_result WHERE training_session_id=?",String.class,sessionId);
        return labels.isEmpty()?null:labels.getFirst();
    }
    private UserRef userRef(long userId) {
        return jdbc.query("SELECT id,user_code,full_name FROM dbo.app_user WHERE id=?",(rs,n)->new UserRef(rs.getLong(1),rs.getString(2),rs.getString(3)),userId).stream().findFirst().orElse(null);
    }
    private String availability(long horseId) {
        List<String> levels=jdbc.queryForList("SELECT restriction_level FROM dbo.medical_restriction WHERE horse_id=? AND lifted_at IS NULL AND effective_from<=CURRENT_TIMESTAMP ORDER BY CASE restriction_level WHEN 'LOCKED' THEN 0 ELSE 1 END,id DESC",String.class,horseId);
        return levels.isEmpty()?"AVAILABLE":levels.getFirst();
    }
    private String restrictionReason(long horseId) {
        List<String> reasons=jdbc.queryForList("SELECT reason FROM dbo.medical_restriction WHERE horse_id=? AND restriction_level='LOCKED' AND lifted_at IS NULL AND effective_from<=CURRENT_TIMESTAMP ORDER BY id DESC",String.class,horseId);
        return reasons.isEmpty()?null:reasons.getFirst();
    }
    private Double duration(Session s) { return duration(s.actualStart(), s.actualEnd()); }
    private Double duration(Instant start, Instant actualEnd) {
        if (start==null) return null;
        Instant end=actualEnd==null?Instant.now():actualEnd;
        return Math.max(0d,Duration.between(start,end).toMillis()/60000d);
    }
    private boolean horseExists(long horseId) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?",Integer.class,horseId); return count!=null&&count>0; }
    private boolean trainerAssignedToHorse(long trainer,long horseId) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment a JOIN dbo.horse_training_program p ON p.id=a.horse_training_program_id WHERE p.horse_id=? AND a.trainer_user_id=? AND a.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (a.effective_to IS NULL OR a.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",Integer.class,horseId,trainer); return count!=null&&count>0; }
    private void authorizeRead(ActorContext actor,Session s) {
        if (actor.roles().contains(RoleCode.VETERINARIAN)||actor.roles().contains(RoleCode.CLUB_MANAGER)||actor.roles().contains(RoleCode.HEAD_TRAINER)) return;
        if (actor.roles().contains(RoleCode.TRAINER)&&Objects.equals(actor.userId(),s.trainerId())) return;
        if (actor.roles().contains(RoleCode.WORK_RIDER)&&actor.userId()==s.riderId()) return;
        throw forbidden("Session result access denied.");
    }
    private long insertId(String sql, PreparedStatementSetter binder) {
        if(sqlServer){String output=sql.replaceFirst("(?i)\\bVALUES\\b","OUTPUT INSERTED.id VALUES");Long id=jdbc.query(output,binder,rs->rs.next()?rs.getLong(1):null);if(id==null)throw new IllegalStateException("SQL Server did not return session result identity.");return id;}
        KeyHolder holder=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);binder.setValues(ps);return ps;},holder);
        Object id=holder.getKeyList().stream().flatMap(m->m.entrySet().stream()).filter(e->e.getKey().equalsIgnoreCase("id")).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!(id instanceof Number n))throw new IllegalStateException("Database did not return session result identity.");return n.longValue();
    }
    private void validate(EvaluationRequest request) { if(request==null||request.result()==null||!Set.of("MET","PARTIALLY_MET","NOT_MET").contains(request.result())||length(request.evaluatedSummary(),2000)||length(request.evaluationNotes(),2000)||length(request.recommendation(),2000))throw validation("Evaluation fields are invalid."); }
    private boolean length(String value,int max){return value!=null&&value.length()>max;}
    private ActorContext actor(){return actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));}
    private ActorContext require(RoleCode role){ActorContext a=actor();if(!a.roles().contains(role))throw forbidden("Role is not allowed for this operation.");return a;}
    private static Map<String,Object> map(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    private static Instant instant(Timestamp t){return t==null?null:t.toInstant();}
    private static Integer nullableInt(java.sql.ResultSet rs,String c)throws java.sql.SQLException{int v=rs.getInt(c);return rs.wasNull()?null:v;}
    private static Long nullableLong(java.sql.ResultSet rs,String c)throws java.sql.SQLException{long v=rs.getLong(c);return rs.wasNull()?null:v;}
    private static void positive(long id){if(id<1)throw validation("id must be positive.");}
    private static BusinessException forbidden(String m){return new BusinessException(ErrorCode.FORBIDDEN,m);}
    private static BusinessException notFound(String m){return new BusinessException(ErrorCode.NOT_FOUND,m);}
    private static BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_ERROR,m);}
    private static BusinessException conflict(String m){return new BusinessException(ErrorCode.CONFLICT,m);}

    private record Metrics(long sampleCount,Integer maxHeartRate,BigDecimal averageHeartRate,BigDecimal maxSpeed,BigDecimal averageSpeed,boolean simulated) {}
    private record PerfSession(long id,String code,String status,Instant actualStart,Instant actualEnd,Instant scheduledStart) {}
    private record Session(long id,String code,String status,Instant actualStart,Instant actualEnd,Instant scheduledStart,Instant scheduledEnd,
            String surface,String focus,String intensity,String interruptionReason,long phaseId,long createdById,long riderId,long programId,
            long horseId,String programStatus,String phaseName,String horseCode,String horseName,String riderCode,String riderName,Long trainerId,
            String trainerCode,String trainerName,String precheck,String observations) {}
}
