package com.racehorse.medical.incident;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.racehorse.medical.incident.IncidentApi.*;

@Repository
public class Core026Repository {
    private final JdbcTemplate jdbc;
    public Core026Repository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Session> session(long id) {
        return jdbc.query("""
                SELECT s.id,s.status,s.work_rider_user_id,ph.horse_training_program_id AS program_id,p.horse_id
                FROM dbo.training_session s
                JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id
                WHERE s.id=?
                """, (rs,n)->new Session(rs.getLong("id"),rs.getLong("horse_id"),rs.getLong("program_id"),
                rs.getLong("work_rider_user_id"),rs.getString("status")), id).stream().findFirst();
    }

    public Optional<Long> careTaskHorse(long id) {
        return jdbc.query("SELECT horse_id FROM dbo.care_task WHERE id=?", (rs,n)->rs.getLong(1), id).stream().findFirst();
    }

    public Optional<CheckContext> precheck(long id) {
        return jdbc.query("""
                SELECT pc.id,pc.training_session_id,pc.performed_by_user_id,pc.performed_at,pc.observations,pc.overall_result,
                       s.status,s.work_rider_user_id,ph.horse_training_program_id AS program_id,p.horse_id
                FROM dbo.pre_session_check pc JOIN dbo.training_session s ON s.id=pc.training_session_id
                JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
                JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id WHERE pc.id=?
                """, (rs,n)->new CheckContext(rs.getLong("id"),rs.getLong("training_session_id"),rs.getLong("horse_id"),
                rs.getLong("program_id"),rs.getLong("work_rider_user_id"),rs.getString("status")), id).stream().findFirst();
    }

