package com.racehorse.horse;

import com.racehorse.auth.*;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.*;
import com.racehorse.horse.activation.HorseActivationService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
public class HorseService {
    private final HorseRepository repository;
    private final CurrentActorProvider actors;
    private final HorseActivationService activationService;

    public HorseService(HorseRepository repository, CurrentActorProvider actors,
                        HorseActivationService activationService) {
        this.repository = repository;
        this.actors = actors;
        this.activationService = activationService;
    }
    private Long trainerScope() {
        ActorContext actor=actors.currentActor().orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED,"Authentication required."));
        if(actor.roles().stream().anyMatch(r->Set.of(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN,RoleCode.GROOM).contains(r)))return null;
        return actor.userId();
    }
    @Transactional
    public Map<String,Object> create(HorseIdentityRequest input) {
        List<String> strengths=validStrengths(input);
        long id;
        try{id=repository.insert(input.normalized());}
        catch(DataIntegrityViolationException error){
            Throwable cause=error;boolean duplicate=false;String field="horseCode";
            while(cause!=null){
                if(cause instanceof SQLException sql){
                    if(sql.getErrorCode()==2601||sql.getErrorCode()==2627||"23505".equals(sql.getSQLState()))duplicate=true;
                    if(sql.getMessage()!=null&&sql.getMessage().contains("registration_no"))field="registrationNo";
                }
                cause=cause.getCause();
            }
            if(!duplicate)throw error;
            throw new BusinessException(ErrorCode.CONFLICT,"Horse code or registration number already exists.",List.of(new ErrorDetail(field,"already exists")));
        }
        if(strengths!=null&&!strengths.isEmpty())declareStrengths(id,strengths);
        return detail(id);
    }
    @Transactional
    public Map<String,Object> update(long horseId, HorseIdentityRequest input) {
        requireHorse(horseId);
        List<String> strengths=validStrengths(input);
        try {
            if (repository.updateIdentity(horseId, input.normalized()) != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
            }
        } catch (DataIntegrityViolationException error) {
            Throwable cause = error;
            boolean duplicate = false;
            String field = "horseCode";
            while (cause != null) {
                if (cause instanceof SQLException sql) {
                    if (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627
                            || "23505".equals(sql.getSQLState())) {
                        duplicate = true;
                    }
                    if (sql.getMessage() != null && sql.getMessage().contains("registration_no")) {
                        field = "registrationNo";
                    }
                }
                cause = cause.getCause();
            }
            if (!duplicate) {
                throw error;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Horse code or registration number already exists.",
                    List.of(new ErrorDetail(field, "already exists")));
        }
        if (strengths != null && !strengths.equals(repository.strengths(List.of(horseId)).get(horseId))) {
            declareStrengths(horseId, strengths);
        }
        return detail(horseId);
    }
    private List<String> validStrengths(HorseIdentityRequest input) {
        List<String> invalid = new ArrayList<>();
        List<String> strengths = HorseStrength.normalize(input.strengths(), invalid);
        if (!invalid.isEmpty()) throw validation("strengths", "Unknown strength code.");
        return strengths;
    }
    private void declareStrengths(long horseId, List<String> strengths) {
        ActorContext actor = actors.currentActor().orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
        repository.replaceStrengths(horseId, strengths, actor.userId());
        repository.auditStrengths(horseId, actor.userId(), strengths.isEmpty()
                ? "Declared strengths cleared."
                : "Declared strengths: " + String.join(", ", strengths) + ". Not a Head Trainer confirmation.");
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(long id) {
        Long trainer=trainerScope();
        Map<String,Object> horse=repository.find(id,trainer).orElseThrow(()->new BusinessException(
            trainer!=null&&repository.exists(id)?ErrorCode.FORBIDDEN:ErrorCode.NOT_FOUND,
            trainer!=null&&repository.exists(id)?"This horse is outside your assigned programs.":"Horse not found."));
        horse.put("strengths",repository.strengths(List.of(id)).get(id));
        horse.put("currentOwnerships",repository.ownerships(id));
        horse.put("latestWeight",repository.latestWeight(id));return horse;
    }
    @Transactional(readOnly=true)
    public HorseRepository.Page list(String q,String lifecycle,String health,String availability,int page,int size) {
        return repository.list(q,lifecycle,health,availability,page,size,trainerScope());
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String,Object> setPedigree(long horseId, PedigreeRequest request) {
        requireHorse(horseId);
        validateParent(horseId, request.sire());
        validateParent(horseId, request.dam());
        if (request.sire().horseId() != null && request.sire().horseId().equals(request.dam().horseId())) {
            throw validation("sire", "Sire and dam must be different horses.");
        }
        repository.updatePedigree(horseId, request);
        activationService.reevaluate(horseId);
        return detail(horseId);
    }
    private void validateParent(long horseId, ParentReference parent) {
        if (parent.horseId() != null && (parent.horseId() == horseId || !repository.exists(parent.horseId()))) {
            throw validation("pedigree", "A parent must reference an existing horse other than this horse.");
        }
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> ownershipHistory(long horseId) {
        requireHorse(horseId);
        return repository.ownershipHistory(horseId);
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String,Object> addOwnership(long horseId, OwnershipRequest request) {
        requireHorse(horseId);
        if (!repository.ownerIsActiveHorseOwner(request.ownerUserId())) {
            throw validation("ownerUserId", "The owner must be an active user with the HORSE_OWNER role.");
        }
        long ownershipId = repository.insertOwnership(horseId, request);
        activationService.reevaluate(horseId);
        return repository.ownershipHistory(horseId).stream()
                .filter(row -> ((Number) row.get("id")).longValue() == ownershipId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Inserted ownership row could not be read back."));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> stableAssignmentHistory(long horseId) {
        requireHorse(horseId);
        return repository.stableAssignmentHistory(horseId);
    }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Map<String,Object> assignStableBox(long horseId, StableAssignmentRequest request) {
        requireHorse(horseId);
        LocalDate effectiveFrom = request.effectiveFrom();
        if (effectiveFrom.getYear() < 1 || effectiveFrom.getYear() > 9999) {
            throw validation("effectiveFrom", "The effective date must be within the SQL Server DATE range.");
        }
        if (!repository.stableBoxExists(request.stableBoxId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Stable box not found.");
        }
        Optional<HorseRepository.OpenStableAssignment> current = repository.openStableAssignment(horseId);
        if (current.isPresent() && !effectiveFrom.isAfter(current.get().effectiveFrom())) {
            throw validation("effectiveFrom", "A move must start after the current assignment begins.");
        }
        Long previousAssignmentId = current.map(HorseRepository.OpenStableAssignment::id).orElse(null);
        if (repository.hasOverlappingHorseAssignment(horseId, previousAssignmentId, effectiveFrom)) {
            throw assignmentConflict("effectiveFrom", "The horse already has an assignment during the requested period.");
        }
        if (repository.hasOverlappingBoxAssignment(request.stableBoxId(), effectiveFrom)) {
            throw assignmentConflict("stableBoxId", "This stable box is assigned during the requested period.");
        }

        ActorContext actor = actors.currentActor().orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
        try {
            if (current.isPresent()) {
                if (repository.closeOpenStableAssignment(current.get().id(), effectiveFrom.minusDays(1)) != 1) {
                    throw assignmentConflict("effectiveFrom", "The current assignment changed during the move.");
                }
            }
            long assignmentId = repository.insertStableAssignment(
                    horseId, request, actor.userId());
            activationService.reevaluate(horseId);
            return repository.stableAssignment(assignmentId)
                    .orElseThrow(() -> new IllegalStateException("Inserted stable assignment could not be read back."));
        } catch (DataAccessException error) {
            if (isAssignmentWriteConflict(error)) {
                throw assignmentConflict("stableBoxId", "The requested assignment overlaps an existing assignment.");
            }
            throw error;
        }
    }
    private void requireHorse(long horseId) {
        if (!repository.exists(horseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
        }
    }
    private static boolean isAssignmentWriteConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == 1205 || sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627
                    || "23505".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
    private static BusinessException assignmentConflict(String field, String message) {
        return new BusinessException(ErrorCode.CONFLICT, message,
                List.of(new ErrorDetail(field, message)));
    }
    private static BusinessException validation(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ErrorDetail(field, message)));
    }
}
