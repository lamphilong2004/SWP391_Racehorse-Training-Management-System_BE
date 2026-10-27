package com.racehorse.training.session;

import com.racehorse.notification.NotificationPublisher;
import com.racehorse.training.TrainingCalendar;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class TrainingSessionService {
    private static final java.time.ZoneId CLUB_ZONE=java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> INTENSITIES=Set.of("LOW","MEDIUM","MEDIUM_HIGH","HIGH");
    private final JdbcTemplate jdbc;
    private final NotificationPublisher notifications;
    private final TrainingSessionRepository repository;
    private final CurrentActorProvider actors;
    private final TrainingCalendar calendar;

    public TrainingSessionService(JdbcTemplate jdbc,TrainingSessionRepository repository,CurrentActorProvider actors,NotificationPublisher notifications,TrainingCalendar calendar) {
        this.calendar=calendar;
        this.notifications=notifications;
        this.jdbc=jdbc; this.repository=repository; this.actors=actors;
    }

    @Transactional(readOnly=true)
    public List<SessionApi.Session> list(Instant from,Instant to,Long trainerId,Long horseId,Long programId,String status) {
        ActorContext a=actor();
        if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusSeconds(366L*86400))) throw validation("from/to must define a valid range no longer than 366 days.");
        if(status!=null&&!Set.of("SCHEDULED","IN_PROGRESS","PAUSED","COMPLETED","INTERRUPTED").contains(status)) throw validation("Unsupported session status.");
        Long trainerScope=a.roles().contains(RoleCode.TRAINER)?a.userId():null;
        Long riderScope=a.roles().contains(RoleCode.WORK_RIDER)?a.userId():null;
        if(trainerId!=null&&!a.roles().contains(RoleCode.HEAD_TRAINER)&&!a.roles().contains(RoleCode.TRAINER)) throw forbidden();
        if(a.roles().contains(RoleCode.TRAINER)&&trainerId!=null&&trainerId!=a.userId()) throw forbidden();
        return repository.sessions(from,to,trainerId,horseId,programId,status,trainerScope,riderScope).stream().map(this::map).toList();
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public SessionApi.Session create(SessionApi.CreateRequest r) {
        ActorContext a=require(RoleCode.TRAINER); validateSchedule(r.horseProgramPhaseId(),r.scheduledStart(),r.scheduledEnd(),r.intensity());
        if(!repository.activeWorkRider(r.workRiderUserId())) throw validation("workRiderUserId must identify an active Work Rider.");
        repository.lockRiderSchedule(r.workRiderUserId());
        if(repository.hasScheduleConflict(r.workRiderUserId(),r.scheduledStart(),r.scheduledEnd(),null)) throw business("Work Rider has an overlapping session schedule.");
        if(repository.hasHorseScheduleConflict(r.horseProgramPhaseId(),r.scheduledStart(),r.scheduledEnd(),null)) throw business("The horse already has another session overlapping this time.");
        requireRiderAcknowledgement(r.horseProgramPhaseId(),r.intensity(),r.workRiderUserId(),r.scheduledStart(),r.scheduledEnd(),null,r.riderWarningAcknowledged());
        SessionApi.SuitabilityResult suitability=evaluate(r.horseProgramPhaseId(),r.intensity(),r.exercises());
        if(suitability.sessionBlocked()) throw business("Training is locked by the Veterinarian: "+suitability.blockedMessage());
        List<String> hardBlocks=new ArrayList<>();
        for(int i=0;i<suitability.exercises().size();i++) if("A".equals(suitability.exercises().get(i).category())) hardBlocks.add("exercises["+i+"]: "+String.join("; ",suitability.exercises().get(i).messages()));
        if(!hardBlocks.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,"The session cannot be scheduled.",hardBlockDetails(hardBlocks));
        validateExerciseInputs(r.exercises(),suitability);
        String code="TS"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase(Locale.ROOT);
        long id=repository.insert(r.horseProgramPhaseId(),a.userId(),r.workRiderUserId(),code,r.scheduledStart(),r.scheduledEnd(),r.surface(),r.focus(),r.intensity());
        for(int i=0;i<r.exercises().size();i++) repository.insertExercise(id,r.exercises().get(i),suitability.exercises().get(i).category());
        repository.notifyRider(r.workRiderUserId(),id,code);
        repository.audit(a.userId(),"TRAINER","SESSION_CREATED",id,"Training session scheduled.");
        if(Boolean.TRUE.equals(r.riderWarningAcknowledged())) repository.audit(a.userId(),"TRAINER","RIDER_WARNINGS_ACKNOWLEDGED",id,"Trainer acknowledged Work Rider warnings.");
        return detail(id,false);
    }

    @Transactional(readOnly=true)
    public SessionApi.Session get(long id) { requireId(id); return detail(id,true); }

    @Transactional
    public SessionApi.Session update(long id,SessionApi.UpdateRequest r) {
        ActorContext a=require(RoleCode.TRAINER); requireId(id);
        repository.lockSession(id);
        Map<String,Object> row=requiredSession(id); authorize(row,a);
        if(!"SCHEDULED".equals(row.get("status"))) throw business("Only a SCHEDULED session can be changed.");
        Instant start=r.scheduledStart()==null?instant(row.get("scheduled_start")):r.scheduledStart();
        Instant end=r.scheduledEnd()==null?instant(row.get("scheduled_end")):r.scheduledEnd();
        String intensity=r.intensity()==null?(String)row.get("intensity"):r.intensity();
        validateSchedule(longValue(row,"horse_program_phase_id"),start,end,intensity);
        long rider=r.workRiderUserId()==null?longValue(row,"work_rider_user_id"):r.workRiderUserId();
        if(!repository.activeWorkRider(rider)) throw validation("workRiderUserId must identify an active Work Rider.");
        repository.lockRiderSchedule(rider);
        if(repository.hasScheduleConflict(rider,start,end,id)) throw business("Work Rider has an overlapping session schedule.");
        if(repository.hasHorseScheduleConflict(longValue(row,"horse_program_phase_id"),start,end,id)) throw business("The horse already has another session overlapping this time.");
        if(rider!=longValue(row,"work_rider_user_id")||!java.util.Objects.equals(intensity,row.get("intensity")))
            requireRiderAcknowledgement(longValue(row,"horse_program_phase_id"),intensity,rider,start,end,id,r.riderWarningAcknowledged());
        Instant checkedAt=repository.precheckPerformedAt(id);
        if(checkedAt!=null&&checkedAt.isBefore(start.minus(com.racehorse.medical.incident.Core026Service.PRECHECK_WINDOW)))
            throw business("A pre-session check is already recorded; the session cannot move more than 12 hours after that check.");
        int changed=jdbc.update("UPDATE dbo.training_session SET scheduled_start=?,scheduled_end=?,surface=COALESCE(?,surface),focus=COALESCE(?,focus),intensity=?,work_rider_user_id=?,updated_at=SYSUTCDATETIME() WHERE id=? AND status='SCHEDULED'",
                timestamp(start),timestamp(end),r.surface(),r.focus(),intensity,rider,id);
        if(changed!=1)throw business("The session state changed; reload and try again.");
        repository.audit(a.userId(),"TRAINER","SESSION_UPDATED",id,"Scheduled session details changed.");
        if(Boolean.TRUE.equals(r.riderWarningAcknowledged())) repository.audit(a.userId(),"TRAINER","RIDER_WARNINGS_ACKNOWLEDGED",id,"Trainer acknowledged Work Rider warnings.");
        return detail(id,false);
    }

    @Transactional
    public SessionApi.Session replaceExercises(long id,List<SessionApi.ExerciseInput> exercises) {
        ActorContext a=require(RoleCode.TRAINER); requireId(id);
        if(exercises==null||exercises.isEmpty()) throw validation("At least one exercise is required.");
        repository.lockSession(id);
        Map<String,Object> row=requiredSession(id); authorize(row,a);
        if(!"SCHEDULED".equals(row.get("status"))) throw business("Exercises can only be changed while SCHEDULED.");
        SessionApi.SuitabilityResult result=evaluate(longValue(row,"horse_program_phase_id"),(String)row.get("intensity"),exercises);
        if(result.sessionBlocked()) throw business("Training is locked by the Veterinarian: "+result.blockedMessage());
        List<String> hardBlocks=new ArrayList<>();
        for(int i=0;i<result.exercises().size();i++) if("A".equals(result.exercises().get(i).category()))
            hardBlocks.add("exercises["+i+"]: "+String.join("; ",result.exercises().get(i).messages()));
        if(!hardBlocks.isEmpty()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "The session exercises cannot be changed.",hardBlockDetails(hardBlocks));
        validateExerciseInputs(exercises,result);
        repository.deleteExercises(id);
        for(int i=0;i<exercises.size();i++) repository.insertExercise(id,exercises.get(i),result.exercises().get(i).category());
        repository.audit(a.userId(),"TRAINER","SESSION_EXERCISES_UPDATED",id,"Scheduled session exercises replaced.");
        return detail(id,false);
    }

    @Transactional(readOnly=true)
    public SessionApi.SuitabilityResult suitability(SessionApi.DraftRequest r) {
        ActorContext a=require(RoleCode.TRAINER); validateDraft(r);
        ensureTrainerAssignment(r.horseProgramPhaseId(),a.userId(),r.scheduledStart());
        SessionApi.SuitabilityResult result=evaluate(r.horseProgramPhaseId(),r.intensity(),r.exercises());
        if(!result.sessionBlocked())validateExerciseInputs(r.exercises(),result);
        return result;
    }

    @Transactional(readOnly=true)
    public SessionApi.CandidateList riderCandidates(SessionApi.DraftRequest r) {
        ActorContext a=require(RoleCode.TRAINER); validateDraft(r); ensureTrainerAssignment(r.horseProgramPhaseId(),a.userId(),r.scheduledStart());
        return candidates(r.horseProgramPhaseId(),r.intensity(),r.scheduledStart(),r.scheduledEnd(),null);
    }

    @Transactional(readOnly=true)
    public SessionApi.CandidateList riderCandidatesForSession(long id) {
        ActorContext a=require(RoleCode.TRAINER); requireId(id); Map<String,Object> row=requiredSession(id); authorize(row,a);
        return candidates(longValue(row,"horse_program_phase_id"),(String)row.get("intensity"),instant(row.get("scheduled_start")),instant(row.get("scheduled_end")),id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public SessionApi.Session start(long id) {
        ActorContext a=actor(); requireId(id); repository.lockSession(id); Map<String,Object> row=requiredSession(id); authorize(row,a);
        if(!Set.of(RoleCode.TRAINER,RoleCode.WORK_RIDER).stream().anyMatch(a.roles()::contains)) throw forbidden();
        if("WORK_RIDER".equals(primaryRole(a))&&longValue(row,"work_rider_user_id")!=a.userId()) throw forbidden();
        if("TRAINER".equals(primaryRole(a))) authorize(row,a);
        if(!"SCHEDULED".equals(row.get("status"))) throw business("Only a SCHEDULED session can start.");
        if(instant(row.get("scheduled_start")).isAfter(calendar.now())) throw business("A session cannot start before its scheduledStart; the Trainer must reschedule.");
        Instant latestStart=row.get("scheduled_end")==null?instant(row.get("scheduled_start")).plus(com.racehorse.medical.incident.Core026Service.PRECHECK_WINDOW):instant(row.get("scheduled_end"));
        if(calendar.now().isAfter(latestStart)) throw business("The scheduled time has passed; the Trainer must reschedule the session.");
        String precheck=repository.precheckResult(id);
        if(precheck==null) throw business("Start is blocked until a Groom pre-session check is recorded.");
        if("ABNORMAL".equals(precheck)) throw business("Start is blocked by an ABNORMAL Groom pre-session check.");
        if(repository.precheckPerformedAt(id).isBefore(instant(row.get("scheduled_start")).minus(com.racehorse.medical.incident.Core026Service.PRECHECK_WINDOW)))
            throw business("The pre-session check is older than 12 hours before the scheduled start; ask the Groom for a new check.");
        if("LOCKED".equals(row.get("availability"))) throw business("Training is locked by the Veterinarian: "+row.get("restriction_reason"));
        if(!"ACTIVE".equals(row.get("program_status"))) throw business("The training program is not ACTIVE.");
        List<SessionApi.ExerciseInput> current=repository.exercises(id).stream().map(e->new SessionApi.ExerciseInput(e.exerciseDefinitionId(),e.exerciseOrder(),e.distanceM(),e.durationMin(),e.repetitions(),e.restIntervalMin(),e.intensity(),e.surface(),e.acknowledgementNote())).toList();
        SessionApi.SuitabilityResult currentCheck=evaluate(longValue(row,"horse_program_phase_id"),(String)row.get("intensity"),current);
        if(currentCheck.sessionBlocked()||currentCheck.exercises().stream().anyMatch(e->"A".equals(e.category()))) throw business("The session is medically blocked under the current restriction.");
        if(repository.transition(id,"SCHEDULED","IN_PROGRESS")!=1) throw business("Session state changed; reload and try again.");
        repository.audit(a.userId(),primaryRole(a),"SESSION_STARTED",id,"Session started after server-side gates passed.");
        return detail(id,false);
    }

    @Transactional
    public SessionApi.Session transition(long id,String from,String to) {
        ActorContext a=require(RoleCode.TRAINER); requireId(id); repository.lockSession(id); Map<String,Object> row=requiredSession(id); authorize(row,a);
        if(repository.transition(id,from,to)!=1) throw business("The session is not in a state that allows this transition.");
        repository.audit(a.userId(),"TRAINER","SESSION_"+to,id,"Session state changed to "+to+".");
        return detail(id,false);
    }

    @Transactional
    public SessionApi.Session resume(long id) {
        ActorContext a=require(RoleCode.TRAINER); requireId(id); repository.lockSession(id); Map<String,Object> row=requiredSession(id); authorize(row,a);
        if(!"PAUSED".equals(row.get("status"))) throw business("Only a PAUSED session can resume.");
        if("LOCKED".equals(row.get("availability"))) throw business("Training is locked by the Veterinarian: "+row.get("restriction_reason"));
        if(!"ALL_NORMAL".equals(repository.precheckResult(id))) throw business("Resume is blocked until a fresh ALL_NORMAL Groom pre-session check is recorded.");
        if(!"ACTIVE".equals(row.get("program_status"))) throw business("The training program is not ACTIVE.");
        List<SessionApi.ExerciseInput> current=repository.exercises(id).stream().map(e->new SessionApi.ExerciseInput(e.exerciseDefinitionId(),e.exerciseOrder(),e.distanceM(),e.durationMin(),e.repetitions(),e.restIntervalMin(),e.intensity(),e.surface(),e.acknowledgementNote())).toList();
        SessionApi.SuitabilityResult currentCheck=evaluate(longValue(row,"horse_program_phase_id"),(String)row.get("intensity"),current);
        if(currentCheck.sessionBlocked()||currentCheck.exercises().stream().anyMatch(e->"A".equals(e.category()))) throw business("The session is medically blocked under the current restriction.");
        if(repository.transition(id,"PAUSED","IN_PROGRESS")!=1) throw business("Session state changed; reload and try again.");
        repository.audit(a.userId(),"TRAINER","SESSION_RESUMED",id,"Session resumed after medical gate passed.");
        return detail(id,false);
    }

    @Transactional(readOnly=true)
    public List<SessionApi.Session> groomSessions(LocalDate date) {
        ActorContext a=require(RoleCode.GROOM);
        if(date==null)throw validation("date is required.");
        Instant from=date.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant to=date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return jdbc.queryForList("""
                SELECT s.id FROM dbo.training_session s
                JOIN dbo.training_session_groom_assignment ga ON ga.training_session_id=s.id
                WHERE ga.groom_user_id=? AND s.scheduled_start>=? AND s.scheduled_start<?
                  AND s.status='SCHEDULED' ORDER BY s.scheduled_start,s.id
                """,a.userId(),timestamp(from),timestamp(to)).stream().map(r->detail(longValue(r,"id"),true)).toList();
    }

    @Transactional(readOnly=true)
    public List<SessionApi.UserRef> groomCandidates(long id) {
        ActorContext a=require(RoleCode.TRAINER);requireId(id);Map<String,Object> row=requiredSession(id);
        ensureTrainerAssignment(longValue(row,"horse_program_phase_id"),a.userId(),instant(row.get("scheduled_start")));
        return jdbc.query("""
                SELECT u.id,u.user_code,u.full_name FROM dbo.app_user u
                WHERE u.account_status='ACTIVE' AND EXISTS (
                  SELECT 1 FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id
                  WHERE ur.user_id=u.id AND r.role_code='GROOM') ORDER BY u.full_name,u.id
                """,(rs,n)->new SessionApi.UserRef(rs.getLong(1),rs.getString(2),rs.getString(3)));
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public SessionApi.Session assignGroom(long id,long groomId) {
        ActorContext a=require(RoleCode.TRAINER);requireId(id);repository.lockSession(id);
        Map<String,Object> row=requiredSession(id);
        ensureTrainerAssignment(longValue(row,"horse_program_phase_id"),a.userId(),instant(row.get("scheduled_start")));
        if(!"SCHEDULED".equals(row.get("status"))||repository.precheckResult(id)!=null)
            throw business("A Groom may be assigned only before a SCHEDULED session's pre-check.");
        if(groomCandidates(id).stream().noneMatch(u->u.id()==groomId))throw validation("groomUserId must identify an ACTIVE Groom.");
        SessionApi.UserRef current=groom(id);
        if(current!=null&&current.id()==groomId)return detail(id,false);
        if(current==null)jdbc.update("INSERT INTO dbo.training_session_groom_assignment(training_session_id,groom_user_id,assigned_by_user_id,assigned_at) VALUES (?,?,?,SYSUTCDATETIME())",id,groomId,a.userId());
        else jdbc.update("UPDATE dbo.training_session_groom_assignment SET groom_user_id=?,assigned_by_user_id=?,assigned_at=SYSUTCDATETIME() WHERE training_session_id=?",groomId,a.userId(),id);
        notifications.persistAndPublish(groomId,Category.SCHEDULE,"GROOM_PRECHECK_ASSIGNED","Pre-session check assigned",
                "Pre-session check assigned for session "+row.get("session_code")+".","TRAINING_SESSION",id,false);
        repository.audit(a.userId(),"TRAINER","SESSION_GROOM_ASSIGNED",id,"Explicit Groom assignment updated.");
        return detail(id,false);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public SessionApi.Session reduceIntensity(long id,String intensity) {
        ActorContext a=require(RoleCode.TRAINER);requireId(id);
        if(intensity==null||!INTENSITIES.contains(intensity))throw validation("Unsupported intensity.");
        repository.lockSession(id);Map<String,Object> row=requiredSession(id);
        ensureTrainerAssignment(longValue(row,"horse_program_phase_id"),a.userId(),Instant.now());
        if(!Set.of("IN_PROGRESS","PAUSED").contains(row.get("status")))throw business("Only IN_PROGRESS or PAUSED sessions allow an intensity reduction.");
        List<String> order=List.of("LOW","MEDIUM","MEDIUM_HIGH","HIGH");
        int previous=order.indexOf(row.get("intensity"));
        if(previous<0||order.indexOf(intensity)>=previous)throw validation("The new intensity must be strictly lower than the current intensity.");
        if(!"ACTIVE".equals(row.get("program_status"))||!"ALL_NORMAL".equals(repository.precheckResult(id)))
            throw business("The active program and ALL_NORMAL pre-check gates must still pass.");
        List<SessionApi.ExerciseInput> exercises=repository.exercises(id).stream().map(e->new SessionApi.ExerciseInput(e.exerciseDefinitionId(),e.exerciseOrder(),e.distanceM(),e.durationMin(),e.repetitions(),e.restIntervalMin(),e.intensity(),e.surface(),e.acknowledgementNote())).toList();
        SessionApi.SuitabilityResult result=evaluate(longValue(row,"horse_program_phase_id"),intensity,exercises);
        if(result.sessionBlocked()||!result.sessionIssues().isEmpty()||result.exercises().stream().anyMatch(e->"A".equals(e.category())))
            throw business("Current medical restrictions and phase intensity bounds must pass.");
        if(jdbc.update("UPDATE dbo.training_session SET intensity=?,updated_at=SYSUTCDATETIME() WHERE id=? AND intensity=? AND status IN ('IN_PROGRESS','PAUSED')",intensity,id,row.get("intensity"))!=1)
            throw business("Session changed concurrently; reload before reducing intensity.");
        repository.audit(a.userId(),"TRAINER","SESSION_INTENSITY_REDUCED",id,"Session intensity reduced from "+row.get("intensity")+" to "+intensity+".");
        return detail(id,false);
    }

    private SessionApi.UserRef groom(long id) {
        return jdbc.query("""
                SELECT u.id,u.user_code,u.full_name FROM dbo.training_session_groom_assignment ga
                JOIN dbo.app_user u ON u.id=ga.groom_user_id WHERE ga.training_session_id=?
                """,(rs,n)->new SessionApi.UserRef(rs.getLong(1),rs.getString(2),rs.getString(3)),id).stream().findFirst().orElse(null);
    }

    private static final List<String> LABEL_ORDER=List.of("RECOMMENDED","SUITABLE","SUITABLE_WITH_CAUTION","NOT_RECOMMENDED");

    /** BR-D22: schedule conflict is the only hard block; skills, horse experience and workload give labels and warnings. */
    private SessionApi.CandidateList candidates(long phaseId,String intensity,Instant start,Instant end,Long except) {
        Map<String,Object> phase=repository.phase(phaseId); if(phase==null) throw notFound("Program phase was not found.");
        long horseId=longValue(phase,"horse_id");
        List<String> strengths=repository.horseStrengths(horseId);
        List<SessionApi.Candidate> list=repository.candidates().stream().map(u->candidate(u,horseId,strengths,intensity,start,end,except))
                .sorted(Comparator.comparingInt(c->LABEL_ORDER.indexOf(c.label()))).toList();
        return new SessionApi.CandidateList(list,list.isEmpty()?"No active Work Riders are available.":null);
    }

    private SessionApi.Candidate candidate(Map<String,Object> u,long horseId,List<String> strengths,String intensity,Instant start,Instant end,Long except) {
        long id=longValue(u,"id"); String level=(String)u.get("experience_level");
        List<String> skills=repository.riderSkills(id);
        List<String> reasons=new ArrayList<>(),warnings=new ArrayList<>();
        boolean conflict=repository.hasScheduleConflict(id,start,end,except);
        List<String> matched=RiderSkillMatch.matchedStrengths(skills,strengths);
        if(!matched.isEmpty()) reasons.add("Kỹ năng phù hợp thế mạnh của ngựa: "+String.join(", ",matched)+".");
        int withHorse=repository.completedSessionsWithHorse(id,horseId);
        if(withHorse>0) reasons.add("Đã hoàn thành "+withHorse+" buổi tập với ngựa này.");
        if(!conflict) reasons.add("Rảnh trong khung giờ này.");
        if(RiderSkillMatch.junior(level)&&intensity!=null&&RiderSkillMatch.HIGH_INTENSITY.contains(intensity))
            warnings.add("Work Rider mới cho buổi cường độ cao; cần xác nhận trước khi giao.");
        int sameDay=repository.riderSessionsOnClubDay(id,start,except);
        if(sameDay>=RiderSkillMatch.DAILY_SESSION_WARNING) warnings.add("Work Rider đã có "+sameDay+" buổi trong ngày này.");
        if(!strengths.isEmpty()&&matched.isEmpty()&&!skills.isEmpty()) warnings.add("Chưa có kỹ năng khớp với thế mạnh đã khai báo của ngựa.");
        String label=conflict?"NOT_RECOMMENDED":!warnings.isEmpty()?"SUITABLE_WITH_CAUTION":(!matched.isEmpty()||withHorse>0)?"RECOMMENDED":"SUITABLE";
        return new SessionApi.Candidate(new SessionApi.UserRef(id,(String)u.get("user_code"),(String)u.get("full_name")),level,label,
                List.copyOf(reasons),List.copyOf(warnings),conflict?"Trùng lịch với một buổi tập khác của Work Rider.":null,skills);
    }

    /** Warnings must be acknowledged before a rider is saved on a session (BR-D22). */
    private void requireRiderAcknowledgement(long phaseId,String intensity,long riderId,Instant start,Instant end,Long except,Boolean acknowledged) {
        if(Boolean.TRUE.equals(acknowledged)) return;
        Map<String,Object> rider=repository.candidates().stream().filter(u->longValue(u,"id")==riderId).findFirst().orElse(null);
        if(rider==null) return;
        long horseId=longValue(repository.phase(phaseId),"horse_id");
        SessionApi.Candidate c=candidate(rider,horseId,repository.horseStrengths(horseId),intensity,start,end,except);
        if(!c.warnings().isEmpty()) throw business("Acknowledge the Work Rider warnings before saving the session.");
    }

    private SessionApi.SuitabilityResult evaluate(long phaseId,String intensity,List<SessionApi.ExerciseInput> items) {
        Map<String,Object> phase=repository.phase(phaseId); if(phase==null) throw notFound("Program phase was not found.");
        long horseId=longValue(phase,"horse_id"); String locked=repository.restrictionReason(horseId);
        String availability=(String)jdbc.queryForList("SELECT restriction_level FROM dbo.medical_restriction WHERE horse_id=? AND lifted_at IS NULL",horseId).stream().findFirst().map(m->m.get("restriction_level")).orElse("AVAILABLE");
        if("LOCKED".equals(availability)) return new SessionApi.SuitabilityResult("LOCKED",true,locked,List.of(),List.of());
        List<String> healthRows=jdbc.queryForList("SELECT health_status FROM dbo.horse WHERE id=?",String.class,horseId);
        String health=healthRows.isEmpty()?null:healthRows.getFirst();
        if(Set.of("INJURED","QUARANTINED").contains(String.valueOf(health))) return new SessionApi.SuitabilityResult("LOCKED",true,"The horse is "+health+"; the Veterinarian must re-examine it before training.",List.of(),List.of());
        if(items==null) items=List.of();
        List<Long> ids=items.stream().map(SessionApi.ExerciseInput::exerciseDefinitionId).distinct().toList();
        Map<Long,Map<String,Object>> defs=new HashMap<>(); for(Map<String,Object>d:repository.exerciseDefinitions(ids))defs.put(longValue(d,"id"),d);
        List<Map<String,Object>> rules="LIMITED".equals(availability)?repository.restrictionRules(horseId):List.of();
        List<SessionApi.SuitabilityItem> exerciseResults=new ArrayList<>();
        for(SessionApi.ExerciseInput e:items) {
            Map<String,Object> d=defs.get(e.exerciseDefinitionId()); if(d==null) throw notFound("Exercise "+e.exerciseDefinitionId()+" was not found.");
            String category=null; List<String> messages=new ArrayList<>(); List<String> actions=List.of();
            if("LIMITED".equals(availability)) {
                boolean prohibited=rules.stream().anyMatch(rule->matches(rule,d,"PROHIBITED"));
                boolean hasAllow=rules.stream().anyMatch(rule->"ALLOWED".equals(rule.get("rule_type")));
                boolean allowedMatch=rules.stream().anyMatch(rule->matches(rule,d,"ALLOWED"));
                if(prohibited||hasAllow&&!allowedMatch) { category="A"; messages.add(prohibited?"Exercise is prohibited by the active restriction.":"Exercise is not included in the active restriction's allowed rules."); actions=List.of("REMOVE","REPLACE"); }
            }
            exerciseResults.add(new SessionApi.SuitabilityItem(e.exerciseOrder(),e.exerciseDefinitionId(),category,messages,actions));
        }
        List<SessionApi.SessionIssue> issues=new ArrayList<>();
        if(intensity!=null&&(!INTENSITIES.contains(intensity)||!within(intensity,(String)phase.get("intensity_min"),(String)phase.get("intensity_max")))) issues.add(new SessionApi.SessionIssue("D","intensity","Intensity is outside the phase's configured range."));
        return new SessionApi.SuitabilityResult(availability,false,null,exerciseResults,issues);
    }

    private boolean matches(Map<String,Object> rule,Map<String,Object> exercise,String type) {
        if(!type.equals(rule.get("rule_type")))return false;
        Object exerciseId=rule.get("exercise_definition_id");
        if(exerciseId!=null&&longValue(rule,"exercise_definition_id")==longValue(exercise,"id"))return true;
        Object note=rule.get("exercise_type_note"), exerciseType=exercise.get("exercise_type");
        return note!=null&&exerciseType!=null&&note.toString().trim().equalsIgnoreCase(exerciseType.toString().trim());
    }

    private void validateExerciseInputs(List<SessionApi.ExerciseInput> items,SessionApi.SuitabilityResult result) {
        Set<Integer> orders=new HashSet<>();
        for(int i=0;i<items.size();i++) {
            SessionApi.ExerciseInput e=items.get(i);
            if(e.exerciseOrder()<1||!orders.add(e.exerciseOrder()))throw validation("exerciseOrder values must be unique positive integers.");
            if(negative(e.distanceM())||negative(e.durationMin())||negative(e.restIntervalMin())||(e.repetitions()!=null&&e.repetitions()<0))throw validation("Exercise metrics cannot be negative.");
            if(e.intensity()!=null&&!INTENSITIES.contains(e.intensity()))throw validation("Unsupported exercise intensity.");
            String cat=result.exercises().get(i).category();
            if("A".equals(cat)&&e.acknowledgementNote()!=null)throw validation("Category A exercises cannot include acknowledgementNote.");
            if(("C".equals(cat)||"D".equals(cat))&&(e.acknowledgementNote()==null||e.acknowledgementNote().isBlank()))throw validation("acknowledgementNote is required for category C/D.");
        }
    }

    private void validateSchedule(long phaseId,Instant start,Instant end,String intensity) {
        Map<String,Object> p=repository.phase(phaseId); if(p==null) throw notFound("Program phase was not found.");
        if(start==null||end!=null&&end.isBefore(start))throw validation("scheduledEnd must be at or after scheduledStart.");
        if(!"ACTIVE".equals(p.get("program_status")))throw business("The training program is not ACTIVE.");
        if(!"ACTIVE".equals(p.get("lifecycle")))throw business("The horse is not ACTIVE.");
        calendar.requireFutureSession(start);
        LocalDate date=start.atZone(CLUB_ZONE).toLocalDate();
        if(date.isBefore(localDate(p.get("start_date")))||date.isAfter(localDate(p.get("end_date"))))throw business("scheduledStart must be inside the selected phase dates.");
        if(date.isBefore(localDate(p.get("period_start")))||date.isAfter(localDate(p.get("period_end"))))throw business("scheduledStart must be inside the program period.");
        if(end!=null) {
            LocalDate endDate=end.atZone(CLUB_ZONE).toLocalDate();
            if(endDate.isBefore(localDate(p.get("start_date")))||endDate.isAfter(localDate(p.get("end_date"))))throw business("scheduledEnd must be inside the selected phase dates.");
            if(endDate.isBefore(localDate(p.get("period_start")))||endDate.isAfter(localDate(p.get("period_end"))))throw business("scheduledEnd must be inside the program period.");
        }
        if(intensity!=null&&(!INTENSITIES.contains(intensity)||!within(intensity,(String)p.get("intensity_min"),(String)p.get("intensity_max"))))throw validation("intensity must fit the phase intensity range.");
        // Keep public date-validation errors, but authorize before returning clinical reasons.
        ensureTrainerAssignment(phaseId,require(RoleCode.TRAINER).userId(),start);
        String reason=repository.restrictionReason(longValue(p,"horse_id"));
        if(reason!=null) throw business("Training is locked by the Veterinarian: "+reason);
    }

    private void validateDraft(SessionApi.DraftRequest r) {
        if(r==null||r.horseProgramPhaseId()<=0||r.scheduledStart()==null||r.exercises()==null)throw validation("Draft session fields are invalid.");
        Map<String,Object> p=repository.phase(r.horseProgramPhaseId()); if(p==null)throw notFound("Program phase was not found.");
        if(!"ACTIVE".equals(p.get("program_status")))throw business("The training program is not ACTIVE.");
        if(!"ACTIVE".equals(p.get("lifecycle")))throw business("The horse is not ACTIVE.");
        if(r.scheduledEnd()!=null&&r.scheduledEnd().isBefore(r.scheduledStart()))throw validation("scheduledEnd must be at or after scheduledStart.");
        calendar.requireFutureSession(r.scheduledStart());
        LocalDate startDate=r.scheduledStart().atZone(CLUB_ZONE).toLocalDate();
        if(startDate.isBefore(localDate(p.get("start_date")))||startDate.isAfter(localDate(p.get("end_date")))
                ||startDate.isBefore(localDate(p.get("period_start")))||startDate.isAfter(localDate(p.get("period_end"))))throw business("scheduledStart must be inside the active phase and program periods.");
        if(r.scheduledEnd()!=null) {
            LocalDate endDate=r.scheduledEnd().atZone(CLUB_ZONE).toLocalDate();
            if(endDate.isBefore(localDate(p.get("start_date")))||endDate.isAfter(localDate(p.get("end_date")))
                    ||endDate.isBefore(localDate(p.get("period_start")))||endDate.isAfter(localDate(p.get("period_end"))))throw business("scheduledEnd must be inside the active phase and program periods.");
        }
        if(r.intensity()!=null&&!INTENSITIES.contains(r.intensity()))throw validation("Unsupported intensity.");
        for(SessionApi.ExerciseInput e:r.exercises())if(e==null)throw validation("Exercise entries cannot be null.");
    }

    private void ensureTrainerAssignment(long phaseId,long trainerId,Instant on) {
        Integer n=jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.horse_program_phase p JOIN dbo.trainer_assignment ta ON ta.horse_training_program_id=p.horse_training_program_id
                WHERE p.id=? AND ta.trainer_user_id=? AND ta.effective_from<=? AND (ta.effective_to IS NULL OR ta.effective_to>=?)
                """,Integer.class,phaseId,trainerId,on.atZone(CLUB_ZONE).toLocalDate(),on.atZone(CLUB_ZONE).toLocalDate());
        if(n==null||n==0)throw forbidden();
    }

    private SessionApi.Session detail(long id,boolean checkScope) {
        Map<String,Object> row=requiredSession(id); if(checkScope)authorize(row,actor());
        List<String> blocks=new ArrayList<>();
        if("LOCKED".equals(row.get("availability")))blocks.add("AVAILABILITY_LOCKED");
        String check=repository.precheckResult(id);
        if(check==null)blocks.add("PRE_CHECK_MISSING");
        else if("ABNORMAL".equals(check))blocks.add("PRE_CHECK_ABNORMAL");
        if(!"ACTIVE".equals(row.get("program_status")))blocks.add("PROGRAM_NOT_ACTIVE");
        List<SessionApi.Exercise> exercises=repository.exercises(id);
        if(exercises.stream().anyMatch(e->"A".equals(e.suitabilityCategory())))blocks.add("EXERCISE_MEDICAL_BLOCK");
        String restrictionMessage=Objects.toString(row.get("restriction_reason"),null);
        String blockedMessage="LOCKED".equals(row.get("availability"))?"Training is locked by the Veterinarian: "+restrictionMessage:null;
        SessionApi.UserRef rider=new SessionApi.UserRef(longValue(row,"work_rider_user_id"),(String)row.get("rider_code"),(String)row.get("rider_name"));
        SessionApi.UserRef trainer=row.get("trainer_user_id")==null?null:new SessionApi.UserRef(longValue(row,"trainer_user_id"),null,(String)row.get("trainer_name"));
        SessionApi.UserRef creator=new SessionApi.UserRef(longValue(row,"created_by_user_id"),(String)row.get("creator_code"),(String)row.get("creator_name"));
        return new SessionApi.Session(id,(String)row.get("session_code"),
                new SessionApi.HorseRef(longValue(row,"horse_id"),(String)row.get("horse_code"),(String)row.get("horse_name")),
                longValue(row,"horse_training_program_id"),(String)row.get("phase_name"),instant(row.get("scheduled_start")),
                instant(row.get("scheduled_end")),(String)row.get("status"),rider,trainer,check,!blocks.isEmpty(),blocks,blockedMessage,
                longValue(row,"horse_program_phase_id"),(String)row.get("surface"),(String)row.get("focus"),(String)row.get("intensity"),
                instant(row.get("actual_start")),instant(row.get("actual_end")),(String)row.get("interruption_reason"),(String)row.get("availability"),
                exercises,repository.precheck(id),repository.linkedIncidentIds(id),repository.hasRiderFeedback(id),repository.hasEvaluation(id),creator,groom(id));
    }

    private Map<String,Object> requiredSession(long id) { Map<String,Object> row=repository.lockedSession(id); if(row==null)throw notFound("Training session was not found.");return row; }
    private void authorize(Map<String,Object> row,ActorContext actor) {
        if(actor.roles().contains(RoleCode.GROOM)) {
            SessionApi.UserRef assigned=groom(longValue(row,"id"));
            if(assigned!=null&&assigned.id()==actor.userId())return;
            throw forbidden();
        }
        if(actor.roles().contains(RoleCode.HEAD_TRAINER)||actor.roles().contains(RoleCode.CLUB_MANAGER)||actor.roles().contains(RoleCode.VETERINARIAN))return;
        if(actor.roles().contains(RoleCode.WORK_RIDER)&&longValue(row,"work_rider_user_id")==actor.userId())return;
        if(actor.roles().contains(RoleCode.TRAINER)&&row.get("trainer_user_id")!=null&&longValue(row,"trainer_user_id")==actor.userId())return;
        throw forbidden();
    }
    private ActorContext actor(){return actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication is required."));}
    private ActorContext require(RoleCode r){ActorContext a=actor();if(!a.roles().contains(r))throw forbidden();return a;}
    private String primaryRole(ActorContext a){return a.roles().contains(RoleCode.WORK_RIDER)?"WORK_RIDER":a.roles().contains(RoleCode.TRAINER)?"TRAINER":"";}
    private BusinessException validation(String m){return new BusinessException(ErrorCode.VALIDATION_ERROR,m);}
    private BusinessException notFound(String m){return new BusinessException(ErrorCode.NOT_FOUND,m);}
    private BusinessException forbidden(){return new BusinessException(ErrorCode.FORBIDDEN,"This actor is outside the required role or assignment scope.");}
    private BusinessException business(String m){return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,m);}
    private void requireId(long id){if(id<=0)throw validation("id must be positive.");}
    private boolean within(String val,String min,String max){if(val==null)return true;List<String> order=List.of("LOW","MEDIUM","MEDIUM_HIGH","HIGH");return (min==null||order.indexOf(val)>=order.indexOf(min))&&(max==null||order.indexOf(val)<=order.indexOf(max));}
    private boolean negative(Double n){return n!=null&&n<0;}
    private LocalDate localDate(Object o){if(o instanceof LocalDate d)return d;if(o instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(o.toString());}
    private Instant instant(Object o){if(o==null)return null;if(o instanceof Instant i)return i;if(o instanceof Timestamp t)return t.toInstant();if(o instanceof java.time.OffsetDateTime d)return d.toInstant();if(o instanceof java.time.LocalDateTime d)return d.toInstant(ZoneOffset.UTC);return Instant.parse(o.toString());}
    private Timestamp timestamp(Instant i){return i==null?null:Timestamp.from(i);}
    private long longValue(Map<String,Object> m,String key){Object v=m.get(key);if(v==null)throw new IllegalStateException("Expected DB value "+key);return ((Number)v).longValue();}
    private Long nullableLong(Object o){return o==null?null:((Number)o).longValue();}
    private List<com.racehorse.common.api.ErrorDetail> hardBlockDetails(List<String> reasons){return reasons.stream().map(r->{int split=r.indexOf(':');return new com.racehorse.common.api.ErrorDetail(split<0?"exercises":r.substring(0,split),split<0?r:r.substring(split+1).trim());}).toList();}
    private SessionApi.Session map(Map<String,Object> row){return detail(longValue(row,"id"),false);}
}
