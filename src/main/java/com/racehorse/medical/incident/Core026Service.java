package com.racehorse.medical.incident;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.notification.NotificationPublisher;
import com.racehorse.training.TrainingCalendar;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.racehorse.medical.incident.IncidentApi.*;

@Service
public class Core026Service {
    private static final Set<String> CHECK_KEYS=Set.of("GENERAL_CONDITION","APPETITE_BEHAVIOUR","LEGS_HOOVES","MOVEMENT","TEMPERATURE");
    private static final Set<String> INCIDENT_TYPES=Set.of("OFF_FEED","COLIC_SIGNS","FEVER","HOOF_OR_LEG_PROBLEM","OTHER");
    private static final Set<String> SEVERITIES=Set.of("LOW","MEDIUM","HIGH");
    private final Core026Repository repository;
    private final CurrentActorProvider actors;
    private final JsonMapper json;
    private final NotificationPublisher notifications;
    private final TrainingCalendar calendar;
    /** A pre-session check is valid from this long before scheduledStart until the session's planned end. */
    public static final java.time.Duration PRECHECK_WINDOW=java.time.Duration.ofHours(12);

    public Core026Service(Core026Repository repository,CurrentActorProvider actors,JsonMapper json,NotificationPublisher notifications,TrainingCalendar calendar){
        this.repository=repository;this.actors=actors;this.json=json;this.notifications=notifications;this.calendar=calendar;}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public PreSessionCheck submitPrecheck(long sessionId,PreSessionCheckRequest request){
        ActorContext actor=requireRole(RoleCode.GROOM);
        repository.lockSession(sessionId);
        var session=repository.session(sessionId).orElseThrow(()->notFound("Training session was not found."));
        if(!"SCHEDULED".equals(session.status()))throw conflict("Pre-session checks are accepted only for SCHEDULED sessions.");
        if(!repository.hasGroomSessionNotice(actor.userId(),sessionId))throw forbidden("This Groom is not assigned or notified for this session.");
        if(repository.hasCheck(sessionId))throw conflict("A pre-session check already exists for this session.");
        Instant[] window=repository.sessionWindow(sessionId).orElseThrow(()->notFound("Training session was not found."));
        Instant now=calendar.now();
        if(now.isBefore(window[0].minus(PRECHECK_WINDOW)))throw conflict("The pre-session check opens 12 hours before the scheduled start.");
        if(now.isAfter(window[1]==null?window[0].plus(PRECHECK_WINDOW):window[1]))throw conflict("The scheduled time has passed; the Trainer must reschedule before a pre-session check.");
        Map<String,CheckItem> checks=validateChecks(request);
        String observations;
        try{observations=json.writeValueAsString(checks);}catch(Exception e){throw new IllegalStateException("Could not serialize pre-session checklist.",e);}
        if(observations.length()>4000)throw validation("The checklist observations exceed the approved persistence capacity.");
        String result=checks.values().stream().allMatch(c->"NORMAL".equals(c.result()))?"ALL_NORMAL":"ABNORMAL";
        long id;
        try{id=repository.insertPrecheck(sessionId,actor.userId(),observations,result,Instant.now().truncatedTo(ChronoUnit.MILLIS));}
        catch(DataIntegrityViolationException e){throw conflict("A pre-session check already exists for this session.");}
        if("ABNORMAL".equals(result))for(long recipient:repository.medicalRecipients(session.programId()))
            notifications.persistAndPublish(recipient,Category.MEDICAL,"PRECHECK_ABNORMAL","Pre-session check ABNORMAL",
                    "The Groom recorded an ABNORMAL pre-session check; the session is blocked until the Veterinarian reviews it.","TRAINING_SESSION",sessionId,false);
        return precheckResponse(repository.precheckData(id));
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Incident create(CreateRequest request){
        ActorContext actor=requireActor();
        if(request==null||request.horseId()==null||request.horseId()<=0||request.observedAt()==null
                ||request.incidentType()==null||!INCIDENT_TYPES.contains(request.incidentType())
                ||request.observedSeverity()==null||!SEVERITIES.contains(request.observedSeverity())
                ||request.description()!=null&&request.description().length()>2000)throw validation("Incident fields are invalid.");
        if(actor.roles().stream().noneMatch(r->Set.of(RoleCode.HEAD_TRAINER,RoleCode.TRAINER,RoleCode.WORK_RIDER,RoleCode.GROOM).contains(r)))
            throw forbidden("The authenticated role cannot report incidents.");
        int sources=(request.preSessionCheckId()==null?0:1)+(request.trainingSessionId()==null?0:1)+(request.careTaskId()==null?0:1);
        if(sources>1)throw validation("Only one of preSessionCheckId, trainingSessionId or careTaskId may be supplied.");

        Long programId=null;Core026Repository.Session session=null;Core026Repository.CheckContext check=null;
        if(request.preSessionCheckId()!=null){
            check=repository.precheck(request.preSessionCheckId()).orElseThrow(()->notFound("Pre-session check was not found."));
            if(check.horseId()!=request.horseId())throw validation("The source context must belong to horseId.");
            session=repository.session(check.sessionId()).orElseThrow(()->notFound("Training session was not found."));programId=check.programId();
        }else if(request.trainingSessionId()!=null){
            session=repository.session(request.trainingSessionId()).orElseThrow(()->notFound("Training session was not found."));
            if(session.horseId()!=request.horseId())throw validation("The source context must belong to horseId.");programId=session.programId();
        }else if(request.careTaskId()!=null){
            long horse=repository.careTaskHorse(request.careTaskId()).orElseThrow(()->notFound("Care task was not found."));
            if(horse!=request.horseId())throw validation("The source context must belong to horseId.");
            if(actor.roles().contains(RoleCode.GROOM))throw forbidden("Groom care-task assignment is unavailable until its approved assignment mapping is deployed.");
        }
        authorizeReport(actor,request.horseId(),session,check,request.careTaskId()!=null,programId);
        if(repository.incidentForSourceExists(request.preSessionCheckId(),request.trainingSessionId(),request.careTaskId()))
            throw conflict("An Incident already references this source context.");
        Incident incident;
        try{incident=repository.incident(repository.insertIncident(request,actor.userId())).orElseThrow();}
        catch(DataIntegrityViolationException e){throw conflict("The source context already has a linked Incident or changed concurrently.");}
        for(long recipient:repository.medicalRecipients(programId))if(recipient!=actor.userId())
            notifications.persistAndPublish(recipient,Category.MEDICAL,"INCIDENT_REPORTED","Incident reported",
                    "An incident was reported and awaits Veterinarian review.","INCIDENT",incident.id(),false);
        return incident;
    }

    @Transactional(readOnly=true)
    public Page list(int page,int size){
        ActorContext actor=requireActor();if(page<0||size<1||size>100)throw validation("page must be non-negative and size must be between 1 and 100.");
        Long reporter=ownReportsOnly(actor)?actor.userId():null;
        if(reporter==null&&!canReadAll(actor))throw forbidden("The authenticated role cannot read incidents.");
        return new Page(repository.incidents(page,size,reporter),page,size,repository.incidentCount(reporter));
    }

    @Transactional(readOnly=true)
    public Incident get(long id){
        ActorContext actor=requireActor();if(id<=0)throw validation("incidentId must be positive.");
        Incident incident=repository.incident(id).orElseThrow(()->notFound("Incident was not found."));
        if(ownReportsOnly(actor)&&incident.reportedByUserId()!=actor.userId())throw forbidden("This incident is outside the authenticated user's report scope.");
        if(!ownReportsOnly(actor)&&!canReadAll(actor))throw forbidden("The authenticated role cannot read incidents.");
        return incident;
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public Incident review(long id,ReviewRequest request){
        ActorContext actor=requireRole(RoleCode.VETERINARIAN);
        if(id<=0)throw validation("incidentId must be positive.");if(request==null||request.action()==null)throw validation("action is required.");
        Incident incident=repository.incident(id).orElseThrow(()->notFound("Incident was not found."));
        String expected=incident.status();long nowId=actor.userId();Long caseId=request.medicalCaseId();String next;
        switch(request.action()){
            case "OPEN_CASE"->{
                if(caseId!=null)throw validation("medicalCaseId must be omitted for OPEN_CASE.");
                requireReportedUnlinked(incident);caseId=repository.insertCase(incident.horseId(),nowId,incident.description(),Instant.now().truncatedTo(ChronoUnit.MILLIS));next="UNDER_REVIEW";
            }
            case "LINK_CASE"->{
                if(caseId==null||caseId<=0)throw validation("medicalCaseId is required for LINK_CASE.");requireReportedUnlinked(incident);
                var linked=repository.medicalCase(caseId).orElseThrow(()->notFound("Medical case was not found."));
                if(linked.horseId()!=incident.horseId()||!"OPEN".equals(linked.status()))throw conflict("The case must be OPEN and belong to this incident's horse.");next="UNDER_REVIEW";
            }
            case "RESOLVE"->{if(caseId!=null)throw validation("medicalCaseId must be omitted for RESOLVE.");
                if(!Set.of("REPORTED","UNDER_REVIEW").contains(incident.status()))throw conflict("Only REPORTED or UNDER_REVIEW incidents can be resolved.");next="RESOLVED";}
            default->throw validation("Unsupported Incident review action.");
        }
        if(repository.transitionIncident(id,expected,next,nowId,caseId)!=1)throw conflict("Incident state changed concurrently; reload and retry.");
        return repository.incident(id).orElseThrow();
    }

    private void authorizeReport(ActorContext actor,long horseId,Core026Repository.Session session,
                                 Core026Repository.CheckContext check,boolean careTask,Long programId){
        if(actor.roles().contains(RoleCode.HEAD_TRAINER))return;
        if(actor.roles().contains(RoleCode.TRAINER)){
            if(programId!=null&&repository.trainerAssigned(programId,actor.userId()))return;
            if(programId==null&&repository.trainerOwnsHorse(horseId,actor.userId()))return;
            throw forbidden("The horse is outside the Trainer's active program assignments.");
        }
        if(actor.roles().contains(RoleCode.WORK_RIDER)){
            if(session!=null&&session.riderId()==actor.userId())return;
            throw forbidden("The session is outside the Work Rider's assignment scope.");
        }
        if(actor.roles().contains(RoleCode.GROOM)){
            if(session!=null&&repository.hasGroomSessionNotice(actor.userId(),session.id()))return;
            throw forbidden("The Groom is not notified for this session.");
        }
        throw forbidden("The report context is not authorized.");
    }

    private Map<String,CheckItem> validateChecks(PreSessionCheckRequest request){
        if(request==null||request.checks()==null||request.checks().size()!=5||!request.checks().keySet().equals(CHECK_KEYS))throw validation("Exactly the five approved checklist keys are required.");
        for(var entry:request.checks().entrySet()){
            CheckItem item=entry.getValue();if(item==null||item.result()==null||!Set.of("NORMAL","ABNORMAL").contains(item.result())
                    ||item.observation()!=null&&item.observation().length()>1000)throw validation("Checklist results must be NORMAL or ABNORMAL with observations up to 1000 characters.");
        }
        return request.checks();
    }
    private PreSessionCheck precheckResponse(Core026Repository.PrecheckData d){
        try{return new PreSessionCheck(d.id(),d.sessionId(),json.readValue(d.json(),new TypeReference<Map<String,CheckItem>>(){}),d.overall(),new UserRef(d.actorId(),d.actorName()),OffsetDateTime.ofInstant(d.at(),ZoneOffset.UTC));}
        catch(Exception e){throw new IllegalStateException("Stored pre-session checklist could not be read.",e);}
    }
    private void requireReportedUnlinked(Incident incident){if(!"REPORTED".equals(incident.status())||incident.medicalCaseId()!=null)throw conflict("Only a REPORTED unlinked incident may open or link a case.");}
    private boolean ownReportsOnly(ActorContext a){return a.roles().stream().anyMatch(r->Set.of(RoleCode.TRAINER,RoleCode.WORK_RIDER,RoleCode.GROOM).contains(r));}
    private boolean canReadAll(ActorContext a){return a.roles().stream().anyMatch(r->Set.of(RoleCode.VETERINARIAN,RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER).contains(r));}
    private ActorContext requireRole(RoleCode role){ActorContext a=requireActor();if(!a.roles().contains(role))throw forbidden("The authenticated role cannot perform this operation.");return a;}
    private ActorContext requireActor(){return actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));}
    private static BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_ERROR,m);}
    private static BusinessException forbidden(String m){return new BusinessException(ErrorCode.FORBIDDEN,m);}
    private static BusinessException notFound(String m){return new BusinessException(ErrorCode.NOT_FOUND,m);}
    private static BusinessException conflict(String m){return new BusinessException(ErrorCode.CONFLICT,m);}
}
