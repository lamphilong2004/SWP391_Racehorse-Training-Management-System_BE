package com.racehorse.training.session;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.racehorse.training.session.Core028Api.*;

@Service
public class Core028Service {
    private static final Set<String> STATUSES=Set.of("SCHEDULED","IN_PROGRESS","PAUSED","COMPLETED","INTERRUPTED");
    private static final Set<String> INCIDENT_TYPES=Set.of("OFF_FEED","COLIC_SIGNS","FEVER","HOOF_OR_LEG_PROBLEM","OTHER");
    private static final Set<String> SEVERITIES=Set.of("LOW","MEDIUM","HIGH");
    private static final Set<String> FEEDBACK_FIELDS=Set.of("horseResponse","balance","breathing","strideFeeling","abnormalBehavior","preInterruptionDescription","notes");
    private final JdbcTemplate jdbc;private final CurrentActorProvider actors;private final JsonMapper json;
    public Core028Service(JdbcTemplate jdbc,CurrentActorProvider actors,JsonMapper json){this.jdbc=jdbc;this.actors=actors;this.json=json;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> assignments(String status){ActorContext a=require(RoleCode.WORK_RIDER);if(status!=null&&!STATUSES.contains(status))throw validation("status is not supported.");
        String filter=status==null?" AND s.status IN ('SCHEDULED','IN_PROGRESS','PAUSED')":" AND s.status=?";
        List<Object> args=new ArrayList<>(List.of(a.userId()));if(status!=null)args.add(status);
        return jdbc.query(summarySql()+" WHERE s.work_rider_user_id=?"+filter+" ORDER BY s.scheduled_start ASC,s.id ASC",(rs,n)->summary(rs),args.toArray());}

    @Transactional(readOnly=true)
    public Map<String,Object> history(int page,int size){ActorContext a=require(RoleCode.WORK_RIDER);validatePage(page,size);
        String sql=summarySql()+" WHERE s.work_rider_user_id=? AND s.status IN ('COMPLETED','INTERRUPTED') ORDER BY COALESCE(s.actual_end,s.scheduled_start) DESC,s.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<Map<String,Object>> items=jdbc.query(sql,(rs,n)->summary(rs),a.userId(),page*size,size);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.training_session WHERE work_rider_user_id=? AND status IN ('COMPLETED','INTERRUPTED')",Long.class,a.userId());
        return Map.of("items",items,"page",page,"size",size,"totalItems",total,"totalPages",(int)Math.ceil(total/(double)size));}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Map<String,Object> complete(long id){ActorContext a=require(RoleCode.WORK_RIDER);Session s=session(id);if(s.riderId()!=a.userId())throw forbidden();
        if(!"IN_PROGRESS".equals(s.status()))throw conflict("Only an IN_PROGRESS session may be completed.");
        if(jdbc.update("UPDATE dbo.training_session SET status='COMPLETED',actual_end=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='IN_PROGRESS'",id)!=1)throw conflict("Session state changed; reload and try again.");
        return summary(id);}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Map<String,Object> stop(long id,StopRequest r){ActorContext a=require(RoleCode.TRAINER);Session s=session(id);authorizeTrainer(s,a);
        if(r==null||r.reason()==null||r.reason().isBlank()||r.reason().length()>1000)throw validation("reason is required and must not exceed 1000 characters.");
        if(r.medicallyRelevant()&&(!INCIDENT_TYPES.contains(r.incidentType())||!SEVERITIES.contains(r.observedSeverity())))throw validation("A medically relevant stop requires incidentType and observedSeverity.");
        interrupt(s,id,r.reason(),r.medicallyRelevant()?r.incidentType():null,r.medicallyRelevant()?r.observedSeverity():null,Instant.now(),a.userId());return summary(id);}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public IssueResult reportIssue(long id,ReportIssueRequest r){ActorContext a=actor();if(r==null||r.description()==null||r.description().isBlank()||r.description().length()>1000||!INCIDENT_TYPES.contains(r.incidentType())||!SEVERITIES.contains(r.observedSeverity()))throw validation("Incident fields are invalid.");
        Session s=session(id);if(a.roles().contains(RoleCode.WORK_RIDER)){if(s.riderId()!=a.userId())throw forbidden();}else if(a.roles().contains(RoleCode.TRAINER))authorizeTrainer(s,a);else throw forbidden();
        if(!Set.of("IN_PROGRESS","PAUSED").contains(s.status()))throw conflict("Only an IN_PROGRESS or PAUSED session may report an issue.");
        Instant at=r.observedAt()==null?Instant.now():r.observedAt();String code="INC-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);
        long incident=insertIncident(s,id,a.userId(),code,r.incidentType(),r.observedSeverity(),r.description(),at);
        if(jdbc.update("UPDATE dbo.training_session SET status='INTERRUPTED',interruption_reason=?,actual_end=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('IN_PROGRESS','PAUSED')",r.description(),id)!=1)throw conflict("Session state changed; reload and try again.");
        notifyIncident(s,id,code);return new IssueResult(incident,code,"INTERRUPTED");}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Feedback feedback(long id,RiderFeedbackRequest r){ActorContext a=require(RoleCode.WORK_RIDER);Session s=session(id);if(s.riderId()!=a.userId())throw forbidden();
        if(!Set.of("COMPLETED","INTERRUPTED").contains(s.status()))throw conflict("Feedback is available only after COMPLETED or INTERRUPTED.");
        if(r==null||r.responses()==null||!FEEDBACK_FIELDS.containsAll(r.responses().keySet()))throw validation("Feedback contains unsupported fields.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.rider_feedback WHERE training_session_id=?",Integer.class,id)>0)throw conflict("Rider feedback was already submitted for this session.");
        for(var e:r.responses().entrySet())if(e.getValue()!=null&&(!(e.getValue() instanceof String v)||v.length()>maxLength(e.getKey())))throw validation("Feedback values must be strings within their field limits.");
        String body;try{body=json.writeValueAsString(r.responses());}catch(Exception e){throw new IllegalStateException("Feedback could not be serialized.",e);}
        KeyHolder keys=new GeneratedKeyHolder();try{jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO dbo.rider_feedback(training_session_id,submitted_by_user_id,submitted_at,responses) VALUES (?,?,CURRENT_TIMESTAMP,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,id);ps.setLong(2,a.userId());ps.setString(3,body);return ps;},keys);}catch(DataIntegrityViolationException e){throw conflict("Rider feedback was already submitted for this session.");}
        long feedbackId=generatedId(keys);Instant submitted=jdbc.queryForObject("SELECT submitted_at FROM dbo.rider_feedback WHERE id=?",Timestamp.class,feedbackId).toInstant();return new Feedback(feedbackId,id,a.userId(),submitted,r.responses());}

    private void interrupt(Session s,long id,String reason,String type,String severity,Instant at,long actorId){if(!Set.of("IN_PROGRESS","PAUSED").contains(s.status()))throw conflict("Only an IN_PROGRESS or PAUSED session may be interrupted.");
        if(type!=null){String code="INC-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);insertIncident(s,id,actorId,code,type,severity,reason,at);notifyIncident(s,id,code);}
        if(jdbc.update("UPDATE dbo.training_session SET status='INTERRUPTED',interruption_reason=?,actual_end=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('IN_PROGRESS','PAUSED')",reason,id)!=1)throw conflict("Session state changed; reload and try again.");}

    private long insertIncident(Session s,long id,long actor,String code,String type,String severity,String description,Instant observed){KeyHolder keys=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO dbo.incident(incident_code,horse_id,reported_by_user_id,training_session_id,observed_at,reported_at,incident_type,observed_severity,description,status) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,'REPORTED')",Statement.RETURN_GENERATED_KEYS);ps.setString(1,code);ps.setLong(2,s.horseId());ps.setLong(3,actor);ps.setLong(4,id);ps.setTimestamp(5,Timestamp.from(observed));ps.setString(6,type);ps.setString(7,severity);ps.setString(8,description);return ps;},keys);return generatedId(keys);}

    private void notifyIncident(Session s,long id,String code){String message="Incident "+code+" was reported for session "+s.code()+".";
        jdbc.update("INSERT INTO dbo.notification(recipient_user_id,category,notification_type,title,message,target_type,target_id) SELECT u.id,'MEDICAL','INCIDENT_REPORTED','Training incident reported',?,'TRAINING_SESSION',? FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id JOIN dbo.role r ON r.id=ur.role_id WHERE u.account_status='ACTIVE' AND r.role_code='VETERINARIAN'",message,id);
        jdbc.update("INSERT INTO dbo.notification(recipient_user_id,category,notification_type,title,message,target_type,target_id) SELECT DISTINCT ta.trainer_user_id,'MEDICAL','INCIDENT_REPORTED','Training incident reported',?,'TRAINING_SESSION',? FROM dbo.horse_program_phase ph JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=ph.horse_training_program_id WHERE ph.id=? AND ta.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",message,id,s.phaseId());}

    private Map<String,Object> summary(long id){List<Map<String,Object>> r=jdbc.query(summarySql()+" WHERE s.id=?",(rs,n)->summary(rs),id);if(r.isEmpty())throw notFound("Training session was not found.");return r.getFirst();}
    private Map<String,Object> summary(java.sql.ResultSet rs)throws java.sql.SQLException{String status=rs.getString("status"),availability=rs.getString("availability"),pre=rs.getString("precheck");List<String> blocked=new ArrayList<>();if("LOCKED".equals(availability))blocked.add("AVAILABILITY_LOCKED");if(pre==null)blocked.add("PRE_CHECK_MISSING");else if("ABNORMAL".equals(pre))blocked.add("PRE_CHECK_ABNORMAL");if(!"ACTIVE".equals(rs.getString("program_status")))blocked.add("PROGRAM_NOT_ACTIVE");boolean canStart="SCHEDULED".equals(status)&&!Timestamp.from(Instant.now()).before(rs.getTimestamp("scheduled_start"))&&blocked.isEmpty();
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",rs.getLong("id"));out.put("sessionCode",rs.getString("session_code"));out.put("horse",Map.of("id",rs.getLong("horse_id"),"horseCode",rs.getString("horse_code"),"name",rs.getString("horse_name")));out.put("programId",rs.getLong("program_id"));out.put("phaseName",rs.getString("phase_name"));out.put("scheduledStart",rs.getTimestamp("scheduled_start").toInstant());out.put("scheduledEnd",rs.getTimestamp("scheduled_end")==null?null:rs.getTimestamp("scheduled_end").toInstant());out.put("status",status);out.put("workRider",Map.of("id",rs.getLong("rider_id"),"userCode",rs.getString("rider_code"),"fullName",rs.getString("rider_name")));Long tid=(Long)rs.getObject("trainer_id");out.put("trainer",tid==null?null:Map.of("id",tid,"userCode",rs.getString("trainer_code"),"fullName",rs.getString("trainer_name")));out.put("preCheckResult",pre);out.put("blocked",!canStart);out.put("blockedReasons",blocked);out.put("blockedMessage",blocked.isEmpty()?null:blocked.getFirst());return out;}
    private String summarySql(){return """
            SELECT s.id,s.session_code,s.scheduled_start,s.scheduled_end,s.status,h.id AS horse_id,h.horse_code,h.name AS horse_name,
                   p.id AS program_id,p.status AS program_status,ph.id AS phase_id,ph.name AS phase_name,
                   wr.id AS rider_id,wr.user_code AS rider_code,wr.full_name AS rider_name,
                   ta.trainer_user_id AS trainer_id,tu.user_code AS trainer_code,tu.full_name AS trainer_name,
                   COALESCE(mr.restriction_level,'AVAILABLE') AS availability,pc.overall_result AS precheck
            FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id
            JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id JOIN dbo.horse h ON h.id=p.horse_id
            JOIN dbo.app_user wr ON wr.id=s.work_rider_user_id
            LEFT JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=p.id AND ta.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))
            LEFT JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id
            LEFT JOIN dbo.medical_restriction mr ON mr.horse_id=h.id AND mr.lifted_at IS NULL
            LEFT JOIN dbo.pre_session_check pc ON pc.training_session_id=s.id
            """;}
    private Session session(long id){if(id<=0)throw validation("id must be positive.");List<Session> rows=jdbc.query("SELECT s.id,s.status,s.work_rider_user_id,ph.horse_training_program_id,p.horse_id,ph.id AS phase_id,s.session_code FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id WHERE s.id=?",(rs,n)->new Session(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getString(7)),id);if(rows.isEmpty())throw notFound("Training session was not found.");return rows.getFirst();}
    private void authorizeTrainer(Session s,ActorContext a){if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment WHERE horse_training_program_id=? AND trainer_user_id=? AND effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (effective_to IS NULL OR effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",Integer.class,s.programId(),a.userId())==0)throw forbidden();}
    private int maxLength(String field){return Set.of("preInterruptionDescription","notes").contains(field)?1000:500;}
    private long generatedId(KeyHolder h){Object id=h.getKeyList().stream().flatMap(m->m.entrySet().stream()).filter(e->e.getKey().equalsIgnoreCase("id")).map(Map.Entry::getValue).findFirst().orElse(null);if(!(id instanceof Number n))throw new IllegalStateException("The database did not return a generated id.");return n.longValue();}
    private ActorContext actor(){return actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));}
    private ActorContext require(RoleCode role){ActorContext a=actor();if(!a.roles().contains(role))throw forbidden();return a;}
    private void validatePage(int page,int size){if(page<0||size<1||size>100)throw validation("page must be non-negative and size must be between 1 and 100.");}
    private BusinessException validation(String s){return new BusinessException(ErrorCode.VALIDATION_ERROR,s);}private BusinessException forbidden(){return new BusinessException(ErrorCode.FORBIDDEN,"The resource is outside the authenticated user's assignment scope.");}private BusinessException notFound(String s){return new BusinessException(ErrorCode.NOT_FOUND,s);}private BusinessException conflict(String s){return new BusinessException(ErrorCode.CONFLICT,s);}
    private record Session(long id,String status,long riderId,long programId,long horseId,long phaseId,String code){Session(long id,String status,long riderId,long programId,long horseId,long phaseId){this(id,status,riderId,programId,horseId,phaseId,null);}}
}
