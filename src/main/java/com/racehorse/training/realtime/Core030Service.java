package com.racehorse.training.realtime;

import com.racehorse.notification.NotificationPublisher;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.jdbc.GeneratedIdentity;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static com.racehorse.training.realtime.Core030Api.*;

@Service
public class Core030Service {
    private final JdbcTemplate jdbc;private final CurrentActorProvider actors;private final SimpMessagingTemplate broker;
    private final BigDecimal globalCritical,globalWarning;private final boolean sqlServer,simulatorEnabled;
    private final com.racehorse.training.session.TrainingSessionService sessions;
    private final NotificationPublisher notifications;
    public Core030Service(JdbcTemplate jdbc,CurrentActorProvider actors,SimpMessagingTemplate broker,
            @Value("${app.medical.thresholds.heart-rate-critical:#{null}}") BigDecimal globalCritical,
            @Value("${app.medical.thresholds.heart-rate-warning:#{null}}") BigDecimal globalWarning,
            @Value("${app.realtime.simulator.enabled:false}") boolean simulatorEnabled,
            com.racehorse.training.session.TrainingSessionService sessions,NotificationPublisher notifications){
        this.sessions=sessions;this.notifications=notifications;
        this.jdbc=jdbc;this.actors=actors;this.broker=broker;this.globalCritical=globalCritical;this.globalWarning=globalWarning;this.simulatorEnabled=simulatorEnabled;
        String databaseProduct=jdbc.execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                        connection.getMetaData().getDatabaseProductName());
        if(databaseProduct==null||databaseProduct.isBlank())throw new IllegalStateException("Database product metadata is required.");
        this.sqlServer=databaseProduct.toLowerCase(Locale.ROOT).contains("sql server");
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public MetricResult submit(long sessionId,MetricRequest request){return record(sessionId,request,"MANUAL",require(RoleCode.WORK_RIDER));}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public MetricResult submitSimulated(long sessionId,MetricRequest request){
        if(!simulatorEnabled)throw notFound("Simulator is available only in dev/demo profiles.");
        return record(sessionId,request,"SIMULATOR",null);
    }

    public void authorizeSimulator(long sessionId,boolean starting){
        if(!simulatorEnabled)throw notFound("Simulator is available only in dev/demo profiles.");
        ActorContext actor=require(RoleCode.TRAINER);Session session=session(sessionId);authorizeTrainer(session,actor.userId());
        if(starting&&!Set.of("IN_PROGRESS","PAUSED").contains(session.status()))throw conflict("The simulator runs only for an IN_PROGRESS or PAUSED session.");
    }

