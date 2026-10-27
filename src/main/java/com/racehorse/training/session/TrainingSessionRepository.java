package com.racehorse.training.session;

import com.racehorse.notification.NotificationPublisher;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class TrainingSessionRepository {
    private final JdbcTemplate jdbc;
    private final NotificationPublisher notifications;
    private final tools.jackson.databind.json.JsonMapper json;
    private final String databaseProduct;
    public TrainingSessionRepository(JdbcTemplate jdbc, tools.jackson.databind.json.JsonMapper json, NotificationPublisher notifications) {
        this.notifications = notifications;
        this.json = json;
        this.jdbc = jdbc;
        this.databaseProduct = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
    }

    public long insert(long phaseId, long trainerId, long riderId, String code, Instant start, Instant end,
                       String surface, String focus, String intensity) {
        String sql = """
                INSERT INTO dbo.training_session
                  (session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,
                   scheduled_start,scheduled_end,surface,focus,intensity,status)
                VALUES (?,?,?,?,?,?,?,?,?,'SCHEDULED')
                """;
        if (isSqlServer()) {
            String outputSql = sql.replaceFirst("(?i)\\bVALUES\\b", "OUTPUT INSERTED.id VALUES");
            Long id = jdbc.query(outputSql,
                    statement -> bindSessionInsert(statement, phaseId, trainerId, riderId, code, start, end,
                            surface, focus, intensity),
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null);
            if (id == null) throw new IllegalStateException("Database did not return session id.");
            return id;
        }

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            bindSessionInsert(ps, phaseId, trainerId, riderId, code, start, end, surface, focus, intensity);
            return ps;
        }, keys);
        return GeneratedIdentity.requireId(keys);
    }

    private void bindSessionInsert(PreparedStatement ps, long phaseId, long trainerId, long riderId, String code,
                                   Instant start, Instant end, String surface, String focus, String intensity)
            throws java.sql.SQLException {
        ps.setString(1,code); ps.setLong(2,phaseId); ps.setLong(3,trainerId); ps.setLong(4,riderId);
        ps.setTimestamp(5,java.sql.Timestamp.from(start));
        if(end==null)ps.setNull(6,java.sql.Types.TIMESTAMP);else ps.setTimestamp(6,java.sql.Timestamp.from(end));
        ps.setString(7,surface); ps.setString(8,focus); ps.setString(9,intensity);
    }

    private boolean isSqlServer() {
        return databaseProduct != null && databaseProduct.toLowerCase(java.util.Locale.ROOT).contains("sql server");
    }

    public void insertExercise(long sessionId, SessionApi.ExerciseInput e, String category) {
        jdbc.update("""
                INSERT INTO dbo.session_exercise
                  (training_session_id,exercise_definition_id,exercise_order,distance,duration,repetitions,
                   rest_interval,intensity,surface,suitability_category,acknowledgement_note)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, sessionId,e.exerciseDefinitionId(),e.exerciseOrder(),e.distanceM(),e.durationMin(),
                e.repetitions(),e.restIntervalMin(),e.intensity(),e.surface(),category,e.acknowledgementNote());
    }

    public Map<String,Object> lockedSession(long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT s.id,s.session_code,s.horse_program_phase_id,s.created_by_user_id,s.work_rider_user_id,
                       s.scheduled_start,s.scheduled_end,s.surface,s.focus,s.intensity,s.status,s.actual_start,
                       s.actual_end,s.interruption_reason,p.horse_training_program_id,p.name AS phase_name,
                       pr.horse_id,pr.status AS program_status,pr.period_start,pr.period_end,h.horse_code,
                       h.name AS horse_name,h.lifecycle,COALESCE(mr.restriction_level,'AVAILABLE') AS availability,
                       mr.reason AS restriction_reason,ta.trainer_user_id,tu.full_name AS trainer_name,
                       ru.user_code AS rider_code,ru.full_name AS rider_name,cu.user_code AS creator_code,cu.full_name AS creator_name
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase p ON p.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program pr ON pr.id=p.horse_training_program_id
                JOIN dbo.horse h ON h.id=pr.horse_id
                LEFT JOIN dbo.medical_restriction mr ON mr.horse_id=pr.horse_id AND mr.lifted_at IS NULL
                LEFT JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=pr.id AND ta.effective_to IS NULL
                LEFT JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id
                JOIN dbo.app_user cu ON cu.id=s.created_by_user_id
                JOIN dbo.app_user ru ON ru.id=s.work_rider_user_id
                WHERE s.id=?
                """,id);
        if(rows.isEmpty()) return null;
        return rows.getFirst();
    }

    public List<SessionApi.Exercise> exercises(long id) {
        return jdbc.query("""
                SELECT se.id,se.exercise_definition_id,ed.name,ed.exercise_type,se.exercise_order,se.distance,
                       se.duration,se.repetitions,se.rest_interval,se.intensity,se.surface,se.suitability_category,
                       se.acknowledgement_note
                FROM dbo.session_exercise se JOIN dbo.exercise_definition ed ON ed.id=se.exercise_definition_id
                WHERE se.training_session_id=? ORDER BY se.exercise_order
                """,(rs,n)->new SessionApi.Exercise(rs.getLong("id"),rs.getLong("exercise_definition_id"),
                rs.getString("name"),rs.getString("exercise_type"),rs.getInt("exercise_order"),
                nullableDouble(rs,"distance"),nullableDouble(rs,"duration"),nullableInt(rs,"repetitions"),
                nullableDouble(rs,"rest_interval"),rs.getString("intensity"),rs.getString("surface"),
                rs.getString("suitability_category"),rs.getString("acknowledgement_note")),id);
    }

    public List<Map<String,Object>> exerciseDefinitions(List<Long> ids) {
        if(ids.isEmpty()) return List.of();
        String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        return jdbc.queryForList("SELECT id,name,exercise_type FROM dbo.exercise_definition WHERE id IN ("+placeholders+")",ids.toArray());
    }

    public List<Map<String,Object>> sessions(Instant from, Instant to, Long trainerId, Long horseId,
                                            Long programId, String status, Long trainerScope, Long riderScope) {
        return jdbc.queryForList("""
                SELECT s.id,s.session_code,s.horse_program_phase_id,s.created_by_user_id,s.work_rider_user_id,
                       s.scheduled_start,s.scheduled_end,s.surface,s.focus,s.intensity,s.status,s.actual_start,
                       s.actual_end,s.interruption_reason,p.horse_training_program_id,p.name AS phase_name,
                       pr.horse_id,pr.status AS program_status,h.horse_code,h.name AS horse_name,h.lifecycle,
                       COALESCE(mr.restriction_level,'AVAILABLE') AS availability,mr.reason AS restriction_reason,
                       ta.trainer_user_id,tu.full_name AS trainer_name,ru.full_name AS rider_name
                FROM dbo.training_session s JOIN dbo.horse_program_phase p ON p.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program pr ON pr.id=p.horse_training_program_id
                JOIN dbo.horse h ON h.id=pr.horse_id
                LEFT JOIN dbo.medical_restriction mr ON mr.horse_id=pr.horse_id AND mr.lifted_at IS NULL
                LEFT JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=pr.id AND ta.effective_to IS NULL
                LEFT JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id
                JOIN dbo.app_user ru ON ru.id=s.work_rider_user_id
                WHERE s.scheduled_start>=? AND s.scheduled_start<=?
                  AND (? IS NULL OR ta.trainer_user_id=?) AND (? IS NULL OR h.id=?)
                  AND (? IS NULL OR pr.id=?) AND (? IS NULL OR s.status=?)
                  AND (? IS NULL OR ta.trainer_user_id=?) AND (? IS NULL OR s.work_rider_user_id=?)
                ORDER BY s.scheduled_start,s.id
                """,timestamp(from),timestamp(to),trainerId,trainerId,horseId,horseId,programId,programId,status,status,
                trainerScope,trainerScope,riderScope,riderScope);
    }

    public List<Map<String,Object>> candidates() {
        return jdbc.queryForList("""
                SELECT u.id,u.user_code,u.full_name,u.experience_level
                FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id
                JOIN dbo.role r ON r.id=ur.role_id
                WHERE u.account_status='ACTIVE' AND r.role_code='WORK_RIDER' ORDER BY u.full_name,u.id
                """);
    }

    public Map<String,Object> phase(long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT p.*,pr.horse_id,pr.status AS program_status,pr.period_start,pr.period_end,h.lifecycle
                FROM dbo.horse_program_phase p JOIN dbo.horse_training_program pr ON pr.id=p.horse_training_program_id
                JOIN dbo.horse h ON h.id=pr.horse_id WHERE p.id=?
                """,id);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public String restrictionReason(long horseId) {
        List<String> reasons=jdbc.query("SELECT reason FROM dbo.medical_restriction WHERE horse_id=? AND lifted_at IS NULL AND restriction_level='LOCKED'",
                (rs,n)->rs.getString(1),horseId);
        return reasons.isEmpty()?null:reasons.getFirst();
    }

    public List<Map<String,Object>> restrictionRules(long horseId) {
        return jdbc.queryForList("""
                SELECT r.rule_type,r.exercise_definition_id,r.exercise_type_note
                FROM dbo.medical_restriction mr JOIN dbo.restriction_exercise_rule r ON r.medical_restriction_id=mr.id
                WHERE mr.horse_id=? AND mr.lifted_at IS NULL AND mr.restriction_level='LIMITED'
                """,horseId);
    }

    public boolean activeWorkRider(long id) {
        Integer n=jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id
                JOIN dbo.role r ON r.id=ur.role_id WHERE u.id=? AND u.account_status='ACTIVE' AND r.role_code='WORK_RIDER'
                """,Integer.class,id);
        return n!=null&&n>0;
    }

    public void lockRiderSchedule(long riderId) {
        if(databaseProduct!=null&&databaseProduct.toLowerCase(java.util.Locale.ROOT).contains("microsoft"))
            jdbc.queryForList("SELECT id FROM dbo.app_user WITH (UPDLOCK,HOLDLOCK) WHERE id=?",riderId);
        else jdbc.queryForList("SELECT id FROM dbo.app_user WHERE id=? FOR UPDATE",riderId);
    }
    public void lockSession(long sessionId) {
        if(databaseProduct!=null&&databaseProduct.toLowerCase(java.util.Locale.ROOT).contains("microsoft"))
            jdbc.queryForList("SELECT id FROM dbo.training_session WITH (UPDLOCK,ROWLOCK) WHERE id=?",sessionId);
        else jdbc.queryForList("SELECT id FROM dbo.training_session WHERE id=? FOR UPDATE",sessionId);
    }

    public String precheckResult(long sessionId) {
        List<String> rows=jdbc.query("SELECT overall_result FROM dbo.pre_session_check WHERE training_session_id=?",(rs,n)->rs.getString(1),sessionId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public com.racehorse.medical.incident.IncidentApi.PreSessionCheck precheck(long sessionId) {
        List<com.racehorse.medical.incident.IncidentApi.PreSessionCheck> rows=jdbc.query("""
                SELECT c.id,c.performed_at,c.observations,c.overall_result,u.id AS user_id,u.user_code,u.full_name
                FROM dbo.pre_session_check c JOIN dbo.app_user u ON u.id=c.performed_by_user_id
                WHERE c.training_session_id=?
                """,(rs,n)->new com.racehorse.medical.incident.IncidentApi.PreSessionCheck(rs.getLong("id"),sessionId,
                rs.getString("observations")==null?Map.of():json.readValue(rs.getString("observations"),new tools.jackson.core.type.TypeReference<Map<String,com.racehorse.medical.incident.IncidentApi.CheckItem>>(){}),
                rs.getString("overall_result"),new com.racehorse.medical.incident.IncidentApi.UserRef(rs.getLong("user_id"),rs.getString("full_name")),
                rs.getTimestamp("performed_at").toInstant().atOffset(java.time.ZoneOffset.UTC)),sessionId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public List<Long> linkedIncidentIds(long sessionId) {
        return jdbc.query("SELECT id FROM dbo.incident WHERE training_session_id=? ORDER BY id",(rs,n)->rs.getLong(1),sessionId);
    }
    public boolean hasRiderFeedback(long sessionId) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.rider_feedback WHERE training_session_id=?",Integer.class,sessionId);
        return n!=null&&n>0;
    }
    public boolean hasEvaluation(long sessionId) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.session_result WHERE training_session_id=?",Integer.class,sessionId);
        return n!=null&&n>0;
    }

    public void notifyRider(long riderId,long sessionId,String code) {
        notifications.persistAndPublish(riderId,Category.SCHEDULE,"SESSION_ASSIGNED","Training session assigned",
                "Session "+code+" has been assigned to you.","TRAINING_SESSION",sessionId,false);
    }

    public void audit(long actorId,String role,String action,long sessionId,String summary) {
        jdbc.update("""
                INSERT INTO dbo.audit_log(actor_user_id,actor_role,action,target_type,target_id,safe_summary,source)
                VALUES (?, ?, ?, 'TRAINING_SESSION', ?, ?, 'HTTP')
                """,actorId,role,action,sessionId,summary);
    }

    public void updateSession(long id, SessionApi.UpdateRequest r, Long riderId) {
        jdbc.update("""
                UPDATE dbo.training_session SET scheduled_start=COALESCE(?,scheduled_start),
                  scheduled_end=CASE WHEN ? THEN ? ELSE scheduled_end END,
                  surface=COALESCE(?,surface),focus=COALESCE(?,focus),intensity=COALESCE(?,intensity),
                  work_rider_user_id=COALESCE(?,work_rider_user_id),updated_at=SYSUTCDATETIME()
                WHERE id=? AND status='SCHEDULED'
                """,r.scheduledStart(),r.scheduledEnd()!=null,r.scheduledEnd(),r.surface(),r.focus(),r.intensity(),riderId,id);
    }

    public void deleteExercises(long id) { jdbc.update("DELETE FROM dbo.session_exercise WHERE training_session_id=?",id); }
    public int transition(long id,String from,String to) {
        if("IN_PROGRESS".equals(to)) return jdbc.update("UPDATE dbo.training_session SET status=?,actual_start=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME() WHERE id=? AND status=?",to,id,from);
        return jdbc.update("UPDATE dbo.training_session SET status=?,updated_at=SYSUTCDATETIME() WHERE id=? AND status=?",to,id,from);
    }
    public boolean hasPassingPrecheck(long id) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.pre_session_check WHERE training_session_id=? AND overall_result='ALL_NORMAL'",Integer.class,id);
        return n!=null&&n>0;
    }
    public boolean hasScheduleConflict(long riderId,Instant start,Instant end,Long exceptId) {
        String overlap=end==null?"(scheduled_start=? OR (scheduled_start<? AND scheduled_end>?))":
                "(scheduled_start<? AND ((scheduled_end IS NOT NULL AND scheduled_end>?) OR (scheduled_end IS NULL AND scheduled_start>=?)))";
        String sql="SELECT COUNT(*) FROM dbo.training_session WHERE work_rider_user_id=? AND status IN ('SCHEDULED','IN_PROGRESS','PAUSED') AND "+overlap;
        List<Object> values=new java.util.ArrayList<>(); values.add(riderId);
        if(end==null){values.add(timestamp(start));values.add(timestamp(start));values.add(timestamp(start));}
        else {values.add(timestamp(end));values.add(timestamp(start));values.add(timestamp(start));}
        if(exceptId!=null){sql+=" AND id<>?";values.add(exceptId);}
        Object[] args=values.toArray();
        Integer n=jdbc.queryForObject(sql,Integer.class,args);
        return n!=null&&n>0;
    }
    /** Another active session of the same horse overlapping [start,end); a horse cannot train twice at once. */
    public boolean hasHorseScheduleConflict(long phaseId,Instant start,Instant end,Long exceptId) {
        Instant effectiveEnd=end==null?start.plusSeconds(1):end;
        String sql="""
                SELECT COUNT(*) FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id
                WHERE p.horse_id=(SELECT p2.horse_id FROM dbo.horse_program_phase ph2 JOIN dbo.horse_training_program p2 ON p2.id=ph2.horse_training_program_id WHERE ph2.id=?)
                  AND s.status IN ('SCHEDULED','IN_PROGRESS','PAUSED')
                  AND s.scheduled_start<? AND (COALESCE(s.scheduled_end,s.scheduled_start)>? OR s.scheduled_start>=?)
                """;
        List<Object> values=new java.util.ArrayList<>(List.of(phaseId,timestamp(effectiveEnd),timestamp(start),timestamp(start)));
        if(exceptId!=null){sql+=" AND s.id<>?";values.add(exceptId);}
        Integer n=jdbc.queryForObject(sql,Integer.class,values.toArray());
        return n!=null&&n>0;
    }
    public List<String> riderSkills(long userId) {
        return jdbc.queryForList("SELECT s.name FROM dbo.user_skill us JOIN dbo.skill s ON s.id=us.skill_id WHERE us.user_id=? AND s.kind='RIDER_SKILL' ORDER BY s.name",String.class,userId);
    }
    public List<String> horseStrengths(long horseId) {
        return jdbc.queryForList("SELECT strength_code FROM dbo.horse_strength WHERE horse_id=?",String.class,horseId);
    }
    public int completedSessionsWithHorse(long riderId,long horseId) {
        Integer n=jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id
                WHERE s.work_rider_user_id=? AND p.horse_id=? AND s.status='COMPLETED'
                """,Integer.class,riderId,horseId);
        return n==null?0:n;
    }
    /** Rider sessions on the same Asia/Ho_Chi_Minh day as {@code start}. */
    public int riderSessionsOnClubDay(long riderId,Instant start,Long exceptId) {
        java.time.ZoneId zone=java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.LocalDate day=start.atZone(zone).toLocalDate();
        String sql="SELECT COUNT(*) FROM dbo.training_session WHERE work_rider_user_id=? AND status IN ('SCHEDULED','IN_PROGRESS','PAUSED','COMPLETED') AND scheduled_start>=? AND scheduled_start<?";
        List<Object> args=new java.util.ArrayList<>(List.of(riderId,timestamp(day.atStartOfDay(zone).toInstant()),timestamp(day.plusDays(1).atStartOfDay(zone).toInstant())));
        if(exceptId!=null){sql+=" AND id<>?";args.add(exceptId);}
        Integer n=jdbc.queryForObject(sql,Integer.class,args.toArray());
        return n==null?0:n;
    }
    public Instant precheckPerformedAt(long sessionId) {
        List<Instant> rows=jdbc.query("SELECT performed_at FROM dbo.pre_session_check WHERE training_session_id=?",(rs,n)->rs.getTimestamp(1).toInstant(),sessionId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private java.sql.Timestamp timestamp(Instant i){return i==null?null:java.sql.Timestamp.from(i);}
    private static Double nullableDouble(java.sql.ResultSet rs,String col)throws java.sql.SQLException { double v=rs.getDouble(col); return rs.wasNull()?null:v; }
    private static Integer nullableInt(java.sql.ResultSet rs,String col)throws java.sql.SQLException { int v=rs.getInt(col); return rs.wasNull()?null:v; }
}