    public boolean hasGroomSessionNotice(long groomId, long sessionId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.notification n
                JOIN dbo.user_role ur ON ur.user_id=n.recipient_user_id
                JOIN dbo.role r ON r.id=ur.role_id
                WHERE n.recipient_user_id=? AND r.role_code='GROOM' AND n.category='SCHEDULE'
                  AND n.target_type='TRAINING_SESSION' AND n.target_id=?
                """, Integer.class, groomId, sessionId)>0;
    }

    public boolean trainerAssigned(long programId, long trainerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.trainer_assignment
                WHERE horse_training_program_id=? AND trainer_user_id=? AND effective_from<=CAST(CURRENT_TIMESTAMP AS DATE)
                  AND (effective_to IS NULL OR effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))
                """, Integer.class, programId, trainerId)>0;
    }

    public boolean trainerOwnsHorse(long horseId,long trainerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.horse_training_program p JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=p.id
                WHERE p.horse_id=? AND p.status='ACTIVE' AND ta.trainer_user_id=?
                  AND ta.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE)
                  AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))
                """,Integer.class,horseId,trainerId)>0;
    }

    public boolean hasCheck(long sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.pre_session_check WHERE training_session_id=?", Integer.class,sessionId)>0;
    }

    public long insertPrecheck(long sessionId,long groomId,String observations,String overall,Instant at) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("""
                INSERT INTO dbo.pre_session_check(training_session_id,performed_by_user_id,performed_at,observations,overall_result)
                VALUES (?,?,?,?,?)
                """,java.sql.Statement.RETURN_GENERATED_KEYS);ps.setLong(1,sessionId);ps.setLong(2,groomId);
            ps.setTimestamp(3,java.sql.Timestamp.from(at));ps.setString(4,observations);ps.setString(5,overall);return ps;},keys);
        return identity(keys);
    }

    public PrecheckData precheckData(long id) {
        return jdbc.queryForObject("""
                SELECT pc.id,pc.training_session_id,pc.performed_by_user_id,u.full_name,pc.performed_at,pc.observations,pc.overall_result
                FROM dbo.pre_session_check pc JOIN dbo.app_user u ON u.id=pc.performed_by_user_id WHERE pc.id=?
                """, (rs,n)->new PrecheckData(rs.getLong("id"),rs.getLong("training_session_id"),rs.getLong("performed_by_user_id"),
                rs.getString("full_name"),rs.getTimestamp("performed_at").toInstant(),rs.getString("observations"),rs.getString("overall_result")),id);
    }

    public long insertIncident(CreateRequest req,long actorId) {
        KeyHolder keys=new GeneratedKeyHolder();String code="INC-"+java.util.UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(java.util.Locale.ROOT);
        jdbc.update(c->{var ps=c.prepareStatement("""
                INSERT INTO dbo.incident(incident_code,horse_id,reported_by_user_id,pre_session_check_id,training_session_id,care_task_id,
                    observed_at,reported_at,incident_type,observed_severity,description,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'REPORTED')
                """,java.sql.Statement.RETURN_GENERATED_KEYS);ps.setString(1,code);ps.setLong(2,req.horseId());ps.setLong(3,actorId);
            nullableLong(ps,4,req.preSessionCheckId());nullableLong(ps,5,req.trainingSessionId());nullableLong(ps,6,req.careTaskId());
            ps.setTimestamp(7,java.sql.Timestamp.from(req.observedAt().toInstant()));ps.setTimestamp(8,java.sql.Timestamp.from(Instant.now()));
            ps.setString(9,req.incidentType());ps.setString(10,req.observedSeverity());ps.setString(11,req.description());return ps;},keys);
        return identity(keys);
    }

    public Optional<Incident> incident(long id) {
        return jdbc.query("""
                SELECT id,incident_code,horse_id,reported_by_user_id,medical_case_id,pre_session_check_id,training_session_id,
                    care_task_id,incident_type,observed_severity,description,observed_at,reported_at,status
                FROM dbo.incident WHERE id=?
                """,(rs,n)->map(rs),id).stream().findFirst();
    }

    public boolean incidentForSourceExists(Long checkId,Long sessionId,Long careTaskId) {
        if(checkId==null&&sessionId==null&&careTaskId==null)return false;
        java.util.List<Object> args=new java.util.ArrayList<>();StringBuilder where=new StringBuilder();
        if(checkId!=null){where.append("pre_session_check_id=?");args.add(checkId);}
        if(sessionId!=null){if(!where.isEmpty())where.append(" OR ");where.append("training_session_id=?");args.add(sessionId);}
        if(careTaskId!=null){if(!where.isEmpty())where.append(" OR ");where.append("care_task_id=?");args.add(careTaskId);}
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident WHERE "+where,Integer.class,args.toArray())>0;
    }

    public List<Incident> incidents(int page,int size,Long reporter) {
        String filter=reporter==null?"":" WHERE reported_by_user_id=?";
        String sql="SELECT id,incident_code,horse_id,reported_by_user_id,medical_case_id,pre_session_check_id,training_session_id,care_task_id,incident_type,observed_severity,description,observed_at,reported_at,status FROM dbo.incident"+filter+" ORDER BY reported_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        if(reporter==null)return jdbc.query(sql,(rs,n)->map(rs),page*size,size);
        return jdbc.query(sql,(rs,n)->map(rs),reporter,page*size,size);
    }

    public long incidentCount(Long reporter) {
        if(reporter==null)return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident",Long.class);
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.incident WHERE reported_by_user_id=?",Long.class,reporter);
    }

    public Optional<CaseRecord> medicalCase(long id) {
        return jdbc.query("SELECT id,horse_id,status FROM dbo.medical_case WHERE id=?",(rs,n)->new CaseRecord(rs.getLong("id"),rs.getLong("horse_id"),rs.getString("status")),id).stream().findFirst();
    }

    public long insertCase(long horseId,long actorId,String summary,Instant at) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO dbo.medical_case(horse_id,opened_by_user_id,status,opened_at,condition_summary) VALUES (?,?,'OPEN',?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,horseId);ps.setLong(2,actorId);ps.setTimestamp(3,java.sql.Timestamp.from(at));ps.setString(4,summary);return ps;},keys);return identity(keys);
    }

    public int transitionIncident(long id,String expected,String status,long actorId,Long medicalCaseId) {
        return jdbc.update("""
                UPDATE dbo.incident SET status=?,reviewed_by_user_id=?,medical_case_id=COALESCE(?,medical_case_id),updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status=?
                """,status,actorId,medicalCaseId,id,expected);
    }

    private Incident map(ResultSet rs)throws SQLException {
        return new Incident(rs.getLong("id"),rs.getString("incident_code"),rs.getLong("horse_id"),rs.getLong("reported_by_user_id"),
                longOrNull(rs,"medical_case_id"),longOrNull(rs,"pre_session_check_id"),longOrNull(rs,"training_session_id"),longOrNull(rs,"care_task_id"),
                rs.getString("incident_type"),rs.getString("observed_severity"),rs.getString("description"),
                java.time.OffsetDateTime.ofInstant(rs.getTimestamp("observed_at").toInstant(),java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(rs.getTimestamp("reported_at").toInstant(),java.time.ZoneOffset.UTC),rs.getString("status"));
    }
    private static Long longOrNull(ResultSet rs,String col)throws SQLException { long v=rs.getLong(col);return rs.wasNull()?null:v; }
    private static void nullableLong(java.sql.PreparedStatement ps,int i,Long value)throws SQLException { if(value==null)ps.setNull(i,java.sql.Types.BIGINT);else ps.setLong(i,value); }
    private static long identity(KeyHolder keys) {
        Number n=keys.getKeys().values().stream().filter(Number.class::isInstance).map(Number.class::cast).findFirst().orElseThrow();return n.longValue();
    }
    public record Session(long id,long horseId,long programId,long riderId,String status){}
    public record CheckContext(long id,long sessionId,long horseId,long programId,long riderId,String status){}
    public record PrecheckData(long id,long sessionId,long actorId,String actorName,Instant at,String json,String overall){}
    public record CaseRecord(long id,long horseId,String status){}
}