    private MetricResult record(long sessionId,MetricRequest request,String source,ActorContext actor){
        lock(sessionId);Session s=session(sessionId);
        if(actor!=null&&s.riderId()!=actor.userId())throw forbidden("This is not the Work Rider's assigned session.");
        if(!Set.of("IN_PROGRESS","PAUSED").contains(s.status()))throw conflict("Metrics are accepted only while the session is IN_PROGRESS or PAUSED.");
        validateMetric(request);BigDecimal critical=effectiveCritical(s.horseId(),request.recordedAt());BigDecimal warning=globalWarning;
        if(warning!=null&&critical!=null&&warning.compareTo(critical)>=0)throw new IllegalStateException("Configured heart-rate warning threshold must be below critical threshold.");
        boolean simulated="SIMULATOR".equals(source);
        long sampleId=insertId("INSERT INTO dbo.training_metric_sample(training_session_id,recorded_at,heart_rate,speed,distance,source) VALUES (?,?,?,?,?,?)",ps->{ps.setLong(1,sessionId);ps.setTimestamp(2,Timestamp.from(request.recordedAt()));if(request.heartRate()==null)ps.setNull(3,java.sql.Types.SMALLINT);else ps.setInt(3,request.heartRate());ps.setBigDecimal(4,request.speedKmh());ps.setBigDecimal(5,request.distanceM());ps.setString(6,source);});
        String state="NORMAL";Long alertId=null;
        if(request.heartRate()!=null&&critical!=null){BigDecimal value=BigDecimal.valueOf(request.heartRate());if(value.compareTo(critical)>=0){state="CRITICAL";List<Integer> previous=jdbc.query("SELECT TOP 1 heart_rate FROM dbo.training_metric_sample WHERE training_session_id=? AND id<? AND heart_rate IS NOT NULL ORDER BY id DESC",(rs,n)->rs.getInt(1),sessionId,sampleId);if(previous.isEmpty()||BigDecimal.valueOf(previous.getFirst()).compareTo(critical)<0)alertId=createCritical(s,sessionId,value,critical,request.recordedAt(),simulated);}else if(warning!=null&&value.compareTo(warning)>=0)state="WARNING";}
        Map<String,Object> thresholds=new LinkedHashMap<>();thresholds.put("heartRateCritical",critical);thresholds.put("heartRateWarning",warning);
        Map<String,Object> event=new LinkedHashMap<>();event.put("sessionId",sessionId);event.put("sampleId",sampleId);event.put("recordedAt",request.recordedAt());event.put("heartRate",request.heartRate());event.put("speedKmh",request.speedKmh());event.put("distanceM",request.distanceM());event.put("source",source);event.put("simulated",simulated);event.put("realtimeState",state);event.put("thresholds",thresholds);
        send("/topic/sessions/"+sessionId+"/metrics",event);
        if("WARNING".equals(state)){Map<String,Object> warningEvent=new LinkedHashMap<>();warningEvent.put("type","WARNING");warningEvent.put("sessionId",sessionId);warningEvent.put("alertId",null);warningEvent.put("severity","WARNING");warningEvent.put("metric","HEART_RATE");warningEvent.put("metricValue",request.heartRate());warningEvent.put("thresholdApplied",warning);warningEvent.put("suggestedActions",List.of("REDUCE_INTENSITY"));warningEvent.put("simulated",simulated);send("/topic/sessions/"+sessionId+"/alerts",warningEvent);}
        return new MetricResult(sampleId,state,alertId);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Alert acknowledge(long alertId,AcknowledgeRequest request){ActorContext actor=actor();if(actor.roles().stream().noneMatch(r->Set.of(RoleCode.TRAINER,RoleCode.VETERINARIAN).contains(r)))throw forbidden("Only Trainer or Veterinarian may acknowledge a monitoring alert.");
        if(request==null||!Set.of("ACKNOWLEDGE","REDUCE_INTENSITY","PAUSE","STOP").contains(request.action()))throw validation("action is not supported.");
        if(actor.roles().contains(RoleCode.VETERINARIAN)&&!actor.roles().contains(RoleCode.TRAINER)&&!"ACKNOWLEDGE".equals(request.action()))throw forbidden("A Veterinarian may only acknowledge an alert.");
        Alert alert=alert(alertId);lock(alert.sessionId());Session session=session(alert.sessionId());if(actor.roles().contains(RoleCode.TRAINER))authorizeTrainer(session,actor.userId());
        if(alert.acknowledgedAt()!=null)throw conflict("Monitoring alert is already acknowledged.");
        if("STOP".equals(request.action())&&(request.stopReason()==null||request.stopReason().isBlank()))throw validation("stopReason is required for STOP.");
        if(Boolean.TRUE.equals(request.medicallyRelevant())&&"STOP".equals(request.action())&&(!Set.of("OFF_FEED","COLIC_SIGNS","FEVER","HOOF_OR_LEG_PROBLEM","OTHER").contains(request.incidentType())||!Set.of("LOW","MEDIUM","HIGH").contains(request.observedSeverity())))throw validation("A medically relevant STOP requires incidentType and observedSeverity.");
        if("PAUSE".equals(request.action())){if(!"IN_PROGRESS".equals(session.status()))throw conflict("Only an IN_PROGRESS session can be paused by an alert action.");jdbc.update("UPDATE dbo.training_session SET status='PAUSED',updated_at=SYSUTCDATETIME() WHERE id=? AND status='IN_PROGRESS'",session.id());publishState(session,"PAUSED",null,actor.userId());}
        if("STOP".equals(request.action())){if(!Set.of("IN_PROGRESS","PAUSED").contains(session.status()))throw conflict("Only an IN_PROGRESS or PAUSED session can be stopped.");if(Boolean.TRUE.equals(request.medicallyRelevant()))createIncident(session,alert.metricValue().toPlainString(),request.incidentType(),request.observedSeverity(),request.stopReason(),actor.userId());jdbc.update("UPDATE dbo.training_session SET status='INTERRUPTED',interruption_reason=?,actual_end=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME() WHERE id=? AND status IN ('IN_PROGRESS','PAUSED')",request.stopReason(),session.id());publishState(session,"INTERRUPTED",request.stopReason(),actor.userId());}
        if("REDUCE_INTENSITY".equals(request.action()))sessions.reduceIntensity(session.id(),request.intensity());
        if(jdbc.update("UPDATE dbo.monitoring_alert SET acknowledged_at=SYSUTCDATETIME(),acknowledged_by_user_id=?,action_taken=?,updated_at=SYSUTCDATETIME() WHERE id=? AND acknowledged_at IS NULL",actor.userId(),request.action(),alertId)!=1)throw conflict("Monitoring alert changed concurrently.");
        Alert updated=alert(alertId);send("/topic/sessions/"+session.id()+"/alerts",Map.of("type","CRITICAL_ACKNOWLEDGED","sessionId",session.id(),"alertId",alertId,"acknowledgedAt",updated.acknowledgedAt(),"acknowledgedBy",updated.acknowledgedBy(),"actionTaken",request.action()));return updated;}

    private Long createCritical(Session s,long sessionId,BigDecimal value,BigDecimal threshold,Instant at,boolean simulated){long id=insertId("INSERT INTO dbo.monitoring_alert(training_session_id,metric,metric_value,threshold_applied,severity,raised_at) VALUES (?,'HEART_RATE',?,?,'CRITICAL',?)",ps->{ps.setLong(1,sessionId);ps.setBigDecimal(2,value);ps.setBigDecimal(3,threshold);ps.setTimestamp(4,Timestamp.from(at));});
        String message="Critical heart rate threshold reached in session "+sessionId+".";
        List<Long> recipients=new ArrayList<>(jdbc.queryForList("SELECT u.id FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id JOIN dbo.role r ON r.id=ur.role_id WHERE u.account_status='ACTIVE' AND r.role_code='VETERINARIAN'",Long.class));
        recipients.addAll(jdbc.queryForList("SELECT DISTINCT ta.trainer_user_id FROM dbo.horse_program_phase ph JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=ph.horse_training_program_id JOIN dbo.app_user tu ON tu.id=ta.trainer_user_id AND tu.account_status='ACTIVE' WHERE ph.id=? AND ta.effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (ta.effective_to IS NULL OR ta.effective_to>=CAST(CURRENT_TIMESTAMP AS DATE)) AND NOT EXISTS (SELECT 1 FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id WHERE ur.user_id=ta.trainer_user_id AND r.role_code='VETERINARIAN')",Long.class,s.phaseId()));
        for(long recipient:recipients)notifications.persistAndPublish(recipient,Category.ALERTS,"MONITORING_ALERT_CRITICAL","Critical monitoring alert",message,"TRAINING_SESSION",sessionId,simulated);
        send("/topic/sessions/"+sessionId+"/alerts",Map.of("type","CRITICAL_RAISED","sessionId",sessionId,"alertId",id,"severity","CRITICAL","metric","HEART_RATE","metricValue",value,"thresholdApplied",threshold,"raisedAt",at,"suggestedActions",List.of("ACKNOWLEDGE","REDUCE_INTENSITY","PAUSE","STOP"),"simulated",simulated));return id;}

    private void createIncident(Session s,String value,String type,String severity,String description,long user){String code="INC-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase(Locale.ROOT);jdbc.update("INSERT INTO dbo.incident(incident_code,horse_id,reported_by_user_id,training_session_id,observed_at,reported_at,incident_type,observed_severity,description,status) VALUES (?,?,?,?,SYSUTCDATETIME(),SYSUTCDATETIME(),?,?,?,'REPORTED')",code,s.horseId(),user,s.id(),type,severity,description);}
    private void publishState(Session s,String status,String reason,long actor){Map<String,Object> event=new LinkedHashMap<>();event.put("sessionId",s.id());event.put("status",status);event.put("previousStatus",s.status());event.put("changedAt",Instant.now());event.put("changedBy",Map.of("id",actor));event.put("interruptionReason",reason);event.put("blocked",false);event.put("blockedReasons",List.of());send("/topic/sessions/"+s.id()+"/state",event);}
    private void send(String destination,Object payload){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){broker.convertAndSend(destination,payload);}
            });
        } else broker.convertAndSend(destination,payload);
    }
    private BigDecimal effectiveCritical(long horseId,Instant at){List<BigDecimal> override=jdbc.query("SELECT critical_value FROM dbo.horse_threshold_override WHERE horse_id=? AND metric='HEART_RATE' AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY effective_from DESC,id DESC",(rs,n)->rs.getBigDecimal(1),horseId,Timestamp.from(at),Timestamp.from(at));return override.isEmpty()?globalCritical:override.getFirst();}
    private Alert alert(long id){List<Alert> rows=jdbc.query("SELECT a.id,a.training_session_id,a.metric,a.metric_value,a.threshold_applied,a.severity,a.raised_at,a.acknowledged_at,a.acknowledged_by_user_id,u.user_code,u.full_name,a.action_taken,a.escalated_to_vet_at FROM dbo.monitoring_alert a LEFT JOIN dbo.app_user u ON u.id=a.acknowledged_by_user_id WHERE a.id=?",(rs,n)->{Long user=nullableLong(rs,"acknowledged_by_user_id");return new Alert(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getString(6),rs.getTimestamp(7).toInstant(),instant(rs.getTimestamp(8)),user==null?null:new UserRef(user,rs.getString("user_code"),rs.getString("full_name")),rs.getString("action_taken"),instant(rs.getTimestamp(13)));},id);if(rows.isEmpty())throw notFound("Monitoring alert was not found.");return rows.getFirst();}
    private Session session(long id){List<Session> rows=jdbc.query("SELECT s.id,s.status,s.work_rider_user_id,p.horse_id,ph.horse_training_program_id,ph.id FROM dbo.training_session s JOIN dbo.horse_program_phase ph ON ph.id=s.horse_program_phase_id JOIN dbo.horse_training_program p ON p.id=ph.horse_training_program_id WHERE s.id=?",(rs,n)->new Session(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6)),id);if(rows.isEmpty())throw notFound("Training session was not found.");return rows.getFirst();}
    private void authorizeTrainer(Session s,long trainer){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.trainer_assignment WHERE horse_training_program_id=? AND trainer_user_id=? AND effective_from<=CAST(CURRENT_TIMESTAMP AS DATE) AND (effective_to IS NULL OR effective_to>=CAST(CURRENT_TIMESTAMP AS DATE))",Integer.class,s.programId(),trainer);if(n==null||n==0)throw forbidden("Session is outside Trainer assignment scope.");}
    private void lock(long id){if(id<=0)throw validation("id must be positive.");if(sqlServer)jdbc.queryForList("SELECT id FROM dbo.training_session WITH (UPDLOCK,ROWLOCK) WHERE id=?",id);else jdbc.queryForList("SELECT id FROM dbo.training_session WHERE id=? FOR UPDATE",id);}
    private void validateMetric(MetricRequest r){if(r==null||r.recordedAt()==null||r.heartRate()==null&&r.speedKmh()==null&&r.distanceM()==null||r.heartRate()!=null&&(r.heartRate()<0||r.heartRate()>32767)||r.speedKmh()!=null&&(r.speedKmh().signum()<0||r.speedKmh().precision()>6||r.speedKmh().scale()>2)||r.distanceM()!=null&&(r.distanceM().signum()<0||r.distanceM().precision()>9||r.distanceM().scale()>2))throw validation("At least one non-negative metric value and recordedAt are required within SQL precision.");}
    private long insertId(String sql,PreparedStatementSetter binder){if(sqlServer){String outputSql=sql.replaceFirst("(?i)\\bVALUES\\b","OUTPUT INSERTED.id VALUES");if(outputSql.equals(sql))throw new IllegalArgumentException("An identity insert must use a VALUES clause.");Long id=jdbc.query(outputSql,binder,rs->rs.next()?rs.getLong(1):null);if(id==null)throw new IllegalStateException("Database did not return identity.");return id;}
        KeyHolder keys=new GeneratedKeyHolder();jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);binder.setValues(ps);return ps;},keys);return identity(keys);}
    private long identity(KeyHolder h){return GeneratedIdentity.requireId(h);}
    private Instant instant(Timestamp t){return t==null?null:t.toInstant();}private Long nullableLong(java.sql.ResultSet r,String col)throws java.sql.SQLException{long v=r.getLong(col);return r.wasNull()?null:v;}
    private ActorContext actor(){return actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));}private ActorContext require(RoleCode role){ActorContext a=actor();if(!a.roles().contains(role))throw forbidden("Role is not allowed for this operation.");return a;}
    private BusinessException forbidden(String m){return new BusinessException(ErrorCode.FORBIDDEN,m);}private BusinessException notFound(String m){return new BusinessException(ErrorCode.NOT_FOUND,m);}private BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_ERROR,m);}private BusinessException conflict(String m){return new BusinessException(ErrorCode.CONFLICT,m);}
    private record Session(long id,String status,long riderId,long horseId,long programId,long phaseId){}
}
